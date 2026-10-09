# Muse consolidation review — 9 October 2026

Repository: [Vanguduza/goat-app](https://github.com/Vanguduza/goat-app)

Integration: [PR #109](https://github.com/Vanguduza/goat-app/pull/109)

## Scope and preserved history

This review consolidates the recovered Muse implementation and the unique work in the remaining branches. It is an integration and correctness review under the existing local-first Android, Room, immutable journal, farm-scope and Animal Farm visual contracts. It does not certify the full product or authorize a release.

| Original ref | Immutable review-start head | Disposition |
|---|---|---|
| main | 9bc3001258f291fd8baa60e30026d55f10847171 | Integration baseline |
| PR #109: chatgpt/goat-muse-recovered-20261009 | f74e388a8ab91bf64fccbf30da975916b39ab522 | Preserve all 49 recovered commits, then add corrective commits |
| muse/goat-farm-completion-20261006 | 474e8ef76dc33c9b60849c35da5c4195854a9287 | Already included by ancestry in PR #109; its vaccination work is retained and reviewed |
| PR #108: docs/pin-local-first-slice-evidence | 8aba02312f6ebf81c6a0ac771989fc50ed20c810 | Valid evidence changes adapted in commit 80d586628e4382c116b701ad1a20fd6f9757931e, with corrected provenance |
| PR #35: implementation/runtime-navigation-reachability-phase05h | d5ad866469df7ff1e986f22ac2b73f5260e8def0 | Already squash-merged through 12eef0b1b28150e3409669da69609ea8b78e6f78 in main; do not reintroduce the stale branch |

The original PR #109 comparison comprised 119 changed files and 49 commits ahead of main. No separate cherry-pick from the original Muse vaccination branch is necessary. A merge commit should retain the recovered history.

PR #108 associated main 9bc3001 with run 37027304171, which tested a different branch commit with the same source tree. The corrected [run 37028681388](https://github.com/Vanguduza/goat-app/actions/runs/37028681388) is the successful Foundation push run bound to the actual main commit. The evidence remains historical. It does not certify this candidate, production Drive REST or physical devices.

## Corrective changes

### Command integrity, conflicts and authorization

Goat identity corrections now use a deterministic official-identifier ID derived from the farm and original mutation ID. Animal reads, uniqueness checks and updates occur inside the same transaction as the immutable operation. Duplicate requests and repeated delivery preserve identical identifier history. Received edits retain the origin's sealed aggregate base; a conflicting edit remains available for review instead of overwriting another device's accepted state.

Configuration, group and farm-resource writers were separated from RoomOpsRepository without creating a second writer. Budget revisions no longer substitute the receiver's current revision for the original base. Unit preferences are independently versioned per quantity while retaining compatibility with older farm-wide preference operations. Group and flock mutations preserve original bases, and a closed poultry flock cannot be reopened through census, move or replacement operations.

These are explicit conflicts, not automatic convergence: concurrent edits can temporarily leave different materialized values on two devices. Both original operations remain in the journal. The regression suite exercises an explicit Conflict Centre set-aside decision followed by a new correction that brings devices into agreement.

The goat identity command and 23 extracted operations now check the current farm-local account and device within the write transaction. Unit settings require MANAGE_FARM_SETTINGS; the other extracted work commands require RECORD_FARM_WORK under the existing RolePermissions contract. Inactive or absent actors, another farm's actor, revoked devices, nonlocal devices and unknown devices fail before material writes or sequence advancement. Imported historical operations retain their original admission/operation authority and are not retrospectively evaluated using a later local role.

The new exhaustive farm readers keep report totals independent of capped presentation lists. Feed, water, purchases, maintenance and labour reports can inspect the complete local farm dataset.

### Drive transport and database upgrades

The Drive gateway now uses Google's Android AuthorizationClient and actual consent from onboarding and Settings. Setup validates the selected account and writable destination before persisting a successful connection. A blank destination creates or reuses the farm's app-owned folder; typing an arbitrary folder ID does not grant access.

Journal and attachment objects are encrypted through the existing farm cipher using AES-256-GCM, authenticated against the farm and exact object path. Bundles are decoded and checked before a backup vector advances. Retries compare authenticated plaintext with the immutable original object, so randomized encryption does not cause false rewrite conflicts. Corrupt objects, inconsistent ranges, duplicate paths and missing sequence ranges do not produce false backup claims. Destination cursors are scoped to the farm, account and folder and reflect current authenticated observations.

The version-37 entity indices for goat weaning and cattle heat records now match their migrations. The exported current Room schema is included. In addition to the existing full migration chain, the instrumentation suite exercises a populated version-29 local farm through the later migrations. A separate SQLite correspondence check executed the 22 constant statements from migrations 29–37 and matched the 11 new tables against the generated schema; this SQL check is distinct from running Room on Android.

See [Google Drive setup and acceptance](../architecture/GOAT_GOOGLE_DRIVE_SETUP.md) for the actual package/signing-certificate and Google Cloud requirements.

### App integration and receipts

Growing module hosts were split into lifecycle, state, content and record views. Existing screens, typed routes, commands, callbacks, shared visual components and reference lineage are retained. The original architecture ceilings were not increased. RoomOpsRepository remains below 1,400 lines, and the goat compatibility entry remains below 120 lines.

Retained callbacks and ports are observable Compose state. Replacing a host callback no longer leaves the rendered action holding an older callback. Submission helpers reserve the busy state synchronously, preventing two rapid requests from creating separate writes before the first coroutine starts.

A committed write is now acknowledged and scheduled for sharing before a read refresh. Refresh or scheduling failure produces a clear saved-on-this-device warning; it does not turn an accepted save into a rejected-command message or suppress the normal sharing request. Cancellation releases the busy state.

Task attachment routes now receive the database, and task/inventory actions sit inside their actual scrolling content. Vaccination target switching records either an individual or a group. The unsupported assumption of another vaccination due 365 days after the last record was removed; unknown next-due dates remain unknown.

Reports use complete farm reads, exact decimal arithmetic, actual inventory/production units and separate currencies with their own minor-digit precision. Recent financial rows also format stored minor units correctly. Water quality decimals are parsed into the intended milli-units.

Genetics resolves exact tags and all relevant active candidates without a 50-row cutoff and uses the stored sex values. Analytics no longer presents undated totals as a forecast rate. Simulation uses entered feed assumptions, dated financial baselines, actual calendar horizons, explicit female/capacity assumptions and finite parameters; saving requires a completed scenario. AI provider input is masked and advisory navigation text matches what the actions do.

The Android compile SDK was restored to 37 to match the declared Compose dependencies; targetSdk remains 36. Missing imports, Compose annotations, callback signatures, nullable accesses and BLE interface integration errors were corrected.

## Validation and evidence discipline

The canonical merge evidence is the completed [Foundation workflow](https://github.com/Vanguduza/goat-app/actions/workflows/foundation-ci.yml) and the other checks attached to PR #109's final candidate. Use the exact tested head/run, not an earlier passing branch or a same-tree assumption.

During review, all 187 domain/network/sync tests passed. A compiler checkpoint passed app Kotlin plus both Android instrumentation source sets. Final candidate app tests, APK assembly, native-reference evidence and Android instrumentation are separate checks; test source or successful compilation alone does not establish their execution.

New or extended regression coverage includes:

| Test area | Main evidence sources |
|---|---|
| Stable identity, retries, duplicate identifiers, farm/role/device denial and visible conflicts | GoatIdentityCommandsTest |
| Original budget/unit/group bases, legacy preferences, terminal flocks, explicit correction, authorization and complete farm totals | OpsConcurrentMutationTest; OpsReplicationApplierTest |
| Encrypted Drive objects, path/tamper/range checks, retries, missing destinations and consent/setup failure | DriveTransportSecurityTest; DriveSetupAttemptTest |
| Correct quantities, prices, currencies and vaccination target/due-date behavior | OperationsReportMathTest; HealthVaccinationRecordsTest; VaccinationTargetNavigationTest |
| Duplicate submits, rejected commands, cancellation, committed-save refresh failure and callback replacement | ModuleWriteStateTest; ModuleCallbackReplacementTest |
| Populated upgrade and full migration chain | FarmOsDatabaseMigrationTest |
| Stable source fingerprints before/after KSP output and sensitivity to new source/tests | scripts/development/test_source_evidence.py |

The source-evidence scripts now share a Git-aware source inventory. Tracked and new non-ignored Kotlin production/test files bind the fingerprint; ignored Gradle/KSP output does not. This fixes the prior situation in which building on a development machine made its evidence disagree with clean CI. YAML feature registries are parsed as YAML, and the navigation audit asserts the exact 25 module identities.

The guarded refresh helper regenerates source inventories as CI_PENDING with no run ID and zero executed/green claims. It preserves historical passing evidence and refuses to promote product status. All545 screen IDs and 156 mandatory features remain. Unapproved route-kind exemptions were removed; no missing route is silently declared headless. Locked reference hashes, token parity and independent visual-approval requirements remain unchanged.

To reproduce static validation:

    python3 scripts/development/refresh_source_evidence.py --check
    bash scripts/ci/verify-kotlin-architecture.sh
    bash scripts/ci/verify-visual-authority.sh
    node scripts/design/verify-handover.cjs
    node docs/ux/animal-farm-visual-lock/scripts/verify-pack.cjs --self-test --registry docs/ux/FARM_OS_SCREEN_REGISTRY.yaml

The Foundation workflow remains responsible for the complete declared Gradle unit/build tasks, native reference artifact verification and emulator migration/restart/replication tests. No failing gate was bypassed and no reference or golden was independently approved by its implementing agent.

## Remaining qualification and boundaries

- **Live Drive acceptance:** Configure the Google Drive API, consent and Android OAuth client for the installed package/signing certificate. Real consent/account/folder acceptance and physical multi-device use are not proven by the in-memory carrier tests.
- **Background delivery:** The Drive gateway currently operates while the local farm session is open. Delivery after the app is closed is a separate implementation/acceptance item.
- **Origin authentication:** Farm-key AEAD prevents a Drive-only writer without farm keys from inventing accepted payloads. It is not per-origin operation signing. A formerly trusted device retaining an old farm key and Drive write access is not proven unable to forge old-key history claiming another origin. Do not treat basic revoked-device tests as proof of that stronger property.
- **Legacy command authorization:** The strengthened transaction-level checks cover the goat identity command and the 23 extracted paths. Untouched older RoomOpsRepository handlers still retain their prior farm-only command boundary and need a separate complete authorization migration.
- **Product/visual completeness:** Green integration CI does not establish all 545 screen contracts or156 feature contracts. FEATURE_GREEN, MODULE_GREEN, MVP_GREEN and visual certification are not promoted here. The existing release block remains in force.

After the tested integration is merged, PR #108 can be closed as superseded by its corrected content in #109. Temporary branches may be removed only after their work is verified merged or explicitly superseded, following PROJECT_TRUTH_PROTOCOL.md.
