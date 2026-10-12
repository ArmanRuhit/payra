# Sprint: Oct 12–15, 2026 — "Interview Core"

**Goal:** a demo-able money platform by **Thu Oct 15 EOD**: login → add money → send money → history, all flowing through a double-entry ledger, proven by a concurrency test. Everything else resumes after the sprint with its design documented (so it stays an interview answer, not a hole).

**Pace rule:** the dates below are a priority map, not a whip. Understanding outranks the calendar — if the core needs more days, it gets them.

**Cut-line rule:** every day, if work overruns, cut from the **bottom** of the next day's list — never from the ledger or the transfer core.

---

## In scope (the core story)

1. **Auth**: phone + OTP → JWT access token (HS256 dev secret; RS256 documented as prod posture)
2. **Ledger**: accounts + journals + postings (double-entry) + wallet balance projection + statement
3. **Add money**: mock bank provider, webhook simulation, `Idempotency-Key`
4. **P2P send**: PIN confirm, tier limit-lite, ordered row locks, concurrency + conservation tests
5. **Web UI**: login, dashboard (balance + history), send, add money

## Out of scope (post-Oct-15 backlog — designs stay in docs/)

- P5 bills & merchant QR · P6 Kafka/outbox · P7 reconciliation + ops console · P8 load/security hardening
- Fee engine (P2P free, Pathao-style), KYC profiles beyond a tier column, refresh tokens, stuck-transfer worker, settlement, risk-lite, Redis OTP *rate limiting* (attempt cap only)

---

## Mon Oct 12 — Identity (backend)

- [ ] P1.1 tail: `UserRepository` + `UserRepositoryTest` (Testcontainers)
- [ ] P1.2 OTP-lite: `V2__create_otp_challenges.sql`; request/verify service (hashed codes, 5-min TTL, attempt cap); tests
- [ ] P1.3 JWT: `SecurityConfig` (permit `/auth/**`, health, api-docs); Nimbus token service; endpoints: `POST /api/v1/auth/otp/request`, `POST /api/v1/auth/otp/verify` (find-or-create → token), `GET /api/v1/me`
- **Done when:** curl register → login → `/me` works; `./mvnw test` green

## Tue Oct 13 — Identity (finish) + first UI

- [ ] P1.4 PIN: set + verify (bcrypt, attempt lockout)
- [ ] P1.5-lite: `V3__add_kyc_tier.sql` (`UNVERIFIED|TIER_1|TIER_2`) + tier on `/me`
- [ ] P1.6-lite web: session via route handler (httpOnly cookie); login page (phone → OTP); dashboard shell (balance placeholder + `/me` + health)
- [ ] Commit + push
- **Done when:** log in from the browser; tier visible

## Wed Oct 14 — Ledger core (the heart)

- [ ] P2.1 `Money` value object (minor units) + unit tests
- [ ] P2.2 `V4__create_ledger.sql`: `ledger_accounts`, `journal_entries`, `postings` + system account seed; wallet account created at registration
- [ ] P2.3 `PostingService`: balanced-entry guard, immutability + invariant tests
- [ ] P2.4 Wallet projection (balance cache) updated in the same transaction
- [ ] P2.5 Balance + statement API
- [ ] P2.6 Reversal + randomized conservation test
- **Done when:** suite green; fund a wallet in a test; statement shows exact entries

## Thu Oct 15 — Money moves + UI + demo

- [ ] P3-lite: mock bank top-up (state-lite, webhook sim endpoint, idempotency keys)
- [ ] P4-lite: P2P saga (ordered locks, PIN confirm, tier limit-lite) + **concurrency test** (parallel sends: no overdraft, sum conserved)
- [ ] Web: send money flow + add money flow + transaction history
- [ ] Demo polish: README quickstart, 3-min demo script, push everything
- **Done when:** UI end-to-end: login → add money → send → history shows both, via the ledger

---

## Daily rhythm (sprint mode)

1. Slices still arrive **one file per message**; teach-back quizzes stay — 3 quick questions, understanding outranks the calendar
2. After each slice: `./mvnw test` (API) or `bunx tsc --noEmit && bun run build` (web)
3. Commit per slice; **push at least once every day**
4. Status: update `CLAUDE.md` progress after each day, not each slice
