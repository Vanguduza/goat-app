package com.farmos.data.herd

import androidx.room.withTransaction
import com.farmos.core.database.FarmAssetEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.FeedIssueEntity
import com.farmos.core.database.GrazingSessionEntity
import com.farmos.core.database.WaterPointEntity
import com.farmos.core.database.WaterPointEventEntity
import com.farmos.core.database.FeedPlanEntity
import com.farmos.core.database.InventoryMovementEntity
import com.farmos.core.database.MaintenanceEventEntity
import com.farmos.core.database.AssetMeterReadingEntity
import com.farmos.core.database.PaddockEntity
import com.farmos.core.database.PurchaseEntity
import com.farmos.core.database.SupplierEntity
import com.farmos.core.database.WaterRecordEntity
import com.farmos.core.database.MoneyRecordEntity
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.model.LocalCommandResult
import com.farmos.domain.ops.CreateFarmAsset
import com.farmos.domain.ops.CreatePaddock
import com.farmos.domain.ops.CreateSupplier
import com.farmos.domain.ops.EndGrazing
import com.farmos.domain.ops.IssueFeed
import com.farmos.domain.ops.OpsValidator
import com.farmos.domain.ops.RecordWaterPoint
import com.farmos.domain.ops.RecordWaterPointEvent
import com.farmos.domain.ops.RecordFeedPlan
import com.farmos.domain.ops.RecordMaintenance
import com.farmos.domain.ops.RecordAssetMeter
import com.farmos.domain.ops.RecordPurchase
import com.farmos.domain.ops.RecordWater
import com.farmos.domain.ops.StartGrazing
import kotlinx.serialization.json.Json

/** Command handlers extracted from the operations facade; validation and writes share one transaction. */
internal class FarmResourceCommands(
    private val database: FarmOsDatabase,
    private val farmId: String,
    private val json: Json,
    private val journal: OpsCommandJournal,
) {
    suspend fun createAsset(command: CreateFarmAsset, context: LocalCommandContext): LocalCommandResult = database.withTransaction {
        OpsValidator.asset(command)?.let { error(it) }
        journal.enqueueCommand(json, context, "asset.create.v1", "farm_asset", command.assetId, 0, command) {
            database.assets().insert(FarmAssetEntity(command.assetId, farmId, command.code.trim(), command.name.trim(), command.kind))
        }
        LocalCommandResult(context.mutationId, command.assetId, true)
    }

    suspend fun recordMaintenance(command: RecordMaintenance, context: LocalCommandContext): LocalCommandResult = database.withTransaction {
        journal.enqueueCommand(json, context, "maintenance.record.v1", "farm_asset", command.assetId, journal.observedVersion("farm_asset", command.assetId), command) {
            database.maintenance().insert(MaintenanceEventEntity(command.eventId, farmId, command.assetId, command.title.trim(), command.occurredEpochDay, command.note))
        }
        LocalCommandResult(context.mutationId, command.eventId, true)
    }

    suspend fun recordAssetMeter(command: RecordAssetMeter, context: LocalCommandContext): LocalCommandResult = database.withTransaction {
        OpsValidator.assetMeter(command)?.let { error(it) }
        journal.enqueueCommand(json, context, "asset.meter_record.v1", "farm_asset", command.assetId, 0, command) {
            database.assetMeters().insert(
                AssetMeterReadingEntity(
                    command.readingId, farmId, command.assetId, command.readingValue,
                    command.unit.trim(), command.occurredEpochDay, command.note?.trim()?.takeIf { it.isNotBlank() },
                ),
            )
        }
        LocalCommandResult(context.mutationId, command.readingId, true)
    }

    suspend fun issueFeed(command: IssueFeed, context: LocalCommandContext): LocalCommandResult = database.withTransaction {
        journal.enqueueCommand(json, context, "feed.issue.v1", "inventory_item", command.itemId, journal.observedVersion("inventory_item", command.itemId), command) {
            val item = requireNotNull(database.inventory().item(farmId, command.itemId)) { "Inventory item not found" }
            OpsValidator.feed(command, item.quantityMilli)?.let { error(it) }
            database.feedIssues().insert(FeedIssueEntity(command.issueId, farmId, command.itemId, command.groupId, command.quantityMilli, command.occurredEpochDay))
            database.inventory().insertMovement(
                InventoryMovementEntity(command.issueId, farmId, command.itemId, "issue", command.quantityMilli, context.occurredAtEpochMillis),
            )
            database.inventory().setQuantity(farmId, command.itemId, item.quantityMilli - command.quantityMilli, context.occurredAtEpochMillis)
        }
        LocalCommandResult(context.mutationId, command.issueId, true)
    }

    suspend fun recordFeedPlan(command: RecordFeedPlan, context: LocalCommandContext): LocalCommandResult = database.withTransaction {
        OpsValidator.feedPlan(command)?.let { error(it) }
        journal.enqueueCommand(json, context, "feed.record_plan.v1", "feed_plan", command.planId, 0, command) {
            database.feedPlans().insert(
                FeedPlanEntity(
                    command.planId, farmId, command.name.trim(), command.speciesCode.trim(),
                    command.rationGramsPerHeadPerDay, command.headCount, command.startEpochDay, command.endEpochDay,
                    command.note?.trim()?.takeIf { it.isNotBlank() },
                ),
            )
        }
        LocalCommandResult(context.mutationId, command.planId, true)
    }

    suspend fun recordWater(command: RecordWater, context: LocalCommandContext): LocalCommandResult = database.withTransaction {
        OpsValidator.water(command)?.let { error(it) }
        journal.enqueueCommand(json, context, "water.record.v1", "water_record", command.recordId, 0, command) {
            database.water().insert(WaterRecordEntity(command.recordId, farmId, command.source.trim(), command.litresMilli, command.occurredEpochDay))
        }
        LocalCommandResult(context.mutationId, command.recordId, true)
    }

    suspend fun recordWaterPoint(command: RecordWaterPoint, context: LocalCommandContext): LocalCommandResult = database.withTransaction {
        OpsValidator.waterPoint(command)?.let { error(it) }
        journal.enqueueCommand(json, context, "water.record_point.v1", "water_point", command.pointId, 0, command) {
            database.waterPoints().insert(
                WaterPointEntity(
                    command.pointId, farmId, command.code.trim(),
                    command.name.trim().ifBlank { command.code.trim() }, command.kind.trim(), command.active,
                ),
            )
        }
        LocalCommandResult(context.mutationId, command.pointId, true)
    }

    suspend fun recordWaterPointEvent(command: RecordWaterPointEvent, context: LocalCommandContext): LocalCommandResult = database.withTransaction {
        OpsValidator.waterPointEvent(command)?.let { error(it) }
        journal.enqueueCommand(json, context, "water.record_point_event.v1", "water_point_event", command.eventId, 0, command) {
            val point = requireNotNull(database.waterPoints().get(farmId, command.pointId)) { "Water point not found" }
            database.waterPointEvents().insert(
                WaterPointEventEntity(
                    command.eventId, farmId, point.id, command.kind.trim(),
                    command.occurredEpochDay,
                    command.resultText?.trim()?.takeIf { it.isNotBlank() },
                    command.valueMilli,
                    command.unit?.trim()?.takeIf { it.isNotBlank() },
                    command.note?.trim()?.takeIf { it.isNotBlank() },
                ),
            )
        }
        LocalCommandResult(context.mutationId, command.eventId, true)
    }

    suspend fun createSupplier(command: CreateSupplier, context: LocalCommandContext): LocalCommandResult = database.withTransaction {
        OpsValidator.supplier(command)?.let { error(it) }
        journal.enqueueCommand(json, context, "supplier.create.v1", "supplier", command.supplierId, 0, command) {
            database.lifecycle().insertSupplier(SupplierEntity(command.supplierId, farmId, command.name.trim(), command.leadTimeDays))
        }
        LocalCommandResult(context.mutationId, command.supplierId, true)
    }

    suspend fun recordPurchase(command: RecordPurchase, context: LocalCommandContext): LocalCommandResult = database.withTransaction {
        OpsValidator.purchase(command)?.let { error(it) }
        journal.enqueueCommand(json, context, "purchase.record.v1", "inventory_item", command.itemId, journal.observedVersion("inventory_item", command.itemId), command) {
            requireNotNull(database.lifecycle().suppliers(farmId).firstOrNull { it.id == command.supplierId }) { "Supplier not found" }
            val item = requireNotNull(database.inventory().item(farmId, command.itemId)) { "Inventory item not found" }
            database.lifecycle().insertPurchase(
                PurchaseEntity(command.purchaseId, farmId, command.supplierId, command.itemId, command.quantityMilli, command.amountMinor, command.currency, command.occurredEpochDay),
            )
            database.inventory().insertMovement(
                InventoryMovementEntity(command.purchaseId, farmId, command.itemId, "receive", command.quantityMilli, context.occurredAtEpochMillis),
            )
            database.inventory().setQuantity(farmId, command.itemId, item.quantityMilli + command.quantityMilli, context.occurredAtEpochMillis)
            database.money().insert(
                MoneyRecordEntity(command.purchaseId, farmId, "expense", "purchase", command.amountMinor, command.currency, command.occurredEpochDay, "purchase"),
            )
        }
        LocalCommandResult(context.mutationId, command.purchaseId, true)
    }

    suspend fun createPaddock(command: CreatePaddock, context: LocalCommandContext): LocalCommandResult = database.withTransaction {
        OpsValidator.paddock(command)?.let { error(it) }
        journal.enqueueCommand(json, context, "paddock.create.v1", "paddock", command.paddockId, 0, command) {
            database.paddocks().insert(
                PaddockEntity(command.paddockId, farmId, command.code.trim(), command.displayName.trim(), command.areaM2, command.waterSource, command.shade, true),
            )
        }
        LocalCommandResult(context.mutationId, command.paddockId, true)
    }

    suspend fun startGrazing(command: StartGrazing, context: LocalCommandContext): LocalCommandResult = database.withTransaction {
        journal.enqueueCommand(json, context, "grazing.start.v1", "grazing_session", command.sessionId, 0, command) {
            if (database.grazing().hasOpen(farmId, command.paddockId)) error("This paddock already has an open grazing session")
            val group = requireNotNull(database.groups().get(farmId, command.groupId)) { "Group not found" }
            database.grazing().insert(
                GrazingSessionEntity(command.sessionId, farmId, command.paddockId, command.groupId, group.speciesCode, command.enteredEpochDay, null, command.headCount),
            )
        }
        LocalCommandResult(context.mutationId, command.sessionId, true)
    }

    suspend fun endGrazing(command: EndGrazing, context: LocalCommandContext): LocalCommandResult = database.withTransaction {
        journal.enqueueCommand(json, context, "grazing.end.v1", "grazing_session", command.sessionId, journal.observedVersion("grazing_session", command.sessionId), command) {
            database.grazing().end(farmId, command.sessionId, command.exitedEpochDay)
        }
        LocalCommandResult(context.mutationId, command.sessionId, true)
    }
}
