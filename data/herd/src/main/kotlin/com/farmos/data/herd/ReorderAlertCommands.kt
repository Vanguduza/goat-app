package com.farmos.data.herd

import androidx.room.withTransaction
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.ReorderAlertEntity
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.model.LocalCommandResult
import com.farmos.domain.ops.OpsValidator
import com.farmos.domain.ops.RecordReorderAlert
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The observed quantities are part of v2 history, independent of later inventory changes. */
@Serializable
internal data class ReorderAlertSnapshot(
    val command: RecordReorderAlert,
    val onHandMilli: Long,
    val reorderMilli: Long,
)

internal class ReorderAlertCommands(
    private val database: FarmOsDatabase,
    private val farmId: String,
    private val json: Json,
    private val journal: OpsCommandJournal,
    private val replaying: Boolean,
) {
    suspend fun record(command: RecordReorderAlert, context: LocalCommandContext): LocalCommandResult =
        database.withTransaction {
            require(context.farmId == farmId) { "Farm context mismatch" }
            OpsValidator.reorderAlert(command)?.let { error(it) }
            if (!replaying) {
                database.requireLocalCommandAuthority(context, OpsCommandPermissions.requiredFor(RECORD, json.encodeToString(command)))
            }
            val original = database.replication().operation(farmId, context.mutationId)
            if (original?.operationType == LEGACY) {
                // Already-applied v1 rows retain their measurements. Its payload never sealed those
                // measurements, so an unapplied receiver cannot reconstruct them from current stock.
                journal.enqueueCommand(json, context, LEGACY, "inventory_item", command.itemId, original.baseVersion, command) {
                    error("Conflict: legacy reorder alert has no immutable stock snapshot; review the original operation")
                }
            } else {
                val snapshot = if (original == null) {
                    require(!replaying) { "A received change must be journalled before it is applied" }
                    val item = requireNotNull(database.inventory().item(farmId, command.itemId)) { "Inventory item not found" }
                    ReorderAlertSnapshot(command, item.quantityMilli, item.reorderMilli)
                } else {
                    require(original.operationType == RECORD) { "Mutation id was already used for a different change" }
                    json.decodeFromString<ReorderAlertSnapshot>(original.payloadJson).also {
                        require(it.command == command) { "Mutation id was already used for a different change" }
                    }
                }
                val expectedVersion = if (original == null) journal.expectedVersion("inventory_item", command.itemId) else original.baseVersion
                journal.enqueueCommand(json, context, RECORD, "inventory_item", command.itemId, expectedVersion, snapshot) {
                    require(snapshot.reorderMilli > 0 && snapshot.onHandMilli <= snapshot.reorderMilli) {
                        "Reorder alert needs on-hand at or below the reorder point"
                    }
                    database.lifecycle().insertReorderAlert(
                        ReorderAlertEntity(
                            command.alertId, farmId, command.itemId, snapshot.onHandMilli,
                            snapshot.reorderMilli, command.occurredEpochDay,
                        ),
                    )
                }
            }
            LocalCommandResult(context.mutationId, command.alertId, true)
        }

    companion object {
        const val LEGACY = "inventory.record_reorder.v1"
        const val RECORD = "inventory.record_reorder.v2"
    }
}
