# CLAUDE.md — Payra: Digital Wallet & Payments Platform

> **Read me first.** Single source of truth for `projects/payra/`: mission, JD analysis, architecture, conventions, pairing workflow, roadmap, and the live progress log. `AGENTS.md` defers here.
> Vault-level rules live in `/desk/e/ruhit/CLAUDE.md`. **Exception:** the vault default "CodeCrafters mode" (hints before code) is *superseded* here by the **pair-typing mode** in §7 — the owner wants full code per slice to type by hand.

Last updated: 2026-10-11 · Status: **Resumed at P0.5 (frontend skeleton) — interview lands after full build; pacing at max**

## 1. Mission & success criteria

Build an interview-grade fintech platform inspired by **Pathao Pay** (wallet + payments + ops backoffice) to:

1. move a live interview onto this project — Pathao, **Software Engineer (Backend), Fintech Engineering**, posted 2026-10-04, applications close **2026-10-17**;
2. prove fintech-engineering depth with working artifacts, not buzzwords.

Success criteria:

- [ ] Runs locally with documented commands (infra + api + web)
- [ ] 8 killer demo flows work end-to-end (§3.3)
- [ ] Owner can whiteboard: ledger, idempotency, concurrency, saga, outbox, recon, limits/fees, security
- [ ] `docs/interview-map.md` complete; 5-min demo rehearsed; mock interview passed
- [ ] ≥95% of code hand-typed by the owner

Non-negotiables: correctness over features; every mechanism has a test; every decision has a one-line "why" the owner can say out loud.

## 2. JD analysis — what the interviewer will probe

Source: [Pathao careers post](https://careers.pathao.com/blog/jobs/software-engineer-backend/) (detail form is login-walled; role intent below is inferred from the post + Pathao Pay's public product + comparable fintech JDs).

| JD signal | What it really means | Where this project answers it |
|---|---|---|
| "scalable, reliable services for digital payments" | service boundaries, stateless API, failure isolation | modular monolith + ArchUnit boundaries + extraction path (§4) |
| "dependable transaction and payment workflows" | state machines, retries, compensation, no lost money | transfer state machine + saga + recovery worker (P3–P4) |
| "work with distributed systems" | async messaging, at-least-once delivery, ordering | Kafka backbone + outbox + DLQ (P6) |
| "performance challenges in a transaction-heavy environment" | locking, indexes, hot rows, measurement | locking strategy + k6 load tests + EXPLAIN work (P8) |
| "design and maintain backend services and APIs" | API contract discipline | versioned REST, OpenAPI, RFC7807 errors, idempotency keys (§9) |
| Role context: Pathao Pay | wallet/MFS domain: top-ups, P2P, bills, QR, limits, KYC | product scope (§3) |
| Fintech rigor (unstated but expected) | double-entry, reconciliation, audit | ledger core + recon engine + audit trail (P2, P7) |

Interview strategy: **every experience question gets answered with "here's how I built X in Payra"** — the full mapping is in `docs/interview-map.md`.

## 3. Product scope

### 3.1 Pathao Pay reference → Payra scope

| Pathao Pay feature | Payra | Phase |
|---|---|---|
| Add Money (bank/card/MFS) | mock provider adapters + webhook flow | P3 |
| Withdraw (bank/MFS) | mock payout with funds reserve | P3 |
| Send Money (P2P, free) | full saga + limits + PIN | P4 |
| Pay Bill (electricity/gas/water) | biller catalog + bill fetch + pay | P5 |
| Make Payment (merchant QR) | QR payload + merchant pay + settlement-lite | P5 |
| Mobile recharge | biller category (cheap re-use) | P5 |
| Pay Tag (get paid by handle) | recipient resolution by @tag | P4 |
| KYC (NID + selfie) | KYC tiers gating limits (mock verify) | P1 |
| PIN + 2FA | PIN + OTP login + attempt lockout | P1 |
| Split Pay, Group Send, Auto-Pay, Pay Card | **stretch** / talk-track only ⭐ | P8 |

### 3.2 Non-goals (and the honest interview answer)

Real PSP/bank integration, real KYC vendor, PAN storage/PCI scope, multi-currency/FX, card issuing, ATM cash-out.

> Answer template: "deliberately mocked at the adapter boundary — here's the interface a real integration plugs into, and the corner cases I designed for: timeouts, unknown states, duplicates, reconciliation."

### 3.3 Killer demo flows (rehearsed in P8)

1. Onboard: phone OTP → PIN → KYC tier
2. Add money via mock bank (webhook arrives) → ledger entry visible
3. Send money with fee + limit checks + PIN → receipt + Kafka event + notification
4. Concurrency proof: parallel transfers test — no overdraft, money conserved
5. Pay a bill; pay via merchant QR
6. Recon run catches an injected provider-only transaction
7. Ops console: transaction timeline, stuck-tx retry, user freeze
8. k6 load numbers + metrics

## 4. Architecture at a glance

```text
customer app (Next.js 16)        ops console (/admin, Next.js)
        |                                |
        |   httpOnly cookie session; server components + route handlers
        v                                v
   Payra API — Spring Boot 4.1 modular monolith (Java 21)
   identity | wallet | ledger | payments | bills | merchants
   fees | risk | notifications | reconciliation | backoffice
        |                    |                    ^
        v                    v                    |
 PostgreSQL 18 (truth)   Redis 8 (throttle)   Kafka (events)
```

- **Module rules:** cross-module access only via `.api` packages; internals in `.internal`; enforced by ArchUnit; async (Kafka) by default across modules.
- **Topics:** `payra.transfer.events.v1`, `payra.ledger.events.v1` (+ `.dlq` variants), `payra.smoke.v1` (P0 wiring test).
- **Key mechanisms:** double-entry ledger; balance projection + invariant checks; API idempotency keys; consumer dedup; transfer state machine + transitions history; saga with compensation; ordered row locks; transactional outbox; recon engine; fee & limit engines; JWT + PIN security; audit log.

Full flows, data model, ledger templates, Kafka design: `docs/architecture.md`.

## 5. Tech stack (pinned)

| Layer | Choice | Notes |
|---|---|---|
| Backend | Java 21, Spring Boot **4.1.x**, Maven 3.9, Lombok | Boot 3.5 is EOL (Jun 2026) — docs-first for 4.x APIs |
| Data | PostgreSQL 18, Flyway | no `ddl-auto`; migrations only |
| Cache/ratelimit | Redis 8 | OTP throttle, rate limits |
| Messaging | Kafka 4.x (KRaft, Docker), spring-kafka | 1 broker locally; 3-broker answer for prod |
| API docs | springdoc-openapi (3.x for Boot 4 — verify at P0.4) | swagger-ui in dev profile |
| Security | Spring Security 7, Nimbus JWT (RS256) | access 15m + rotating refresh |
| Backend tests | JUnit 5, AssertJ, Testcontainers (PG + Kafka), ArchUnit | concurrency + invariant suites |
| Frontend | Next.js **16.4** App Router, TypeScript, Tailwind **4.3**, shadcn/ui | check `node_modules/next/dist/docs/` first |
| FE checks | `tsc --noEmit`, eslint, build; Playwright smoke (P8) | |
| Infra | Docker Compose: `postgres:18`, `redis:8`, `apache/kafka:4.x`, `kafka-ui` | ports 5432/6379/9092/8090; api 8080, web 3000 |
| Load test | k6 via `grafana/k6` docker image | P8 |

## 6. Target repo layout

```text
projects/payra/
├── CLAUDE.md            # this file
├── AGENTS.md
├── docs/
│   ├── architecture.md  # flows, models, mechanisms
│   ├── interview-map.md # JD → artifact → talking points + Q&A bank
│   ├── glossary.md      # vocabulary for this project
│   └── adr/             # decision records (from P2.7)
├── infra/               # docker-compose.yml, k6/ (P0.2, P8)
├── api/                 # Spring Boot — single Maven module, packages = modules
└── web/                 # Next.js — customer app + /admin ops console
```

## 7. Working agreement — pair-typing mode

The owner learns by typing every line by hand. Per slice, the agent posts in chat:

1. **Goal** — what we build + why it matters in the interview (2–3 lines)
2. **Concepts** — short explainer + links to official docs
3. **Code — ONE FILE PER MESSAGE.** The agent posts a single file, then stops. The owner types it, replies "next", and only then does the next file arrive. Code comments are terse: "why", not "what" (see §7.1).
4. **Verify** — exact commands + expected output (slice acceptance)
5. **Quiz** — 3 questions the owner answers after it runs (fundamental / tradeoff / failure mode)

Rules:

- The agent **never creates files** under `api/`, `web/`, `infra/` — code lives in chat; the owner types it. (Agent may create/update docs in `docs/` and progress in this file.)
- **Modifications are diffs.** Once a file is typed, every change arrives as a unified diff or a minimal before/after snippet — never a full-file repost.
- Owner may ask "explain" anytime; may request **attempt-first mode** for a slice when feeling confident.
- A slice is *done* when: typed, verification green, quiz answered, committed.
- After each slice: agent updates §11 + affected docs, then proposes the next slice.
- Ambiguity → ask; never guess silently.

### 7.1 Communication style — i-have-adhd (always on)

<!-- BEGIN:i-have-adhd -->
This project runs with the `i-have-adhd` output style (skill source: `~/.config/opencode/vendor/i-have-adhd/skills/i-have-adhd/SKILL.md`). Every agent response follows its ruleset:

- **Lead with the next action.** First line is something the owner can do; context after, if at all. No preamble, no recap, no closing pleasantries.
- **Number multi-step work.** One bounded action per step; fold trivial steps into the one before.
- **Restate state every turn** — slice, file X of Y, next action. The reader cannot hold state between messages.
- **End with ONE action doable in under two minutes.** Concrete time estimates, never "a bit of work".
- **Cap visible lists at 5 items**; park tangents, surface them once at the end.
- **Matter-of-fact errors:** cause + fix, no "uh oh".
- **Make wins visible:** say what now works, concretely.

Turn off only on "stop adhd mode" or "normal mode". Repo conventions always outrank the style.
<!-- END:i-have-adhd -->

## 8. Roadmap

Pace target: ~2 slices/day; each phase ends **green and demo-able**. Interview-safe stop points: after **P5** (product complete) and **P6** (async story complete). ⭐ = stretch/cuttable.

### Phase 0 — Foundation (infra + skeletons run locally)

- [x] P0.1 Repo init: folders, `git init`, README, .gitignore, .editorconfig
- [x] P0.2 `infra/docker-compose.yml`: Postgres 18, Redis 8, Kafka 4 KRaft, kafka-ui; verified up
- [ ] P0.3 Backend skeleton (start.spring.io — deps listed); explain generated files; run + actuator health
- [ ] P0.4 Backend baseline: `com.payra.<module>` packages, problem+json errors, springdoc, Testcontainers base test, ArchUnit ruleset
- [ ] P0.5 Frontend skeleton (create-next-app: TS + Tailwind + App Router); theme tokens; layout shell
- [ ] P0.6 FE↔BE first contact: typed API client, env config, home page shows API health

Exit: `docker compose up -d`, `mvn spring-boot:run`, `bun run dev` all green and documented.

### Phase 1 — Identity & Access (KYC-lite)

- [ ] P1.1 identity: user model + Flyway migration + repository
- [ ] P1.2 OTP-lite: request/verify, hashed OTP, Redis throttle, tests
- [ ] P1.3 JWT auth: Security config, RS256 tokens, refresh rotation, tests
- [ ] P1.4 PIN: bcrypt, attempt counter + lockout, tests
- [ ] P1.5 KYC tiers: profile, mock verify, tier assignment, tests
- [ ] P1.6 FE auth: phone → OTP → PIN screens; httpOnly cookie session via route handlers; route guard

Exit: register/login from the UI; KYC tier visible.

### Phase 2 — Ledger Core (the heart)

- [ ] P2.1 Money value object (minor units) + currency rules + unit tests
- [ ] P2.2 Chart of accounts + system account seeds
- [ ] P2.3 Journal + posting model; posting service with balanced-entry guard; invariant tests
- [ ] P2.4 Wallet projection (balance cache) updated in the same TX as postings
- [ ] P2.5 Balance + statement APIs; keyset pagination
- [ ] P2.6 Reversal flow + audit trail + randomized conservation tests
- [ ] P2.7 ADRs (money, ledger design) + minimal FE wallet page (balance + statement)

Exit: fund system account → journal → wallet balance + statement; invariant suite green.

### Phase 3 — Add Money & Withdraw (provider integration)

- [ ] P3.1 Provider adapter interface + mock bank/MFS/card (latency + failure modes)
- [ ] P3.2 Top-up saga: `transfer` table + state machine + `POST /topups`; naive direct Kafka publish (fixed in P6 — deliberate teaching arc)
- [ ] P3.3 Webhook receiver: signature-lite, provider txn refs, duplicate-callback handling, tests
- [ ] P3.4 Idempotency framework for write APIs + concurrent-duplicate tests
- [ ] P3.5 Withdraw: reserve/release semantics + tests
- [ ] P3.6 FE: Add Money flow, Withdraw flow, transaction detail timeline

Exit: add money end-to-end in UI via simulated webhook; idempotency tests green.

### Phase 4 — P2P Send Money (flagship)

- [ ] P4.1 Directory: recipient resolve by phone/@PayTag + beneficiary model
- [ ] P4.2 Fee engine: rule catalog + calculator + tests
- [ ] P4.3 Limits engine: tier caps + transactional usage counters + tests
- [ ] P4.4 Transfer saga: ordered locks, PIN confirm, journal posting, idempotent endpoint
- [ ] P4.5 Concurrency & conservation test suite (parallel sends, no overdraft)
- [ ] P4.6 Recovery worker for stuck transfers + ops-visible states
- [ ] P4.7 FE: Send flow (steps, fee/limit preview, PIN modal, receipt)

Exit: two users; money moves; invariants hold under parallel load.

### Phase 5 — Bills & Merchant QR

- [ ] P5.1 Bills: biller catalog seeds + bill fetch (mock) + tests
- [ ] P5.2 Bill pay flow (fee + state machine reuse)
- [ ] P5.3 Merchants: registry, QR encode/decode, merchant pay
- [ ] P5.4 ⭐ Settlement-lite: T+1 batch job, payables → payouts, tests
- [ ] P5.5 FE: bill pay flow, QR pay page (camera + manual payload)

Exit: pay a bill and a merchant QR; settlement batch output inspectable. **← interview-safe stop point**

### Phase 6 — Kafka Backbone & Async Reliability

- [ ] P6.1 Topic/envelope conventions + producer config (`acks=all`, idempotence)
- [ ] P6.2 Transactional outbox: table + poller → Kafka; refactor P3.2; **dual-write failure demo**
- [ ] P6.3 Notifications consumer: in-app notifications + dedup table; tests
- [ ] P6.4 ⭐ Risk-lite consumer: velocity rules → hold flags
- [ ] P6.5 Retry/DLQ strategy + poison-event tests + consumer metrics

Exit: kafka-ui shows healthy flow; kill-consumer demo shows no lost events; DLQ replay documented. **← interview-safe stop point**

### Phase 7 — Reconciliation & Ops Console

- [ ] P7.1 Provider settlement files (CSV) + external-refs model
- [ ] P7.2 Recon engine: ledger clearing vs settlement matching; mismatch taxonomy; tests
- [ ] P7.3 Ops APIs: tx search/timeline/retry, freeze user, recon runs (ADMIN authz)
- [ ] P7.4 FE console: transaction monitor + detail timeline + retry/freeze actions
- [ ] P7.5 FE console: recon runs + mismatch drilldown

Exit: injected provider-only transaction is caught and visible in the console.

### Phase 8 — Hardening, Performance & Interview Prep

- [ ] P8.1 k6 load tests (send-money hot path) + results documented
- [ ] P8.2 Perf pass: hot-path indexes, EXPLAIN ANALYZE lesson, connection-pool tuning
- [ ] P8.3 Observability: business metrics (success rate, p95, stuck count, DLQ size, consumer lag)
- [ ] P8.4 Security pass: rate limits, token rotation, OWASP checklist, secrets audit
- [ ] P8.5 Playwright smoke of the killer flow
- [ ] P8.6 Docs polish: diagrams, README, 5-min demo script
- [ ] P8.7 Interview drill: finalize Q&A bank, mock interview, rehearse demo

Exit: interview-ready.

## 9. Conventions

- **Money:** `long` minor units + `Money` value object; BDT (1 taka = 100 poisha); never float/double; fee rounding policy ADR before fees ship.
- **IDs:** UUID v4 PKs (app-generated); user-facing transfer references (`TRF-...`); v7 locality tradeoff noted in ADR.
- **Packages:** `com.payra.<module>` with `.api` (public) / `.internal` (private); ArchUnit enforces. Profiles: `dev` (default), `test`.
- **DB:** snake_case, plural tables; migrations `V<n>__snake_desc.sql`; indexes `ix_*`; constraints `ck_*`; `gen_random_uuid()`.
- **API:** `/api/v1/...`; RFC 7807 problem+json with `code` (catalogue in `docs/architecture.md`); `Idempotency-Key` on writes; keyset pagination (`?cursor=&limit=`).
- **Events:** envelope `{eventId, eventType, version, occurredAt, aggregateType, aggregateId, traceId, payload}`; Kafka key = aggregateId; payload semantics never change — new version instead.
- **Time:** store UTC (`Instant`); render `Asia/Dhaka` in UI.
- **Security:** no secrets in git; PIN/OTP never logged; tokens short-lived; roles `CUSTOMER`, `OPS_ADMIN`.
- **Tests:** every slice ships tests; wallet-mutating code needs a concurrency test; ledger needs invariant tests; `mvn verify` + `bun run build` green before commit.
- **Lombok (adopted P0.4b, owner request):** targeted `@Getter`/`@Setter`/`@Builder` only; prefer records for DTOs (no annotation needed); never `@Data`/`@EqualsAndHashCode`/`@ToString` on JPA entities — lazy proxies and mutable identity make generated equality a bug factory.
- **Commits:** conventional (`feat(ledger): ...`), one slice per commit.
- **Docs-first:** Boot 4.x and Next 16 differ from older tutorials — verify against official docs (`docs.spring.io/spring-boot/4.1/`, `node_modules/next/dist/docs/`, `kafka.apache.org/documentation/`) before writing code in a new area.

## 10. Commands (updated as slices land)

```bash
# infra
cd infra && docker compose up -d          # P0.2+
# backend
cd api && mvn spring-boot:run             # P0.3+
mvn verify                                # tests (needs Docker)
# frontend
cd web && bun install && bun run dev      # P0.5+
bunx tsc --noEmit && bun run build
# load (P8)
docker run --rm -i --network host grafana/k6 run - < infra/k6/send-money.js
```

## 11. Progress log

### 2026-10-07 — Planning

- JD studied (closes 2026-10-17); Pathao Pay feature set researched from public sources.
- Decisions: 3–4 week full plan · customer app + ops console · **Kafka from day one** (P3 naive publish → P6 outbox refactor as a teaching arc) · pair-typing mode · owner somewhat new in both stacks → small slices, heavier explainers.
- Name exploration: 15+ candidates collision-checked against real fintech products (KhataPay, PayLoom, Kosh, TakaFlow, Mohur, Kori, Keel, Trellis, Ingot, Daybook all busy; Coffer/Assay clean but unused). Owner decision: **keep "Payra"** — locked 2026-10-07.
- Created: `CLAUDE.md`, `AGENTS.md`, `docs/architecture.md`, `docs/interview-map.md`, `docs/glossary.md`.
- Workflow refinement (owner request): **i-have-adhd** output style always on (§7.1); slices delivered **one file per message**; typed files modified via **diffs only**.
- Next: **P0.1** repo init.

### 2026-10-07 — P0.1 complete

- Repo scaffold committed: `80736db chore: repo scaffold (readme, gitignore, editorconfig)`.
- Quiz: 3/3 reviewed — gap: formatter vs editorconfig (explained); sharpened: secret incident response (rotate → rewrite → ignore), `-b main` names the first branch.
- README eyebrow softened: "modeled on existing payment platforms" (public-face hygiene).
- Next: **P0.2** — infra (docker-compose: Postgres 18, Redis 8, Kafka 4 KRaft, kafka-ui).

### 2026-10-07 — P0.2 complete

- Infra up & verified: PostgreSQL 18.6, Redis `PONG`, Kafka 4.3.1 KRaft (`smoke-check` topic created), kafka-ui showing `payra-local` ONLINE (controller: KRAFT).
- Review rounds caught 6 typos before first run (`=` vs `:`, `container-name`, `kakfa`, wrong Kafka props, kafka-ui var names); `docker compose config` now validates clean.
- Quiz: Q1/Q2 (dual listeners, KRaft) explained; Q3 (dev-only credential vs secret store) covered inline.
- Next: **P0.3** — Spring Boot skeleton via start.spring.io.

### 2026-10-07 — P0.3 complete

- Skeleton generated via start.spring.io (Boot 4.1.1); initial package `com.payra.api` — superseded by P0.4a below.
- Review fixed one real bug: `data-jdbc` → `data-jpa` (artifact `spring-boot-starter-data-jpa-test:4.1.1` verified on Maven Central). Boot 4 naming learned: `webmvc`/`flyway`/`kafka` starters, per-starter `-test` modules, Testcontainers 2.x ids.
- `application.properties`: datasource, `ddl-auto=validate`, `open-in-view=false`, kafka, actuator — with explanatory comments.
- `curl /actuator/health` → `status: UP` (`db` + `redis 8.10.2`).
- Quiz 2.5/3 — Maven-vs-JDK axis corrected; "pool exhaustion" wording fixed.
- Next: **P0.4** — backend baseline (restructure → error contract → springdoc → ArchUnit).

### 2026-10-07 — P0.4a complete: restructure to `com.payra`

- App moved to `com.payra.PayraApplication`; test trio moved (`PayraApplicationTests`, `TestPayraApplication`, `TestcontainersConfiguration` with pinned tags `apache/kafka-native:4.3.1`, `postgres:18`, `redis:8`); old `com.payra.api` deleted.
- `./mvnw test` green — full context boots against Testcontainers (TC 2.0.5; Postgres 18.6, Redis 8, Kafka 4.3.1-native) in 59 s first run (image pulls).
- Benign warnings noted: Flyway "no migrations found" (expected until P1.1); Mockito self-attach deprecation (revisit P8).
- Convention change (owner request): **Lombok adopted** — accessors via `@Getter`/`@Setter`; owner-added pom fixed (invalid `annotationProcessor` scope → optional + fat-jar exclude).
- Next: P0.4b — error contract (`ErrorCode` catalogue + `PayraException` + RFC 7807 handler).

### 2026-10-07 — Wrap-up (end of day)

- **Done:** error contract typed — `ErrorCode` (catalogue), `PayraException` (base exception), `GlobalExceptionHandler` (RFC 7807), plus `spring.mvc.problemdetails.enabled=true`; Lombok adopted (§5 + §9 updated); suite green (`./mvnw test` → context test passed, 6.2 s with cached containers).
- **Pending in P0.4b:** `GlobalExceptionHandlerTest` (file 5 of 5 — code in chat history) + test run + quiz.
- **Resume checklist (next session):**
  1. Type `GlobalExceptionHandlerTest` (ask for a re-post if needed)
  2. `cd api && ./mvnw test` → expect **5 green**
  3. Commit: `test(platform): cover error contract`
  4. **P0.4c:** one `pom.xml` diff — springdoc-openapi `3.1.1`, ArchUnit `1.5.1`, metadata touch-up
  5. **P0.4d:** architecture-boundary test → P0.4 closes → P0.5 (frontend skeleton)
- Reviews caught today: 9 compose-file typos; wrong JPA starter (`data-jdbc` vs `data-jpa`); wrong `MethodArgumentNotValidException` import (messaging vs `web.bind` — silent 500s on validation errors); missing URI slash; `annotationProcessor` (not a Maven scope).

### 2026-10-08 — P0.4b complete: error contract tested

- `GlobalExceptionHandlerTest` committed (`a6e2e83 test(platform): cover error contract`) — standalone MockMvc; 5 tests in 0.18 s; suite **6/6 green**.
- Contract locked by tests: exact `type` URL, always-present `code`, `detail` omitted when equal to `title`, and 500s leaking nothing (fake `password=hunter2` probe stays server-side).
- Review: 2 Javadoc typos caught + one expected-noise question resolved (test 5's ERROR trace is by design — handler logs, response stays clean).
- Quiz: Q1 sharpened (standalone vs full-context MockMvc); Q2/Q3 taught — detail-omission tradeoff, why 500 bodies never echo `getMessage()` (re-derive before interview).
- Env note: `git status` may print `ok` on a clean tree (owner's `rtk` output filter) — benign, not a stray file.
- Next: **P0.4c** pom diff (springdoc `3.1.1`, ArchUnit `1.5.1`, metadata) → **P0.4d** boundary test.

### 2026-10-08 — P0.4c complete: API docs + ArchUnit on board

- Commits: `7c7d871 docs: close P0.4b (progress log, error URL)` then `e3a64ae chore(api): add springdoc + ArchUnit` — tree clean.
- `pom.xml`: springdoc-openapi `3.1.1` (runtime) + archunit-junit5 `1.5.1` (test), versions pinned via properties — neither is in the Boot BOM, so Boot does not manage them.
- Verified on 8080: `/v3/api-docs` → OpenAPI 3.1 JSON (`paths` empty until P1); `/actuator/health` → `UP` (db, redis 8.10.2, disk, ssl); swagger-ui → 200. Suite 6/6.
- Port incident: Trakwise frontend moved `8080:80` → `8082:80` in `/desk/jobs/projects/Trakwise/docker-compose.yml` (`36c6eda`); stack recreated from its real path (Docker labels had the pre-move `/desk/Codes/Trakwise`); named volume preserved. Trakwise UI now `localhost:8082` — Payra keeps 8080.
- Quiz: Q1 ✓ (explicit versions, not managed by Boot BOM); Q2 taught (Swagger UI = dev affordance — disable/gate before deploy); Q3 taught (test scope keeps the artifact to runtime needs — imports, conflicts, CVE scans, size).
- Next: **P0.4d** — ArchUnit boundary test → P0.4 closes → P0.5 (frontend skeleton).

### 2026-10-08 — Wrap-up: P0.4 CLOSED (module boundaries enforced)

- `ArchitectureBoundaryTest` landed (`6c8dc66 test: enforce module boundaries with ArchUnit`, docs `688be78 docs: log P0.4c`). Suite **9/9 green** — boundary 3 (0.9 s) + handler 5 + contextLoads 1; tree clean.
- P0.4 umbrella fully delivered: modular `com.payra` packages, RFC 7807 error contract, API docs (springdoc), Testcontainers base, ArchUnit boundary rules.
- Placement lesson: file was first created under `src/main/java` → `com.tngtech` unresolved; ArchUnit is test-scope, so the compiler proved the classpath boundary — moved to `src/test/java`.
- Resume checklist (5 min): (1) typo pass on the test file — class `ArchitechtureBoundaryTest` → `ArchitectureBoundaryTest`; Javadoc: `bounderies` → `boundaries`, `an new` → `a new`, `claass` → `class`, `out of data` → `out of date`, `rull` → `rule`; re-run `./mvnw test` → 9/9; commit `style(test): fix typos in boundary test`; (2) answer the P0.4d quiz — (a) why derive `MODULES` from compiled code, (b) what `allowEmptyShould(true)` trades away, (c) a concrete P2+ violation rule 1 catches.
- Next slice — **P0.5 frontend skeleton**: Next.js 16 App Router + TS + Tailwind 4.3 + shadcn; checks `bunx tsc --noEmit`, lint, build. Then **P0.6** FE↔BE first contact (typed client + health widget).

### 2026-10-11 — Resumed

- Owner re-prioritization: interview lands **after full project completion**; time-crunched → phases run in order at max pace, interview drill (P8.7) stays last; ⭐ stretch items are the first cut if needed.
- P0.4 confirmed closed from the Oct 8 session (suite 9/9; springdoc; ArchUnit boundaries; Trakwise vacated 8080 → Payra keeps it).
- Drive-by fixes queued: `application.properties` comment typos (`PFC 7807`, `# --- Actuator --`); P0.4d quiz still open (optional).
- Next: **P0.5** — generate `web/` (create-next-app, Bun, App Router, Tailwind, `--empty`), then verify dev / typecheck / lint / build.

## 12. Docs map

| File | Purpose |
|---|---|
| `docs/architecture.md` | flows, module map, ledger templates, data model, mechanisms |
| `docs/interview-map.md` | JD→artifact matrix, Q&A bank, demo script, metrics |
| `docs/glossary.md` | vocabulary used in this project |
| `docs/adr/` | decision records (from P2.7 onward) |

## 13. Interview map (summary)

Top talking points (full matrix in `docs/interview-map.md`):

1. **Double-entry ledger + projection invariants** — money can't appear or disappear.
2. **Idempotency everywhere** — API keys + consumer dedup = effectively-once effects.
3. **Concurrency** — ordered row locks; tests prove no overdraft under parallel transfers.
4. **Saga + compensation + recovery worker** — provider callbacks may be late, duplicated, or missing.
5. **Transactional outbox → Kafka** — no dual-write; replayable; DLQ operationally owned.
6. **Reconciliation as a first-class product** — recon runs, mismatch taxonomy, ops drilldown.
7. **Limits/fees engines** — rules, not `if`s; transactional usage counters.
8. **Modular monolith with enforced boundaries** — "here's the seam where each module becomes a service."
