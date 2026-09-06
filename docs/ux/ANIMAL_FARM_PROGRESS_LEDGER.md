# Animal Farm visual-lock progress ledger

## Recovery checkpoint — 2026-09-06

- **Authorized target branch:** `implementation/animal-farm-visual-lock`
- **Remote target inspected:** `origin/implementation/animal-farm-visual-lock`
- **Remote HEAD inspected:** `afc53440b305a2ea086f16155c0d9227e4030180`
- **Handover checkpoint:** `afc53440b305a2ea086f16155c0d9227e4030180`
- **Pre-install parent landmark:** `e86981682748605e9db1570224e8c801c35b6058`
- **Remote advancement beyond handover:** no; remote target equals the handover checkpoint.
- **Recovery worktree:** `/home/ubuntu/goat-app-af-gate1`
- **Recovery work branch:** `agent/animal-farm-gate1-recovery`, created from the remote target to avoid disturbing concurrent work.

### Concurrent/dirty work preserved

The primary checkout `/home/ubuntu/goat-app-visual-realignment` is on `implementation/foundation-vertical-slice` with modified and untracked application/feature files. It was not changed.

The pre-existing target worktree `/home/ubuntu/goat-app-visual-lock-integration` is on `implementation/animal-farm-visual-lock`, one commit behind remote at `e86981682748605e9db1570224e8c801c35b6058`, and contains modified/untracked visual-lock implementation work. Blob comparison showed those files differ from the remote checkpoint. It was not reset, stashed, overwritten, fast-forwarded or absorbed.

### Governing authority read before product edits

- `AGENTS.md`
- `docs/00_PROJECT_TRUTH.md`
- `docs/ux/ANIMAL_FARM_COLD_START_RESUME.md`
- `docs/ux/HANDOVER-CHECKPOINT.md`
- `docs/ux/animal-farm-visual-lock/START-HERE.md`
- `docs/ux/animal-farm-visual-lock/AGENT-INSTRUCTIONS.md`
- `docs/ux/animal-farm-visual-lock/DESIGN-SYSTEM.md`
- `docs/ux/animal-farm-visual-lock/PAGE-PATTERNS.md`
- `docs/ux/animal-farm-visual-lock/MIGRATION-AND-GATES.md`
- `docs/ux/animal-farm-visual-lock/VALIDATION.md`
- `docs/ux/animal-farm-visual-lock/references/HOME_DESIGN_LOCK.md`
- `docs/ux/animal-farm-visual-lock/references/SCREEN_DESIGN_STANDARD.md`
- `docs/ux/animal-farm-visual-lock/references/animal-farm-locked-homes.html` (read as reference data only)
- affected registry index/source implementation mappings for Global, Home and representative Gate-1 species/capture surfaces
- `docs/ux/FARM_OS_VISUAL_AUTHORITY.md` and `docs/ux/FARM_OS_ROLE_DASHBOARD_CONTRACT.md`, with the visual-lock amendment treated as superseding conflicting presentation text
- applicable accessibility/content/theming rules in `docs/FARM_OS_DESIGN_SYSTEM_SPEC.md`
- applicable product, security, architecture, verification and offline rules in `docs/FARM_OS_TECHNICAL_IMPLEMENTATION_HANDBOOK.md`

### Handover integrity checks actually executed at `afc53440...`

All passed:

1. `node scripts/design/verify-handover.cjs`
2. `node docs/ux/animal-farm-visual-lock/scripts/verify-pack.cjs --self-test --registry docs/ux/FARM_OS_SCREEN_REGISTRY.yaml`
3. `node --check docs/ux/animal-farm-visual-lock/scripts/assemble-pack.cjs`
4. `node --check docs/ux/animal-farm-visual-lock/scripts/refine-registry.cjs`
5. `node --check docs/ux/animal-farm-visual-lock/scripts/verify-pack.cjs`

Verified facts: 39 original handover files, 19 protected hashes, eight negative integrity checks, 545 exact registry entries = 537 base + eight role variants, stale source footer remains 537, green flags remain false.

### Route / screen / feature reconciliation evidence

- **Audit tooling commits:** `e8a5157bd7fff836c9172247ba11743a395e5c8f`, `28638c64ca973c9d2f85661206f452aa09c18dc5`
- **Evidence tested commit:** `28638c64ca973c9d2f85661206f452aa09c18dc5`
- Product/navigation source at the evidence-tested commit is unchanged from handover `afc53440...`; only the audit tools were added.

Created source-derived audit tooling and evidence:

- `scripts/design/audit-navigation.cjs`
- `docs/ux/evidence/animal-farm-visual-lock/navigation-source-audit.json`
- `scripts/design/build-route-screen-feature-gap.py`
- `docs/ux/evidence/animal-farm-visual-lock/route-screen-feature-gap.csv`
- `docs/ux/evidence/animal-farm-visual-lock/route-screen-feature-gap-summary.json`

Verified static observations at tested commit `28638c6...`:

- 19 `FarmModule` values.
- Nine role personas, including eight registered role variants plus the fail-safe General fallback.
- Ten nested page/tab state machines recovered from the current Kotlin implementation.
- 545 registry rows preserved exactly.
- 143/545 rows have some source-derived route/implementation evidence; 402/545 currently have no such evidence.
- Runtime traversal has not yet been executed by this audit, so runtime reachability remains `UNEXECUTED` for all 545 rows.
- `RabbitPage.NESTS` is a render-only candidate: the branch exists but no transition into it was found.
- `OperatingModuleHost` has Tasks/Health/Money/Inventory branches that are pre-empted by dedicated hosts in the sole live caller path.
- Worker quick actions `Record Weight`, `Add Treatment`, and `Scan Animal` currently enter generic modules instead of exact owning workflows.
- No canonical feature-ID catalog was identified even though the supplied screen-contract template requires `feature_ids`; this remains open and must not be invented.

### Verified status changes

None. No screen, feature, module or MVP status has been promoted. Registry membership remains `MAPPED` inventory only.

### Open evidence gates

- Runtime route traversal, parameter scoping, return/restoration and deep-link checks.
- Authorization/role interaction tests for each route and atomic surface.
- Canonical feature-ID binding or governing owner/source-of-truth clarification.
- Native visual reference implementation, deterministic fixtures, screenshot matrix, accessibility evidence and independent acceptance.
- Architecture end-to-end revalidation after presentation foundation integration.

### Active next slice

Gate 1 shared visual foundation, starting with the smallest isolated semantic theme/tokens + Theme selection seam needed by Login and Home. It must not alter business command, Room/outbox, Supabase/RLS or sync behavior.

## Gate 1 foundation and recovered Android build evidence — 2026-09-06

### Versioned implementation checkpoints

- `2dacd4812457b1cb78a72158280f4fa2a61d37bb` — shared Animal Farm semantic theme/token foundation and focused token tests.
- `451068a2cf9dc56e26946f607ba6b9a8b2225e03` — gap-audit self-test made non-mutating; self-test now uses temporary current-source evidence rather than overwriting canonical evidence or requiring an older committed audit to equal HEAD.
- `9ec7480a8e9dfcb969e631e9b9928c79b791e45f` — recovered baseline Android build contracts without changing the protected visual-lock package.

The build-repair commit fixes only defects exposed by executing the recovered handover code: obsolete Compose `weight` imports, a Room DAO table-name typo, cross-module Kotlin smart-cast failures, lost `ColumnScope` receivers in auth state helpers, app-internal visibility mismatches, a stale typed task projection, incomplete species call signatures, and one JUnit4 expression-bodied test returning a non-`Unit` value. The generated Room v13 schema snapshot is committed because the database module exports schemas to `core/database/schemas` and the governing handbook requires generated Room schema snapshots to be versioned rather than hand-edited.

### Executable Android evidence

The connected host is ARM64. Machine-local tooling was provisioned outside the repository: Android SDK platform 37.0, Build-Tools 37.0.0 (with 36.0.0 also installed by Gradle), platform-tools 37.0.1, Gradle 9.3.1, and an ARM64 OpenJDK 17 toolchain. No machine-local SDK path was committed.

Verified passes:

- `:core:design:testDebugUnitTest` — passed; this executes the locked light/dark palette, Outdoor candidate, and touch-target token contract tests.
- `:app:compileDebugKotlin` — passed after `9ec7480...`.
- `bash scripts/ci/verify-kotlin-architecture.sh` — passed.
- `:domain:goat:test`, `:domain:rabbit:test`, `:domain:ops:test` — passed.
- `:core:network:testDebugUnitTest` — passed after correcting the JUnit4 test return contract.
- `:core:sync:testDebugUnitTest` — passed in the CI-equivalent task run.
- `:core:database:compileDebugAndroidTestKotlin` — passed; migration-test source compiles, with warnings only.
- Protected handover verifier and protected package self-test continue to pass: 39 original package files, 19 protected hashes, eight negative checks, and 545 exact registry IDs.

The remaining local CI-equivalent Android-test compilation is environment-blocked, not source-certified: during `app` Android-test resource processing, Android Gradle Plugin 9.1.1 resolves Maven `aapt2-9.1.1-14792394-linux`, which is an **x86-64** ELF binary, while the connected Oracle host is **aarch64**. It fails before `:app:compileDebugAndroidTestKotlin` can execute. Therefore app Android-test compilation remains unproven on this host and is not reported as passed.

GitHub draft PR #2 targets `implementation/animal-farm-visual-lock`, not `main`. Its first Foundation CI run failed before executing any workflow steps: independent jobs had no assigned runner (`runner_id: 0`), so that run supplies no compile/test evidence.

### Refreshed static reconciliation evidence

The canonical static navigation/gap evidence was regenerated from clean commit `9ec7480a8e9dfcb969e631e9b9928c79b791e45f` after the build repairs:

- registry rows: 545;
- rows with some static source-derived route/implementation evidence: 143;
- rows with no static route evidence: 402;
- runtime reachability executed: 0.

The counts are unchanged from the prior audit. Static evidence remains non-runtime and does not upgrade any screen, feature, module or MVP status.

### Status law after build recovery

No `VISUAL_GREEN`, `FEATURE_GREEN`, `MODULE_GREEN`, or `MVP_GREEN` claim is made. Compilation and unit tests prove only the exercised code contracts. Native screenshot evidence, accessibility evidence, deterministic runtime route traversal, exact feature binding, independent visual acceptance, and post-presentation end-to-end architecture verification remain open.

### Next Gate 1 slice

Continue the presentation foundation with the governed Home **Theme-only** control and application-level theme selection seam, then migrate Login using the supplied original animal-lineup asset and locked copy/layout rules while preserving authentication callbacks and truthful backend/session states. Outdoor remains a candidate requiring native acceptance and must not be certified from token tests alone.
## Gate 1 Theme and Login lock implementation — 2026-09-06

### Versioned implementation checkpoint

- `18304a3b4b0fe0c111ed81d25ca106087d999cdd` — application-level Animal Farm Theme seam, Theme-only home control, exact locked image assets, and locked entrance/login presentation.

### Implemented presentation contracts

- `FarmOsTheme` now carries the active `AnimalFarmThemeMode` plus a composition-level mode-change callback; business and navigation APIs are unchanged.
- The application persists only the local theme enum in a dedicated private preference namespace. It is separate from remembered farm/session context and does not enter Room, outbox, Supabase or sync state.
- Both current home bottom bars include the shared `HomeThemeButton` at the bottom-right and apply navigation-bar safe insets. The control exposes only Light, Dark and Outdoor. Outdoor is labeled as a native candidate because owner/native acceptance is still open.
- `farm_animal_lineup.png` and `farm_family_portraits_v1.png` were copied byte-for-byte from the protected visual-lock package into Android `drawable-nodpi`. SHA-256 remains `5f708f81...` and `41828998...` respectively.
- `scripts/design/verify-handover.cjs` now also fails if either Android copy differs from its protected source while preserving the exact protected-package inventory check.
- `FoundationAuthScreen` no longer uses the historical drawn wordmark/pastoral identity or redundant slogans. The entrance identity is now centered `Animal Farm` → exact lineup → real entrance state.
- Sign-in retains the existing callback/enablement behavior, centers the `Sign in` action, keeps labeled credentials, and adds email/password keyboard types plus an accessible password visibility control. Membership selection, farm setup and truthful connection-unavailable states remain real callbacks/states rather than decorative actions.

### Executable evidence at exact commit `18304a3...`

- `:core:design:testDebugUnitTest` — PASS.
- `:app:compileDebugKotlin` — PASS.
- `bash scripts/ci/verify-kotlin-architecture.sh` — PASS before commit with the same source tree.
- Protected handover integrity — PASS, including exact Android visual-lock copies.
- Protected package self-test — PASS; 545 IDs remain intact and package green flags remain false.
- Navigation audit self-test and route/screen/feature-gap self-test — PASS.
- New/owned design and entrance files were ktlint-clean after explicit Compose naming suppression. Legacy formatting debt remains in older app/home files and was not mass-formatted in this slice.

### Refreshed reconciliation evidence

Canonical static route evidence is rebound to `18304a3b4b0fe0c111ed81d25ca106087d999cdd`:

- registry rows: 545;
- some static source-derived route/implementation evidence: 143;
- no static route evidence: 402;
- runtime reachability executed: 0.

### Status law

This slice is implemented and compiles, but **no visual status is promoted**. There is still no native screenshot matrix, physical/emulator visual inspection, 200% text evidence, TalkBack evidence, dark/outdoor image-plate acceptance, or independent visual approval. `VISUAL_GREEN`, `FEATURE_GREEN`, `MODULE_GREEN`, and `MVP_GREEN` therefore remain unclaimed.

### Next Gate 1 slice

Establish deterministic native reference fixtures/previews for Login and Theme selection, then begin the locked Management A and Worker D+C home compositions. The existing generic/pastoral role dashboards are still migration targets and must not be treated as visually locked because the shared theme and login now compile.

## Gate 1 Management A / Worker D+C and exact deep entries — 2026-09-06

### Reconstruction facts that superseded the handover prompt

- Workspace boot was docs-only `main` at `402ee5f0299e60cc29cf8be57e5f5cd500d8e4b9`.
- Protected target `origin/implementation/animal-farm-visual-lock` remains `afc53440b305a2ea086f16155c0d9227e4030180`.
- Latest versioned recovery checkpoint is `c603a903b570e5519cdc571b88a2c166177900ba`.
- Claimed home commit `b3ccf6f8b19fcdb3679099a310cdccec1f57ebb2` does **not** exist on any local or GitHub ref. Management A and Worker D+C were still the pastoral RoleDashboardScreens at recovery HEAD.
- Historic worktrees `/home/ubuntu/goat-app-af-gate1`, `goat-app-visual-realignment`, and `goat-app-visual-lock-integration` are not present in this environment and were not created or reset.
- `THE_ANIMAL_FARM_OFFLINE_FIRST_DATABASE_ARCHITECTURE.md` is not in this repository. Adopted write/offline law remains `docs/00_PROJECT_TRUTH.md`: Room + transactional outbox + WorkManager + versioned Supabase RPC. Peer coordinator / Farm Relay / Drive backup were **not** adopted and were not invented.

### Versioned implementation checkpoint

- `e51770b082a825487c40622869be103a1e9e5e5d` — locked Management A and Worker D+C homes, exact worker deep entries, Rabbit nest-box reachability, and home projection tests.

### Implemented contracts

- FOS-HOME-012-A/B use P02 control-room composition: farm/date context, ranked real attention, work/resource tiles, family launchers from the approved portrait sheet. Goat counts are device-local; other species show `Open` rather than invented headcounts.
- FOS-HOME-012-D uses P03: DUE_NOW / UPCOMING / COMPLETED as a presentation projection of existing `open`/`done` + due dates. Review & decide carousel uses previous/next controls. Empty stages stay empty.
- Worker shortcuts now enter exact owners:
  - Record Weight → `GoatEntryPage.WEIGHT` / FOS-GOAT-011, with herd pick when no goat is selected.
  - Add Treatment → `Health(openTreatment = true)` / FOS-HEALTH-007.
  - Scan Animal → existing FOS-GOAT-006 tag/name search. FOS-GOAT-007 RFID and FOS-HOME-006 global search were not invented.
- `RabbitPage.NESTS` is reachable from the rabbitry dashboard (`onOpen(RabbitPage.NESTS)`).
- Goat session orchestration was extracted to `GoatModuleHost` so `FarmSessionContent.kt` stays under the 450-line ceiling (122 lines).
- Theme-only home gear and login lock from `18304a3...` are preserved.

### Executable evidence at exact commit `e51770b...`

This host is **x86_64**. The historic aarch64 AAPT2 blocker does not apply here. Android SDK platform 37.0 and build-tools 37.0.0 were provisioned locally and are not committed.

- `:domain:ops:test` `:domain:goat:test` `:domain:rabbit:test` — PASS, including `WorkerTaskProjectionTest`.
- `:core:design:testDebugUnitTest` — PASS, including locked family-window coordinates.
- `:core:network:testDebugUnitTest` — PASS.
- `:core:sync:testDebugUnitTest` — PASS.
- `:app:compileDebugKotlin` — PASS.
- `:app:assembleDebug` — PASS. Local artifact `app/build/outputs/apk/debug/app-debug.apk` (not committed).
- `:app:compileDebugAndroidTestKotlin` — PASS.
- `:core:database:compileDebugAndroidTestKotlin` — PASS, warnings only.
- `bash scripts/ci/verify-kotlin-architecture.sh` — PASS.
- Protected handover verifier — PASS.
- Protected pack self-test — PASS; 545 IDs intact; green flags remain false.
- Navigation audit self-test — PASS. `RabbitPage.NESTS` is no longer render-only. `direct_action_scope_violations = []`.

### Refreshed static reconciliation evidence

Canonical static evidence rebound to `e51770b082a825487c40622869be103a1e9e5e5d`:

- registry rows: 545;
- some static source-derived route/implementation evidence: 145;
- no static route evidence: 400;
- runtime reachability executed: 0.

The +2 static mentions are source-derived ID references, not runtime certification.

### Offline / APK-hosted infrastructure coverage from source (not assumed complete)

Present and used by the compiled APK:

- Room operational store and generated schema v13;
- `sync_outbox` with PENDING / IN_FLIGHT / ACKNOWLEDGED / CONFLICT / REJECTED / RETRY_WAIT / DEAD_LETTER;
- WorkManager drain (`SyncWorker`);
- server-issued pull cursor + `FarmOsPullReconciler`;
- device id on commands;
- local save receipts that do not claim server acknowledgment.

Not present; not implemented in this slice because they are not adopted Project Truth:

- APK-hosted local sync HTTP API;
- peer discovery / transport broker;
- coordinator lease / failover;
- shift handover / chunk resume;
- Farm Relay;
- Google Drive backup/restore;
- Android Keystore-backed device enrolment/revocation fabric.

### Status law

No `VISUAL_GREEN`, `FEATURE_GREEN`, `MODULE_GREEN`, `MVP_GREEN`, `OFFLINE_GREEN`, `SYNC_GREEN`, `COORDINATOR_GREEN`, `SHIFT_GREEN`, `BACKUP_GREEN`, `SECURITY_GREEN`, or `PROJECT_GREEN` claim is made. Native screenshot/device evidence remains unexecuted. Outdoor remains a candidate.

### Next independent slices

1. Continue Gate 1 restyle of species navigator, goat dashboard/weight, and remaining specialist homes on the same token family without dropping commands.
2. Keep Project Truth sync/outbox hardening inside the adopted Supabase path.
3. Do not promote a peer-coordinator architecture unless an accepted EDR supersedes Project Truth.

## Gate 1 module canvas restyle — 2026-09-06

### Versioned implementation checkpoint

- `59b1688cee4662192eb00490a36d7f61cddf62bc` — shared illustrated primitives and Gate 1 module chrome restyled onto Animal Farm tokens.

### Implemented presentation contracts

- `FarmPastoralBackdrop` is a semantic Animal Farm canvas. Geometric hills, barns, gradients and drawn animals are removed.
- `FarmOsWordmark` renders `Animal Farm` in Inter, not a leaf/Farm OS mark.
- `FarmStorySurface` and `FarmIllustratedSectionSurface` use locked surface/divider tokens with no heavy shadow, so remaining call sites follow dark/outdoor palettes.
- Splash (FOS-GLOBAL-001) uses centered Animal Farm plus the exact lineup asset. Marketing slogans are gone.
- Species navigator (FOS-HOME-002) is a family-launcher grid, not a mixed-species CRUD list.
- Goat, rabbit, poultry, sheep/cattle, and task dashboards use `AnimalFarmModuleHeader` with approved family portraits where a species is known.
- Health, inventory and finance keep existing commands; dashboard subtitles are factual device-local copy.
- Visual-authority CI now fails if pastoral scenery or the removed slogans return. `AGENTS.md` names `FARM_OS_VISUAL_AUTHORITY.md` as superseded inventory/provenance.

Commands, Room writes, outbox and navigation contracts are unchanged.

### Executable evidence at exact commit `59b1688...`

- `:core:design:testDebugUnitTest` — PASS, including family-window and species-to-family mapping tests.
- `:app:compileDebugKotlin` — PASS.
- `:feature:goat:compileDebugKotlin` `:feature:rabbit:compileDebugKotlin` `:feature:ops:compileDebugKotlin` — PASS.
- `:domain:goat:test` `:domain:rabbit:test` `:domain:ops:test` — PASS.
- `:core:network:testDebugUnitTest` `:core:sync:testDebugUnitTest` — PASS.
- `bash scripts/ci/verify-kotlin-architecture.sh` — PASS.
- `bash scripts/ci/verify-visual-authority.sh` — PASS.
- Protected handover verifier — PASS.
- Protected pack self-test — PASS; 545 IDs intact; green flags remain false.
- Navigation audit self-test — PASS. `direct_action_scope_violations = []`. `RabbitPage.NESTS` remains reachable.

### Refreshed static reconciliation evidence

Canonical static evidence rebound to `59b1688cee4662192eb00490a36d7f61cddf62bc`:

- registry rows: 545;
- some static source-derived route/implementation evidence: 145;
- no static route evidence: 400;
- runtime reachability executed: 0.

Counts are unchanged from `e51770b...`. This slice is presentation-only.

### Status law

No `VISUAL_GREEN`, `FEATURE_GREEN`, `MODULE_GREEN`, `MVP_GREEN`, `OFFLINE_GREEN`, `SYNC_GREEN`, `COORDINATOR_GREEN`, `SHIFT_GREEN`, `BACKUP_GREEN`, `SECURITY_GREEN`, or `PROJECT_GREEN` claim is made. Native screenshot/device evidence remains unexecuted. Outdoor remains a candidate. Peer coordinator / Farm Relay / Drive backup remain unadopted Project Truth and were not invented.

### Next independent slices

1. Continue remaining operational chrome (sheep/cattle ops homes, shared capture shells) on the same tokens without dropping commands.
2. Close more real transitions only where a page and owner already exist.
3. Keep Project Truth sync/outbox hardening. Do not implement APK-hosted P2P unless an accepted EDR supersedes Project Truth.

## Truthful goat sync receipts — 2026-09-06

### Versioned implementation checkpoints

- `3eaa72f230f7492ce333d3fd4b2e6d9f83f5f79a` — replace `Synced` wording in goat manual sync.
- `59913762c4fab66ffdd08241249f2f12fb8d8aaa` — extract `goatManualSyncReceipt` with `:app:testDebugUnitTest` coverage on this x86_64 host.

### Contract

Manual sync receipts name the server outcome or stay on local-pending language. An empty successful drain+pull does not say `Synced`. Local writes still say `Saved on this device · waiting to sync`.

### Evidence

- `:app:testDebugUnitTest --tests com.farmos.app.GoatSyncReceiptTest` — PASS, including `processDebugResources` on x86_64.
- `:app:compileDebugKotlin` — PASS.
- Visual-authority guardrail rejects a return of `Synced ·` in `GoatModuleHost.kt`.

No green-gate promotion. Native visual evidence remains unexecuted. Peer coordinator / Drive backup remain unadopted.

## GitHub Actions runner assignment — 2026-09-06

Inspected `origin` runs for HEAD `a2c2ff70bfdceb808e9d35eb277f9477b8db4ce2` and earlier branch commits.

Every failed job reports `runner_id: 0`, empty `runner_name`, and `steps: []`. Jobs finish in about two seconds. This includes Actions smoke, whose only step is `echo`. Android, handover-integrity, supabase, Meilisearch, search-pipeline and edge-functions therefore never checked out the repository.

This is the same assignment failure seen on `agent/animal-farm-gate1-recovery` and earlier foundation PRs. It is a **GitHub-hosted runner provisioning block** on this private repository, not a compile/test verdict.

Local CI-equivalent commands that did execute on this x86_64 host:

- `bash scripts/ci/verify-kotlin-architecture.sh` — PASS
- `bash scripts/ci/verify-visual-authority.sh` — PASS
- `node scripts/design/verify-handover.cjs` — PASS
- pack self-test — PASS (545 IDs)
- Deno 2.9.6 `deno check` of Edge Functions and search tools — PASS
- `deno test --allow-env supabase/functions/tests/supabase_api_keys_test.ts` — 4 passed
- Android compile/unit tests recorded in earlier ledger sections

Not executed here: `supabase start` / pgTAP, live Meilisearch Docker contract, search-pipeline script, emulator e2e. Those remain unproven, not locally failed.

Do not treat `runner_id: 0` as a source defect. Do not skip or weaken those jobs to manufacture a CI green. External action required: the repository owner must restore GitHub-hosted Actions runners/minutes for `ubuntu-latest`. No self-hosted worker is connected to this cloud run.

## Pending-sync attention owner — 2026-09-06

- `6c7d056a51cbe6457e12314d6d319a6b9ee0934d` — Management A “Waiting to sync” opens `GoatEntryPage.SYNC` / FOS-SYNC-002 instead of the task board.

`:app:compileDebugKotlin` and navigation self-test passed. No green-gate promotion.

## Task detail deep entry — 2026-09-06

- `b38a72f096de9ec893054ebadd5844cb9fe51d99` — FOS-TASK-003 detail; worker Review & decide opens `FarmDestination.Task`. Complete is the only write. No edit/attachment/recurrence invented.

`:app:compileDebugKotlin` and navigation self-test passed. Static inventory rebound below. No green-gate promotion.

Rebound static inventory: 545 rows; 146 some evidence; 399 none; runtime 0.

## Withdrawal attention owner — 2026-09-06

- `82365ba0525a53d121d362e42efc0d050ee77b26` — Management A “Withdrawal window open” opens `HealthEntryPage.WITHDRAWALS` / FOS-HEALTH-009 instead of the generic health dashboard. Worker Add Treatment now uses `HealthEntryPage.TREATMENT` (same FOS-HEALTH-007 owner). Withdrawal detail was not invented.

`:app:compileDebugKotlin` and navigation self-test passed. Static inventory rebound below. No green-gate promotion.

Rebound static inventory: 545 rows; 146 some evidence; 399 none; runtime 0. FOS-HEALTH-009 remains mapped-plus-code; FOS-HEALTH-010 remains no route evidence.
