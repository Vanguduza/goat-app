package com.farmos.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.farmos.domain.replication.CommandMergeClassification
import com.farmos.domain.replication.DeviceStatus
import com.farmos.domain.replication.MergeClass
import com.farmos.domain.replication.OperationEnvelope
import com.farmos.domain.replication.SyncVector

/** One immutable replicated farm operation, stored exactly as sealed. */
@Entity(
    tableName = "replication_operations",
    indices = [
        Index(value = ["farmId", "deviceId", "deviceSequence"], unique = true),
        Index(value = ["farmId", "entityType", "entityId"]),
    ],
)
data class ReplicationOperationEntity(
    @PrimaryKey val operationId: String,
    val farmId: String,
    val entityType: String,
    val entityId: String,
    val actorId: String,
    val deviceId: String,
    val deviceSequence: Long,
    val businessTimeEpochMillis: Long,
    val createdAtEpochMillis: Long,
    val baseVersion: Long?,
    val operationType: String,
    val mergeClass: String,
    val payloadJson: String,
    val protocolVersion: Int,
    val schemaVersion: Int,
    val provenance: String,
    val checksum: String,
)

/**
 * A device known to the farm. The row with [isLocal] is this device: its [lastReportedOwnSequence] is the
 * last sequence it issued, so sequences are never reused, even across process restarts.
 */
@Entity(tableName = "replication_devices", primaryKeys = ["farmId", "deviceId"])
data class ReplicationDeviceEntity(
    val farmId: String,
    val deviceId: String,
    val name: String,
    val status: String,
    val lastReportedOwnSequence: Long,
    val revokedAfterSequence: Long?,
    val isLocal: Boolean,
)

/** Contiguous-watermark input: highest sequence and row count per originating device. */
data class DeviceSequenceSpan(val deviceId: String, val maxSequence: Long, val operationCount: Long)

/** Blocking journal access for callers that already run on a background thread inside a transaction. */
@Dao
interface ReplicationBlockingDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertOperation(operation: ReplicationOperationEntity)

    @Upsert
    fun upsertDevice(device: ReplicationDeviceEntity)

    @Query("SELECT * FROM replication_devices WHERE farmId = :farmId AND deviceId = :deviceId LIMIT 1")
    fun device(farmId: String, deviceId: String): ReplicationDeviceEntity?

    @Query("SELECT * FROM replication_operations WHERE farmId = :farmId AND entityType = :entityType AND entityId = :entityId")
    fun operationsForEntity(farmId: String, entityType: String, entityId: String): List<ReplicationOperationEntity>
}

@Dao
interface ReplicationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOperation(operation: ReplicationOperationEntity)

    @Upsert
    suspend fun upsertDevice(device: ReplicationDeviceEntity)

    @Query("SELECT * FROM replication_devices WHERE farmId = :farmId AND deviceId = :deviceId LIMIT 1")
    suspend fun device(farmId: String, deviceId: String): ReplicationDeviceEntity?

    @Query("SELECT * FROM replication_devices WHERE farmId = :farmId ORDER BY deviceId")
    suspend fun devices(farmId: String): List<ReplicationDeviceEntity>

    @Query("SELECT * FROM replication_operations WHERE farmId = :farmId AND operationId = :operationId LIMIT 1")
    suspend fun operation(farmId: String, operationId: String): ReplicationOperationEntity?

    @Query(
        """
        SELECT * FROM replication_operations
        WHERE farmId = :farmId AND deviceId = :deviceId AND deviceSequence BETWEEN :fromSequence AND :toSequence
        ORDER BY deviceSequence
        """,
    )
    suspend fun operationsInRange(farmId: String, deviceId: String, fromSequence: Long, toSequence: Long): List<ReplicationOperationEntity>

    @Query(
        """
        SELECT deviceId, MAX(deviceSequence) AS maxSequence, COUNT(*) AS operationCount
        FROM replication_operations WHERE farmId = :farmId GROUP BY deviceId ORDER BY deviceId
        """,
    )
    suspend fun sequenceSpans(farmId: String): List<DeviceSequenceSpan>

    @Query("SELECT COUNT(*) FROM replication_operations WHERE farmId = :farmId")
    suspend fun count(farmId: String): Long

    /** Every journalled operation on one record, for folding field updates in canonical order. */
    @Query("SELECT * FROM replication_operations WHERE farmId = :farmId AND entityType = :entityType AND entityId = :entityId")
    suspend fun operationsForEntity(farmId: String, entityType: String, entityId: String): List<ReplicationOperationEntity>
}

fun ReplicationOperationEntity.toEnvelope(): OperationEnvelope = OperationEnvelope(
    operationId = operationId,
    farmId = farmId,
    entityType = entityType,
    entityId = entityId,
    actorId = actorId,
    deviceId = deviceId,
    deviceSequence = deviceSequence,
    businessTimeEpochMillis = businessTimeEpochMillis,
    createdAtEpochMillis = createdAtEpochMillis,
    baseVersion = baseVersion,
    operationType = operationType,
    mergeClass = MergeClass.valueOf(mergeClass),
    payload = mapOf(COMMAND_PAYLOAD_KEY to payloadJson),
    protocolVersion = protocolVersion,
    schemaVersion = schemaVersion,
    provenance = provenance,
    checksum = checksum,
)

fun OperationEnvelope.toEntity(): ReplicationOperationEntity = ReplicationOperationEntity(
    operationId = operationId,
    farmId = farmId,
    entityType = entityType,
    entityId = entityId,
    actorId = actorId,
    deviceId = deviceId,
    deviceSequence = deviceSequence,
    businessTimeEpochMillis = businessTimeEpochMillis,
    createdAtEpochMillis = createdAtEpochMillis,
    baseVersion = baseVersion,
    operationType = operationType,
    mergeClass = mergeClass.name,
    payloadJson = payload.getValue(COMMAND_PAYLOAD_KEY),
    protocolVersion = protocolVersion,
    schemaVersion = schemaVersion,
    provenance = provenance,
    checksum = checksum,
)

/** Payload key holding the command's canonical JSON inside a journalled operation. */
const val COMMAND_PAYLOAD_KEY = "command"

/**
 * Writes a local mutation's outbox row and its immutable replication operation. Callers invoke this
 * inside the same Room transaction as the domain write, so the domain change, the outbox row and the
 * journal entry commit or roll back together. The operation id is the mutation id; the device sequence
 * continues from the local device's last issued sequence.
 */
suspend fun FarmOsDatabase.insertOutboxAndJournal(outbox: OutboxEntity) {
    outbox().insert(outbox)
    journalLocalOperation(
        operationId = outbox.mutationId,
        farmId = outbox.farmId,
        entityType = outbox.aggregateType,
        entityId = outbox.aggregateId,
        actorId = outbox.actorId,
        deviceId = outbox.deviceId,
        businessTimeEpochMillis = outbox.occurredAtEpochMillis,
        createdAtEpochMillis = outbox.createdAtEpochMillis,
        baseVersion = outbox.expectedStreamVersion,
        operationType = outbox.commandName,
        payloadJson = outbox.payloadJson,
        schemaVersion = outbox.commandSchemaVersion,
    )
}

/**
 * Journals a device-local change that has no server-era outbox command, such as a farm setting. Like
 * [insertOutboxAndJournal], callers run it inside the Room transaction that makes the change, and it
 * takes the next sequence of the local device.
 */
suspend fun FarmOsDatabase.journalLocalOperation(
    operationId: String,
    farmId: String,
    entityType: String,
    entityId: String,
    actorId: String,
    deviceId: String,
    businessTimeEpochMillis: Long,
    createdAtEpochMillis: Long,
    baseVersion: Long?,
    operationType: String,
    payloadJson: String,
    schemaVersion: Int,
) {
    val journal = replication()
    val (operation, device) = localOperation(
        journal.device(farmId, deviceId), operationId, farmId, entityType, entityId, actorId, deviceId,
        businessTimeEpochMillis, createdAtEpochMillis, baseVersion, operationType, payloadJson, schemaVersion,
    )
    journal.insertOperation(operation)
    journal.upsertDevice(device)
}

/**
 * Blocking form of [journalLocalOperation] for callers that run on a background thread with blocking
 * DAOs, such as the local access store. Callers run it inside the transaction that makes the change.
 */
fun FarmOsDatabase.journalLocalOperationBlocking(
    operationId: String,
    farmId: String,
    entityType: String,
    entityId: String,
    actorId: String,
    deviceId: String,
    businessTimeEpochMillis: Long,
    operationType: String,
    payloadJson: String,
) {
    val journal = replicationBlocking()
    val (operation, device) = localOperation(
        journal.device(farmId, deviceId), operationId, farmId, entityType, entityId, actorId, deviceId,
        businessTimeEpochMillis, businessTimeEpochMillis, null, operationType, payloadJson, 1,
    )
    journal.insertOperation(operation)
    journal.upsertDevice(device)
}

/** Seals the next operation of the local device and its updated device row. */
private fun localOperation(
    previous: ReplicationDeviceEntity?,
    operationId: String,
    farmId: String,
    entityType: String,
    entityId: String,
    actorId: String,
    deviceId: String,
    businessTimeEpochMillis: Long,
    createdAtEpochMillis: Long,
    baseVersion: Long?,
    operationType: String,
    payloadJson: String,
    schemaVersion: Int,
): Pair<ReplicationOperationEntity, ReplicationDeviceEntity> {
    val sequence = (previous?.lastReportedOwnSequence ?: 0) + 1
    val operation = OperationEnvelope.seal(
        operationId = operationId,
        farmId = farmId,
        entityType = entityType,
        entityId = entityId,
        actorId = actorId,
        deviceId = deviceId,
        deviceSequence = sequence,
        businessTimeEpochMillis = businessTimeEpochMillis,
        createdAtEpochMillis = createdAtEpochMillis,
        baseVersion = baseVersion,
        operationType = operationType,
        mergeClass = CommandMergeClassification.forCommand(operationType),
        payload = mapOf(COMMAND_PAYLOAD_KEY to payloadJson),
        schemaVersion = schemaVersion,
        provenance = LOCAL_PROVENANCE,
    )
    return operation.toEntity() to ReplicationDeviceEntity(
        farmId = farmId,
        deviceId = deviceId,
        name = previous?.name ?: THIS_DEVICE_NAME,
        status = previous?.status ?: DeviceStatus.ACTIVE.name,
        lastReportedOwnSequence = sequence,
        revokedAfterSequence = previous?.revokedAfterSequence,
        isLocal = true,
    )
}

/**
 * This device's incorporated sync vector for [farmId]. A device's watermark counts only when its
 * operations are contiguous from sequence 1; a gap holds it at zero until the journal holds a
 * contiguous prefix, matching the protocol's contiguous-watermark rule.
 */
suspend fun FarmOsDatabase.replicationVector(farmId: String): SyncVector =
    SyncVector(
        replication().sequenceSpans(farmId)
            .filter { it.maxSequence == it.operationCount }
            .associate { it.deviceId to it.maxSequence },
    )

private const val LOCAL_PROVENANCE = "local"
private const val THIS_DEVICE_NAME = "This device"
