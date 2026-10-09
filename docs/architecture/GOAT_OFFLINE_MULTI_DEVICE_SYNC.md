# GOAT offline multi-device synchronisation — canonical specification

**Authority:** owner architecture lock of 30 September 2026 (`docs/00_PROJECT_TRUTH.md` §0).
**Executable contract:** `domain/replication` (pure Kotlin, in canonical CI as `:domain:replication:test`).
**Status legend:** `IMPLEMENTED` = executable types and passing bounded tests in this repository; `PLANNED` = specified here, not yet implemented. These architecture implementation markers do not grant feature, MVP, release or visual qualification. New candidate changes remain pending validation until their exact CI record establishes a pass; code presence or production compilation alone supplies no such result. See [the qualification record](../reviews/2026-10-09-google-test-qualification.md) and [Project Truth](../00_PROJECT_TRUTH.md).

GOAT has no application server. Each farm device keeps its own Room/SQLite database and works fully offline. Devices exchange immutable operations over the farm LAN and, through approved Drive Gateways, via the owner's Google Drive. Drive is replication and backup, never the live database. Whole SQLite files are never synchronised.

## 1. Identities

| Identity | Definition | Status |
|---|---|---|
| Farm ID | Opaque, farm-scoped identifier. Every operation, bundle, checkpoint and Drive path carries it. Operations for another farm are rejected. | IMPLEMENTED (`OperationEnvelope.farmId`, `OperationBundle.verify`) |
| Device ID | Stable per-install identifier, registered in the farm's `DeviceRegistry`. | IMPLEMENTED (`FarmDevice`) |
| Operation ID | Globally unique (UUID in production). Identity of an operation; never reused. | IMPLEMENTED |
| Device position | `device_id + device_sequence`; the device's replication position. | IMPLEMENTED (`DevicePosition`) |
| Actor | GOAT local account (or worker record) that performed the action. | IMPLEMENTED (`actorId`) |

## 2. Device sequence numbers

Each device issues sequences 1, 2, 3 … with no gaps and never reuses one. Device sequence ordering never depends on the wall clock; a clock moving backwards does not affect sequencing (`deviceSequencesAreMonotonicAndIndependentOfWallClock`). A second operation claiming an already-used position with different content is refused (`aReusedPositionWithDifferentContentIsRefused`). **IMPLEMENTED.**

## 3. Operation envelope

`OperationEnvelope` fields: `operationId`, `farmId`, `entityType`, `entityId`, `actorId`, `deviceId`, `deviceSequence`, `businessTimeEpochMillis`, `createdAtEpochMillis`, `baseVersion`, `operationType`, `mergeClass`, `payload` (flat field map), `protocolVersion`, `schemaVersion`, `provenance`, `checksum`.

`checksum` = SHA-256 over a length-prefixed canonical encoding of every other field (payload keys sorted), so any alteration is detected. Business time is the farm event time and is never replaced by upload or receipt time. **IMPLEMENTED.**

Room journal (**IMPLEMENTED**, introduced in schema version 14): `replication_operations` stores each sealed operation (unique per farm, device and sequence); `replication_devices` holds the farm device registry, and its local row carries the last issued sequence so sequences are never reused across restarts. Capture handlers use `insertOutboxAndJournal` to write the domain change, outbox row and operation in one Room transaction; app management handlers journal through `journalLocalOperation` in their transaction. The operation ID is the mutation ID and the merge class comes from `CommandMergeClassification`. `ReplicationJournalOwnerTest` covers sequencing, atomic rejection, per-farm sequences and ingestion of the Room journal without changing the protocol envelope.

Known migration gap: outbox rows written before schema version 14 have no journal entries. Retained legacy rows require audited migration or reconciliation; their presence does not restore the server-era outbox as an authority.

## 4. Sync vectors

A `SyncVector` maps device → highest *contiguous* sequence incorporated. A gap holds the watermark back until the missing operation arrives. Two parties exchange vectors first; `missingFrom` yields exactly the ranges to transfer, so no full-database comparison is needed. **IMPLEMENTED** (`vectorsExchangeTransfersOnlyTheMissingOperations`).

## 5. Bundles and batching

An `OperationBundle` is a contiguous, single-device, single-farm range of operations with its own SHA-256. Receivers verify farm, device, protocol version, contiguity, every operation checksum and the bundle checksum before applying anything. Protocol admission is atomic: a malformed bundle is rejected without changing the replica journal. Room domain application is tracked separately per command (§9), so an accepted immutable receipt may still wait on a business dependency or conflict. Published bundles are immutable. **IMPLEMENTED** (`malformedOrCorruptedBundlesAreRejectedWithoutDamagingLocalData`).

## 6. Idempotent replay and late arrival

The same operation delivered twice (or by LAN and Drive) applies once. An operation recorded offline at 08:00 and uploaded at 23:00 remains an 08:00 event; history is ordered by business time, device, sequence — never by sync order. An earlier shift that reaches Drive after a later shift is still fully incorporated. **IMPLEMENTED** (`theSameOperationDeliveredTwiceAppliesOnce`, `anEarlierShiftSyncingLateIsStillIncorporatedAtItsOriginalBusinessTime`, `peerAndDriveDeliveringTheSameOperationDoNotDuplicateIt`).

## 7. Conflict classes

| Merge class | Semantics | Status |
|---|---|---|
| `APPEND_ONLY_EVENT` | Facts coexist; never conflict. | IMPLEMENTED |
| `FIELD_UPDATE` | Disjoint fields merge. Same field, same base version, different devices, different values → conflict. | IMPLEMENTED |
| `POSTING` | Money/stock postings all retained; reconciled explicitly, never overwritten. | IMPLEMENTED (retention); PLANNED (domain reconciliation views) |
| `IRREVERSIBLE_STATUS` | Concurrent status changes from the same base conflict and require review. | IMPLEMENTED (detection) |

In the pure field-update model, values resolve deterministically by business time, then device, then sequence, and all replicas converge for the same operation set. Conflicts are surfaced; both operations stay in the journal. A new corrective operation ordered after both conflicting ones closes that model's conflict on each device. **IMPLEMENTED** (`independentFieldChangesFromTheSameBaseMerge`, `theSameFieldChangedConcurrentlyEntersTheConflictCentreAndResolvesByANewOperation`). Room command projections also enforce their original version and domain preconditions. A stale concurrent configuration command can remain unapplied with an explicit conflict reason; retaining the same journal does not automatically make those materialised values agree.

Conflict Centre and Conflict Detail are **IMPLEMENTED** for received operations waiting on application: they show actor/device/business time, command content and failure reason, offer retry, and let authorised management record a reasoned set-aside decision without deleting history. The decision replicates. For an animal exit, the accompanying reversal supplies the domain correction; setting aside a different command does not itself rewrite an already accepted value. The full generic side-by-side field merge and retain/accept/corrective UI remains **PLANNED**. See [ConflictCentreScreens](../../app/src/main/java/com/farmos/app/ConflictCentreScreens.kt), [ConflictResolution](../../app/src/main/java/com/farmos/app/ConflictResolution.kt), `ConflictResolutionTest`, `ConflictCentreScreensTest` and `OpsConcurrentMutationTest`.

## 8. Checkpoints and bootstrap

A `Checkpoint` records the vector it represents, current field values with their writers, all facts/postings/irreversible operations and open conflicts, sealed by SHA-256. A new device restores it, then replays only operations above its watermarks; operations at or below them are recognised as already incorporated. Checkpoint + delta converges to the same state as full replay, including late operations whose business time precedes the checkpoint. A tampered checkpoint is refused. Compaction never erases history: superseded field updates stay in the immutable published bundles. **IMPLEMENTED** (`checkpointRestorePlusDeltaReplayConvergesWithFullReplay`).

## 9. Transports

One protocol, interchangeable transports (`ReplicationTransport`): vectors, fetch ranges, publish bundles. `SyncSession` runs the common exchange contract; Android adapters add durable storage, authority and carrier checks.

| Transport | Status |
|---|---|
| `LocalPeerTransport` | IMPLEMENTED as the executable in-process farm LAN contract. |
| `LanPeerTransport` + `LanSyncServer` | IMPLEMENTED on the JVM with authenticated handshakes, registry checks, per-direction AES-256-GCM session keys and counter nonces. Device identity proofs allow an authorised device holding an older farm key to obtain rotations; unpaired, revoked, foreign-farm and impostor peers and altered/replayed frames are refused. `LanTransportTest` uses loopback sockets. Android `FarmLanRuntime` serves the Room journal, announces and discovers `_goatfarm._tcp` through NSD, and synchronises while a local farm session is open. Background LAN synchronisation with the app closed and a Wi-Fi lock remain PLANNED. |
| `RoomReplicaEndpoint` | IMPLEMENTED: bundle verification, device/revocation checks and journal admission share one Room transaction. Received operations apply through command-specific `OperationApplier`s in business order, each in a transaction. `replication_applications` (introduced in schema v17) keeps dependency/conflict failures as `FAILED` and unknown handlers as `AWAITING_APPLIER`; receipts remain available for retry/review without blocking unrelated journal transfer. An ingested receipt is not a claim that its domain change applied. |
| `GoogleDriveTransport` / Android `DriveReplicationTransport` | IMPLEMENTED: the write-once protocol contract and Android Drive REST carrier, checked Google consent/destination setup, farm-payload encryption, signed key-prerequisite bootstrap, current local gateway approval and WorkManager delivery. Carrier and Room tests cover the executable boundaries; live Google and physical-device acceptance remain required. |

Android appliers cover the farm/access/device settings, farm-operations command families, goat commands and the sheep/cattle/rabbit/poultry capture families. Supported legacy receipts replay through their governed handlers without issuing another operation. Exact receipt and typed payload matching distinguish a historical replay from a new local write. `OpsReplicationApplierTest` checks coverage against writer sources; `RoomReplicaEndpointTest` checks actual Room exchange. An irreversible change that cannot apply retains its failed receipt and reason for review.

Drive object paths are deterministic, farm-isolated and immutable:

| Purpose | Path | Status |
|---|---|---|
| Ordinary journal bundle | `GOAT/farms/<farm-id>/sync/<device-id>/<from>-<to>.bundle` with 12-digit sequence numbers | IMPLEMENTED (`DriveJournalLayout`, `DriveReplicationTransport`) |
| Content-addressed attachment bytes | `GOAT/farms/<farm-id>/attachments/<sha256>` | IMPLEMENTED (`FarmDriveRuntime`) |
| Signed enrolment or rotation prerequisite | `GOAT/farms/<farm-id>/key-bootstrap/<gateway-device-id>/<wrapping-key-id>/<exact-bundle-sha256>.rotation` | IMPLEMENTED (`DriveRotationBootstrapCodec`) |

These are logical object names within the validated Drive folder. The carrier refuses paths outside its allowlist and refuses SQLite payloads. It detects duplicate immutable names instead of choosing an arbitrary object. Separate `descriptor/`, `devices/`, `manifests/`, `checkpoints/`, `backups/`, `exports/` and `audit/` publication layouts remain **PLANNED**; the implemented key bootstrap is not a whole-farm checkpoint or all-device-loss restore.

Drive gateway approval is **IMPLEMENTED** and device-local. An active Owner/Manager with storage/backup permission approves the selected Google account and writable destination on the active local farm device. Every pass and carrier operation rechecks that binding and the absence of a revocation cutoff. The REST uploader repeats the check after metadata lookup, immediately before POST. Google identity and a folder ID grant no farm authority; worker devices can exchange with approved gateways over authenticated LAN.

Drive background delivery is **IMPLEMENTED**: unique network-constrained periodic and immediate WorkManager requests share the foreground farm coordinator. Scheduling awaits both enqueue `Operation` futures and reports asynchronous failures. Workers never launch Google consent or create farm keys. Cancellation after `RUNNING` persists `RETRY_WAIT` for that pass's current connection and clears the live syncing state before propagating cancellation; restart loading also converts interrupted `RUNNING` state to retry. See [Drive setup and acceptance](GOAT_GOOGLE_DRIVE_SETUP.md), [FarmLan](../../app/src/main/java/com/farmos/app/FarmLan.kt), [FarmDriveRuntime](../../app/src/main/java/com/farmos/app/FarmDriveRuntime.kt) and [DriveBackgroundWork](../../app/src/main/java/com/farmos/app/DriveBackgroundWork.kt).

## 10. Discovery, pairing, authentication

- Discovery: Android NSD/mDNS service `_goatfarm._tcp`. The descriptor exposes only non-secret data (farm ID and display name, protocol version, sync generation, Drive folder identity, peer endpoints, key identifier, farm public pairing identity). Never OAuth tokens, passwords, private keys or management credentials. The TXT record is a closed key set (`FarmDiscoveryDescriptor`); a record carrying any other key is refused as non-conforming. **Descriptor and Android NSD registration/discovery IMPLEMENTED (`discoveryAdvertisesOnlyTheClosedSetOfNonSecretFields`, `NsdFarmPeerDiscovery` in `FarmLan.kt`); physical network acceptance remains a separate gate.**
- Pairing: discovery says "a GOAT farm exists here"; pairing says "this device is authorised". A new device requests enrolment; a Management/Owner account approves it; approval provisions farm identity, sync configuration, permissions, encryption material, peers and the Drive folder identity. QR/pairing-code fallback. `PairingAuthority` approves either when an account allowed to approve devices confirms the six-digit code shown on the new device (the code binds the farm pairing fingerprint, the device public key and a nonce, so a device in the middle yields a different code), or through a single-use, 15-minute QR invitation issued by such an account. Requests expire after 10 minutes, are single-use (a wrong code burns the request), and revoked or retired device ids cannot re-enrol. Approval wraps every farm key to the device's own P-256 public key. The pairing exchange runs over the LAN with `PairingServer`/`PairingClient`: the channel carries no secret (the request holds the new device's public key and nonce; the grant's farm keys are wrapped to that key), and the server asks a person on the farm device, who types the code shown on the new device; a scanned invitation is decided without asking. **Rules and wire protocol IMPLEMENTED (`PairingAndKeysTest`, `PairingWireTest`); Android screens IMPLEMENTED: Devices (FOS-ADMIN-021) opens Add a device and takes the code; Join a farm on this network (FOS-GLOBAL-007) finds the farm, shows the code, installs the grant and copies the farm before sign-in (`FarmNetworkPairingUiTest`). QR invitation screens PLANNED.**
- Session authorisation: a revoked, lost or retired device cannot take part in a session, and operations it originates above its revocation watermark are refused. **IMPLEMENTED** (`aRevokedDeviceCannotContinueSynchronising`).

## 11. Encryption and key recovery

Ordinary Drive journal bundles and attachment bytes are sealed with AES-256-GCM under the current farm key, with the farm and exact object path authenticated as associated data. Historical keys remain in the Android Keystore-sealed vault so earlier ciphertext can be opened. Pairing wraps farm keys to each device identity using ephemeral ECDH P-256, HKDF-SHA256 and AES-256-GCM, bound to farm, device and key ID. No private key or secret is broadcast over discovery. **IMPLEMENTED** (`FarmKeys.kt`, `PairingAndKeysTest`, `FarmKeyVaultTest`, `DriveTransportSecurityTest`).

Local rotation uses a recoverable three-step boundary: stage the new key **inactive** with sealed pending metadata; commit its exact `farm.key_rotated.v1` Room receipt; then reconcile the committed operation and activate the key. The rotation includes a wrapped copy for the reporting device and eligible active/temporarily offline recipients with a public identity key and no revocation cutoff. The old current key remains current until the commit is proven. Reconciliation never activates an uncommitted candidate; a mismatched or unresolved receipt blocks delivery as `KEY_ROTATION_PENDING`. Delivery code can reconcile an existing staged rotation but never provisions missing keys. **IMPLEMENTED** ([FarmKeyRotation](../../app/src/main/java/com/farmos/app/FarmKeyRotation.kt), `FarmKeyRotationRecoveryTest`).

The original local rotation business time must be strictly greater than the current rotation time already observed on the device. A clock that has not advanced is refused before staging; the operator must correct it and retry. Received rotations retain their event time and resolve the current key by business time, then key ID for a tie. This business-clock guard is separate from gap-free device sequence ordering (§2). **IMPLEMENTED** (`FarmKeyRotationOrderingTest`, `keyRotationWins`).

A remaining authorised device can obtain missed rotations through device-authenticated LAN or signed Drive gateway bootstrap. LAN identity proofs establish the peer independently of its current shared-key version. Drive publishes only exact accepted singleton `farm.key_rotated.v1` and device-enrolment prerequisites under applicable retained farm keys, with each new key still wrapped to eligible recipients. The gateway signs the farm, signer identity, immutable path and exact bundle bytes. The receiving Room transaction requires a known, currently permitted registered signer (`ACTIVE` or `TEMPORARILY_OFFLINE`, with no revocation cutoff), verifies the signature and strict farm/path/content binding, and admits dependencies before ordinary journal replay. An enrolment attested by an already known gateway can establish a later rotation signer. Shared-key ciphertext cannot manufacture that trust.

Normal business bundles stay current-key encrypted. Bootstrap copies never re-encrypt ordinary records under old keys, bridge missing journal sequences, or alone establish a complete backup. Exact already accepted prerequisites and matching installed identity bindings remain retry compatible. Previously unadmitted unsigned enrolment/rotation archives need an updated authorised gateway that holds accepted history, or authenticated LAN relay; the reader does not weaken admission for such archives. **IMPLEMENTED** ([DriveKeyBootstrap](../../app/src/main/java/com/farmos/app/DriveKeyBootstrap.kt), [DriveRotationBootstrapCodec](../../app/src/main/java/com/farmos/app/DriveRotationBootstrapCodec.kt), `DriveKeyRotationDeliveryTest`, `DriveKeyBootstrapSecurityTest`).

Owner escrow and recovery of farm keys after **all authorised devices and their recoverable keys are lost remain PLANNED**. A Google account or Drive folder does not replace that missing recovery material. Standalone encrypted backup/checkpoint publication and a qualified full restore require their own implementation and acceptance evidence; the existing journal gateway and retained-key catch-up do not supply those claims.

## 12. Devices: retired and lost

`DeviceRegistry` statuses: `ACTIVE`, `TEMPORARILY_OFFLINE`, `RETIRED`, `LOST_REVOKED`. Before normal retirement the registry warns when the device reported sequences the farm has not incorporated. A lost device makes farm completeness `UNKNOWN_LOST_DEVICE_DATA`; completeness is never claimed when unsynchronised work may have been lost. An offline device never blocks other devices or new checkpoints. **IMPLEMENTED** (`lostDeviceStatusIsReportedTruthfully`).

## 13. Attachments

Attachment identity is the SHA-256 of the content, not the file name. Metadata replicates as an append-only operation before the bytes. A device that knows an attachment but lacks its bytes shows `AVAILABLE_WHEN_CONNECTED`. Received bytes are stored only when they match the declared hash and size. **IMPLEMENTED** (`attachmentMetadataReplicatesBeforeItsBytes`). Local content-addressed file storage, authenticated LAN byte transfer, encrypted Drive byte transfer, and Android photo/file selection are **IMPLEMENTED** in [FarmAttachments](../../app/src/main/java/com/farmos/app/FarmAttachments.kt), `FarmLanRuntime` and `FarmDriveRuntime`; `FarmAttachmentsTest` and `AttachmentsScreensTest` cover the local integration. These paths move attachment bytes separately from metadata and never synchronise a SQLite file.

## 14. Truthful replication states

`ReplicationState`: `SAVED_LOCALLY`, `LAN_SYNC_PENDING`, `SYNCED_WITH_PEER`, `DRIVE_SYNC_PENDING`, `SYNCED_TO_DRIVE`, `DRIVE_UNAVAILABLE`, `PEER_UNAVAILABLE`, `CONFLICT`, `UNKNOWN`. A place never observed is `UNKNOWN`, never "synced". Saved locally never implies synchronised or backed up. **IMPLEMENTED** (`replicationStatesNeverClaimSyncOrBackupThatDidNotHappen`). Android journal counts and gateway status wiring are **IMPLEMENTED** in `SettingsHost`, `DriveCursorStore` and `DriveDeliveryStateStore`. Upload acknowledgement is distinct from authenticated reread of the remote contiguous journal; saved locally, uploaded, verified backed up and failed are separate counts. Durable gateway states include consent/approval requirements, missing keys, pending rotation and retry, and never imply a completed transfer merely because work was scheduled. A standalone backup archive/restore workflow and all-device-loss recovery remain **PLANNED**.

## 15. Schema migration

Every operation carries `protocolVersion` and `schemaVersion`. Receivers refuse bundles from a newer protocol version (**IMPLEMENTED**). Supported command-specific legacy versions have explicit replay handlers. A general schema upcasting framework remains **PLANNED**; unsupported operations must remain visible for a compatible handler or review, never be discarded.

## 16. Failure states

| Failure | Behaviour |
|---|---|
| No Internet | Local work continues; Drive session reports `TRANSPORT_UNAVAILABLE`; LAN still syncs. |
| No peers | Local work continues; states show `LAN_SYNC_PENDING` / `PEER_UNAVAILABLE`. |
| Corrupted bundle or checkpoint | Rejected atomically with a reason; local data untouched. |
| Foreign-farm data | Rejected; nothing applied. |
| Revoked/lost device | Session refused; later operations refused; completeness reported as unknown. |
| Published bundle rewrite | Refused by the write-once store. |
| Unknown, revoked or unauthenticated bootstrap signer; path/content mismatch | Fresh enrolment/rotation admission refused before a journal position is occupied. Required recovery dependencies remain visible. |
| Missing farm key or unresolved staged rotation | `KEYS_UNAVAILABLE` or `KEY_ROTATION_PENDING`; no automatic replacement key. |
| Lost local gateway approval or Google consent | `APPROVAL_REQUIRED` or `NEEDS_CONSENT`; explicit checked reconnect required. A cutoff discovered during metadata lookup prevents the upload POST. |
| Asynchronous WorkManager enqueue failure | Scheduling failure is persisted and reported; queued work is not reported as completed delivery. |
| Cancellation after `RUNNING` | Current pass returns to durable `RETRY_WAIT`, clears syncing and propagates cancellation; local records and immutable receipts remain. |

## 17. Required adversarial tests

| # | Requirement | Test in `AdversarialSyncTest` | Status |
|---|---|---|---|
| 1 | Same operation twice applies once | `theSameOperationDeliveredTwiceAppliesOnce` | IMPLEMENTED |
| 2–7 | Earlier offline shift reconnects after later shift reached Drive; original business time, actor and creation time preserved | `anEarlierShiftSyncingLateIsStillIncorporatedAtItsOriginalBusinessTime` | IMPLEMENTED |
| 8 | Only missing operations transferred via vectors | `vectorsExchangeTransfersOnlyTheMissingOperations` | IMPLEMENTED |
| 9–10 | LAN without Internet; Drive after Internet returns | `lanSyncWorksWithoutInternetAndDriveCatchesUpWhenItReturns` | IMPLEMENTED |
| 11 | Device missing for days catches up | `aDeviceAwayForSeveralDaysCatchesUpInBundles` | IMPLEMENTED |
| 12 | Peer and Drive delivery not duplicated | `peerAndDriveDeliveringTheSameOperationDoNotDuplicateIt` | IMPLEMENTED |
| 13 | Independent field changes merge | `independentFieldChangesFromTheSameBaseMerge` | IMPLEMENTED |
| 14 | Same-field conflict enters Conflict Centre | `theSameFieldChangedConcurrentlyEntersTheConflictCentreAndResolvesByANewOperation` | IMPLEMENTED (protocol); Android unapplied-command review implemented, generic field merge UI planned (§7) |
| 15 | Attachment metadata before bytes | `attachmentMetadataReplicatesBeforeItsBytes` | IMPLEMENTED |
| 16 | Farm A can never enter farm B | `operationsFromOneFarmCanNeverEnterAnother` | IMPLEMENTED |
| 17 | Revoked device cannot sync | `aRevokedDeviceCannotContinueSynchronising` | IMPLEMENTED |
| 18 | Lost device represented truthfully | `lostDeviceStatusIsReportedTruthfully` | IMPLEMENTED |
| 19 | Checkpoint + delta converges | `checkpointRestorePlusDeltaReplayConvergesWithFullReplay` | IMPLEMENTED |
| 20 | Corrupted bundles rejected safely | `malformedOrCorruptedBundlesAreRejectedWithoutDamagingLocalData` | IMPLEMENTED |

These contracts exercise the protocol core. Android Room, NSD, socket, Drive REST, Keystore, attachment and background-delivery implementations now exist and have separate regression sources; the protocol tests alone do not verify those integrations. The current candidate's production compilation does not stand in for its final unit/integration results. Record actual runs for the immutable candidate, and retain the required live Google, physical-device, recovery and independent visual proof. See [Drive verification and limits](GOAT_GOOGLE_DRIVE_SETUP.md#verification-and-limits) and [Project Truth](../00_PROJECT_TRUTH.md). No architecture status in this document grants product release qualification.

## 18. Migration from the server-era implementation

The repository still contains the Supabase outbox/RPC/pull-cursor synchronisation (`core/sync`, `supabase/`) and Meilisearch projection. Under the 30 September 2026 lock these are superseded as authorities. They are migrated in audited tranches: reusable concepts (mutation IDs, durable outbox, idempotency, expected stream versions, farm scoping) move into this protocol; server-only authority is retired. Until each tranche lands, the old code is classified in the owner decision register and must not be extended as an authority.
