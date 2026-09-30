package com.farmos.app

import androidx.room.withTransaction
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.ReplicationDeviceEntity
import com.farmos.core.database.replicationVector
import com.farmos.core.database.toEntity
import com.farmos.core.database.toEnvelope
import com.farmos.domain.replication.BundleVerdict
import com.farmos.domain.replication.DeviceStatus
import com.farmos.domain.replication.IngestResult
import com.farmos.domain.replication.OperationBundle
import com.farmos.domain.replication.OperationEnvelope
import com.farmos.domain.replication.ReplicaEndpoint
import com.farmos.domain.replication.SequenceRange
import com.farmos.domain.replication.SyncVector
import kotlinx.coroutines.runBlocking

/**
 * This device's Room replication journal as a replication endpoint, so the LAN server, the Drive gateway
 * and [com.farmos.domain.replication.SyncSession] run against the real farm journal. Calls block and must
 * run off the main thread; transports already run on their own threads.
 *
 * Ingest follows the protocol exactly: a bundle is verified first; every operation must come from a
 * device of this farm within its revocation watermark; a known operation id with different content, or
 * a position already held by another operation, rejects the whole bundle; everything else is inserted
 * in one transaction, so a rejected bundle changes nothing. Received operations enter the journal with
 * their original identity, device, sequence and business time.
 */
class RoomReplicaEndpoint(
    private val database: FarmOsDatabase,
    override val farmId: String,
    override val deviceId: String,
) : ReplicaEndpoint {
    private val journal get() = database.replication()

    override fun vector(): SyncVector = runBlocking { database.replicationVector(farmId) }

    override fun bundlesFor(ranges: List<SequenceRange>, maxOperationsPerBundle: Int): List<OperationBundle> = runBlocking {
        ranges.flatMap { range ->
            val rows = journal.operationsInRange(farmId, range.deviceId, range.from, range.to)
            // Only the contiguous prefix from the requested start can be offered.
            val present = rows.withIndex().takeWhile { (index, row) -> row.deviceSequence == range.from + index }.map { it.value.toEnvelope() }
            present.chunked(maxOperationsPerBundle).map { OperationBundle.seal(farmId, range.deviceId, it) }
        }
    }

    override fun ingest(bundle: OperationBundle): IngestResult = runBlocking {
        when (val verdict = bundle.verify(farmId)) {
            is BundleVerdict.Rejected -> return@runBlocking IngestResult(rejectedReason = verdict.reason)
            BundleVerdict.Valid -> Unit
        }
        database.withTransaction {
            val origin = journal.device(farmId, bundle.deviceId)
                ?: return@withTransaction IngestResult(rejectedReason = "Device ${bundle.deviceId} is not a device of this farm")
            val fresh = mutableListOf<OperationEnvelope>()
            var duplicates = 0
            for (op in bundle.operations) {
                val cutoff = origin.revokedAfterSequence
                if (cutoff != null && op.deviceSequence > cutoff) {
                    return@withTransaction IngestResult(rejectedReason = "Device ${op.deviceId} is not permitted to add operation ${op.deviceSequence}")
                }
                val known = journal.operation(farmId, op.operationId)
                val atPosition = journal.operationsInRange(farmId, op.deviceId, op.deviceSequence, op.deviceSequence).singleOrNull()
                when {
                    known != null && known.checksum != op.checksum ->
                        return@withTransaction IngestResult(rejectedReason = "Operation ${op.operationId} was altered after it was published")
                    known != null -> duplicates++
                    atPosition != null ->
                        return@withTransaction IngestResult(rejectedReason = "Position ${op.deviceId}:${op.deviceSequence} is already taken by another operation")
                    else -> fresh += op
                }
            }
            fresh.forEach { journal.insertOperation(it.toEntity()) }
            if (fresh.isNotEmpty()) {
                journal.upsertDevice(origin.copy(lastReportedOwnSequence = maxOf(origin.lastReportedOwnSequence, bundle.toSequence)))
            }
            IngestResult(applied = fresh.size, duplicates = duplicates)
        }
    }

    override fun maySynchronise(deviceId: String): Boolean = runBlocking {
        // This device before its first local write has no row yet, and is not revoked.
        val device = journal.device(farmId, deviceId) ?: return@runBlocking deviceId == this@RoomReplicaEndpoint.deviceId
        device.status == DeviceStatus.ACTIVE.name || device.status == DeviceStatus.TEMPORARILY_OFFLINE.name
    }

    /** Records a device approved through pairing so its operations and sessions are accepted. */
    fun registerPairedDevice(pairedDeviceId: String, name: String) = runBlocking {
        val existing = journal.device(farmId, pairedDeviceId)
        check(existing == null || existing.status == DeviceStatus.ACTIVE.name) { "A retired or revoked device cannot be registered again" }
        if (existing == null) {
            journal.upsertDevice(ReplicationDeviceEntity(farmId, pairedDeviceId, name, DeviceStatus.ACTIVE.name, 0, null, isLocal = false))
        }
    }
}
