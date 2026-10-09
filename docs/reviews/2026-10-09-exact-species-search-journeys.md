# Exact sheep, cattle and rabbit search journeys

## Scope and source

This bounded change follows the tested search slice at `95e150c8651742c1160e33837d70821ed8b449ef`. Search results for sheep and cattle now open the existing individual-animal profile with the selected farm-local identity. Rabbit results use the recorded female or male sex to choose the existing Doe or Buck profile. These entries read the exact animal independently of the ordinary 200-row herd window and reject another farm, another species, or a mismatched Rabbit profile.

The same production hosts own normal module entry and direct profile entry. The profile identity keys its state, including unfinished child forms. Sheep and cattle weight, lifecycle and operations pages return to the exact profile; profile Back returns to the originating search with its query and filters. Existing ordinary module-entry return labels remain intact. Species group options remain loaded for the operations reachable from a profile.

Poultry search matches remain visible with an explanation that individual bird profiles are unavailable. Rabbit matches without a recorded Doe/Buck sex likewise remain visible without a guessed profile. Neither result opens an unrelated dashboard. This accurately represents the existing profile contracts; it does not implement or qualify a new poultry or unknown-sex rabbit profile.

## Executable regression scope

`SpeciesSearchRuntimeNavigationTest` adds six real Room and native Compose tests:

- Search an exact sheep beyond the 200-row herd window, retain its subject through weight capture, verify the exact grams and local journal actor/device/farm/animal, and return through profile, lifecycle, operations, search and home.
- Exercise the equivalent cattle journey with a distinct weight and same-tag animals in other species and farms.
- Open the actual Doe and Buck profiles beyond the Rabbit herd window, verify recorded identity/sex and preserve the originating query on both visible and system Back.
- Reject wrong species, foreign farm and wrong Rabbit sex without substituting a first list entry or exposing mutation actions.
- Replace one valid profile with another and then switch farms, discarding an unfinished weight form and the old selected identity.
- Render real local Poultry and unknown-sex Rabbit search matches with the unavailable explanation and no unrelated navigation callback.

`FarmRuntimeRouteTest` also checks the four canonical route-owner identities and required animal parameter. That mapping guard supports the rendered journeys; an identifier assertion alone is not runtime qualification.

## Validation and limits

The parent executed the frozen worktree from 2026-10-09T16:00:23.548577Z to 2026-10-09T16:03:55.530892Z. The serialized `:app:cleanTestDebugUnitTest :app:testDebugUnitTest` run used `--no-build-cache` and the nine explicit class filters below. All **41 cases passed**, with zero failures, errors or skipped tests. Each retained JUnit report was freshly written and its suite timestamp belonged to this run.

| Test class | Passing cases |
| --- | ---: |
| `SpeciesSearchRuntimeNavigationTest` | 6 |
| `SpeciesHerdRuntimeNavigationTest` | 3 |
| `RabbitProfileScreensTest` | 2 |
| `RabbitRuntimeNavigationTest` | 1 |
| `RabbitRecordRuntimeNavigationTest` | 4 |
| `GlobalSearchRuntimeNavigationTest` | 6 |
| `GlobalSearchContractTest` | 3 |
| `FarmRuntimeRouteTest` | 10 |
| `AndroidBackNavigationTest` | 6 |

Execution used parent commit `95e150c8651742c1160e33837d70821ed8b449ef` plus the frozen 20-file species diff. The recorded source fingerprint was unchanged before and after execution: `412495a6631e3a2853a7dd85a030d32cdc72d3d275919df723748553cfb73bcc`. The subsequent edit records this result in this review; it does not change the tested production or test source.

The retained operator evidence is identified by exact bytes:

- Result record `species-tests-attempt1-result.json`: SHA-256 `7d20ff1e7f26b86409a4b4ca1878a0b8e85093fb6e29f119d320afed7ecf7094`. It contains the command, all nine JUnit report hashes, counts, timestamps and source fingerprints.
- Gradle log `species-tests-attempt1-app.log`: SHA-256 `89fecd82f481fbd95623755e677ae90f8c7a6d94fb1d4462231195548fed3540`.
- Six-case species journey JUnit report `app-TEST-com.farmos.app.SpeciesSearchRuntimeNavigationTest.xml`: SHA-256 `ed561f7762f7900a3af0472672753009e5f9c17a372787d3a23412665f14b10a`.
- Tested `app/src/test/java/com/farmos/app/SpeciesSearchRuntimeNavigationTest.kt`: SHA-256 `b346b96137f62e20d82fa7df175f67fc49a4134c3d7a2de58a9b500b5ec8f215`.

An independent source review found no introduced blocker in farm/species/sex selection, identity replacement, child-return behavior or the six journey assertions. This is a bounded source review, not native visual approval.

These are local JVM unit tests, with real Room and native Compose rendering where applicable; the route-mapping cases are supporting unit checks. No emulator execution, process-death restoration, provider consent, multi-device transport, physical-device performance, owner acceptance or release qualification is established by this run. The full 156-feature, 29-module and 545-screen scope and existing evidence gates are unchanged.
