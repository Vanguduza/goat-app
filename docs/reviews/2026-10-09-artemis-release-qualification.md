# Artemis integration and release qualification — 9 October 2026

## Status and authority

**Status: integration qualification remains open; product release remains blocked.** This record consolidates the observed preparation, executed local tests, unsuccessful attempts, source reviews and outstanding evidence. It does not issue a feature, module, visual, physical-device or MVP certificate.

The review branch starts at GOAT commit `4a556f3b5a8fe20cce89e954d52288b3545270eb`, tree `d54831d806b0169031aad6ffecc97dfe9e4f06b0`. That is an integration checkpoint after the earlier search/navigation work, not the final candidate or a new main certificate. The inherited main remains PR110 merge `349d40339923e6860f7ee7e74e5f149afab11f47`; its earlier qualification and testing-package evidence remains in [the Google test qualification record](2026-10-09-google-test-qualification.md). New source changes require their own exact-candidate execution and CI evidence.

[Project Truth](../00_PROJECT_TRUTH.md), [the Project Truth Protocol](../../PROJECT_TRUTH_PROTOCOL.md), [AGENTS.md](../../AGENTS.md), [the completion control plane](../realisation/COMPLETION_CONTROL_PLANE.md) and [the Animal Farm migration and acceptance gates](../ux/animal-farm-visual-lock/MIGRATION-AND-GATES.md) remain binding. GOAT is a local-first Room application with local accounts and operation-level LAN/approved-Drive replication. Artemis is testing infrastructure; it is not an application dependency or another data authority.

The full mandatory scope remains **156 features, 29 modules and 545 registered Screen IDs**. The inherited completion state has **0 feature greens, 0 module greens and 0 visual-green screens**, and `release_blocked=true`. No flag or protected reference was changed to prepare this record.

The inherited Phase 5 ledger is bound to `ee514f2f98d6872dcf941ad18d6e9dde69f09457` and Foundation run `37928955372`. Its combined 315/545 IDs mix rendered traversal, rendered-owner, entry-action and route-contract evidence. They are not 315 fully qualified screens. The inherited inventory leaves 230 IDs without this combined evidence and 55 of 156 features without route evidence. Those historical counts are not a recertification of the new candidate; the per-case evidence review and final refresh remain pending below.

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
| Exact Sheep/Cattle/Rabbit journeys, 16:03:55 — `species-tests-attempt1-result.json` | Nine app suites, 41 freshly executed cases: Android Back 6; runtime routes 10; global-search contract 3 and rendered search 6; Rabbit profile 2, record navigation 4 and runtime navigation 1; species herd navigation 3 and exact species search 6. Source fingerprint `412495a6631e3a2853a7dd85a030d32cdc72d3d275919df723748553cfb73bcc` was unchanged. | PASS with no failures/errors/skips. The receipt records working HEAD `95e150c8651742c1160e33837d70821ed8b449ef`; the tested patch was subsequently committed as `a244a1606a15b75b28e37b8baacb6592b7551558`. Final joined-source execution remains pending. |
| Session/report readiness, targeted attempt 3, 16:15:05 — `session-tests-attempt3-result.json` | `LocalFarmSessionAuthorityTest`: 6 cases; `ReportRuntimeNavigationTest`: 8, freshly executed at the corrected source fingerprint recorded below. | PASS for these 14 cases. The initial 55-case attempt and the intervening 14-case attempt remain separate failed histories; neither is silently relabelled. |

The retained first/second recovery logs distinguish compilation failures from test failures and restored results. Subsequent fixes must retain the strengthened runtime transaction/authority checks and correct the real fixtures or caller paths; no timeout increase or permission bypass is accepted as closure.

### Reviewed slices and consolidated-validation boundaries

Source review and local execution are separate below. The whole joined candidate still requires its own validation:

- **Session authority and report disclosure:** coherent Room observation of the current farm/account/local device, credential continuity, role-keyed disposal of privileged nested state, fresh export/share admission, and cleanup across carrier-startup or disclosure cancellation. The independent review checked the corrected partial-startup and opened-stream ownership paths. The new suites are `LocalFarmSessionAuthorityTest`, `LocalSessionAuthorityTest`, `ReportDisclosureAuthorityTest` and the updated `ReportRuntimeNavigationTest`. The first session run executed 55 cases, with 53 passing and two test-helper failures (an unscrollable synthetic delivery button and an intent poll that did not idle Android’s main queue). Those were corrected. Targeted attempt 2 freshly executed 14 cases with 12 passing; its two different failures exposed a report hub rendered before metric loading/reflow and a returned Documents wrapper rendered before its IO entries. The two original failures passed in that attempt. The helpers now wait for the actual loaded data and one enabled/displayed action, without timeout increases or repeated-click retries. Targeted attempt 3 freshly executed the same 14 cases successfully at 16:15:05, with no failures/errors/skips and unchanged test-driver source fingerprint `9e8764fa764020ae797aee6792415079e484b45b2f60267a23bd6ea28ca33e5f`. The 12-file slice is now commit `b9251a6fa44f3bf5927dbbc8b48909350c81ed28`, tree `ba23504965e4d60d0e5d559ba112449c90d802d0`, recorded through the normal Truth hooks and verified clean. This targeted rerun is not a fresh execution of all 55 cases or the final joined candidate. Both failed attempt receipts remain retained.
- **Exact Sheep/Cattle/Rabbit profiles:** farm/species-scoped direct reads beyond the 200-row list; recorded Rabbit sex validation; profile/farm state replacement; nested returns to the selected profile and retained search. The six `SpeciesSearchRuntimeNavigationTest` cases and updated route mapping guard were independently source-reviewed without a remaining concrete introduced blocker. They passed within the 41-case species run above. The source commit is `a244a1606a15b75b28e37b8baacb6592b7551558`, tree `cd0c100aee8c00d6e7be636b73aa60ef5c3c14fc`; final joined-source execution remains pending.
- **Actual MainActivity journey:** `LocalFarmActivityJourneyTest` uses the production activity and UI for a synthetic local farm, Owner, goat registration and exact 45.125 kg record, followed by activity recreation, sign-in, exact search/profile and Back. It requires an empty isolated local-only app installation and retains its synthetic data. `ActivityScenario.recreate()` is not process death, Room close/reopen, filesystem power loss, a physical reboot or second-device proof. The three-file source slice was independently reviewed without a remaining concrete introduced blocker and committed as `13be7bbfc898e17b3502b2b4360ab8ea7dbde870`; its normal integration commit is `c3741a4ff534e6e8ef98b783d31b82676e42caa6`, with identical tree `e35b14e463954e719e56b6d187f21470db8066a8`. Joined compilation and device execution remain pending.
- **Recovery durability:** the separate Android instrumentation exercises named file-backed databases, recovery-code/PIN transitions, a failed SQLite journal write, and a test-owned Keystore alias loss. This is not all-device-loss escrow or a physical power-loss test. The corrected local source is commit `3c6cfef20026e498e4ebe3c37f788a2e86d8ff16`, tree `7d55453c438a9d355cf9f334f186fee0d58b5b42`. The 19 previously affected application fixture cases passed in attempt 4; the three Android durability cases still need device execution.
- **Per-case Phase 5 evidence:** the two consistency helpers and three builder/consumer hooks were independently source-reviewed. A concrete incompatibility with the real report click helper was corrected with a focused positive/negative regression; no remaining concrete blocker was found in this bounded source review. The checker distinguishes complete exact method identities, reviewed source/map hashes, positive traversal/return anchors, actual JUnit execution and same-tree clean artifact identity. It does not authenticate CI origin or issue approval. The final map, committed source hashes and current run binding remain pending. Session/security cases must remain executed-test evidence without invented screen traversal.

The joined source was independently read at clean commit `88de9dabf8e277bf0fb7a6ef4a21769041761aa4`, tree `442c263fd4371a1c5f4984fb53c8144144600b48`. The extracted `LocalFarmSession` factory carries `initialKeys = app.keyVault`; `MainActivity` still selects this production local path. Accepted-only carrier startup, role-keyed content disposal and failure cleanup remain intact. `FarmSessionContent` retains role-keyed TaskPlanning, per-farm search state, exact species destinations, the return-to-search callback, and both the current export authority and correct Back callback on Reports. `LocalFarmEntry` has no competing session factory. No concrete merge regression was found in this bounded read-only review. The observed source SHA-256 values were `67c176bdb161cb00d13adf404eb79a04815d67f99b557c61c03e543870a01584` for `app/src/main/java/com/farmos/app/LocalFarmSession.kt` and `1a2093fe4d3f6321526fb1e0674e748d6e269bd63f689d50e2534ac0e11757be` for `app/src/main/java/com/farmos/app/FarmSessionContent.kt`. This is source-review evidence, not a successful combined build or device run.

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
| Google Play reduced-cache follow-up | At 15:47:06, the 1536 MiB / 1-core / 3 GiB Google Play profile was prepared with `-tb-size 128`. The receipt explicitly records **not started and not admitted**. Its eventual boot/controller/Google results remain pending. |

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

These are separate boundaries:

| Boundary | Observed status and required next evidence |
| --- | --- |
| Artemis model execution | Both the earlier provider preflight and the admitted-ATD diagnostic report `configured_count=0` and no pinned `.env`. This blocks provider-backed autonomous tasks. The pinned source supports its native API-provider configuration, and Vertex AI requires its supported authorized ADC/project configuration. Each selected model needs a compatible provider. No existing DDS subscription CLI or Drive connection was repurposed as a secret source. |
| Google Cloud CLI | Fresh read-only observation at **15:47:57.647379 UTC**: SDK **586.0.0** installed, `gcloud auth list` returned **0 accounts / 0 active**, and the ADC file was absent. No credential contents were read or recorded; no login was initiated. This does not prove Cloud project access or OAuth-client administration. |
| Supported CLI authorization | The installed help documents `--no-browser` with a trusted second machine containing browser and gcloud, and `--no-launch-browser` with an interactive browser authorization-code exchange. These require an owner-controlled secure terminal/browser handoff. Codes, tokens and credentials must not be routed through model/chat messages or committed to GOAT. The help hash is retained; no authorization flow was started. |
| Cloud Console | The owner-facing browser observation remained **Site Unavailable** at approximately 15:24 UTC and again on the Cloud Console root URL at approximately 16:03 UTC. This is a dated access observation from the parent operator, not evidence of an anti-bot challenge, a successful login or OAuth configuration. A later successful console or supported CLI operation must have its own receipt. |
| GOAT Google Drive | A Google account connected to another client, working gcloud, an Artemis provider key, and GOAT's Android consent are distinct. Live Drive needs an appropriate Google-enabled target, the actual APK package/signing identity configured for consent, explicit in-app `drive.file` authorization, current GOAT storage/device approval and verified delivery/readback. These remain pending. |

Credential setup must use a supported owner-controlled mechanism, outside GOAT task inputs and source. The observed missing provider or Cloud access does not block offline Room workflows or deterministic local tests. It does block claims that Artemis autonomous execution or live Google delivery has succeeded.

The public CLI help is retained as `google-cli-login-help-20261009T154757Z.txt`, SHA-256 `2498ac114ce048824e5b24c34adfc5ed264b7a9b75bb76b353c91b1e98cfaf3b`. The sanitized readiness receipt is `google-cli-readiness-20261009T154757Z.json`. It records only versions, counts, file presence, supported-help evidence and the absence of a login attempt.

## Remaining qualification gates

| Gate | Current boundary and concrete closure evidence |
| --- | --- |
| Complete feature/module scope | Finish each applicable Feature Implementation Contract: commands/queries/events, permissions, species/tenant constraints, offline/retry/conflict/recovery, reconciliation, search, safety, UI states and NFR/field evidence. A route or passing infrastructure slice cannot set a feature green. All 156 features, 29 modules and 545 screens remain mandatory. |
| Current integrated build and tests | Consolidate the reviewed slices, resolve the recorded fixture failures, and execute the relevant domain/JVM/instrumentation suites plus required Foundation checks on the final frozen source. Retain task dispositions, fresh-versus-cache provenance, JUnit and artifact hashes. |
| Honest per-case route evidence | Review exact method-to-screen claims and source hashes; distinguish traversal, owner rendering, action emission, route mapping and non-UI security tests. Verify current successful cases before a bounded Phase 5 reconciliation. Do not reuse historical class-level or static markers as new traversal proof. |
| Artemis functional target | Obtain current successful controller/screenshot/hierarchy observations and configured provider readiness, then run source/APK-bound scenarios through admitted MCP and inspect sealed terminal receipts. Boot/admission alone does not close this gate. |
| Live Google/Drive | Obtain legitimate Cloud/OAuth access and a compatible Google-enabled target, then execute actual app consent, folder/configuration, approved background delivery, another-device catch-up and restoration/revocation cases under the existing local-first contract. |
| Recovery and multi-device acceptance | Complete fresh device instrumentation and real transport/restore cases with the stated failure boundaries. Activity recreation, named-database reopen, loopback replicas and fake Drive stores do not prove physical restart, two physical devices, all-device-loss escrow or a complete disaster-recovery procedure. |
| Visual and accessibility acceptance | Preserve the Animal Farm reference authority and approved assets. Required coverage includes light/dark/outdoor, 1.0/1.3/2.0 font scaling, 360/411/780 dp widths and applicable keyboard/inset/long-content/TalkBack/locale/role states. Native captures require the prescribed owner and independent acceptance; this implementing agent cannot approve its own goldens or a matrix reduction. |
| Whole-product/field release | Complete the remaining security/data-integrity, restore, performance, representative mixed-farm and applicable hardware/clinical/AI evidence. Module/MVP green derives from completed mandatory contracts. `release_blocked` remains authoritative until its actual gates close. |

The prior ten-surface, 270-capture reference matrix is a foundation sample, not all-screen visual acceptance. Neither the reusable testing target nor the four Artemis scenarios reduces the product scope.

## Final integration record — pending parent completion

The clean root integration checkpoint `88de9dabf8e277bf0fb7a6ef4a21769041761aa4` includes the recovery, exact-species, Activity and session slices plus the committed `fresh-test-evidence.gradle` CI helper. That helper is intended to force actual native/visual Test-task execution and new JUnit each run while preserving compilation caching. At 16:21 UTC the parent started the bounded combined native validation over the nine CI JUnit modules plus Android-test APK assembly. Its terminal outcome, final per-case map binding and current Foundation evidence are pending; no successful combined or connected-device result is claimed here.

The following fields intentionally remain pending. Update them only after the corresponding action and retained evidence exist.

| Required final field | Status |
| --- | --- |
| Final consolidated source commit/tree, branch and PR | **PENDING** |
| Corrected recovery/session/exact-species/Activity test results at that source | **PENDING** |
| Independent per-case evidence map/verifier review, final source hashes and guard results | **PENDING** |
| Current full Foundation run, actual tested checkout/tree, required job/check outcomes and fresh test inventory | **PENDING** |
| Actual connected target(s), instrumentation execution and exact device/transport limitations | **PENDING** |
| Final APK digest, public certificate/package/version/provenance and installation/readback | **PENDING** |
| New controller/provider/Google Play preflight and bounded Artemis terminal receipts | **PENDING** |
| Accepted native reference/visual/accessibility and field/physical/restore evidence | **PENDING; no approvals issued here** |
| Evidence-only reconciliation with preserved historical/protected inputs | **PENDING; no generator run by this review** |
| Normal merge/main verification, temporary-branch cleanup and any revised testing-package delivery | **PENDING** |
| Feature/module/visual/MVP qualification | **BLOCKED; no green promotion** |

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
| `atd-tb128-probe-153852.json` | `7e683398e899bb20408c7ffadd675bdeeef067427e74ef49067270b9246f71a1` |
| `atd-device-admission.json` | `0454d5289fbf17000454ab071db743b871b7c097e4cdcc523253b5506804ec71` |
| `artemis-atd-preflight-response.jsonl` | `98fa2c1c6e40141f7b2958dacc38bdc18eeb57872dbb6a6190eba428281a1d04` |
| `artemis-atd-preflight-progress.json` | `3d128deb2e3a5af97edccb345e6a200d72f39f529d2526fd27c37139a3f3721d` |
| `atd-tb128-controlled-stop.json` | `0fc1f1a171dc2d283e1371fa31c30d52c323d176974a723a42f5081d004c9cd7` |
| `googleplay-tb128-configured.json` | `c56d4756b4fbda57d495faa8470ae52356593ff2951e6e917e075a7637776a4c` |
| `google-cli-readiness-20261009T154757Z.json` | `e9901841d64d2619eb20ae570743c481f080dbd47582a7ff46ad10e81b9087e9` |
