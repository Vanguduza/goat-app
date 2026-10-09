# Artemis integration and release qualification — 9 October 2026

## Current merge gate after fixture lifetime correction

**PR111 remains unmerged. The corrected candidate requires fresh canonical CI; product release remains blocked.** Candidate C2, `04a1f94e1302dd79786a5baed1fda0e643807bd8`, failed [Foundation run 37966997043](https://github.com/Vanguduza/goat-app/actions/runs/37966997043): 788 native cases in 165 suites executed, with 787 passing and one failure, zero errors/skips. All seven corrected report-disclosure cases passed. Visual and connected jobs were skipped after the Android job failed; they supply no current execution evidence.

The failing case was `GlobalSearchRuntimeNavigationTest.switchingFarmsClearsSearchAndLoadsOnlyTheNewFarmsSubject`. The selected profile became visible while its composition-owned refresh was still reading `unsharedLocalOperations`. The fixture's `@After` then closed Room before the Compose rule's teardown, leaving `ReplicationPeerMarkDao.highestHeld` using a closed connection pool. The original native artifact `11633977880` is retained unchanged with SHA-256 `b498d266b9ab034c44a9b94de88ea6ff5196943a65656eaeaa69f3899f9e0d52`; the failed job log has SHA-256 `010fb158fd2ad69967dc403ca1c8756340879903330100b9e3acf1620f3e31b7`. Actual checkout `fedc9916f84e8935de78d6b7422678b7df4d49ec` has C2's exact tree.

The independently reviewed source correction is `69b3eb244d5e99983bcb2db4301560315f49c671`. In both GlobalSearch and SpeciesSearch fixtures, one `RuleChain` places a database `ExternalResource` outside the Compose rule. Room closes after the inner rule returns. The twelve existing test methods, helpers, assertions and setup remain byte-identical; no production code, sleep, catch, permission or timeout changed. This fixes the observed composition-owned lifetime; it does not claim to join unrelated application-owned background work. A fresh two-suite, twelve-case local execution and full canonical CI remain required.

Current generated route inventories are `CI_PENDING`. The prior passing C ledger is archived byte-for-byte with its original source/run pins and SHA-256 `484678892b093934f161d6867ba7edab8ddb069f85e50a7948d9cd3ff92818b1`. Its historical results below do not certify the changed fixture source. The reviewed map preserves all method/claim objects and changes only the two fixture source hashes and reviewed source commit.

### Earlier report-fixture correction and retained failed E

The first evidence commit `5991d242096f9478d2a53db6a2dcf4ea8e0a1337` failed [Foundation run 37964751640](https://github.com/Vanguduza/goat-app/actions/runs/37964751640): 788 fresh native cases, 787 passing and one failure, no errors/skips. `ReportDisclosureAuthorityTest.aDelayedChooserCannotUseTheOldAuthenticatedCredentialAfterAReset` failed before credential reset when its export-sheet control was absent. The four backend jobs and Truth, handover and smoke checks passed; visual/connected jobs were skipped. The original artifact `11633280867` has SHA-256 `5ea3dd703494df210aa566863ab98342b7fce3f95dcd8341d05908bfda21e53c` and its failed log SHA-256 is `4e506e097b927ff771c75f331f6e0086f462e78465763df08df925e57e695dd9`.

The correction `f7ab02b443e07f6903f75821c78885f8369953ab` waits for the asynchronous Room metric projection, asserts the expected farm contents, clicks one displayed/enabled entry, verifies its actual destination, and waits for exactly one enabled/clickable requested action. Share uses the same helper. All seven security/cancellation cases, single chooser launch, denial messages, zero provider opens/files/export receipts, retained data and 10-second bounds remain.

At clean C2, the focused report run executed **7/7 passing cases** from **17:34:20.267 to 17:35:34.775 UTC**. The actual Test task executed, the JUnit timestamp lies within that invocation, and source/tree remained unchanged. The copied XML SHA-256 is `ae0978d0f3a1e4b7dafca760cd8a91bc6c0ca5f7481cff03ad3e206d3bdcaaef` and log SHA-256 is `91a380c97863e6093d5dff16f8761a440750c0cdc976fcceba11c4cdd4f5fd57`. The same seven cases also passed in C2's otherwise failed canonical run; neither result relabels that whole run successful.

## Status and authority

The original candidate C, `46cfc8ca57d54a10a62edda5b8858b23eebf30b6`, passed all seven Foundation jobs and all ten PR checks in run `37961965633`, with 788 fresh native and 60 connected cases. That is retained historical evidence. The intervening E and C2 failures above remain failed; a new source candidate, its evidence descendant, merge/main verification, reusable Artemis controller/provider readiness, live Google delivery and owner/physical/visual qualification are pending.

The branch remains `chatgpt/goat-artemis-qualified-changes-20261009` and PR111. Inherited main is PR110 merge `349d40339923e6860f7ee7e74e5f149afab11f47`; its earlier qualification and testing-package evidence remains in [the Google test qualification record](2026-10-09-google-test-qualification.md). Changed source requires its own execution and CI evidence.

[Project Truth](../00_PROJECT_TRUTH.md), [the Project Truth Protocol](../../PROJECT_TRUTH_PROTOCOL.md), [AGENTS.md](../../AGENTS.md), [the completion control plane](../realisation/COMPLETION_CONTROL_PLANE.md) and [the Animal Farm migration and acceptance gates](../ux/animal-farm-visual-lock/MIGRATION-AND-GATES.md) remain binding. GOAT is a local-first Room application with local accounts and operation-level LAN/approved-Drive replication. Artemis is testing infrastructure, not an application dependency or data authority.

The full mandatory scope remains **156 features, 29 modules and 545 registered Screen IDs**. Generated completion state has **0 feature greens, 0 module greens and 0 visual-green screens**, with `release_blocked=true`. No flag or protected reference was changed.

Historical C's admitted ledger covered **330/545 IDs with some bounded route evidence**, leaving **215 without**; **105/156 features** had some route evidence and **51 did not**. Its overlapping categories were **225 rendered traversal, 111 rendered owner, 27 entry-action and 72 typed route-contract IDs**. These are not fully qualified screens and must not be summed. The current ledger is pending; it issues no replacement execution claim until fresh CI is verified.

## What each kind of evidence establishes

| Evidence | Permitted conclusion |
| --- | --- |
| Source inspection and an independent code review | The inspected implementation/contract is understood and any reported source defects have a disposition. It is not execution. |
| Gradle task execution plus current JUnit and frozen source identity | The named cases ran on that source and environment, with the recorded outcome. A partial failed job remains failed. |
| Restored Gradle test output | Prior results were restored for a cache key. A newly copied XML file or `fresh=true` collection flag does not prove a new execution. |
| Boot property, ADB identity and device admission | The intended guest booted and its serial is permitted through the testing plane. This does not prove GOAT rendering, controller compatibility or a provider-backed Artemis task. |
| Governed terminal Artemis receipt | A particular admitted task produced the sealed artifacts and outcome. Each required assertion still needs review against trace evidence; task completion alone does not imply passing tests. |
| Current Foundation/native artifacts | The recorded workflow and test/rendering scope passed at its bound source. Owner/independent visual acceptance and whole-product gates remain separate. |

The retained local receipt directory is `/home/ubuntu/work/goat-artemis-qualification-tooling-20261009`. Names below resolve there unless a repository path is given. These VM receipts are not implied to be checked into Git or available as public download links. Their important hashes are listed at the end of this record.

## Executed local engineering evidence

The following are separate local runs. Some classes repeat between runs; the rows must not be summed into a unique product-test count. Their working-source fingerprints, commands, JUnit hashes and timestamps are retained in the named receipts. A receipt's HEAD alone does not identify uncommitted source that was part of that run.

| Run and completion time (UTC) | Recorded execution | Outcome and boundary |
| --- | --- | --- |
| Reports, 14:21:21 — `report-tests-result.json` | `ReportRuntimeNavigationTest`: 8 cases; `ReportsScreensTest`: 3. Source fingerprint `f15ed60a1b8e7704dd96392d66326fe87038cfaa04cfd04d2ba11ea21a257497` was unchanged during the run. | PASS for those JVM/native-Compose cases. This is not live export-provider, Google or field acceptance. |
| Back navigation, 14:35:19 — `back-tests-result.json` | `AndroidBackNavigationTest`: 6; `FarmRuntimeNavigationTest`: 5; `ReportsScreensTest`: 3; `GoatReferenceContractTest`: 14. Source fingerprint `118b8e85bc9b17072c94cbc670ccceda8849b62ea7989d5b2c5b66b16707e040` was unchanged. | PASS for the named app/feature JVM suites. `AndroidBackNavigationTest` is a JVM test despite its name. |
| Exact Goat search, attempt 2, 15:09:44 — `search-tests-attempt2-result.json` | `GlobalSearchRuntimeNavigationTest`: 6; `GlobalSearchContractTest`: 3; `FarmRuntimeRouteTest`: 9; `AndroidBackNavigationTest`: 6; `GoatReferenceContractTest`: 14. Source fingerprint `ac582a6b6945ce161080d89c1f2d94b116497a650b5936640879dd1102004407` was unchanged. | PASS for these cases after the preceding failed attempt. The receipt's working HEAD is `4738081ec0886e14b2ac13bc038f86d1ddf02e08`; the tested source was subsequently committed/integrated. It is not a full final-candidate run. |
| Recovery, first attempt, 14:53:00 — `recovery-tests-result.json` | `LocalAccessServiceTest`: 10 cases actually ran at 14:51:29. The log shows `:domain:access:test` executing. | Domain cases passed; the overall phase FAILED on application compilation (`O_DIRECTORY`). The domain result does not make the application or recovery slice green. |
| Recovery, attempt 2, 15:25:37 — `recovery-tests-attempt2-result.json` | The same 10-case XML, timestamp 14:51:29 and SHA-256 `aa01234388ccb20d0de9d0785b30f82478610ba85bc9732d46573feda54634f9`, was restored. The log explicitly says `:domain:access:test FROM-CACHE`. | This was **not a second fresh domain execution**, despite the collector's `fresh=true` flag. The phase FAILED on the Android-test fixture's `Closeable` compilation. |
| Recovery, attempt 3, 15:49:31 — `recovery-tests-attempt3-result.json` | Compilation completed; `LocalAccessServiceTest` ran freshly at 15:47:52 with 10 passing cases. The selected app suites then ran and produced current XML. | Overall FAILED. `LocalFarmEntryTest.ownerRecoveryReplacesThePinAndRotatesTheCode` and 10 `SettingsHostTest` cases reached the strengthened access boundary outside a Room transaction. The accepted local/Drive/rotation tests in this run do not erase those failures. The targeted fixture-corrected rerun is recorded below. |
| Recovery, targeted attempt 4, 16:05:59 — `recovery-tests-attempt4-result.json` | `LocalFarmEntryTest`: 3 cases; `SettingsHostTest`: 16, all freshly executed. Source fingerprint `5f87f197b7a0d8be7a4a137473445f16a780bc8e85debdbd137616b70ec471a2` was unchanged. | PASS for these 19 corrected fixture cases, with no failure/error/skip. This rerun did not execute the domain suite again or perform device instrumentation. |
| Exact Sheep/Cattle/Rabbit journeys, 16:03:55 — `species-tests-attempt1-result.json` | Nine app suites, 41 freshly executed cases: Android Back 6; runtime routes 10; global-search contract 3 and rendered search 6; Rabbit profile 2, record navigation 4 and runtime navigation 1; species herd navigation 3 and exact species search 6. Source fingerprint `412495a6631e3a2853a7dd85a030d32cdc72d3d275919df723748553cfb73bcc` was unchanged. | PASS with no failures/errors/skips. The receipt records working HEAD `95e150c8651742c1160e33837d70821ed8b449ef`; the tested patch was subsequently committed as `a244a1606a15b75b28e37b8baacb6592b7551558`. Its later joined unit evidence is recorded below; full-build and device qualification remain pending. |
| Session/report readiness, targeted attempt 3, 16:15:05 — `session-tests-attempt3-result.json` | `LocalFarmSessionAuthorityTest`: 6 cases; `ReportRuntimeNavigationTest`: 8, freshly executed at the corrected source fingerprint recorded below. | PASS for these 14 cases. The initial 55-case attempt and the intervening 14-case attempt remain separate failed histories; neither is silently relabelled. |
| Combined native validation, attempt 1, 16:34:15 — `combined-native-attempt1-result.json` | All nine requested unit-test tasks executed at clean commit `88de9dabf8e277bf0fb7a6ef4a21769041761aa4`: 788 cases in 165 suites, with zero failures/errors/skips. Exact module counts and retained-source identity are below. | The unit-test portion PASSED, but the combined build **FAILED** resolving the versionless Android-test Compose dependency. Gradle reported **13m 21s**. No instrumentation APK, device execution or full-build PASS is claimed. |
| Targeted packaging, attempt 1, 16:48:13 — `packaging-attempt1-result.json` | Debug APK assembly, app Android-test compilation/APK assembly and database Android-test Kotlin compilation at `46cfc8ca57d54a10a62edda5b8858b23eebf30b6`. No unit or connected test task ran. | **PASS for this packaging/compilation scope**, after the existing Compose BOM was added to Android-test dependencies. The debug app task was `UP-TO-DATE`; app Android-test compilation/packaging and database Android-test compilation executed. This does not rerun or relabel the earlier 788-case result or the failed combined build. |

The retained first/second recovery logs distinguish compilation failures from test failures and restored results. Subsequent fixes must retain the strengthened runtime transaction/authority checks and correct the real fixtures or caller paths; no timeout increase or permission bypass is accepted as closure.

### Combined native attempt 1: passing unit tests, failed Android-test build

The terminal receipt covers **16:20:53.513147–16:34:15.576021 UTC** at commit `88de9dabf8e277bf0fb7a6ef4a21769041761aa4`, tree `442c263fd4371a1c5f4984fb53c8144144600b48`. Gradle reports **`BUILD FAILED in 13m 21s`**; the surrounding collection interval is 13m 22.063s. The test-driver source fingerprint was unchanged before and after: `3b0869c37ee9b4c356fa7a3b55232db8bdbdf971c5a587bc1ae1b123b1a8780f`.

The log records all nine named Test tasks executing, with no `FROM-CACHE`, `UP-TO-DATE` or skipped disposition on those tasks. The committed `scripts/testing/fresh-test-evidence.gradle` disabled test-result caching while other build tasks could use the compilation cache. All 165 retained JUnit files were independently read back and matched to their receipt SHA-256 values; their actual testcase counts total **788**, each suite timestamp is within this run, and no testcase or suite records a failure, error or skip.

| Module | Executed suites | Passed unit cases |
| --- | ---: | ---: |
| `domain/goat` | 1 | 19 |
| `domain/rabbit` | 3 | 16 |
| `domain/ops` | 12 | 68 |
| `domain/replication` | 6 | 49 |
| `domain/access` | 1 | 10 |
| `core/network` | 3 | 17 |
| `core/sync` | 2 | 15 |
| `app` | 127 | 542 |
| `feature/goat` | 10 | 52 |
| **Total** | **165** | **788** |

Application Kotlin compilation and `:app:assembleDebug` completed. The collector retained the intermediate debug application APK (30,965,007 bytes, SHA-256 `bd82da2e2b3e62f6589a3d17acfbd0d8955f0b9353bf6e4caa03bedfd6571e6c`). This is a local build output, not the final signed testing-package/install/readback qualification.

The combined command then failed at `:app:processDebugAndroidTestManifest`: `:app:debugAndroidTestRuntimeClasspath` could not resolve **`androidx.compose.ui:ui-test-junit4:.`**, with no dependency version. At this checkpoint the Android-test dependency was unresolved; the later correction and targeted packaging result are recorded separately below. This failure prevented completion of the requested Android-test APK/compilation phase; it was **not a unit-test failure**, and the 788 passing unit cases do not make the combined build successful. No connected instrumentation or Artemis functional task ran in this attempt.

The result is retained as `combined-native-attempt1-result.json`; its Gradle log is `combined-native-attempt1-gradle.log` and its 165 exact XML files are under `combined-native-attempt1-junit/`. This bounded unit evidence belongs to `88de9da`, not automatically to a later dependency, mapping, evidence or integration commit.

### Targeted packaging attempt 1: dependency corrected, instrumentation built

The existing Compose BOM `2026.08.00` is now also applied to `androidTestImplementation`, through correction commit `969df09d9f790e7cf4b785ed51ea1fe39aeb9daf`. The app and Android tests use the same existing BOM; this correction does not upgrade its version or weaken a test.

The targeted packaging command ran **16:47:10.235254–16:48:13.893763 UTC** at candidate `46cfc8ca57d54a10a62edda5b8858b23eebf30b6`, tree `14ef784242b2fd9a12fa861b257267fda3ced044`, and returned exit 0 with **`BUILD SUCCESSFUL in 1m 3s`**. The test-driver source fingerprint was unchanged: `513002e18aa96371abb6c7dbc6dee31731b038d56913aea4018c7cebdf4c09f2`.

The independently read log shows `:app:processDebugAndroidTestManifest`, `:app:compileDebugAndroidTestKotlin`, `:app:packageDebugAndroidTest`, `:app:assembleDebugAndroidTest` and `:core:database:compileDebugAndroidTestKotlin` completing. `:app:assembleDebug` was `UP-TO-DATE`, so this attempt reused the debug application output. Some intermediate resource tasks came from the build cache. It ran **no unit tests and no connected instrumentation tests**.

Both retained files were independently read and matched to the receipt's exact size and SHA-256:

| Local output | Bytes | SHA-256 |
| --- | ---: | --- |
| `app-debug.apk` | 30,965,007 | `bd82da2e2b3e62f6589a3d17acfbd0d8955f0b9353bf6e4caa03bedfd6571e6c` |
| `app-debug-androidTest.apk` | 1,178,319 | `ef588f1f0e96bb4cc134669bfa572a43255444152e87003df3f98cfeffa5075e` |

This closes the observed versionless-dependency packaging failure for this candidate. It is not a rerun of the 788 unit cases at `88de9da`, a successful full Foundation run, final package/signing/install verification, or device execution of `LocalFarmActivityJourneyTest` or `LocalAccessRecoveryDurabilityTest`. The original combined receipt remains **FAILED** and its unit portion remains **788 passing cases**. The targeted result and log are retained as `packaging-attempt1-result.json` and `packaging-attempt1-gradle.log`.

### Reviewed slices and consolidated-validation boundaries

Source review and earlier isolated execution are separate below. The combined run supplies passing unit evidence at joined `88de9da`; the targeted follow-up supplies successful packaging/compilation evidence at `46cfc8c`. These separate historical runs are now followed by the successful terminal C Foundation and connected results recorded below; wider product qualification remains open:

- **Session authority and report disclosure:** coherent Room observation of the current farm/account/local device, credential continuity, role-keyed disposal of privileged nested state, fresh export/share admission, and cleanup across carrier-startup or disclosure cancellation. The independent review checked the corrected partial-startup and opened-stream ownership paths. The new suites are `LocalFarmSessionAuthorityTest`, `LocalSessionAuthorityTest`, `ReportDisclosureAuthorityTest` and the updated `ReportRuntimeNavigationTest`. The first session run executed 55 cases, with 53 passing and two test-helper failures (an unscrollable synthetic delivery button and an intent poll that did not idle Android’s main queue). Those were corrected. Targeted attempt 2 freshly executed 14 cases with 12 passing; its two different failures exposed a report hub rendered before metric loading/reflow and a returned Documents wrapper rendered before its IO entries. The two original failures passed in that attempt. The helpers now wait for the actual loaded data and one enabled/displayed action, without timeout increases or repeated-click retries. Targeted attempt 3 freshly executed the same 14 cases successfully at 16:15:05, with no failures/errors/skips and unchanged test-driver source fingerprint `9e8764fa764020ae797aee6792415079e484b45b2f60267a23bd6ea28ca33e5f`. The 12-file slice is now commit `b9251a6fa44f3bf5927dbbc8b48909350c81ed28`, tree `ba23504965e4d60d0e5d559ba112449c90d802d0`, recorded through the normal Truth hooks and verified clean. This targeted rerun is not a fresh execution of all 55 cases or the final joined candidate. Both failed attempt receipts remain retained.
- **Exact Sheep/Cattle/Rabbit profiles:** farm/species-scoped direct reads beyond the 200-row list; recorded Rabbit sex validation; profile/farm state replacement; nested returns to the selected profile and retained search. The six `SpeciesSearchRuntimeNavigationTest` cases and updated route mapping guard were independently source-reviewed without a remaining concrete introduced blocker. They passed within the 41-case species run above. The source commit is `a244a1606a15b75b28e37b8baacb6592b7551558`, tree `cd0c100aee8c00d6e7be636b73aa60ef5c3c14fc`. These unit cases also passed on joined `88de9da` and then in C's fresh native CI run. The successful C Foundation workflow does not establish every species journey on physical devices or close field qualification.
- **Actual MainActivity journey:** `LocalFarmActivityJourneyTest` uses the production activity and UI for a synthetic local farm, Owner, goat registration and exact 45.125 kg record, followed by activity recreation, sign-in, exact search/profile and Back. It requires an empty isolated local-only app installation and retains its synthetic data. `ActivityScenario.recreate()` is not process death, Room close/reopen, filesystem power loss, a physical reboot or second-device proof. The three-file source slice was independently reviewed without a remaining concrete introduced blocker and committed as `13be7bbfc898e17b3502b2b4360ab8ea7dbde870`; its normal integration commit is `c3741a4ff534e6e8ef98b783d31b82676e42caa6`, with identical tree `e35b14e463954e719e56b6d187f21470db8066a8`. The joined `88de9da` Android-test build was blocked by the versionless Compose dependency. The corrected app instrumentation compiled and its APK was assembled at `46cfc8c`. This exact Activity journey subsequently passed on the one C CI emulator in 25.199 seconds; its execution and remaining physical/provider limits are recorded below.
- **Recovery durability:** the separate Android instrumentation exercises named file-backed databases, recovery-code/PIN transitions, a failed SQLite journal write, and a test-owned Keystore alias loss. This is not all-device-loss escrow or a physical power-loss test. The corrected local source is commit `3c6cfef20026e498e4ebe3c37f788a2e86d8ff16`, tree `7d55453c438a9d355cf9f334f186fee0d58b5b42`. The 19 previously affected application fixture cases passed in attempt 4. The three Android durability cases subsequently passed on the one C CI emulator; that result does not establish physical power-loss behavior or all-device-loss recovery.
- **Per-case Phase 5 evidence:** the two consistency helpers and three builder/consumer hooks were independently source-reviewed. A concrete incompatibility with the real report click helper was corrected with a focused positive/negative regression; no remaining concrete blocker was found in this bounded source review. The checker distinguishes complete exact method identities, reviewed source/map hashes, positive traversal/return anchors, actual JUnit execution and same-tree clean artifact identity. It does not authenticate CI origin or issue approval. The final map and seven committed source bindings were independently reviewed. The parent subsequently verified the full C run and admitted the bounded PASS ledger from clean C. Session/security cases remain executed-test evidence without invented screen traversal; final E preservation/CI and broader qualification remain pending.

The joined source was independently read at clean commit `88de9dabf8e277bf0fb7a6ef4a21769041761aa4`, tree `442c263fd4371a1c5f4984fb53c8144144600b48`. The extracted `LocalFarmSession` factory carries `initialKeys = app.keyVault`; `MainActivity` still selects this production local path. Accepted-only carrier startup, role-keyed content disposal and failure cleanup remain intact. `FarmSessionContent` retains role-keyed TaskPlanning, per-farm search state, exact species destinations, the return-to-search callback, and both the current export authority and correct Back callback on Reports. `LocalFarmEntry` has no competing session factory. No concrete merge regression was found in this bounded read-only review. The observed source SHA-256 values were `67c176bdb161cb00d13adf404eb79a04815d67f99b557c61c03e543870a01584` for `app/src/main/java/com/farmos/app/LocalFarmSession.kt` and `1a2093fe4d3f6321526fb1e0674e748d6e269bd63f689d50e2534ac0e11757be` for `app/src/main/java/com/farmos/app/FarmSessionContent.kt`. This is source-review evidence, not a successful combined build or device run.

## Candidate C native CI — earlier pending observation

The parent's retained validation summary at **16:58:43.577531 UTC** records the native job of [Foundation run 37961965633](https://github.com/Vanguduza/goat-app/actions/runs/37961965633), job `113926776866`, as **SUCCESS**. The candidate is `46cfc8ca57d54a10a62edda5b8858b23eebf30b6`; the actual synthetic checkout is `a2b0fd96cbcdb62efbc4b4a1821dae894627aa70`. Both have tree `14ef784242b2fd9a12fa861b257267fda3ced044`.

The summary records **788 fresh passing native unit cases in 165 suites**, with zero failures/errors/skips, across the same nine modules listed above. It also records the exact seven mapped classes/42 cases and a runtime-source collection of 66 classes/288 cases. These are execution/source-inventory counts, not counts of fully traversed or qualified screens. The summary explicitly leaves `repository_evidence_promoted=false` and `foundation_terminal_acceptance_pending=true`.

This document's author read that retained summary and its bound identities; the parent collected and validated the actual GitHub artifact/log inputs. At that 16:58 observation, the complete Foundation run was still in progress, with device and visual work pending. The summary's pending fields remain an accurate historical snapshot. The later terminal verification below supersedes the pending status; neither observation promotes a feature/module/visual flag.

The summary is retained at `ci-pr111-candidate/native/validation-summary.json`. It binds native artifact `11631399975`, ZIP SHA-256 `6731245fa206d8a648fb330e78c54ccfdf9b54bda23ea6adeb3fbdd8eed103ad`, and job-log SHA-256 `166432220e6354b507819aa70b4f5bc54456f1cb7598c1eeff63f74db6486fc7`.

## Terminal candidate C Foundation and connected verification

Foundation run `37961965633` completed successfully at **17:01:51 UTC**. In a separate verification recorded at **17:05:05.123557 UTC**, the parent re-fetched the run, job, PR, artifact and Git commit APIs, rehashed the native artifact ZIP and log, and checked all 165 current JUnit suites/788 cases and all nine actual Test-task markers. All seven Foundation jobs and all ten PR checks were successful. The exact candidate remains `46cfc8ca57d54a10a62edda5b8858b23eebf30b6`; actual checkout `a2b0fd96cbcdb62efbc4b4a1821dae894627aa70` has the same tree `14ef784242b2fd9a12fa861b257267fda3ced044`. This is C evidence; E CI and merge are not claimed.

The independently collected connected report is frozen at `ci-pr111-candidate/connected/connected-ci-evidence.json`. The device job `113929024408` ran from 16:55:53 to **17:01:50 UTC** and passed on **one Google APIs API 36 x86_64 emulator, serial `emulator-5554`**, with the workflow's KVM step successful. The document author independently rehashed and parsed the retained identity and JUnit files: the checkout/tree match C, worktree status is empty, and **51 database cases plus 9 app cases in 37 suites** have zero failures, errors or skips. Their timestamps agree with the timestamped connected-test log.

| Connected app class | Passed cases | Actual scope |
| --- | ---: | --- |
| `LocalFarmActivityJourneyTest` | 1 | Actual MainActivity local onboarding, exact 45.125 kg Room write, Activity recreation, sign-in, exact search/profile and Back; case duration **25.199s**. |
| `LocalAccessRecoveryDurabilityTest` | 3 | Credential/recovery-code durability across database reopen, actual SQLite recovery-journal rollback, and loss of a test-owned Keystore alias without reprovisioning. |
| `FanoutOfflineDurabilityTest` | 3 | Retained Rabbit/task/cage writes, journal-failure rollback and retry, and denied money writes across named database reopen. |
| `RevokedMembershipClientTest` | 1 | Revoked remembered authority stops the tested synchronization path. |
| `TwoDeviceAuthoritativeSyncTest` | 1 | A second independent replica receives authoritative goat and weight operations through the explicit loopback LAN fixture. |
| **App total** | **9** | The separate database module contributes **51** more passing cases. |

The connected artifact is `11632112359` (`goat-connected-test-evidence`), ZIP SHA-256 `5159d6a002605963e4ded46222d34844ae75dd8d423092ec12b4cbb6942db14a`. Its tested app APK is 30,965,007 bytes with SHA-256 `8af565f050be8bc4d7c46edb3fba989034a4a02cb8f893272a2f6c80961886ca`; this CI output is distinct from the earlier retained local APK. Final retained-package signing, installation and download/readback remain separate work.

These connected cases establish actual Android execution on that one CI emulator. Activity recreation is not process death or a physical restart; the second-replica test uses explicit loopback transport, not two physical handsets. The unconfigured Drive path does not establish live Google consent or cloud recovery. The CI emulator is separate from the resource-constrained DIAL Artemis target and does not prove its controller/provider readiness. A successful visual-reference job supplies its recorded CI evidence, not owner or independent visual acceptance.

After terminal verification, the parent admitted the C-bound `PASS_EXACT_HEAD_CI` runtime ledger. A read-only observation confirmed its C/run binding, the four distinct coverage categories and zero green claims. Current coverage is 330 combined IDs with 215 unresolved; 105 features have some route evidence and 51 do not. This is bounded evidence reconciliation, with the mandatory 156-feature/29-module/545-screen scope retained. The documentation/derived-evidence E commit and its own CI are still pending.

## Emulator installation, boot and shutdown evidence

The installed official emulator is version **37.2.12**. This VM exposes no usable KVM acceleration; the attempts use software TCG. Resource limits are real test-environment constraints, not Android performance results. The existing unrelated AVD was preserved.

| Profile | Retained observation |
| --- | --- |
| Google Play API 36, `goat-artemis-api36`, `emulator-5560`; 2048 MiB guest RAM / 2 guest cores / 3 GiB host service cap | Started at 14:08:31. The attempt reached the cap without accepted boot. The final 14:21:32 service receipt reports `Result=oom-kill`, exit status 9. |
| Same Google Play image, 1536 MiB / 1 core / 3 GiB cap | The smaller profile was configured at 14:29:35. It again reached the cap; the 14:47:12 stop observation still had unaccepted boot and zero OOM counters at that instant. Its later final service receipt reports `oom-kill`, status 9. Preserve both observations rather than rewriting the earlier snapshot. |
| AOSP ATD API 36 revision 1, `goat-artemis-atd-api36`, `emulator-5562`; 1536 MiB / 1 core / 3 GiB cap | Configured at 15:09:13. The first ATD attempt reached the cap with a timed-out shell probe; stop was requested at 15:21:20. The 15:22:04 terminal receipt reports `oom-kill`, status 9. It was not admitted. |
| Same ATD with QEMU translation-cache option `-tb-size 128` | Configured at 15:25:39 and started at 15:26:21. At **15:38:43.774333 UTC**, `sys.boot_completed` returned **1**, `system_server` was present, service memory was 2,760,507,392 bytes, and the sampled OOM counters were zero. This is the first retained accepted boot observation. |
| Reduced-cache ATD admission | At **15:40:48.106385 UTC**, the API 36 AOSP ATD identity was checked and **emulator-5562** was added as an enabled testing device. The launch policy is `OWNER_MANAGED_SYSTEMD_UNIT_ONLY`; automatic AVD launch admission was not changed. |
| Reduced-cache ATD stop | At 15:47:04, before the requested stop, service memory was 2,852,843,520 bytes. The stop command returned 0, but the final process state was **`Result=core-dump`, `ExecMainStatus=11`**. This is not a clean shutdown or restart-durability result. |
| Google Play reduced-cache follow-up, earlier configuration | At 15:47:06, the 1536 MiB / 1-core / 3 GiB Google Play profile was prepared with `-tb-size 128`. That receipt records **not started and not admitted** at that time. The later attempt is recorded separately below. |
| Google Play API 36 TB128, attempt 2 | The monitored attempt ended **FAILED at 17:03:57.029407 UTC** after the host memory guard triggered at approximately 207 MB available. Final service state was **`Result=oom-kill`, `ExecMainStatus=9`**, with a 3 GiB memory peak and no verified boot. The stop command returned 0; this does not turn the failed guest into a successful or clean-shutdown result. |

At the updated checkpoint, **Google APIs API 34 x86_64 revision 14 is installed and independently verified**, and the new `goat-artemis-api34-googleapis` AVD/service is prepared, disabled and not booted. The official 1,563,721,130-byte archive matches published SHA-1 `e0f6c9a0691aa27bd597d0deb1bcfdc943ac8ca7` and SHA-256 `783a40134baf4f3012d4464fbe1571b1612a0dbd2e7a44d14bd8328923443833`; all 24 installed archive members match. The profile uses serial `emulator-5564`, 1536 MiB guest RAM, one core, 720×1600 display, 3 GiB host cap, no swap and translation-cache size 128. Unit SHA-256 is `de7fb697f5bd2f8d56221c856be43410ca64cd58ac42914d323a22d3e10773d3`. Google APIs includes Google services but is not a Play Store image.

The independently reviewed, unstarted monitor has SHA-256 `34e8b47019688212a3e56707e4987797667298011f4b204932716a277a991f14`. It requires positively stopped heavy jobs, the exact unit bytes, two adequate memory samples, and successful boot/API/AVD identity within 24 minutes after start; it retains bounded pressure/timeout cleanup. No new device admission, boot, helper/controller or provider pass is claimed. ATD and the failed Google Play profile remain stopped, with their original failed receipts preserved. Reusable local Artemis qualification is still pending despite separate successful historical CI instrumentation.

The ATD unit is a persistent ubuntu user-service file at `/home/ubuntu/.config/systemd/user/goat-artemis-atd-api36.service`. The reduced-cache unit SHA-256 is `d84e27cb8163393434247d06a6e323694d3c122ae00916d07e2b428d56939818`. A unit file is reusable configuration; it is not proof of unattended boot, a running service after the recorded stop, or a successful restart.

The admitted-device registry is `/var/lib/dial-control/config/android-testing-devices.json`. The subsequent canonical status response saw `emulator-5562` in both its admitted and connected sets and **no admitted AVDs**. The stock name-only launch path does not express this host's required software/headless/resource configuration. Future starts remain owner-managed; tasks use a freshly checked admitted serial.

AOSP ATD deliberately lacks the Google apps/SystemUI needed for some system and Google flows. Its missing SystemUI service messages therefore cannot be treated as a GOAT crash by themselves. Conversely, a booted ATD cannot establish Google Play consent, system-picker behavior, full screen-lock handling, native notification behavior, physical hardware operation or accelerated performance.

## Actual Artemis preflight

The [repository-native operator contract](../testing/ARTEMIS_ANDROID_TESTING.md) and `scripts/testing/artemis_goat.py` bind requests to a clean expected commit/tree and actual APK/provenance/signature. The scenario manifest currently describes four bounded journeys with 22 required assertions. It is not the complete GOAT E2E suite.

The governed server remains `dial-hermes-android-testing` version 2.0.0, with Artemis subordinate to the testing-plane admission and trace owner. The installed Artemis pin is `371aa6df56880643da57b30da936e9812fb0ec66`, running as ubuntu. The project binding is `goat-app`; no DDS development gate or unrelated project authority was changed.

The retained preflight started at 15:42:04 and ended **FAILED at 15:44:36**:

1. `android_testing_status` returned successfully and confirmed the connected, admitted ATD serial.
2. `android_diagnose(attempt_fix=false, verify_credentials=false, probe_device=true)` returned a **blocked** verdict: three of five required readiness checks passed; no multimodal provider was configured; the lock state was unknown; controller/UIAutomator initialization did not respond within 20 seconds. The device-probe duration was 20.079 seconds. No screenshot bytes or hierarchy element count was produced. The optional bundled accessibility helper was present as an artifact but not installed on the guest; no fixes were applied.
3. `android_device_state(view_type="screenshot")` produced no retained response before the harness's 120-second per-call timeout. The driver then exited 1.

These are readiness checks, not five GOAT test cases. Generic diagnostic advice to unlock a phone does not establish the cause on this ATD. Controller compatibility/initialization and slow-emulator behavior require a supported investigation. No screenshot, hierarchy, GOAT launch or functional Artemis task is claimed by this preflight.

Once the supported controller and provider are ready, consume tasks only through the canonical admitted MCP. A completed task can contain failed assertions. The GOAT helper's successful receipt validation is `TRACE_CHECKS_PASSED_REVIEW_REQUIRED`; review each assertion against the actual trace, screenshot and output evidence before claiming its bounded outcome. No raw upstream server, generic dispatcher, fabricated receipt or gate change substitutes for this path.

## Provider, Google CLI and Drive readiness

The named CLI configuration `goat-release-testing` was created at **17:44:35 UTC** for the observed test project without activating it or changing the existing default. It contains no new account credential; gcloud still has zero accounts and ADC remains absent. The private receipt `google-test-cli-profile-created-20261009.json` has SHA-256 `6b255e2989a6afeeaeca6a2391e8ee9dc1c81bfdf5f158a418b8321965ebde9b`. No account email, project identifier or secret is reproduced here. The prepared AI Studio key dialog has not been submitted.

These are separate boundaries:

| Boundary | Observed status and required next evidence |
| --- | --- |
| Artemis model execution | Both the earlier provider preflight and the admitted-ATD diagnostic report `configured_count=0` and no pinned `.env`. This blocks provider-backed autonomous tasks. The pinned source supports its native API-provider configuration, and Vertex AI requires its supported authorized ADC/project configuration. Each selected model needs a compatible provider. No existing DDS subscription CLI or Drive connection was repurposed as a secret source. |
| Google Cloud CLI | Fresh read-only observation at **15:47:57.647379 UTC**: SDK **586.0.0** installed, `gcloud auth list` returned **0 accounts / 0 active**, and the ADC file was absent. No credential contents were read or recorded; no login was initiated. This does not prove Cloud project access or OAuth-client administration. |
| Supported CLI authorization | The installed help documents `--no-browser` with a trusted second machine containing browser and gcloud, and `--no-launch-browser` with an interactive browser authorization-code exchange. These require an owner-controlled secure terminal/browser handoff. Codes, tokens and credentials must not be routed through model/chat messages or committed to GOAT. The help hash is retained; no authorization flow was started. |
| Cloud Console | The owner-facing browser observation remained **Site Unavailable** at approximately 15:24 UTC and again on the Cloud Console root URL at approximately 16:03 UTC; it was still unavailable in the later test-project observation. This is a dated access observation from the parent operator, not evidence of an anti-bot challenge or OAuth configuration. The working AI Studio Projects surface is recorded separately below. |
| Google AI Studio test project | In the existing signed-in Google account, the parent operator observed creation of **GOAT Release Testing** through [AI Studio Projects](https://aistudio.google.com/projects). The private observation receipt recorded at **16:58:33.882561 UTC** identifies the Projects row as **Free tier**. The parent also observed **Create API key** on that row; the receipt records `api_key_created=false`. This establishes only the observed project creation, not API execution or configured GOAT cloud delivery. |
| GOAT Google Drive | A Google account connected to another client, working gcloud, an Artemis provider key, and GOAT's Android consent are distinct. Live Drive needs an appropriate Google-enabled target, the actual APK package/signing identity configured for consent, explicit in-app `drive.file` authorization, current GOAT storage/device approval and verified delivery/readback. These remain pending. |

The working AI Studio surface means a test project could be created even while Cloud Console remained unavailable. The parent recorded that observation in `google-test-project-created-20261009.json`; the document author verified the receipt hash and only its nonsecret status fields. No account email or project identifier is reproduced here. The receipt records that no existing project keys were read or copied, no API key was created, gcloud was not authenticated, Artemis credentials were not configured and a Drive OAuth client was not configured. **API credential creation and secure installation, model execution, and GOAT Drive OAuth/consent/delivery remain pending.**

Credential setup must use a supported owner-controlled mechanism, outside GOAT task inputs and source. The observed missing provider or Cloud access does not block offline Room workflows or deterministic local tests. It does block claims that Artemis autonomous execution or live Google delivery has succeeded.

The public CLI help is retained as `google-cli-login-help-20261009T154757Z.txt`, SHA-256 `2498ac114ce048824e5b24c34adfc5ed264b7a9b75bb76b353c91b1e98cfaf3b`. The sanitized readiness receipt is `google-cli-readiness-20261009T154757Z.json`. It records only versions, counts, file presence, supported-help evidence and the absence of a login attempt.

## Remaining qualification gates

| Gate | Current boundary and concrete closure evidence |
| --- | --- |
| Complete feature/module scope | Finish each applicable Feature Implementation Contract: commands/queries/events, permissions, species/tenant constraints, offline/retry/conflict/recovery, reconciliation, search, safety, UI states and NFR/field evidence. A route or passing infrastructure slice cannot set a feature green. All 156 features, 29 modules and 545 screens remain mandatory. |
| Current integrated build and tests | **New candidate CI pending.** Historical C passed all seven Foundation jobs, all ten PR checks, 788 native and 60 connected cases. E and C2 subsequently failed different fixture issues and remain failed. Both corrections are recorded above; require fresh full validation, evidence preservation and merge/main checks. |
| Honest per-case route evidence | Current ledger **CI_PENDING**. Historical C's archived, bounded PASS covers 330 combined IDs and leaves 215 unresolved. Preserve distinct evidence classes, exact source/case pins and zero green claims when admitting any new passing run. |
| Artemis functional target | Obtain current successful controller/screenshot/hierarchy observations and configured provider readiness, then run source/APK-bound scenarios through admitted MCP and inspect sealed terminal receipts. Boot/admission alone does not close this gate. |
| Live Google/Drive | AI Studio test-project creation is UI-observed, but API credentials/secure installation and Drive OAuth configuration remain pending, with Cloud Console still unavailable. Obtain the required supported access and a compatible Google-enabled target, then execute actual app consent, folder/configuration, approved background delivery, another-device catch-up and restoration/revocation cases under the existing local-first contract. |
| Recovery and multi-device acceptance | The named Android durability, Activity and replica cases passed on one C CI emulator. Physical restart, two physical devices, all-device-loss escrow and a complete disaster-recovery procedure remain unproven; preserve the actual database-reopen, Keystore-alias and loopback boundaries. |
| Visual and accessibility acceptance | Preserve the Animal Farm reference authority and approved assets. Required coverage includes light/dark/outdoor, 1.0/1.3/2.0 font scaling, 360/411/780 dp widths and applicable keyboard/inset/long-content/TalkBack/locale/role states. Native captures require the prescribed owner and independent acceptance; this implementing agent cannot approve its own goldens or a matrix reduction. |
| Whole-product/field release | Complete the remaining security/data-integrity, restore, performance, representative mixed-farm and applicable hardware/clinical/AI evidence. Module/MVP green derives from completed mandatory contracts. `release_blocked` remains authoritative until its actual gates close. |

The prior ten-surface, 270-capture reference matrix is a foundation sample, not all-screen visual acceptance. Neither the reusable testing target nor the four Artemis scenarios reduces the product scope.

## Final integration record — pending parent completion

Source and evidence history remains ordered: C passed; its evidence descendant E failed report readiness; C2 corrected report readiness but failed the navigation fixture database lifetime; the reviewed lifetime correction is now integrated at `69b3eb244d5e99983bcb2db4301560315f49c671`. The pending replacement candidate must pass its own full Foundation workflow. Earlier failures are retained and no downstream skipped job is accepted as execution.

| Required final field | Status |
| --- | --- |
| Consolidated branch and PR | `chatgpt/goat-artemis-qualified-changes-20261009`, PR111; not merged. |
| Current corrected source | Navigation lifetime correction `69b3eb244d5e99983bcb2db4301560315f49c671`, tree `3f7380ea2f4207cea78a75c063c9e252c6cecb9d`; replacement candidate source/map reconciliation follows. |
| Focused report correction | PASS, seven fresh cases at C2, independently checked; all seven also pass in C2 canonical CI. |
| Focused navigation correction | PENDING fresh execution of the two preserved six-case suites. |
| Full corrected Foundation/native/connected validation | PENDING; E and C2 are failed histories. Historical C passed 788 native and 60 connected cases at its own source. |
| Per-case source map and active ledger | All 42 additional cases and their claims preserved; two reviewed source hashes updated. Current ledger CI_PENDING; original C PASS archived unchanged. |
| Local reusable emulator | API34 Google APIs revision14 installed and image verified; service prepared disabled, boot/controller/provider qualification pending. |
| Final merged APK/certificate/provenance and installation | PENDING. Preserve existing PR110 draft testing package. |
| Google testing configuration | Dedicated project and inactive named gcloud configuration prepared; no key, authenticated CLI, ADC, Drive OAuth client or actual app consent/delivery. |
| Evidence descendant, merge/main checks and revised draft package | PENDING. |
| Visual, physical, restoration and whole-product acceptance | BLOCKED; no feature/module/visual/MVP green promotion. |

## Selected retained receipt hashes

SHA-256 identifies the exact observed bytes; a hash is not an independent qualification signature.

| Receipt | SHA-256 |
| --- | --- |
| `report-tests-result.json` | `d567ea4ba1fdabce36d47937fe08f4ef1d5b34c0149e6fe9bad3e96f52a6c7c4` |
| `back-tests-result.json` | `9c1e0b8637a2843d7f8c0db7730f972cb74e05ab5ab8e943e68657fcbb65a043` |
| `search-tests-attempt2-result.json` | `ca031d43cb9e6acd67ad089de15f8b381974258d6e697cc6560f1b73ac0b027e` |
| `recovery-tests-result.json` | `0871b25e12c8a614585ce0268b235f25b72160f4456de2107dfd3903d2f4bf40` |
| `recovery-tests-attempt2-result.json` | `9a127deb81163efb3c29b33ef499338fb3b3b5f562cc887058060dc794058b18` |
| `recovery-tests-attempt4-result.json` | `f674e4dcd26e9f44424fbe5f1bd03d3b3d58b2b87b575edfb2343c9a960f024c` |
| `species-tests-attempt1-result.json` | `7d20ff1e7f26b86409a4b4ca1878a0b8e85093fb6e29f119d320afed7ecf7094` |
| `session-tests-attempt2-result.json` | `9f9005133a5aa4ba95f266a493227926abfb9c475047f892fad08ddf7a76c5dc` |
| `session-tests-attempt3-result.json` | `a6c3a29a058dfdce0f6de388e217d5a0ca9caa0204490dd8aabf760b97ad3aec` |
| `combined-native-attempt1-result.json` | `589b9843003d84752a10bad44c51a7fc95047dafa22be1c9dc5ee9bebb1ab524` |
| `combined-native-attempt1-gradle.log` | `83dc0e09afccb952cfe2334f650d3fc26830972414e2ab8e0368426a87f3bf73` |
| `packaging-attempt1-result.json` | `7a109d2b2d8c8c5aeca1a209e83cf2aab1ae34966a063342f25d10215dc9947a` |
| `packaging-attempt1-gradle.log` | `fd4cd9754eccf1245f1cdf20e9428d8b3fdd39110db16db640affca331efaaee` |
| `atd-tb128-probe-153852.json` | `7e683398e899bb20408c7ffadd675bdeeef067427e74ef49067270b9246f71a1` |
| `atd-device-admission.json` | `0454d5289fbf17000454ab071db743b871b7c097e4cdcc523253b5506804ec71` |
| `artemis-atd-preflight-response.jsonl` | `98fa2c1c6e40141f7b2958dacc38bdc18eeb57872dbb6a6190eba428281a1d04` |
| `artemis-atd-preflight-progress.json` | `3d128deb2e3a5af97edccb345e6a200d72f39f529d2526fd27c37139a3f3721d` |
| `atd-tb128-controlled-stop.json` | `0fc1f1a171dc2d283e1371fa31c30d52c323d176974a723a42f5081d004c9cd7` |
| `googleplay-tb128-configured.json` | `c56d4756b4fbda57d495faa8470ae52356593ff2951e6e917e075a7637776a4c` |
| `google-cli-readiness-20261009T154757Z.json` | `e9901841d64d2619eb20ae570743c481f080dbd47582a7ff46ad10e81b9087e9` |
| `google-test-project-created-20261009.json` (private observation record; identifiers omitted here) | `c7d3731fe9324354e93042300e57f1813ca126e800af099ec0e8d0de0f94743d` |
| `ci-pr111-candidate/native/validation-summary.json` (earlier native-only observation) | `8ed3dfbfc892ed3063867e886facc25d7dbea0c3fe33c0e334a65ecc52f8503f` |
| `ci-pr111-candidate/root-independent-candidate-verification.json` | `210dc23986a4a4fe02eb5a8097b24447767b14ba943185ceb1b9c49d42a9afa6` |
| `ci-pr111-candidate/connected/connected-ci-evidence.json` | `b59b850ed4b85008c2d0d4820d981cb80515e90a9d14314356ab1fcd911f4edf` |
| `googleplay-tb128-boot-attempt2-result.json` | `0bd4698b3c14137ef6bd9e363bff74de636547a936c1ff636685b29c58fe6a0b` |
