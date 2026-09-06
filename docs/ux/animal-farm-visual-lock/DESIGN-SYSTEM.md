# Visual system and theme pack

## Accurate description

**Soft, image-led operational modernism** is a useful short description: a quiet pale green/ivory background; forest-green primary blocks; restrained pale-lime emphasis; white secondary surfaces; gently rounded geometry; readable sans-serif type; large, clear action labels; compact meaningful metrics; realistic animal portraits; and direct, tactile controls. The hierarchy changes with the job. A priority card is prominent, an operational row is compact, and a capture screen is calm and focused.

“Farm themed” means recognizable animal identity and grounded colors, not barns, hills, clouds, script slogans and decorative scenery behind all content. “Modern interactive” means that the user can filter, select, inspect, record and recover—not that every page becomes a carousel or a wall of identical cards.

## Exact colors versus native baseline

The locked HTML is the visual reference for homes. Its exact light palette is:

- Background `#F3F4ED`; surface `#FFFFFF`; soft surface `#E8ECE3`.
- Ink `#183328`; muted ink `#5B6A5E`; divider `#DCE3D7`.
- Primary `#285640`, on-primary `#FFFFFF`.
- Lime `#DCEBBA`, on-lime `#294128`.
- Warning surface `#F8E4B9`, warning ink `#67420D`.

The exact dark counterparts are in `theme/locked-web-tokens.json` and `theme/locked-web.css`, extracted from the retained HTML—not approximated from a photograph of a monitor. Use semantic foreground/background pairs together.

The older native theme uses nearby but **different** values, including canvas `#FAFAF6`, home hero `#285744`, lime `#DFEDBA`, native general primary `#2C5539`. Those values are retained in `references/native/FosTheme.kt` and `HomeTokens.kt` for migration context; do not call them byte-exact HTML colors. For the new common theme, use the locked web colors for the visual home match, retain Inter and native accessibility semantics, then freeze the approved native token manifest. Any change to those reference colors requires a recorded decision, not silent averaging of palettes.

Outdoor is required by the existing screen standard but has no approved outdoor web preview. Retain the older native outdoor baseline (white canvas, black ink, dark green `#23432D`, 64dp targets) as a **candidate requiring native acceptance**. Do not certify it from light-mode screenshots. Semantic error, success, information and withdrawal colors may be adapted from the native baseline, with measured contrast and non-color indicators.

The extracted web file includes unused exploration tokens/styles because the supplied HTML is preserved exactly. Their presence does not authorize B's atlas or another page style. Use only components defined by the selected lock and the approved recipes.

## Typography

Keep the existing native **Inter** family and bundled license. The locked browser concept uses `Segoe UI, Roboto, Arial, sans-serif`; this is not evidence that it used Inter or Caveat. Use Inter for the Android adaptation without introducing another font family. Keep numbers tabular where alignment matters. No handwritten/script heading or brand slogan is part of this lock; the later repository's Caveat accents are not a reason to add them throughout.

Start from the retained native scale: display 28/34sp semibold, large title 22/28, small title 16/22, body 15/22, label 13/18, numeric 24/30. The web focus task title is 30px, regular-medium, and management hero title 26px; preserve their relative emphasis in the native pilot, not an assumed px=sp conversion. Test actual Inter variable weights. Text grows vertically at 200%; never fix a card height around one English sentence or shrink text to fit.

## Geometry and spacing

The locked web reference has a 390px demonstration viewport. It is not a production fixed width. Its key dimensions are: management hero radius 24px; focus card radius 28px; secondary tiles 20px; primary actions/module controls 16px; content horizontal padding 18px; header padding 22px; hero padding 20px; focus padding 21px; two-tile gap 10px; module grid gap 8px. These are recorded in the theme pack as source geometry.

Native adaptation uses semantic dp tokens: spacing 4/8/12/16/24, page inset responsive around 16–22dp, action radius 16dp, primary card 24dp, focus card 28dp. Do not impose 24dp radius on a compact row or 28dp on every surface. The native pilot resolves the remaining optical differences and freezes the approved values once. Feature authors then consume tokens, not their own dp constants.

Use flat surfaces with subtle structural dividers. No heavy shadows, glass blur, neon, indiscriminate gradients or default Material purple. Keep icons from one approved line/rounded family, usually 24dp; icon-only controls have accessible names. Pointer-sized controls in the HTML are not the native accessibility standard: use at least 48dp and 64dp outdoors.

## Management A composition

Top: compact brand/farm/date context and clear page title. Main content: one dark forest priority-attention hero with short category, actionable title, count/context, relevant identity image and a clear review entry. Below: a two-up work tile and resource tile, lime emphasis against a white counterpart. Then concise module launchers; species selection uses the correct family images. Bottom navigation remains stable, with the Theme-only gear at the bottom right.

Attention is driven by ranked real exceptions, not a fixed Nala demo. Work summary explains execution status; resources explain real actionable availability/expiry/shortage. Further permitted economics, health, people and capacity summaries can extend below the core composition or into owning dashboards without obscuring that hierarchy. No blended cross-species growth metric. Every number links to its contributing records and freshness.

On narrow screens or large text, stack the two tiles and hero image/text without losing action labels. On tablets, use a balanced command-center grid with drilldown; do not stretch a 390px card across the display or replace A with an unrelated desktop dashboard.

## Worker D+C composition

Keep day/date header and work summary, then the stage selector. The central **Review & decide** region contains a single prominent lime card with category and due chip at top; centered animal/subject image; subject name and explicit family; large concise task title; one relevant fact; and full-width forest action at the bottom. Under the card: previous/next controls, readable position and short pagination indicators. Keep quick recording, resources, task-linked guides and own updates reachable.

No automatic sliding. Swiping is optional in addition to buttons. Filter by selected stage; reset or restore position deliberately; clamp after data changes; return from detail to the same task when still present. An empty stage stays empty. Do not turn every task into an approval task because the heading says “decide”. Do not copy the concept's stage names into a new domain enum without the approved transition matrix.

The worker can review an assignment, open/continue it, record permitted results, attach findings, access approved instructions/resources and escalate through the approved workflow. Administrative, commercial and clinical powers remain denied unless independently granted. Guides are not introductory paragraphs above every action.

## Login and authentication

Center **Animal Farm** above the original lineup. Place the lineup above the credential fields, preserving image proportions. The image shows realistic goat, rabbit, hen, sheep and cow; it is not a geometric mascot scene. Keep **Sign in** centered and remove “to your farm”, “Welcome back” plus repeated orientation copy where it duplicates the heading. Use clear labels, appropriate keyboard/autofill, password visibility and concise field-level errors when those interactions are implemented. Never add social-provider buttons without working contracts.

Preserve current sign-in/session/farm-membership callbacks. Account creation, recovery, invitation and farm setup are distinct registered workflows, not decorative buttons on a login mockup. Small screens with keyboards may scroll the image away; credentials and submit remain usable. Never persist plaintext credentials as an offline draft.

## Asset and identity contract

The pack contains `farm_animal_lineup.png` (1536×1024) and `farm_family_portraits_v1.png` (1774×887). The derivative is opaque pale ivory, not transparent. Do not recolor or regenerate it for dark mode. Use a deliberate image plate consistent with the approved native review. Preserve proportions and species silhouette; do not crop ears/horns accidentally.

The retained `AnimalPortrait.kt` documents source-sheet crop windows. Reuse the windows as evidence, then visually verify the crop at actual display sizes. Do not assume another resized image uses the same coordinates. Exact asset hashes are in `reference-manifest.json`.

Family mapping: goat→Goats; rabbit→Rabbits; sheep→Sheep; cow→Cattle; hen→Poultry family launcher. A duck/turkey/goose workflow must not display a chicken as its subject. Use correct approved kind imagery or a neutral labeled placeholder until available. The synthetic brand animal is not a photograph of Nala.

For every individual profile, provide authorized Add photo / Change photo / Remove photo, system image selection, subject-and-tag preview, Use photo confirmation and cancel. Keep the previous usable photo on selection/validation failure. The same personal portrait should appear on profile, list, task card/detail and other permitted identity surfaces. Family launcher remains unchanged. Do not create a personal animal out of a cage, litter or flock.

Use the existing attachment pipeline for private farm-scoped media, durable pending state, restart/retry, replacement conflicts, revocation and orphan cleanup. Normalize orientation and strip location metadata from derivatives; validate decoded type/dimensions/size. The old preview's 10MB/25MP bounds are preview policy, not a silently approved final server limit. Reading a profile does not grant editing. Never ship public bucket URLs or in-memory-only photos as completed persistence.

## Shared components to implement once

`AnimalFarmScaffold`, `FarmHeader`, `HomeThemeButton`, `ManagementAttentionCard`, `WorkSummaryTile`, `ResourceSummaryTile`, `FamilyLauncher`, `WorkStageSelector`, `ReviewTaskCard`, `TaskCarouselControls`, `QuickCaptureAction`, `ResourceRow`, `GuideEntry`, `AnimalIdentityHeader`, `ProfilePhotoEditor`, `ScopedRecordList`, `CaptureSection`, `FieldWithUnit`, `PrimaryActionBar`, `StatusReceipt`, `SafetyNotice`, `TimelineEntry`, `MetricWithDrilldown`, `ChartTableSwitch`, `ComparisonPanel`, `PlanBoard`, `SelectionSheet` and `ConfirmationSheet`.

These are target component responsibilities, not claims that these symbol names already exist. Each requires loading/empty/error, keyboard/TalkBack and adaptive tests where applicable. UI accepts typed state and emits intents; it does not own business writes. Add a new shared component only when a real page job cannot be expressed cleanly by the existing set.
