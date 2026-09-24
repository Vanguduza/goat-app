package com.farmos.data.herd

import androidx.room.withTransaction
import com.farmos.core.database.AnimalEntity
import com.farmos.core.database.AnimalGroupEntity
import com.farmos.core.database.FarmAssetEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.CattleCalvingEntity
import com.farmos.core.database.CattlePdEntity
import com.farmos.core.database.CattleServiceEntity
import com.farmos.core.database.FamachaScoreEntity
import com.farmos.core.database.FeedIssueEntity
import com.farmos.core.database.FormularyItemEntity
import com.farmos.core.database.GoatMilkEntity
import com.farmos.core.database.GrazingSessionEntity
import com.farmos.core.database.HealthObservationEntity
import com.farmos.core.database.HealthPackEntity
import com.farmos.core.database.HealthTreatmentEntity
import com.farmos.core.database.InventoryItemEntity
import com.farmos.core.database.InventoryMovementEntity
import com.farmos.core.database.LabourEntryEntity
import com.farmos.core.database.MaintenanceEventEntity
import com.farmos.core.database.PaddockEntity
import com.farmos.core.database.PoultryFlockDayEntity
import com.farmos.core.database.PurchaseEntity
import com.farmos.core.database.CattleBcsEntity
import com.farmos.core.database.CattleLocomotionEntity
import com.farmos.core.database.CattleMilkEntity
import com.farmos.core.database.CattleSccEntity
import com.farmos.core.database.GoatBcsEntity
import com.farmos.core.database.AnimalIdentifierEntity
import com.farmos.core.database.CattleDofEntity
import com.farmos.core.database.CattleDryOffEntity
import com.farmos.core.database.CattleLotCloseEntity
import com.farmos.core.database.CattleLotPlacementEntity
import com.farmos.core.database.CattleWeaningEntity
import com.farmos.core.database.EnabledPoultryKindEntity
import com.farmos.core.database.GoatHeatEntity
import com.farmos.core.database.GoatLactationPlanEntity
import com.farmos.core.database.GoatMatingEntity
import com.farmos.core.database.GoatPregnancyEntity
import com.farmos.core.database.GoatSccEntity
import com.farmos.core.database.GroupCensusEntity
import com.farmos.core.database.HealthPackApplyEntity
import com.farmos.core.database.HealthPackSlotEntity
import com.farmos.core.database.InventoryLotEntity
import com.farmos.core.database.LabResultEntity
import com.farmos.core.database.OfficialMovementEntity
import com.farmos.core.database.PedigreeRelationEntity
import com.farmos.core.database.ReorderAlertEntity
import com.farmos.core.database.PoultryBiosecurityEntity
import com.farmos.core.database.PoultryPlacementEntity
import com.farmos.core.database.PoultryVaccinationEntity
import com.farmos.core.database.RabbitGiStasisEntity
import com.farmos.core.database.SheepMicronEntity
import com.farmos.core.database.VetVisitEntity
import com.farmos.core.database.RabbitInventoryLinkEntity
import com.farmos.core.database.PoultryHatchEntity
import com.farmos.core.database.PoultryHouseEntity
import com.farmos.core.database.RabbitMatingOutcomeEntity
import com.farmos.core.database.SheepFlystrikeEntity
import com.farmos.core.database.RabbitContractEntity
import com.farmos.core.database.RabbitKitEntity
import com.farmos.core.database.RabbitMarketPlanEntity
import com.farmos.core.database.RabbitRetentionEntity
import com.farmos.core.database.RabbitWaitlistEntity
import com.farmos.core.database.SheepDagEntity
import com.farmos.core.database.SheepFootrotEntity
import com.farmos.core.database.SheepShearingEntity
import com.farmos.core.database.RabbitFosterEntity
import com.farmos.core.database.RabbitKindlingEntity
import com.farmos.core.database.RabbitPalpationEntity
import com.farmos.core.database.RabbitWeanEntity
import com.farmos.core.database.SheepMarkingEntity
import com.farmos.core.database.SheepWeaningEntity
import com.farmos.core.database.SheepWoolEntity
import com.farmos.core.database.SaleRecordEntity
import com.farmos.core.database.SheepJoiningEntity
import com.farmos.core.database.SheepLambingEntity
import com.farmos.core.database.SheepScanEntity
import com.farmos.core.database.SupplierEntity
import com.farmos.core.database.WaterRecordEntity
import com.farmos.core.database.WithdrawalWindowEntity
import com.farmos.core.database.KiddingEntity
import com.farmos.core.database.MeasurementEntity
import com.farmos.core.database.MoneyRecordEntity
import com.farmos.core.database.OutboxEntity
import com.farmos.core.database.RabbitCageEntity
import com.farmos.core.database.RabbitNestBoxEntity
import com.farmos.core.database.RabbitWaveEntity
import com.farmos.core.database.SyncCursorEntity
import com.farmos.core.database.TaskEntity
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.model.LocalCommandResult
import com.farmos.core.model.SyncState
import com.farmos.core.network.PulledDomainEvent
import com.farmos.core.network.SupabasePullClient
import com.farmos.core.network.UnsupportedServerEvent
import com.farmos.domain.goat.RecordGoatFamacha
import com.farmos.domain.goat.RecordGoatKidding
import com.farmos.domain.goat.RecordGoatMilk
import com.farmos.domain.goat.RecordGoatWeight
import com.farmos.domain.goat.SetGoatStatus
import com.farmos.domain.goat.RecordGoatHeat
import com.farmos.domain.goat.RecordGoatMating
import com.farmos.domain.goat.RecordGoatPregnancy
import com.farmos.domain.ops.AcceptHealthPack
import com.farmos.domain.ops.AddHealthPackSlot
import com.farmos.domain.ops.ApplyHealthPack
import com.farmos.domain.ops.AssignAnimalIdentifier
import com.farmos.domain.ops.CloseCattleLot
import com.farmos.domain.ops.PlaceCattleLot
import com.farmos.domain.ops.RecordCattleDaysOnFeed
import com.farmos.domain.ops.RecordGroupCensus
import com.farmos.domain.ops.RecordReorderAlert
import com.farmos.domain.ops.SetInventoryReorder
import com.farmos.domain.ops.CompleteFarmTask
import com.farmos.domain.ops.CreateAnimalGroup
import com.farmos.domain.ops.EnablePoultryKind
import com.farmos.domain.ops.IssueInventoryLot
import com.farmos.domain.ops.LinkPedigree
import com.farmos.domain.ops.ReceiveInventoryLot
import com.farmos.domain.ops.RecordCattleWeaning
import com.farmos.domain.ops.RecordFamacha
import com.farmos.domain.ops.RecordLabResult
import com.farmos.domain.ops.RecordOfficialMovement
import com.farmos.domain.ops.RecordSheepMicron
import com.farmos.domain.ops.RecordVetVisit
import com.farmos.domain.ops.CreateFarmAsset
import com.farmos.domain.ops.CreateFarmTask
import com.farmos.domain.ops.CreateFormularyItem
import com.farmos.domain.ops.CreateInventoryItem
import com.farmos.domain.ops.CandlePoultryHatch
import com.farmos.domain.ops.CreatePaddock
import com.farmos.domain.ops.CreatePoultryHouse
import com.farmos.domain.ops.CreateSupplier
import com.farmos.domain.ops.EndGrazing
import com.farmos.domain.ops.IssueFeed
import com.farmos.domain.ops.MoveInventory
import com.farmos.domain.ops.OpsValidator
import com.farmos.domain.ops.RecordCattleBcs
import com.farmos.domain.ops.RecordCattleLocomotion
import com.farmos.domain.ops.RecordCattleMilk
import com.farmos.domain.ops.RecordCattleScc
import com.farmos.domain.ops.RecordSheepDag
import com.farmos.domain.ops.RecordSheepFootrot
import com.farmos.domain.ops.RecordSheepShearing
import com.farmos.domain.goat.RecordGoatBcs
import com.farmos.domain.goat.RecordGoatScc
import com.farmos.domain.ops.RecordCattleCalving
import com.farmos.domain.ops.RecordCattlePd
import com.farmos.domain.ops.RecordCattleService
import com.farmos.domain.ops.RecordHealthObservation
import com.farmos.domain.ops.RecordHealthTreatment
import com.farmos.domain.ops.RecordLabour
import com.farmos.domain.ops.RecordMaintenance
import com.farmos.domain.ops.RecordMoney
import com.farmos.domain.ops.PoultryKindIncubation
import com.farmos.domain.ops.RecordPoultryFlockDay
import com.farmos.domain.ops.PlacePoultryFlock
import com.farmos.domain.ops.RecordCattleDryOff
import com.farmos.domain.ops.RecordPoultryBiosecurity
import com.farmos.domain.ops.RecordPoultryHatch
import com.farmos.domain.ops.RecordPoultryVaccination
import com.farmos.domain.ops.RecordPurchase
import com.farmos.domain.ops.RecordSheepFlystrike
import com.farmos.domain.ops.SetPoultryHatch
import com.farmos.domain.ops.RecordRabbitFoster
import com.farmos.domain.ops.RecordRabbitKindling
import com.farmos.domain.ops.RecordRabbitPalpation
import com.farmos.domain.ops.RecordSale
import com.farmos.domain.ops.RecordSheepJoining
import com.farmos.domain.ops.RecordSheepLambing
import com.farmos.domain.ops.RecordSheepMarking
import com.farmos.domain.ops.RecordSheepScan
import com.farmos.domain.ops.RecordSheepWeaning
import com.farmos.domain.ops.RecordSheepWool
import com.farmos.domain.ops.RecordWater
import com.farmos.domain.ops.StartGrazing
import com.farmos.domain.rabbit.RabbitProgrammeValidator
import com.farmos.domain.rabbit.CreateRabbitCage
import com.farmos.domain.rabbit.CreateRabbitNestBox
import com.farmos.domain.rabbit.CreateRabbitWave
import com.farmos.domain.rabbit.BindRabbitBedding
import com.farmos.domain.rabbit.AgreeRabbitContract
import com.farmos.domain.rabbit.DecideRabbitRetention
import com.farmos.domain.rabbit.EnqueueRabbitWaitlist
import com.farmos.domain.rabbit.FulfillRabbitWaitlist
import com.farmos.domain.rabbit.PromoteRabbitKit
import com.farmos.domain.rabbit.RecordRabbitMarketPlan
import com.farmos.domain.rabbit.RecordRabbitGiStasis
import com.farmos.domain.rabbit.RecordRabbitMatingOutcome
import com.farmos.domain.rabbit.RecordRabbitWean
import com.farmos.domain.rabbit.RegisterRabbitKit
import com.farmos.domain.rabbit.SetRabbitNestBoxStatus
import com.farmos.domain.rabbit.KudbatSemiIntensiveExcel
import java.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal suspend fun consumeLotsFefo(
    database: FarmOsDatabase,
    farmId: String,
    itemId: String,
    quantityMilli: Long,
    updatedAt: Long,
) {
    val item = requireNotNull(database.inventory().item(farmId, itemId)) { "Inventory item not found" }
    require(item.quantityMilli >= quantityMilli) { "Not enough stock on this farm" }
    val lots = database.lifecycle().lotsFor(farmId, itemId)
    require(lots.sumOf { it.quantityMilli } >= quantityMilli) { "Not enough lot stock. Receive a dated lot first." }
    var remaining = quantityMilli
    for (lot in lots) {
        if (remaining <= 0L) break
        val take = minOf(lot.quantityMilli, remaining)
        database.lifecycle().setLotQuantity(farmId, lot.id, lot.quantityMilli - take)
        remaining -= take
    }
    require(remaining == 0L) { "Not enough lot stock. Receive a dated lot first." }
    database.inventory().setQuantity(farmId, itemId, item.quantityMilli - quantityMilli, updatedAt)
}

internal fun nestTransitionAllowed(from: String, to: String): Boolean = when (from) {
    "available", "sanitized" -> to == "assigned" || to == "in_cage"
    "assigned" -> to == "in_cage" || to == "dirty"
    "in_cage" -> to == "dirty"
    "dirty" -> to == "sanitized" || to == "available"
    else -> false
}

internal fun JsonObject.optionalText(key: String): String? {
    val value = this[key] ?: return null
    if (value is JsonNull) return null
    return value.jsonPrimitive.content.takeUnless { it.isBlank() }
}

internal fun JsonObject.requiredText(key: String): String =
    requireNotNull(optionalText(key)) { "Missing $key" }

internal fun CreateFarmTask.toEntity(farmId: String, updatedAt: Long, status: String) = TaskEntity(
    id = taskId,
    farmId = farmId,
    moduleCode = moduleCode,
    taskCode = taskCode,
    title = title,
    dueOnEpochDay = dueEpochDay,
    status = status,
    animalId = animalId,
    cageId = cageId,
    waveId = waveId,
    updatedAtEpochMillis = updatedAt,
)

