package com.farmos.domain.replication

/** Inclusive range of one device's operation sequences. */
data class SequenceRange(val deviceId: String, val from: Long, val to: Long) {
    init {
        require(from >= 1) { "Sequences start at 1" }
        require(to >= from) { "Empty sequence range" }
    }

    operator fun contains(sequence: Long): Boolean = sequence in from..to
}

/**
 * Per-device high-water marks: this replica has incorporated every operation originated by each
 * device up to and including its watermark. Devices never seen have watermark 0.
 */
class SyncVector(entries: Map<String, Long> = emptyMap()) {
    val entries: Map<String, Long> = entries.filterValues { it > 0 }.toSortedMap()

    fun watermark(deviceId: String): Long = entries[deviceId] ?: 0

    /** Ranges [remote] holds that this vector does not. Exchanging vectors is enough to know what to transfer. */
    fun missingFrom(remote: SyncVector): List<SequenceRange> =
        remote.entries.mapNotNull { (deviceId, remoteMark) ->
            val localMark = watermark(deviceId)
            if (remoteMark > localMark) SequenceRange(deviceId, localMark + 1, remoteMark) else null
        }

    /** True when this vector has incorporated everything [other] has. */
    fun covers(other: SyncVector): Boolean = other.entries.all { (deviceId, mark) -> watermark(deviceId) >= mark }

    fun merge(other: SyncVector): SyncVector =
        SyncVector((entries.keys + other.entries.keys).associateWith { maxOf(watermark(it), other.watermark(it)) })

    override fun equals(other: Any?): Boolean = other is SyncVector && other.entries == entries

    override fun hashCode(): Int = entries.hashCode()

    override fun toString(): String = entries.entries.joinToString(prefix = "{", postfix = "}") { "${it.key}=${it.value}" }
}
