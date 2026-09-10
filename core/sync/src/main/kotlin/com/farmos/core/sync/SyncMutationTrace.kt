package com.farmos.core.sync

data class SyncMutationTrace(
    val mutationId: String,
    val farmId: String,
    val aggregateType: String,
    val aggregateId: String,
    val aggregateOrdinal: Long,
    val rpc: String,
    val result: String,
    val rejectionClass: String?,
    val retryCount: Int,
    val workName: String?,
    val recordedAtEpochMillis: Long,
) {
    fun toStructuredLine(): String = buildString {
        append("mutationId=").append(mutationId)
        append(" farmId=").append(farmId)
        append(" aggregateType=").append(aggregateType)
        append(" aggregateId=").append(aggregateId)
        append(" aggregateOrdinal=").append(aggregateOrdinal)
        append(" rpc=").append(rpc)
        append(" result=").append(result)
        append(" rejectionClass=").append(rejectionClass ?: "none")
        append(" retryCount=").append(retryCount)
        append(" workName=").append(workName ?: "foreground")
        append(" recordedAtEpochMillis=").append(recordedAtEpochMillis)
    }
}

fun interface SyncObserver {
    fun onMutation(trace: SyncMutationTrace)
}
