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

Owner lock of 30 September 2026 (`docs/00_PROJECT_TRUTH.md` §0). There is **no application server**.

- Native Android/Kotlin/Compose.
- Room/SQLite is the operational datastore on every device. A local commit is a successful save. "Saved locally", "Synchronised" and "Backed up" are distinct states.
- Immutable replication operations in `domain/replication`, exchanged over the farm LAN and through owner Google Drive Gateways. Whole SQLite files are never synchronised.
- Local accounts, roles and permissions. No service-role key in the APK.
- Local full-database search is the search authority.
- Full MVP scope includes all documented species and shared modules.

Supabase, Meilisearch and the server RPC/outbox path remain in the tree as **superseded provenance**. Do not extend them as authorities. They are retired in audited tranches (`docs/architecture/GOAT_OFFLINE_MULTI_DEVICE_SYNC.md`).

## Canonical development branch

`main` is the sole persistent branch and the only development authority. Temporary implementation branches may exist only while an active pull request is under review and must be deleted immediately after merge. The former orphan `project-truth-ledger` branch was frozen into `docs/project-state/project-truth-ledger-snapshot/` and retired on 24 September 2026 so repository truth no longer depends on a second branch.

The designated architecture slice is:

```text
Register Goat
→ Record Weight Offline
→ survive process/device restart
→ replicate missing operations to a second farm device (LAN; Drive when configured)
→ reconcile from the journal (idempotent, original business time)
→ farm-scoped local search
→ second-device visibility
```

A successful slice earns only `VERTICAL_SLICE_GREEN`. It does **not** make the goat feature, goat module, or MVP complete.

Historical architecture-slice certificate: `VERTICAL_SLICE_GREEN` on `6c7c79a93dc54c74134a70b3763549b442c22349` from [canonical run 33801257315](https://github.com/Vanguduza/goat-app/actions/runs/33801257315). That run proved the **pre-lock** Supabase/Meilisearch slice and authorizes feature fan-out only. Its original provenance remains unchanged.

Post-lock Room/LAN evidence is recorded separately in VS-21–VS-24 of [`docs/realisation/VERTICAL_SLICE_GATE.json`](docs/realisation/VERTICAL_SLICE_GATE.json), against main `9bc3001258f291fd8baa60e30026d55f10847171` and [run 37028681388](https://github.com/Vanguduza/goat-app/actions/runs/37028681388), with its emulator/loopback limitations. Main `936a94ea4ad6e88e3965612b8075b1683a4b3895` also passed [Foundation run 37909999633](https://github.com/Vanguduza/goat-app/actions/runs/37909999633), including the declared connected tests and unapproved native reference capture. These runs do not establish live Drive acceptance or complete product qualification. `FEATURE_GREEN`, `MODULE_GREEN`, `MVP_GREEN` and `VISUAL_GREEN` remain false; exact evidence and remaining acceptance are recorded in the [Google test qualification review](docs/reviews/2026-10-09-google-test-qualification.md).

Post-certificate fan-out remains intentionally uncertified at feature/module/MVP level. Canonical CI currently still executes server-era provenance jobs (pgTAP, Meilisearch, search-pipeline) alongside Android/domain tests. Passing those jobs does not restore server authority and does not promote any feature, module, visual surface or the MVP to green.

## Build and verification

Canonical CI (`.github/workflows/foundation-ci.yml`) runs:

- Domain tests (`goat`, `rabbit`, `ops`, `replication`, `access`) plus session/sync/app unit tests
- `assembleDebug` and a SHA-256 of the APK; instrumentation sources compile
- Connected emulator proofs for Room migration/reopen durability and representative offline durability
- Deterministic unapproved native reference captures, matrix/bounds checks, visual-lock token guards and Kotlin orchestration no-growth ceilings
- **Provenance (superseded, do not extend):** local Supabase pgTAP, Edge Function checks, Meilisearch contract, search-pipeline

Use JDK 21 (Temurin, as in CI), Android compile SDK 37, and Gradle 9.3.1. Local secrets and production credentials are never committed.
