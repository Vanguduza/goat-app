# Farm OS

Multi-species farm operating system for goats, rabbits, poultry, sheep and cattle, with shared health, operations, finance, genetics, intelligence, simulation, search and Copilot modules.

## Current authority

Start with:

1. [`docs/00_PROJECT_TRUTH.md`](docs/00_PROJECT_TRUTH.md) — current owner-approved scope and architecture.
2. `docs/FARM_OS_AGRIBUSINESS_TECHNICAL_MASTER_PLAN_REVISION_2.md` — retained product/domain depth where not superseded.
3. `docs/FARM_OS_CONSOLIDATED_PROJECT_DOCUMENTATION.md` — specialist veterinary, health, AI and rabbit reference binder.
4. `docs/FARM_OS_TECHNICAL_IMPLEMENTATION_HANDBOOK.md` — process guidance where compatible with Project Truth.
5. `AGENTS.md` — mandatory execution rules for coding agents.

Older documents are provenance only when they conflict with Project Truth.

## Locked MVP architecture

- Native Android/Kotlin/Compose.
- Room local operational store + transactional outbox.
- WorkManager durable synchronization.
- Supabase Auth + PostgreSQL + RLS + Storage + Database Functions/RPC + Edge Functions.
- Append-only `domain_events` for event-ledger domains.
- Meilisearch as mandatory, rebuildable server search projection; local search remains available offline.
- Full MVP scope includes all documented species and shared modules.

## Canonical development branch

`main` is the only development authority. `project-truth-ledger` is an orphan CI-written audit history and must never be merged into `main`.

The designated architecture slice is:

```text
Register Goat
→ Record Weight Offline
→ survive restart
→ synchronize through Supabase RPC
→ reconcile authoritative state
→ index/search through Meilisearch
→ verify on a second device
```

A successful slice earns only `VERTICAL_SLICE_GREEN`. It does **not** make the goat feature, goat module, or MVP complete.

Current architecture-slice certificate: `VERTICAL_SLICE_GREEN` on `6c7c79a93dc54c74134a70b3763549b442c22349` from [canonical run 33801257315](https://github.com/Vanguduza/goat-app/actions/runs/33801257315). Fan-out of further feature work is authorized. `FEATURE_GREEN`, `MODULE_GREEN`, and `MVP_GREEN` remain false. See [`docs/realisation/VERTICAL_SLICE_GATE.json`](docs/realisation/VERTICAL_SLICE_GATE.json).

Post-certificate fan-out remains intentionally uncertified at feature/module/MVP level. The consolidated `main` head now passes the strengthened canonical CI suite, including Room migration/reopen durability, representative fan-out offline durability, broader domain tests, instrumentation-source compilation, Supabase pgTAP, Meilisearch and search-pipeline verification. This does not promote any feature, module, visual surface or the MVP to green; those states require their separate evidence contracts.

## Build and verification

Canonical CI (`.github/workflows/foundation-ci.yml`) runs:

- Goat, rabbit and operations domain tests plus session/sync unit tests
- Kotlin compilation for app and Android instrumentation sources; canonical CI does **not** run `assembleDebug`
- Connected emulator proofs for Room migration/reopen durability, representative fan-out offline durability, two-device pull visibility, auth refresh, and revoked-membership client handling
- Local Supabase migrations and the full pgTAP database suite
- Edge Function type-checks and API-key compatibility tests
- Live Meilisearch index contract and tenant isolation
- Full search pipeline: authority → index job → outage/retry → rebuild
- Kotlin duplicate-import and orchestration no-growth stabilization checks

Use JDK 17, Android compile SDK 37, Gradle 9.3.1, and pinned Supabase CLI 2.116.0. Local secrets and production credentials are never committed.
