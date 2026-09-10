# Animal-family portrait artwork

Date: 31 August 2026. Mode: built-in image editing; no API/CLI fallback used.

## Selected asset

- Project file: `core-design/src/main/res/drawable-nodpi/farm_family_portraits_v1.png`.
- SHA-256: `4182899863b830a6a812d8b60df3504991f75781b9d41c85380da63d6f6d39d2`.
- Dimensions: 1774 × 887. Opaque RGB on pale ivory, not a transparent sprite sheet.
- Source: existing login artwork `core-design/src/main/res/drawable-nodpi/farm_animal_lineup.png`. It remains unchanged.
- Synthetic brand portraits derived from the login animals; these are not personal identification photos or evidence about Nala.
- A size-optimized 1400-pixel WebP copy is embedded in the selected-home preview. This is packaging only; creative edits used the built-in image tool.
- Code-defined portrait windows isolate goat, rabbit, hen, sheep and cow without neighbouring animal fragments. Full-body extraction of the overlapping original was not achievable through simple rectangular windows.
- The first generated result contained a baked checkerboard despite the transparency request. It was rejected as a final UI asset. The second edit deliberately uses a clean opaque background; no true-alpha claim is made.
- Native Compose consumption, per-density packaging and native screenshot approval remain pending. The original login resource is not replaced.

## Prompt 1 — isolate animals

Input: the original login artwork.

> Use case: background-extraction.
> Asset type: Animal Farm UI family portrait sprite sheet.
> Edit target: the supplied login lineup. Isolate the SAME five animals. Preserve the individual appearance, coat markings, face, texture, realistic style and lighting of the goat, white rabbit, brown hen, white sheep, and brown-and-white cow.
> Composition: one wide sheet of five equal-width cells in one horizontal row in this exact order: goat, rabbit, hen, sheep, cow. One full animal per cell, centred, generously separated, no overlap, all ears, horns, tails and feet visible. Scale each animal independently for legibility in its cell. Reconstruct only areas originally occluded by a neighbouring animal.
> Background: genuinely transparent alpha, no studio background, no checkerboard baked into pixels, no ground plane. No neighbouring animal fragments, text, labels, icons or borders. Do not change their identity or invent additional animals.

Intermediate tool output: `exec-31717879-76e2-47da-bfb2-052da0e16634.png`. This was an intermediate, not a project asset. Its unequal cell widths are handled by explicit portrait windows rather than assuming an equal-cell sprite grid.

## Prompt 2 — final background correction

Input: the intermediate separated-animal sheet above.

> Use case: precise-object-edit. Edit target: this separated five-animal sheet. Change ONLY the checkerboard background to one perfectly flat, solid pale ivory background, hex #F3F5ED. Preserve every animal exactly: the same identities, coat markings, proportions, poses, edges, fur, feathers, lighting, placement and size. Keep the goat, rabbit, hen, sheep and cow distinct and completely visible. No checkerboard, no pattern, no texture, no scenery, no shadows on the background, no new objects, no text, no borders. Deliver a clean opaque portrait source sheet on flat pale ivory, not simulated transparency.

Selected tool output: `exec-e0ef01ad-2896-4f2a-b5d2-c4955cf9a757.png`, copied into the project path above. Both generated outputs remain in the tool's generated-images directory; no original or rejected image was deleted.

## Family mapping and identity boundary

Goat → Goats; rabbit → Rabbits; sheep → Sheep; cow → Cattle; hen → Poultry family launcher only. A kind-specific duck or turkey screen must not use the hen as that kind's identity. Individual animal photos replace only the subject portrait; family artwork and labels remain independent.

See [the approved home and photo contract](HOME_DESIGN_LOCK.md).
