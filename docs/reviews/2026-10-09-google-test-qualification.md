# Google-backed test and product qualification

## Status and owner direction

**Product release qualification remains open.** On 9 October 2026 the owner authorized using their Google account for cloud configuration for testing and requested closure of product release qualification. This continues the Muse consolidation merged in [PR #109](https://github.com/Vanguduza/goat-app/pull/109). The complete product scope, local-first architecture and existing evidence requirements remain applicable; testing does not introduce a reduced release tier or remove a gate.

This record establishes the proven starting point and the acceptance work still required. It grants no `FEATURE_GREEN`, `MODULE_GREEN`, `MVP_GREEN` or `VISUAL_GREEN` status. The release block in [`PROJECT_CANONICAL_STATE.json`](../../PROJECT_CANONICAL_STATE.json) remains effective under [`PROJECT_TRUTH_PROTOCOL.md`](../../PROJECT_TRUTH_PROTOCOL.md).

## Exact main baseline

The following evidence belongs to the main commit merged on 9 October 2026. Changes made after that commit require their own exact-candidate validation.

| Provenance | Recorded value |
| --- | --- |
| Repository | `Vanguduza/goat-app` |
| Main commit | [`936a94ea4ad6e88e3965612b8075b1683a4b3895`](https://github.com/Vanguduza/goat-app/commit/936a94ea4ad6e88e3965612b8075b1683a4b3895) |
| Source tree | `d7e11d6372398f633edffbc2bef5765334ee703b` |
| Pre-merge candidate with the same tree | `d5093121b182ad838e0d07e7ed4d811ef8cc36f0` |
| Main Foundation run | [37909999633](https://github.com/Vanguduza/goat-app/actions/runs/37909999633), `push` / `main`, completed successfully; final update `2026-10-09T09:27:23Z` |
| CI debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| CI debug APK SHA-256 | `f27d5efeec7bcd5102e924e9522d35e096c4f52798de669120d4bc8c57529aae` |
| Baseline application identity | `com.farmos.app`, version code `1`, version name `0.1.0-foundation` |

Application identity and build definitions are recorded in the [baseline app Gradle configuration](https://github.com/Vanguduza/goat-app/blob/936a94ea4ad6e88e3965612b8075b1683a4b3895/app/build.gradle.kts). The APK hash above identifies the output logged by this CI run; it does not transfer to a rebuilt or differently signed APK.

### What the main run executed

All seven Foundation jobs succeeded. The four server-era jobs (`supabase`, `meilisearch-contract`, `search-pipeline`, `edge-functions`) remain provenance checks; they do not restore an application server as an authority.

| Execution | Verified scope | Acceptance boundary |
| --- | --- | --- |
| [Android job 113752596539](https://github.com/Vanguduza/goat-app/actions/runs/37909999633/job/113752596539) | The declared Android/domain unit-test and assembly tasks completed; the debug APK hash was logged. | Passing executed tests does not complete every Feature Implementation Contract. No aggregate unit-test count is inferred from task names. |
| [Device job 113754515037](https://github.com/Vanguduza/goat-app/actions/runs/37909999633/job/113754515037) | The logs show 51 database instrumentation tests followed by 3 app instrumentation tests, with both Gradle invocations successful. | The runner uses one emulator, separate local databases and loopback LAN. This is evidence for the exercised scenarios, not two physical handsets, live Google consent or all-device-loss recovery. |
| [Native job 113754514937](https://github.com/Vanguduza/goat-app/actions/runs/37909999633/job/113754514937) | Ten reference surfaces × three themes × three widths × three font scales: 270 deterministic combinations. Raw captures, annotations, UI trees, bounds and touch-target checks completed. | The reference set is unapproved. Machine checks do not supply owner pixel acceptance, independent review, TalkBack acceptance or full-screen certification. |

The exact execution definitions are [`foundation-ci.yml`](https://github.com/Vanguduza/goat-app/blob/936a94ea4ad6e88e3965612b8075b1683a4b3895/.github/workflows/foundation-ci.yml) and [`run-two-device-e2e.sh`](https://github.com/Vanguduza/goat-app/blob/936a94ea4ad6e88e3965612b8075b1683a4b3895/scripts/ci/run-two-device-e2e.sh). The latter excludes the historical live-Supabase authentication test; its successful completion makes no live-provider authentication claim.

### Main native artifact

| Artifact field | Recorded value |
| --- | --- |
| Name | `animal-farm-native-reference-unapproved` |
| ID and location | [11607140616](https://github.com/Vanguduza/goat-app/actions/runs/37909999633/artifacts/11607140616) |
| SHA-256 | `215485a90ece43064204fa743a333a805e305f049ceb6ef43a34b661b160d2a7` |
| Size | 32,544,790 bytes |
| Recorded expiry | `2026-10-23T09:24:46Z` |
| Approval status | Unapproved; no independent, owner or outdoor acceptance is issued by this record. |

Artifact metadata and decoded job logs were inspected for this record. The bundle was not downloaded or independently visually accepted during this documentation audit. Durable preservation and review must retain its exact commit, run and digest; a link to an expiring artifact is not a permanent golden baseline.

### Historical certificates remain pinned

The original `VERTICAL_SLICE_GREEN` certificate remains the 3 September 2026 pre-lock Supabase/Meilisearch proof at `6c7c79a93dc54c74134a70b3763549b442c22349`, [run 33801257315](https://github.com/Vanguduza/goat-app/actions/runs/33801257315). It authorizes feature fan-out only.

Separately, checks VS-21–VS-24 in [`VERTICAL_SLICE_GATE.json`](../realisation/VERTICAL_SLICE_GATE.json) record post-lock Room/LAN evidence at main `9bc3001258f291fd8baa60e30026d55f10847171`, [run 37028681388](https://github.com/Vanguduza/goat-app/actions/runs/37028681388). Their recorded emulator and loopback limitations remain applicable. The 9 October main run above adds execution evidence for its own commit; it neither rewrites those historical proofs nor expands them into full LAN/Drive or product certification.

## Google and Android acceptance

At work opening, the connected Google account was verified privately. The Cloud console was unavailable in the browser used for the initial inspection, the VM Cloud CLI had no signed-in account, and no ADB test device was attached. These are initial access observations, not permanent platform limitations. Actual configuration and device acceptance must be recorded when performed. Account email addresses, credentials and private signing or farm keys remain outside the public repository.

### Stable build and cloud configuration

Cloud acceptance must bind the actual installed build to an owner-controlled signing identity:

1. Record the repository, exact tested commit and ref, build target, package ID, version code/name, installed signing-certificate fingerprint and APK SHA-256. Use a stable, securely retained signing key for repeatable installation and upgrade acceptance. The baseline Foundation debug build alone does not establish that continuity.
2. Configure the Android OAuth client for the installed package and signing-certificate fingerprint, with the intended project, consent configuration and test-account access. Record non-secret configuration references and verified outcomes. Do not commit credentials, tokens, private keys or private account identifiers.
3. Complete the Android app's actual Google authorization flow on the target device. Select and verify the intended Drive destination through the app. Connected-account access in a separate tool is not evidence that the installed Android package has consent.
4. Preserve farm-local account, role and device authorization independently of Google identity. Store provider tokens and farm keys through the accepted secure storage boundary. Verify key custody and the owner recovery procedure before treating uploaded data as recoverable backup.

### Live acceptance to record

Use test farm data and retain evidence for the exact APK and participating device builds:

- Create a local operation and attachment, observe durable local success, upload to the selected Drive destination, then retrieve and replay on a separately authorized replica. Verify original operation identity, business time, audit, farm scope and attachment integrity.
- Exercise denied or cancelled consent, expired or revoked access, unavailable account/folder and transport interruption. Accepted local state must survive; retry and pending states must be truthful. Record the evidence that permits each transition to `Synchronised` or `Backed up`.
- Verify the intended foreground/background delivery lifecycle, process restart and physical-device reboot. Prove recovery on replacement devices when every original device is unavailable, including the owner/key recovery boundary and rejection of unauthorized recovery.

These are acceptance conditions, not reports that the scenarios have already run. The locked behavior comes from [`docs/00_PROJECT_TRUTH.md` §0](../00_PROJECT_TRUTH.md) and [`GOAT_OFFLINE_MULTI_DEVICE_SYNC.md`](../architecture/GOAT_OFFLINE_MULTI_DEVICE_SYNC.md). Google Drive remains an operation/attachment replication and backup transport; the operational database stays local.

## Candidate implementation in PR #110

The qualification candidate is [PR #110](https://github.com/Vanguduza/goat-app/pull/110). Its source changes address one local-first authorization and delivery boundary; they do not certify the remaining product contracts.

### Local commands and historical operations

The shared Ops/Herd policy explicitly covers 135 command IDs and fails closed for unknown commands. Legacy Goat writers use the same current-account/local-device admission rule. App management commands now check permission in their Room transaction; recording currency preserves the Owner-only D-018 requirement. A disabled, foreign, missing or demoted account, or a nonlocal, retired/revoked or cutoff-bearing device cannot create a new local business write or consume a replication sequence.

An accepted retry must match its original farm, mutation ID, command/schema, entity, actor, device, business time and decoded payload. It returns before changed business-state validation. Received pending operations keep the original historical identity and require an existing journal receipt; a caller cannot manufacture replay admission or borrow the receiver's current role. Mutable customer, sale, lifecycle and configuration checks remain inside the transaction that journals the write. Fault-injection cases exercise rollback of projections, outbox and device sequence together.

Three command-version changes preserve existing history:

- New generic gram measurements use `rabbit.record_weight.v2`. The shared v1 decoder distinguishes the historical gram and kilogram shapes explicitly, independent of applier-map order; ambiguous payloads remain reviewable failures.
- New formulary entries use `formulary.item_create.v2` and seal `vetApproved=false`. The catalogue identifies drafts and clinical selectors exclude them. Historical v1 evidence is preserved. This does not implement veterinary attestation or authorize treatment merely from a role.
- New `inventory.record_reorder.v2` operations seal the observed on-hand and reorder quantities. Retry and replay use those original figures. Applied v1 history stays unchanged; an unapplied v1 lacks the original snapshot and remains in review rather than reconstructing it from current stock.

### Durable Drive delivery and key rotation

Foreground and WorkManager delivery share one farm coordinator. Approved configuration is bound locally to the approving farm account and exact device. Each carrier operation rechecks current storage permission and active local-device authority. Legacy unbound configurations and denied approval require explicit reconnection; receiving replicated settings cannot grant a device cloud access.

Background work stores only the farm ID, uses network constraints and unique immediate/periodic work, and requests silent renewal of existing Google consent. It cannot open a consent screen or provision missing farm keys. Durable status distinguishes offline, retry, missing consent, approval, missing keys and pending rotation. Provider failures cannot undo committed local records. A successful worker completion is not itself a claim that farm data was backed up. Scheduling awaits both WorkManager enqueue results, asynchronous scheduler failure remains visible, and cancellation after a running attempt is persisted as retryable interruption. Drive writes recheck approval after metadata lookup and immediately before upload.

Farm-key rotation stages a UUID-identified key inactive in the sealed vault, commits its exact wrapped-key operation, then reconciles the journal before activation. A failed journal or activation retains historical keys and a recoverable pending marker. A mismatching journal blocks transport. Received and delayed local rotations preserve all keys and compare business time plus key ID deterministically. A new local rotation must have a supplied business time strictly later than the device's observed current rotation; a clock-order conflict is rejected before staging or journaling. This is rotation recovery on a device with its sealed vault, not an all-devices-lost owner key escrow or restoration mechanism.

An authorized gateway can now relay accepted enrollment and rotation prerequisites in narrowly scoped, signed bootstrap objects encrypted under retained farm keys. The signature binds the exact encoded operation, farm, gateway identity and object path. A previously known, currently permitted device must attest a new signer or rotation; an old encryption key alone cannot introduce a device that then signs its own rotation. Admission rechecks the signer in the Room transaction before accepting new journal positions. Normal operation bundles remain encrypted under the current key and retain their original identities. Missing prerequisites stay visible; historical unsigned archives need a current authorized gateway or prior authenticated LAN receipts to supply the attestation. This is gateway attestation, not per-origin authentication of all historical business operations.

LAN synchronization can recover from independently rotated current keys by retrying a shared retained key only after the protocol's specific initial key-change refusal. Each retry uses a new socket, ephemeral key and nonce, and requires the already known peer's authenticated device identity. Other refusals and timeouts do not trigger a key downgrade.

### Native reference and test-build identity

The FOS-GLOBAL-002 native reference now renders the actual farm/username/PIN entry flow rather than the obsolete email/password fixture. The ten-surface, 270-combination matrix remains unapproved; correcting a fixture does not supply owner or independent acceptance.

The [test-build operator](../../scripts/testing/prepare-google-test-build.py) requires a clean expected repository commit, the retained external debug certificate, and empty server-era provider inputs. It assembles the existing debug variant, retains the APK before signature and manifest inspection, verifies the installed identity with Android SDK tools, and records its hash, public certificate, toolchain and canonical evidence revisions. Its provenance guards test dirty source, missing keys, substituted identities and a competing build replacing Gradle's original output. No private key or credential is included in the output.

### Candidate execution record

Local validation before the final CI candidate established:

- Access (9 cases), Ops (68 cases) and replication (49 cases, including six new real-socket retained-key cases) passed. The unchanged Goat (19) and Rabbit (16) domain reports were restored from the build cache and are distinguished from fresh execution.
- The production application, app instrumentation sources and database instrumentation sources compiled successfully. The app regression run executed 208 cases across 53 suites. It passed 203 cases and exposed five failures confined to three test fixtures: coroutine exception identity, the single-farm entry expectation, and a positional inventory argument that populated a timestamp rather than the reorder threshold. Those fixtures were corrected without weakening their behavior checks; the journal rollback case now requires the injected SQLite failure.
- All seven cases in the corrected fixtures passed their targeted rerun, with zero failures, errors or skips. Final acceptance of all app and domain cases, native references and instrumentation is bound to the final CI candidate recorded on [PR #110](https://github.com/Vanguduza/goat-app/pull/110). The earlier main CI table remains baseline evidence only.
- Architecture, visual-authority, source-inventory consistency and the initial four test-build provenance guards passed. Source inventories remain `CI_PENDING` until separately reconciled; historical certificates and approval ledgers are not relabelled by this change.

The first full implementation run, [37923471779](https://github.com/Vanguduza/goat-app/actions/runs/37923471779), executed 469 app tests and exposed two remaining fixture failures: the reference sign-in assertion did not await its Room-backed callback, and the health navigation test still used the former formulary action label. Both fixtures were corrected while preserving the sign-in identity and route/return assertions. The four backend jobs passed, but native and instrumentation jobs were skipped because Android failed; this run supplies no native artifact or verified CI APK.

The first retained APK assembled successfully, but the operator refused SDK 37's changed public output labels. The corrected parser accepts the verified scheme label and minimum-SDK field from the actual SDK output, retains the single-signer and matching-certificate checks, and rejects ambiguous SDK metadata. Its five regression methods now cover both tool formats. A fresh clean-source preparation is required to issue final provenance; diagnostic parsing of the earlier retained bytes does not qualify that failed preparation.

The PR records the final immutable head, exact CI runs and results; retained test-build provenance records its actual source and installed identity. No live Google OAuth, physical two-device, independent visual or full-product acceptance is claimed by this implementation section.

## Remaining full-product gates

The baseline [`PROJECT_COMPLETION_STATE.json`](https://github.com/Vanguduza/goat-app/blob/936a94ea4ad6e88e3965612b8075b1683a4b3895/PROJECT_COMPLETION_STATE.json) retains 156 mandatory features, 29 modules and 545 registered screens with zero feature/module/MVP/visual green claims. An implementation inventory is not certification or a substitute for reviewing each contract.

| Gate | Exact closure condition and evidence still required |
| --- | --- |
| Feature and module completion | Implement each complete Feature Implementation Contract and pass every applicable gate in [`AGENTS.md`](../../AGENTS.md): domain/property tests, migrations/model integrity, farm/role authorization, offline and concurrency behavior, search/rebuild, reconciliation, recovery, AI safety, UI/accessibility, NFR/observability, field workflow and donor provenance. Every mandatory feature must pass before its module can be green. |
| Local authorization and replication integrity | Validate the candidate write-boundary and delivery corrections above, and close the remaining clinical-attestation and per-origin authentication/revocation contracts recorded in the [Muse consolidation review](2026-10-09-muse-consolidation.md). Transport encryption and current role admission alone do not prove every historical-authority or clinical property. |
| Native visual acceptance | Complete every applicable registered-screen contract, route entry/return and state/interaction/accessibility evidence. Obtain approved native goldens, screenshot diffs, independent review, owner pixel acceptance and outdoor acceptance under the [visual gates](../ux/animal-farm-visual-lock/MIGRATION-AND-GATES.md). The main artifact covers ten reference surfaces and does not certify all 545 screens. |
| Device, field and recovery acceptance | Supply actual device and representative mixed-farm workflow evidence, including offline durability, physical restart/reboot, two-device synchronization, conflict/rejection, device loss/revocation, all-device-loss restore and security/data-integrity checks. Emulator/loopback evidence retains its execution boundary. |
| Performance and accessibility | Execute the applicable phone/tablet, supported locale/RTL, keyboard/inset, long-content, permission, TalkBack, battery and performance cases required by the [screen standard](../ux/animal-farm-visual-lock/references/SCREEN_DESIGN_STANDARD.md) and visual gates. Preserve the light/dark/outdoor × 1.0/1.3/2.0 font-scale × 360/411/780dp-width matrix; any reduction requires independent approval and recorded rationale. |
| Signed build and cloud acceptance | Bind a stable signing identity and Android OAuth configuration to the installed APK, complete the live acceptance above, and retain install/upgrade, consent, provider failure and recovery evidence without exposing secrets. |
| MVP and release decision | Every mandatory module must be `MODULE_GREEN`; whole-product security, data integrity, offline, restore/disaster-recovery, performance and representative mixed-farm certification must pass under [Project Truth §9](../00_PROJECT_TRUTH.md). A release record must bind exact provenance and applicable evidence; the canonical release block can be changed only from supported qualification. |

## Evidence and next actions

1. Complete the bounded signing/cloud and authorization implementation work, review it, and freeze the candidate sources before refreshing evidence. Preserve the existing historical certificate and Phase 4 approvals; do not borrow a passing run from another source tree.
2. Run the declared candidate checks and record the exact commit, source fingerprint, CI run, command results, device/build identities, artifact paths/digests and limitations. [`refresh_source_evidence.py`](../../scripts/development/refresh_source_evidence.py) produces pending source inventories with zero executed certification; it is not a CI-promotion or release-unblocking tool.
3. Perform live Google/Drive and physical-device acceptance with the stable signed build. Record each observed success or failure, the relevant logs/artifacts and the remaining external action where evidence is unavailable.
4. Obtain the required independent and owner visual decisions against actual captures and diffs. The implementing agent cannot approve its own baseline or exception. [`verify-pack.cjs --release`](../ux/animal-farm-visual-lock/VALIDATION.md) checks selected-screen evidence structure and hashes; it neither executes Android tests nor authenticates a reviewer or qualifies the entire app.
5. Continue remaining feature/module and whole-product acceptance. Update generated completion and release state only from evidence that meets the existing contracts and through reviewed changes to the evidence machinery where needed. Preserve all mandatory scope and the separate meanings of architecture, feature, module, MVP and visual green.

A successful Google test setup closes that setup's demonstrated acceptance items. Full product qualification remains open until the complete release conditions above are evidenced and accepted.
