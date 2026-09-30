# GOAT offline multi-device synchronisation — canonical specification

**Authority:** owner architecture lock of 30 September 2026 (`docs/00_PROJECT_TRUTH.md` §0).
**Executable contract:** `domain/replication` (pure Kotlin, in canonical CI as `:domain:replication:test`).
**Status legend:** `IMPLEMENTED` = executable types and passing tests in this repository; `PLANNED` = specified here, not yet implemented. Nothing below is claimed done unless marked `IMPLEMENTED`.

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

Each device issues sequences 1, 2, 3 … with no gaps and never reuses one. Ordering never depends on the wall clock; a clock moving backwards does not affect sequencing (`deviceSequencesAreMonotonicAndIndependentOfWallClock`). A second operation claiming an already-used position with different content is refused (`aReusedPositionWithDifferentContentIsRefused`). **IMPLEMENTED.**

## 3. Operation envelope

`OperationEnvelope` fields: `operationId`, `farmId`, `entityType`, `entityId`, `actorId`, `deviceId`, `deviceSequence`, `businessTimeEpochMillis`, `createdAtEpochMillis`, `baseVersion`, `operationType`, `mergeClass`, `payload` (flat field map), `protocolVersion`, `schemaVersion`, `provenance`, `checksum`.

`checksum` = SHA-256 over a length-prefixed canonical encoding of every other field (payload keys sorted), so any alteration is detected. Business time is the farm event time and is never replaced by upload or receipt time. **IMPLEMENTED.**

Production requirement (**PLANNED**): the Room domain write and its operation row commit in one transaction.

## 4. Sync vectors

A `SyncVector` maps device → highest *contiguous* sequence incorporated. A gap holds the watermark back until the missing operation arrives. Two parties exchange vectors first; `missingFrom` yields exactly the ranges to transfer, so no full-database comparison is needed. **IMPLEMENTED** (`vectorsExchangeTransfersOnlyTheMissingOperations`).

## 5. Bundles and batching

An `OperationBundle` is a contiguous, single-device, single-farm range of operations with its own SHA-256. Receivers verify farm, device, protocol version, contiguity, every operation checksum and the bundle checksum before applying anything. Application is atomic: one bad operation rejects the whole bundle and changes nothing. Published bundles are immutable. **IMPLEMENTED** (`malformedOrCorruptedBundlesAreRejectedWithoutDamagingLocalData`).

## 6. Idempotent replay and late arrival

The same operation delivered twice (or by LAN and Drive) applies once. An operation recorded offline at 08:00 and uploaded at 23:00 remains an 08:00 event; history is ordered by business time, device, sequence — never by sync order. An earlier shift that reaches Drive after a later shift is still fully incorporated. **IMPLEMENTED** (`theSameOperationDeliveredTwiceAppliesOnce`, `anEarlierShiftSyncingLateIsStillIncorporatedAtItsOriginalBusinessTime`, `peerAndDriveDeliveringTheSameOperationDoNotDuplicateIt`).

## 7. Conflict classes

| Merge class | Semantics | Status |
|---|---|---|
| `APPEND_ONLY_EVENT` | Facts coexist; never conflict. | IMPLEMENTED |
| `FIELD_UPDATE` | Disjoint fields merge. Same field, same base version, different devices, different values → conflict. | IMPLEMENTED |
| `POSTING` | Money/stock postings all retained; reconciled explicitly, never overwritten. | IMPLEMENTED (retention); PLANNED (domain reconciliation views) |
| `IRREVERSIBLE_STATUS` | Concurrent status changes from the same base conflict and require review. | IMPLEMENTED (detection) |

Field values resolve deterministically by business time, then device, then sequence, so every replica converges whatever the arrival order. Conflicts are surfaced (never silently discarded); both operations stay in the journal. Resolution records a new corrective operation ordered after both conflicting ones; it replicates and closes the conflict on every device. **IMPLEMENTED** (`independentFieldChangesFromTheSameBaseMerge`, `theSameFieldChangedConcurrentlyEntersTheConflictCentreAndResolvesByANewOperation`).

Conflict Centre UI (local state, incoming state, device, actor, business time, operation identity, base version, fields; retain / accept / corrective): **PLANNED.**

## 8. Checkpoints and bootstrap

A `Checkpoint` records the vector it represents, current field values with their writers, all facts/postings/irreversible operations and open conflicts, sealed by SHA-256. A new device restores it, then replays only operations above its watermarks; operations at or below them are recognised as already incorporated. Checkpoint + delta converges to the same state as full replay, including late operations whose business time precedes the checkpoint. A tampered checkpoint is refused. Compaction never erases history: superseded field updates stay in the immutable published bundles. **IMPLEMENTED** (`checkpointRestorePlusDeltaReplayConvergesWithFullReplay`).

## 9. Transports

One protocol, interchangeable transports (`ReplicationTransport`): vectors, fetch ranges, publish bundles. `SyncSession` runs any transport identically.

| Transport | Status |
|---|---|
| `LocalPeerTransport` (farm LAN, works with no Internet) | IMPLEMENTED as executable in-process contract; PLANNED: socket carrier over authenticated TLS |
| `GoogleDriveTransport` (Drive Gateway) | IMPLEMENTED against a write-once store; PLANNED: Google Drive REST carrier |

Drive layout (deterministic, farm-isolated, immutable): `GOAT/farms/<farm-id>/sync/<device-id>/<from>-<to>.bundle` (12-digit zero-padded). **IMPLEMENTED** (`DriveJournalLayout`). Further folders — `descriptor/`, `devices/`, `manifests/`, `checkpoints/`, `attachments/`, `backups/`, `exports/`, `audit/` — are **PLANNED**.

Drive Gateway: only Owner/Management-approved devices hold Drive authorisation; worker devices sync with gateways over LAN. A Drive folder ID never grants farm authority, and Drive identity is separate from GOAT accounts. **PLANNED.**

## 10. Discovery, pairing, authentication

- Discovery: Android NSD/mDNS service `_goatfarm._tcp`. The descriptor exposes only non-secret data (farm ID and display name, protocol version, sync generation, Drive folder identity, peer endpoints, key identifier, farm public pairing identity). Never OAuth tokens, passwords, private keys or management credentials. **PLANNED.**
- Pairing: discovery says "a GOAT farm exists here"; pairing says "this device is authorised". A new device requests enrolment; a Management/Owner account approves it; approval provisions farm identity, sync configuration, permissions, encryption material, peers and the Drive folder identity. QR/pairing-code fallback. **PLANNED.**
- Session authorisation: a revoked, lost or retired device cannot take part in a session, and operations it originates above its revocation watermark are refused. **IMPLEMENTED** (`aRevokedDeviceCannotContinueSynchronising`).

## 11. Encryption

Farm payload encryption for Drive bundles, backups and sensitive attachments. Farm data keys are identified by key ID, provisioned to devices at pairing, and rotated when a device is revoked so it cannot read later batches. Recovery works through owner recovery material. Keys are stored with the Android Keystore and never broadcast over discovery. **PLANNED.**

## 12. Devices: retired and lost

`DeviceRegistry` statuses: `ACTIVE`, `TEMPORARILY_OFFLINE`, `RETIRED`, `LOST_REVOKED`. Before normal retirement the registry warns when the device reported sequences the farm has not incorporated. A lost device makes farm completeness `UNKNOWN_LOST_DEVICE_DATA`; completeness is never claimed when unsynchronised work may have been lost. An offline device never blocks other devices or new checkpoints. **IMPLEMENTED** (`lostDeviceStatusIsReportedTruthfully`).

## 13. Attachments

Attachment identity is the SHA-256 of the content, not the file name. Metadata replicates as an append-only operation before the bytes. A device that knows an attachment but lacks its bytes shows `AVAILABLE_WHEN_CONNECTED`. Received bytes are stored only when they match the declared hash and size. **IMPLEMENTED** (`attachmentMetadataReplicatesBeforeItsBytes`). Local file storage, peer/Drive byte transfer and Android capture: **PLANNED.**

## 14. Truthful replication states

`ReplicationState`: `SAVED_LOCALLY`, `LAN_SYNC_PENDING`, `SYNCED_WITH_PEER`, `DRIVE_SYNC_PENDING`, `SYNCED_TO_DRIVE`, `DRIVE_UNAVAILABLE`, `PEER_UNAVAILABLE`, `CONFLICT`, `UNKNOWN`. A place never observed is `UNKNOWN`, never "synced". Saved locally never implies synchronised or backed up. **IMPLEMENTED** (`replicationStatesNeverClaimSyncOrBackupThatDidNotHappen`). Backup states and UI wiring: **PLANNED.**

## 15. Schema migration

Every operation carries `protocolVersion` and `schemaVersion`. Receivers refuse bundles from a newer protocol version (**IMPLEMENTED**). Upcasting older operation schemas into current domain appliers is **PLANNED**; an unknown schema version must be held, never dropped.

## 16. Failure states

| Failure | Behaviour |
|---|---|
| No Internet | Local work continues; Drive session reports `TRANSPORT_UNAVAILABLE`; LAN still syncs. |
| No peers | Local work continues; states show `LAN_SYNC_PENDING` / `PEER_UNAVAILABLE`. |
| Corrupted bundle or checkpoint | Rejected atomically with a reason; local data untouched. |
| Foreign-farm data | Rejected; nothing applied. |
| Revoked/lost device | Session refused; later operations refused; completeness reported as unknown. |
| Published bundle rewrite | Refused by the write-once store. |

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
| 14 | Same-field conflict enters Conflict Centre | `theSameFieldChangedConcurrentlyEntersTheConflictCentreAndResolvesByANewOperation` | IMPLEMENTED (protocol); UI PLANNED |
| 15 | Attachment metadata before bytes | `attachmentMetadataReplicatesBeforeItsBytes` | IMPLEMENTED |
| 16 | Farm A can never enter farm B | `operationsFromOneFarmCanNeverEnterAnother` | IMPLEMENTED |
| 17 | Revoked device cannot sync | `aRevokedDeviceCannotContinueSynchronising` | IMPLEMENTED |
| 18 | Lost device represented truthfully | `lostDeviceStatusIsReportedTruthfully` | IMPLEMENTED |
| 19 | Checkpoint + delta converges | `checkpointRestorePlusDeltaReplayConvergesWithFullReplay` | IMPLEMENTED |
| 20 | Corrupted bundles rejected safely | `malformedOrCorruptedBundlesAreRejectedWithoutDamagingLocalData` | IMPLEMENTED |

These prove the protocol core. They do not prove the Android integration (Room journal, NSD, sockets, Drive REST, Keystore), which remains PLANNED and will carry its own device and integration evidence.

## 18. Migration from the server-era implementation

The repository still contains the Supabase outbox/RPC/pull-cursor synchronisation (`core/sync`, `supabase/`) and Meilisearch projection. Under the 30 September 2026 lock these are superseded as authorities. They are migrated in audited tranches: reusable concepts (mutation IDs, durable outbox, idempotency, expected stream versions, farm scoping) move into this protocol; server-only authority is retired. Until each tranche lands, the old code is classified in the owner decision register and must not be extended as an authority.
