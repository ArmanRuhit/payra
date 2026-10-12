# Interview Map — Payra

How to steer a Pathao (fintech backend) interview onto this project. Owned by the project owner; seeds get refined after each phase (P8.7 = final drill).

## 1. 60-second project pitch (draft; refine in P8)

> "I built Payra, a digital wallet platform modelled on Pathao Pay — Spring Boot modular monolith with a double-entry ledger at the core, Kafka for events, and a Next.js customer app plus an ops console. The interesting parts are the reliability mechanisms: every money movement is a balanced journal entry, all write APIs are idempotent, transfers are a state machine with compensation and a recovery worker, events go out through a transactional outbox, and the whole thing is audited by a reconciliation engine that catches discrepancies the way finance teams actually do. I can walk through any layer — the ledger, concurrency, or the event pipeline."

## 2. JD signal → artifact → talking point

| JD signal | Artifact in Payra | Talking point |
|---|---|---|
| Scalable, reliable payment services | `api/` module map + ArchUnit rules | modular monolith for transactional integrity; event seams = extraction path to services |
| Dependable transaction workflows | `payments` state machine, `transfer_events` | every state transition recorded; failure = defined transition; nothing half-done |
| Distributed systems | outbox + Kafka + consumers | dual-write problem; at-least-once + idempotent effects = effectively-once |
| Transaction-heavy performance | locking rules, indexes, k6 results | ordered row locks; cached balance + invariant recon; measured p95 (P8 numbers) |
| Design & maintain APIs | `/api/v1`, OpenAPI, problem+json | contract-first, versioned, machine-readable errors, idempotency keys |
| Fintech rigor | ledger, recon, suspense, audit | "money can't appear or disappear" — invariants + tests; recon taxonomy |
| Risk & compliance (context) | limits engine, KYC tiers, velocity ⭐ | rules as data; transactional counters; where fraud checks run |
| Ownership / ops | ops console, recovery worker, DLQ | "who fixes it at 3am?" — retry buttons, timelines, replay runbooks |
| Pathao Pay domain | top-up/P2P/bills/QR/settlement | speak their product language; adapter boundary for real PSPs |

## 3. Question bank

**Ledger & money**

1. Why double-entry instead of a `balance` column? — seed: audit trail, invariants (debits=credits), recomputable balances; balance column exists but only as a projection with a verification job. *(artifact: P2)*
2. Why minor units instead of BigDecimal/float? — float rounding errors compound; `long` poisha exact; BigDecimal when FX/multi-decimal currencies arrive. *(P2.1 + ADR)*
3. How do you detect money bugs? — conservation tests, randomized posting tests, projection-vs-ledger check, recon. *(P2.6)*
4. What is a suspense account and when does money land there? — unknown provider state; recon resolves it; never silently drop. *(P3–P7)*

**Concurrency**

5. Two parallel sends from the same wallet — what happens? — row `FOR UPDATE`; second waits; balance re-checked; test with N parallel threads asserts no overdraft. *(P4.5)*
6. Deadlock risk when A pays B and B pays A simultaneously? — lock wallets in ascending id order; retry on deadlock surfaced as conflict. *(P4.4)*
7. Optimistic vs pessimistic locking — when each? — optimistic `@Version` on transfers (rare conflicts); pessimistic on hot wallet rows (correctness-critical, short TXs). *(P4.4)*
8. Where do workers fit without starving the API? — `SKIP LOCKED` scans, small batches, separate pool. *(P4.6)*

**Idempotency & exactly-once**

9. Client double-taps "Send" — what prevents double debit? — `Idempotency-Key` + record states + response replay; concurrent duplicate → 409. *(P3.4)*
10. Kafka redelivers a `transfer.completed` — does the user get two notifications? — `processed_events` dedup insert in same TX as effect. *(P6.3)*
11. Is exactly-once possible? — at-least-once delivery + idempotent effects = effectively-once; container TX + dedup; don't chase magic. *(P6)*

**Saga, events, messaging**

12. Why not 2PC with the bank? — cross-org distributed transactions impractical; saga + compensation + recon instead. *(P3.2)*
13. Why the outbox pattern? — dual-write: DB commit + Kafka publish can't be atomic; outbox row commits with state change, poller publishes; demo of the failure it prevents. *(P6.2)*
14. How do you keep per-transfer event order? — Kafka key = transferId; same partition; partition-level ordering. *(P6.1)*
15. A consumer keeps crashing on one message? — bounded retries + backoff → DLQ + alert; poison test in suite; replay tooling. *(P6.5)*
16. Why is the naive `kafkaTemplate.send()` inside a `@Transactional` method dangerous? — the dual-write demo from P3→P6. *(P6.2)*

**Reconciliation**

17. Why is recon unavoidable? — no distributed TX across orgs; states drift; recon is the audit net. *(P7.2)*
18. Walk me through your mismatch taxonomy. — matched / amount / fee / in-ledger-only / in-provider-only / duplicate, each with an ops action. *(P7)*
19. How does ops resolve a mismatch? — console drilldown → timeline + ledger + provider raw → adjust via reversal or suspense release. *(P7.4–5)*

**Limits, fees, risk**

20. How are tier limits enforced without races? — `usage_counters` row locked in the transfer TX; boundary tests. *(P4.3)*
21. Fee engine design? — `fee_rules` as data (flat + rate + min/max, effective dating), pure calculator, posted to `FEE_REVENUE`. *(P4.2)*
22. Where would real fraud rules plug in? — `risk` consumer/module pre-commit; velocity rules → hold flag → ops queue. *(P6.4)*

**Security**

23. JWT tradeoffs and revocation? — short access + rotating refresh with hashed sessions; logout = revoke refresh. *(P1.3)*
24. PIN brute force? — bcrypt + attempt counter + time lock + rate limit; identical error for wrong PIN vs locked (don't leak). *(P1.4)*
25. PCI scope? — we never store PANs; adapter/tokenization boundary; minimizes scope. *(docs §9)*

**Architecture & performance**

26. Why modular monolith over microservices now? — transactional integrity of money; one deploy; enforced boundaries; extract via events when a module needs independent scaling (notifications, analytics first). *(docs §2)*
27. What breaks first at 10× traffic? — DB write hot spots & locks (single wallet), then Kafka consumers; mitigation: queueing per wallet, partitioning, workers. *(P8)*
28. Index design for the statement query? — `(account_id, created_at desc)`, keyset pagination; show EXPLAIN before/after. *(P8.2)*
29. Which metrics for payments? — success rate by type, p95 latency, stuck transfers, DLQ size, consumer lag, suspense balance. *(P8.3)*
30. Testing strategy? — unit domain, Testcontainers integration, concurrency suites, ArchUnit boundaries, Playwright smoke, k6 load. *(all phases)*

**JPA & transactions**

31. You changed a field but never called `save()` — why did the DB update? — dirty checking: the persistence context is a unit of work; managed entities are compared against their load-time snapshot at flush (usually commit) and changed ones emit UPDATEs. Traps: detached changes silently vanish; auto-flush before queries; Hibernate updates all mapped columns by default; this is also why `open-in-view=false`. *(OtpService P1.2)*
32. How do you persist an audit/failure record when the surrounding operation fails? — independent commit: `REQUIRES_NEW` (suspends caller TX, commits on its own) + `noRollbackFor=PayraException` (default rules would roll back on our own throw). Requires: one TX has one fate — inner rules can't save a doomed outer TX; `NESTED` is savepoints, not independent commit; costs: second connection + deadlock risk against locks held by the suspended TX; alternatives: after-commit hooks, outbox. *(OtpService.verify P1.2)*

## 4. Demo script (5 minutes; rehearse in P8.7)

1. Landing → login with phone; OTP from API log; set PIN
2. Dashboard → Add Money → webhook arrives → balance + ledger trail visible
3. Send Money: fee + limit preview → PIN → receipt; open kafka-ui: `transfer.completed` on topic
4. Run the concurrency test live: parallel transfers, no overdraft, sum conserved
5. Ops console: transfer timeline + transitions + ledger + retry action
6. Inject provider-only transaction → recon run catches it → drilldown
7. Close with k6 numbers + metrics

## 5. Numbers to memorize (fill during P8)

| Metric | Value |
|---|---|
| Send-money throughput (local k6) | TBD |
| p95 send latency | TBD |
| Parallel-transfer test: N senders, drift | TBD |
| Test counts (unit/integration) | TBD |
| Ledger invariant randomization iterations | TBD |

## 6. Honest weak spots (fill during P8)

- Local single-broker Kafka; prod = replication + ISR, replay ops runbook.
- Mock providers; real integration adds certification cycles, timeouts, HSMs.
- No FX/multi-currency; ledger supports it conceptually but untested.
