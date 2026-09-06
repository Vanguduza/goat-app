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
