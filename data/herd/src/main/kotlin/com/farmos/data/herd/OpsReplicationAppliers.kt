package com.farmos.data.herd

import com.farmos.core.database.COMMAND_PAYLOAD_KEY
import com.farmos.core.database.OperationApplier
import com.farmos.core.model.LocalCommandContext
import com.farmos.domain.ops.AcceptHealthPack
import com.farmos.domain.ops.AddHealthPackSlot
import com.farmos.domain.ops.CreateFarmCustomer
import com.farmos.domain.ops.RecordCustomerSale
import com.farmos.domain.ops.RecordWorkerLabour
import com.farmos.domain.ops.UpdateFarmCustomer
import com.farmos.domain.ops.ApplyHealthPack
import com.farmos.domain.ops.AssignAnimalIdentifier
import com.farmos.domain.ops.CandlePoultryHatch
import com.farmos.domain.ops.CloseCattleLot
import com.farmos.domain.ops.CompleteFarmTask
import com.farmos.domain.ops.CreateAnimalGroup
import com.farmos.domain.ops.CreateFarmAsset
import com.farmos.domain.ops.CreateFarmTask
import com.farmos.domain.ops.ReverseAnimalExit
import com.farmos.domain.ops.RecordAnimalExit
import com.farmos.domain.ops.UpdateFarmWorker
import com.farmos.domain.ops.CreateFarmWorker
import com.farmos.domain.ops.UpdateFarmTask
import com.farmos.domain.ops.SubmitStockCount
import com.farmos.domain.ops.StartStockCount
import com.farmos.domain.ops.RejectStockCount
import com.farmos.domain.ops.RecordStockCountLine
import com.farmos.domain.ops.PostStockCount
import com.farmos.domain.ops.EndTaskSeries
import com.farmos.domain.ops.EditTaskSeries
import com.farmos.domain.ops.CreateTaskSeries
import com.farmos.domain.ops.CompleteTaskOccurrence
import com.farmos.domain.ops.CreateFormularyItem
import com.farmos.domain.ops.CreateInventoryItem
import com.farmos.domain.ops.CreatePaddock
import com.farmos.domain.ops.CreatePoultryHouse
import com.farmos.domain.ops.CreateSupplier
import com.farmos.domain.ops.EnablePoultryKind
import com.farmos.domain.ops.EndGrazing
import com.farmos.domain.ops.IssueFeed
import com.farmos.domain.ops.IssueInventoryLot
import com.farmos.domain.ops.LinkPedigree
import com.farmos.domain.ops.MoveInventory
import com.farmos.domain.ops.PlaceCattleLot
import com.farmos.domain.ops.PlacePoultryFlock
import com.farmos.domain.ops.ReceiveInventoryLot
import com.farmos.domain.ops.RecordCattleBcs
import com.farmos.domain.ops.RecordCattleCalving
import com.farmos.domain.ops.RecordCattleDaysOnFeed
import com.farmos.domain.ops.RecordCattleDryOff
import com.farmos.domain.ops.RecordCattleLocomotion
import com.farmos.domain.ops.RecordCattleMilk
import com.farmos.domain.ops.RecordCattlePd
import com.farmos.domain.ops.RecordCattleScc
import com.farmos.domain.ops.RecordCattleService
import com.farmos.domain.ops.RecordCattleServiceV2
import com.farmos.domain.ops.RecordCattleWeaning
import com.farmos.domain.ops.RecordFamacha
import com.farmos.domain.ops.RecordGroupCensus
import com.farmos.domain.ops.RecordHealthObservation
import com.farmos.domain.ops.RecordHealthTreatment
import com.farmos.domain.ops.RecordLabResult
import com.farmos.domain.ops.RecordLabour
import com.farmos.domain.ops.RecordMaintenance
import com.farmos.domain.ops.RecordMoney
import com.farmos.domain.ops.RecordOfficialMovement
import com.farmos.domain.ops.RecordPoultryBiosecurity
import com.farmos.domain.ops.RecordPoultryFlockDay
import com.farmos.domain.ops.RecordPoultryHatch
import com.farmos.domain.ops.RecordPoultryVaccination
import com.farmos.domain.ops.RecordPurchase
import com.farmos.domain.ops.RecordRabbitFoster
import com.farmos.domain.ops.RecordRabbitKindling
import com.farmos.domain.ops.RecordRabbitPalpation
import com.farmos.domain.ops.RecordReorderAlert
import com.farmos.domain.ops.RecordSale
import com.farmos.domain.ops.RecordSheepDag
import com.farmos.domain.ops.RecordSheepFlystrike
import com.farmos.domain.ops.RecordSheepFootrot
import com.farmos.domain.ops.RecordSheepJoining
import com.farmos.domain.ops.RecordSheepJoiningV2
import com.farmos.domain.ops.RecordSheepLambing
import com.farmos.domain.ops.RecordSheepMarking
import com.farmos.domain.ops.RecordSheepMicron
import com.farmos.domain.ops.RecordSheepScan
import com.farmos.domain.ops.RecordSheepShearing
import com.farmos.domain.ops.RecordSheepWeaning
import com.farmos.domain.ops.RecordSheepWool
import com.farmos.domain.ops.RecordVetVisit
import com.farmos.domain.ops.RecordWater
import com.farmos.domain.ops.SetInventoryReorder
import com.farmos.domain.ops.SetPoultryHatch
import com.farmos.domain.ops.StartGrazing
import com.farmos.domain.rabbit.AgreeRabbitContract
import com.farmos.domain.rabbit.BindRabbitBedding
import com.farmos.domain.rabbit.CreateRabbitCage
import com.farmos.domain.rabbit.CreateRabbitNestBox
import com.farmos.domain.rabbit.CreateRabbitWave
import com.farmos.domain.rabbit.DecideRabbitRetention
import com.farmos.domain.rabbit.EnqueueRabbitWaitlist
import com.farmos.domain.rabbit.FulfillRabbitWaitlist
import com.farmos.domain.rabbit.PromoteRabbitKit
import com.farmos.domain.rabbit.RecordRabbitGiStasis
import com.farmos.domain.rabbit.RecordRabbitMarketPlan
import com.farmos.domain.rabbit.RecordRabbitMatingOutcome
import com.farmos.domain.rabbit.RecordRabbitWean
import com.farmos.domain.rabbit.RegisterRabbitKit
import com.farmos.domain.rabbit.SetRabbitNestBoxStatus
import com.farmos.domain.replication.OperationEnvelope
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Appliers for every farm operations command: an operation received from another device is decoded
 * and replayed through the same handler that wrote it on its origin device, with its original actor,
 * device, identity and business time, in replay mode so it is not journalled a second time.
 * `OpsReplicationAppliersTest` proves the table covers every command the repository journals.
 */
object OpsReplicationAppliers {
    private val json = Json { encodeDefaults = true }

    val all: Map<String, OperationApplier> = mapOf(
        "animal.identifier_assign.v1" to replay { ops, op -> ops.assignIdentifier(decode<AssignAnimalIdentifier>(op), context(op)) },
        "asset.create.v1" to replay { ops, op -> ops.createAsset(decode<CreateFarmAsset>(op), context(op)) },
        "cattle.lot_close.v1" to replay { ops, op -> ops.closeCattleLot(decode<CloseCattleLot>(op), context(op)) },
        "cattle.lot_place.v1" to replay { ops, op -> ops.placeCattleLot(decode<PlaceCattleLot>(op), context(op)) },
        "cattle.record_bcs.v1" to replay { ops, op -> ops.recordCattleBcs(decode<RecordCattleBcs>(op), context(op)) },
        "cattle.record_calving.v1" to replay { ops, op -> ops.recordCalving(decode<RecordCattleCalving>(op), context(op)) },
        "cattle.record_dof.v1" to replay { ops, op -> ops.recordDaysOnFeed(decode<RecordCattleDaysOnFeed>(op), context(op)) },
        "cattle.record_dryoff.v1" to replay { ops, op -> ops.recordDryOff(decode<RecordCattleDryOff>(op), context(op)) },
        "cattle.record_locomotion.v1" to replay { ops, op -> ops.recordLocomotion(decode<RecordCattleLocomotion>(op), context(op)) },
        "cattle.record_milk.v1" to replay { ops, op -> ops.recordCattleMilk(decode<RecordCattleMilk>(op), context(op)) },
        "cattle.record_pd.v1" to replay { ops, op -> ops.recordCattlePd(decode<RecordCattlePd>(op), context(op)) },
        "cattle.record_scc.v1" to replay { ops, op -> ops.recordScc(decode<RecordCattleScc>(op), context(op)) },
        "cattle.record_service.v1" to replay { ops, op -> ops.recordCattleService(decode<RecordCattleService>(op), context(op)) },
        BreedingDueCommands.CATTLE_SERVICE_V2 to OperationApplier { database, op ->
            BreedingDueCommands(database, op.farmId, replaying = true).recordCattleService(decode<RecordCattleServiceV2>(op), context(op))
        },
        "cattle.record_weaning.v1" to replay { ops, op -> ops.recordCattleWeaning(decode<RecordCattleWeaning>(op), context(op)) },
        "feed.issue.v1" to replay { ops, op -> ops.issueFeed(decode<IssueFeed>(op), context(op)) },
        "formulary.item_create.v1" to replay { ops, op -> ops.createFormulary(decode<CreateFormularyItem>(op), context(op)) },
        "grazing.end.v1" to replay { ops, op -> ops.endGrazing(decode<EndGrazing>(op), context(op)) },
        "grazing.start.v1" to replay { ops, op -> ops.startGrazing(decode<StartGrazing>(op), context(op)) },
        "group.census.v1" to replay { ops, op -> ops.recordCensus(decode<RecordGroupCensus>(op), context(op)) },
        "group.create.v1" to replay { ops, op -> ops.createGroup(decode<CreateAnimalGroup>(op), context(op)) },
        "health.pack_accept.v1" to replay { ops, op -> ops.acceptPack(decode<AcceptHealthPack>(op), context(op)) },
        "health.pack_apply.v1" to replay { ops, op -> ops.applyPack(decode<ApplyHealthPack>(op), context(op)) },
        "health.pack_slot_add.v1" to replay { ops, op -> ops.addPackSlot(decode<AddHealthPackSlot>(op), context(op)) },
        "health.record_lab.v1" to replay { ops, op -> ops.recordLab(decode<RecordLabResult>(op), context(op)) },
        "health.record_observation.v1" to replay { ops, op -> ops.recordObservation(decode<RecordHealthObservation>(op), context(op)) },
        "health.record_treatment.v1" to replay { ops, op -> ops.recordTreatment(decode<RecordHealthTreatment>(op), context(op)) },
        "health.record_vet_visit.v1" to replay { ops, op -> ops.recordVetVisit(decode<RecordVetVisit>(op), context(op)) },
        "inventory.item_create.v1" to replay { ops, op -> ops.createItem(decode<CreateInventoryItem>(op), context(op)) },
        "inventory.lot_issue.v1" to replay { ops, op -> ops.issueLot(decode<IssueInventoryLot>(op), context(op)) },
        "inventory.lot_receive.v1" to replay { ops, op -> ops.receiveLot(decode<ReceiveInventoryLot>(op), context(op)) },
        "inventory.move.v1" to replay { ops, op -> ops.move(decode<MoveInventory>(op), context(op)) },
        "inventory.record_reorder.v1" to replay { ops, op -> ops.recordReorderAlert(decode<RecordReorderAlert>(op), context(op)) },
        "inventory.set_reorder.v1" to replay { ops, op -> ops.setReorder(decode<SetInventoryReorder>(op), context(op)) },
        "labour.record.v1" to replay { ops, op -> ops.recordLabour(decode<RecordLabour>(op), context(op)) },
        "maintenance.record.v1" to replay { ops, op -> ops.recordMaintenance(decode<RecordMaintenance>(op), context(op)) },
        "money.record.v1" to replay { ops, op -> ops.recordMoney(decode<RecordMoney>(op), context(op)) },
        "official.record_movement.v1" to replay { ops, op -> ops.recordOfficialMovement(decode<RecordOfficialMovement>(op), context(op)) },
        "paddock.create.v1" to replay { ops, op -> ops.createPaddock(decode<CreatePaddock>(op), context(op)) },
        "pedigree.link.v1" to replay { ops, op -> ops.linkPedigree(decode<LinkPedigree>(op), context(op)) },
        "poultry.flock_day.v1" to replay { ops, op -> ops.recordFlockDay(decode<RecordPoultryFlockDay>(op), context(op)) },
        "poultry.flock_place.v1" to replay { ops, op -> ops.placeFlock(decode<PlacePoultryFlock>(op), context(op)) },
        "poultry.hatch_candle.v1" to replay { ops, op -> ops.candleHatch(decode<CandlePoultryHatch>(op), context(op)) },
        "poultry.hatch_record.v1" to replay { ops, op -> ops.recordHatch(decode<RecordPoultryHatch>(op), context(op)) },
        "poultry.hatch_set.v1" to replay { ops, op -> ops.setHatch(decode<SetPoultryHatch>(op), context(op)) },
        "poultry.house_create.v1" to replay { ops, op -> ops.createHouse(decode<CreatePoultryHouse>(op), context(op)) },
        "poultry.kind_enable.v1" to replay { ops, op -> ops.enablePoultryKind(decode<EnablePoultryKind>(op), context(op)) },
        "poultry.record_biosecurity.v1" to replay { ops, op -> ops.recordBiosecurity(decode<RecordPoultryBiosecurity>(op), context(op)) },
        "poultry.record_vaccination.v1" to replay { ops, op -> ops.recordVaccination(decode<RecordPoultryVaccination>(op), context(op)) },
        "purchase.record.v1" to replay { ops, op -> ops.recordPurchase(decode<RecordPurchase>(op), context(op)) },
        "rabbit.bedding_bind.v1" to replay { ops, op -> ops.bindBedding(decode<BindRabbitBedding>(op), context(op)) },
        "rabbit.cage_create.v1" to replay { ops, op -> ops.createCage(decode<CreateRabbitCage>(op), context(op)) },
        "rabbit.contract_agree.v1" to replay { ops, op -> ops.agreeContract(decode<AgreeRabbitContract>(op), context(op)) },
        "rabbit.kit_promote.v1" to replay { ops, op -> ops.promoteKit(decode<PromoteRabbitKit>(op), context(op)) },
        "rabbit.kit_register.v1" to replay { ops, op -> ops.registerKit(decode<RegisterRabbitKit>(op), context(op)) },
        "rabbit.market_plan.v1" to replay { ops, op -> ops.recordPlan(decode<RecordRabbitMarketPlan>(op), context(op)) },
        "rabbit.nest_box_create.v1" to replay { ops, op -> ops.createNestBox(decode<CreateRabbitNestBox>(op), context(op)) },
        "rabbit.nest_box_set_status.v1" to replay { ops, op -> ops.setNestBoxStatus(decode<SetRabbitNestBoxStatus>(op), context(op)) },
        "rabbit.record_foster.v1" to replay { ops, op -> ops.recordFoster(decode<RecordRabbitFoster>(op), context(op)) },
        "rabbit.record_gi_stasis.v1" to replay { ops, op -> ops.recordGiStasis(decode<RecordRabbitGiStasis>(op), context(op)) },
        "rabbit.record_kindling.v1" to replay { ops, op -> ops.recordKindling(decode<RecordRabbitKindling>(op), context(op)) },
        "rabbit.record_mating_outcome.v1" to replay { ops, op -> ops.recordMatingOutcome(decode<RecordRabbitMatingOutcome>(op), context(op)) },
        "rabbit.record_palpation.v1" to replay { ops, op -> ops.recordPalpation(decode<RecordRabbitPalpation>(op), context(op)) },
        "rabbit.record_wean.v1" to replay { ops, op -> ops.recordWean(decode<RecordRabbitWean>(op), context(op)) },
        "rabbit.retention_decide.v1" to replay { ops, op -> ops.decideRetention(decode<DecideRabbitRetention>(op), context(op)) },
        "rabbit.waitlist_enqueue.v1" to replay { ops, op -> ops.enqueueWaitlist(decode<EnqueueRabbitWaitlist>(op), context(op)) },
        "rabbit.waitlist_fulfill.v1" to replay { ops, op -> ops.fulfillWaitlist(decode<FulfillRabbitWaitlist>(op), context(op)) },
        "rabbit.wave_create.v1" to replay { ops, op -> ops.createWave(decode<CreateRabbitWave>(op), context(op)) },
        "sale.record.v1" to replay { ops, op -> ops.recordSale(decode<RecordSale>(op), context(op)) },
        "sheep.record_dag.v1" to replay { ops, op -> ops.recordDag(decode<RecordSheepDag>(op), context(op)) },
        "sheep.record_famacha.v1" to replay { ops, op -> ops.recordSheepFamacha(decode<RecordFamacha>(op), context(op)) },
        "sheep.record_flystrike.v1" to replay { ops, op -> ops.recordFlystrike(decode<RecordSheepFlystrike>(op), context(op)) },
        "sheep.record_footrot.v1" to replay { ops, op -> ops.recordFootrot(decode<RecordSheepFootrot>(op), context(op)) },
        "sheep.record_joining.v1" to replay { ops, op -> ops.recordJoining(decode<RecordSheepJoining>(op), context(op)) },
        BreedingDueCommands.SHEEP_JOINING_V2 to OperationApplier { database, op ->
            BreedingDueCommands(database, op.farmId, replaying = true).recordJoining(decode<RecordSheepJoiningV2>(op), context(op))
        },
        "sheep.record_lambing.v1" to replay { ops, op -> ops.recordLambing(decode<RecordSheepLambing>(op), context(op)) },
        "sheep.record_marking.v1" to replay { ops, op -> ops.recordMarking(decode<RecordSheepMarking>(op), context(op)) },
        "sheep.record_micron.v1" to replay { ops, op -> ops.recordMicron(decode<RecordSheepMicron>(op), context(op)) },
        "sheep.record_scan.v1" to replay { ops, op -> ops.recordScan(decode<RecordSheepScan>(op), context(op)) },
        "sheep.record_shearing.v1" to replay { ops, op -> ops.recordShearing(decode<RecordSheepShearing>(op), context(op)) },
        "sheep.record_weaning.v1" to replay { ops, op -> ops.recordSheepWeaning(decode<RecordSheepWeaning>(op), context(op)) },
        "sheep.record_wool.v1" to replay { ops, op -> ops.recordWool(decode<RecordSheepWool>(op), context(op)) },
        "supplier.create.v1" to replay { ops, op -> ops.createSupplier(decode<CreateSupplier>(op), context(op)) },
        "task.complete.v1" to replay { ops, op -> ops.completeTask(decode<CompleteFarmTask>(op), context(op)) },
        "task.create.v1" to replay { ops, op -> ops.createTask(decode<CreateFarmTask>(op), context(op)) },
        AnimalExitCommands.RECORD to OperationApplier { database, op -> AnimalExitCommands(database, op.farmId, replaying = true).record(decode<RecordAnimalExit>(op), context(op)) },
        AnimalExitCommands.REVERSE to OperationApplier { database, op -> AnimalExitCommands(database, op.farmId, replaying = true).reverse(decode<ReverseAnimalExit>(op), context(op)) },
        WorkerRegisterCommands.CREATE to OperationApplier { database, op -> WorkerRegisterCommands(database, op.farmId, replaying = true).create(decode<CreateFarmWorker>(op), context(op)) },
        CustomerCommands.CREATE to OperationApplier { database, op -> CustomerCommands(database, op.farmId, replaying = true).create(decode<CreateFarmCustomer>(op), context(op)) },
        CustomerCommands.UPDATE to OperationApplier { database, op -> CustomerCommands(database, op.farmId, replaying = true).update(decode<UpdateFarmCustomer>(op), context(op)) },
        CustomerCommands.SALE to OperationApplier { database, op -> CustomerCommands(database, op.farmId, replaying = true).recordSale(decode<RecordCustomerSale>(op), context(op)) },
        LabourCommands.RECORD to OperationApplier { database, op -> LabourCommands(database, op.farmId, replaying = true).record(decode<RecordWorkerLabour>(op), context(op)) },
        WorkerRegisterCommands.UPDATE to OperationApplier { database, op -> WorkerRegisterCommands(database, op.farmId, replaying = true).update(decode<UpdateFarmWorker>(op), context(op)) },
        StockCountCommands.START to stock { commands, op -> commands.start(decode<StartStockCount>(op), context(op)) },
        StockCountCommands.LINE to stock { commands, op -> commands.recordLine(decode<RecordStockCountLine>(op), context(op)) },
        StockCountCommands.SUBMIT to stock { commands, op -> commands.submit(decode<SubmitStockCount>(op), context(op)) },
        StockCountCommands.POST to stock { commands, op -> commands.post(decode<PostStockCount>(op), context(op)) },
        StockCountCommands.REJECT to stock { commands, op -> commands.reject(decode<RejectStockCount>(op), context(op)) },
        TaskSeriesCommands.SERIES_CREATE to series { commands, op -> commands.create(decode<CreateTaskSeries>(op), context(op)) },
        TaskSeriesCommands.OCCURRENCE_COMPLETE to series { commands, op -> commands.complete(decode<CompleteTaskOccurrence>(op), context(op)) },
        TaskSeriesCommands.SERIES_EDIT to series { commands, op -> commands.edit(decode<EditTaskSeries>(op), context(op)) },
        TaskSeriesCommands.SERIES_END to series { commands, op -> commands.end(decode<EndTaskSeries>(op), context(op)) },
        TaskSeriesCommands.TASK_UPDATE to series { commands, op -> commands.update(decode<UpdateFarmTask>(op), context(op)) },
        "water.record.v1" to replay { ops, op -> ops.recordWater(decode<RecordWater>(op), context(op)) },
    )

    private inline fun <reified T> decode(op: OperationEnvelope): T = json.decodeFromString(op.payload.getValue(COMMAND_PAYLOAD_KEY))

    private fun context(op: OperationEnvelope) =
        LocalCommandContext(op.farmId, op.actorId, op.deviceId, op.operationId, op.businessTimeEpochMillis)

    private fun stock(block: suspend (StockCountCommands, OperationEnvelope) -> Unit) =
        OperationApplier { database, op -> block(StockCountCommands(database, op.farmId, replaying = true), op) }

    private fun series(block: suspend (TaskSeriesCommands, OperationEnvelope) -> Unit) =
        OperationApplier { database, op -> block(TaskSeriesCommands(database, op.farmId, replaying = true), op) }

    private fun replay(block: suspend (RoomOpsRepository, OperationEnvelope) -> Unit) =
        OperationApplier { database, op -> block(RoomOpsRepository(database, op.farmId, replaying = true), op) }
}
