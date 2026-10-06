# Farm OS — Agent Operating Rules

Farm OS (GOAT) is a multi-species livestock operating system: a local-first Android/Kotlin/Compose app with Room on every device, farm-LAN peer replication and owner Google Drive replication/backup. There is no application server (owner lock of 30 September 2026, `docs/00_PROJECT_TRUTH.md` §0).
Before changing code, read `docs/00_PROJECT_TRUTH.md`, then the relevant product/spec section. Do not implement from chat memory.

## Authority — non-negotiable

1. Explicit owner decisions outrank everything.
2. `docs/00_PROJECT_TRUTH.md` + accepted Farm OS v3 Implementation Closure / Full Realisation registries govern current scope and architecture.
3. Feature/master/spec documents govern domain detail only where they do not conflict with v3 truth.
4. `FARM_OS_TECHNICAL_IMPLEMENTATION_HANDBOOK.md` remains process guidance where not superseded by v3.
5. Accepted EDRs may implement/clarify higher authority; they may not silently reduce owner-approved scope.
6. Earlier documents are provenance. A stale document cannot regain authority because an agent retrieved it first.

Current locked owner decisions:

- MVP contains all documented features and all specified animal modules: goat, rabbit, poultry, sheep and cattle plus all shared modules.
- No application server. Room/SQLite on each device is the operational datastore; devices replicate immutable operations over the farm LAN and through approved Google Drive Gateways (`docs/architecture/GOAT_OFFLINE_MULTI_DEVICE_SYNC.md`, `domain/replication`). Supabase and Meilisearch are superseded authorities being migrated out in audited tranches; do not extend them.
- Local full-database search is the search authority.
- A green vertical slice proves architecture only. It does not make a feature green.
- `FEATURE_GREEN` requires the complete Feature Implementation Contract and all applicable tests/gates.

## Schema and write authority

- Room schema plus the replication operation contract are the data authority. Every Room schema version increase needs executable migration-chain evidence. `supabase/migrations/` is server-era provenance pending migration.
- Material business writes use versioned local command boundaries that commit the Room change and its replication operation together. Do not create a second direct-table business writer.
- `domain_events` is append-only for event-ledger domains; corrections are compensating/superseding events.
- Master/configuration records use governed relational versioning/audit rather than fake event sourcing.
- Every farm-owned durable table and every replicated operation carries `farm_id`; operations for another farm are rejected; cross-farm relationships are prevented structurally.
- No privileged credential in the APK. Drive tokens and farm keys live in Keystore-backed storage and are never broadcast over LAN discovery.

## Offline and sync

- User-visible success means the local Room transaction has committed.
- Every replicated mutation is an immutable operation with a global ID, per-device sequence, original business time and checksum; replay is idempotent.
- Devices exchange sync vectors and transfer only missing operations. Sync order is never business order; late devices are reconciled, never discarded.
- Never synchronise whole SQLite files; no last-file, last-device or timestamp wins.
- "Saved locally", "Synchronised" and "Backed up" are distinct, truthful states.
- Sync, Drive or provider failure cannot roll back accepted local state.

## Module boundaries

- Only domain/application command handlers create authoritative business events; Compose screens never write Room business tables directly.
- Features never import another species feature to reuse biology or copy.
- Species UX stays species-native: no generic Animals home, no mammalized poultry, no sheep-as-goat or cattle-as-sheep implementation.
- Vendor AI SDKs live behind Farm OS-owned ports.
- Search lives behind Farm OS-owned search interfaces over the complete local database; capped presentation lists never feed selectors, metrics or reports.

## Fan-out discipline

A green architecture slice authorizes implementation breadth, not uncontrolled batch size.

- New feature fan-out must be delivered in reviewable vertical batches. Prefer one coherent Feature Implementation Contract or one tightly coupled infrastructure slice per commit series.
- Every newly added domain module must enter canonical CI in the same batch as its production code.
- Every Room schema version increase must add executable migration-chain evidence; fresh-database tests alone are insufficient.
- Do not expand `FarmOsPullReconciler.kt`, `OperatingModuleHost.kt`, `MainActivity.kt`, or other known orchestration hotspots beyond the checked-in stabilization ceilings. Split responsibilities first.
- A batch that introduces thousands of lines across unrelated species/modules without completed compilation/test evidence is not a valid completion batch.
- If CI is unavailable, do not convert unexecuted code into a green claim. Keep the last proven certificate pinned to its evidence commit.

## Design system

`docs/ux/animal-farm-visual-lock/` is the sole presentation authority. Start at `docs/ux/animal-farm-visual-lock/START-HERE.md`, then read `DESIGN-SYSTEM.md`, `PAGE-PATTERNS.md` and the affected registry entries. The owner decision of 24 September 2026 removed superseded visual authorities and their unused runtime artifacts; do not recreate, quote as active guidance, or reintroduce them.

- Every UI change declares its `FOS-*` Screen ID and canonical visual lineage.
- Features use governed Animal Farm tokens/components; external repositories are never visual authorities.
- Management uses the locked A / Control room family. Worker uses the locked D shell + C Review & decide family.
- Inter is the single production font family. Handwritten/script fonts are not part of the lock.
- No default Material purple/blue, emoji, marketing copy, arbitrary ungoverned radii or placeholder content.
- Required production illustration assets and native evidence remain visual-green blockers until approved; never fake certification.

## Donor repositories

Every donor-dependent implementation declares one mode: `QDRS_REFERENCE_ONLY`, `BEHAVIOR_PORT`, `ALGORITHM_ORACLE_REFERENCE`, `UI_PATTERN_REFERENCE`, `SELECTIVE_CODE_IMPORT`, `RETAINED_COMMODITY`, or `NATIVE_BUILD`.

- Licence check before code reuse.
- GPL/AGPL code is not copied into the Farm OS proprietary APK; reconstruct behavior independently unless owner/licensing decisions explicitly change this.
- Every donor use records commit/path provenance, extracted behavior, rejected architecture, native gaps and parity/superiority tests.

## AI boundaries

AI is advisory and evidence-bound. No autonomous prescribing, dosing, treating, culling, selling, accounting posting, authorization bypass or unrestricted SQL/shell/http tools.

## Definition of done — every feature

A feature is **not** complete because code exists or because an architecture slice passed.

`FEATURE_GREEN` requires, as applicable:

- [ ] Full Feature Implementation Contract realized: states, commands, queries, events, permissions and UI surfaces.
- [ ] Failing-first domain/property tests.
- [ ] Schema migration + Room mirror/model + tests.
- [ ] `farm_id`, relational tenant integrity, local role/permission and negative authorization tests.
- [ ] Offline save/restart/retry/conflict/rejection behavior.
- [ ] Idempotency and optimistic-concurrency tests.
- [ ] Search projection/local fallback/rebuild tests where searchable.
- [ ] Material eventuality recovery procedures and tests.
- [ ] Accounting/inventory reconciliation where applicable.
- [ ] AI evidence/safety tests where applicable.
- [ ] Phone/tablet/loading/empty/error/offline/conflict/accessibility UI states.
- [ ] NFR/observability evidence.
- [ ] Field workflow evidence.
- [ ] Donor provenance/parity evidence when donor-derived.

A module reaches `MODULE_GREEN` only when every mandatory Feature ID inside it is `FEATURE_GREEN`. MVP reaches `MVP_GREEN` only when all mandatory modules and whole-product certification pass.

## Vertical-slice rule

The designated slice is:

`Register Goat → Record Weight Offline → Restart → Sync → Reconcile → Search → Second Device`.

`VERTICAL_SLICE_GREEN` authorizes controlled feature fan-out only. It must never be converted into a feature/module/MVP completeness claim.

## Forbidden

- A second source of truth for mutable state; shadow authoritative tables; spreadsheet imports bypassing governed commands.
- Direct feature writes to Room business tables that bypass command handlers and the replication journal.
- Synchronising whole SQLite database files between devices.
- Retrying side effects without idempotency and reconciliation.
- Widening permissions, tool scope or autonomy to make a test pass.
- Hard-coded model vendors, privileged search credentials, storage providers or currency/locale assumptions.
- Free-typed medication products in treatment records.
- Placeholder UI content or emoji.
- Claiming `FEATURE_GREEN`, `MODULE_GREEN` or `MVP_GREEN` without the corresponding evidence registry being complete.

## When blocked

Record the blocker and the smallest owner/external action needed. Do not invent a temporary alternate architecture to make progress. Continue other unblocked work when it does not compromise the contract.

## Animal Farm visual alignment — owner-directed amendment

Before any UI work read `docs/ux/animal-farm-visual-lock/AGENT-INSTRUCTIONS.md`, DESIGN-SYSTEM.md, PAGE-PATTERNS.md and the affected registry entries. That pack and its exact approved home references are the only visual truth. Superseded visual documents were removed; historical logs may mention them only as provenance and never as instructions. The lock does not supersede feature scope, domain safety, offline architecture or authorization rules.

Management uses A / Control room; worker uses D shell with C's Review & decide task carousel. Reuse the original Animal Farm login animals and optional individual photos. Home gear is bottom-right and Theme-only. Every quantum screen/role variant/atom requires a reviewed contract, shared-token/component lineage and native evidence. Existing code, generated mockups and old green claims are not visual authority. No implementing agent may approve its own reference/golden changes or weaken gates.
