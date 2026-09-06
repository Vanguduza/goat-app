# Existing implementation alignment and anti-drift gates

## Evidence-backed starting point

Inspected source snapshot: `e86981682748605e9db1570224e8c801c35b6058`. This is static code/document inspection, not a completed device-level visual or functional audit. The supplied registry's `VISUAL_IMPLEMENTED` labels are claims of code existence, not visual acceptance.

Current source differences that must be addressed:

- `app/.../FoundationAuthScreen.kt` uses `FarmPastoralBackdrop`/`FarmOsWordmark` and includes “Sign in to your farm.” Restore Animal Farm + exact lineup + centered Sign in while preserving membership and auth callbacks. Remove redundant engineering explanations from the production screen; keep truthful concise recovery states.
- `app/.../RoleDashboardScreens.kt` management uses a pastoral backdrop, story surface, slogan and explanatory action grid. Worker uses a generic My Tasks section, explanatory copy and quick links rather than the locked stage-filtered D+C card. Rebuild composition using P02/P03; retain valid state adapters and exact permissions. “Record Weight”/scan must resolve to subject-scoped routes, not merely the goat module. Inspect “Add Treatment” authorization rather than inferring safety from visibility.
- `core/design/.../FarmIllustratedComponents.kt` draws backdrop/animals with Canvas and fixed cream surfaces. Replace brand identity with approved raster assets and semantic theme surfaces. Retain generic useful primitives only after restyling. A code-drawn goat is not the supplied animal. Fixed light surfaces create a dark-theme consistency risk that requires native verification.
- `core/design/.../FarmOsTheme.kt` and `FarmOsTypography.kt` contain a different visual system, including Caveat accent usage in the app. Consolidate one governed family around the recovered lock, preserve Inter, add outdoor mode and test all Material fallback roles. Do not change theme globally without checking dialogs, menus, fields and safety colors.
- `FarmHomeScreen.kt`, `SpeciesNavigatorScreen.kt`, `SpeciesHerdScreen.kt`, `GoatExperienceScreen.kt`, `GoatCaptureScreens.kt`, `TasksBoardScreen.kt` and the rabbit/health/inventory/finance/poultry/sheep/cattle operating screens need a route-by-route composition review. Their source-to-ID claims are preserved in the registry index. Do not assume a filename serving several IDs has implemented every state or navigation path.
- `docs/ux/FARM_OS_CURRENT_UI_IMPLEMENTATION_MAP.yaml` cites an older baseline (`5e9335...`) and does not enumerate the later role dashboard file in its surface list. Rebuild the map from actual routes and feature contracts at the current HEAD.
- The quantum YAML contains the base atlas plus eight role variants while its footer says 537. The old generator only enumerates base modules and would drop those variants if run unchanged. Compute counts from IDs and make registry round-trip tests preserve variants; never change scope to satisfy a hard-coded count.

## Migration sequence

### Gate 0 — Recover authority and inventory

Import this pack into `docs/ux/animal-farm-visual-lock/` of the actual implementation checkout. Merge the rule snippet into AGENTS.md; optionally install the always-on `.mdc` rule. Amend conflicting visual authority, role contracts, reference manifests and CI comments. Do not replace product architecture truth.

Capture live SHA, dirty work and route inventory. Compare all current registry IDs with this snapshot. Distinguish route, role variant, alias and reusable atomic surface. Join each feature to at least one route or an explicit headless contract. Repair the source registry/generator mismatch without renumbering. Keep green flags false until real evidence exists.

### Gate 1 — Shared foundation and native reference set

Create the semantic token layer and shared components. Bundle exact assets/font/license. Preserve fallback identity and dark-mode image plates. Establish native reference screens: login, management A, worker D+C, species navigator, goat dashboard/profile, weight capture, safety/status review, list/filter, a tablet master/detail and Theme/outdoor selection.

Render against deterministic fixtures. Match locked homes visually and behaviorally. Freeze owner-approved native goldens and token version before broad fan-out. Native reference source in this pack is historical and partially implemented; it is not a drop-in complete module and not a certified golden. Do not bulk-copy its debug demo data or in-memory photo implementation.

### Gate 2 — Restyle implemented routes without regression

Start with auth/home/navigation, then goat identity/capture and the designated offline weight slice. Continue tasks/health/inventory, rabbit, sheep/cattle/poultry and existing shared operational pages. For each batch preserve commands, typed state, route parameters and permission tests; change presentation and missing interaction glue with explicit tests. Keep one coherent feature contract or tightly coupled shared component slice in flight.

Re-run `Register Goat → Record Weight Offline → Restart → Sync → Reconcile → Search → Second Device` after presentation integration. Architectural success does not certify all goat features. Do not broaden batches before compile/test evidence is available.

### Gate 3 — Fill remaining quantum contracts

Use the page index, not whichever module is convenient. Finish missing list/detail/history/capture/plan/reference/report/atomic states in each species and shared module. Resolve exact feature fields and routes before implementing. Implement actual commands and reads where authorized by the development plan; never render a menu item and call the feature done. Complete individual photo persistence and attachment state integration across all eligible species.

### Gate 4 — Whole-product acceptance

Every registry ID has an implemented contract, tested owning-route entry/return, required state evidence, approved native visual lineage and independent acceptance. Every mandatory feature passes the project FIC definition; every module is green only from its constituent features. Complete device/field/security/performance/recovery certification before MVP_GREEN. A clean screenshot alone is not a product gate.

## Per-screen contract and evidence

Complete `templates/screen-contract.json`: job, precise feature IDs, roles/grants, scope, routes/deep links, data source, fields/units/bounds, commands/effects, composition/component mapping, reference, state fixtures, accessibility, device layout and test IDs. A missing mandatory field blocks design-ready. Declared N/A needs a reason and test of the actual boundary.

Required states include initial/loading/content/empty/error/disabled, validation, offline/stale, draft, local committed/pending, syncing/retry, conflict/rejected/dead-letter, auth expired/revoked/denied, partial outcomes and confirmation where applicable. Pure local preferences need not simulate nonexistent network states. Critical pages keep critical state evidence rather than claiming equivalence to a normal form.

Required native matrix: light/dark/outdoor; font scales 1.0/1.3/2.0; widths 360/411/780dp; keyboard/safe insets; long content; TalkBack; locale/RTL where supported; permission variants. Expand all applicable intersections or record an independently approved risk-based reduction—never silently omit large text or outdoor. At tablet widths, test actual two-pane planning/data views, not stretched phone UI.

Behavior checks: stage filtering/pagination bounds/return restoration; no wrong-task completion; real quick routes; fallback/personal photo isolation; pick/cancel/replace/remove/failure/restart/revocation; no fake totals; consistent details/drilldown; required guide/resource access; safe drafts and duplicate-save protection; no lost action behind keyboard/navigation; all icon labels and targets.

## Enforceable anti-drift layers

1. **Reference integrity:** hash exact references/assets/font and require separate review to update the manifest. `scripts/verify-pack.cjs` supplies this baseline.
2. **Single token source:** generate or validate Kotlin/CSS from the approved semantic manifest. Add a Kotlin-aware lint rule disallowing raw colors/font families/shape constants outside governed design code, with explicit reviewed exceptions for chart data, images and platform APIs. A regex-only lint is advisory, not proof.
3. **Component governance:** feature code composes approved primitives; the shared component catalogue owns variants/states. New components get a native reference and acceptance before reuse.
4. **Registry/route coverage:** export actual Navigation graph and aliases; compare exact sets with the registry. Join route→screen→feature→command/evidence. Check dialogs, sheets and role variants. Preserve base/variant counts separately; reject duplicate IDs, stale generator output, orphan routes and features.
5. **Native screenshots:** compare per-page/state fixtures to approved goldens at fixed renderer/device/font configuration. Report diff images. Set thresholds from renderer noise in the pilot, not an arbitrary generous percentage. Require review for geometry, text or asset changes even when aggregate pixel difference is small.
6. **Interaction and domain tests:** visual regression cannot prove that a CTA works, an upload persists, a worker is denied or a task completes correctly. Test each separately and retain existing backend/local invariants.
7. **Evidence-bound status:** test artifact hashes, tested SHA, CI run and independent approval determine green. `--release` in the pack checker checks record completeness for selected IDs; it does not execute Android tests or authenticate an approver.
8. **Repository controls:** install required CI statuses and CODEOWNERS/branch protections on tokens, reference assets, goldens, registry/generator and authority docs. A maintainer must approve these controls and visual-baseline changes. Do not assume local files can enforce remote branch protection.

The implementing agent may not approve its own baseline update or disable a failing gate. It can record an exception request. An authorized human/independent reviewer must approve exceptions. Policy files alone are not tamper-proof; protection settings and review ownership provide the missing enforcement.

## Autonomous execution loop after authorization

Select the next unmet, unblocked contract → confirm exact IDs and dependencies → read source/recipe → write failing tests → implement the smallest slice → compile and test → render/diff → independent review → update evidence/status → commit a reviewable batch → repeat. Persist a ledger with last proven SHA, attempted slice, open failures and next action. Stop the affected slice for unresolved domain/permission/visual decisions; continue unrelated safe work. Do not create a scheduled loop or deploy from this guide alone.

Never spend repeated cycles rewriting all screens while tests are unavailable. Keep last proven evidence pinned. A stale CI green from another commit or build type is not current certification. Ensure the release pipeline actually assembles and tests the Android artifact, not only source compilation.

## Status vocabulary

`MAPPED` means inventory only. `CONTRACT_READY` means reviewed specific design/behavior contract. `IMPLEMENTED_UNVERIFIED` means code exists. `VISUAL_GREEN` means approved native visual/state/accessibility evidence at a specific SHA. `FEATURE_GREEN` additionally requires the complete functional contract and project gates. `MODULE_GREEN` and `MVP_GREEN` derive from complete mandatory coverage, never manual optimistic labels.
