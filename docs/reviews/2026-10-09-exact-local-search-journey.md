# Exact local search and Goat detail journeys — 2026-10-09

## Scope and qualification boundary

This batch preserves the selected Goat's immutable animal ID through global local search, profile, weight capture and return to the same farm search session. It also keeps database read errors distinct from a successful search with no results.

This is bounded implementation and local test evidence. It does not promote any feature, module, visual or product release gate. The full 156-feature / 29-module / 545-screen scope remains required. Sheep, Cattle and Rabbit exact-subject entry plumbing is a following batch; an individual Poultry search result is not evidence for a flock profile. No physical-device, Google-account, owner acceptance or all-device-loss recovery claim is made.

The governing contracts are [D-004, D-026 and D-027](../project-state/GOAT_OWNER_DECISION_REGISTER.md) and [Project Truth](../../PROJECT_TRUTH_PROTOCOL.md).

## Changes and actual exercised behavior

- Goat profile entry carries the exact animal ID. Its repository lookup is farm scoped and now also requires the Goat species; a missing, foreign-farm or other-species ID reports that the selected Goat is unavailable and never selects the first Goat.
- The profile can load a Goat outside the 500-row presentation window. Farm and entry changes clear the prior profile state.
- Search input, displayed results and filters survive a profile/weight/return journey in the same farm session. Changing farms clears that state.
- The query owner merges tag/name/species and active identifier matches from the farm-local Room database, removes duplicate animal IDs, and shows at most 50 matches. Both underlying SQL queries rank case-insensitive exact tag/identifier matches before their limits. Tests include more than 51 partial animal and identifier collisions; this does not claim exhaustive query pagination.
- A retained filter can always be cleared, including with the filter panel closed or when its old option is absent from a later query's matches. Filtered-out matches are described as filtered, not as a database search with no matching records.
- A failed local database read remains an error. It does not display the successful no-results state.
- A real weight command records 53.125 kg as 53,125 grams for the selected Goat, attributed to the current farm, account and device. The other farm's similarly tagged Goat and a similarly tagged Cow remain unchanged.

The production pages keep their existing illustration, spacing and visual contracts. Runtime tags identify actual rendered query/results owners; tags alone do not establish acceptance.

## Validation and first-attempt diagnosis

The first scoped app run executed 24 tests: 21 passed and three new full journeys failed before search execution. The test found the owner-home route wrapper while the real Room-backed summary was still loading, then tried to click a Search action that had not rendered yet. The production Search route and label were correct. The correction was limited to waiting for the actual clickable action, scrolling it into view and asserting visibility before clicking; the substantive query, exact subject, write, isolation and return assertions were preserved.

The corrected run executed 24 app tests and 14 Goat contract tests: **38 passed, zero failures, errors or skips**. Every retained JUnit report was fresh. Execution ran from 2026-10-09 15:07:53 UTC to 15:09:44 UTC, with the same source fingerprint before and after:

`ac582a6b6945ce161080d89c1f2d94b116497a650b5936640879dd1102004407`

This was the working source on parent commit `4738081ec0886e14b2ac13bc038f86d1ddf02e08`, before committing the tested changes and this review. The review was written after execution and does not claim that its own prose was an executed source input.

| Executed class | Tests | JUnit SHA-256 |
| --- | ---: | --- |
| `com.farmos.app.GlobalSearchRuntimeNavigationTest` | 6 | `5a2272dff725b8f3f755bf719d9bb187eeb5d110e21620e302186364835ab853` |
| `com.farmos.app.GlobalSearchContractTest` | 3 | `32759e424a9ffd3ba7df59ef784e8f37a0c10e0c3aef96c4e62f5f84404f2390` |
| `com.farmos.app.FarmRuntimeRouteTest` | 9 | `217e7bcdc4938e17472b65391398f9f94ad1ba46365bd83a7e8882ad42ad9f47` |
| `com.farmos.app.AndroidBackNavigationTest` | 6 | `84bf733f40a720ff070ed4288277300923fd4b2fde22747ab1b23ce2d93792e7` |
| `com.farmos.feature.goat.GoatReferenceContractTest` | 14 | `72672aa889c1c043ad7f34b132aab1c36992cd71b9c265df471966d1adc7e2db` |

### Retained validation record hashes

| Record | SHA-256 |
| --- | --- |
| First failing run result | `7bf6f2eaf06e9d7569408c91068fe46895656a08de46111bfef0d7dd354dd85b` |
| First failing app log | `aecc1f5ef71ef23edf9fc6fa6faa188c767d68ad60fc374719a51c6e4717d37f` |
| Corrected passing run result | `ca031d43cb9e6acd67ad089de15f8b381974258d6e697cc6560f1b73ac0b027e` |
| Corrected app log | `28516a4d6c35d81f0466827e8eabf274329a0d6aa14266dbb5d1a5d7e00a5861` |
| Corrected Goat log | `b9e7bf9f877186b47b13ee8c3a8eb98df865e950508d4354d1372a013ecc1778` |

The test worker was serialized with one Gradle worker and a 1 GiB Gradle heap / 1 GiB test-worker limit. These are JVM/Room/Compose tests, not connected Android instrumentation or physical-device proof. No broader CI or release result is inferred from this local run.
