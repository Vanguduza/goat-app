# Recovery and access authority qualification work

This bounded follow-up starts from main `349d40339923e6860f7ee7e74e5f149afab11f47`.
The canonical scope remains 156 features, 29 modules and 545 screens.
Source changes and test scenarios do not change any acceptance flag. Executor results must bind the
actual candidate; this record does not certify live Google, owner approval, physical-device behaviour,
a physical reboot or sudden power-loss survival.

## Corrected boundaries

Local access mutations now require a known ACTIVE local device of the same farm with no revocation
cutoff, inside the existing Room transaction. Acting account authority is still checked by the local
access service. Management identity receipts retain the acting manager rather than attributing the
change to the subject account; recovery changes retain the recovering Owner.

Access appliers require the exact recorded receipt and bind payload farm, account/event identity,
business time and audit actor to the envelope. Account identity changes accept only the canonical
mutable identity fields. Invalid pending payloads cannot poison a valid account fold. Global account
and audit primary-key collisions cannot overwrite another farm or silently discard its access history.
This is replay of an admitted original operation, without borrowing the receiver's current account role.

Later account/recovery folds include only APPLIED received history and locally committed receipts.
Local journal rows normally have no application row because their projection and receipt commit in the
same Room transaction; this exception requires a known isLocal origin. The scheduler adds only the
exact admitted receipt currently being applied, never another pending or SET_ASIDE operation.
A set-aside receipt cannot be applied directly. Each scheduler transaction rechecks application state,
and a failed attempt cannot overwrite an APPLIED or SET_ASIDE decision that committed while it rolled back.
This exclusion does not reverse a projection that another device already applied: the existing conflict
contract still requires an explicit correction for such an effect, rather than claiming universal reversal.

Recovery hashes use the complete canonical operation order, including device/sequence tie breakers.
Opposite arrival order of simultaneous offline recoveries converges consistently with the Owner
credential. A local change that cannot win its known identity/recovery history because of the device
clock fails within the transaction, rather than returning an unusable PIN or recovery code.
A newer retained legacy recovery projection is not silently downgraded by an older receipt.

First farm keys are created explicitly at farm setup, after the Owner and access journal writes and
before Room commits. Setup requires configured key storage. Key-store failure rolls back the farm,
accounts, recovery hash, audit and journal. A failure after the sealed-file replacement but before Room
commit can retain an unreferenced vault for a new UUID; it must not replace an existing farm's keys.

Every LAN key/identity lookup is strict. A missing, corrupt or inaccessible vault cannot cause carrier
startup, discovery or identity lookup to provision replacement keys. Local PIN recovery remains
separate from farm-key recovery and does not create missing encryption material.

Vault persistence syncs the sealed temporary file before atomic same-directory replacement and syncs
the directory metadata afterward, including parent metadata when creating a vault directory. A retry
also syncs the existing directory's parent, because a prior mkdir may have survived a failed metadata
barrier. Errors propagate. Before replacement, prior bytes remain intact. A final directory-sync failure may leave the
complete replacement visible; pending rotation reconciliation preserves retained keys and prevents
an uncommitted rotation from becoming active. Temporary-file cleanup never deletes the valid vault.

## Regression and device scenarios

- LocalAccessAuthorityTest: denied device/current-account authority, global ID isolation, exact SQLite
  fault injection and complete Room rollback.
- LocalAccessRecoveryConvergenceTest: simultaneous offline recovery, equal-time device/sequence
  ordering, backward clocks and a later credential receipt that must not produce a false recovery success.
- LocalAccessReplaySecurityTest: exact admitted receipts, malformed identity/tenant claims, historical
  actor/time preservation, global primary-key collisions, retained legacy recovery state, exclusion of
  set-aside/pending/unadmitted history, and a queued management decision during a real Room rollback.
- FarmKeyRecoveryBoundaryTest: explicit setup, missing/corrupt vault refusal, no replacement identity,
  failed sealing rollback, and separation of PIN recovery from encryption-key recovery.
- FarmVaultDurabilityTest: file-sync and directory-sync failures, old-key preservation, setup rollback,
  reconciliation of an uncommitted staged rotation, and retry of a failed mkdir parent-metadata barrier.
- LocalAccessRecoveryDurabilityTest: Android file-backed Room close/reopen, actual SQLite rollback,
  and loss of a test-owned Android Keystore alias without minting a replacement alias or vault.

JVM vault fixtures use real filesystem file/directory synchronization through an explicit test adapter.
Robolectric 4.17's ShadowLinux directory-open implementation uses RandomAccessFile, which cannot open
directories. Android instrumentation uses the production public Android POSIX API; it does not use
that JVM adapter. See the [Android Os API](https://developer.android.com/reference/android/system/Os).

The first local validation attempt passed all 10 domain access tests, then stopped at production
compilation because O_DIRECTORY is absent from Android's public SDK. No app unit or instrumentation
tests executed in that attempt. The repaired code uses public O_RDONLY, verifies the opened descriptor
with fstat/S_ISDIR, then syncs and closes it; those APIs were checked against the installed API 37.0
android.jar. The history/race/directory-retry regressions were added before the later attempts described
below. Earlier attempt evidence remains a failed compile checkpoint, not an app test pass.

The second local attempt reused 10 passing domain access test results from attempt 1 through
Gradle's build cache; the retained XML timestamp identifies that earlier execution, not a fresh run.
App production compilation passed. It stopped at Android instrumentation compilation: RoomDatabase is not a Closeable
receiver for the fixture's six use calls. The fixture now owns an explicit Closeable around each
database lifetime, retaining close-before-reopen and every recovery/rollback assertion. No app unit
or instrumentation tests executed in that attempt. These test-fixture repairs require a subsequent
run; neither failed attempt is a device durability pass.

The third local attempt disabled the build cache and cleaned the domain test task. All 10 domain
access cases executed and passed with current-run XML timestamps. Production, app unit and Android
instrumentation sources compiled. The app selection executed 106 cases: 95 passed and 11 failed.
All 27 new authority/replay/recovery/vault regression cases passed. The 11 failures were older UI fixture
calls to createAccount or signIn outside the required Room transaction, including sign-in assertions
after the rendered Owner recovery had completed. Those calls now use LocalFarmDirectory.transact;
the transaction guard and every test assertion remain unchanged. Production setup, sign-in, recovery
and Settings account mutations already use their transaction boundary. The retained third-attempt result remains a failed run. Compiled instrumentation is not evidence
that the Android durability scenarios executed.

The fourth local attempt targeted only LocalFarmEntryTest and SettingsHostTest, with the build cache
disabled and the app test task cleaned. It ran from 16:04:42 to 16:05:59 UTC on 9 October 2026 and
executed all 19 selected cases (3 entry, 16 settings), with zero failures, errors or skips and current-run
suite timestamps. The driver retained an unchanged source snapshot fingerprint
`5f87f197b7a0d8be7a4a137473445f16a780bc8e85debdbd137616b70ec471a2`.
Its retained `recovery-tests-attempt4-result.json` SHA-256 is
`f674e4dcd26e9f44424fbe5f1bd03d3b3d58b2b87b575edfb2343c9a960f024c`.
The evidence is 95 passing app cases in attempt 3 plus this targeted 19-case rerun after fixture repair;
some cases overlap. It is not a new full 106-case run, full CI certification or device durability proof.

## Acceptance work still requiring its own implementation or observed evidence

The [offline sync architecture](../architecture/GOAT_OFFLINE_MULTI_DEVICE_SYNC.md) and
[Google setup contract](../architecture/GOAT_GOOGLE_DRIVE_SETUP.md) still require a coherent
all-devices-lost Owner key escrow/restore path and its backup/checkpoint publication contract.
A PIN recovery code on an already provisioned device is not that escrow path.

Historical business operations still rely on accepted device membership, checksums and farm encryption;
this slice does not invent per-origin signatures for previously unsigned operations or retroactively
assert their cryptographic provenance.

The [Health](../FARM_OS_HEALTH_MODULE_SPEC.md) and [Vet Intelligence](../FARM_OS_VET_INTELLIGENCE_FEATURE_IMPLEMENTATION_SPEC.md)
contracts still require verified veterinary attestation/pack acceptance, restricted-class gating and
the guarded due-and-trained Worker vaccination path. Creating a draft formulary does not satisfy
those contracts.

Live Google authorization and provider delivery, physical-device/reboot behaviour and Owner visual
approval remain separate observed acceptance evidence. Emulator or source tests must not be relabelled
as those outcomes.
