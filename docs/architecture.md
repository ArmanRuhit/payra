# Architecture — Payra

Living document, updated at phase exits. Plan/progress: `../CLAUDE.md`. Talking points: `interview-map.md`.

## 1. Context & runtime topology

```text
[browser] customer app          [browser] ops console
      |                               |
      v                               v
[web] Next.js 16 (server components + route handlers = BFF-lite,
      session in httpOnly cookies, refresh handled in one place)
      |                               |
      +---------------+---------------+
                      v  REST /api/v1 (JWT Bearer, cookies on FE side)
[api] Spring Boot 4.1 modular monolith (Java 21)
      modules: identity wallet ledger payments bills merchants fees
               risk notifications reconciliation backoffice
      |
      +--> provider adapters (mock bank / MFS / card)  ---> settlement CSV files
      |
      +--> [PostgreSQL 18]  source of truth (ledger)
      +--> [Redis 8]        OTP throttle, rate limits
      +--> [Kafka 4]        events (transfer, ledger) + DLQ  --> notifications/risk consumers
```

Provider simulators run **inside** `api` (adapter pattern + a dev endpoint to trigger callbacks). Settlement CSVs are generated to disk to simulate what a bank would send us — food for the recon engine in P7.

## 2. Modular monolith

Single Maven module; **packages are the module boundaries**. Why: money movement needs transactional integrity across wallet+ledger+fees — the strongest correctness story is one process, one DB, ACID for the core; microservice extraction happens later at event seams. (Interview answer exists in `interview-map.md`.)

| Module | Responsibility | Public surface (`.api`) |
|---|---|---|
| `platform` | shared kernel: Money, IDs, error model, outbox, idempotency helper, Kafka config, security primitives | used by all modules |
| `identity` | users, OTP auth, JWT, PIN, sessions, KYC tiers, roles | `IdentityApi`, events |
| `wallet` | wallet aggregate: status, balance projection, statements | `WalletApi` |
| `ledger` | chart of accounts, journals + postings, reversals, invariant checks | `LedgerApi`, `JournalPosted` events |
| `payments` | top-up, withdraw, P2P, provider adapters, transfer state machine, webhooks, recovery worker | `PaymentsApi` |
| `bills` | biller catalog, bill fetch, bill payment | `BillsApi` |
| `merchants` | merchant registry, QR encode/decode, merchant pay, settlement-lite | `MerchantsApi` |
| `fees` | fee rule catalog + calculator (pure domain) | `FeesApi` |
| `risk` | rule-based checks: velocity, thresholds, hold flags ⭐ | `RiskApi` |
| `notifications` | event consumers → in-app notifications + email stub | — (consumer only) |
| `reconciliation` | settlement ingestion, matching engine, mismatch reports | `ReconApi` |
| `backoffice` | ops/admin APIs: tx search, retry, freeze, recon views | REST only |

**ArchUnit rules (P0.4):** module A may access module B only via `com.payra.b.api..`; never `.internal`; `platform` is open to all; no cycles between modules.

## 3. Core data model

Grouped by module (all tables UUID PK, `created_at`/`updated_at` where mutable):

- **identity:** `users`, `kyc_profiles`, `sessions` (refresh-token hashes), `otp_challenges`, `pin_credentials` (hash, attempts, locked_until)
- **wallet:** `wallets` (user_id, currency, status, `balance_minor`, `reserved_minor`)
- **ledger:** `ledger_accounts` (owner_type, owner_id, purpose, currency), `journal_entries` (reference, type, status POSTED/REVERSED, reversal_of), `postings` (journal_id, account_id, direction, amount_minor) — immutable
- **payments:** `transfers` (type P2P/TOPUP/WITHDRAW/BILL/QR, status, amount/fee, refs, external refs, version), `transfer_events` (state history: from, to, reason, actor, at), `provider_payments` (transfer_id, provider, provider_txn_id, external_status, raw)
- **bills:** `billers` (category, code, fees), `bill_payments` (biller_id, customer_ref, bill_ref, status)
- **merchants:** `merchants` (status, qr payload seed), `settlement_batches`, `settlement_items`
- **fees:** `fee_rules` (type, flat_minor, rate_ppm, min/max, effective window)
- **limits:** `limit_rules` (tier, window DAY/MONTH, max_minor), `usage_counters` (user, window_key, consumed_minor)
- **platform:** `idempotency_records`, `outbox_events`, `processed_events` (consumer dedup), `audit_log`
- **recon:** `recon_runs`, `recon_items` (status, delta_minor, refs)
- **notifications:** `notifications` (user_id, type, payload, read_at)

## 4. The ledger (how money is represented)

- Every money movement = one `journal_entry` with **balanced postings**: `Σ debits = Σ credits`, enforced in the posting service + invariant tests.
- Postings are **immutable**; corrections are reversal entries (never UPDATE).
- Wallet balance is a **projection** (cache) over postings, updated in the same transaction; a recon/invariant check proves `balance_minor == Σ postings` at any time.
- Account purposes: `WALLET` (user liability), `PROVIDER_CLEARING` (asset per provider), `FEE_REVENUE`, `BILLER_PAYABLE`, `PAYOUT_CLEARING`, `SUSPENSE` (everything unexplained lands here until recon resolves it).

Journal templates:

| Flow | Debit | Credit |
|---|---|---|
| Add money (provider in) | `PROVIDER_CLEARING:bank` | `WALLET:user` |
| P2P send | `WALLET:sender` (amount + fee) | `WALLET:receiver` (amount) + `FEE_REVENUE` (fee) |
| Bill payment | `WALLET:user` | `BILLER_PAYABLE:biller` |
| Withdraw (payout out) | `WALLET:user` | `PAYOUT_CLEARING:bank` |
| Fee-only | `WALLET:user` | `FEE_REVENUE` |

Invariants (test-enforced): balanced entries; non-negative available balance; conservation (sum over all accounts of signed balance = 0); projection == postings; no postings on non-POSTED entries.

## 5. Transfer state machine & saga

```text
INITIATED ──> AWAITING_CALLBACK ──> COMPLETED
    │               │                   │
    │               v                   v
    └──> FAILED <───┘              REVERSED (post-completion correction)
         ^
RESERVED ──> EXECUTING ──> (withdraw: release on FAILED, consume on COMPLETED)
```

| Type | Flow | Notes |
|---|---|---|
| P2P | sync single-TX saga | limits → fee → ordered locks → journal → COMPLETED; failure = rollback, nothing half-done |
| TOPUP | async provider-in | INITIATED → AWAITING_CALLBACK; webhook or recovery worker resolves |
| WITHDRAW | async provider-out | reserve funds → EXECUTING → COMPLETED consumes reserve / FAILED releases |
| BILL/QR | sync saga to BILLER_PAYABLE | settlement batch later converts payable → payout ⭐ |

Compensation rules: never call providers inside DB transactions (saga, not 2PC); unknown provider state → stay in AWAITING_CALLBACK; recovery worker decides by age + querying provider; unresolved money sits in SUSPENSE, never silently dropped. Every transition appends to `transfer_events` (ops timeline).

## 6. Concurrency & idempotency

**Locking rules:** debit path takes `SELECT ... FOR UPDATE` on wallet rows; multi-wallet operations lock in **ascending wallet-id order** (deadlock avoidance); one (short) transaction per state step; optimistic `@Version` on `transfers`; workers scan with `SKIP LOCKED`.

**API idempotency protocol:** clients send `Idempotency-Key` on every write. First call inserts `idempotency_record(IN_PROGRESS)`; concurrent duplicate → `409 IDEMPOTENCY_IN_PROGRESS`; replay with same body hash → stored response returned (status + body); same key, different body → `422`; TTL 24h.

**Consumer dedup protocol:** delivery is at-least-once; effects are idempotent: `INSERT INTO processed_events(event_id, consumer) ...` in the same DB transaction as the effect; unique-violation = already processed → ack and skip. At-least-once delivery + idempotent effect = **effectively-once**. (This phrase is interview gold.)

## 7. Events & Kafka

Envelope (JSON, explicit, no class-name headers):

```json
{ "eventId": "uuid", "eventType": "transfer.completed", "version": 1,
  "occurredAt": "2026-10-07T12:00:00Z", "aggregateType": "transfer",
  "aggregateId": "uuid", "traceId": "…", "payload": { } }
```

| Topic | Key | Producers → Consumers |
|---|---|---|
| `payra.transfer.events.v1` | transferId | payments → notifications, archiving |
| `payra.ledger.events.v1` | journalId | ledger → notifications ⭐, future analytics |
| `payra.smoke.v1` | any | P0 wiring smoke test |
| `<topic>.dlq` | original | on retry exhaustion; ops replay documented |

Producer config: `acks=all`, idempotent producer, JSON serializer; local 1 broker, prod answer = 3 brokers + `min.insync.replicas=2`. **Publishing goes through the transactional outbox** (P6): events are written to `outbox_events` in the same DB transaction as the state change; a poller publishes and marks sent. Pre-P6 (P3–P5) we deliberately publish naively to experience the dual-write failure first-hand. Consumer-side retry: `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` with backoff; per-aggregate ordering via topic key.

## 8. Reconciliation

- Provider settlement file (CSV, daily): `provider_txn_id, type, gross_minor, fee_minor, status, settled_at`.
- Recon run: match each line against `provider_payments` + ledger `*_CLEARING` account postings for the day.
- Taxonomy: `MATCHED`, `AMOUNT_MISMATCH`, `FEE_MISMATCH`, `IN_LEDGER_ONLY` (we think it happened, provider disagrees), `IN_PROVIDER_ONLY` (provider moved money we don't know about), `DUPLICATE`.
- Run artifacts: `recon_runs` (counts, totals) + `recon_items` (row-level, drilldown in ops console). Internal invariant run: wallet projection vs postings; suspense aging report.

## 9. Security model

- **AuthN:** phone + OTP (hashed, 5-min TTL, attempt cap, Redis throttle) → JWT access (RS256, 15m) + refresh (rotating, hashed in `sessions`, 30d). FE keeps tokens in httpOnly cookies via route handlers.
- **PIN:** bcrypt (cost 12), 5 attempts → 15-min lock, separate from password hygiene; never logged.
- **AuthZ:** roles `CUSTOMER`, `OPS_ADMIN`; admin endpoints under `/api/v1/admin/**`; ops console itself behind role guard.
- **Data protection:** no PAN/card data stored (adapter handles tokens), NID masked (`••••1234`), full audit log of state-changing actions (actor, action, entity, before/after summary).
- **OWASP pass in P8.4:** rate limiting, input validation, mass-assignment, SSRF on adapters, secrets audit.

## 10. Performance notes

- Hot queries get indexes: `transfers(user_id, created_at desc)`, `postings(account_id, created_at desc)`, `notifications(user_id, read_at)`, `outbox_events(status, created_at)` partial on pending, `usage_counters(user_id, window_key)`.
- Balance reads hit the projection (O(1)); ledger sums are for verification, not the hot path. That tradeoff + its reconciliation guard is the story.
- Keyset pagination everywhere (never `OFFSET` on big tables).
- Load targets to fill in P8.1: send-money throughput & p95, contention behavior at N parallel senders, DB connection pool sizing evidence.

## 11. Error model (RFC 7807)

```json
{ "type": "https://payra.dev/errors/insufficient-funds",
  "title": "Insufficient funds", "status": 422, "code": "WALLET_INSUFFICIENT_FUNDS",
  "detail": "Available balance ৳450.00, required ৳500.00 + ৳5.00 fee",
  "traceId": "…" }
```

Code catalogue (grows per phase): `AUTH_OTP_*`, `AUTH_PIN_LOCKED`, `KYC_TIER_REQUIRED`, `WALLET_INSUFFICIENT_FUNDS`, `WALLET_FROZEN`, `LIMIT_EXCEEDED_DAILY|MONTHLY|PER_TX`, `IDEMPOTENCY_IN_PROGRESS|MISMATCH`, `TRANSFER_INVALID_STATE`, `PROVIDER_UNAVAILABLE`.

## 12. TODO (fills as phases run)

- [ ] P3: adapter interface + simulator details
- [ ] P6: outbox poller design (batch size, backoff, poison handling)
- [ ] P7: settlement CSV format final; recon SQL sketch
- [ ] P8: measured numbers + diagrams export
