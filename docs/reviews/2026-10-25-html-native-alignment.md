# Native UI alignment to the approved dashboard HTML

## Source authority

Owner instruction, 25 October 2026: the native app must look and interact like the repository HTML, not merely use its colours. The immutable source is `docs/ux/animal-farm-visual-lock/references/animal-farm-locked-homes.html`, Git blob `6baaea08be28a749a253d12e5e4d6bea98c8c15d`, SHA-256 `94766ebbd122861b6c4dbfe65e6908b21895bc8ea44596475899cb45fc49fd11`. The source, original illustrations, fonts, locked pixel references and independent approvals are not replaced.

## Reusable native visual system

`AnimalFarmHtmlStyle.kt` and the shared controls carry the browser-measured hierarchy: normal phone content inset18/header22; hero radius24/padding20; paired summary radius20/gap10; compact module radius16/minimum78; worker review radius28/padding21; sheet top radius26; primary radius16; secondary14; input12. Header28, hero26, task30, section16 and body15 styles use the approved native Inter adaptation with explicit measured line heights. Native typography applies to Material-backed feature screens, not just Home.

The exact approved Lucide SVG paths are retained as Android vectors with their original ISC/Feather license. The family-art asset and crop coordinates are unchanged. Personal animal photos must remain owned by the existing farm-scoped attachment/selection pipeline, not inferred from generic imagery.

## Actual dashboard composition and interactions

Management: compact farm/date header and Farm pulse title; an actual priority task/restriction hero; paired Work and Resources summaries; six icon modules (Animals, Team, Breeding, Finance, Inventory, Reports); icon footer Home, Work, Record, Modules and Theme. Animals opens an illustrated family sheet. Secondary features and Sync stay in the existing farm-tools menu, not a second homepage stack. The hero opens the actual priority task where present; generic work and unknown restriction states do not borrow another animal's identity.

Worker: My work/Work board header; day banner; underlined stage tabs and counts; image-led Review & decide card; explicit previous/next controls, dots and readable position; optional swipe; Weight, Finding and Resources tiles; Home, Work, Record, Guides and Theme footer. Opening a task never marks it complete. A farm/actor/role-scoped retained UI state keeps the visible task and scroll on return; loading does not erase the selected ID. Native modal sheets have actual focus/Back/IME behaviour.

## Functional adaptations, not fabricated HTML demo logic

The browser's Active/Review toggles are mock interactions; the current domain exposes open/done with due/upcoming projections. The port uses To do/Later/Completed with the same stage treatment until a governed real transition exists. Native controls must retain48dp normal and64dp outdoor targets, 200% text and natural wrapping, rather than shrinking text to imitate a fixed browser viewport. The original Light/Dark theme sheet is retained, with the required native Outdoor variant. Header context and card facts come from real farm records, not copied Nala/CageB sample values. The identity resolver refuses cross-farm tasks and uses actual animal name/tag or cage code. Guides may open real task details and attachments but never fabricate veterinary advice or sample nest-box quantities as production records.

## App-wide application

Shared operational-page headers, form fields, primary/secondary buttons, section surfaces, tool groups and resource/action rows use the same geometry and type hierarchy. Existing feature-specific owners and capture/read-model workflows remain in place across all modules. A module's stock, finance, breeding, nesting or grazing visual must preserve its real units and state semantics. Adopting the shared style is not evidence that every individual page/state has been visually reviewed.

## Validation and delivery boundary

Five initial actual native assertions failed against the old dashboards before implementation. New tests verify the reference hierarchy, horizontal hero portrait, paired tiles, six-module grid, native family/theme sheets, exact selected-task navigation and carousel state. Original permission, durable-record, Back and feature-owner tests stay in place and traverse the actual replacement menus. Separate read-model tests cover identity/farm isolation.

The `HTML-to-native dashboard parity` workflow retains original browser renders and real Compose captures for direct review, with exact source identity. Its captures are unapproved candidates, not regenerated goldens. Passing compilation, structural tests or the full device/theme matrix cannot replace inspection of the actual visual result. No full-app visual certificate, main merge or production release is granted by this change.


## App-wide completion of the shared presentation layer

The owner’s follow-on instruction applies this rebuild app-wide. The source adoption audit inspects all 156 current Composable source files in the app and species/operations features. The HTML-derived global theme is selected by the app theme, with no alternate feature palette/typography or default Material button/field implementation found. Seventy source files use the operational-page shell, 38 the grouped tool menu, 31 the metric strip, 28 the alternate-view tabs, 25 the recorded/planned timeline, and 20 the chart/table component. These are source-consumer counts, not runtime screen-approval counts.

The operational shell now uses the same content inset, compact heading and section spacing. Alternate views use underlined reference-style tabs while true filters retain selectable chips. Metric tiles remain white or explicitly emphasized lime, retain exact value text and units, and offer a drilldown only when the existing owner supplies one. Species, task and search shells share the spacing; selectors, record rows, resource actions and the native sign-out confirmation use the same shape/type/palette system. Rabbitry uses the same metric and view navigation components while preserving breeding, nesting and cage-rotation owners. Flock/hatchery structures are not replaced with individual-mammal structures.

The display name is Animal Farm, including the launcher, clinical disclaimers, settings and Copilot. The Android package, database, protocol identifiers, Google Drive folder defaults and every clinical prohibition remain unchanged.

`verify_html_style_adoption.py` and its negative tests prevent newly added app/feature controls from silently importing a second default Material design system. This check is a source-level regression guard, not a pixel-based approval or a substitute for inspecting real native output.

A first integrated run completed 62 native/unit checks with zero failures, errors or skips, including six dedicated light/dark/dashboard-sheet captures and the 200% text footer check. That run preceded display-brand wording changes. The current full integration and source-bound remote workflow results must be recorded separately; do not relabel those initial results as final full-product certification.
