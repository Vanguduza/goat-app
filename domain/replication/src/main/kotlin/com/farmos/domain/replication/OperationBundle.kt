package com.farmos.domain.replication

/**
 * An immutable, contiguous batch of one device's operations: exactly sequences [fromSequence]..[toSequence]
 * of [deviceId] in farm [farmId]. Once published a bundle is never rewritten; later operations go into
 * later bundles.
 */
data class OperationBundle(
    val farmId: String,
    val deviceId: String,
    val fromSequence: Long,
    val toSequence: Long,
    val operations: List<OperationEnvelope>,
    val protocolVersion: Int,
    val checksum: String,
) {
    val range: SequenceRange get() = SequenceRange(deviceId, fromSequence, toSequence)

    /** Every structural and integrity rule a receiver checks before applying anything. */
    fun verify(expectedFarmId: String): BundleVerdict {
        if (farmId != expectedFarmId) return BundleVerdict.Rejected("Bundle belongs to another farm")
        if (protocolVersion > REPLICATION_PROTOCOL_VERSION) return BundleVerdict.Rejected("Bundle uses a newer protocol version")
        if (operations.isEmpty()) return BundleVerdict.Rejected("Bundle has no operations")
        if (operations.size.toLong() != toSequence - fromSequence + 1) return BundleVerdict.Rejected("Bundle range does not match its operations")
        operations.forEachIndexed { index, op ->
            if (op.farmId != farmId) return BundleVerdict.Rejected("Operation ${op.operationId} belongs to another farm")
            if (op.deviceId != deviceId) return BundleVerdict.Rejected("Operation ${op.operationId} came from another device")
            if (op.deviceSequence != fromSequence + index) return BundleVerdict.Rejected("Bundle sequences are not contiguous")
            if (!op.checksumValid()) return BundleVerdict.Rejected("Operation ${op.operationId} failed its checksum")
        }
        if (checksum != computeChecksum(farmId, deviceId, fromSequence, toSequence, protocolVersion, operations)) {
            return BundleVerdict.Rejected("Bundle checksum does not match")
        }
        return BundleVerdict.Valid
    }

    companion object {
        fun seal(farmId: String, deviceId: String, operations: List<OperationEnvelope>): OperationBundle {
            require(operations.isNotEmpty()) { "A bundle needs operations" }
            val sorted = operations.sortedBy { it.deviceSequence }
            val from = sorted.first().deviceSequence
            val to = sorted.last().deviceSequence
            return OperationBundle(
                farmId, deviceId, from, to, sorted, REPLICATION_PROTOCOL_VERSION,
                computeChecksum(farmId, deviceId, from, to, REPLICATION_PROTOCOL_VERSION, sorted),
            )
        }

        private fun computeChecksum(
            farmId: String,
            deviceId: String,
            from: Long,
            to: Long,
            protocolVersion: Int,
            operations: List<OperationEnvelope>,
        ): String = Sha256.hex(
            listOf(farmId, deviceId, from.toString(), to.toString(), protocolVersion.toString())
                .plus(operations.map { it.checksum })
                .joinToString("|"),
        )
    }
}

sealed interface BundleVerdict {
    data object Valid : BundleVerdict

    data class Rejected(val reason: String) : BundleVerdict
}
