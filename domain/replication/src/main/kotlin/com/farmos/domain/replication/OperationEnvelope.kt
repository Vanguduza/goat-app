package com.farmos.domain.replication

import java.security.MessageDigest

/** Current replication protocol version carried on every operation and bundle. */
const val REPLICATION_PROTOCOL_VERSION = 1

/**
 * How concurrent operations on the same entity combine. There is no single generic merge rule:
 * each operation declares the semantics of the record it changes.
 */
enum class MergeClass {
    /** Facts that coexist (a weight, a treatment, an observation). Never conflict. */
    APPEND_ONLY_EVENT,

    /** Field updates on a mutable record. Disjoint fields merge; the same field from the same base conflicts. */
    FIELD_UPDATE,

    /** Money or stock postings. Every posting is kept; totals reconcile explicitly, never by overwrite. */
    POSTING,

    /** Status changes that cannot be undone (death, cull, sale exit). Concurrent changes need domain review. */
    IRREVERSIBLE_STATUS,
}

/**
 * One immutable replicated farm mutation. Its identity is [operationId]; its replication position is
 * [deviceId] + [deviceSequence]. [businessTimeEpochMillis] is when the farm event happened and is never
 * replaced by the time the operation was received, uploaded or synchronised.
 *
 * [payload] is a flat, farm-scoped field map; for [MergeClass.FIELD_UPDATE] its keys are the changed fields.
 * [checksum] is SHA-256 over the canonical encoding of every other property, so any alteration in
 * transit or storage is detectable.
 */
data class OperationEnvelope(
    val operationId: String,
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
    val mergeClass: MergeClass,
    val payload: Map<String, String>,
    val protocolVersion: Int,
    val schemaVersion: Int,
    val provenance: String,
    val checksum: String,
) {
    val position: DevicePosition get() = DevicePosition(deviceId, deviceSequence)
    val entityKey: EntityKey get() = EntityKey(entityType, entityId)

    fun checksumValid(): Boolean = checksum == computeChecksum()

    internal fun computeChecksum(): String = Sha256.hex(canonicalEncoding())

    private fun canonicalEncoding(): String = buildString {
        field(operationId)
        field(farmId)
        field(entityType)
        field(entityId)
        field(actorId)
        field(deviceId)
        field(deviceSequence.toString())
        field(businessTimeEpochMillis.toString())
        field(createdAtEpochMillis.toString())
        field(baseVersion?.toString() ?: "~")
        field(operationType)
        field(mergeClass.name)
        field(payload.size.toString())
        payload.toSortedMap().forEach { (key, value) ->
            field(key)
            field(value)
        }
        field(protocolVersion.toString())
        field(schemaVersion.toString())
        field(provenance)
    }

    companion object {
        /** Builds an envelope and seals it with its checksum. */
        fun seal(
            operationId: String,
            farmId: String,
            entityType: String,
            entityId: String,
            actorId: String,
            deviceId: String,
            deviceSequence: Long,
            businessTimeEpochMillis: Long,
            createdAtEpochMillis: Long,
            baseVersion: Long?,
            operationType: String,
            mergeClass: MergeClass,
            payload: Map<String, String>,
            schemaVersion: Int,
            provenance: String,
            protocolVersion: Int = REPLICATION_PROTOCOL_VERSION,
        ): OperationEnvelope {
            require(operationId.isNotBlank()) { "Operation id is required" }
            require(farmId.isNotBlank()) { "Farm id is required" }
            require(deviceId.isNotBlank()) { "Device id is required" }
            require(deviceSequence >= 1) { "Device sequence starts at 1" }
            val unsealed = OperationEnvelope(
                operationId, farmId, entityType, entityId, actorId, deviceId, deviceSequence,
                businessTimeEpochMillis, createdAtEpochMillis, baseVersion, operationType, mergeClass,
                payload.toSortedMap(), protocolVersion, schemaVersion, provenance, checksum = "",
            )
            return unsealed.copy(checksum = unsealed.computeChecksum())
        }
    }
}

/** A device's replication position: the [sequence]-th operation that device originated. */
data class DevicePosition(val deviceId: String, val sequence: Long)

/** A replicated entity, independent of which device created or changed it. */
data class EntityKey(val entityType: String, val entityId: String)

/**
 * Deterministic business order for farm history: business time, then originating device, then its
 * sequence. Synchronisation arrival order never participates.
 */
val CanonicalOperationOrder: Comparator<OperationEnvelope> =
    compareBy<OperationEnvelope>({ it.businessTimeEpochMillis }, { it.deviceId }, { it.deviceSequence })

internal object Sha256 {
    fun hex(text: String): String = hex(text.toByteArray(Charsets.UTF_8))

    fun hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

/** Length-prefixed field so that no two different field lists share an encoding. */
private fun StringBuilder.field(value: String) {
    append(value.length).append(':').append(value).append('|')
}
