# Glossary — Payra

Vocabulary the owner must be able to say out loud (interview-ready one-liners).

| Term | Meaning |
|---|---|
| **Wallet** | User-facing balance; in the ledger it's a liability account the platform owes the user. |
| **Ledger** | Book of record; append-only journal entries + postings describing every money movement. |
| **Journal entry** | One balanced transaction (a group of postings where debits = credits). |
| **Posting** | Single debit or credit line against one account; immutable. |
| **Double-entry** | Every movement touches ≥2 accounts; debits must equal credits; detects creation/destruction of money. |
| **Projection (cache)** | Derived read model (e.g. wallet `balance_minor`) kept consistent with the source of truth and verified by invariant checks. |
| **Invariant** | A truth that must always hold (e.g. Σ debits = Σ credits); enforced by code + tests. |
| **Conservation** | Money is neither created nor destroyed — sum of all account balances stays constant. |
| **Clearing account** | Internal ledger account holding money mid-flight with a partner/provider. |
| **Suspense account** | Parking account for money in an unclear state; resolved by ops/recon, never ignored. |
| **Reversal** | A compensating journal entry that cancels a previous one (never UPDATE rows). |
| **Reconciliation** | Comparing our ledger against provider/bank statements to find discrepancies. |
| **Settlement** | The actual movement of funds between institutions after transactions (often T+1); distinct from authorization. |
| **MFS** | Mobile Financial Service (e.g. bKash, Nagad) — a partner provider category. |
| **PSP** | Payment Service Provider — a partner that processes payments. |
| **KYC** | Know Your Customer — identity verification; we model tiers (unverified → tier1 → tier2) gating limits. |
| **Limits / velocity** | Caps on amounts per transaction/day/month and on transaction frequency; anti-fraud basics. |
| **Idempotency** | Same request twice → same effect once; the API-safe retry property. |
| **Idempotency key** | Client-supplied unique key enabling safe retries of writes. |
| **At-least-once** | Kafka delivery guarantee: messages may be redelivered → consumers must dedup. |
| **Effectively-once** | At-least-once delivery + idempotent processing = each business effect happens once. |
| **Outbox pattern** | Write events to a DB table in the same transaction as the state change; a poller publishes them to Kafka — kills the dual-write problem. |
| **Dual-write problem** | DB commit and message publish are two systems; either can fail alone → lost or phantom events. |
| **Saga** | Multi-step distributed workflow with compensations instead of a global transaction. |
| **Compensation** | An action that undoes a previously completed step (e.g. release reserved funds). |
| **DLQ** | Dead-letter queue/topic where poison messages land after failed retries, for ops inspection/replay. |
| **Reserve / hold** | Funds earmarked but not yet spent (withdraw in flight); `available = balance − reserved`. |
| **Minor units** | Amounts stored in the smallest currency unit (poisha for BDT: 1 taka = 100 poisha). |
| **BNPL** | Buy Now Pay Later — adjacent fintech product (not in scope; vocabulary only). |
| **EMV QR** | Standardized QR payment payload format used at merchants (simplified in Payra). |
| **NPSB / BEFTN / INSTAPAY** | Bangladeshi interbank rails Pathao Pay integrates with; we mock the concept via adapters. |
| **Keyset pagination** | Paging by `(created_at, id)` cursor instead of `OFFSET` — stable and fast on large tables. |
| **Optimistic lock** | Detect concurrent modification via version column; retry on conflict. |
| **Pessimistic lock** | `SELECT ... FOR UPDATE` — block concurrent writers while holding the row for a short transaction. |
| **Deadlock** | Cycle of locks; prevented by acquiring locks in a deterministic order (ascending id). |
| **Trace ID** | Correlation id propagated across HTTP + Kafka for end-to-end debugging. |
| **Audit log** | Record of who did what, when, to which entity — for compliance and forensics. |
| **PCI DSS** | Card-industry security standard; minimized in Payra by never storing PANs. |
