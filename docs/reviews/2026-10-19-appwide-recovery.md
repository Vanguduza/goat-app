# App-wide native UX continuation — recovery checkpoint

Date: 2026-10-19
State: **RECOVERY STAGED; VM EXECUTION BLOCKED; APP-WIDE QUALIFICATION NOT RUN**

## Owner requirement and authority

The owner rejected the narrower home/search/rabbit correction and directed alignment of **all app pages and all app modules** with the Animal Farm visual style. The instruction to proceed remains active; it is not a visual-approval decision.

`AGENTS.md`, `docs/00_PROJECT_TRUTH.md` and `docs/ux/animal-farm-visual-lock/` remain the authority. This document is an execution checkpoint, not a new design authority, reduced MVP definition or completion certificate. Management retains A / Control room; worker retains D shell and C Review & decide. Existing domain, local durability, permissions, species separation and replication rules stay intact.

## Exact source boundaries

- Repository: `Vanguduza/goat-app` (GitHub repository ID `1341051068`).
- Existing PR: #112, draft and unmerged at the current read.
- Existing branch: `chatgpt/goat-ux-repair-20261018`.
- Published pre-expansion baseline: `0e3add77e016ed599c9cf2223dab944dde49a848`.
- Last observed expanded worktree: `/home/ubuntu/work/goat-ux-repair-20261018` on **dial-control**.
- Existing VM evidence: `/home/ubuntu/work/goat-ux-evidence-20261018/all-pages`.
- Recovery utility staged in commit `8fb615515b790c048623055818d50db505ecd1f4`: `scripts/development/recover_appwide_poultry_timeline.py`.

The current GitHub read still found the pre-expansion PR head. The broader changes were observed previously in the VM worktree and had not been published in that read. Do not reconstruct, replace, reset, clean, stash or rebase that worktree from this checkpoint. Preserve both tracked modifications and relevant untracked source before any integration operation. A fresh runtime read is required to establish its present state and writer ownership.

This recovery commit adds a utility and this checkpoint only. It does **not** contain the expanded Android changes, apply the fix to the VM, build an APK, change any reference image, or make an acceptance claim.

## Actual access results on this continuation

The direct `Global_DIAL.dial_global_status` call and the bounded `dial_estate_exec_prepare` inspection both returned JSON-RPC `32600: Session terminated`. No prepared-operation ID or executable grant was issued. No VM command from this continuation was executed through that route.

Desktop Commander's previous status check returned a monthly usage-limit pause with an instruction not to retry or reconnect that service. That path is not being retried or bypassed. The independent Work Tracker read remains available; its Muse portal reports NOT_READY and its dedicated submit/resolve route is not admitted. No replacement Muse task or alternate root route was launched.

GitHub reads and writes are available. They are used here for a narrowly staged recovery artifact, not as a substitute for the unpushed working tree. No network, VPN, OAuth, permission, VM lifecycle, CI infrastructure or production data change is authorized by this checkpoint.

The external unblock is a functioning Global DIAL MCP session. Existing app-change authorization does not need to be reissued merely because the transport is unavailable.

## Identified compiler failure and narrow correction

The last observed `app-compile-5-result.json` finished at `2026-10-18T17:51:11.383738+00:00` with exit code 1. Its source-fingerprint receipt reported no changes during that compilation. Later Analytics edits were observed separately; therefore that failed run does not validate the newest worktree.

The first syntax failure was in `feature/ops/src/main/kotlin/com/farmos/feature/ops/PoultryFlockRecordScreens.kt`, around line 163:

```kotlin
recordsContent = {events.size}") {
```

The earlier text-splicing operation selected the first `{` in a section expression. That character belonged to Kotlin string interpolation in `"Events · ${events.size}"`, not the composable lambda. The resulting fragment is malformed source. The intended correction is only:

```kotlin
recordsContent = {
```

The existing `events.forEachIndexed` body, `HorizontalDivider`, `FlockRow`, event tags, selection and read-model behavior must be preserved.

The staged utility is read-only by default. It checks the expected VM worktree, branch, baseline ancestry, owning function and exact observed body. It refuses absent, duplicate or changed source. Applying requires the freshly observed SHA-256, creates a byte-for-byte backup outside the Android source tree, uses a temporary replacement file, and verifies the readback. It is idempotent for its exact corrected form. It performs no network access, branch update, Gradle launch or permission change.

Seven pure-text regression tests are included: exact correction, idempotence, refusal of the older published implementation, refusal of a changed record body, duplicate functions, corruption outside the function, and unknown malformed variants. **These tests have not been executed in this continuation.** They are preparation, not evidence of passing tests.

Only execute the utility through an authorized VM route after establishing exclusive writer ownership. Its digest checks do not replace the normal writer lease. Do not pull blindly into the dirty worktree merely to obtain the utility: retrieve its immutable GitHub source separately into the existing evidence area, inspect it, and invoke it there against the fixed worktree.

## Dependency-ordered continuation

### 1. Preserve and reconcile, without restarting unrelated work

Read the live branch, HEAD, tracked/untracked source, current processes, latest compilation receipts and governing design pack. Resolve any active writer before editing. Make a bounded source/evidence checkpoint that excludes credentials and generated bulk artifacts. Identify every additional local change after the last compilation; do not assume the historical file is still malformed.

### 2. Restore a compiling integrated source

Execute the utility's pure-text tests on the VM, inspect the target, and apply only if its exact preconditions hold. Rerun `:app:compileDebugKotlin` with the existing toolchain and bounded worker/memory settings. Fix subsequent real compiler errors in reviewable batches; retain a fingerprinted receipt for each run. Do not widen dependency boundaries, remove modules, remove tests or weaken architecture ceilings merely to compile.

### 3. Complete page-level coverage, not just module-home coverage

The previous inventory of **151 UI source files** is a file inventory, not proof of 151 screens or proof of complete coverage. Project Truth defines **545 registered screen templates**. Reconcile every registered Screen ID and applicable role/device/state variant, including non-route atoms, against its owning source and the approved page pattern. Record distinct statuses: inspected, implementation changed, compile-verified, interaction-verified, native-reviewed and owner-approved. None are interchangeable.

The coverage matrix must include these families:

| Family | Required alignment and appropriate visual behavior |
|---|---|
| Goats, rabbits, sheep, cattle | Module homes, lists, animal profiles, focused capture, identity, parentage, breeding/birth/weaning, production, exits and history; species-native timelines and boards based on actual records. |
| Poultry | Kinds, houses, flocks, placement/moves, flock days, hatchery, incubation, candling, hatch results, health, biosecurity, closeout and reports; do not impose mammalian workflows. |
| Health and tasks | List/detail/capture and review surfaces, clinical references, protocols, vaccination, withdrawals, schedules, recurrence, assignment and attachments; expose real states without claiming diagnosis or completion from elapsed time. |
| Feed, water, inventory and pasture | Planning, stock/lot movement, count/review, source/point events, grazing start/end and occupancy; charts keep units and periods explicit and maps never invent geography. |
| Finance, sales and procurement | Transactions, documents, customers/suppliers, capture/review, budgets and actuals; currency-separated exact amounts and only recorded commercial states. |
| Labour, groups and assets | Worker/register/log/attendance views, memberships/census/moves, maintenance and documents; workload and service history must not masquerade as assignments or future completion. |
| Capacity, genetics, analytics, decision intelligence, simulation and AI | Focused inputs, comparisons, evidence, outputs and uncertainty; keep modelled/planned states separate from recorded facts. |
| Search, reports, administration and system pages | Search suggestions and filters, export/document views, farm setup/switching, roles, audit, local entry/auth, sync/conflicts, settings, empty/error/loading/offline states and permissions. Use consistent layouts rather than adding ornamental workflows where none exist. |

Shared colors alone are insufficient. Check typography, spacing, shapes, illustrations, hierarchy, primary/secondary actions, button affordances, consistent navigation and appropriately grouped tools. Remove development prose from task-focused screens without concealing safety information. Do not reduce a complex module to a long stack of cards or buttons. Every feature must remain discoverable and reachable through its intended owner/worker route.

### 4. Behavioral and native qualification

Run full applicable Kotlin/unit, Room, command-ownership, farm-scope, navigation/Back/restoration and interaction checks. Verify drafts survive relevant section changes without emitting writes. Whole-farm search/selectors must query before limiting; an initial list window must not exclude eligible records. Unknown/loading metrics remain unknown, not zero. A planned date is not a completed event; future-dated maintenance and expired withdrawal dates must retain their actual semantics.

Review actual native Compose captures and interaction bounds across light, dark and outdoor modes, phone/tablet widths and enlarged text. Existing candidate fixtures propose widths 360/411/780 dp and font scales 1.0/1.3/2.0; confirm these against the canonical matrix and add missing cases rather than reducing it. Capture representative workflow states in every module and retain per-Screen-ID coverage, not merely a small gallery of dashboards. Generated reference images and implementing-agent review cannot substitute for owner visual approval.

### 5. Publish and deliver

After preserving and reconciling the VM changes, publish reviewable commits on the same repair lineage. Keep #112 draft until required owner-native approval. Record the exact new Kotlin source SHA, tests and native captures; build and verify a newly identified APK with the retained authorized signing arrangement. Do not uninstall the owner's phone app or erase local farm data. Physical S24 verification and installation require a live authorized device route; do not describe emulator evidence as physical-device evidence.

## Evidence boundary

The earlier 809 passing Android tests, 60 connected tests and 26 native captures belong to the narrower published baseline. They remain historical evidence and cannot certify the expanded worktree or this recovery utility. No new `VISUAL_GREEN`, `FEATURE_GREEN`, `MODULE_GREEN` or `MVP_GREEN` state is asserted here.
