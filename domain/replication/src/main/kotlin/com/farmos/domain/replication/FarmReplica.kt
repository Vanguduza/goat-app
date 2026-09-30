package com.farmos.domain.replication

/** Deterministic order key for last-writer resolution: business time, device, sequence. */
data class OrderKey(val businessTimeEpochMillis: Long, val deviceId: String, val deviceSequence: Long) : Comparable<OrderKey> {
    override fun compareTo(other: OrderKey): Int =
        compareValuesBy(this, other, { it.businessTimeEpochMillis }, { it.deviceId }, { it.deviceSequence })
}

/** The operation that currently determines one field's value. */
data class FieldWrite(
    val value: String,
    val operationId: String,
    val deviceId: String,
    val baseVersion: Long?,
    val order: OrderKey,
)

/**
 * A genuine concurrent change: two devices changed the same field(s) of the same record from the same
 * base version to different values. Both operations stay in the journal; nothing is discarded.
 */
data class ReplicationConflict(
    val conflictId: String,
    val entityKey: EntityKey,
    val fields: List<String>,
    val existing: OperationEnvelope,
    val incoming: OperationEnvelope,
    val resolvedByOperationId: String? = null,
) {
    val open: Boolean get() = resolvedByOperationId == null
}

enum class ConflictResolution { RETAIN_EXISTING, ACCEPT_INCOMING }

data class IngestResult(
    val applied: Int = 0,
    val duplicates: Int = 0,
    val rejectedReason: String? = null,
) {
    val rejected: Boolean get() = rejectedReason != null
}

/**
 * One device's copy of one farm's replicated journal. Local writes commit here first and succeed
 * without any peer, gateway or Internet. Incoming operations are verified, de-duplicated and applied
 * idempotently whatever transport delivered them and in whatever order they arrive.
 */
class FarmReplica(
    val farmId: String,
    val deviceId: String,
    val registry: DeviceRegistry,
    private val newOperationId: () -> String,
    private val clock: () -> Long,
) {
    private val journal = LinkedHashMap<String, OperationEnvelope>()
    private val positions = HashMap<DevicePosition, String>()
    private val byEntity = HashMap<EntityKey, MutableList<String>>()
    private val checkpointMarks = HashMap<String, Long>()
    private val fields = HashMap<EntityKey, MutableMap<String, FieldWrite>>()
    private val conflicts = LinkedHashMap<String, ReplicationConflict>()
    private var lastOwnSequence = 0L

    /** Records a local farm mutation. It is saved locally once this returns; replication happens later. */
    fun record(
        entityType: String,
        entityId: String,
        actorId: String,
        businessTimeEpochMillis: Long,
        baseVersion: Long?,
        operationType: String,
        mergeClass: MergeClass,
        payload: Map<String, String>,
        schemaVersion: Int = 1,
        provenance: String = "local",
    ): OperationEnvelope {
        check(registry.accepts(deviceId, lastOwnSequence + 1)) { "This device is not permitted to record farm operations" }
        val op = OperationEnvelope.seal(
            operationId = newOperationId(),
            farmId = farmId,
            entityType = entityType,
            entityId = entityId,
            actorId = actorId,
            deviceId = deviceId,
            deviceSequence = lastOwnSequence + 1,
            businessTimeEpochMillis = businessTimeEpochMillis,
            createdAtEpochMillis = clock(),
            baseVersion = baseVersion,
            operationType = operationType,
            mergeClass = mergeClass,
            payload = payload,
            schemaVersion = schemaVersion,
            provenance = provenance,
        )
        lastOwnSequence = op.deviceSequence
        registry.reportOwnSequence(deviceId, lastOwnSequence)
        apply(op)
        return op
    }

    /** Contiguous high-water marks of every device this replica has incorporated. */
    fun vector(): SyncVector {
        val devices = checkpointMarks.keys + positions.keys.map { it.deviceId }
        return SyncVector(
            devices.associateWith { device ->
                var mark = checkpointMarks[device] ?: 0
                while (positions.containsKey(DevicePosition(device, mark + 1))) mark++
                mark
            },
        )
    }

    fun contains(operationId: String): Boolean = journal.containsKey(operationId)

    fun operation(operationId: String): OperationEnvelope? = journal[operationId]

    /** Contiguous bundles covering as much of each requested range as this replica holds. */
    fun bundlesFor(ranges: List<SequenceRange>, maxOperationsPerBundle: Int = 100): List<OperationBundle> =
        ranges.flatMap { range ->
            val present = generateSequence(range.from) { it + 1 }
                .takeWhile { it <= range.to }
                .map { positions[DevicePosition(range.deviceId, it)]?.let(journal::get) }
                .takeWhile { it != null }
                .filterNotNull()
                .toList()
            present.chunked(maxOperationsPerBundle).map { OperationBundle.seal(farmId, range.deviceId, it) }
        }

    /**
     * Verifies then applies a bundle atomically: if any rule fails nothing is applied. Operations
     * already incorporated are counted as duplicates and change nothing.
     */
    fun ingest(bundle: OperationBundle): IngestResult {
        when (val verdict = bundle.verify(farmId)) {
            is BundleVerdict.Rejected -> return IngestResult(rejectedReason = verdict.reason)
            BundleVerdict.Valid -> Unit
        }
        val fresh = mutableListOf<OperationEnvelope>()
        var duplicates = 0
        for (op in bundle.operations) {
            if (!registry.accepts(op.deviceId, op.deviceSequence)) {
                return IngestResult(rejectedReason = "Device ${op.deviceId} is not permitted to add operation ${op.deviceSequence}")
            }
            val known = journal[op.operationId]
            when {
                known != null && known.checksum != op.checksum ->
                    return IngestResult(rejectedReason = "Operation ${op.operationId} was altered after it was published")
                known != null -> duplicates++
                op.deviceSequence <= (checkpointMarks[op.deviceId] ?: 0) -> duplicates++
                positions[op.position].let { it != null && it != op.operationId } ->
                    return IngestResult(rejectedReason = "Position ${op.deviceId}:${op.deviceSequence} is already taken by another operation")
                else -> fresh += op
            }
        }
        fresh.forEach(::apply)
        if (bundle.deviceId == deviceId) lastOwnSequence = maxOf(lastOwnSequence, bundle.toSequence)
        return IngestResult(applied = fresh.size, duplicates = duplicates)
    }

    /** Farm history in business order; synchronisation order never affects it. */
    fun history(): List<OperationEnvelope> = journal.values.sortedWith(CanonicalOperationOrder)

    /** Current values of mutable record fields. */
    fun state(): Map<EntityKey, Map<String, String>> =
        fields.mapValues { (_, byField) -> byField.mapValues { it.value.value }.toSortedMap() }

    /** Append-only facts and postings recorded against [entityKey], in business order. */
    fun facts(entityKey: EntityKey): List<OperationEnvelope> =
        byEntity[entityKey].orEmpty().mapNotNull(journal::get)
            .filter { it.mergeClass == MergeClass.APPEND_ONLY_EVENT || it.mergeClass == MergeClass.POSTING }
            .sortedWith(CanonicalOperationOrder)

    fun conflicts(): List<ReplicationConflict> = conflicts.values.toList()

    /**
     * Resolves a conflict with a new corrective operation. The two conflicting operations stay in the
     * journal; the resolution is itself replicated like any other operation.
     */
    fun resolveConflict(conflictId: String, resolution: ConflictResolution, actorId: String): OperationEnvelope {
        val conflict = requireNotNull(conflicts[conflictId]) { "Unknown conflict" }
        check(conflict.open) { "Conflict already resolved" }
        val chosen = if (resolution == ConflictResolution.RETAIN_EXISTING) conflict.existing else conflict.incoming
        // A resolution is a new decision made now, always ordered after both operations it resolves.
        val latest = maxOf(conflict.existing.businessTimeEpochMillis, conflict.incoming.businessTimeEpochMillis)
        return record(
            entityType = conflict.entityKey.entityType,
            entityId = conflict.entityKey.entityId,
            actorId = actorId,
            businessTimeEpochMillis = maxOf(clock(), latest + 1),
            baseVersion = null,
            operationType = "conflict.resolve",
            mergeClass = MergeClass.FIELD_UPDATE,
            payload = conflict.fields.associateWith { chosen.payload.getValue(it) },
            provenance = RESOLUTION_PROVENANCE_PREFIX + conflictId,
        )
    }

    /** Integrity-sealed snapshot of this replica's incorporated state. */
    fun checkpoint(): Checkpoint = Checkpoint.seal(
        farmId = farmId,
        vector = vector(),
        fields = fields.mapValues { it.value.toMap() },
        facts = journal.values.filter { it.mergeClass != MergeClass.FIELD_UPDATE }.sortedWith(CanonicalOperationOrder),
        openConflicts = conflicts.values.filter { it.open },
    )

    /** A digest of observable state, used to prove two replicas converged. */
    fun stateDigest(): String = Sha256.hex(
        buildString {
            fields.toSortedMap(compareBy({ it.entityType }, { it.entityId })).forEach { (key, byField) ->
                append(key.entityType).append('/').append(key.entityId).append('{')
                byField.toSortedMap().forEach { (name, write) -> append(name).append('=').append(write.value).append(';') }
                append('}')
            }
            journal.values.filter { it.mergeClass != MergeClass.FIELD_UPDATE }
                .map { it.operationId }.sorted().forEach { append(it).append(',') }
        },
    )

    private fun apply(op: OperationEnvelope) {
        journal[op.operationId] = op
        positions[op.position] = op.operationId
        byEntity.getOrPut(op.entityKey) { mutableListOf() } += op.operationId
        if (op.mergeClass == MergeClass.FIELD_UPDATE || op.mergeClass == MergeClass.IRREVERSIBLE_STATUS) {
            detectConflicts(op)
            val byField = fields.getOrPut(op.entityKey) { mutableMapOf() }
            val key = OrderKey(op.businessTimeEpochMillis, op.deviceId, op.deviceSequence)
            op.payload.forEach { (name, value) ->
                val current = byField[name]
                if (current == null || key > current.order) {
                    byField[name] = FieldWrite(value, op.operationId, op.deviceId, op.baseVersion, key)
                }
            }
        }
        if (op.provenance.startsWith(RESOLUTION_PROVENANCE_PREFIX)) {
            val conflictId = op.provenance.removePrefix(RESOLUTION_PROVENANCE_PREFIX)
            conflicts[conflictId]?.let { conflicts[conflictId] = it.copy(resolvedByOperationId = op.operationId) }
        }
    }

    private fun detectConflicts(incoming: OperationEnvelope) {
        if (incoming.baseVersion == null) return
        val candidates = byEntity[incoming.entityKey].orEmpty().mapNotNull(journal::get)
        for (existing in candidates) {
            if (existing.operationId == incoming.operationId) continue
            if (existing.mergeClass != incoming.mergeClass) continue
            if (existing.deviceId == incoming.deviceId || existing.baseVersion != incoming.baseVersion) continue
            val clashing = existing.payload.keys.intersect(incoming.payload.keys)
                .filter { existing.payload[it] != incoming.payload[it] }
                .sorted()
            if (clashing.isEmpty()) continue
            val id = listOf(existing.operationId, incoming.operationId).sorted().joinToString("+")
            if (id !in conflicts) conflicts[id] = ReplicationConflict(id, incoming.entityKey, clashing, existing, incoming)
        }
    }

    internal fun restoreFrom(checkpoint: Checkpoint) {
        checkpoint.vector.entries.forEach { (device, mark) -> checkpointMarks[device] = mark }
        checkpoint.fields.forEach { (key, byField) -> fields[key] = byField.toMutableMap() }
        checkpoint.facts.forEach { op ->
            journal[op.operationId] = op
            positions[op.position] = op.operationId
            byEntity.getOrPut(op.entityKey) { mutableListOf() } += op.operationId
        }
        checkpoint.openConflicts.forEach { conflicts[it.conflictId] = it }
        lastOwnSequence = checkpoint.vector.watermark(deviceId)
    }

    companion object {
        const val RESOLUTION_PROVENANCE_PREFIX = "conflict-resolution:"

        /**
         * Bootstraps a replica from a verified checkpoint. Operations after the checkpoint's watermarks are
         * then replayed normally; operations at or below them are recognised as already incorporated.
         */
        fun restore(
            checkpoint: Checkpoint,
            deviceId: String,
            registry: DeviceRegistry,
            newOperationId: () -> String,
            clock: () -> Long,
        ): FarmReplica {
            check(checkpoint.checksumValid()) { "Checkpoint failed its integrity check" }
            return FarmReplica(checkpoint.farmId, deviceId, registry, newOperationId, clock).also { it.restoreFrom(checkpoint) }
        }
    }
}
