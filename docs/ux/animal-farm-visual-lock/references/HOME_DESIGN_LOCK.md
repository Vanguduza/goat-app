# Animal Farm — approved home direction and animal identity imagery

Owner decision: 31 August 2026. Status: **design direction locked**. Native implementation, persistence and release gates remain open.

## Decision and authority

The owner selected **A / Control room for management** and **D / Work board for workers**, replacing D's plain task-card region with **C's Review & decide carousel**. The supplied `20260831_141041.jpg` identifies the D card to replace; `20260831_140944.jpg` identifies the desired C card and carousel controls. The first image is not the management attention tile, so A's management composition remains intact.

The owner also directed reuse of the login animals for their respective animal families and a personal photo-upload option for every individually profiled animal. This is an explicit, narrowly scoped owner amendment to the earlier text-led preview and the login-only imagery restriction. It does not approve new clinical rules, task transitions, role grants, storage contracts or arbitrary redesign of every module. The Constitution, locked source specifications and accepted EDR files remain unchanged.

The four-option exploration remains a historical reference. The selected composite, not a runtime A–D style picker, is the implementation target. Management and worker layouts follow authenticated permissions; the comparison's role switch is not an app feature or authorization mechanism.

## HM01 — management: A / Control room

- Retain the farm-wide command centre: priority attention card, work summary, resource summary and module launchers.
- Use real scoped projections and freshness in the integrated app. No invented finance, stock, animal population or completion totals.
- Module entry stays species-native. A family launcher is not a mixed-species animal list.
- Named-animal attention cards use the animal's selected profile photo, falling back to the approved family image. Family labels remain explicit.
- Retain the bottom-right Settings icon with Theme only on the home surface.

## HM02 — worker: D shell with C task carousel

- Retain D's workday header, work-stage navigation, work summary, quick recording, resources and guides.
- Replace the central stack of plain task cards shown in image 1 with the richer C card: task category, due date, subject image, subject name/family, action title, relevant fact and a prominent permitted action.
- Retain the requested **Review & decide** section heading. It means reviewing the assigned task and choosing a permitted next action, not granting management or clinical approval powers. Worker CTAs remain Open task / Continue task / Record result as appropriate.
- Each stage filters the carousel. Its count and pagination refer only to that stage. Switching stage resets the card index; removing or moving a task clamps the index safely.
- Provide previous/next buttons and a readable position indicator; touch swipe is an additional production interaction, never the only way to navigate. No auto-advance. Restore task/position on return from detail.
- Empty stage shows a concise empty state, not a task from another stage. The stage labels in the concept are illustrative; D15's approved transition matrix still governs the native model.
- Capture, linked guides, resources and own recent updates remain reachable. A completed card requires the real domain outcome; starting or opening it is not completion.

## Family imagery versus personal identification

Use the existing `core-design/src/main/res/drawable-nodpi/farm_animal_lineup.png` as the family-art source: goat → Goats, rabbit → Rabbits, sheep → Sheep, cow → Cattle, hen → Poultry family launcher. Do not substitute the geometric concept animals.

The preview uses clean, separated portrait artwork derived from that image, saved as `core-design/src/main/res/drawable-nodpi/farm_family_portraits_v1.png`. A size-optimized embedded copy and code-defined portrait windows keep the preview compact. The original login asset is unchanged. These are synthetic brand animals, never evidence that the illustrated goat is Nala. A small family portrait stays independent of the personal animal photo. The selected derivative is opaque on pale ivory, not a transparent cutout; [asset provenance and both editing prompts](ANIMAL_FAMILY_ART_PROVENANCE.md) record this explicitly.

The hen represents the poultry family launcher, not all poultry kinds. Inside a duck, turkey, goose or other kind-specific workflow, use the actual subject photo or a neutral labelled placeholder until correct approved kind imagery exists; never silently illustrate a duck as a chicken.

An individual photo appears consistently on its own profile, list row, assigned-task card, task detail and other permitted identity surfaces. Name and farm tag remain visible; an image never replaces canonical identity or scanning. Cage B, a litter, a mob or a flock is not a fabricated individual animal profile. Their media stays on the correct group/location record.

## Profile photo interaction contract

Applies to individual profiles throughout Goats, Rabbits, Sheep, Cattle and individually tracked poultry, including GT02/GT03, RB23, SH02, CT02 and the existing poultry breeder/identity surfaces. It inherits SY03's attachment queue.

1. Profile shows the current personal image or family fallback, plus **Add photo** / **Change photo** when authorized.
2. Select one image. Android uses the system photo picker with selected-item access, not a request for the whole photo library. Camera capture can be a separately granted contextual entry; it is not required to upload an existing photo.
3. Preview the subject name/tag with the selected image and confirm **Use photo**, or cancel without altering the current image. Preview implementation accepts JPEG, PNG and WebP up to 10 MB; native limits, formats and camera policy must be finalized with the attachment contract.
4. **Remove photo** confirms, then returns to the family fallback. Removal does not delete the animal or silently erase audit evidence.
5. Keep the old usable photo until a replacement is validated. Failed/cancelled selection or decoding must not blank an existing portrait. File contents must decode as an allowed image, not merely have a matching extension.
6. Show local/pending/uploaded/failed state accurately. Offline selection survives restart in the native app; retry uses the same operation identity. Changing an image in the concept is only an in-memory preview, not an uploaded or durable farm record.
7. Derive `farm_id` and animal identity from trusted context. Reading a profile does not automatically grant photo editing. Enforce permissions for selection, association, replacement, removal, cache reads and server object access.
8. Keep media private and farm-scoped. Normalize orientation and strip location metadata from upload derivatives; bound decoded dimensions and output size. Never log local URIs, image data or private object URLs. Use scoped object references, not a public URL as authorization.
9. Define replacement conflicts, cancellation, removal, revoked access, orphan cleanup and account-switch cache custody before native integration. Reuse the approved attachment pipeline; do not invent a second photo database or independent upload source of truth.

This elaborates an existing product capability: the master plan's Animals model already contains `photo_url`, and its sync section already specifies a photo queue. It does not authorize hand-editing the example schema into a migration. Schema, Room, grants, durable operation and policy tests remain one coordinated implementation gate.

Engineering references checked 31 August 2026: [Android photo picker](https://developer.android.com/training/data-storage/shared/photo-picker) and [persistent background work](https://developer.android.com/develop/background-work/background-tasks/persistent). These support the proposed native integration, not a claim it is built.

## Acceptance and delivery boundary

- Locked comparison has only management and worker destinations; rejected B and standalone C are not selectable app layouts.
- A remains the management composition; D retains its work stages and uses C inside the selected stage.
- Same Nala/Cage B fixture content is retained. Profile photo changes affect only Nala, not the Goats family image or rabbit/group imagery.
- Verify stage filtering, pagination bounds, details/return, photo pick/review/cancel/confirm/replace/remove/error and theme-only settings at narrow widths and in both preview themes.
- Native delivery still requires failing-first tests, all five load states, light/dark/outdoor screenshot coverage, 200% text, TalkBack and physical-device checks. Photo durability, tenancy, pending uploads and retry/conflict tests cannot be satisfied by the browser concept.
- Keep the full-product scope and existing screen IDs. Do not mark B0, B1, SY03 or species profiles implemented from this design lock alone.

## Evidence

The selected-direction fragment is stored in the project at [previews/animal-farm-locked-homes.html](previews/animal-farm-locked-homes.html), with a matching copy for this conversation. The four-option `animal-farm-page-styles.html` is retained unchanged.

Verification on 31 August 2026:

- Failing-first check caught the missing explicit photo confirmation before implementation.
- `node scripts/Test-HomeDesignPreview.cjs`: 14 checks of the real preview handlers with DOM, file-reader and image-decoder boundary doubles. Covers stage filtering, carousel bounds, start-not-complete, A retention, five families, photo confirmation/cancel/replacement/removal, invalid type/size/decode/dimensions and stale asynchronous callbacks. This is not an end-to-end file-picker or native test.
- `scripts/Test-DeliveryCoverage.Tests.ps1`: planning package passed, including 12 deliberate negative checks, 47 feature packages, 146 screen briefs, 21 audit findings and 12 delivery stages. Existing source hashes and coverage identities remain intact.
- Browser inspection: management A; worker D+C; Nala profile entry; empty Active stage; theme-only settings; family chooser in dark theme. Final artwork was inspected at source resolution and then checked in the worker card and all five family choices after integration. No claim of full narrow-width, 200% font or native screenshot coverage is made.
- The browser file-chooser test timed out. Actual file selection remains an unverified integration gate even though the preview's selection/confirmation handlers pass isolated tests.
- No Android APK, durable photo upload, storage policy or native feature is marked complete by these results.
