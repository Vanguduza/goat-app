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

class RoomOpsRepository(
    private val database: FarmOsDatabase,
    private val farmId: String,
    private val json: Json = Json { encodeDefaults = true },
) {
    suspend fun createTask(command: CreateFarmTask, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.task(command)?.let { error(it) }
        enqueue(context, "task.create.v1", "task", command.taskId, 0, json.encodeToString(command)) {
            database.tasks().insert(command.toEntity(farmId, context.occurredAtEpochMillis, "open"))
        }
        return LocalCommandResult(context.mutationId, command.taskId, true)
    }

    suspend fun completeTask(command: CompleteFarmTask, context: LocalCommandContext): LocalCommandResult {
        enqueue(
            context,
            "task.complete.v1",
            "task",
            command.taskId,
            expectedVersion("task", command.taskId),
            json.encodeToString(command),
        ) {
            database.tasks().updateStatus(farmId, command.taskId, "done", context.occurredAtEpochMillis)
        }
        return LocalCommandResult(context.mutationId, command.taskId, true)
    }

    suspend fun openTasks() = database.tasks().openForFarm(farmId)

    suspend fun completedTasks(limit: Int = 100) = database.tasks().completedForFarm(farmId, limit)

    suspend fun createCage(command: CreateRabbitCage, context: LocalCommandContext): LocalCommandResult {
        RabbitProgrammeValidator.cage(command)?.let { error(it) }
        enqueue(context, "rabbit.cage_create.v1", "rabbit_cage", command.cageId, 0, json.encodeToString(command)) {
            database.rabbitProgramme().insertCage(
                RabbitCageEntity(command.cageId, farmId, command.code.trim(), command.doeCapacity),
            )
        }
        return LocalCommandResult(context.mutationId, command.cageId, true)
    }

    suspend fun createNestBox(command: CreateRabbitNestBox, context: LocalCommandContext): LocalCommandResult {
        RabbitProgrammeValidator.nestBox(command)?.let { error(it) }
        enqueue(context, "rabbit.nest_box_create.v1", "rabbit_nest_box", command.nestBoxId, 0, json.encodeToString(command)) {
            database.rabbitProgramme().insertBox(
                RabbitNestBoxEntity(command.nestBoxId, farmId, command.cageId, command.code.trim(), "available"),
            )
        }
        return LocalCommandResult(context.mutationId, command.nestBoxId, true)
    }

    suspend fun createWave(command: CreateRabbitWave, context: LocalCommandContext): LocalCommandResult {
        val boxes = database.rabbitProgramme().availableBoxes(farmId, command.cageId)
        RabbitProgrammeValidator.wave(command, boxes)?.let { error(it) }
        val dates = KudbatSemiIntensiveExcel.schedule(command.matingEpochDay)
        enqueue(context, "rabbit.wave_create.v1", "rabbit_wave", command.waveId, 0, json.encodeToString(command)) {
            database.rabbitProgramme().insertWave(
                RabbitWaveEntity(
                    id = command.waveId,
                    farmId = farmId,
                    cageId = command.cageId,
                    packId = KudbatSemiIntensiveExcel.PACK_ID,
                    doeCount = command.doeCount,
                    matingEpochDay = dates.matingEpochDay,
                    nestInEpochDay = dates.nestInEpochDay,
                    kindlingEpochDay = dates.kindlingEpochDay,
                    nestOutEpochDay = dates.nestOutEpochDay,
                    rebreedEpochDay = dates.rebreedEpochDay,
                    weanEpochDay = dates.weanEpochDay,
                ),
            )
            listOf(
                Triple(command.placeTaskId, "NEST_BOX_PLACE", dates.nestInEpochDay to "Place nest box"),
                Triple(command.kindlingTaskId, "EXPECTED_KINDLING", dates.kindlingEpochDay to "Watch for kindling"),
                Triple(command.removeTaskId, "NEST_BOX_REMOVE", dates.nestOutEpochDay to "Remove nest box"),
                Triple(command.rebreedTaskId, "REBREED", dates.rebreedEpochDay to "Rebreed"),
                Triple(command.weanTaskId, "WEAN", dates.weanEpochDay to "Wean kits"),
            ).forEach { (taskId, code, due) ->
                database.tasks().insert(
                    TaskEntity(
                        id = taskId,
                        farmId = farmId,
                        moduleCode = "rabbit",
                        taskCode = code,
                        title = due.second,
                        dueOnEpochDay = due.first,
                        status = "open",
                        animalId = null,
                        cageId = command.cageId,
                        waveId = command.waveId,
                        updatedAtEpochMillis = context.occurredAtEpochMillis,
                    ),
                )
            }
        }
        return LocalCommandResult(context.mutationId, command.waveId, true)
    }

    suspend fun cages() = database.rabbitProgramme().cages(farmId)
    suspend fun waves() = database.rabbitProgramme().waves(farmId)
    suspend fun nestBoxes() = database.rabbitProgramme().boxes(farmId)
    suspend fun availableBoxes(cageId: String) = database.rabbitProgramme().availableBoxes(farmId, cageId)

    suspend fun setNestBoxStatus(command: SetRabbitNestBoxStatus, context: LocalCommandContext): LocalCommandResult {
        RabbitProgrammeValidator.nestStatus(command)?.let { error(it) }
        val box = requireNotNull(database.rabbitProgramme().boxes(farmId).firstOrNull { it.id == command.nestBoxId }) {
            "Nest box not found"
        }
        if (!nestTransitionAllowed(box.status, command.status)) {
            error("Nest box status change is not allowed")
        }
        enqueue(context, "rabbit.nest_box_set_status.v1", "rabbit_nest_box", command.nestBoxId, expectedVersion("rabbit_nest_box", command.nestBoxId), json.encodeToString(command)) {
            database.rabbitProgramme().updateBoxStatus(farmId, command.nestBoxId, command.status)
        }
        return LocalCommandResult(context.mutationId, command.nestBoxId, true)
    }

    suspend fun recordWean(command: RecordRabbitWean, context: LocalCommandContext): LocalCommandResult {
        RabbitProgrammeValidator.wean(command)?.let { error(it) }
        enqueue(context, "rabbit.record_wean.v1", "rabbit_wave", command.waveId, expectedVersion("rabbit_wave", command.waveId), json.encodeToString(command)) {
            database.lifecycle().insertWean(RabbitWeanEntity(command.weanId, farmId, command.waveId, command.weanedCount, command.occurredEpochDay))
        }
        return LocalCommandResult(context.mutationId, command.weanId, true)
    }

    suspend fun recordMarking(command: RecordSheepMarking, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.marking(command)?.let { error(it) }
        val aggregateType = if (command.animalId.isNullOrBlank()) "animal_group" else "animal"
        val aggregateId = command.animalId ?: command.groupId!!
        enqueue(context, "sheep.record_marking.v1", aggregateType, aggregateId, expectedVersion(aggregateType, aggregateId), json.encodeToString(command)) {
            database.lifecycle().insertMarking(SheepMarkingEntity(command.markingId, farmId, command.groupId, command.animalId, command.markedCount, command.occurredEpochDay))
        }
        return LocalCommandResult(context.mutationId, command.markingId, true)
    }

    suspend fun recordSheepWeaning(command: RecordSheepWeaning, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.sheepWeaning(command)?.let { error(it) }
        val aggregateType = if (command.animalId.isNullOrBlank()) "animal_group" else "animal"
        val aggregateId = command.animalId ?: command.groupId!!
        enqueue(context, "sheep.record_weaning.v1", aggregateType, aggregateId, expectedVersion(aggregateType, aggregateId), json.encodeToString(command)) {
            database.lifecycle().insertWeaning(SheepWeaningEntity(command.weaningId, farmId, command.groupId, command.animalId, command.weanedCount, command.occurredEpochDay))
        }
        return LocalCommandResult(context.mutationId, command.weaningId, true)
    }

    suspend fun recordCattleBcs(command: RecordCattleBcs, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.cattleBcs(command)?.let { error(it) }
        enqueue(context, "cattle.record_bcs.v1", "animal", command.animalId, expectedVersion("animal", command.animalId), json.encodeToString(command)) {
            database.lifecycle().insertBcs(CattleBcsEntity(command.scoreId, farmId, command.animalId, command.scale, command.scoreTenths, command.occurredEpochDay))
        }
        return LocalCommandResult(context.mutationId, command.scoreId, true)
    }

    suspend fun recordWool(command: RecordSheepWool, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.wool(command)?.let { error(it) }
        val aggregateType = if (command.animalId.isNullOrBlank()) "animal_group" else "animal"
        val aggregateId = command.animalId ?: command.groupId!!
        enqueue(context, "sheep.record_wool.v1", aggregateType, aggregateId, expectedVersion(aggregateType, aggregateId), json.encodeToString(command)) {
            database.lifecycle().insertWool(SheepWoolEntity(command.clipId, farmId, command.animalId, command.groupId, command.greasyGrams, command.occurredEpochDay))
        }
        return LocalCommandResult(context.mutationId, command.clipId, true)
    }

    suspend fun recordCattleMilk(command: RecordCattleMilk, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.cattleMilk(command)?.let { error(it) }
        val cow = requireNotNull(database.animals().get(farmId, command.animalId)) { "Active cow not found" }
        require(cow.speciesCode == "cattle" && cow.sex == "FEMALE" && cow.status == "active") { "Active cow not found" }
        enqueue(context, "cattle.record_milk.v1", "animal", command.animalId, expectedVersion("animal", command.animalId), json.encodeToString(command)) {
            database.lifecycle().insertCattleMilk(CattleMilkEntity(command.milkId, farmId, command.animalId, command.litresMilli, command.occurredEpochDay))
        }
        return LocalCommandResult(context.mutationId, command.milkId, true)
    }

    suspend fun recordDag(command: RecordSheepDag, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.dag(command)?.let { error(it) }
        enqueue(context, "sheep.record_dag.v1", "animal", command.animalId, expectedVersion("animal", command.animalId), json.encodeToString(command)) {
            database.lifecycle().insertDag(SheepDagEntity(command.scoreId, farmId, command.animalId, command.score, command.occurredEpochDay))
        }
        return LocalCommandResult(context.mutationId, command.scoreId, true)
    }

    suspend fun recordFootrot(command: RecordSheepFootrot, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.footrot(command)?.let { error(it) }
        enqueue(context, "sheep.record_footrot.v1", "animal", command.animalId, expectedVersion("animal", command.animalId), json.encodeToString(command)) {
            database.lifecycle().insertFootrot(SheepFootrotEntity(command.scoreId, farmId, command.animalId, command.score, command.occurredEpochDay))
        }
        return LocalCommandResult(context.mutationId, command.scoreId, true)
    }

    suspend fun registerKit(command: RegisterRabbitKit, context: LocalCommandContext): LocalCommandResult {
        RabbitProgrammeValidator.kit(command)?.let { error(it) }
        enqueue(context, "rabbit.kit_register.v1", "rabbit_wave", command.waveId, expectedVersion("rabbit_wave", command.waveId), json.encodeToString(command)) {
            database.lifecycle().insertKit(
                RabbitKitEntity(command.kitId, farmId, command.waveId, null, command.tempLabel.trim(), command.sex, "alive", "undecided", null),
            )
        }
        return LocalCommandResult(context.mutationId, command.kitId, true)
    }

    suspend fun promoteKit(command: PromoteRabbitKit, context: LocalCommandContext): LocalCommandResult {
        RabbitProgrammeValidator.promote(command)?.let { error(it) }
        val kit = requireNotNull(database.lifecycle().kit(farmId, command.kitId)) { "Alive unpromoted kit not found" }
        require(kit.status == "alive" && kit.animalId == null) { "Alive unpromoted kit not found" }
        val sex = command.sex ?: if (kit.sex == "female") "FEMALE" else if (kit.sex == "male") "MALE" else error("Promote needs doe or buck sex")
        enqueue(context, "rabbit.kit_promote.v1", "animal", command.animalId, 0, json.encodeToString(command)) {
            database.animals().insert(
                AnimalEntity(command.animalId, farmId, command.tag.trim(), null, "rabbit", sex, "active", null, null, context.occurredAtEpochMillis),
            )
            database.lifecycle().upsertKit(kit.copy(animalId = command.animalId, earTag = command.tag.trim(), retention = "keep_breeder"))
        }
        return LocalCommandResult(context.mutationId, command.animalId, true)
    }

    suspend fun decideRetention(command: DecideRabbitRetention, context: LocalCommandContext): LocalCommandResult {
        RabbitProgrammeValidator.retention(command)?.let { error(it) }
        val kit = requireNotNull(database.lifecycle().kit(farmId, command.kitId)) { "Kit not found" }
        enqueue(context, "rabbit.retention_decide.v1", "rabbit_kit", command.kitId, expectedVersion("rabbit_kit", command.kitId), json.encodeToString(command)) {
            database.lifecycle().insertRetention(
                RabbitRetentionEntity(command.decisionId, farmId, command.kitId, command.decision, command.occurredEpochDay),
            )
            database.lifecycle().upsertKit(kit.copy(retention = command.decision))
        }
        return LocalCommandResult(context.mutationId, command.decisionId, true)
    }

    suspend fun enqueueWaitlist(command: EnqueueRabbitWaitlist, context: LocalCommandContext): LocalCommandResult {
        RabbitProgrammeValidator.waitlist(command)?.let { error(it) }
        enqueue(context, "rabbit.waitlist_enqueue.v1", "rabbit_waitlist", command.waitlistId, 0, json.encodeToString(command)) {
            database.lifecycle().insertWaitlist(
                RabbitWaitlistEntity(command.waitlistId, farmId, command.contactName.trim(), command.desiredSex, command.qty, "open", null),
            )
        }
        return LocalCommandResult(context.mutationId, command.waitlistId, true)
    }

    suspend fun fulfillWaitlist(command: FulfillRabbitWaitlist, context: LocalCommandContext): LocalCommandResult {
        val row = requireNotNull(database.lifecycle().waitlist(farmId).firstOrNull { it.id == command.waitlistId && it.status == "open" }) {
            "Open waitlist row not found"
        }
        val kit = requireNotNull(database.lifecycle().kit(farmId, command.kitId)) { "Waitlist match needs a living kit marked sale_pet" }
        require(kit.status == "alive" && kit.retention == "sale_pet") { "Waitlist match needs a living kit marked sale_pet" }
        enqueue(context, "rabbit.waitlist_fulfill.v1", "rabbit_waitlist", command.waitlistId, expectedVersion("rabbit_waitlist", command.waitlistId), json.encodeToString(command)) {
            database.lifecycle().upsertWaitlist(row.copy(status = "fulfilled", matchedKitId = command.kitId))
        }
        return LocalCommandResult(context.mutationId, command.waitlistId, true)
    }

    suspend fun agreeContract(command: AgreeRabbitContract, context: LocalCommandContext): LocalCommandResult {
        RabbitProgrammeValidator.contract(command)?.let { error(it) }
        enqueue(context, "rabbit.contract_agree.v1", "rabbit_contract", command.contractId, 0, json.encodeToString(command)) {
            database.lifecycle().insertContract(
                RabbitContractEntity(command.contractId, farmId, command.waitlistId, command.buyerName.trim(), command.animalId, command.amountMinor, command.currency, "agreed", command.occurredEpochDay),
            )
            database.sales().insert(SaleRecordEntity(command.contractId, farmId, "live_rabbit", 1000, command.amountMinor, command.currency, command.occurredEpochDay))
            database.money().insert(MoneyRecordEntity(command.contractId, farmId, "income", "sales", command.amountMinor, command.currency, command.occurredEpochDay, "rabbit contract"))
        }
        return LocalCommandResult(context.mutationId, command.contractId, true)
    }

    suspend fun recordPlan(command: RecordRabbitMarketPlan, context: LocalCommandContext): LocalCommandResult {
        RabbitProgrammeValidator.plan(command)?.let { error(it) }
        enqueue(context, "rabbit.market_plan.v1", "rabbit_market_plan", command.planId, 0, json.encodeToString(command)) {
            database.lifecycle().insertPlan(
                RabbitMarketPlanEntity(command.planId, farmId, command.kitId, command.waveId, command.targetWeightGrams, command.targetEpochDay, command.purpose, "active"),
            )
        }
        return LocalCommandResult(context.mutationId, command.planId, true)
    }

    suspend fun recordLocomotion(command: RecordCattleLocomotion, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.locomotion(command)?.let { error(it) }
        enqueue(context, "cattle.record_locomotion.v1", "animal", command.animalId, expectedVersion("animal", command.animalId), json.encodeToString(command)) {
            database.lifecycle().insertLocomotion(CattleLocomotionEntity(command.scoreId, farmId, command.animalId, command.score, command.occurredEpochDay))
        }
        return LocalCommandResult(context.mutationId, command.scoreId, true)
    }

    suspend fun recordScc(command: RecordCattleScc, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.scc(command)?.let { error(it) }
        enqueue(context, "cattle.record_scc.v1", "animal", command.animalId, expectedVersion("animal", command.animalId), json.encodeToString(command)) {
            database.lifecycle().insertScc(CattleSccEntity(command.recordId, farmId, command.animalId, command.cellsPerMl, command.dimDays, command.occurredEpochDay))
        }
        return LocalCommandResult(context.mutationId, command.recordId, true)
    }

    suspend fun recordShearing(command: RecordSheepShearing, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.shearing(command)?.let { error(it) }
        val aggregateType = if (command.animalId.isNullOrBlank()) "animal_group" else "animal"
        val aggregateId = command.animalId ?: command.groupId!!
        enqueue(context, "sheep.record_shearing.v1", aggregateType, aggregateId, expectedVersion(aggregateType, aggregateId), json.encodeToString(command)) {
            database.lifecycle().insertShearing(
                SheepShearingEntity(command.eventId, farmId, command.animalId, command.groupId, command.kind, command.greasyGrams, command.occurredEpochDay),
            )
        }
        return LocalCommandResult(context.mutationId, command.eventId, true)
    }

    suspend fun createHouse(command: CreatePoultryHouse, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.house(command)?.let { error(it) }
        enqueue(context, "poultry.house_create.v1", "poultry_house", command.houseId, 0, json.encodeToString(command)) {
            database.lifecycle().insertHouse(
                PoultryHouseEntity(command.houseId, farmId, command.code.trim(), command.kind, command.poultryKindCode),
            )
        }
        return LocalCommandResult(context.mutationId, command.houseId, true)
    }

    suspend fun setHatch(command: SetPoultryHatch, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.hatchSet(command)?.let { error(it) }
        val days = requireNotNull(PoultryKindIncubation.days(command.poultryKindCode, command.incubationDays))
        enqueue(context, "poultry.hatch_set.v1", "poultry_hatch", command.hatchId, 0, json.encodeToString(command)) {
            database.lifecycle().insertHatch(
                PoultryHatchEntity(
                    command.hatchId, farmId, command.poultryKindCode, command.houseId, command.groupId,
                    command.eggsSet, days, command.setEpochDay, "set", null, null, null, null, null, null,
                ),
            )
            val candleDay = command.setEpochDay + PoultryKindIncubation.candlingLeadDays(command.poultryKindCode)
            val hatchDay = command.setEpochDay + days
            database.tasks().insert(TaskEntity(command.candleTaskId, farmId, "poultry", "CANDLING", "Candling", candleDay, "open", null, null, null, context.occurredAtEpochMillis))
            database.tasks().insert(TaskEntity(command.lockTaskId, farmId, "poultry", "LOCKDOWN", "Transfer / lock-down", hatchDay - PoultryKindIncubation.LOCKDOWN_LEAD_DAYS, "open", null, null, null, context.occurredAtEpochMillis))
            database.tasks().insert(TaskEntity(command.hatchTaskId, farmId, "poultry", "EXPECTED_HATCH", "Expected hatch", hatchDay, "open", null, null, null, context.occurredAtEpochMillis))
        }
        return LocalCommandResult(context.mutationId, command.hatchId, true)
    }

    suspend fun candleHatch(command: CandlePoultryHatch, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.hatchCandle(command)?.let { error(it) }
        val hatch = requireNotNull(database.lifecycle().hatch(farmId, command.hatchId)) { "Candling needs a set hatch" }
        require(hatch.status == "set") { "Candling needs a set hatch" }
        require(command.fertile + command.infertile + command.midDead == hatch.eggsSet) { "Candling counts must add up to eggs set" }
        enqueue(context, "poultry.hatch_candle.v1", "poultry_hatch", command.hatchId, expectedVersion("poultry_hatch", command.hatchId), json.encodeToString(command)) {
            database.lifecycle().upsertHatch(
                hatch.copy(status = "candled", fertile = command.fertile, infertile = command.infertile, midDead = command.midDead),
            )
        }
        return LocalCommandResult(context.mutationId, command.hatchId, true)
    }

    suspend fun recordHatch(command: RecordPoultryHatch, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.hatchRecord(command)?.let { error(it) }
        val hatch = requireNotNull(database.lifecycle().hatch(farmId, command.hatchId)) { "Hatch record needs a candled hatch" }
        require(hatch.status == "candled") { "Hatch record needs a candled hatch" }
        require(command.hatched + command.culls <= (hatch.fertile ?: hatch.eggsSet)) { "Hatched and cull counts cannot exceed fertile eggs" }
        enqueue(context, "poultry.hatch_record.v1", "poultry_hatch", command.hatchId, expectedVersion("poultry_hatch", command.hatchId), json.encodeToString(command)) {
            database.lifecycle().upsertHatch(
                hatch.copy(status = "hatched", hatched = command.hatched, culls = command.culls, placementGroupId = command.placementGroupId),
            )
        }
        return LocalCommandResult(context.mutationId, command.hatchId, true)
    }

    suspend fun recordFlystrike(command: RecordSheepFlystrike, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.flystrike(command)?.let { error(it) }
        enqueue(context, "sheep.record_flystrike.v1", "animal", command.animalId, expectedVersion("animal", command.animalId), json.encodeToString(command)) {
            database.lifecycle().insertFlystrike(
                SheepFlystrikeEntity(command.scoreId, farmId, command.animalId, command.score, command.region, command.occurredEpochDay),
            )
        }
        return LocalCommandResult(context.mutationId, command.scoreId, true)
    }

    suspend fun recordMatingOutcome(command: RecordRabbitMatingOutcome, context: LocalCommandContext): LocalCommandResult {
        RabbitProgrammeValidator.matingOutcome(command)?.let { error(it) }
        enqueue(context, "rabbit.record_mating_outcome.v1", "rabbit_wave", command.waveId, expectedVersion("rabbit_wave", command.waveId), json.encodeToString(command)) {
            database.lifecycle().insertMatingOutcome(
                RabbitMatingOutcomeEntity(command.outcomeId, farmId, command.waveId, command.outcome, command.occurredEpochDay),
            )
        }
        return LocalCommandResult(context.mutationId, command.outcomeId, true)
    }

    suspend fun bindBedding(command: BindRabbitBedding, context: LocalCommandContext): LocalCommandResult {
        RabbitProgrammeValidator.bedding(command)?.let { error(it) }
        val beddingItemId = command.beddingItemId
        if (!beddingItemId.isNullOrBlank()) {
            requireNotNull(database.inventory().item(farmId, beddingItemId)) { "Bedding item not found on this farm" }
        }
        val feedItemId = command.feedItemId
        if (!feedItemId.isNullOrBlank()) {
            requireNotNull(database.inventory().item(farmId, feedItemId)) { "Feed item not found on this farm" }
        }
        enqueue(context, "rabbit.bedding_bind.v1", "farm", farmId, expectedVersion("farm", farmId), json.encodeToString(command)) {
            database.lifecycle().upsertInventoryLink(
                RabbitInventoryLinkEntity(farmId, command.beddingItemId, command.beddingQtyMilli, command.feedItemId, true),
            )
        }
        return LocalCommandResult(context.mutationId, farmId, true)
    }

    suspend fun recordVaccination(command: RecordPoultryVaccination, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.vaccination(command)?.let { error(it) }
        val formulary = requireNotNull(database.formulary().get(farmId, command.formularyItemId)) {
            "Vaccination needs a vet-approved poultry formulary item"
        }
        require(formulary.vetApproved && formulary.speciesCode == "poultry") {
            "Vaccination needs a vet-approved poultry formulary item"
        }
        enqueue(context, "poultry.record_vaccination.v1", "animal_group", command.groupId, expectedVersion("animal_group", command.groupId), json.encodeToString(command)) {
            database.lifecycle().insertVaccination(
                PoultryVaccinationEntity(command.vaccinationId, farmId, command.groupId, command.poultryKindCode, command.formularyItemId, command.occurredEpochDay),
            )
        }
        return LocalCommandResult(context.mutationId, command.vaccinationId, true)
    }

    suspend fun recordDryOff(command: RecordCattleDryOff, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.dryOff(command)?.let { error(it) }
        enqueue(context, "cattle.record_dryoff.v1", "animal", command.animalId, expectedVersion("animal", command.animalId), json.encodeToString(command)) {
            database.lifecycle().insertDryOff(
                CattleDryOffEntity(command.dryOffId, farmId, command.animalId, command.occurredEpochDay, command.expectedCalvingEpochDay),
            )
        }
        return LocalCommandResult(context.mutationId, command.dryOffId, true)
    }

    suspend fun kits() = database.lifecycle().kits(farmId)
    suspend fun waitlist() = database.lifecycle().waitlist(farmId)
    suspend fun houses() = database.lifecycle().houses(farmId)
    suspend fun hatches() = database.lifecycle().hatches(farmId)
    suspend fun placeFlock(command: PlacePoultryFlock, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.flockPlace(command)?.let { error(it) }
        enqueue(context, "poultry.flock_place.v1", "animal_group", command.groupId, expectedVersion("animal_group", command.groupId), json.encodeToString(command)) {
            database.lifecycle().insertPlacement(
                PoultryPlacementEntity(command.placementId, farmId, command.groupId, command.houseId, command.poultryKindCode, command.headCount, command.occurredEpochDay),
            )
            database.tasks().insert(TaskEntity(command.inspectTaskId, farmId, "poultry", "BIOSECURITY", "Placement inspection / biosecurity", command.occurredEpochDay, "open", null, null, null, context.occurredAtEpochMillis))
            database.tasks().insert(TaskEntity(command.vaxTaskId, farmId, "poultry", "FLOCK_VAX", "Kind vaccination pack", command.occurredEpochDay + 1, "open", null, null, null, context.occurredAtEpochMillis))
        }
        return LocalCommandResult(context.mutationId, command.placementId, true)
    }

    suspend fun recordBiosecurity(command: RecordPoultryBiosecurity, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.biosecurity(command)?.let { error(it) }
        val aggregateId = command.houseId ?: command.groupId!!
        enqueue(context, "poultry.record_biosecurity.v1", "poultry_house", aggregateId, expectedVersion("poultry_house", aggregateId), json.encodeToString(command)) {
            database.lifecycle().insertBiosecurity(
                PoultryBiosecurityEntity(command.walkId, farmId, command.houseId, command.groupId, command.findings.trim(), command.mixedSpecies, command.occurredEpochDay),
            )
        }
        return LocalCommandResult(context.mutationId, command.walkId, true)
    }

    suspend fun recordSheepFamacha(command: RecordFamacha, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.sheepFamacha(command)?.let { error(it) }
        val sheep = requireNotNull(database.animals().get(farmId, command.animalId)) { "FAMACHA needs a sheep" }
        require(sheep.speciesCode == "sheep") { "FAMACHA needs a sheep" }
        enqueue(context, "sheep.record_famacha.v1", "animal", command.animalId, expectedVersion("animal", command.animalId), json.encodeToString(command)) {
            database.famacha().insert(FamachaScoreEntity(command.scoreId, farmId, command.animalId, command.score, command.occurredEpochDay))
        }
        return LocalCommandResult(context.mutationId, command.scoreId, true)
    }

    suspend fun assignIdentifier(command: AssignAnimalIdentifier, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.identifier(command)?.let { error(it) }
        requireNotNull(database.animals().get(farmId, command.animalId)) { "Identifier needs an animal on this farm" }
        enqueue(context, "animal.identifier_assign.v1", "animal", command.animalId, expectedVersion("animal", command.animalId), json.encodeToString(command)) {
            database.lifecycle().insertIdentifier(
                AnimalIdentifierEntity(command.identifierId, farmId, command.animalId, command.type, command.value.trim(), true, command.occurredEpochDay),
            )
        }
        return LocalCommandResult(context.mutationId, command.identifierId, true)
    }

    suspend fun recordOfficialMovement(command: RecordOfficialMovement, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.movement(command)?.let { error(it) }
        val animal = requireNotNull(database.animals().get(farmId, command.animalId)) { "Official movement needs a cattle or sheep record" }
        require(animal.speciesCode == "cattle" || animal.speciesCode == "sheep") { "Official movement needs a cattle or sheep record" }
        enqueue(context, "official.record_movement.v1", "animal", command.animalId, expectedVersion("animal", command.animalId), json.encodeToString(command)) {
            database.lifecycle().insertMovement(
                OfficialMovementEntity(command.movementId, farmId, command.animalId, animal.speciesCode, command.direction, command.fromPlace, command.toPlace, command.occurredEpochDay),
            )
        }
        return LocalCommandResult(context.mutationId, command.movementId, true)
    }

    suspend fun receiveLot(command: ReceiveInventoryLot, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.lotReceive(command)?.let { error(it) }
        val item = requireNotNull(database.inventory().item(farmId, command.itemId)) { "Inventory item not found" }
        enqueue(context, "inventory.lot_receive.v1", "inventory_item", command.itemId, expectedVersion("inventory_item", command.itemId), json.encodeToString(command)) {
            database.lifecycle().insertLot(
                InventoryLotEntity(command.lotId, farmId, command.itemId, command.lotCode.trim(), command.expiresEpochDay, command.quantityMilli),
            )
            database.inventory().setQuantity(farmId, command.itemId, item.quantityMilli + command.quantityMilli, context.occurredAtEpochMillis)
        }
        return LocalCommandResult(context.mutationId, command.lotId, true)
    }

    suspend fun issueLot(command: IssueInventoryLot, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.lotIssue(command)?.let { error(it) }
        enqueue(context, "inventory.lot_issue.v1", "inventory_item", command.itemId, expectedVersion("inventory_item", command.itemId), json.encodeToString(command)) {
            val inserted = database.inventory().insertMovement(
                InventoryMovementEntity(command.issueId, farmId, command.itemId, "issue", command.quantityMilli, context.occurredAtEpochMillis),
            )
            require(inserted != -1L) { "Lot issue already recorded" }
            consumeLotsFefo(database, farmId, command.itemId, command.quantityMilli, context.occurredAtEpochMillis)
        }
        return LocalCommandResult(context.mutationId, command.issueId, true)
    }

    suspend fun recordVetVisit(command: RecordVetVisit, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.vetVisit(command)?.let { error(it) }
        enqueue(context, "health.record_vet_visit.v1", "vet_visit", command.visitId, 0, json.encodeToString(command)) {
            database.lifecycle().insertVetVisit(
                VetVisitEntity(command.visitId, farmId, command.speciesCode, command.animalId, command.groupId, command.reason.trim(), command.attendingVet.trim(), command.occurredEpochDay),
            )
        }
        return LocalCommandResult(context.mutationId, command.visitId, true)
    }

    suspend fun recordLab(command: RecordLabResult, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.lab(command)?.let { error(it) }
        enqueue(context, "health.record_lab.v1", "lab_result", command.resultId, 0, json.encodeToString(command)) {
            database.lifecycle().insertLab(
                LabResultEntity(command.resultId, farmId, command.animalId, command.groupId, command.testName.trim(), command.resultText.trim(), command.cellsPerMl, command.occurredEpochDay),
            )
        }
        return LocalCommandResult(context.mutationId, command.resultId, true)
    }

    suspend fun recordCattleWeaning(command: RecordCattleWeaning, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.cattleWeaning(command)?.let { error(it) }
        val animalId = command.animalId
        val aggregateId = animalId ?: command.groupId!!
        if (animalId != null) {
            val calf = requireNotNull(database.animals().get(farmId, animalId)) { "Cattle weaning needs a calf" }
            require(calf.speciesCode == "cattle") { "Cattle weaning needs a calf" }
        }
        enqueue(context, "cattle.record_weaning.v1", "animal", aggregateId, expectedVersion("animal", aggregateId), json.encodeToString(command)) {
            database.lifecycle().insertCattleWeaning(
                CattleWeaningEntity(command.weaningId, farmId, command.animalId, command.groupId, command.weightGrams, command.occurredEpochDay),
            )
        }
        return LocalCommandResult(context.mutationId, command.weaningId, true)
    }

    suspend fun recordMicron(command: RecordSheepMicron, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.micron(command)?.let { error(it) }
        val aggregateId = command.animalId ?: command.groupId!!
        enqueue(context, "sheep.record_micron.v1", "animal", aggregateId, expectedVersion("animal", aggregateId), json.encodeToString(command)) {
            database.lifecycle().insertMicron(
                SheepMicronEntity(command.testId, farmId, command.animalId, command.groupId, command.micronTenths, command.occurredEpochDay),
            )
        }
        return LocalCommandResult(context.mutationId, command.testId, true)
    }

    suspend fun enablePoultryKind(command: EnablePoultryKind, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.enableKind(command)?.let { error(it) }
        enqueue(context, "poultry.kind_enable.v1", "farm", farmId, expectedVersion("farm", farmId), json.encodeToString(command)) {
            database.lifecycle().upsertEnabledKind(EnabledPoultryKindEntity(farmId, command.poultryKindCode))
        }
        return LocalCommandResult(context.mutationId, command.poultryKindCode, true)
    }

    suspend fun recordGiStasis(command: RecordRabbitGiStasis, context: LocalCommandContext): LocalCommandResult {
        RabbitProgrammeValidator.giStasis(command)?.let { error(it) }
        val rabbit = requireNotNull(database.animals().get(farmId, command.animalId)) { "GI stasis flag needs a rabbit" }
        require(rabbit.speciesCode == "rabbit") { "GI stasis flag needs a rabbit" }
        enqueue(context, "rabbit.record_gi_stasis.v1", "animal", command.animalId, expectedVersion("animal", command.animalId), json.encodeToString(command)) {
            database.lifecycle().insertGiStasis(
                RabbitGiStasisEntity(command.flagId, farmId, command.animalId, command.signs.trim(), command.occurredEpochDay),
            )
            database.healthObservations().insert(
                HealthObservationEntity(
                    id = command.flagId,
                    farmId = farmId,
                    animalId = command.animalId,
                    speciesCode = "rabbit",
                    signs = command.signs.trim(),
                    firstAidApplied = null,
                    redFlag = true,
                    occurredAtEpochMillis = command.occurredEpochDay * 86_400_000L,
                ),
            )
            database.tasks().insert(
                TaskEntity(command.taskId, farmId, "rabbit", "GI_STASIS", "GI stasis red flag. Call the vet.", command.occurredEpochDay, "open", command.animalId, null, null, context.occurredAtEpochMillis),
            )
        }
        return LocalCommandResult(context.mutationId, command.flagId, true)
    }

    suspend fun linkPedigree(command: LinkPedigree, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.pedigree(command)?.let { error(it) }
        val child = requireNotNull(database.animals().get(farmId, command.animalId)) { "Pedigree link needs two animals of the same species on this farm" }
        val parent = requireNotNull(database.animals().get(farmId, command.parentId)) { "Pedigree link needs two animals of the same species on this farm" }
        require(child.speciesCode == parent.speciesCode) { "Pedigree link needs two animals of the same species on this farm" }
        enqueue(context, "pedigree.link.v1", "animal", command.animalId, expectedVersion("animal", command.animalId), json.encodeToString(command)) {
            database.lifecycle().insertPedigree(
                PedigreeRelationEntity(command.linkId, farmId, command.animalId, command.parentId, command.relationType),
            )
        }
        return LocalCommandResult(context.mutationId, command.linkId, true)
    }

    suspend fun inventoryLink() = database.lifecycle().inventoryLink(farmId)
    suspend fun vaccinations() = database.lifecycle().vaccinations(farmId)
    suspend fun placements() = database.lifecycle().placements(farmId)
    suspend fun enabledPoultryKinds() = database.lifecycle().enabledPoultryKinds(farmId)

    suspend fun recordObservation(command: RecordHealthObservation, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.observation(command)?.let { error(it) }
        enqueue(context, "health.record_observation.v1", "health_observation", command.observationId, 0, json.encodeToString(command)) {
            database.healthObservations().insert(
                HealthObservationEntity(
                    id = command.observationId,
                    farmId = farmId,
                    animalId = command.animalId,
                    speciesCode = command.speciesCode,
                    signs = command.signs.trim(),
                    firstAidApplied = command.firstAidApplied,
                    redFlag = command.redFlag,
                    occurredAtEpochMillis = command.occurredAtEpochMillis,
                ),
            )
        }
        return LocalCommandResult(context.mutationId, command.observationId, true)
    }

    suspend fun recentObservations() = database.healthObservations().recent(farmId, 50)

    suspend fun recordMoney(command: RecordMoney, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.money(command)?.let { error(it) }
        enqueue(context, "money.record.v1", "money_record", command.recordId, 0, json.encodeToString(command)) {
            database.money().insert(
                MoneyRecordEntity(
                    id = command.recordId,
                    farmId = farmId,
                    kind = command.kind,
                    categoryCode = command.categoryCode,
                    amountMinor = command.amountMinor,
                    currency = command.currency,
                    occurredEpochDay = command.occurredEpochDay,
                    note = command.note,
                ),
            )
        }
        return LocalCommandResult(context.mutationId, command.recordId, true)
    }

    suspend fun recentMoney() = database.money().recent(farmId, 50)

    suspend fun createItem(command: CreateInventoryItem, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.inventoryItem(command)?.let { error(it) }
        enqueue(context, "inventory.item_create.v1", "inventory_item", command.itemId, 0, json.encodeToString(command)) {
            database.inventory().insertItem(
                InventoryItemEntity(
                    id = command.itemId,
                    farmId = farmId,
                    sku = command.sku.trim(),
                    name = command.name.trim(),
                    unit = command.unit,
                    quantityMilli = 0,
                    updatedAtEpochMillis = context.occurredAtEpochMillis,
                ),
            )
        }
        return LocalCommandResult(context.mutationId, command.itemId, true)
    }

    suspend fun move(command: MoveInventory, context: LocalCommandContext): LocalCommandResult {
        val item = requireNotNull(database.inventory().item(farmId, command.itemId)) { "Inventory item not found" }
        OpsValidator.inventoryMove(command, item.quantityMilli)?.let { error(it) }
        enqueue(
            context,
            "inventory.move.v1",
            "inventory_item",
            command.itemId,
            expectedVersion("inventory_item", command.itemId),
            json.encodeToString(command),
        ) {
            database.inventory().insertMovement(
                InventoryMovementEntity(
                    id = command.movementId,
                    farmId = farmId,
                    itemId = command.itemId,
                    direction = command.direction,
                    quantityMilli = command.quantityMilli,
                    occurredAtEpochMillis = command.occurredAtEpochMillis,
                ),
            )
            val next = if (command.direction == "receive") {
                item.quantityMilli + command.quantityMilli
            } else {
                item.quantityMilli - command.quantityMilli
            }
            database.inventory().setQuantity(farmId, command.itemId, next, context.occurredAtEpochMillis)
        }
        return LocalCommandResult(context.mutationId, command.movementId, true)
    }

    suspend fun items() = database.inventory().items(farmId)

    suspend fun createGroup(command: CreateAnimalGroup, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.group(command)?.let { error(it) }
        enqueue(context, "group.create.v1", "animal_group", command.groupId, 0, json.encodeToString(command)) {
            database.groups().insert(AnimalGroupEntity(command.groupId, farmId, command.speciesCode, command.name.trim(), command.headCount))
        }
        return LocalCommandResult(context.mutationId, command.groupId, true)
    }

    suspend fun groups() = database.groups().forFarm(farmId)

    suspend fun createPaddock(command: CreatePaddock, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.paddock(command)?.let { error(it) }
        enqueue(context, "paddock.create.v1", "paddock", command.paddockId, 0, json.encodeToString(command)) {
            database.paddocks().insert(
                PaddockEntity(command.paddockId, farmId, command.code.trim(), command.displayName.trim(), command.areaM2, command.waterSource, command.shade, true),
            )
        }
        return LocalCommandResult(context.mutationId, command.paddockId, true)
    }

    suspend fun paddocks() = database.paddocks().active(farmId)

    suspend fun startGrazing(command: StartGrazing, context: LocalCommandContext): LocalCommandResult {
        if (database.grazing().hasOpen(farmId, command.paddockId)) error("This paddock already has an open grazing session")
        val group = requireNotNull(database.groups().get(farmId, command.groupId)) { "Group not found" }
        enqueue(context, "grazing.start.v1", "grazing_session", command.sessionId, 0, json.encodeToString(command)) {
            database.grazing().insert(
                GrazingSessionEntity(command.sessionId, farmId, command.paddockId, command.groupId, group.speciesCode, command.enteredEpochDay, null, command.headCount),
            )
        }
        return LocalCommandResult(context.mutationId, command.sessionId, true)
    }

    suspend fun endGrazing(command: EndGrazing, context: LocalCommandContext): LocalCommandResult {
        enqueue(context, "grazing.end.v1", "grazing_session", command.sessionId, expectedVersion("grazing_session", command.sessionId), json.encodeToString(command)) {
            database.grazing().end(farmId, command.sessionId, command.exitedEpochDay)
        }
        return LocalCommandResult(context.mutationId, command.sessionId, true)
    }

    suspend fun openGrazing() = database.grazing().open(farmId)

    suspend fun recordLabour(command: RecordLabour, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.labour(command)?.let { error(it) }
        enqueue(context, "labour.record.v1", "labour_entry", command.entryId, 0, json.encodeToString(command)) {
            database.labour().insert(LabourEntryEntity(command.entryId, farmId, command.workerName.trim(), command.taskCode.trim(), command.minutes, command.occurredEpochDay, command.note))
        }
        return LocalCommandResult(context.mutationId, command.entryId, true)
    }

    suspend fun recentLabour() = database.labour().recent(farmId, 50)

    suspend fun createAsset(command: CreateFarmAsset, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.asset(command)?.let { error(it) }
        enqueue(context, "asset.create.v1", "farm_asset", command.assetId, 0, json.encodeToString(command)) {
            database.assets().insert(FarmAssetEntity(command.assetId, farmId, command.code.trim(), command.name.trim(), command.kind))
        }
        return LocalCommandResult(context.mutationId, command.assetId, true)
    }

    suspend fun assets() = database.assets().forFarm(farmId)

    suspend fun recordMaintenance(command: RecordMaintenance, context: LocalCommandContext): LocalCommandResult {
        enqueue(context, "maintenance.record.v1", "farm_asset", command.assetId, expectedVersion("farm_asset", command.assetId), json.encodeToString(command)) {
            database.maintenance().insert(MaintenanceEventEntity(command.eventId, farmId, command.assetId, command.title.trim(), command.occurredEpochDay, command.note))
        }
        return LocalCommandResult(context.mutationId, command.eventId, true)
    }

    suspend fun recentMaintenance() = database.maintenance().recent(farmId, 50)

    suspend fun issueFeed(command: IssueFeed, context: LocalCommandContext): LocalCommandResult {
        val item = requireNotNull(database.inventory().item(farmId, command.itemId)) { "Inventory item not found" }
        OpsValidator.feed(command, item.quantityMilli)?.let { error(it) }
        enqueue(context, "feed.issue.v1", "inventory_item", command.itemId, expectedVersion("inventory_item", command.itemId), json.encodeToString(command)) {
            database.feedIssues().insert(FeedIssueEntity(command.issueId, farmId, command.itemId, command.groupId, command.quantityMilli, command.occurredEpochDay))
            database.inventory().insertMovement(
                InventoryMovementEntity(command.issueId, farmId, command.itemId, "issue", command.quantityMilli, context.occurredAtEpochMillis),
            )
            database.inventory().setQuantity(farmId, command.itemId, item.quantityMilli - command.quantityMilli, context.occurredAtEpochMillis)
        }
        return LocalCommandResult(context.mutationId, command.issueId, true)
    }

    suspend fun recentFeed() = database.feedIssues().recent(farmId, 50)

    suspend fun recordWater(command: RecordWater, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.water(command)?.let { error(it) }
        enqueue(context, "water.record.v1", "water_record", command.recordId, 0, json.encodeToString(command)) {
            database.water().insert(WaterRecordEntity(command.recordId, farmId, command.source.trim(), command.litresMilli, command.occurredEpochDay))
        }
        return LocalCommandResult(context.mutationId, command.recordId, true)
    }

    suspend fun recentWater() = database.water().recent(farmId, 50)

    suspend fun recordSale(command: RecordSale, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.sale(command)?.let { error(it) }
        enqueue(context, "sale.record.v1", "sale_record", command.saleId, 0, json.encodeToString(command)) {
            database.sales().insert(SaleRecordEntity(command.saleId, farmId, command.itemKind.trim(), command.quantityMilli, command.amountMinor, command.currency, command.occurredEpochDay))
            database.money().insert(MoneyRecordEntity(command.saleId, farmId, "income", "sales", command.amountMinor, command.currency, command.occurredEpochDay, command.itemKind.trim()))
        }
        return LocalCommandResult(context.mutationId, command.saleId, true)
    }

    suspend fun recentSales() = database.sales().recent(farmId, 50)

    suspend fun createFormulary(command: CreateFormularyItem, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.formulary(command)?.let { error(it) }
        enqueue(context, "formulary.item_create.v1", "formulary_item", command.itemId, 0, json.encodeToString(command)) {
            database.formulary().insert(
                FormularyItemEntity(command.itemId, farmId, command.productName.trim(), command.speciesCode, command.vetClass, command.meatWithdrawalDays, command.milkWithdrawalDays, command.eggWithdrawalDays, true),
            )
        }
        return LocalCommandResult(context.mutationId, command.itemId, true)
    }

    suspend fun approvedFormulary() = database.formulary().approved(farmId)

    suspend fun recordTreatment(command: RecordHealthTreatment, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.treatment(command)?.let { error(it) }
        val formulary = requireNotNull(database.formulary().get(farmId, command.formularyItemId)) { "Vet-approved formulary item not found" }
        require(formulary.vetApproved) { "Treatment needs a vet-approved formulary item" }
        enqueue(context, "health.record_treatment.v1", "health_treatment", command.treatmentId, 0, json.encodeToString(command)) {
            database.treatments().insert(
                HealthTreatmentEntity(
                    command.treatmentId, farmId, command.animalId, command.speciesCode, command.formularyItemId, command.reason.trim(),
                    formulary.meatWithdrawalDays, formulary.milkWithdrawalDays, formulary.eggWithdrawalDays, command.occurredAtEpochMillis,
                ),
            )
            val occurredEpochDay = command.occurredAtEpochMillis / 86_400_000L
            listOf(
                Triple("meat", formulary.meatWithdrawalDays, "${command.treatmentId}:meat"),
                Triple("milk", formulary.milkWithdrawalDays, "${command.treatmentId}:milk"),
                Triple("egg", formulary.eggWithdrawalDays, "${command.treatmentId}:egg"),
            ).forEach { (kind, days, id) ->
                if (days != null) {
                    database.lifecycle().insertWithdrawal(
                        WithdrawalWindowEntity(id, farmId, command.treatmentId, formulary.productName, kind, occurredEpochDay + days),
                    )
                }
            }
        }
        return LocalCommandResult(context.mutationId, command.treatmentId, true)
    }

    suspend fun recentTreatments() = database.treatments().recent(farmId, 50)

    suspend fun recordFlockDay(command: RecordPoultryFlockDay, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.flockDay(command)?.let { error(it) }
        enqueue(context, "poultry.flock_day.v1", "animal_group", command.groupId, expectedVersion("animal_group", command.groupId), json.encodeToString(command)) {
            database.poultryFlockDays().insert(
                PoultryFlockDayEntity(command.dayId, farmId, command.groupId, command.eggs, command.dead, command.culls, command.feedGrams, command.occurredEpochDay),
            )
        }
        return LocalCommandResult(context.mutationId, command.dayId, true)
    }

    suspend fun recentFlockDays() = database.poultryFlockDays().recent(farmId, 50)

    suspend fun recordJoining(command: RecordSheepJoining, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.joining(command)?.let { error(it) }
        val group = requireNotNull(database.groups().get(farmId, command.groupId)) { "Joining needs a sheep mob" }
        require(group.speciesCode == "sheep") { "Joining needs a sheep mob" }
        enqueue(context, "sheep.record_joining.v1", "animal_group", command.groupId, expectedVersion("animal_group", command.groupId), json.encodeToString(command)) {
            database.lifecycle().insertJoining(SheepJoiningEntity(command.joiningId, farmId, command.groupId, command.startedEpochDay))
            database.tasks().insert(TaskEntity(command.scanTaskId, farmId, "sheep", "SCAN", "Pregnancy scanning", command.startedEpochDay + 70, "open", null, null, null, context.occurredAtEpochMillis))
            database.tasks().insert(TaskEntity(command.preLambTaskId, farmId, "sheep", "PRE_LAMB", "Pre-lambing vaccination / nutrition", command.startedEpochDay + 140, "open", null, null, null, context.occurredAtEpochMillis))
            database.tasks().insert(TaskEntity(command.paddockTaskId, farmId, "sheep", "LAMBING_PADDOCK", "Lambing paddock set-up", command.startedEpochDay + 140, "open", null, null, null, context.occurredAtEpochMillis))
            database.tasks().insert(TaskEntity(command.lambingTaskId, farmId, "sheep", "EXPECTED_LAMBING", "Expected lambing start", command.startedEpochDay + 147, "open", null, null, null, context.occurredAtEpochMillis))
        }
        return LocalCommandResult(context.mutationId, command.joiningId, true)
    }

    suspend fun recordScan(command: RecordSheepScan, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.scan(command)?.let { error(it) }
        val ewe = requireNotNull(database.animals().get(farmId, command.animalId)) { "Ewe not found" }
        require(ewe.speciesCode == "sheep") { "Ewe not found" }
        enqueue(context, "sheep.record_scan.v1", "animal", command.animalId, expectedVersion("animal", command.animalId), json.encodeToString(command)) {
            database.lifecycle().insertScan(SheepScanEntity(command.scanId, farmId, command.animalId, command.result, command.occurredEpochDay))
        }
        return LocalCommandResult(context.mutationId, command.scanId, true)
    }

    suspend fun recordLambing(command: RecordSheepLambing, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.lambing(command)?.let { error(it) }
        val ewe = requireNotNull(database.animals().get(farmId, command.damAnimalId)) { "Active ewe not found" }
        require(ewe.speciesCode == "sheep" && ewe.sex == "FEMALE" && ewe.status == "active") { "Active ewe not found" }
        enqueue(context, "sheep.record_lambing.v1", "animal", command.damAnimalId, expectedVersion("animal", command.damAnimalId), json.encodeToString(command)) {
            database.lifecycle().insertLambing(SheepLambingEntity(command.lambingId, farmId, command.damAnimalId, command.bornCount, command.liveCount, command.deadCount, command.occurredEpochDay))
        }
        return LocalCommandResult(context.mutationId, command.lambingId, true)
    }

    suspend fun recordCattleService(command: RecordCattleService, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.cattleService(command)?.let { error(it) }
        val cow = requireNotNull(database.animals().get(farmId, command.animalId)) { "Active cow not found" }
        require(cow.speciesCode == "cattle" && cow.status == "active") { "Active cow not found" }
        enqueue(context, "cattle.record_service.v1", "animal", command.animalId, expectedVersion("animal", command.animalId), json.encodeToString(command)) {
            database.lifecycle().insertService(CattleServiceEntity(command.serviceId, farmId, command.animalId, command.method, command.occurredEpochDay))
            database.tasks().insert(TaskEntity(command.pdTaskId, farmId, "cattle", "PD", "Pregnancy diagnosis (PD)", command.occurredEpochDay + 32, "open", command.animalId, null, null, context.occurredAtEpochMillis))
            database.tasks().insert(TaskEntity(command.paddockTaskId, farmId, "cattle", "CALVING_PADDOCK", "Calving paddock / close-up pen", command.occurredEpochDay + 259, "open", command.animalId, null, null, context.occurredAtEpochMillis))
            database.tasks().insert(TaskEntity(command.calvingTaskId, farmId, "cattle", "EXPECTED_CALVING", "Expected calving", command.occurredEpochDay + 280, "open", command.animalId, null, null, context.occurredAtEpochMillis))
        }
        return LocalCommandResult(context.mutationId, command.serviceId, true)
    }

    suspend fun recordCattlePd(command: RecordCattlePd, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.cattlePd(command)?.let { error(it) }
        val cow = requireNotNull(database.animals().get(farmId, command.animalId)) { "Cow not found" }
        require(cow.speciesCode == "cattle") { "Cow not found" }
        enqueue(context, "cattle.record_pd.v1", "animal", command.animalId, expectedVersion("animal", command.animalId), json.encodeToString(command)) {
            database.lifecycle().insertPd(CattlePdEntity(command.pdId, farmId, command.animalId, command.result, command.occurredEpochDay))
        }
        return LocalCommandResult(context.mutationId, command.pdId, true)
    }

    suspend fun recordCalving(command: RecordCattleCalving, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.calving(command)?.let { error(it) }
        val cow = requireNotNull(database.animals().get(farmId, command.damAnimalId)) { "Active cow not found" }
        require(cow.speciesCode == "cattle" && cow.sex == "FEMALE" && cow.status == "active") { "Active cow not found" }
        enqueue(context, "cattle.record_calving.v1", "animal", command.damAnimalId, expectedVersion("animal", command.damAnimalId), json.encodeToString(command)) {
            database.lifecycle().insertCalving(CattleCalvingEntity(command.calvingId, farmId, command.damAnimalId, command.bornCount, command.liveCount, command.deadCount, command.occurredEpochDay))
        }
        return LocalCommandResult(context.mutationId, command.calvingId, true)
    }

    suspend fun recordPalpation(command: RecordRabbitPalpation, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.palpation(command)?.let { error(it) }
        enqueue(context, "rabbit.record_palpation.v1", "rabbit_wave", command.waveId, expectedVersion("rabbit_wave", command.waveId), json.encodeToString(command)) {
            database.lifecycle().insertPalpation(RabbitPalpationEntity(command.palpationId, farmId, command.waveId, command.result, command.occurredEpochDay))
        }
        return LocalCommandResult(context.mutationId, command.palpationId, true)
    }

    suspend fun recordKindling(command: RecordRabbitKindling, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.kindling(command)?.let { error(it) }
        enqueue(context, "rabbit.record_kindling.v1", "rabbit_wave", command.waveId, expectedVersion("rabbit_wave", command.waveId), json.encodeToString(command)) {
            database.lifecycle().insertKindling(RabbitKindlingEntity(command.kindlingId, farmId, command.waveId, command.liveCount, command.deadCount, command.occurredEpochDay))
        }
        return LocalCommandResult(context.mutationId, command.kindlingId, true)
    }

    suspend fun recordFoster(command: RecordRabbitFoster, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.foster(command)?.let { error(it) }
        val from = requireNotNull(database.rabbitProgramme().waves(farmId).firstOrNull { it.id == command.fromWaveId }) { "Foster waves not found" }
        val to = requireNotNull(database.rabbitProgramme().waves(farmId).firstOrNull { it.id == command.toWaveId }) { "Foster waves not found" }
        val within = kotlin.math.abs(command.occurredEpochDay - from.kindlingEpochDay) <= 3 ||
            kotlin.math.abs(command.occurredEpochDay - to.kindlingEpochDay) <= 3
        if (!within && !command.ackOutsideWindow) {
            error("Foster after 3 days from kindling needs an explicit acknowledgement")
        }
        enqueue(context, "rabbit.record_foster.v1", "rabbit_wave", command.toWaveId, expectedVersion("rabbit_wave", command.toWaveId), json.encodeToString(command)) {
            database.lifecycle().insertFoster(
                RabbitFosterEntity(command.fosterId, farmId, command.fromWaveId, command.toWaveId, command.kitCount, within, command.occurredEpochDay),
            )
        }
        return LocalCommandResult(context.mutationId, command.fosterId, true)
    }

    suspend fun createSupplier(command: CreateSupplier, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.supplier(command)?.let { error(it) }
        enqueue(context, "supplier.create.v1", "supplier", command.supplierId, 0, json.encodeToString(command)) {
            database.lifecycle().insertSupplier(SupplierEntity(command.supplierId, farmId, command.name.trim(), command.leadTimeDays))
        }
        return LocalCommandResult(context.mutationId, command.supplierId, true)
    }

    suspend fun recordPurchase(command: RecordPurchase, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.purchase(command)?.let { error(it) }
        requireNotNull(database.lifecycle().suppliers(farmId).firstOrNull { it.id == command.supplierId }) { "Supplier not found" }
        val item = requireNotNull(database.inventory().item(farmId, command.itemId)) { "Inventory item not found" }
        enqueue(context, "purchase.record.v1", "inventory_item", command.itemId, expectedVersion("inventory_item", command.itemId), json.encodeToString(command)) {
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
        return LocalCommandResult(context.mutationId, command.purchaseId, true)
    }

    suspend fun acceptPack(command: AcceptHealthPack, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.pack(command)?.let { error(it) }
        enqueue(context, "health.pack_accept.v1", "health_protocol_pack", command.packId, 0, json.encodeToString(command)) {
            database.lifecycle().insertPack(
                HealthPackEntity(command.packId, farmId, command.speciesCode.trim(), command.name.trim(), "vet_accepted", command.acceptedByVet.trim()),
            )
        }
        return LocalCommandResult(context.mutationId, command.packId, true)
    }

    suspend fun addPackSlot(command: AddHealthPackSlot, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.packSlot(command)?.let { error(it) }
        val pack = requireNotNull(database.lifecycle().packs(farmId).firstOrNull { it.id == command.packId }) { "Slot needs a vet-accepted protocol pack" }
        require(pack.status == "vet_accepted") { "Slot needs a vet-accepted protocol pack" }
        enqueue(context, "health.pack_slot_add.v1", "health_protocol_pack", command.packId, expectedVersion("health_protocol_pack", command.packId), json.encodeToString(command)) {
            database.lifecycle().insertPackSlot(
                HealthPackSlotEntity(command.slotId, farmId, command.packId, command.slotCode.trim(), command.title.trim(), command.offsetDays, command.fromEvent, command.isCore),
            )
        }
        return LocalCommandResult(context.mutationId, command.slotId, true)
    }

    suspend fun applyPack(command: ApplyHealthPack, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.packApply(command)?.let { error(it) }
        val pack = requireNotNull(database.lifecycle().packs(farmId).firstOrNull { it.id == command.packId }) { "Pack apply needs a vet-accepted protocol pack" }
        require(pack.status == "vet_accepted") { "Pack apply needs a vet-accepted protocol pack" }
        val slots = database.lifecycle().coreSlots(farmId, command.packId)
        require(slots.isNotEmpty()) { "Pack apply needs at least one core slot" }
        enqueue(context, "health.pack_apply.v1", "health_protocol_pack", command.packId, expectedVersion("health_protocol_pack", command.packId), json.encodeToString(command)) {
            database.lifecycle().insertPackApply(
                HealthPackApplyEntity(command.applyId, farmId, command.packId, command.animalId, command.groupId, command.anchorEpochDay),
            )
            for (slot in slots) {
                database.tasks().insert(
                    TaskEntity(
                        "${command.applyId}:${slot.id}", farmId, pack.speciesCode, "PACK_SLOT", slot.title,
                        command.anchorEpochDay + slot.offsetDays, "open", command.animalId, null, null, context.occurredAtEpochMillis,
                    ),
                )
            }
        }
        return LocalCommandResult(context.mutationId, command.applyId, true)
    }

    suspend fun placeCattleLot(command: PlaceCattleLot, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.lotPlace(command)?.let { error(it) }
        val group = requireNotNull(database.groups().get(farmId, command.groupId)) { "Lot place needs a cattle lot" }
        require(group.speciesCode == "cattle") { "Lot place needs a cattle lot" }
        enqueue(context, "cattle.lot_place.v1", "animal_group", command.groupId, expectedVersion("animal_group", command.groupId), json.encodeToString(command)) {
            database.lifecycle().insertLotPlace(
                CattleLotPlacementEntity(command.placementId, farmId, command.groupId, command.headCount, command.placedEpochDay),
            )
        }
        return LocalCommandResult(context.mutationId, command.placementId, true)
    }

    suspend fun recordDaysOnFeed(command: RecordCattleDaysOnFeed, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.daysOnFeed(command)?.let { error(it) }
        val group = requireNotNull(database.groups().get(farmId, command.groupId)) { "Days on feed needs a cattle lot" }
        require(group.speciesCode == "cattle") { "Days on feed needs a cattle lot" }
        enqueue(context, "cattle.record_dof.v1", "animal_group", command.groupId, expectedVersion("animal_group", command.groupId), json.encodeToString(command)) {
            database.lifecycle().insertDof(
                CattleDofEntity(command.recordId, farmId, command.groupId, command.daysOnFeed, command.occurredEpochDay),
            )
        }
        return LocalCommandResult(context.mutationId, command.recordId, true)
    }

    suspend fun closeCattleLot(command: CloseCattleLot, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.lotClose(command)?.let { error(it) }
        val group = requireNotNull(database.groups().get(farmId, command.groupId)) { "Lot close-out needs a cattle lot" }
        require(group.speciesCode == "cattle") { "Lot close-out needs a cattle lot" }
        enqueue(context, "cattle.lot_close.v1", "animal_group", command.groupId, expectedVersion("animal_group", command.groupId), json.encodeToString(command)) {
            database.lifecycle().insertLotClose(
                CattleLotCloseEntity(command.closeoutId, farmId, command.groupId, command.headOut, command.weightGrams, command.daysOnFeed, command.occurredEpochDay),
            )
        }
        return LocalCommandResult(context.mutationId, command.closeoutId, true)
    }

    suspend fun setReorder(command: SetInventoryReorder, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.setReorder(command)?.let { error(it) }
        requireNotNull(database.inventory().item(farmId, command.itemId)) { "Inventory item not found" }
        enqueue(context, "inventory.set_reorder.v1", "inventory_item", command.itemId, expectedVersion("inventory_item", command.itemId), json.encodeToString(command)) {
            database.inventory().setReorder(farmId, command.itemId, command.reorderMilli, context.occurredAtEpochMillis)
        }
        return LocalCommandResult(context.mutationId, command.itemId, true)
    }

    suspend fun recordReorderAlert(command: RecordReorderAlert, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.reorderAlert(command)?.let { error(it) }
        val item = requireNotNull(database.inventory().item(farmId, command.itemId)) { "Inventory item not found" }
        require(item.reorderMilli > 0 && item.quantityMilli <= item.reorderMilli) { "Reorder alert needs on-hand at or below the reorder point" }
        enqueue(context, "inventory.record_reorder.v1", "inventory_item", command.itemId, expectedVersion("inventory_item", command.itemId), json.encodeToString(command)) {
            database.lifecycle().insertReorderAlert(
                ReorderAlertEntity(command.alertId, farmId, command.itemId, item.quantityMilli, item.reorderMilli, command.occurredEpochDay),
            )
        }
        return LocalCommandResult(context.mutationId, command.alertId, true)
    }

    suspend fun recordCensus(command: RecordGroupCensus, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.census(command)?.let { error(it) }
        requireNotNull(database.groups().get(farmId, command.groupId)) { "Census needs a group on this farm" }
        enqueue(context, "group.census.v1", "animal_group", command.groupId, expectedVersion("animal_group", command.groupId), json.encodeToString(command)) {
            database.lifecycle().insertCensus(
                GroupCensusEntity(command.censusId, farmId, command.groupId, command.headCount, command.occurredEpochDay),
            )
            database.groups().setHeadCount(farmId, command.groupId, command.headCount)
        }
        return LocalCommandResult(context.mutationId, command.censusId, true)
    }

    suspend fun suppliers() = database.lifecycle().suppliers(farmId)
    suspend fun purchases() = database.lifecycle().purchases(farmId, 50)
    suspend fun withdrawals() = database.lifecycle().withdrawals(farmId, 50)
    suspend fun packs() = database.lifecycle().packs(farmId)

    suspend fun diseases(): List<com.farmos.core.database.DiseaseCatalogEntity> {
        val existing = database.diseaseCatalog().all()
        if (existing.isNotEmpty()) return existing
        listOf(
            com.farmos.core.database.DiseaseCatalogEntity("enterotoxemia_cd", "goat", "Enterotoxemia", "Sudden death, convulsions, bloated kids on rich feed", "Isolate remaining animals, stop sudden grain, call the vet", "CDT pack accepted by the attending vet", "vaccine", true),
            com.farmos.core.database.DiseaseCatalogEntity("haemonchus", "goat", "Haemonchus", "Pale eyelids, bottle jaw, weakness", "Shade and water. Do not blanket-drench the herd.", "FAMACHA and FEC protocol, not a calendar drench", "anthelmintic", true),
            com.farmos.core.database.DiseaseCatalogEntity("gi_stasis", "rabbit", "Gut stasis", "No faeces, hunched, off feed", "Keep warm, offer hay and water, call the vet the same day", "Hay always available, reduce stress", "fluids", true),
        ).forEach { database.diseaseCatalog().upsert(it) }
        return database.diseaseCatalog().all()
    }

    private suspend fun expectedVersion(aggregateType: String, aggregateId: String): Long {
        val authoritative = database.aggregateVersions().getVersion(farmId, aggregateType, aggregateId) ?: 0L
        val queued = database.outbox().countUnacknowledgedForAggregate(farmId, aggregateType, aggregateId)
        return authoritative + queued
    }

    private suspend fun enqueue(
        context: LocalCommandContext,
        commandName: String,
        aggregateType: String,
        aggregateId: String,
        expectedStreamVersion: Long?,
        payloadJson: String,
        localWrite: suspend () -> Unit,
    ) {
        require(context.farmId == farmId) { "Farm context mismatch" }
        database.withTransaction {
            val ordinal = database.outbox().nextAggregateOrdinal(farmId, aggregateType, aggregateId)
            localWrite()
            database.outbox().insert(
                OutboxEntity(
                    mutationId = context.mutationId,
                    farmId = farmId,
                    actorId = context.actorId,
                    deviceId = context.deviceId,
                    commandName = commandName,
                    commandSchemaVersion = 1,
                    aggregateType = aggregateType,
                    aggregateId = aggregateId,
                    aggregateOrdinal = ordinal,
                    expectedStreamVersion = expectedStreamVersion,
                    payloadJson = payloadJson,
                    occurredAtEpochMillis = context.occurredAtEpochMillis,
                    createdAtEpochMillis = System.currentTimeMillis(),
                    state = SyncState.PENDING.name,
                    attemptCount = 0,
                    nextAttemptAtEpochMillis = null,
                    lastErrorCode = null,
                    serverEventId = null,
                    serverStreamVersion = null,
                ),
            )
        }
    }
}
