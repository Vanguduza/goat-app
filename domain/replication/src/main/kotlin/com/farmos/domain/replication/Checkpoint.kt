package com.farmos.domain.replication

/**
 * An integrity-sealed snapshot of incorporated farm state at [vector]. A new device restores it and then
 * replays only operations above its watermarks, instead of replaying the farm's whole history.
 *
 * Compaction never erases history: superseded field updates are omitted from the snapshot but remain in
 * the immutable published bundles; facts, postings and irreversible status changes are carried in full.
 */
data class Checkpoint(
    val farmId: String,
    val vector: SyncVector,
    val fields: Map<EntityKey, Map<String, FieldWrite>>,
    val facts: List<OperationEnvelope>,
    val openConflicts: List<ReplicationConflict>,
    val protocolVersion: Int,
    val checksum: String,
) {
    fun checksumValid(): Boolean =
        facts.all { it.checksumValid() && it.farmId == farmId } &&
            checksum == compute(farmId, vector, fields, facts, openConflicts, protocolVersion)

    companion object {
        fun seal(
            farmId: String,
            vector: SyncVector,
            fields: Map<EntityKey, Map<String, FieldWrite>>,
            facts: List<OperationEnvelope>,
            openConflicts: List<ReplicationConflict>,
        ): Checkpoint = Checkpoint(
            farmId, vector, fields, facts, openConflicts, REPLICATION_PROTOCOL_VERSION,
            compute(farmId, vector, fields, facts, openConflicts, REPLICATION_PROTOCOL_VERSION),
        )

        private fun compute(
            farmId: String,
            vector: SyncVector,
            fields: Map<EntityKey, Map<String, FieldWrite>>,
            facts: List<OperationEnvelope>,
            openConflicts: List<ReplicationConflict>,
            protocolVersion: Int,
        ): String = Sha256.hex(
            buildString {
                append(farmId).append('|').append(protocolVersion).append('|').append(vector.toString()).append('|')
                fields.toSortedMap(compareBy({ it.entityType }, { it.entityId })).forEach { (key, byField) ->
                    append(key.entityType).append('/').append(key.entityId).append('{')
                    byField.toSortedMap().forEach { (name, write) ->
                        append(name.length).append(':').append(name).append('=')
                        append(write.value.length).append(':').append(write.value).append('@').append(write.operationId).append(';')
                    }
                    append('}')
                }
                facts.map { it.checksum }.sorted().forEach { append(it).append(',') }
                openConflicts.map { it.conflictId }.sorted().forEach { append(it).append(',') }
            },
        )
    }
}
