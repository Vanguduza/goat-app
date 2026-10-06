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

class FarmOsPullReconciler(
    private val database: FarmOsDatabase,
    private val pullClient: SupabasePullClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun reconcile(farmId: String, pageSize: Int = 200): PullPageResult {
        var cursor = database.syncCursors().get(farmId) ?: 0L
        var applied = 0
        var pages = 0
        while (true) {
            val events = pullClient.pull(farmId, cursor, pageSize)
            if (events.isEmpty()) break
            database.withTransaction {
                for (event in events) {
                    applyEvent(farmId, event)
                    database.aggregateVersions().advance(
                        farmId = farmId,
                        aggregateType = event.aggregateType,
                        aggregateId = event.aggregateId,
                        streamVersion = event.streamVersion,
                        updatedAtEpochMillis = now(),
                    )
                    cursor = maxOf(cursor, event.changeCursor)
                    applied++
                }
                database.syncCursors().upsert(
                    SyncCursorEntity(farmId = farmId, changeCursor = cursor, updatedAtEpochMillis = now()),
                )
            }
            pages++
            if (events.size < pageSize) break
        }
        return PullPageResult(applied, pages, cursor)
    }

    private suspend fun applyEvent(farmId: String, event: PulledDomainEvent) {
        val recordedAt = Instant.parse(event.recordedAt).toEpochMilli()
        when (event.eventType) {
            "goat.registered.v1",
            "rabbit.registered.v1",
            "sheep.registered.v1",
            "cattle.registered.v1",
            "poultry.registered.v1",
            -> {
                val species = event.eventType.substringBefore('.')
                val obj = event.payload.jsonObject
                val existing = database.animals().get(farmId, event.aggregateId)
                database.animals().upsertFromServer(
                    AnimalEntity(
                        id = event.aggregateId,
                        farmId = farmId,
                        tag = obj.requiredText("tag"),
                        name = obj.optionalText("name"),
                        speciesCode = species,
                        sex = obj.requiredText("sex"),
                        status = existing?.status ?: "active",
                        dateOfBirthEpochDay = obj.optionalText("dateOfBirthEpochDay")?.toLongOrNull(),
                        poultryKindCode = obj.optionalText("poultryKindCode"),
                        updatedAtEpochMillis = recordedAt,
                    ),
                )
            }
            "goat.weight_recorded.v1",
            "rabbit.weight_recorded.v1",
            "sheep.weight_recorded.v1",
            "cattle.weight_recorded.v1",
            "poultry.weight_recorded.v1",
            -> {
                val payload = json.decodeFromJsonElement(RecordGoatWeight.serializer(), event.payload)
                database.measurements().upsertFromServer(
                    MeasurementEntity(
                        id = payload.measurementId,
                        farmId = farmId,
                        animalId = payload.animalId,
                        type = "weight",
                        valueLong = payload.weightGrams,
                        unit = "g",
                        measuredAtEpochMillis = payload.measuredAtEpochMillis,
                    ),
                )
            }
            "goat.status_changed.v1",
            "rabbit.status_changed.v1",
            "sheep.status_changed.v1",
            "cattle.status_changed.v1",
            "poultry.status_changed.v1",
            -> {
                val payload = json.decodeFromJsonElement(SetGoatStatus.serializer(), event.payload)
                database.animals().updateStatus(farmId, payload.animalId, payload.status.wireValue(), recordedAt)
            }
            "goat.kidded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordGoatKidding.serializer(), event.payload)
                database.kidding().upsertFromServer(
                    KiddingEntity(
                        id = payload.kiddingId,
                        farmId = farmId,
                        damId = payload.damAnimalId,
                        bornCount = payload.bornCount,
                        liveCount = payload.liveCount,
                        deadCount = payload.deadCount,
                        occurredEpochDay = payload.occurredEpochDay,
                    ),
                )
            }
            "task.created.v1" -> {
                val payload = json.decodeFromJsonElement(CreateFarmTask.serializer(), event.payload)
                database.tasks().upsertFromServer(payload.toEntity(farmId, recordedAt, "open"))
            }
            "task.completed.v1" -> {
                val payload = json.decodeFromJsonElement(CompleteFarmTask.serializer(), event.payload)
                database.tasks().updateStatus(farmId, payload.taskId, "done", recordedAt)
            }
            "rabbit.cage_created.v1" -> {
                val payload = json.decodeFromJsonElement(CreateRabbitCage.serializer(), event.payload)
                database.rabbitProgramme().upsertCage(
                    RabbitCageEntity(payload.cageId, farmId, payload.code, payload.doeCapacity),
                )
            }
            "rabbit.nest_box_created.v1" -> {
                val payload = json.decodeFromJsonElement(CreateRabbitNestBox.serializer(), event.payload)
                database.rabbitProgramme().upsertBox(
                    RabbitNestBoxEntity(payload.nestBoxId, farmId, payload.cageId, payload.code, "available"),
                )
            }
            "rabbit.wave_created.v1" -> {
                val payload = json.decodeFromJsonElement(CreateRabbitWave.serializer(), event.payload)
                val dates = KudbatSemiIntensiveExcel.schedule(payload.matingEpochDay)
                database.rabbitProgramme().upsertWave(
                    RabbitWaveEntity(
                        id = payload.waveId,
                        farmId = farmId,
                        cageId = payload.cageId,
                        packId = KudbatSemiIntensiveExcel.PACK_ID,
                        doeCount = payload.doeCount,
                        matingEpochDay = dates.matingEpochDay,
                        nestInEpochDay = dates.nestInEpochDay,
                        kindlingEpochDay = dates.kindlingEpochDay,
                        nestOutEpochDay = dates.nestOutEpochDay,
                        rebreedEpochDay = dates.rebreedEpochDay,
                        weanEpochDay = dates.weanEpochDay,
                    ),
                )
                listOf(
                    payload.placeTaskId to ("NEST_BOX_PLACE" to (dates.nestInEpochDay to "Place nest box")),
                    payload.kindlingTaskId to ("EXPECTED_KINDLING" to (dates.kindlingEpochDay to "Watch for kindling")),
                    payload.removeTaskId to ("NEST_BOX_REMOVE" to (dates.nestOutEpochDay to "Remove nest box")),
                    payload.rebreedTaskId to ("REBREED" to (dates.rebreedEpochDay to "Rebreed")),
                    payload.weanTaskId to ("WEAN" to (dates.weanEpochDay to "Wean kits")),
                ).forEach { (taskId, spec) ->
                    database.tasks().upsertFromServer(
                        TaskEntity(
                            id = taskId,
                            farmId = farmId,
                            moduleCode = "rabbit",
                            taskCode = spec.first,
                            title = spec.second.second,
                            dueOnEpochDay = spec.second.first,
                            status = "open",
                            animalId = null,
                            cageId = payload.cageId,
                            waveId = payload.waveId,
                            updatedAtEpochMillis = recordedAt,
                        ),
                    )
                }
            }
            "health.observation_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordHealthObservation.serializer(), event.payload)
                database.healthObservations().upsertFromServer(
                    HealthObservationEntity(
                        id = payload.observationId,
                        farmId = farmId,
                        animalId = payload.animalId,
                        speciesCode = payload.speciesCode,
                        signs = payload.signs,
                        firstAidApplied = payload.firstAidApplied,
                        redFlag = payload.redFlag,
                        occurredAtEpochMillis = payload.occurredAtEpochMillis,
                    ),
                )
            }
            "money.recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordMoney.serializer(), event.payload)
                database.money().upsertFromServer(
                    MoneyRecordEntity(
                        id = payload.recordId,
                        farmId = farmId,
                        kind = payload.kind,
                        categoryCode = payload.categoryCode,
                        amountMinor = payload.amountMinor,
                        currency = payload.currency,
                        occurredEpochDay = payload.occurredEpochDay,
                        note = payload.note,
                    ),
                )
            }
            "inventory.item_created.v1" -> {
                val payload = json.decodeFromJsonElement(CreateInventoryItem.serializer(), event.payload)
                database.inventory().upsertItem(
                    InventoryItemEntity(
                        id = payload.itemId,
                        farmId = farmId,
                        sku = payload.sku,
                        name = payload.name,
                        unit = payload.unit,
                        quantityMilli = 0,
                        updatedAtEpochMillis = recordedAt,
                    ),
                )
            }
            "goat.famacha_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordGoatFamacha.serializer(), event.payload)
                database.famacha().upsertFromServer(
                    FamachaScoreEntity(payload.scoreId, farmId, payload.animalId, payload.score, payload.occurredEpochDay),
                )
            }
            "group.created.v1" -> {
                val payload = json.decodeFromJsonElement(CreateAnimalGroup.serializer(), event.payload)
                database.groups().upsertFromServer(
                    AnimalGroupEntity(payload.groupId, farmId, payload.speciesCode, payload.name, payload.headCount),
                )
            }
            "paddock.created.v1" -> {
                val payload = json.decodeFromJsonElement(CreatePaddock.serializer(), event.payload)
                database.paddocks().upsertFromServer(
                    PaddockEntity(payload.paddockId, farmId, payload.code, payload.displayName, payload.areaM2, payload.waterSource, payload.shade, true),
                )
            }
            "grazing.started.v1" -> {
                val payload = json.decodeFromJsonElement(StartGrazing.serializer(), event.payload)
                val group = database.groups().get(farmId, payload.groupId)
                database.grazing().upsertFromServer(
                    GrazingSessionEntity(payload.sessionId, farmId, payload.paddockId, payload.groupId, group?.speciesCode ?: "goat", payload.enteredEpochDay, null, payload.headCount),
                )
            }
            "grazing.ended.v1" -> {
                val payload = json.decodeFromJsonElement(EndGrazing.serializer(), event.payload)
                database.grazing().end(farmId, payload.sessionId, payload.exitedEpochDay)
            }
            "labour.recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordLabour.serializer(), event.payload)
                database.labour().upsertFromServer(
                    LabourEntryEntity(payload.entryId, farmId, payload.workerName, payload.taskCode, payload.minutes, payload.occurredEpochDay, payload.note),
                )
            }
            "asset.created.v1" -> {
                val payload = json.decodeFromJsonElement(CreateFarmAsset.serializer(), event.payload)
                database.assets().upsertFromServer(
                    FarmAssetEntity(payload.assetId, farmId, payload.code, payload.name, payload.kind),
                )
            }
            "maintenance.recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordMaintenance.serializer(), event.payload)
                database.maintenance().upsertFromServer(
                    MaintenanceEventEntity(payload.eventId, farmId, payload.assetId, payload.title, payload.occurredEpochDay, payload.note),
                )
            }
            "feed.issued.v1" -> {
                val payload = json.decodeFromJsonElement(IssueFeed.serializer(), event.payload)
                database.feedIssues().upsertFromServer(
                    FeedIssueEntity(payload.issueId, farmId, payload.itemId, payload.groupId, payload.quantityMilli, payload.occurredEpochDay),
                )
                val inserted = database.inventory().insertMovement(
                    InventoryMovementEntity(payload.issueId, farmId, payload.itemId, "issue", payload.quantityMilli, recordedAt),
                )
                if (inserted != -1L) {
                    val item = database.inventory().item(farmId, payload.itemId)
                    if (item != null) {
                        database.inventory().setQuantity(farmId, payload.itemId, item.quantityMilli - payload.quantityMilli, recordedAt)
                    }
                }
            }
            "water.recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordWater.serializer(), event.payload)
                database.water().upsertFromServer(
                    WaterRecordEntity(payload.recordId, farmId, payload.source, payload.litresMilli, payload.occurredEpochDay),
                )
            }
            "sale.recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordSale.serializer(), event.payload)
                database.sales().upsertFromServer(
                    SaleRecordEntity(payload.saleId, farmId, payload.itemKind, payload.quantityMilli, payload.amountMinor, payload.currency, payload.occurredEpochDay),
                )
                database.money().upsertFromServer(
                    MoneyRecordEntity(payload.saleId, farmId, "income", "sales", payload.amountMinor, payload.currency, payload.occurredEpochDay, payload.itemKind),
                )
            }
            "formulary.item_created.v1" -> {
                val payload = json.decodeFromJsonElement(CreateFormularyItem.serializer(), event.payload)
                database.formulary().upsertFromServer(
                    FormularyItemEntity(payload.itemId, farmId, payload.productName, payload.speciesCode, payload.vetClass, payload.meatWithdrawalDays, payload.milkWithdrawalDays, payload.eggWithdrawalDays, payload.vetApproved),
                )
            }
            "health.treatment_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordHealthTreatment.serializer(), event.payload)
                val formulary = database.formulary().get(farmId, payload.formularyItemId)
                database.treatments().upsertFromServer(
                    HealthTreatmentEntity(
                        payload.treatmentId, farmId, payload.animalId, payload.speciesCode, payload.formularyItemId, payload.reason,
                        formulary?.meatWithdrawalDays, formulary?.milkWithdrawalDays, formulary?.eggWithdrawalDays, payload.occurredAtEpochMillis,
                    ),
                )
                applyWithdrawalWindows(
                    farmId = farmId,
                    treatmentId = payload.treatmentId,
                    product = formulary?.productName ?: payload.formularyItemId,
                    occurredEpochDay = payload.occurredAtEpochMillis / 86_400_000L,
                    meatDays = formulary?.meatWithdrawalDays,
                    milkDays = formulary?.milkWithdrawalDays,
                    eggDays = formulary?.eggWithdrawalDays,
                )
            }
            "sheep.joining_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordSheepJoining.serializer(), event.payload)
                database.lifecycle().upsertJoining(
                    SheepJoiningEntity(payload.joiningId, farmId, payload.groupId, payload.startedEpochDay),
                )
                applyJoiningTasks(farmId, payload, recordedAt)
            }
            "sheep.scanned.v1" -> {
                val payload = json.decodeFromJsonElement(RecordSheepScan.serializer(), event.payload)
                database.lifecycle().upsertScan(
                    SheepScanEntity(payload.scanId, farmId, payload.animalId, payload.result, payload.occurredEpochDay),
                )
            }
            "sheep.lambed.v1" -> {
                val payload = json.decodeFromJsonElement(RecordSheepLambing.serializer(), event.payload)
                database.lifecycle().upsertLambing(
                    SheepLambingEntity(payload.lambingId, farmId, payload.damAnimalId, payload.bornCount, payload.liveCount, payload.deadCount, payload.occurredEpochDay),
                )
            }
            "cattle.service_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordCattleService.serializer(), event.payload)
                database.lifecycle().upsertService(
                    CattleServiceEntity(payload.serviceId, farmId, payload.animalId, payload.method, payload.occurredEpochDay),
                )
                applyCattleServiceTasks(farmId, payload, recordedAt)
            }
            "cattle.pd_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordCattlePd.serializer(), event.payload)
                database.lifecycle().upsertPd(
                    CattlePdEntity(payload.pdId, farmId, payload.animalId, payload.result, payload.occurredEpochDay),
                )
            }
            "cattle.calved.v1" -> {
                val payload = json.decodeFromJsonElement(RecordCattleCalving.serializer(), event.payload)
                database.lifecycle().upsertCalving(
                    CattleCalvingEntity(payload.calvingId, farmId, payload.damAnimalId, payload.bornCount, payload.liveCount, payload.deadCount, payload.occurredEpochDay),
                )
            }
            "rabbit.palpated.v1" -> {
                val payload = json.decodeFromJsonElement(RecordRabbitPalpation.serializer(), event.payload)
                database.lifecycle().upsertPalpation(
                    RabbitPalpationEntity(payload.palpationId, farmId, payload.waveId, payload.result, payload.occurredEpochDay),
                )
            }
            "rabbit.kindled.v1" -> {
                val payload = json.decodeFromJsonElement(RecordRabbitKindling.serializer(), event.payload)
                database.lifecycle().upsertKindling(
                    RabbitKindlingEntity(payload.kindlingId, farmId, payload.waveId, payload.liveCount, payload.deadCount, payload.occurredEpochDay),
                )
            }
            "rabbit.fostered.v1" -> {
                val payload = json.decodeFromJsonElement(RecordRabbitFoster.serializer(), event.payload)
                val within = event.payload.jsonObject["withinWindow"]?.jsonPrimitive?.content == "true"
                database.lifecycle().upsertFoster(
                    RabbitFosterEntity(payload.fosterId, farmId, payload.fromWaveId, payload.toWaveId, payload.kitCount, within, payload.occurredEpochDay),
                )
            }
            "supplier.created.v1" -> {
                val payload = json.decodeFromJsonElement(CreateSupplier.serializer(), event.payload)
                database.lifecycle().upsertSupplier(
                    SupplierEntity(payload.supplierId, farmId, payload.name, payload.leadTimeDays),
                )
            }
            "purchase.recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordPurchase.serializer(), event.payload)
                database.lifecycle().upsertPurchase(
                    PurchaseEntity(payload.purchaseId, farmId, payload.supplierId, payload.itemId, payload.quantityMilli, payload.amountMinor, payload.currency, payload.occurredEpochDay),
                )
                val inserted = database.inventory().insertMovement(
                    InventoryMovementEntity(payload.purchaseId, farmId, payload.itemId, "receive", payload.quantityMilli, recordedAt),
                )
                if (inserted != -1L) {
                    val item = database.inventory().item(farmId, payload.itemId)
                    if (item != null) {
                        database.inventory().setQuantity(farmId, payload.itemId, item.quantityMilli + payload.quantityMilli, recordedAt)
                    }
                }
                database.money().upsertFromServer(
                    MoneyRecordEntity(payload.purchaseId, farmId, "expense", "purchase", payload.amountMinor, payload.currency, payload.occurredEpochDay, "purchase"),
                )
            }
            "goat.milk_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordGoatMilk.serializer(), event.payload)
                database.lifecycle().upsertMilk(
                    GoatMilkEntity(payload.milkId, farmId, payload.animalId, payload.litresMilli, payload.occurredEpochDay),
                )
            }
            "rabbit.nest_box_status_changed.v1" -> {
                val payload = json.decodeFromJsonElement(SetRabbitNestBoxStatus.serializer(), event.payload)
                database.rabbitProgramme().updateBoxStatus(farmId, payload.nestBoxId, payload.status)
            }
            "rabbit.weaned.v1" -> {
                val payload = json.decodeFromJsonElement(RecordRabbitWean.serializer(), event.payload)
                database.lifecycle().upsertWean(
                    RabbitWeanEntity(payload.weanId, farmId, payload.waveId, payload.weanedCount, payload.occurredEpochDay),
                )
            }
            "sheep.marked.v1" -> {
                val payload = json.decodeFromJsonElement(RecordSheepMarking.serializer(), event.payload)
                database.lifecycle().upsertMarking(
                    SheepMarkingEntity(payload.markingId, farmId, payload.groupId, payload.animalId, payload.markedCount, payload.occurredEpochDay),
                )
            }
            "sheep.weaned.v1" -> {
                val payload = json.decodeFromJsonElement(RecordSheepWeaning.serializer(), event.payload)
                database.lifecycle().upsertWeaning(
                    SheepWeaningEntity(payload.weaningId, farmId, payload.groupId, payload.animalId, payload.weanedCount, payload.occurredEpochDay),
                )
            }
            "cattle.bcs_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordCattleBcs.serializer(), event.payload)
                database.lifecycle().upsertBcs(
                    CattleBcsEntity(payload.scoreId, farmId, payload.animalId, payload.scale, payload.scoreTenths, payload.occurredEpochDay),
                )
            }
            "rabbit.kit_registered.v1" -> {
                val payload = json.decodeFromJsonElement(RegisterRabbitKit.serializer(), event.payload)
                database.lifecycle().upsertKit(
                    RabbitKitEntity(payload.kitId, farmId, payload.waveId, null, payload.tempLabel, payload.sex, "alive", "undecided", null),
                )
            }
            "rabbit.kit_promoted.v1" -> {
                val payload = json.decodeFromJsonElement(PromoteRabbitKit.serializer(), event.payload)
                val kit = database.lifecycle().kit(farmId, payload.kitId)
                if (kit != null) {
                    database.lifecycle().upsertKit(kit.copy(animalId = payload.animalId, earTag = payload.tag, retention = "keep_breeder"))
                }
                val sex = payload.sex ?: kit?.sex?.let { if (it == "female") "FEMALE" else if (it == "male") "MALE" else null } ?: "FEMALE"
                database.animals().upsertFromServer(
                    AnimalEntity(payload.animalId, farmId, payload.tag, null, "rabbit", sex, "active", null, null, recordedAt),
                )
            }
            "rabbit.retention_decided.v1" -> {
                val payload = json.decodeFromJsonElement(DecideRabbitRetention.serializer(), event.payload)
                database.lifecycle().upsertRetention(
                    RabbitRetentionEntity(payload.decisionId, farmId, payload.kitId, payload.decision, payload.occurredEpochDay),
                )
                val kit = database.lifecycle().kit(farmId, payload.kitId)
                if (kit != null) database.lifecycle().upsertKit(kit.copy(retention = payload.decision))
            }
            "rabbit.waitlist_enqueued.v1" -> {
                val payload = json.decodeFromJsonElement(EnqueueRabbitWaitlist.serializer(), event.payload)
                database.lifecycle().upsertWaitlist(
                    RabbitWaitlistEntity(payload.waitlistId, farmId, payload.contactName, payload.desiredSex, payload.qty, "open", null),
                )
            }
            "rabbit.waitlist_fulfilled.v1" -> {
                val payload = json.decodeFromJsonElement(FulfillRabbitWaitlist.serializer(), event.payload)
                val row = database.lifecycle().waitlist(farmId).firstOrNull { it.id == payload.waitlistId }
                if (row != null) {
                    database.lifecycle().upsertWaitlist(row.copy(status = "fulfilled", matchedKitId = payload.kitId))
                }
            }
            "rabbit.contract_agreed.v1" -> {
                val payload = json.decodeFromJsonElement(AgreeRabbitContract.serializer(), event.payload)
                database.lifecycle().upsertContract(
                    RabbitContractEntity(payload.contractId, farmId, payload.waitlistId, payload.buyerName, payload.animalId, payload.amountMinor, payload.currency, "agreed", payload.occurredEpochDay),
                )
                database.sales().upsertFromServer(
                    SaleRecordEntity(payload.contractId, farmId, "live_rabbit", 1000, payload.amountMinor, payload.currency, payload.occurredEpochDay),
                )
                database.money().upsertFromServer(
                    MoneyRecordEntity(payload.contractId, farmId, "income", "sales", payload.amountMinor, payload.currency, payload.occurredEpochDay, "rabbit contract"),
                )
            }
            "rabbit.market_plan_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordRabbitMarketPlan.serializer(), event.payload)
                database.lifecycle().upsertPlan(
                    RabbitMarketPlanEntity(payload.planId, farmId, payload.kitId, payload.waveId, payload.targetWeightGrams, payload.targetEpochDay, payload.purpose, "active"),
                )
            }
            "goat.bcs_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordGoatBcs.serializer(), event.payload)
                database.lifecycle().upsertGoatBcs(
                    GoatBcsEntity(payload.scoreId, farmId, payload.animalId, payload.scoreTenths, payload.occurredEpochDay),
                )
            }
            "cattle.locomotion_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordCattleLocomotion.serializer(), event.payload)
                database.lifecycle().upsertLocomotion(
                    CattleLocomotionEntity(payload.scoreId, farmId, payload.animalId, payload.score, payload.occurredEpochDay),
                )
            }
            "cattle.scc_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordCattleScc.serializer(), event.payload)
                database.lifecycle().upsertScc(
                    CattleSccEntity(payload.recordId, farmId, payload.animalId, payload.cellsPerMl, payload.dimDays, payload.occurredEpochDay),
                )
            }
            "sheep.sheared.v1" -> {
                val payload = json.decodeFromJsonElement(RecordSheepShearing.serializer(), event.payload)
                database.lifecycle().upsertShearing(
                    SheepShearingEntity(payload.eventId, farmId, payload.animalId, payload.groupId, payload.kind, payload.greasyGrams, payload.occurredEpochDay),
                )
            }
            "cattle.milk_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordCattleMilk.serializer(), event.payload)
                database.lifecycle().upsertCattleMilk(
                    CattleMilkEntity(payload.milkId, farmId, payload.animalId, payload.litresMilli, payload.occurredEpochDay),
                )
            }
            "sheep.dag_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordSheepDag.serializer(), event.payload)
                database.lifecycle().upsertDag(
                    SheepDagEntity(payload.scoreId, farmId, payload.animalId, payload.score, payload.occurredEpochDay),
                )
            }
            "sheep.footrot_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordSheepFootrot.serializer(), event.payload)
                database.lifecycle().upsertFootrot(
                    SheepFootrotEntity(payload.scoreId, farmId, payload.animalId, payload.score, payload.occurredEpochDay),
                )
            }
            "poultry.house_created.v1" -> {
                val payload = json.decodeFromJsonElement(CreatePoultryHouse.serializer(), event.payload)
                database.lifecycle().upsertHouse(
                    PoultryHouseEntity(payload.houseId, farmId, payload.code, payload.kind, payload.poultryKindCode),
                )
            }
            "poultry.eggs_set.v1" -> {
                val payload = json.decodeFromJsonElement(SetPoultryHatch.serializer(), event.payload)
                val days = requireNotNull(PoultryKindIncubation.days(payload.poultryKindCode, payload.incubationDays))
                database.lifecycle().upsertHatch(
                    PoultryHatchEntity(
                        payload.hatchId, farmId, payload.poultryKindCode, payload.houseId, payload.groupId,
                        payload.eggsSet, days, payload.setEpochDay, "set", null, null, null, null, null, null,
                    ),
                )
                applyHatchTasks(farmId, payload, days, recordedAt)
            }
            "poultry.hatch_candled.v1" -> {
                val payload = json.decodeFromJsonElement(CandlePoultryHatch.serializer(), event.payload)
                val existing = database.lifecycle().hatch(farmId, payload.hatchId)
                if (existing != null) {
                    database.lifecycle().upsertHatch(
                        existing.copy(status = "candled", fertile = payload.fertile, infertile = payload.infertile, midDead = payload.midDead),
                    )
                }
            }
            "poultry.hatch_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordPoultryHatch.serializer(), event.payload)
                val existing = database.lifecycle().hatch(farmId, payload.hatchId)
                if (existing != null) {
                    database.lifecycle().upsertHatch(
                        existing.copy(status = "hatched", hatched = payload.hatched, culls = payload.culls, placementGroupId = payload.placementGroupId),
                    )
                }
            }
            "sheep.flystrike_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordSheepFlystrike.serializer(), event.payload)
                database.lifecycle().upsertFlystrike(
                    SheepFlystrikeEntity(payload.scoreId, farmId, payload.animalId, payload.score, payload.region, payload.occurredEpochDay),
                )
            }
            "rabbit.mating_outcome_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordRabbitMatingOutcome.serializer(), event.payload)
                database.lifecycle().upsertMatingOutcome(
                    RabbitMatingOutcomeEntity(payload.outcomeId, farmId, payload.waveId, payload.outcome, payload.occurredEpochDay),
                )
            }
            "goat.scc_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordGoatScc.serializer(), event.payload)
                database.lifecycle().upsertGoatScc(
                    GoatSccEntity(payload.recordId, farmId, payload.animalId, payload.cellsPerMl, payload.dimDays, payload.occurredEpochDay),
                )
            }
            "rabbit.bedding_bound.v1" -> {
                val payload = json.decodeFromJsonElement(BindRabbitBedding.serializer(), event.payload)
                database.lifecycle().upsertInventoryLink(
                    RabbitInventoryLinkEntity(farmId, payload.beddingItemId, payload.beddingQtyMilli, payload.feedItemId, true),
                )
            }
            "poultry.vaccination_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordPoultryVaccination.serializer(), event.payload)
                database.lifecycle().upsertVaccination(
                    PoultryVaccinationEntity(payload.vaccinationId, farmId, payload.groupId, payload.poultryKindCode, payload.formularyItemId, payload.occurredEpochDay),
                )
            }
            "cattle.dryoff_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordCattleDryOff.serializer(), event.payload)
                database.lifecycle().upsertDryOff(
                    CattleDryOffEntity(payload.dryOffId, farmId, payload.animalId, payload.occurredEpochDay, payload.expectedCalvingEpochDay),
                )
            }
            "poultry.flock_placed.v1" -> {
                val payload = json.decodeFromJsonElement(PlacePoultryFlock.serializer(), event.payload)
                database.lifecycle().upsertPlacement(
                    PoultryPlacementEntity(payload.placementId, farmId, payload.groupId, payload.houseId, payload.poultryKindCode, payload.headCount, payload.occurredEpochDay),
                )
                database.tasks().upsertFromServer(
                    TaskEntity(payload.inspectTaskId, farmId, "poultry", "BIOSECURITY", "Placement inspection / biosecurity", payload.occurredEpochDay, "open", null, null, null, recordedAt),
                )
                database.tasks().upsertFromServer(
                    TaskEntity(payload.vaxTaskId, farmId, "poultry", "FLOCK_VAX", "Kind vaccination pack", payload.occurredEpochDay + 1, "open", null, null, null, recordedAt),
                )
            }
            "poultry.biosecurity_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordPoultryBiosecurity.serializer(), event.payload)
                database.lifecycle().upsertBiosecurity(
                    PoultryBiosecurityEntity(payload.walkId, farmId, payload.houseId, payload.groupId, payload.findings, payload.mixedSpecies, payload.occurredEpochDay),
                )
            }
            "sheep.wool_clipped.v1" -> {
                val payload = json.decodeFromJsonElement(RecordSheepWool.serializer(), event.payload)
                database.lifecycle().upsertWool(
                    SheepWoolEntity(payload.clipId, farmId, payload.animalId, payload.groupId, payload.greasyGrams, payload.occurredEpochDay),
                )
            }
            "health.pack_accepted.v1" -> {
                val payload = json.decodeFromJsonElement(AcceptHealthPack.serializer(), event.payload)
                database.lifecycle().upsertPack(
                    HealthPackEntity(payload.packId, farmId, payload.speciesCode, payload.name, "vet_accepted", payload.acceptedByVet),
                )
            }
            "poultry.flock_day_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordPoultryFlockDay.serializer(), event.payload)
                database.poultryFlockDays().upsertFromServer(
                    PoultryFlockDayEntity(payload.dayId, farmId, payload.groupId, payload.eggs, payload.dead, payload.culls, payload.feedGrams, payload.occurredEpochDay),
                )
            }
            "inventory.moved.v1" -> {
                val payload = json.decodeFromJsonElement(MoveInventory.serializer(), event.payload)
                val inserted = database.inventory().insertMovement(
                    InventoryMovementEntity(
                        id = payload.movementId,
                        farmId = farmId,
                        itemId = payload.itemId,
                        direction = payload.direction,
                        quantityMilli = payload.quantityMilli,
                        occurredAtEpochMillis = payload.occurredAtEpochMillis,
                    ),
                )
                if (inserted != -1L) {
                    val item = database.inventory().item(farmId, payload.itemId) ?: return
                    val next = if (payload.direction == "receive") {
                        item.quantityMilli + payload.quantityMilli
                    } else {
                        item.quantityMilli - payload.quantityMilli
                    }
                    database.inventory().setQuantity(farmId, payload.itemId, next, recordedAt)
                }
            }
            "goat.heat_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordGoatHeat.serializer(), event.payload)
                database.lifecycle().upsertHeat(GoatHeatEntity(payload.heatId, farmId, payload.animalId, payload.occurredEpochDay))
            }
            "goat.mating_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordGoatMating.serializer(), event.payload)
                database.lifecycle().upsertMating(
                    GoatMatingEntity(payload.matingId, farmId, payload.damId, payload.sireId, payload.method, payload.occurredEpochDay),
                )
                database.tasks().upsertFromServer(
                    TaskEntity(payload.pregCheckTaskId, farmId, "goat", "PREG_CHECK", "Pregnancy check", payload.occurredEpochDay + 45, "open", payload.damId, null, null, recordedAt),
                )
            }
            "goat.pregnancy_checked.v1" -> {
                val payload = json.decodeFromJsonElement(RecordGoatPregnancy.serializer(), event.payload)
                database.lifecycle().upsertPregnancy(
                    GoatPregnancyEntity(payload.checkId, farmId, payload.animalId, payload.result, payload.occurredEpochDay),
                )
            }
            "sheep.famacha_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordFamacha.serializer(), event.payload)
                database.famacha().upsertFromServer(
                    FamachaScoreEntity(payload.scoreId, farmId, payload.animalId, payload.score, payload.occurredEpochDay),
                )
            }
            "animal.identifier_assigned.v1" -> {
                val payload = json.decodeFromJsonElement(AssignAnimalIdentifier.serializer(), event.payload)
                database.lifecycle().upsertIdentifier(
                    AnimalIdentifierEntity(payload.identifierId, farmId, payload.animalId, payload.type, payload.value, true, payload.occurredEpochDay),
                )
            }
            "official.movement_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordOfficialMovement.serializer(), event.payload)
                val species = database.animals().get(farmId, payload.animalId)?.speciesCode ?: "cattle"
                database.lifecycle().upsertMovement(
                    OfficialMovementEntity(payload.movementId, farmId, payload.animalId, species, payload.direction, payload.fromPlace, payload.toPlace, payload.occurredEpochDay),
                )
            }
            "inventory.lot_received.v1" -> {
                val payload = json.decodeFromJsonElement(ReceiveInventoryLot.serializer(), event.payload)
                val inserted = database.lifecycle().insertLotIfMissing(
                    InventoryLotEntity(payload.lotId, farmId, payload.itemId, payload.lotCode, payload.expiresEpochDay, payload.quantityMilli),
                )
                if (inserted != -1L) {
                    val item = database.inventory().item(farmId, payload.itemId) ?: return
                    database.inventory().setQuantity(farmId, payload.itemId, item.quantityMilli + payload.quantityMilli, recordedAt)
                }
            }
            "inventory.lot_issued.v1" -> {
                val payload = json.decodeFromJsonElement(IssueInventoryLot.serializer(), event.payload)
                val inserted = database.inventory().insertMovement(
                    InventoryMovementEntity(payload.issueId, farmId, payload.itemId, "issue", payload.quantityMilli, recordedAt),
                )
                if (inserted != -1L) {
                    consumeLotsFefo(database, farmId, payload.itemId, payload.quantityMilli, recordedAt)
                }
            }
            "health.vet_visit_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordVetVisit.serializer(), event.payload)
                database.lifecycle().upsertVetVisit(
                    VetVisitEntity(payload.visitId, farmId, payload.speciesCode, payload.animalId, payload.groupId, payload.reason, payload.attendingVet, payload.occurredEpochDay),
                )
            }
            "health.lab_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordLabResult.serializer(), event.payload)
                database.lifecycle().upsertLab(
                    LabResultEntity(payload.resultId, farmId, payload.animalId, payload.groupId, payload.testName, payload.resultText, payload.cellsPerMl, payload.occurredEpochDay),
                )
            }
            "cattle.weaning_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordCattleWeaning.serializer(), event.payload)
                database.lifecycle().upsertCattleWeaning(
                    CattleWeaningEntity(payload.weaningId, farmId, payload.animalId, payload.groupId, payload.weightGrams, payload.occurredEpochDay),
                )
            }
            "sheep.micron_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordSheepMicron.serializer(), event.payload)
                database.lifecycle().upsertMicron(
                    SheepMicronEntity(payload.testId, farmId, payload.animalId, payload.groupId, payload.micronTenths, payload.occurredEpochDay),
                )
            }
            "poultry.kind_enabled.v1" -> {
                val payload = json.decodeFromJsonElement(EnablePoultryKind.serializer(), event.payload)
                database.lifecycle().upsertEnabledKind(EnabledPoultryKindEntity(farmId, payload.poultryKindCode))
            }
            "rabbit.gi_stasis_flagged.v1" -> {
                val payload = json.decodeFromJsonElement(RecordRabbitGiStasis.serializer(), event.payload)
                database.lifecycle().upsertGiStasis(
                    RabbitGiStasisEntity(payload.flagId, farmId, payload.animalId, payload.signs, payload.occurredEpochDay),
                )
                database.healthObservations().upsertFromServer(
                    HealthObservationEntity(
                        id = payload.flagId,
                        farmId = farmId,
                        animalId = payload.animalId,
                        speciesCode = "rabbit",
                        signs = payload.signs,
                        firstAidApplied = null,
                        redFlag = true,
                        occurredAtEpochMillis = payload.occurredEpochDay * 86_400_000L,
                    ),
                )
                database.tasks().upsertFromServer(
                    TaskEntity(payload.taskId, farmId, "rabbit", "GI_STASIS", "GI stasis red flag. Call the vet.", payload.occurredEpochDay, "open", payload.animalId, null, null, recordedAt),
                )
            }
            "pedigree.linked.v1" -> {
                val payload = json.decodeFromJsonElement(LinkPedigree.serializer(), event.payload)
                database.lifecycle().upsertPedigree(
                    PedigreeRelationEntity(payload.linkId, farmId, payload.animalId, payload.parentId, payload.relationType),
                )
            }
            "health.pack_slot_added.v1" -> {
                val payload = json.decodeFromJsonElement(AddHealthPackSlot.serializer(), event.payload)
                database.lifecycle().upsertPackSlot(
                    HealthPackSlotEntity(payload.slotId, farmId, payload.packId, payload.slotCode, payload.title, payload.offsetDays, payload.fromEvent, payload.isCore),
                )
            }
            "health.pack_applied.v1" -> {
                val payload = json.decodeFromJsonElement(ApplyHealthPack.serializer(), event.payload)
                database.lifecycle().upsertPackApply(
                    HealthPackApplyEntity(payload.applyId, farmId, payload.packId, payload.animalId, payload.groupId, payload.anchorEpochDay),
                )
                val pack = database.lifecycle().packs(farmId).firstOrNull { it.id == payload.packId }
                for (slot in database.lifecycle().coreSlots(farmId, payload.packId)) {
                    database.tasks().upsertFromServer(
                        TaskEntity(
                            "${payload.applyId}:${slot.id}", farmId, pack?.speciesCode ?: "goat", "PACK_SLOT", slot.title,
                            payload.anchorEpochDay + slot.offsetDays, "open", payload.animalId, null, null, recordedAt,
                        ),
                    )
                }
            }
            "cattle.lot_placed.v1" -> {
                val payload = json.decodeFromJsonElement(PlaceCattleLot.serializer(), event.payload)
                database.lifecycle().upsertLotPlace(
                    CattleLotPlacementEntity(payload.placementId, farmId, payload.groupId, payload.headCount, payload.placedEpochDay),
                )
            }
            "cattle.dof_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordCattleDaysOnFeed.serializer(), event.payload)
                database.lifecycle().upsertDof(
                    CattleDofEntity(payload.recordId, farmId, payload.groupId, payload.daysOnFeed, payload.occurredEpochDay),
                )
            }
            "cattle.lot_closed.v1" -> {
                val payload = json.decodeFromJsonElement(CloseCattleLot.serializer(), event.payload)
                database.lifecycle().upsertLotClose(
                    CattleLotCloseEntity(payload.closeoutId, farmId, payload.groupId, payload.headOut, payload.weightGrams, payload.daysOnFeed, payload.occurredEpochDay),
                )
            }
            "goat.kid_registered.v1" -> {
                val payload = json.decodeFromJsonElement(com.farmos.domain.goat.RegisterGoatKid.serializer(), event.payload)
                val existing = database.animals().get(farmId, payload.animalId)
                database.animals().upsertFromServer(
                    AnimalEntity(
                        id = payload.animalId,
                        farmId = farmId,
                        tag = payload.tag,
                        name = payload.name,
                        speciesCode = "goat",
                        sex = payload.sex.name,
                        status = existing?.status ?: "active",
                        dateOfBirthEpochDay = payload.dateOfBirthEpochDay,
                        updatedAtEpochMillis = recordedAt,
                    ),
                )
                val damId = database.kidding().get(farmId, payload.kiddingId)?.damId
                if (damId != null) {
                    database.lifecycle().upsertKid(
                        com.farmos.core.database.GoatKidEntity(payload.animalId, farmId, payload.animalId, payload.kiddingId, damId),
                    )
                    database.lifecycle().upsertPedigree(
                        PedigreeRelationEntity(payload.pedigreeLinkId, farmId, payload.animalId, damId, "dam"),
                    )
                }
            }
            "goat.lactation_planned.v1" -> {
                val payload = json.decodeFromJsonElement(com.farmos.domain.goat.PlanGoatLactation.serializer(), event.payload)
                database.lifecycle().upsertLactation(
                    GoatLactationPlanEntity(payload.planId, farmId, payload.animalId, payload.kiddingId, payload.occurredEpochDay),
                )
                database.tasks().upsertFromServer(
                    TaskEntity(payload.checkTaskId, farmId, "goat", "LACTATION_CHECK", "Lactation follow-up", payload.occurredEpochDay + 7, "open", payload.animalId, null, null, recordedAt),
                )
            }
            "inventory.reorder_set.v1" -> {
                val payload = json.decodeFromJsonElement(SetInventoryReorder.serializer(), event.payload)
                database.inventory().setReorder(farmId, payload.itemId, payload.reorderMilli, recordedAt)
            }
            "inventory.reorder_alerted.v1" -> {
                val payload = json.decodeFromJsonElement(RecordReorderAlert.serializer(), event.payload)
                val item = database.inventory().item(farmId, payload.itemId)
                database.lifecycle().upsertReorderAlert(
                    ReorderAlertEntity(payload.alertId, farmId, payload.itemId, item?.quantityMilli ?: 0, item?.reorderMilli ?: 0, payload.occurredEpochDay),
                )
            }
            "group.census_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordGroupCensus.serializer(), event.payload)
                database.lifecycle().upsertCensus(
                    GroupCensusEntity(payload.censusId, farmId, payload.groupId, payload.headCount, payload.occurredEpochDay),
                )
                database.groups().setHeadCount(farmId, payload.groupId, payload.headCount)
            }
            else -> throw UnsupportedServerEvent(
                "Unsupported event ${event.eventType} schema=${event.schemaVersion}; cursor was not advanced",
            )
        }
    }

    private suspend fun applyJoiningTasks(farmId: String, payload: RecordSheepJoining, recordedAt: Long) {
        listOf(
            Triple(payload.scanTaskId, "SCAN", payload.startedEpochDay + 70L to "Pregnancy scanning"),
            Triple(payload.preLambTaskId, "PRE_LAMB", payload.startedEpochDay + 140L to "Pre-lambing vaccination / nutrition"),
            Triple(payload.paddockTaskId, "LAMBING_PADDOCK", payload.startedEpochDay + 140L to "Lambing paddock set-up"),
            Triple(payload.lambingTaskId, "EXPECTED_LAMBING", payload.startedEpochDay + 147L to "Expected lambing start"),
        ).forEach { (taskId, code, dueAndTitle) ->
            database.tasks().upsertFromServer(
                TaskEntity(taskId, farmId, "sheep", code, dueAndTitle.second, dueAndTitle.first, "open", null, null, null, recordedAt),
            )
        }
    }

    private suspend fun applyHatchTasks(farmId: String, payload: SetPoultryHatch, days: Int, recordedAt: Long) {
        val candleDay = payload.setEpochDay + PoultryKindIncubation.candlingLeadDays(payload.poultryKindCode)
        val hatchDay = payload.setEpochDay + days
        listOf(
            Triple(payload.candleTaskId, "CANDLING", candleDay to "Candling"),
            Triple(payload.lockTaskId, "LOCKDOWN", (hatchDay - PoultryKindIncubation.LOCKDOWN_LEAD_DAYS) to "Transfer / lock-down"),
            Triple(payload.hatchTaskId, "EXPECTED_HATCH", hatchDay to "Expected hatch"),
        ).forEach { (taskId, code, dueAndTitle) ->
            database.tasks().upsertFromServer(
                TaskEntity(taskId, farmId, "poultry", code, dueAndTitle.second, dueAndTitle.first, "open", null, null, null, recordedAt),
            )
        }
    }

    private suspend fun applyCattleServiceTasks(farmId: String, payload: RecordCattleService, recordedAt: Long) {
        listOf(
            Triple(payload.pdTaskId, "PD", payload.occurredEpochDay + 32L to "Pregnancy diagnosis (PD)"),
            Triple(payload.paddockTaskId, "CALVING_PADDOCK", payload.occurredEpochDay + 259L to "Calving paddock / close-up pen"),
            Triple(payload.calvingTaskId, "EXPECTED_CALVING", payload.occurredEpochDay + 280L to "Expected calving"),
        ).forEach { (taskId, code, dueAndTitle) ->
            database.tasks().upsertFromServer(
                TaskEntity(taskId, farmId, "cattle", code, dueAndTitle.second, dueAndTitle.first, "open", payload.animalId, null, null, recordedAt),
            )
        }
    }

    private suspend fun applyWithdrawalWindows(
        farmId: String,
        treatmentId: String,
        product: String,
        occurredEpochDay: Long,
        meatDays: Int?,
        milkDays: Int?,
        eggDays: Int?,
    ) {
        listOf(
            Triple("meat", meatDays, "$treatmentId:meat"),
            Triple("milk", milkDays, "$treatmentId:milk"),
            Triple("egg", eggDays, "$treatmentId:egg"),
        ).forEach { (kind, days, id) ->
            if (days != null) {
                database.lifecycle().upsertWithdrawal(
                    WithdrawalWindowEntity(id, farmId, treatmentId, product, kind, occurredEpochDay + days),
                )
            }
        }
    }
}

data class PullPageResult(
    val appliedEvents: Int,
    val pages: Int,
    val finalCursor: Long,
)
