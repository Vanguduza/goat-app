# Farm OS — Technical Implementation Handbook

**Version:** 1.0 · **Date:** 22 August 2026
**Upstream methodology:** [DDE — Development & Engineering Engine](https://github.com/Vanguduza/dde) (`docs/blueprint/REV_2_0.md`, `AGENTS.md`). This handbook adapts DDE's authority model, schema law, gates and governance to the Farm OS platform.
**Sibling documents:** `GOAT_RABBIT_FARM_PLATFORM_TECHNICAL_IMPLEMENTATION_MASTER_PLAN.md` · `FARM_OS_HEALTH_MODULE_SPEC.md` · `FARM_OS_EMBEDDED_AI_MODULE_SPEC.md` · `FARM_OS_RABBIT_NEST_BOX_SCHEDULE.md` · `FARM_OS_RABBIT_BREEDING_PROGRAMME_SPEC.md`

---

## Contents

| Ch | Title |
|----|-------|
| 0 | How to use this handbook |
| 1 | Authority model |
| 2 | Product Constitution (Farm OS) |
| 3 | The five environments |
| 4 | Architectural principles |
| 5 | Canonical manufacturing spine |
| 6 | Schema authority |
| 7 | Data laws |
| 8 | Module boundary map |
| 9 | Definition of done |
| 10 | Phase gates — CI green ≠ phase done |
| 11 | Engineering Decision Records (EDRs) |
| 12 | Verification & evidence |
| 13 | Offline-first & durability |
| 14 | Security & credential law |
| 15 | Staged delivery map |
| 16 | Forbidden patterns |
| 17 | Governance & when blocked |
| 18 | Traceability appendix |

---

## 0. How to use this handbook

**Audience.** The owner, and every AI agent or human contributor building Farm OS. Agents MUST read Chapters 1, 9, 10 and 16 before writing code.

**Division of authority.**

- The **master plan** and **feature specs** govern **WHAT** Farm OS is (scope, data model, screens, schedules).
- This **handbook** governs **HOW** Farm OS is built (authority, process, gates, verification, durability).
- Neither overrides the other; they compose. A spec change that alters architecture follows Chapter 17 change control.

**Precedence on conflict** (highest first):

1. Product Constitution (Chapter 2)
2. Feature specs / master plan normative sections
3. This handbook's process rules
4. Accepted EDRs where they amend any of the above (an EDR supersedes; it never rewrites history)
5. Agent suggestions, conversation memory, model opinion — never authoritative

**Reading discipline (DDE rule).** Read the relevant spec section before changing anything it governs. Do not implement from chat memory.

---

## 1. Authority model

Farm OS copies DDE's precedence ranks. A lower rank may *inform* a higher rank but may never modify it. Modification requires a governed change path (Chapter 17) terminating in a rank-0 owner decision. This rule is the primary defence against stale chat memory, donor contamination and conversational drift.

| Rank | Artifact class | Farm OS concrete artifact | Changed by |
|------|----------------|---------------------------|------------|
| 0 | Human-approved governance decisions | Owner decisions recorded in mission/issue log | Owner only |
| 1 | Product Constitution | Chapter 2 below + master plan "Alignment rules (non-negotiable)" | Rank-0 decision via change control |
| 2 | Approved product brief | Master plan purpose/scope header | Rank ≤1 |
| 3 | Approved requirements | Spec requirement statements (e.g. breeding programme §MVP scope lock) | Rank ≤2 |
| 4 | Accepted EDRs | `docs/truth/edr/EDR-*.md` | Supersede only, never rewrite |
| 5 | Business rules | KudBat offsets, COI thresholds, foster windows (locked tables in specs) | Rank ≤4 |
| 6 | Architecture | Master plan data model §4, module map §5, AI spec §architecture | Rank ≤5 |
| 7 | Task specification | Phase task breakdowns, milestone tickets | Planner within phase scope |
| 8 | Verified implementation & evidence | Code + tests + gate reports — produced, never asserted | Produced by missions |
| 9 | External evidence | Vet research pack, Merck Manual, supplier docs, web research | Ingested with citation, never auto-promoted |
| 10 | Agent hypotheses | Chat suggestions, drafts | Freely produced, never authoritative |

**Two standing rules.**

1. Never edit `docs/truth/**`, the Constitution, or locked spec sections as a side effect of implementing a task. Propose; do not mutate.
2. When code must diverge from a spec, stop: the divergence is an EDR candidate, not a commit.

---

## 2. Product Constitution (Farm OS)

*Versioned Project Truth. Changes only through change control (Chapter 17).*

### 2.1 Purpose

A multi-species livestock operating system for smallholder and commercial farms: Android-first recording, species-correct lifecycle workflows, advisory intelligence, offline resilience. Goats and rabbits are the Phase 1–8 system of record; poultry, sheep and cattle are first-class products, not filters.

### 2.2 Target users

Owner-operators (single farm), farm workers (task executors), breeding managers (rabbit programme), veterinarians/advisors (read-mostly review).

### 2.3 Non-negotiable constraints

1. **Hosting law.** The backend is Supabase (Postgres, Auth, Storage, Edge Functions) plus the APK. No farm-hosted servers, VMs, sidecars, containers or self-managed LLM runtimes are part of the product.
2. **Species alignment rules.** No generic Animals home; the only home is a module dashboard. Goat UX designed for goats; rabbit UX for rabbits; poultry kinds live inside `:feature-poultry`; sheep/cattle likewise. Shared tables (`animals`, `domain_events`, measurements) are infrastructure and must never become a shared animal-list product. RFID/search identifies a record then opens that species' module.
3. **AI is advisory.** Lane A/B/C outputs recommend; they never auto-treat, auto-cull, auto-sell or mutate clinical records without a human confirm step.
4. **Offline-first.** Every daily workflow works without connectivity; sync reconciles idempotently later.
5. **Tenancy.** `farm_id` from the authenticated JWT + Postgres RLS on every table. Application-code filtering alone is non-compliant.

### 2.4 Core workflows

Daily round (dashboard → today board → tasks), breeding cycle (mate → palpate → nest box → kindling → wean → rebreed), health event capture → treatment course → withdrawal tracking, growth recording → charts/anomalies, inventory drawdown linked to events, sales/waitlist fulfilment.

### 2.5 UX principles

Species-native vocabulary; one-screen-per-task flows usable with dirty hands; notifications actionable (complete/snooze/escalate); charts answer a husbandry question, never decorate; dark-mode and large-touch defaults.

### 2.6 Security principles

Fail-closed RLS; secrets in Android Keystore; least-privilege DB roles; audit trail for AI actions (`ai_audit`) and sensitive mutations; no long-lived credentials to anything executing model-generated content.

### 2.7 Architecture principles

Modular Gradle monolith; CQRS with `domain_events` as Source of Record; Room mirror for offline; WorkManager for scheduled truth; BYO Model API behind settings; design for replacement (see Chapter 4).

### 2.8 Explicit exclusions

iOS/web clients; multi-farm org hierarchies (Phase 8+); drug dose invention; automatic financial ledger beyond optional posting hook; DNA lab integrations; marketplace payments; herd-level ML training in-app.

### 2.9 Governance rules

Constitution changes require rank-0 approval + dated entry in the master plan revision header. Specs may elaborate but not contradict. Conflicts resolve downward through Chapter 1 ranks.

---

## 3. The five environments

Confusing these is the most common way this project fails. They stay separate.

| # | Environment | Farm OS instance | Authority |
|---|-------------|------------------|-----------|
| 1 | **Authoring environment** | Cursor Desktop + local repo on the owner's Windows machine | Never authoritative for anything |
| 2 | **Platform state** | Supabase project: Postgres (`domain_events`, projections, RLS), Auth, Storage | The only authoritative state |
| 3 | **Execution runtime** | On-device app sandbox: Room mirror, WorkManager workers, outbox queue | Derived/mirrored state only |
| 4 | **Product verification** | Instrumented test runs, debug builds against a staging Supabase project, golden fixtures | Produces evidence, holds no truth |
| 5 | **Model providers** | BYO OpenAI-compatible Model API endpoints configured in Settings; optional `ai-proxy` Edge Function | Replaceable, never trusted, never authoritative |

Rules: environment 1 never writes directly to environment 2 except through reviewed migrations; environment 3 reconciles to environment 2 and never becomes a second source of truth; environment 5 sees only minimised, purpose-scoped payloads (AI spec §safety) and its output is evidence-class (rank 9), not truth.

---

## 4. Architectural principles

| Principle | Meaning in Farm OS |
|---|---|
| Intent before implementation | Every feature starts from a spec statement with acceptance conditions, not from a screen sketch. |
| Truth before memory | `domain_events` + Supabase outrank agent memory, chat history and model opinion. |
| Models are workers | Lane B/C models sit behind stable interfaces (`AiInferencePort`); swap without touching domain code. |
| Capabilities are governed, not owned | Copilot tools request access via allow-listed `FarmToolRegistry`; scope is explicit and auditable. |
| Context is compiled | Model prompts receive task-specific payloads, never an undifferentiated farm dump. |
| Scheduling is evidence-driven | Task/notification generation derives from locked offsets (KudBat pack), not ad-hoc heuristics. |
| Verification is independent | Schedule dates are checked against independently computed fixtures, not the engine that produced them. |
| Evidence is durable | Gate reports, test results and anomaly audits persist as records, linkable to requirements. |
| External information is evidence | Vet research and web findings are cited inputs; they become rules only through spec change. |
| Autonomy is bounded | Workers/copilot act inside declared scopes with stop conditions and human escalation. |
| Complexity must earn its place | No framework, bus or service split without a measured need (DDE §2.7 discipline). |
| Design for replacement | Model APIs, storage and providers swappable behind contracts; Settings is the seam. |
| Learn without drifting | Any learning/anomaly tuning changes policy artifacts through promotion, never product intent. |
| Cost is measured per verified outcome | Optimise for verified husbandry outcomes (conception %, kit survival), not token price or LOC. |
| Every stage ships a working system | No phase leaves the previous phase's golden flow unable to run end-to-end. |

## 5. Canonical manufacturing spine

This is the normative chain for every Farm OS change. Subordinate runtime bindings (Room mirror, WorkManager job, notification channel) never become alternative sources of state.

```
Owner intent / husbandry need
    |
Requirement .......................... spec section w/ acceptance conditions   (rank 3)
    |
EDR (if architectural) ............... docs/truth/edr/                        (rank 4)
    |
Schema migration ..................... supabase/migrations/*.sql               (Ch.6 authority)
    |
domain_event type registered ......... domain_events ledger contract            (Source of Record)
    |
Projection built ..................... analytics_* / breeding read tables       (derived, rebuildable)
    |
Room entity + DAO mirror ............. app offline cache                        (Ch.13 durability)
    |
UI screen (:feature-*) ............... species-native Compose screens
    |
Task/notification generated .......... WorkManager templates from locked offsets
    |
Verification ......................... unit/DAO/RLS/UI/golden fixtures        (Ch.12)
    |
Evidence + gate sign-off ............. phase gate report                        (Ch.10)
```

Each step maps to a governing document: steps 1–2 to the relevant spec; step 3 to the master plan data model; steps 4–5 to the CQRS section of the master plan; step 8 to the species alignment rules; step 10 to this handbook's gates.

---

## 6. Schema authority

1. **Single source of truth:** Supabase SQL migrations (`supabase/migrations/`). Room entities, API DTOs and doc tables are downstream artifacts.
2. **Rule of three.** A schema change ships as: SQL migration **+** matching Room entity update **+** test coverage (DAO test and, where tenancy applies, an RLS policy test). Any two without the third is rejected in review.
3. **Migrations apply cleanly to an empty database and are reversible** (down migration or verified compensating migration). CI applies `up`, asserts, applies `down`, re-applies `up`.
4. **Additive first.** Breaking changes require a new versioned path and a compatibility note in the EDR/spec section they amend.
5. **Prose vs schema:** when documentation and migrations disagree, migrations win; the prose is corrected in the same change.
6. **Generated artifacts are committed, never hand-edited** (e.g. generated Room DAOs from processors, exported schema snapshots). Drift between snapshot and migrations fails CI.

**Identity law.**

| Rule | Farm OS application |
|---|---|
| Time-ordered UUIDs (UUIDv7), generated client-side, native `uuid` PKs | Every durable table (`animals`, `rabbit_matings`, `domain_events`, …); client-side generation keeps offline creates conflict-free |
| Human-facing codes are separate immutable `slug` columns — never PKs, never FK targets | `ear_tag`, cage `code` ('A','B','C'), nest box `code`, wave `code`, `EDR-nnnn`. Unique per farm via `UNIQUE (farm_id, code)` |

---

## 7. Data laws

| Law | Rule | Enforcement |
|---|---|---|
| Tenancy | Every table carries `farm_id`; RLS predicate `farm_id = auth.jwt() ->> 'farm_id'` (or session GUC equivalent) on all farm-scoped rows | Postgres RLS, fail-closed: no claim ⇒ zero rows. RLS tests per table (Ch.12) |
| Append-only truth | `domain_events` is insert-only; corrections are compensating events, never updates/deletes | DB role for app has INSERT-only on ledger; revoke UPDATE/DELETE |
| Projections are rebuildable | Read models (`analytics_anomalies`, breeding projections, dashboards) can be dropped and rebuilt from the ledger alone | Rebuild script exercised in CI against fixture ledger |
| Idempotent mutations | Client mutations carry `mutation_id`; server deduplicates; retries never double-apply (no double mating record, no double inventory draw) | Unique index on `(farm_id, mutation_id)` in command intake |
| Optimistic concurrency | Mutable aggregates (`tasks`, `litters`, contracts) carry `lock_version`; mismatch returns `VERSION_CONFLICT` to UI | Domain layer check + integration test |
| Safe queue draining | Outbox/dispatch loops use `SELECT … FOR UPDATE SKIP LOCKED` so multiple workers never double-send notifications | Integration test with concurrent dispatchers |
| Single-database scale invariant | Modules are Gradle packages, not services; one Supabase Postgres is shared and a transaction may span module boundaries. Extracting any service must first preserve this invariant — that is an EDR, not a refactor | Architecture test + review rule |
| Retention & growth | Ledger partitioned by month once volume warrants; projections carry the query load; raw ledger retention is permanent (audit value) | Deferred until measured — named deferral, Ch.15 |

---

## 8. Module boundary map

Boundaries are enforced by tests (Konsist/arch-unit style module-graph test in CI), not goodwill — mirroring DDE "boundaries enforced by tests".

| Module | May depend on | Must never |
|---|---|---|
| `:core:*` (db, sync, design, notifications) | nothing internal beyond other `:core-*` | import any `:feature-*` or vendor AI SDK |
| `:domain-*` (per-species engines, `:domain-ai`) | `:core-*` | import Compose/UI, Android framework classes, network clients; only `:domain-*` may emit `domain_event`s |
| `:feature-goat` / `:feature-rabbit` / `:feature-poultry` / `:feature-sheep` / `:feature-cattle` | own `:domain-<species>`, `:core-*` | touch another feature's tables/DAOs/screens; embed another species' UX vocabulary |
| `:ai-client` | `:core-*` | be imported directly by features (features use `:domain-ai` ports) |
| `:ai-runtime-rules` / `:ai-runtime-onnx` / `:ai-runtime-litert` | `:domain-ai` port interfaces | appear anywhere else — vendor SDKs exist only here, behind adapters |
| Copilot tool layer (`FarmToolRegistry`) | allow-listed tool interface | expose write tools without declared side-effect class + confirm step |
| `interfaces/*` (settings, exports) | public ports | reach core tables directly |

Additional rules: RFID/search resolves identity then routes into the owning species feature (never a cross-species list screen). Shared infrastructure tables have exactly one owning writer module per event type. Any boundary exception requires an EDR naming why the graph test should gain an exemption.

---

## 9. Definition of done

All of these, every time — a task missing one is not done:

- [ ] A failing-first test existed that failed before the implementation existed (unit/domain engine, DAO, RLS, or UI).
- [ ] Full local check green: `lint`, `testDebugUnitTest`, instrumented subset for touched modules, `assembleDebug`.
- [ ] New migrations apply cleanly to an empty database and reverse cleanly.
- [ ] New tables carry `farm_id` with RLS enabled and tested; new queries cannot bypass tenancy.
- [ ] New async operation (worker, sync job, notification, export) has a durable identity, an idempotency key (`mutation_id`/dedupe key) and observable state (status visible in UI or logs).
- [ ] Side-effecting capability declares a side-effect class: `local_record` (ledger write), `notification`, `external_call` (Model API), `commercial` (sales contract/inventory deduction). Commercial + external require human-confirm or reconciliation read.
- [ ] Public behaviour change reflected in the governing spec section it belongs to (not chat memory).
- [ ] Golden fixtures still pass: 11-doe KudBat wave schedule fixture, COI pedigree fixtures (parent-child block, half-sib warn, foundation override), offline outbox replay fixture.
- [ ] No forbidden pattern introduced (Chapter 16).

## 10. Phase gates — CI green ≠ phase done

**CI green is a CI gate, not a phase gate.** Lint/tests passing does not close a phase. A phase closes only after the spec-gate review below.

### 10.1 Gate procedure

Before declaring a phase (or mission within it) complete, re-read the chartered spec section(s). For every MUST/shall/recovery-grade rule in scope, either:

- **(a)** name the production mutation call site that enforces it — the actual code path where the rule fires on real data, opened and verified, not a docstring claim; or
- **(b)** list it as **deferred** with a proposed EDR.

Record the mapping in the phase gate report. Never "tests pass ⇒ phase closed."

### 10.2 Worked example — rabbit nest-box rule

Rule: *"nest box placed mating+28; removed kindling+21; reintroduced rebreed+28"*

| Gate step | Result |
|---|---|
| Call sites named | `WaveScheduler`/task template generation writes `nest_in_on`/`nest_out_on`; `NestAssigner.reserveNestBoxes()` enforces availability at placement; `NestBoxReminderWorker` fires place/remove notifications from those columns |
| Verified at mutation site | Placement task creation mutates `rabbit_nest_box_assignments` only through `reserveNestBoxes`, which rejects a box not in `available`/`sanitized` state |
| Adversarial check | Could a new sync session replay a placement with a fresh `mutation_id` and double-assign? No: unique active-assignment constraint per box + idempotency index |
| Deferred items | QR-scan assisted placement = deferred, EDR proposed |

Only then does Phase 2's breeding-programme slice sign off, and only then may the next slice chain.

### 10.3 Adversarial self-check (before any gate sign-off)

- Could a new device session or new `mutation_id` bypass this control?
- Is each claimed call site a real mutation of authoritative state, not a read/helper?
- Does the rule hold with connectivity off (offline path) as well as online?

### 10.4 No blind chaining

Do not start the next phase until the current gate report is written. If a correction mission is open, freeze further phase progression. Standing auto-resume applies only when an independent gate returns PASS or PASS-WITH-EDR.

---

## 11. Engineering Decision Records (EDRs)

Accepted EDRs are immutable; they are superseded by newer EDRs, never rewritten. Location: `docs/truth/edr/EDR-nnnn-<slug>.md`. Any architectural divergence discovered mid-task becomes an EDR candidate — stop and propose.

### 11.1 Template

```markdown
# EDR-nnnn — <slug title>

**Status:** Proposed | Accepted | Superseded by EDR-mmmm
**Date:** YYYY-MM-DD
**Affected requirements/spec sections:** …

## Context
Why this decision exists now; constraints in force.

## Alternatives considered
| Option | Assessment |
|---|---|

## Decision
The decision, stated normatively.

## Rationale
Why this option; what evidence ranks support it.

## Consequences
Positive, negative, neutral. What becomes easier/harder.

## Open questions (require explicit human decision)
…

## Smallest safe next step
…
```

### 11.2 EDR-0001 — In-app CopilotEngine replaces hosted Hermes

**Status:** Accepted · **Date:** 2026-08-20 · **Amends:** AI spec §architecture; master plan stack matrix

**Context.** Original design assumed a farm-hosted Hermes/Ollama sidecar for LLM reasoning. The owner set the hosting law: nothing hosted outside Supabase + APK; no sidecars unless free-tier viable. Hermes requires sustained RAM/CPU beyond any free tier.

**Alternatives considered.** Farm-hosted Ollama (violates hosting law, costs); free sidecar hosting (insufficient RAM, sleeps kill tool-loops); cloud LLM API direct-from-app without governance (credential leakage, unscoped tools).

**Decision.** The Copilot is an in-app Kotlin tool-loop (`CopilotEngine`). Reasoning calls go to a BYO Model API (Settings). Tool execution goes through allow-listed `FarmToolRegistry` against Supabase. Hermes task map: planning → engine loop; retrieval → compiled context builder; tool execution → registry; memory → Room+Supabase state; voice → deferred.

**Consequences.** No server ops for the owner; model quality depends on owner-configured endpoint; audit via `ai_audit`; long loops bounded by device battery policy.

**Open questions.** Default recommended Model API preset list (rank 0 to confirm).

**Smallest safe next step.** Settings screen + `AiClient` contract test behind a fake transport.

### 11.3 EDR-0002 — BYO Model API in Settings; optional Supabase ai-proxy

**Status:** Accepted · **Date:** 2026-08-20 · **Amends:** AI spec §settings/routing

**Context.** Lane C needs an external LLM endpoint. Keys must never ship inside the APK nor be logged; some owners prefer not to expose even their own key to the client.

**Alternatives considered.** Hard-coded vendor SDK per provider (violates design-for-replacement); embedded default key (forbidden); proxy-only with platform key (creates vendor lock at platform layer).

**Decision.** Settings fields: base URL, model ID, API key, mode (`on_device_only` / `hybrid`), optional `ai-proxy` Edge Function toggle. Routing: offline ⇒ Lane A/B only; hybrid ⇒ Lane C when reachable; proxy mode sends minimised payloads via Edge Function holding the key server-side (Supabase secret). Key stored in Android Keystore; never logged, never rendered in full in UI.

**Consequences.** Owner controls cost/vendor; proxy adds a Supabase-only escape hatch; no vendor code outside `:ai-client`.

**Open questions.** Rate-limit/backoff defaults; payload redaction checklist finalisation.

---

## 12. Verification & evidence

### 12.1 Test pyramid mapped to Farm OS

| Layer | Tooling | Covers |
|---|---|---|
| Domain engines (unit) | JUnit | WaveScheduler offsets, NestAssigner selection, COI math, foster window rules, colour prediction |
| Persistence | Room in-memory DB | DAO queries, migration integrity, projection rebuild |
| Tenancy | SQL-level RLS tests run against staging Postgres | Every table's policy: right farm sees rows, wrong farm sees zero, missing claim fails closed |
| UI | Compose tests | Species screens, today board, pairing planner flows |
| Background | WorkManager `TestDriver` | Notification schedules fire at locked offsets; retry/backoff policies |
| End-to-end golden fixtures | Instrumented scenario runs | 11-doe KudBat wave; COI pedigree cases; outbox replay |

### 12.2 Independent verification principle

The code that generates must not be its only judge. Schedule dates produced by `WaveScheduler` are verified against independently computed fixture dates (hand-derived from the locked offset table), never re-computed by calling the same engine. Similarly, COI results are checked against precomputed pedigree values, and anomaly detection against labelled example series.

### 12.3 Evidence

Every gate produces durable evidence: gate report file, test output, fixture diffs, linked requirement IDs. Evidence is append-only and referenceable from later missions ("Phase 2 gate, item 4"). Verifier quality is itself measured: if a verifier never fails across stages, it is reviewed for vacuity.

---

## 13. Offline-first & durability

| Concern | Rule |
|---|---|
| Outbox mutations | Client-side changes queue in a Room outbox with `mutation_id`; dispatch is idempotent server-side; order per aggregate preserved |
| Replay safety | Replaying the outbox after restore produces identical server state (idempotency index absorbs duplicates) |
| Pull-cursor sync | Device tracks last-received `domain_events` cursor; catch-up pulls events in ledger order; projections rebuilt locally deterministically |
| Conflicts | Optimistic `lock_version` on mutable aggregates; event-ledger merges are additive; UI surfaces genuine conflicts (e.g. same kit sold twice) for human resolution |
| Checkpoint/restore | Long workers (export builders, bulk photo upload) checkpoint progress; crash resume continues, never restarts side effects already applied |
| Effect journal | Side-effecting actions beyond the ledger (sales contract PDF, inventory deduction, notification send) record intent→result in a local journal; reconciliation reads confirm outcome before marking done; UNKNOWN outcomes are never blind-retried — only verified absence permits a new attempt |

---

## 14. Security & credential law

1. Identity: Supabase Auth JWT carries `farm_id`; RLS derives tenancy from the token, never from client-supplied target IDs.
2. Fail closed: missing/expired claim ⇒ zero rows everywhere; no anonymous read paths.
3. Secrets: Model API keys and service keys live in Android Keystore / Supabase secrets respectively; never in code, logs, screenshots, or prompt payloads.
4. Model payloads: minimised, purpose-scoped, species-filtered; no cross-farm data ever leaves row scope.
5. Least privilege: app DB role holds INSERT on ledger, SELECT/DML on its scopes only; Edge Functions use isolated service roles with narrow grants.
6. Audit: `ai_audit` records every Copilot tool invocation (tool, args hash, principal, result class); sensitive commercial actions append `audit_events`.
7. Forbidden: passing any long-lived credential to code that executes model-generated content (prompt-injected code must never inherit keys).

## 15. Staged delivery map

Phases follow the master plan. Every stage leaves the whole system runnable (principle: every stage ships a working system).

| Phase | Goal | Gate criteria (beyond DoD) | Explicitly deferred |
|---|---|---|---|
| 1 | Foundations: auth, farms, animals, ledger, offline sync, species shells | RLS proven fail-closed on all base tables; outbox replay fixture green | Multi-farm orgs; role UI beyond Owner/Worker |
| 2 | Reproduction: goat kidding; rabbit breeding programme (waves, nest boxes, COI, litters, tasks) | Nest-box rule gate (Ch.10.2); COI fixtures; 11-doe golden wave | Buck borrowing marketplace; QR hardware printing |
| 3 | Growth & production: measurements, charts (Vico), Lane A anomalies, `analytics_*` projections | Anomaly call sites named; rebuild-from-ledger proven | Lane B models; Lane C activation |
| 4 | Health module: events, treatments, withdrawal, task integration | Withdrawal blocking rules at mutation sites | Vet telemedicine; lab integrations |
| 5 | Poultry / sheep / cattle modules as first-class products | Species alignment rule audit per module | Kinds beyond chicken/duck in poultry v1 |
| 6 | Inventory, sales, waitlist, contracts, market planner | Commercial side-effect class enforced; reconciliation reads proven | Payments processing; invoicing taxes |
| 7 | Exports: cage cards PDF, ICS calendar, photo journal, reports | Export determinism fixtures | Custom report builder |
| 8 | Intelligence: Lane B on-device models, Lane C BYO Model API, Copilot + FarmToolRegistry, `ai_audit` | Tool allow-list audit; payload minimisation checklist; advisory-only confirm gates | Voice; autonomous treatment suggestions; herd-level training |

Deferrals are named, not silent: each has an owner decision pending and may become an EDR when activated.

---

## 16. Forbidden patterns

1. A second source of truth for any mutable state (shadow tables, un-synced local edits, spreadsheet imports that bypass the ledger).
2. A generic Animals home screen or cross-species animal-list product.
3. AI auto-treatment, auto-culling, auto-selling, or clinical mutation without human confirmation.
4. Hard-coding a model vendor, cloud region or storage provider behind anything but Settings/contracts.
5. Retrying a side-effecting operation without an idempotency key or reconciliation read.
6. Widening RLS, a tool lease, or an autonomy scope to make a test pass.
7. Hand-editing generated artifacts (migrations snapshots, generated DAOs, exported schema docs).
8. Introducing a framework, message bus or microservice split for core state without a measured need + EDR.
9. Editing `docs/truth/**` or locked spec sections as a side effect of implementation.
10. Shipping a "temporary" alternative when blocked instead of stating the blocking decision.

---

## 17. Governance & when blocked

### 17.1 Change control flow

| Change type | Path |
|---|---|
| Constitution / alignment rule change | Rank-0 owner decision → dated revision entry in master plan header → specs updated |
| Spec elaboration (no contradiction) | Direct edit of the owning spec, noted in its revision line |
| Architectural divergence found mid-task | Stop → draft EDR → owner decision → accepted EDR supersedes; specs amended by reference |
| Business-rule change (offsets, thresholds) | Spec change (rank 5 artifacts) + fixture update in same change |
| EDR supersession | New EDR references old; old file gains no edits beyond a superseded-by header note |

### 17.2 When blocked

Say so. State the smallest decision that would unblock. Stop. Do not invent a contract, do not implement a temporary alternative, do not widen any scope to progress. A blocked state is reported in the mission/gate record with the exact question for the owner.

---

## 18. Traceability appendix

| Handbook chapter | Farm OS source | DDE counterpart |
|---|---|---|
| 1 Authority model | Master plan alignment rules | REV_2_0 §2.2 authority ranks |
| 2 Constitution | Master plan + AI spec hosting law | Product Constitution template |
| 3 Environments | AI spec hosting footprint | REV_2_0 §1.3 five environments |
| 4 Principles | Master plan principles; AI spec product law | REV_2_0 §2.3 |
| 5 Spine | Master plan CQRS §4 | REV_2_0 §2.5 canonical spine |
| 6 Schema authority | Master plan data model §4 | REV_2_0 §3.1–3.4 |
| 7 Data laws | Master plan CQRS + sync; breeding spec schema | REV_2_0 §3.2, §3.5, §12 |
| 8 Boundaries | Master plan module map §5 | REV_2_0 §2.6; AGENTS.md boundaries |
| 9 Definition of done | Breeding spec test plan §13 | AGENTS.md definition of done |
| 10 Phase gates | Nest box schedule locked offsets | `.cursor/rules/mission-chapter-gate.mdc` |
| 11 EDRs | AI spec locked decisions | EDR-0001…0007 in dde repo |
| 12 Verification | Breeding spec tests; AI spec evals | REV_2_0 Ch.11 |
| 13 Durability | Master plan offline sync | REV_2_0 Ch.12 |
| 14 Security | AI spec safety/privacy | REV_2_0 Ch.14 |
| 15 Staged delivery | Master plan phased delivery | REV_2_0 Ch.18 |
| 16 Forbidden | Master plan invariants; AI spec forbidden | AGENTS.md forbidden list |
| 17 Governance | Master plan revision process | REV_2_0 Ch.20; AGENTS.md when blocked |
| 18 Traceability | This table | REV_2_0 Ch.20 traceability |

*End of handbook. This document governs HOW; the specs govern WHAT; the owner outranks everything.*



