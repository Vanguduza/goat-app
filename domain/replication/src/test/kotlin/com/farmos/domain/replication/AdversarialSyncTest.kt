package com.farmos.domain.replication

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Adversarial multi-device replication scenarios. Each test drives real replicas through real transports
 * (farm-LAN peer and immutable Drive journal) and checks convergence, idempotency, business time and
 * isolation rather than any single implementation detail.
 */
class AdversarialSyncTest {
    private val farm = "farm-premier"
    private var nextId = 0
    private var now = 1_000_000L

    private fun registry(vararg devices: String) = DeviceRegistry(devices.map { FarmDevice(it, "Device $it", DeviceStatus.ACTIVE) })

    private fun replica(device: String, registry: DeviceRegistry = registry("A", "B", "C"), farmId: String = farm) =
        FarmReplica(farmId, device, registry, { "op-${++nextId}" }, { now })

    private fun FarmReplica.weigh(animal: String, kg: String, at: Long, actor: String = "worker-$deviceId") = record(
        entityType = "animal",
        entityId = animal,
        actorId = actor,
        businessTimeEpochMillis = at,
        baseVersion = null,
        operationType = "weight.record",
        mergeClass = MergeClass.APPEND_ONLY_EVENT,
        payload = mapOf("kg" to kg),
    )

    private fun FarmReplica.edit(animal: String, base: Long, at: Long, vararg fields: Pair<String, String>) = record(
        entityType = "animal",
        entityId = animal,
        actorId = "manager-$deviceId",
        businessTimeEpochMillis = at,
        baseVersion = base,
        operationType = "animal.update",
        mergeClass = MergeClass.FIELD_UPDATE,
        payload = fields.toMap(),
    )

    private fun hours(h: Int) = h * 3_600_000L

    @Test
    fun theSameOperationDeliveredTwiceAppliesOnce() {
        val a = replica("A")
        val b = replica("B")
        a.weigh("goat-1", "31.5", hours(8))
        val bundle = a.bundlesFor(b.vector().missingFrom(a.vector())).single()

        assertEquals(IngestResult(applied = 1), b.ingest(bundle))
        assertEquals(IngestResult(duplicates = 1), b.ingest(bundle))
        assertEquals(1, b.facts(EntityKey("animal", "goat-1")).size)
    }

    @Test
    fun anEarlierShiftSyncingLateIsStillIncorporatedAtItsOriginalBusinessTime() {
        val drive = ImmutableBundleStore()
        val a = replica("A")
        val b = replica("B")
        // Shift A records offline in the morning.
        val morning = (1..4).map { a.weigh("goat-$it", "3$it.0", hours(8) + it) }
        // Shift B works in the afternoon and reaches Drive first.
        now = hours(15)
        (1..3).forEach { b.weigh("goat-$it", "4$it.0", hours(14) + it) }
        assertEquals(SyncSessionStatus.COMPLETED, SyncSession.run(b, GoogleDriveTransport(drive)).status)

        // Device A reconnects at 23:00.
        now = hours(23)
        val lateUpload = SyncSession.run(a, GoogleDriveTransport(drive))
        assertEquals(4, lateUpload.pushedOperations)
        assertEquals(3, lateUpload.pulledOperations)
        SyncSession.run(b, GoogleDriveTransport(drive))

        listOf(a, b).forEach { replica ->
            assertEquals(SyncVector(mapOf("A" to 4, "B" to 3)), replica.vector())
            val history = replica.history()
            assertEquals(morning.map { it.operationId }, history.take(4).map { it.operationId })
            history.filter { it.deviceId == "A" }.forEach { op ->
                val original = morning.single { it.operationId == op.operationId }
                assertEquals(original.businessTimeEpochMillis, op.businessTimeEpochMillis)
                assertEquals(original.createdAtEpochMillis, op.createdAtEpochMillis)
                assertEquals("worker-A", op.actorId)
                assertTrue(op.checksumValid())
            }
        }
        assertEquals(a.stateDigest(), b.stateDigest())
    }

    @Test
    fun vectorsExchangeTransfersOnlyTheMissingOperations() {
        val phone = replica("A")
        val tablet = replica("B")
        repeat(4) { phone.weigh("goat-$it", "20", hours(7) + it) }
        SyncSession.run(tablet, LocalPeerTransport(phone))
        repeat(5) { tablet.weigh("goat-$it", "21", hours(9) + it) }
        repeat(2) { phone.weigh("goat-$it", "22", hours(10) + it) }

        assertEquals(listOf(SequenceRange("B", 1, 5)), phone.vector().missingFrom(tablet.vector()))
        assertEquals(listOf(SequenceRange("A", 5, 6)), tablet.vector().missingFrom(phone.vector()))
        val outcome = SyncSession.run(phone, LocalPeerTransport(tablet))
        assertEquals(5, outcome.pulledOperations)
        assertEquals(2, outcome.pushedOperations)
        assertEquals(0, outcome.duplicateOperations)
        assertEquals(phone.vector(), tablet.vector())
    }

    @Test
    fun lanSyncWorksWithoutInternetAndDriveCatchesUpWhenItReturns() {
        var internet = false
        val drive = ImmutableBundleStore()
        val a = replica("A")
        val b = replica("B")
        a.weigh("goat-1", "30", hours(8))

        assertEquals(SyncSessionStatus.TRANSPORT_UNAVAILABLE, SyncSession.run(a, GoogleDriveTransport(drive) { internet }).status)
        assertEquals(1, SyncSession.run(b, LocalPeerTransport(a)).pulledOperations)
        assertTrue(drive.paths().isEmpty())

        internet = true
        val upload = SyncSession.run(b, GoogleDriveTransport(drive) { internet })
        assertEquals(SyncSessionStatus.COMPLETED, upload.status)
        assertEquals(1, upload.pushedOperations)
        assertEquals(listOf("GOAT/farms/farm-premier/sync/A/000000000001-000000000001.bundle"), drive.paths())
    }

    @Test
    fun aDeviceAwayForSeveralDaysCatchesUpInBundles() {
        val drive = ImmutableBundleStore()
        val a = replica("A")
        val c = replica("C")
        (1..250).forEach { a.weigh("goat-${it % 17}", "25", hours(24 * (it % 5)) + it) }
        SyncSession.run(a, GoogleDriveTransport(drive))
        assertEquals(3, drive.paths().size)

        val catchUp = SyncSession.run(c, GoogleDriveTransport(drive))
        assertEquals(250, catchUp.pulledOperations)
        assertEquals(a.vector(), c.vector())
        assertEquals(a.stateDigest(), c.stateDigest())
    }

    @Test
    fun peerAndDriveDeliveringTheSameOperationDoNotDuplicateIt() {
        val drive = ImmutableBundleStore()
        val a = replica("A")
        val b = replica("B")
        a.weigh("goat-1", "30", hours(8))
        SyncSession.run(a, GoogleDriveTransport(drive))
        SyncSession.run(b, LocalPeerTransport(a))

        val bundle = drive.get(drive.paths().single())!!
        assertEquals(IngestResult(duplicates = 1), b.ingest(bundle))
        assertEquals(1, b.facts(EntityKey("animal", "goat-1")).size)
    }

    @Test
    fun independentFieldChangesFromTheSameBaseMerge() {
        val a = replica("A")
        val b = replica("B")
        a.edit("goat-1", base = 3, at = hours(9), "name" to "Nala")
        b.edit("goat-1", base = 3, at = hours(10), "colour" to "brown")
        SyncSession.run(a, LocalPeerTransport(b))

        assertTrue(a.conflicts().isEmpty())
        assertTrue(b.conflicts().isEmpty())
        assertEquals(mapOf("colour" to "brown", "name" to "Nala"), a.state().getValue(EntityKey("animal", "goat-1")))
        assertEquals(a.stateDigest(), b.stateDigest())
    }

    @Test
    fun theSameFieldChangedConcurrentlyEntersTheConflictCentreAndResolvesByANewOperation() {
        val a = replica("A")
        val b = replica("B")
        val local = a.edit("goat-1", base = 3, at = hours(9), "name" to "Nala")
        val remote = b.edit("goat-1", base = 3, at = hours(10), "name" to "Zuri")
        SyncSession.run(a, LocalPeerTransport(b))

        val conflict = a.conflicts().single()
        assertEquals(conflict.conflictId, b.conflicts().single().conflictId)
        assertEquals(listOf("name"), conflict.fields)
        assertTrue(a.contains(local.operationId) && a.contains(remote.operationId))

        val correction = a.resolveConflict(conflict.conflictId, ConflictResolution.RETAIN_EXISTING, "manager-A")
        assertEquals("Nala", a.state().getValue(EntityKey("animal", "goat-1")).getValue("name"))
        SyncSession.run(b, LocalPeerTransport(a))

        assertFalse(b.conflicts().single().open)
        assertEquals(correction.operationId, b.conflicts().single().resolvedByOperationId)
        assertEquals("Nala", b.state().getValue(EntityKey("animal", "goat-1")).getValue("name"))
        assertTrue(b.contains(local.operationId) && b.contains(remote.operationId))
        assertEquals(a.stateDigest(), b.stateDigest())
    }

    @Test
    fun attachmentMetadataReplicatesBeforeItsBytes() {
        val a = replica("A")
        val b = replica("B")
        val bytes = "lab result PDF".toByteArray()
        val manifest = AttachmentManifest.describe(bytes, "application/pdf", "lab.pdf", EntityKey("animal", "goat-1"))
        a.recordAttachment(manifest, "vet-A", hours(11))
        SyncSession.run(b, LocalPeerTransport(a))

        val known = b.attachmentManifests().single()
        val store = AttachmentStore()
        assertEquals(AttachmentAvailability.AVAILABLE_WHEN_CONNECTED, store.availability(known))
        assertFalse(store.receive(known, "tampered".toByteArray()))
        assertEquals(AttachmentAvailability.AVAILABLE_WHEN_CONNECTED, store.availability(known))
        assertTrue(store.receive(known, bytes))
        assertEquals(AttachmentAvailability.LOCAL, store.availability(known))
    }

    @Test
    fun operationsFromOneFarmCanNeverEnterAnother() {
        val farmA = replica("A")
        val farmB = replica("B", farmId = "farm-other")
        farmA.weigh("goat-1", "30", hours(8))
        val bundle = farmA.bundlesFor(listOf(SequenceRange("A", 1, 1))).single()

        assertTrue(farmB.ingest(bundle).rejected)
        assertEquals(SyncVector(), farmB.vector())
        assertFailsWith<IllegalStateException> { SyncSession.run(farmB, LocalPeerTransport(farmA)) }

        val drive = ImmutableBundleStore()
        SyncSession.run(farmA, GoogleDriveTransport(drive))
        assertEquals(0, SyncSession.run(farmB, GoogleDriveTransport(drive)).pulledOperations)
        assertEquals(SyncVector(), farmB.vector())
    }

    @Test
    fun aRevokedDeviceCannotContinueSynchronising() {
        val farmRegistry = registry("A", "B")
        val a = replica("A", registry("A", "B"))
        val b = replica("B", farmRegistry)
        a.weigh("goat-1", "30", hours(8))
        a.weigh("goat-2", "31", hours(8))
        SyncSession.run(b, LocalPeerTransport(a))
        farmRegistry.markLostOrRevoked("A", b.vector())

        a.weigh("goat-3", "32", hours(9))
        assertEquals(SyncSessionStatus.PEER_NOT_AUTHORISED, SyncSession.run(b, LocalPeerTransport(a), remoteDeviceId = "A").status)
        val afterRevocation = a.bundlesFor(listOf(SequenceRange("A", 3, 3))).single()
        assertTrue(b.ingest(afterRevocation).rejected)
        assertEquals(2, b.vector().watermark("A"))
    }

    @Test
    fun lostDeviceStatusIsReportedTruthfully() {
        val farmRegistry = registry("A", "B")
        farmRegistry.reportOwnSequence("A", 5)
        val farmVector = SyncVector(mapOf("A" to 3))

        val check = farmRegistry.checkRetirement("A", farmVector)
        assertFalse(check.safeToRetire)
        assertEquals(4L..5L, check.unpublishedSequences)
        assertEquals(FarmCompleteness.KNOWN_STATE_WITH_PENDING_DEVICES, farmRegistry.completeness(farmVector))

        farmRegistry.markLostOrRevoked("A", farmVector)
        assertEquals(DeviceStatus.LOST_REVOKED, farmRegistry.device("A")!!.status)
        assertEquals(FarmCompleteness.UNKNOWN_LOST_DEVICE_DATA, farmRegistry.completeness(SyncVector(mapOf("A" to 5))))
        assertEquals(FarmCompleteness.ALL_KNOWN_DEVICES_SYNCHRONISED, registry("B").completeness(SyncVector()))
    }

    @Test
    fun checkpointRestorePlusDeltaReplayConvergesWithFullReplay() {
        val a = replica("A")
        val b = replica("B")
        a.weigh("goat-1", "30", hours(8))
        a.edit("goat-1", base = 1, at = hours(9), "name" to "Nala")
        b.weigh("goat-2", "28", hours(8))
        SyncSession.run(b, LocalPeerTransport(a))
        val checkpoint = b.checkpoint()

        // More work, including a late operation whose business time precedes the checkpointed edit.
        a.edit("goat-1", base = 2, at = hours(12), "name" to "Nala II")
        a.edit("goat-1", base = 1, at = hours(7), "colour" to "white")
        b.weigh("goat-2", "29", hours(13))
        SyncSession.run(a, LocalPeerTransport(b))

        val restored = FarmReplica.restore(checkpoint, "C", registry("A", "B", "C"), { "op-${++nextId}" }, { now })
        val delta = SyncSession.run(restored, LocalPeerTransport(a))
        assertEquals(3, delta.pulledOperations)

        val fullReplay = replica("C")
        SyncSession.run(fullReplay, LocalPeerTransport(a))
        assertEquals(a.stateDigest(), restored.stateDigest())
        assertEquals(fullReplay.stateDigest(), restored.stateDigest())
        assertEquals(a.vector(), restored.vector())

        val tampered = checkpoint.copy(vector = SyncVector(mapOf("A" to 99)))
        assertFailsWith<IllegalStateException> {
            FarmReplica.restore(tampered, "C", registry("A", "B", "C"), { "op-x" }, { now })
        }
    }

    @Test
    fun malformedOrCorruptedBundlesAreRejectedWithoutDamagingLocalData() {
        val drive = ImmutableBundleStore()
        val a = replica("A")
        val b = replica("B")
        b.weigh("goat-9", "40", hours(6))
        val before = b.stateDigest()
        a.weigh("goat-1", "30", hours(8))
        a.weigh("goat-2", "31", hours(8))
        SyncSession.run(a, GoogleDriveTransport(drive))

        val path = drive.paths().single()
        val good = drive.get(path)!!
        val forged = good.operations[1].copy(payload = mapOf("kg" to "999"))
        drive.corrupt(path, good.copy(operations = listOf(good.operations[0], forged)))
        val outcome = SyncSession.run(b, GoogleDriveTransport(drive))

        assertEquals(1, outcome.rejectedReasons.size)
        assertEquals(0, outcome.pulledOperations)
        assertEquals(before, b.stateDigest())
        assertEquals(0, b.vector().watermark("A"))

        val gap = OperationBundle.seal(farm, "A", listOf(good.operations[1]))
        assertTrue(b.ingest(gap.copy(fromSequence = 1)).rejected)
        assertFailsWith<IllegalStateException> { drive.put(path, OperationBundle.seal(farm, "A", listOf(good.operations[0]))) }
    }

    @Test
    fun aReusedPositionWithDifferentContentIsRefused() {
        val a = replica("A")
        val b = replica("B")
        val original = a.weigh("goat-1", "30", hours(8))
        SyncSession.run(b, LocalPeerTransport(a))
        val equivocation = OperationEnvelope.seal(
            operationId = "op-forged", farmId = farm, entityType = "animal", entityId = "goat-1", actorId = "worker-A",
            deviceId = "A", deviceSequence = original.deviceSequence, businessTimeEpochMillis = hours(8),
            createdAtEpochMillis = now, baseVersion = null, operationType = "weight.record",
            mergeClass = MergeClass.APPEND_ONLY_EVENT, payload = mapOf("kg" to "12"), schemaVersion = 1, provenance = "local",
        )
        // At or below the incorporated watermark it is recognised as already incorporated, never applied twice.
        assertEquals(1, b.facts(EntityKey("animal", "goat-1")).size)
        val result = b.ingest(OperationBundle.seal(farm, "A", listOf(equivocation)))
        assertTrue(result.rejected || result.applied == 0)
        assertEquals(1, b.facts(EntityKey("animal", "goat-1")).size)
        assertNotNull(b.operation(original.operationId))
    }

    @Test
    fun replicationStatesNeverClaimSyncOrBackupThatDidNotHappen() {
        val a = replica("A")
        val op = a.weigh("goat-1", "30", hours(8))

        val offline = replicationStates(op, peerVectors = emptyList(), driveVector = SyncVector(), driveConfigured = true,
            driveReachable = false, peersReachable = false, inConflict = false)
        assertEquals(
            setOf(ReplicationState.SAVED_LOCALLY, ReplicationState.LAN_SYNC_PENDING, ReplicationState.PEER_UNAVAILABLE,
                ReplicationState.DRIVE_SYNC_PENDING, ReplicationState.DRIVE_UNAVAILABLE),
            offline,
        )
        val synced = replicationStates(op, listOf(SyncVector(mapOf("A" to 1))), SyncVector(mapOf("A" to 1)), true, true, true, false)
        assertEquals(setOf(ReplicationState.SAVED_LOCALLY, ReplicationState.SYNCED_WITH_PEER, ReplicationState.SYNCED_TO_DRIVE), synced)
        val neverObserved = replicationStates(op, null, null, driveConfigured = true, driveReachable = true, peersReachable = true, inConflict = false)
        assertEquals(setOf(ReplicationState.SAVED_LOCALLY, ReplicationState.UNKNOWN), neverObserved)
    }

    @Test
    fun deviceSequencesAreMonotonicAndIndependentOfWallClock() {
        val a = replica("A")
        now = 5_000_000
        val first = a.weigh("goat-1", "30", hours(8))
        now = 1_000 // clock moved backwards
        val second = a.weigh("goat-1", "31", hours(9))
        assertEquals(1, first.deviceSequence)
        assertEquals(2, second.deviceSequence)
        assertEquals(SyncVector(mapOf("A" to 2)), a.vector())
    }
}
