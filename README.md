# Payra — Digital Wallet & Payments Platform

A production-minded implementation of a digital wallet platform inspired by established payment platforms: add money, P2P transfers, bill payments, merchant QR payments — plus an operations console for transaction monitoring and reconciliation.

Built to demonstrate real payment-systems engineering: a double-entry ledger,
idempotent APIs, concurrency-safe money movement, a Kafka event backbone, and a
reconciliation engine.

> Status: 🚧 Phase 0 — foundation under construction.
> Plan, conventions, and progress: [`CLAUDE.md`](CLAUDE.md)

## Stack

| Part | Tech |
|---|---|
| Backend (`api/`) | Java 21 · Spring Boot 4.1 · PostgreSQL 18 · Redis 8 · Kafka 4 · Flyway |
| Frontend (`web/`) | Next.js 16 (App Router) · TypeScript · Tailwind CSS 4 · shadcn/ui |
| Infra (`infra/`) | Docker Compose · k6 (load tests) |

## Repository layout

```text
api/     Spring Boot backend (modular monolith)
web/     Next.js customer app + ops console
infra/   docker-compose, load tests
docs/    architecture, interview map, glossary, ADRs