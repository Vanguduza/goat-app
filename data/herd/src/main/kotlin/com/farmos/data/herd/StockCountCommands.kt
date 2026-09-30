package com.farmos.data.herd

import androidx.room.withTransaction
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.InventoryMovementEntity
import com.farmos.core.database.StockCountEntity
import com.farmos.core.database.StockCountLineEntity
import com.farmos.core.database.journalLocalOperation
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.model.LocalCommandResult
import com.farmos.domain.ops.PostStockCount
import com.farmos.domain.ops.RecordStockCountLine
import com.farmos.domain.ops.RejectStockCount
import com.farmos.domain.ops.StartStockCount
import com.farmos.domain.ops.StockCountRules
import com.farmos.domain.ops.StockCountStatus
import com.farmos.domain.ops.SubmitStockCount
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Stock counts (owner decision D-021): count, then variance, then review, then post. Posting adds one
 * adjustment movement per counted item under an id every device derives, and changes stock only when
 * that movement is new, so a count posted twice or on two devices adjusts stock once. Journalled for
 * farm replication only.
 */
class StockCountCommands(
    private val database: FarmOsDatabase,
    private val farmId: String,
    /** Replays an operation received from another device: domain writes only, it is already journalled. */
    private val replaying: Boolean = false,
    private val json: Json = Json { encodeDefaults = true },
) {
    suspend fun start(command: StartStockCount, context: LocalCommandContext): LocalCommandResult {
        journal(context, START, command.countId, json.encodeToString(command)) {
            if (database.stockCounts().get(farmId, command.countId) == null) {
                database.stockCounts().upsert(
                    StockCountEntity(command.countId, farmId, StockCountStatus.COUNTING.name, context.actorId, context.occurredAtEpochMillis, null, null, null, null, null),
                )
            }
        }
        return LocalCommandResult(context.mutationId, command.countId, true)
    }

    suspend fun recordLine(command: RecordStockCountLine, context: LocalCommandContext): LocalCommandResult {
        StockCountRules.lineError(command.countedMilli)?.let { error(it) }
        val count = count(command.countId)
        require(count.status == StockCountStatus.COUNTING.name) { "This count is no longer open for counting" }
        requireNotNull(database.inventory().item(farmId, command.itemId)) { "Inventory item not found" }
        val lineId = StockCountRules.lineId(command.countId, command.itemId)
        journal(context, LINE, command.countId, json.encodeToString(command)) {
            val existing = database.stockCounts().line(farmId, lineId)
            // Across devices the later count of an item wins, whatever order the records arrive in.
            if (existing == null || existing.countedAtEpochMillis <= context.occurredAtEpochMillis) {
                database.stockCounts().upsertLine(
                    StockCountLineEntity(lineId, farmId, command.countId, command.itemId, command.onHandAtCountMilli, command.countedMilli, context.actorId, context.occurredAtEpochMillis),
                )
            }
        }
        return LocalCommandResult(context.mutationId, lineId, true)
    }

    suspend fun submit(command: SubmitStockCount, context: LocalCommandContext): LocalCommandResult {
        val count = count(command.countId)
        move(count, StockCountStatus.SUBMITTED)
        journal(context, SUBMIT, command.countId, json.encodeToString(command)) {
            val current = count(command.countId)
            if (current.status == StockCountStatus.COUNTING.name) {
                database.stockCounts().upsert(current.copy(status = StockCountStatus.SUBMITTED.name, submittedByActorId = context.actorId, submittedAtEpochMillis = context.occurredAtEpochMillis))
            }
        }
        return LocalCommandResult(context.mutationId, command.countId, true)
    }

    suspend fun post(command: PostStockCount, context: LocalCommandContext): LocalCommandResult {
        val count = count(command.countId)
        move(count, StockCountStatus.POSTED)
        require(command.adjustments.none { it.varianceMilli == 0L }) { "Only non-zero variances are posted" }
        journal(context, POST, command.countId, json.encodeToString(command)) {
            command.adjustments.forEach { adjustment ->
                val item = requireNotNull(database.inventory().item(farmId, adjustment.itemId)) { "Inventory item not found" }
                val inserted = database.inventory().insertMovement(
                    InventoryMovementEntity(
                        id = StockCountRules.adjustmentMovementId(command.countId, adjustment.itemId),
                        farmId = farmId,
                        itemId = adjustment.itemId,
                        direction = if (adjustment.varianceMilli > 0) StockCountRules.COUNT_GAIN else StockCountRules.COUNT_LOSS,
                        quantityMilli = kotlin.math.abs(adjustment.varianceMilli),
                        occurredAtEpochMillis = context.occurredAtEpochMillis,
                    ),
                )
                // A variance is applied once: only when its adjustment movement is new on this device.
                if (inserted != -1L) {
                    database.inventory().setQuantity(farmId, adjustment.itemId, item.quantityMilli + adjustment.varianceMilli, context.occurredAtEpochMillis)
                }
            }
            val current = count(command.countId)
            if (current.status != StockCountStatus.POSTED.name) {
                database.stockCounts().upsert(current.copy(status = StockCountStatus.POSTED.name, decidedByActorId = context.actorId, decidedAtEpochMillis = context.occurredAtEpochMillis))
            }
        }
        return LocalCommandResult(context.mutationId, command.countId, true)
    }

    suspend fun reject(command: RejectStockCount, context: LocalCommandContext): LocalCommandResult {
        require(command.reason.isNotBlank()) { "Say why the count is rejected" }
        val count = count(command.countId)
        move(count, StockCountStatus.REJECTED)
        journal(context, REJECT, command.countId, json.encodeToString(command)) {
            val current = count(command.countId)
            if (current.status != StockCountStatus.REJECTED.name) {
                database.stockCounts().upsert(
                    current.copy(status = StockCountStatus.REJECTED.name, decidedByActorId = context.actorId, decidedAtEpochMillis = context.occurredAtEpochMillis, rejectionReason = command.reason.trim()),
                )
            }
        }
        return LocalCommandResult(context.mutationId, command.countId, true)
    }

    private suspend fun count(countId: String): StockCountEntity =
        requireNotNull(database.stockCounts().get(farmId, countId)) { "Stock count not found" }

    /** Refuses a transition the count cannot make; a replayed operation may find the count already there. */
    private fun move(count: StockCountEntity, next: StockCountStatus) {
        val status = StockCountStatus.valueOf(count.status)
        if (replaying && status == next) return
        require(StockCountRules.canMove(status, next)) { "A ${status.name.lowercase()} count cannot be ${next.name.lowercase()}" }
    }

    private suspend fun journal(context: LocalCommandContext, commandName: String, countId: String, payloadJson: String, localWrite: suspend () -> Unit) {
        require(context.farmId == farmId) { "Farm context mismatch" }
        if (replaying) return database.withTransaction { localWrite() }
        database.withTransaction {
            localWrite()
            database.journalLocalOperation(
                operationId = context.mutationId,
                farmId = farmId,
                entityType = "stock_count",
                entityId = countId,
                actorId = context.actorId,
                deviceId = context.deviceId,
                businessTimeEpochMillis = context.occurredAtEpochMillis,
                createdAtEpochMillis = System.currentTimeMillis(),
                baseVersion = null,
                operationType = commandName,
                payloadJson = payloadJson,
                schemaVersion = 1,
            )
        }
    }

    companion object {
        const val START = "stock.count_start.v1"
        const val LINE = "stock.count_line.v1"
        const val SUBMIT = "stock.count_submit.v1"
        const val POST = "stock.count_post.v1"
        const val REJECT = "stock.count_reject.v1"
    }
}
