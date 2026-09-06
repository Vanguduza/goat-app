# Farm OS — Agent Operating Rules

Farm OS is a multi-species livestock operating system (Android/Kotlin/Compose + Supabase + Meilisearch).
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
- Supabase is the canonical backend/database authority.
- Meilisearch is mandatory MVP server search and remains a rebuildable projection.
- A green vertical slice proves architecture only. It does not make a feature green.
- `FEATURE_GREEN` requires the complete Feature Implementation Contract and all applicable tests/gates.

## Schema and write authority

- `supabase/migrations/` is the server schema source of truth. Schema changes require matching Android model/mirror changes and tests.
- Material business writes use versioned command boundaries/RPCs. Do not create a second direct-table business writer.
- `domain_events` is append-only for event-ledger domains; corrections are compensating/superseding events.
- Master/configuration records use governed relational versioning/audit rather than fake event sourcing.
- Every farm-owned durable table carries `farm_id`; cross-farm relationships are prevented structurally where applicable and protected by fail-closed RLS.
- No service-role, Meilisearch admin, or other privileged server key may appear in the APK.

## Offline and sync

- User-visible success means the local Room transaction has committed.
- Every remotely retried mutation has `mutation_id` and explicit state.
- Outbox states: `PENDING`, `IN_FLIGHT`, `ACKNOWLEDGED`, `CONFLICT`, `REJECTED`, `RETRY_WAIT`, `DEAD_LETTER`.
- Pull ordering uses a server-issued monotonic cursor, never phone wall clock.
- Search/provider failure cannot roll back accepted authoritative state.

## Module boundaries

- Only domain/application command handlers create authoritative business events; Compose screens never write Supabase tables directly.
- Features never import another species feature to reuse biology or copy.
- Species UX stays species-native: no generic Animals home, no mammalized poultry, no sheep-as-goat or cattle-as-sheep implementation.
- Vendor AI SDKs live behind Farm OS-owned ports.
- Meilisearch lives behind Farm OS-owned search interfaces and is never a system of record.

## Fan-out discipline

A green architecture slice authorizes implementation breadth, not uncontrolled batch size.

- New feature fan-out must be delivered in reviewable vertical batches. Prefer one coherent Feature Implementation Contract or one tightly coupled infrastructure slice per commit series.
- Every newly added domain module must enter canonical CI in the same batch as its production code.
- Every Room schema version increase must add executable migration-chain evidence; fresh-database tests alone are insufficient.
- Do not expand `FarmOsPullReconciler.kt`, `OperatingModuleHost.kt`, `MainActivity.kt`, or other known orchestration hotspots beyond the checked-in stabilization ceilings. Split responsibilities first.
- A batch that introduces thousands of lines across unrelated species/modules without completed compilation/test evidence is not a valid completion batch.
- If CI is unavailable, do not convert unexecuted code into a green claim. Keep the last proven certificate pinned to its evidence commit.

## Design system

`docs/ux/animal-farm-visual-lock/` is the current owner-directed presentation authority. Start at `docs/ux/ANIMAL_FARM_COLD_START_RESUME.md`. Earlier visual documents retain feature inventory and compatible operational rules only.

- Every UI change declares its `FOS-*` Screen ID and canonical visual lineage.
- Features use governed tokens/components; external repositories are never visual authorities.
- Use the Animal Farm locked A management / D+C worker family and supplied assets; earlier pastoral I1–I3 prescriptions are superseded.
- No default Material purple/blue, emoji, marketing copy, arbitrary ungoverned radii or placeholder content.
- Required production font/illustration assets remain visual-green blockers until approved assets are versioned; never fake certification.

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
- [ ] `farm_id`, relational tenant integrity, RLS and negative authorization tests.
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
- Direct feature writes to authoritative Supabase business tables.
- Retrying side effects without idempotency and reconciliation.
- Widening RLS, tool scope or autonomy to make a test pass.
- Hard-coded model vendors, privileged search credentials, storage providers or currency/locale assumptions.
- Free-typed medication products in treatment records.
- Placeholder UI content or emoji.
- Claiming `FEATURE_GREEN`, `MODULE_GREEN` or `MVP_GREEN` without the corresponding evidence registry being complete.

## When blocked

Record the blocker and the smallest owner/external action needed. Do not invent a temporary alternate architecture to make progress. Continue other unblocked work when it does not compromise the contract.

## Animal Farm visual alignment — owner-directed amendment

Before any UI work read `docs/ux/animal-farm-visual-lock/AGENT-INSTRUCTIONS.md`, DESIGN-SYSTEM.md, PAGE-PATTERNS.md and the affected registry entries. That pack and its exact approved home references supersede conflicting visual descriptions in prior Farm OS visual documents, but do not supersede feature scope, domain safety, offline architecture or authorization rules.

Management uses A / Control room; worker uses D shell with C's Review & decide task carousel. Reuse the original Animal Farm login animals and optional individual photos. Home gear is bottom-right and Theme-only. Every quantum screen/role variant/atom requires a reviewed contract, shared-token/component lineage and native evidence. Existing code, generated mockups and old green claims are not visual authority. No implementing agent may approve its own reference/golden changes or weaken gates.
