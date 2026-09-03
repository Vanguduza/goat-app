package com.farmos.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomHerdRepository
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.AcceptHealthPack
import com.farmos.domain.ops.CompleteFarmTask
import com.farmos.domain.ops.CreateAnimalGroup
import com.farmos.domain.ops.CreateFarmAsset
import com.farmos.domain.ops.CreateFarmTask
import com.farmos.domain.ops.CreateFormularyItem
import com.farmos.domain.ops.CreateInventoryItem
import com.farmos.domain.ops.CreatePaddock
import com.farmos.domain.ops.CreateSupplier
import com.farmos.domain.ops.EndGrazing
import com.farmos.domain.ops.IssueFeed
import com.farmos.domain.ops.MoveInventory
import com.farmos.domain.ops.RecordCattleBcs
import com.farmos.domain.ops.RecordCattleCalving
import com.farmos.domain.ops.CandlePoultryHatch
import com.farmos.domain.ops.CreatePoultryHouse
import com.farmos.domain.ops.RecordCattleMilk
import com.farmos.domain.ops.RecordCattlePd
import com.farmos.domain.ops.RecordCattleService
import com.farmos.domain.ops.PlacePoultryFlock
import com.farmos.domain.ops.RecordCattleDryOff
import com.farmos.domain.ops.RecordPoultryBiosecurity
import com.farmos.domain.ops.RecordCattleLocomotion
import com.farmos.domain.ops.RecordCattleScc
import com.farmos.domain.ops.RecordPoultryHatch
import com.farmos.domain.ops.RecordPoultryVaccination
import com.farmos.domain.ops.RecordSheepDag
import com.farmos.domain.ops.RecordSheepFlystrike
import com.farmos.domain.ops.RecordSheepShearing
import com.farmos.domain.ops.SetPoultryHatch
import com.farmos.domain.ops.RecordSheepFootrot
import com.farmos.domain.ops.RecordHealthObservation
import com.farmos.domain.ops.RecordHealthTreatment
import com.farmos.domain.ops.RecordLabour
import com.farmos.domain.ops.RecordMaintenance
import com.farmos.domain.ops.RecordMoney
import com.farmos.domain.ops.RecordPoultryFlockDay
import com.farmos.domain.ops.RecordPurchase
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
import com.farmos.domain.ops.AddHealthPackSlot
import com.farmos.domain.ops.ApplyHealthPack
import com.farmos.domain.ops.AssignAnimalIdentifier
import com.farmos.domain.ops.CloseCattleLot
import com.farmos.domain.ops.EnablePoultryKind
import com.farmos.domain.ops.IssueInventoryLot
import com.farmos.domain.ops.LinkPedigree
import com.farmos.domain.ops.PlaceCattleLot
import com.farmos.domain.ops.ReceiveInventoryLot
import com.farmos.domain.ops.RecordCattleDaysOnFeed
import com.farmos.domain.ops.RecordCattleWeaning
import com.farmos.domain.ops.RecordFamacha
import com.farmos.domain.ops.RecordGroupCensus
import com.farmos.domain.ops.RecordLabResult
import com.farmos.domain.ops.RecordOfficialMovement
import com.farmos.domain.ops.RecordReorderAlert
import com.farmos.domain.ops.RecordSheepMicron
import com.farmos.domain.ops.RecordVetVisit
import com.farmos.domain.ops.SetInventoryReorder
import com.farmos.domain.ops.RecordWater
import com.farmos.domain.ops.StartGrazing
import com.farmos.domain.rabbit.AgreeRabbitContract
import com.farmos.domain.rabbit.RecordRabbitGiStasis
import com.farmos.domain.rabbit.BindRabbitBedding
import com.farmos.domain.rabbit.CreateRabbitCage
import com.farmos.domain.rabbit.CreateRabbitNestBox
import com.farmos.domain.rabbit.CreateRabbitWave
import com.farmos.domain.rabbit.DecideRabbitRetention
import com.farmos.domain.rabbit.EnqueueRabbitWaitlist
import com.farmos.domain.rabbit.FulfillRabbitWaitlist
import com.farmos.domain.rabbit.PromoteRabbitKit
import com.farmos.domain.rabbit.RecordRabbitMarketPlan
import com.farmos.domain.rabbit.RegisterRabbitKit
import com.farmos.feature.ops.HealthObservationScreen
import com.farmos.feature.ops.InventoryScreen
import com.farmos.feature.ops.MoneyCaptureScreen
import com.farmos.feature.ops.SimpleCaptureScreen
import com.farmos.feature.ops.TasksBoardScreen
import com.farmos.feature.rabbit.RabbitProgrammeScreen
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun OperatingModuleHost(
    module: FarmModule,
    farmId: String,
    database: FarmOsDatabase,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var taskRows by remember { mutableStateOf(emptyList<String>()) }
    var healthRows by remember { mutableStateOf(emptyList<String>()) }
    var moneyRows by remember { mutableStateOf(emptyList<String>()) }
    var inventoryRows by remember { mutableStateOf(emptyList<String>()) }
    var cages by remember { mutableStateOf(emptyList<String>()) }
    var waves by remember { mutableStateOf(emptyList<String>()) }
    var boxes by remember { mutableStateOf(0L) }
    var selectedCageId by remember { mutableStateOf<String?>(null) }
    var speciesRows by remember { mutableStateOf(emptyList<SpeciesAnimalRow>()) }
    var rabbitRows by remember { mutableStateOf(emptyList<String>()) }
    var groupRows by remember { mutableStateOf(emptyList<String>()) }
    var paddockRows by remember { mutableStateOf(emptyList<String>()) }
    var grazingRows by remember { mutableStateOf(emptyList<String>()) }
    var labourRows by remember { mutableStateOf(emptyList<String>()) }
    var assetRows by remember { mutableStateOf(emptyList<String>()) }
    var feedRows by remember { mutableStateOf(emptyList<String>()) }
    var waterRows by remember { mutableStateOf(emptyList<String>()) }
    var saleRows by remember { mutableStateOf(emptyList<String>()) }
    var catalogRows by remember { mutableStateOf(emptyList<String>()) }
    var treatmentRows by remember { mutableStateOf(emptyList<String>()) }
    var flockRows by remember { mutableStateOf(emptyList<String>()) }
    var packRows by remember { mutableStateOf(emptyList<String>()) }
    var withdrawalRows by remember { mutableStateOf(emptyList<String>()) }
    var supplierRows by remember { mutableStateOf(emptyList<String>()) }
    var purchaseRows by remember { mutableStateOf(emptyList<String>()) }
    var nestBoxRows by remember { mutableStateOf(emptyList<String>()) }
    var kitRows by remember { mutableStateOf(emptyList<String>()) }
    var waitlistRows by remember { mutableStateOf(emptyList<String>()) }
    var houseRows by remember { mutableStateOf(emptyList<String>()) }
    var hatchRows by remember { mutableStateOf(emptyList<String>()) }
    var vaxRows by remember { mutableStateOf(emptyList<String>()) }
    var beddingLine by remember { mutableStateOf("No bedding item bound.") }

    val speciesCode = when (module) {
        FarmModule.SHEEP -> "sheep"
        FarmModule.CATTLE -> "cattle"
        FarmModule.POULTRY -> "poultry"
        else -> null
    }
    val herd = remember(farmId, speciesCode) {
        speciesCode?.let { RoomHerdRepository(database, farmId, it) }
    }
    val rabbitHerd = remember(farmId) { RoomHerdRepository(database, farmId, "rabbit") }

    suspend fun refreshOps() {
        taskRows = ops.openTasks().map { "${it.id} ${it.title} · ${it.taskCode}" }
        healthRows = ops.recentObservations().map { "${it.speciesCode} · ${it.signs}" }
        moneyRows = ops.recentMoney().map { "${it.kind} ${it.categoryCode} ${it.amountMinor} ${it.currency}" }
        inventoryRows = ops.items().map { "${it.id} ${it.sku} · ${it.name} · ${it.quantityMilli} ${it.unit}" }
        val cageEntities = ops.cages()
        cages = cageEntities.map { it.code }
        if (selectedCageId == null) selectedCageId = cageEntities.firstOrNull()?.id
        waves = ops.waves().map { "${it.id} · ${it.doeCount} does · mating day ${it.matingEpochDay}" }
        boxes = selectedCageId?.let { ops.availableBoxes(it) } ?: 0
        nestBoxRows = ops.nestBoxes().map { "${it.id} ${it.code} · ${it.status}" }
        kitRows = ops.kits().map { "${it.id} ${it.tempLabel} · ${it.sex} · ${it.retention} · ${it.status}" }
        waitlistRows = ops.waitlist().map { "${it.id} ${it.contactName} · qty ${it.qty} · ${it.status}" }
        houseRows = ops.houses().map { "${it.id} ${it.code} · ${it.kind} · ${it.poultryKindCode}" }
        hatchRows = ops.hatches().map { "${it.id} ${it.poultryKindCode} · ${it.eggsSet} eggs · ${it.status}" }
        vaxRows = ops.vaccinations().map { "${it.id} ${it.poultryKindCode} · flock ${it.groupId} · ${it.formularyItemId}" }
        beddingLine = ops.inventoryLink()?.let { "Bedding ${it.nestBeddingItemId ?: "none"} · ${it.nestBeddingQtyMilli} milli" }
            ?: "No bedding item bound."
        speciesRows = herd?.list()?.map { animal ->
            SpeciesAnimalRow(
                animalId = animal.id,
                label = buildString {
                    append(animal.tag)
                    animal.name?.let { append(" · ").append(it) }
                    append(" · ").append(animal.sex.lowercase())
                    append(" · ").append(animal.status)
                    animal.poultryKindCode?.let { append(" · ").append(it) }
                },
                active = animal.status == "active",
            )
        }.orEmpty()
        rabbitRows = rabbitHerd.list().map { animal ->
            buildString {
                append(animal.tag)
                animal.name?.let { append(" · ").append(it) }
                append(" · ").append(if (animal.sex == "FEMALE") "doe" else "buck")
                append(" · ").append(animal.status)
            }
        }
        groupRows = ops.groups().map { "${it.id} ${it.name} · ${it.speciesCode} · ${it.headCount}" }
        paddockRows = ops.paddocks().map { "${it.id} ${it.code} · ${it.displayName} · ${it.waterSource}" }
        grazingRows = ops.openGrazing().map { "${it.id} paddock ${it.paddockId} · group ${it.groupId}" }
        labourRows = ops.recentLabour().map { "${it.workerName} · ${it.taskCode} · ${it.minutes} min" }
        assetRows = ops.assets().map { "${it.id} ${it.code} · ${it.name}" }
        feedRows = ops.recentFeed().map { "${it.itemId} · ${it.quantityMilli} milli" }
        waterRows = ops.recentWater().map { "${it.source} · ${it.litresMilli} ml" }
        saleRows = ops.recentSales().map { "${it.itemKind} · ${it.amountMinor} ${it.currency}" }
        catalogRows = ops.diseases().map { "${it.speciesCode} · ${it.displayName} · ${it.firstAid}" }
        treatmentRows = ops.recentTreatments().map { "${it.speciesCode} · ${it.reason} · formulary ${it.formularyItemId}" }
        flockRows = ops.recentFlockDays().map { "eggs ${it.eggs} · dead ${it.dead} · culls ${it.culls} · feed ${it.feedGrams} g" }
        packRows = ops.packs().map { "${it.speciesCode} · ${it.name} · ${it.status} · ${it.acceptedByVet.orEmpty()}" }
        withdrawalRows = ops.withdrawals().map { "${it.windowKind} · ${it.product} ends day ${it.endsEpochDay}" }
        supplierRows = ops.suppliers().map { "${it.id} ${it.name} · lead ${it.leadTimeDays} d" }
        purchaseRows = ops.purchases().map { "${it.id} item ${it.itemId} · ${it.quantityMilli} milli · ${it.amountMinor} ${it.currency}" }
    }

    LaunchedEffect(module) { runCatching { refreshOps() } }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            runCatching {
                block()
                refreshOps()
            }.onSuccess {
                enqueueSync()
            }.onFailure {
                error = it.message
            }
            busy = false
        }
    }

    when (module) {
        FarmModule.RABBIT -> RabbitProgrammeScreen(
            cages = cages,
            waves = waves,
            availableBoxes = boxes,
            busy = busy,
            error = error,
            does = rabbitRows,
            onRegisterDoe = { tag, name, sex ->
                run { rabbitHerd.register(UUID.randomUUID().toString(), tag, name, sex, null, newContext()) }
            },
            onCreateCage = { code -> run { ops.createCage(CreateRabbitCage(UUID.randomUUID().toString(), code), newContext()) } },
            onCreateNestBox = { cageCode, boxCode ->
                run {
                    val cageId = ops.cages().firstOrNull { it.code == cageCode }?.id ?: error("Cage not found")
                    selectedCageId = cageId
                    ops.createNestBox(CreateRabbitNestBox(UUID.randomUUID().toString(), cageId, boxCode), newContext())
                }
            },
            onCreateWave = { cageCode, doeCount, matingDay ->
                run {
                    val cageId = ops.cages().firstOrNull { it.code == cageCode }?.id ?: error("Cage not found")
                    selectedCageId = cageId
                    ops.createWave(
                        CreateRabbitWave(
                            waveId = UUID.randomUUID().toString(),
                            cageId = cageId,
                            doeCount = doeCount,
                            matingEpochDay = LocalDate.parse(matingDay).toEpochDay(),
                            placeTaskId = UUID.randomUUID().toString(),
                            kindlingTaskId = UUID.randomUUID().toString(),
                            removeTaskId = UUID.randomUUID().toString(),
                            rebreedTaskId = UUID.randomUUID().toString(),
                            weanTaskId = UUID.randomUUID().toString(),
                        ),
                        newContext(),
                    )
                }
            },
            onPalpate = { waveId, result, day ->
                run {
                    ops.recordPalpation(
                        RecordRabbitPalpation(UUID.randomUUID().toString(), waveId, result, LocalDate.parse(day).toEpochDay()),
                        newContext(),
                    )
                }
            },
            onKindle = { waveId, live, dead, day ->
                run {
                    ops.recordKindling(
                        RecordRabbitKindling(
                            UUID.randomUUID().toString(),
                            waveId,
                            live.toIntOrNull() ?: 0,
                            dead.toIntOrNull() ?: 0,
                            LocalDate.parse(day).toEpochDay(),
                        ),
                        newContext(),
                    )
                }
            },
            onFoster = { fromWave, toWave, kits, day, ack ->
                run {
                    ops.recordFoster(
                        RecordRabbitFoster(
                            fosterId = UUID.randomUUID().toString(),
                            fromWaveId = fromWave,
                            toWaveId = toWave,
                            kitCount = kits.toIntOrNull() ?: 0,
                            occurredEpochDay = LocalDate.parse(day).toEpochDay(),
                            ackOutsideWindow = ack,
                        ),
                        newContext(),
                    )
                }
            },
            nestBoxes = nestBoxRows,
            onSetNestStatus = { boxId, status ->
                run { ops.setNestBoxStatus(com.farmos.domain.rabbit.SetRabbitNestBoxStatus(boxId, status), newContext()) }
            },
            onWean = { waveId, count, day ->
                run {
                    ops.recordWean(
                        com.farmos.domain.rabbit.RecordRabbitWean(
                            UUID.randomUUID().toString(),
                            waveId,
                            count.toIntOrNull() ?: 0,
                            LocalDate.parse(day).toEpochDay(),
                        ),
                        newContext(),
                    )
                }
            },
            onRecordOutcome = { waveId, outcome, day ->
                run {
                    ops.recordMatingOutcome(
                        com.farmos.domain.rabbit.RecordRabbitMatingOutcome(
                            UUID.randomUUID().toString(),
                            waveId,
                            outcome,
                            LocalDate.parse(day).toEpochDay(),
                        ),
                        newContext(),
                    )
                }
            },
            onRecordGiStasis = { animalId, signs, day ->
                run {
                    ops.recordGiStasis(
                        RecordRabbitGiStasis(
                            flagId = UUID.randomUUID().toString(),
                            animalId = animalId,
                            signs = signs,
                            occurredEpochDay = LocalDate.parse(day).toEpochDay(),
                            taskId = UUID.randomUUID().toString(),
                        ),
                        newContext(),
                    )
                }
            },
            onBack = onBack,
        )
        FarmModule.TASKS -> TasksBoardScreen(
            rows = taskRows,
            busy = busy,
            error = error,
            onCreate = { title, moduleCode, code, due ->
                run {
                    ops.createTask(
                        CreateFarmTask(
                            taskId = UUID.randomUUID().toString(),
                            moduleCode = moduleCode,
                            taskCode = code,
                            title = title,
                            dueEpochDay = LocalDate.parse(due).toEpochDay(),
                        ),
                        newContext(),
                    )
                }
            },
            onComplete = { taskId -> run { ops.completeTask(CompleteFarmTask(taskId), newContext()) } },
            onBack = onBack,
        )
        FarmModule.HEALTH -> HealthObservationScreen(
            rows = healthRows,
            catalog = catalogRows,
            treatments = treatmentRows,
            packs = packRows,
            withdrawals = withdrawalRows,
            busy = busy,
            error = error,
            onRecord = { species, signs, firstAid, redFlag ->
                run {
                    ops.recordObservation(
                        RecordHealthObservation(
                            observationId = UUID.randomUUID().toString(),
                            speciesCode = species,
                            signs = signs,
                            firstAidApplied = firstAid.trim().ifBlank { null },
                            redFlag = redFlag,
                            occurredAtEpochMillis = System.currentTimeMillis(),
                        ),
                        newContext(),
                    )
                }
            },
            onCreateFormulary = { product, species, vetClass, meatDays ->
                run {
                    ops.createFormulary(
                        CreateFormularyItem(
                            itemId = UUID.randomUUID().toString(),
                            productName = product,
                            speciesCode = species,
                            vetClass = vetClass,
                            meatWithdrawalDays = meatDays,
                            vetApproved = true,
                        ),
                        newContext(),
                    )
                }
            },
            onRecordTreatment = { species, formularyItemId, reason ->
                run {
                    ops.recordTreatment(
                        RecordHealthTreatment(
                            treatmentId = UUID.randomUUID().toString(),
                            speciesCode = species,
                            formularyItemId = formularyItemId,
                            reason = reason,
                            occurredAtEpochMillis = System.currentTimeMillis(),
                        ),
                        newContext(),
                    )
                }
            },
            onAcceptPack = { species, name, vet ->
                run {
                    ops.acceptPack(
                        AcceptHealthPack(UUID.randomUUID().toString(), species, name, vet),
                        newContext(),
                    )
                }
            },
            onRecordVetVisit = { species, reason, vet, day ->
                run {
                    ops.recordVetVisit(
                        RecordVetVisit(
                            visitId = UUID.randomUUID().toString(),
                            speciesCode = species,
                            reason = reason,
                            attendingVet = vet,
                            occurredEpochDay = LocalDate.parse(day).toEpochDay(),
                        ),
                        newContext(),
                    )
                }
            },
            onRecordLab = { animalId, testName, resultText, cells, day ->
                run {
                    ops.recordLab(
                        RecordLabResult(
                            resultId = UUID.randomUUID().toString(),
                            animalId = animalId.trim().ifBlank { null },
                            testName = testName,
                            resultText = resultText,
                            cellsPerMl = cells.toIntOrNull(),
                            occurredEpochDay = LocalDate.parse(day).toEpochDay(),
                        ),
                        newContext(),
                    )
                }
            },
            onAddPackSlot = { packId, slotCode, title, offset, fromEvent ->
                run {
                    ops.addPackSlot(
                        AddHealthPackSlot(
                            slotId = UUID.randomUUID().toString(),
                            packId = packId,
                            slotCode = slotCode,
                            title = title,
                            offsetDays = offset.toIntOrNull() ?: 0,
                            fromEvent = fromEvent,
                        ),
                        newContext(),
                    )
                }
            },
            onApplyPack = { packId, animalId, day ->
                run {
                    ops.applyPack(
                        ApplyHealthPack(
                            applyId = UUID.randomUUID().toString(),
                            packId = packId,
                            animalId = animalId.trim().ifBlank { null },
                            anchorEpochDay = LocalDate.parse(day).toEpochDay(),
                        ),
                        newContext(),
                    )
                }
            },
            onBack = onBack,
        )
        FarmModule.MONEY -> MoneyCaptureScreen(
            rows = moneyRows,
            busy = busy,
            error = error,
            onRecord = { kind, category, amount, day ->
                run {
                    val major = amount.replace(',', '.').toDoubleOrNull() ?: error("Enter an amount")
                    ops.recordMoney(
                        RecordMoney(
                            recordId = UUID.randomUUID().toString(),
                            kind = kind,
                            categoryCode = category,
                            amountMinor = (major * 100.0).toLong(),
                            occurredEpochDay = LocalDate.parse(day).toEpochDay(),
                        ),
                        newContext(),
                    )
                }
            },
            onBack = onBack,
        )
        FarmModule.INVENTORY -> InventoryScreen(
            rows = inventoryRows,
            busy = busy,
            error = error,
            onCreate = { sku, name, unit ->
                run { ops.createItem(CreateInventoryItem(UUID.randomUUID().toString(), sku, name, unit), newContext()) }
            },
            onMove = { itemId, direction, quantity ->
                run {
                    val qty = quantity.replace(',', '.').toDoubleOrNull() ?: error("Enter a quantity")
                    ops.move(
                        MoveInventory(
                            movementId = UUID.randomUUID().toString(),
                            itemId = itemId,
                            direction = direction,
                            quantityMilli = (qty * 1000.0).toLong(),
                            occurredAtEpochMillis = System.currentTimeMillis(),
                        ),
                        newContext(),
                    )
                }
            },
            onReceiveLot = { itemId, lotCode, expiry, quantity ->
                run {
                    val qty = quantity.replace(',', '.').toDoubleOrNull() ?: error("Enter a quantity")
                    ops.receiveLot(
                        ReceiveInventoryLot(
                            lotId = UUID.randomUUID().toString(),
                            itemId = itemId,
                            lotCode = lotCode,
                            expiresEpochDay = LocalDate.parse(expiry).toEpochDay(),
                            quantityMilli = (qty * 1000.0).toLong(),
                        ),
                        newContext(),
                    )
                }
            },
            onIssueLot = { itemId, quantity ->
                run {
                    val qty = quantity.replace(',', '.').toDoubleOrNull() ?: error("Enter a quantity")
                    ops.issueLot(
                        IssueInventoryLot(
                            issueId = UUID.randomUUID().toString(),
                            itemId = itemId,
                            quantityMilli = (qty * 1000.0).toLong(),
                        ),
                        newContext(),
                    )
                }
            },
            onSetReorder = { itemId, quantity ->
                run {
                    val qty = quantity.replace(',', '.').toDoubleOrNull() ?: error("Enter a quantity")
                    ops.setReorder(
                        SetInventoryReorder(itemId, (qty * 1000.0).toLong()),
                        newContext(),
                    )
                }
            },
            onRecordReorder = { itemId, day ->
                run {
                    ops.recordReorderAlert(
                        RecordReorderAlert(UUID.randomUUID().toString(), itemId, LocalDate.parse(day).toEpochDay()),
                        newContext(),
                    )
                }
            },
            onBack = onBack,
        )
        FarmModule.SHEEP, FarmModule.CATTLE, FarmModule.POULTRY -> {
            val title = when (module) {
                FarmModule.SHEEP -> "Sheep flock"
                FarmModule.CATTLE -> "Cattle herd"
                else -> "Poultry flock"
            }
            SpeciesHerdScreen(
                title = title,
                femaleLabel = when (module) {
                    FarmModule.SHEEP -> "Ewe"
                    FarmModule.CATTLE -> "Cow"
                    else -> "Female"
                },
                maleLabel = when (module) {
                    FarmModule.SHEEP -> "Ram"
                    FarmModule.CATTLE -> "Bull"
                    else -> "Male"
                },
                kindRequired = module == FarmModule.POULTRY,
                rows = speciesRows,
                busy = busy,
                error = error,
                onRegister = { tag, name, sex, kind ->
                    run {
                        herd?.register(UUID.randomUUID().toString(), tag, name, sex, kind, newContext())
                    }
                },
                onRecordWeight = { animalId, weightText ->
                    run {
                        val kg = weightText.replace(',', '.').toDoubleOrNull()
                            ?: error("Enter a valid weight in kg")
                        herd?.recordWeight(
                            animalId = animalId,
                            measurementId = UUID.randomUUID().toString(),
                            weightGrams = (kg * 1_000.0).toLong(),
                            measuredAtEpochMillis = System.currentTimeMillis(),
                            context = newContext(),
                        )
                    }
                },
                onSetStatus = { animalId, status ->
                    run { herd?.setStatus(animalId, status, newContext()) }
                },
                onBack = onBack,
                extra = { selected ->
                    when (module) {
                        FarmModule.SHEEP -> {
                            val groupId = remember { mutableStateOf("") }
                            val joiningDay = remember { mutableStateOf("") }
                            val scanResult = remember { mutableStateOf("single") }
                            val scanDay = remember { mutableStateOf("") }
                            val born = remember { mutableStateOf("") }
                            val live = remember { mutableStateOf("") }
                            val dead = remember { mutableStateOf("0") }
                            val lambingDay = remember { mutableStateOf("") }
                            androidx.compose.material3.HorizontalDivider()
                            androidx.compose.material3.Text("Joining, scanning, lambing")
                            androidx.compose.material3.OutlinedTextField(groupId.value, { groupId.value = it }, label = { androidx.compose.material3.Text("Sheep mob id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(joiningDay.value, { joiningDay.value = it }, label = { androidx.compose.material3.Text("Joining start") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    run {
                                        ops.recordJoining(
                                            RecordSheepJoining(
                                                joiningId = UUID.randomUUID().toString(),
                                                groupId = groupId.value,
                                                startedEpochDay = LocalDate.parse(joiningDay.value).toEpochDay(),
                                                scanTaskId = UUID.randomUUID().toString(),
                                                preLambTaskId = UUID.randomUUID().toString(),
                                                paddockTaskId = UUID.randomUUID().toString(),
                                                lambingTaskId = UUID.randomUUID().toString(),
                                            ),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && groupId.value.isNotBlank() && joiningDay.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Record joining") }
                            androidx.compose.material3.OutlinedTextField(scanResult.value, { scanResult.value = it }, label = { androidx.compose.material3.Text("Scan result") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(scanDay.value, { scanDay.value = it }, label = { androidx.compose.material3.Text("Scan date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    val animalId = selected?.animalId ?: error("Select a ewe")
                                    run {
                                        ops.recordScan(
                                            RecordSheepScan(UUID.randomUUID().toString(), animalId, scanResult.value, LocalDate.parse(scanDay.value).toEpochDay()),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && selected != null && scanDay.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Record scan") }
                            androidx.compose.material3.OutlinedTextField(born.value, { born.value = it }, label = { androidx.compose.material3.Text("Born") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(live.value, { live.value = it }, label = { androidx.compose.material3.Text("Live") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(dead.value, { dead.value = it }, label = { androidx.compose.material3.Text("Dead") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(lambingDay.value, { lambingDay.value = it }, label = { androidx.compose.material3.Text("Lambing date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    val animalId = selected?.animalId ?: error("Select a ewe")
                                    run {
                                        ops.recordLambing(
                                            RecordSheepLambing(
                                                UUID.randomUUID().toString(),
                                                animalId,
                                                born.value.toIntOrNull() ?: 0,
                                                live.value.toIntOrNull() ?: 0,
                                                dead.value.toIntOrNull() ?: 0,
                                                LocalDate.parse(lambingDay.value).toEpochDay(),
                                            ),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && selected != null && lambingDay.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Record lambing") }
                            val markCount = remember { mutableStateOf("") }
                            val markDay = remember { mutableStateOf("") }
                            val weanCount = remember { mutableStateOf("") }
                            val weanDay = remember { mutableStateOf("") }
                            val woolGrams = remember { mutableStateOf("") }
                            val woolDay = remember { mutableStateOf("") }
                            androidx.compose.material3.OutlinedTextField(markCount.value, { markCount.value = it }, label = { androidx.compose.material3.Text("Marked count") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(markDay.value, { markDay.value = it }, label = { androidx.compose.material3.Text("Marking date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    run {
                                        ops.recordMarking(
                                            RecordSheepMarking(
                                                markingId = UUID.randomUUID().toString(),
                                                groupId = groupId.value.trim().ifBlank { null },
                                                animalId = selected?.animalId,
                                                markedCount = markCount.value.toIntOrNull() ?: 0,
                                                occurredEpochDay = LocalDate.parse(markDay.value).toEpochDay(),
                                            ),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && markCount.value.isNotBlank() && markDay.value.isNotBlank() && (selected != null || groupId.value.isNotBlank()),
                            ) { androidx.compose.material3.Text("Record marking") }
                            androidx.compose.material3.OutlinedTextField(weanCount.value, { weanCount.value = it }, label = { androidx.compose.material3.Text("Weaned count") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(weanDay.value, { weanDay.value = it }, label = { androidx.compose.material3.Text("Weaning date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    run {
                                        ops.recordSheepWeaning(
                                            RecordSheepWeaning(
                                                weaningId = UUID.randomUUID().toString(),
                                                groupId = groupId.value.trim().ifBlank { null },
                                                animalId = selected?.animalId,
                                                weanedCount = weanCount.value.toIntOrNull() ?: 0,
                                                occurredEpochDay = LocalDate.parse(weanDay.value).toEpochDay(),
                                            ),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && weanCount.value.isNotBlank() && weanDay.value.isNotBlank() && (selected != null || groupId.value.isNotBlank()),
                            ) { androidx.compose.material3.Text("Record weaning") }
                            androidx.compose.material3.OutlinedTextField(woolGrams.value, { woolGrams.value = it }, label = { androidx.compose.material3.Text("Greasy grams") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(woolDay.value, { woolDay.value = it }, label = { androidx.compose.material3.Text("Clip date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    run {
                                        ops.recordWool(
                                            RecordSheepWool(
                                                clipId = UUID.randomUUID().toString(),
                                                animalId = selected?.animalId,
                                                groupId = groupId.value.trim().ifBlank { null },
                                                greasyGrams = woolGrams.value.toIntOrNull() ?: 0,
                                                occurredEpochDay = LocalDate.parse(woolDay.value).toEpochDay(),
                                            ),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && woolGrams.value.isNotBlank() && woolDay.value.isNotBlank() && (selected != null || groupId.value.isNotBlank()),
                            ) { androidx.compose.material3.Text("Record wool clip") }
                            val dag = remember { mutableStateOf("") }
                            val footrot = remember { mutableStateOf("") }
                            val scoreDay = remember { mutableStateOf("") }
                            androidx.compose.material3.OutlinedTextField(dag.value, { dag.value = it }, label = { androidx.compose.material3.Text("Dag score") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(footrot.value, { footrot.value = it }, label = { androidx.compose.material3.Text("Footrot score") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(scoreDay.value, { scoreDay.value = it }, label = { androidx.compose.material3.Text("Score date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    val animalId = selected?.animalId ?: error("Select a sheep")
                                    run {
                                        ops.recordDag(
                                            RecordSheepDag(UUID.randomUUID().toString(), animalId, dag.value.toIntOrNull() ?: -1, LocalDate.parse(scoreDay.value).toEpochDay()),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && selected != null && dag.value.isNotBlank() && scoreDay.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Record dag") }
                            androidx.compose.material3.Button(
                                onClick = {
                                    val animalId = selected?.animalId ?: error("Select a sheep")
                                    run {
                                        ops.recordFootrot(
                                            RecordSheepFootrot(UUID.randomUUID().toString(), animalId, footrot.value.toIntOrNull() ?: -1, LocalDate.parse(scoreDay.value).toEpochDay()),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && selected != null && footrot.value.isNotBlank() && scoreDay.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Record footrot") }
                            val shearKind = remember { mutableStateOf("shearing") }
                            val shearGrams = remember { mutableStateOf("") }
                            val shearDay = remember { mutableStateOf("") }
                            androidx.compose.material3.OutlinedTextField(shearKind.value, { shearKind.value = it }, label = { androidx.compose.material3.Text("Shearing kind") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(shearGrams.value, { shearGrams.value = it }, label = { androidx.compose.material3.Text("Greasy grams") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(shearDay.value, { shearDay.value = it }, label = { androidx.compose.material3.Text("Shearing date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    run {
                                        ops.recordShearing(
                                            RecordSheepShearing(
                                                eventId = UUID.randomUUID().toString(),
                                                animalId = selected?.animalId,
                                                groupId = groupId.value.trim().ifBlank { null },
                                                kind = shearKind.value,
                                                greasyGrams = shearGrams.value.toIntOrNull(),
                                                occurredEpochDay = LocalDate.parse(shearDay.value).toEpochDay(),
                                            ),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && shearDay.value.isNotBlank() && (selected != null || groupId.value.isNotBlank()),
                            ) { androidx.compose.material3.Text("Record shearing") }
                            val fly = remember { mutableStateOf("") }
                            val flyDay = remember { mutableStateOf("") }
                            androidx.compose.material3.OutlinedTextField(fly.value, { fly.value = it }, label = { androidx.compose.material3.Text("Flystrike 0-5") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(flyDay.value, { flyDay.value = it }, label = { androidx.compose.material3.Text("Flystrike date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    val animalId = selected?.animalId ?: error("Select a sheep")
                                    run {
                                        ops.recordFlystrike(
                                            RecordSheepFlystrike(UUID.randomUUID().toString(), animalId, fly.value.toIntOrNull() ?: 0, occurredEpochDay = LocalDate.parse(flyDay.value).toEpochDay()),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && selected != null && fly.value.isNotBlank() && flyDay.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Record flystrike") }
                            val sheepFamacha = remember { mutableStateOf("") }
                            val micronTenths = remember { mutableStateOf("") }
                            val idType = remember { mutableStateOf("ear_tag") }
                            val idValue = remember { mutableStateOf("") }
                            val moveDir = remember { mutableStateOf("on") }
                            val moveFrom = remember { mutableStateOf("") }
                            val moveTo = remember { mutableStateOf("") }
                            val parentId = remember { mutableStateOf("") }
                            val relation = remember { mutableStateOf("sire") }
                            androidx.compose.material3.OutlinedTextField(sheepFamacha.value, { sheepFamacha.value = it }, label = { androidx.compose.material3.Text("FAMACHA 1-5") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    val animalId = selected?.animalId ?: error("Select a sheep")
                                    run {
                                        ops.recordSheepFamacha(
                                            RecordFamacha(UUID.randomUUID().toString(), animalId, sheepFamacha.value.toIntOrNull() ?: 0, LocalDate.parse(scoreDay.value).toEpochDay()),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && selected != null && sheepFamacha.value.isNotBlank() && scoreDay.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Record sheep FAMACHA") }
                            androidx.compose.material3.OutlinedTextField(micronTenths.value, { micronTenths.value = it }, label = { androidx.compose.material3.Text("Micron tenths 80-500") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    run {
                                        ops.recordMicron(
                                            RecordSheepMicron(
                                                UUID.randomUUID().toString(),
                                                selected?.animalId,
                                                groupId.value.trim().ifBlank { null },
                                                micronTenths.value.toIntOrNull() ?: 0,
                                                LocalDate.parse(scoreDay.value).toEpochDay(),
                                            ),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && micronTenths.value.isNotBlank() && scoreDay.value.isNotBlank() && (selected != null || groupId.value.isNotBlank()),
                            ) { androidx.compose.material3.Text("Record micron") }
                            androidx.compose.material3.OutlinedTextField(idType.value, { idType.value = it }, label = { androidx.compose.material3.Text("Identifier type") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(idValue.value, { idValue.value = it }, label = { androidx.compose.material3.Text("Identifier value") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    val animalId = selected?.animalId ?: error("Select a sheep")
                                    run {
                                        ops.assignIdentifier(
                                            AssignAnimalIdentifier(UUID.randomUUID().toString(), animalId, idType.value, idValue.value, LocalDate.parse(scoreDay.value).toEpochDay()),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && selected != null && idValue.value.isNotBlank() && scoreDay.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Assign identifier") }
                            androidx.compose.material3.OutlinedTextField(moveDir.value, { moveDir.value = it }, label = { androidx.compose.material3.Text("Movement on/off/transfer") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(moveFrom.value, { moveFrom.value = it }, label = { androidx.compose.material3.Text("From place") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(moveTo.value, { moveTo.value = it }, label = { androidx.compose.material3.Text("To place") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    val animalId = selected?.animalId ?: error("Select a sheep")
                                    run {
                                        ops.recordOfficialMovement(
                                            RecordOfficialMovement(
                                                UUID.randomUUID().toString(),
                                                animalId,
                                                moveDir.value,
                                                moveFrom.value.trim().ifBlank { null },
                                                moveTo.value.trim().ifBlank { null },
                                                LocalDate.parse(scoreDay.value).toEpochDay(),
                                            ),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && selected != null && scoreDay.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Record official movement") }
                            androidx.compose.material3.OutlinedTextField(parentId.value, { parentId.value = it }, label = { androidx.compose.material3.Text("Parent id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(relation.value, { relation.value = it }, label = { androidx.compose.material3.Text("Pedigree relation") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    val animalId = selected?.animalId ?: error("Select a sheep")
                                    run {
                                        ops.linkPedigree(
                                            LinkPedigree(UUID.randomUUID().toString(), animalId, parentId.value, relation.value),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && selected != null && parentId.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Link pedigree") }
                        }
                        FarmModule.CATTLE -> {
                            val method = remember { mutableStateOf("ai") }
                            val serviceDay = remember { mutableStateOf("") }
                            val pdResult = remember { mutableStateOf("pregnant") }
                            val pdDay = remember { mutableStateOf("") }
                            val born = remember { mutableStateOf("") }
                            val live = remember { mutableStateOf("") }
                            val dead = remember { mutableStateOf("0") }
                            val calvingDay = remember { mutableStateOf("") }
                            androidx.compose.material3.HorizontalDivider()
                            androidx.compose.material3.Text("Service, PD, calving")
                            androidx.compose.material3.OutlinedTextField(method.value, { method.value = it }, label = { androidx.compose.material3.Text("Service method") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(serviceDay.value, { serviceDay.value = it }, label = { androidx.compose.material3.Text("Service date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    val animalId = selected?.animalId ?: error("Select a cow")
                                    run {
                                        ops.recordCattleService(
                                            RecordCattleService(
                                                serviceId = UUID.randomUUID().toString(),
                                                animalId = animalId,
                                                method = method.value,
                                                occurredEpochDay = LocalDate.parse(serviceDay.value).toEpochDay(),
                                                pdTaskId = UUID.randomUUID().toString(),
                                                paddockTaskId = UUID.randomUUID().toString(),
                                                calvingTaskId = UUID.randomUUID().toString(),
                                            ),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && selected != null && serviceDay.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Record service") }
                            androidx.compose.material3.OutlinedTextField(pdResult.value, { pdResult.value = it }, label = { androidx.compose.material3.Text("PD result") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(pdDay.value, { pdDay.value = it }, label = { androidx.compose.material3.Text("PD date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    val animalId = selected?.animalId ?: error("Select a cow")
                                    run {
                                        ops.recordCattlePd(
                                            RecordCattlePd(UUID.randomUUID().toString(), animalId, pdResult.value, LocalDate.parse(pdDay.value).toEpochDay()),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && selected != null && pdDay.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Record PD") }
                            androidx.compose.material3.OutlinedTextField(born.value, { born.value = it }, label = { androidx.compose.material3.Text("Born") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(live.value, { live.value = it }, label = { androidx.compose.material3.Text("Live") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(dead.value, { dead.value = it }, label = { androidx.compose.material3.Text("Dead") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(calvingDay.value, { calvingDay.value = it }, label = { androidx.compose.material3.Text("Calving date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    val animalId = selected?.animalId ?: error("Select a cow")
                                    run {
                                        ops.recordCalving(
                                            RecordCattleCalving(
                                                UUID.randomUUID().toString(),
                                                animalId,
                                                born.value.toIntOrNull() ?: 0,
                                                live.value.toIntOrNull() ?: 0,
                                                dead.value.toIntOrNull() ?: 0,
                                                LocalDate.parse(calvingDay.value).toEpochDay(),
                                            ),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && selected != null && calvingDay.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Record calving") }
                            val scale = remember { mutableStateOf("1_5") }
                            val score = remember { mutableStateOf("") }
                            val bcsDay = remember { mutableStateOf("") }
                            androidx.compose.material3.OutlinedTextField(scale.value, { scale.value = it }, label = { androidx.compose.material3.Text("BCS scale") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(score.value, { score.value = it }, label = { androidx.compose.material3.Text("Score tenths") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(bcsDay.value, { bcsDay.value = it }, label = { androidx.compose.material3.Text("BCS date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    val animalId = selected?.animalId ?: error("Select a cow")
                                    run {
                                        ops.recordCattleBcs(
                                            RecordCattleBcs(
                                                UUID.randomUUID().toString(),
                                                animalId,
                                                scale.value,
                                                score.value.toIntOrNull() ?: 0,
                                                LocalDate.parse(bcsDay.value).toEpochDay(),
                                            ),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && selected != null && score.value.isNotBlank() && bcsDay.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Record BCS") }
                            val milkLitres = remember { mutableStateOf("") }
                            val milkDay = remember { mutableStateOf("") }
                            androidx.compose.material3.OutlinedTextField(milkLitres.value, { milkLitres.value = it }, label = { androidx.compose.material3.Text("Milk litres") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(milkDay.value, { milkDay.value = it }, label = { androidx.compose.material3.Text("Milk date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    val animalId = selected?.animalId ?: error("Select a cow")
                                    val litres = milkLitres.value.replace(',', '.').toDoubleOrNull() ?: error("Enter litres")
                                    run {
                                        ops.recordCattleMilk(
                                            RecordCattleMilk(
                                                UUID.randomUUID().toString(),
                                                animalId,
                                                (litres * 1000.0).toLong(),
                                                LocalDate.parse(milkDay.value).toEpochDay(),
                                            ),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && selected != null && milkLitres.value.isNotBlank() && milkDay.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Record milk") }
                            val loco = remember { mutableStateOf("") }
                            val scc = remember { mutableStateOf("") }
                            val dim = remember { mutableStateOf("") }
                            val scoreDay2 = remember { mutableStateOf("") }
                            androidx.compose.material3.OutlinedTextField(loco.value, { loco.value = it }, label = { androidx.compose.material3.Text("Locomotion 1-5") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(scc.value, { scc.value = it }, label = { androidx.compose.material3.Text("SCC cells/ml") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(dim.value, { dim.value = it }, label = { androidx.compose.material3.Text("DIM days") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(scoreDay2.value, { scoreDay2.value = it }, label = { androidx.compose.material3.Text("Score date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    val animalId = selected?.animalId ?: error("Select a cow")
                                    run {
                                        ops.recordLocomotion(
                                            RecordCattleLocomotion(UUID.randomUUID().toString(), animalId, loco.value.toIntOrNull() ?: 0, LocalDate.parse(scoreDay2.value).toEpochDay()),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && selected != null && loco.value.isNotBlank() && scoreDay2.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Record locomotion") }
                            androidx.compose.material3.Button(
                                onClick = {
                                    val animalId = selected?.animalId ?: error("Select a cow")
                                    run {
                                        ops.recordScc(
                                            RecordCattleScc(
                                                UUID.randomUUID().toString(),
                                                animalId,
                                                scc.value.toIntOrNull() ?: 0,
                                                dim.value.toIntOrNull(),
                                                LocalDate.parse(scoreDay2.value).toEpochDay(),
                                            ),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && selected != null && scc.value.isNotBlank() && scoreDay2.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Record SCC") }
                            val dryDay = remember { mutableStateOf("") }
                            val calvingDay2 = remember { mutableStateOf("") }
                            androidx.compose.material3.OutlinedTextField(dryDay.value, { dryDay.value = it }, label = { androidx.compose.material3.Text("Dry-off date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(calvingDay2.value, { calvingDay2.value = it }, label = { androidx.compose.material3.Text("Expected calving") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    val animalId = selected?.animalId ?: error("Select a cow")
                                    run {
                                        ops.recordDryOff(
                                            RecordCattleDryOff(
                                                UUID.randomUUID().toString(),
                                                animalId,
                                                LocalDate.parse(dryDay.value).toEpochDay(),
                                                calvingDay2.value.trim().takeIf { it.isNotBlank() }?.let { LocalDate.parse(it).toEpochDay() },
                                            ),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && selected != null && dryDay.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Record dry-off") }
                            val weanGrams = remember { mutableStateOf("") }
                            val cattleIdType = remember { mutableStateOf("nlis") }
                            val cattleIdValue = remember { mutableStateOf("") }
                            val cattleMoveDir = remember { mutableStateOf("on") }
                            val cattleFrom = remember { mutableStateOf("") }
                            val cattleTo = remember { mutableStateOf("") }
                            val cattleParent = remember { mutableStateOf("") }
                            val cattleRel = remember { mutableStateOf("sire") }
                            androidx.compose.material3.OutlinedTextField(weanGrams.value, { weanGrams.value = it }, label = { androidx.compose.material3.Text("Wean grams") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    run {
                                        ops.recordCattleWeaning(
                                            RecordCattleWeaning(
                                                UUID.randomUUID().toString(),
                                                selected?.animalId,
                                                null,
                                                weanGrams.value.toLongOrNull(),
                                                LocalDate.parse(dryDay.value).toEpochDay(),
                                            ),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && selected != null && dryDay.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Record weaning") }
                            androidx.compose.material3.OutlinedTextField(cattleIdType.value, { cattleIdType.value = it }, label = { androidx.compose.material3.Text("Identifier type") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(cattleIdValue.value, { cattleIdValue.value = it }, label = { androidx.compose.material3.Text("Identifier value") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    val animalId = selected?.animalId ?: error("Select a cow")
                                    run {
                                        ops.assignIdentifier(
                                            AssignAnimalIdentifier(UUID.randomUUID().toString(), animalId, cattleIdType.value, cattleIdValue.value, LocalDate.parse(dryDay.value).toEpochDay()),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && selected != null && cattleIdValue.value.isNotBlank() && dryDay.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Assign identifier") }
                            androidx.compose.material3.OutlinedTextField(cattleMoveDir.value, { cattleMoveDir.value = it }, label = { androidx.compose.material3.Text("Movement on/off/transfer") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(cattleFrom.value, { cattleFrom.value = it }, label = { androidx.compose.material3.Text("From place") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(cattleTo.value, { cattleTo.value = it }, label = { androidx.compose.material3.Text("To place") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    val animalId = selected?.animalId ?: error("Select a cow")
                                    run {
                                        ops.recordOfficialMovement(
                                            RecordOfficialMovement(
                                                UUID.randomUUID().toString(),
                                                animalId,
                                                cattleMoveDir.value,
                                                cattleFrom.value.trim().ifBlank { null },
                                                cattleTo.value.trim().ifBlank { null },
                                                LocalDate.parse(dryDay.value).toEpochDay(),
                                            ),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && selected != null && dryDay.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Record official movement") }
                            androidx.compose.material3.OutlinedTextField(cattleParent.value, { cattleParent.value = it }, label = { androidx.compose.material3.Text("Parent id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(cattleRel.value, { cattleRel.value = it }, label = { androidx.compose.material3.Text("Pedigree relation") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    val animalId = selected?.animalId ?: error("Select a cow")
                                    run {
                                        ops.linkPedigree(
                                            LinkPedigree(UUID.randomUUID().toString(), animalId, cattleParent.value, cattleRel.value),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && selected != null && cattleParent.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Link pedigree") }
                            val lotId = remember { mutableStateOf("") }
                            val lotHeads = remember { mutableStateOf("") }
                            val dofDays = remember { mutableStateOf("") }
                            val closeHeads = remember { mutableStateOf("") }
                            androidx.compose.material3.OutlinedTextField(lotId.value, { lotId.value = it }, label = { androidx.compose.material3.Text("Cattle lot id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(lotHeads.value, { lotHeads.value = it }, label = { androidx.compose.material3.Text("Heads on feed") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    run {
                                        ops.placeCattleLot(
                                            PlaceCattleLot(UUID.randomUUID().toString(), lotId.value, lotHeads.value.toIntOrNull() ?: 0, LocalDate.parse(dryDay.value).toEpochDay()),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && lotId.value.isNotBlank() && lotHeads.value.isNotBlank() && dryDay.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Place lot on feed") }
                            androidx.compose.material3.OutlinedTextField(dofDays.value, { dofDays.value = it }, label = { androidx.compose.material3.Text("Days on feed") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    run {
                                        ops.recordDaysOnFeed(
                                            RecordCattleDaysOnFeed(UUID.randomUUID().toString(), lotId.value, dofDays.value.toIntOrNull() ?: -1, LocalDate.parse(dryDay.value).toEpochDay()),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && lotId.value.isNotBlank() && dofDays.value.isNotBlank() && dryDay.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Record days on feed") }
                            androidx.compose.material3.OutlinedTextField(closeHeads.value, { closeHeads.value = it }, label = { androidx.compose.material3.Text("Head out") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    run {
                                        ops.closeCattleLot(
                                            CloseCattleLot(
                                                UUID.randomUUID().toString(),
                                                lotId.value,
                                                closeHeads.value.toIntOrNull() ?: 0,
                                                weanGrams.value.toLongOrNull(),
                                                dofDays.value.toIntOrNull(),
                                                LocalDate.parse(dryDay.value).toEpochDay(),
                                            ),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && lotId.value.isNotBlank() && closeHeads.value.isNotBlank() && dryDay.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Close lot") }
                        }
                        FarmModule.POULTRY -> {
                            val houseCode = remember { mutableStateOf("") }
                            val houseKind = remember { mutableStateOf("house") }
                            val poultryKind = remember { mutableStateOf("chicken") }
                            val eggs = remember { mutableStateOf("") }
                            val setDay = remember { mutableStateOf("") }
                            val hatchId = remember { mutableStateOf("") }
                            val fertile = remember { mutableStateOf("") }
                            val infertile = remember { mutableStateOf("") }
                            val midDead = remember { mutableStateOf("") }
                            val hatched = remember { mutableStateOf("") }
                            val culls = remember { mutableStateOf("0") }
                            androidx.compose.material3.HorizontalDivider()
                            androidx.compose.material3.Text("Hatchery uses kind incubation. Chicken 21, duck 28, muscovy 35, quail 17.")
                            houseRows.forEach { androidx.compose.material3.Text(it) }
                            hatchRows.forEach { androidx.compose.material3.Text(it) }
                            if (houseRows.isEmpty() && hatchRows.isEmpty()) {
                                androidx.compose.material3.Text("No houses or hatches on this device.")
                            }
                            androidx.compose.material3.OutlinedTextField(houseCode.value, { houseCode.value = it }, label = { androidx.compose.material3.Text("House code") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(houseKind.value, { houseKind.value = it }, label = { androidx.compose.material3.Text("Housing kind") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(poultryKind.value, { poultryKind.value = it }, label = { androidx.compose.material3.Text("Poultry kind") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    run {
                                        ops.createHouse(
                                            CreatePoultryHouse(UUID.randomUUID().toString(), houseCode.value, houseKind.value, poultryKind.value),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && houseCode.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Create house") }
                            androidx.compose.material3.OutlinedTextField(eggs.value, { eggs.value = it }, label = { androidx.compose.material3.Text("Eggs set") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(setDay.value, { setDay.value = it }, label = { androidx.compose.material3.Text("Set date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    run {
                                        ops.setHatch(
                                            SetPoultryHatch(
                                                hatchId = UUID.randomUUID().toString(),
                                                poultryKindCode = poultryKind.value,
                                                eggsSet = eggs.value.toIntOrNull() ?: 0,
                                                setEpochDay = LocalDate.parse(setDay.value).toEpochDay(),
                                                candleTaskId = UUID.randomUUID().toString(),
                                                lockTaskId = UUID.randomUUID().toString(),
                                                hatchTaskId = UUID.randomUUID().toString(),
                                            ),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && eggs.value.isNotBlank() && setDay.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Set eggs") }
                            androidx.compose.material3.OutlinedTextField(hatchId.value, { hatchId.value = it }, label = { androidx.compose.material3.Text("Hatch id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(fertile.value, { fertile.value = it }, label = { androidx.compose.material3.Text("Fertile") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(infertile.value, { infertile.value = it }, label = { androidx.compose.material3.Text("Infertile") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(midDead.value, { midDead.value = it }, label = { androidx.compose.material3.Text("Mid-dead") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    run {
                                        ops.candleHatch(
                                            CandlePoultryHatch(
                                                hatchId.value,
                                                fertile.value.toIntOrNull() ?: 0,
                                                infertile.value.toIntOrNull() ?: 0,
                                                midDead.value.toIntOrNull() ?: 0,
                                                LocalDate.parse(setDay.value).toEpochDay(),
                                            ),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && hatchId.value.isNotBlank() && setDay.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Record candling") }
                            androidx.compose.material3.OutlinedTextField(hatched.value, { hatched.value = it }, label = { androidx.compose.material3.Text("Hatched") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(culls.value, { culls.value = it }, label = { androidx.compose.material3.Text("Culls") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    run {
                                        ops.recordHatch(
                                            RecordPoultryHatch(
                                                hatchId.value,
                                                hatched.value.toIntOrNull() ?: 0,
                                                culls.value.toIntOrNull() ?: 0,
                                                occurredEpochDay = LocalDate.parse(setDay.value).toEpochDay(),
                                            ),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && hatchId.value.isNotBlank() && hatched.value.isNotBlank() && setDay.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Record hatch") }
                            val vaxGroup = remember { mutableStateOf("") }
                            val vaxForm = remember { mutableStateOf("") }
                            val vaxDay = remember { mutableStateOf("") }
                            androidx.compose.material3.Text("Vaccination uses a vet-approved poultry formulary item. This is not a diagnosis.")
                            vaxRows.forEach { androidx.compose.material3.Text(it) }
                            androidx.compose.material3.OutlinedTextField(vaxGroup.value, { vaxGroup.value = it }, label = { androidx.compose.material3.Text("Flock group id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(vaxForm.value, { vaxForm.value = it }, label = { androidx.compose.material3.Text("Formulary item id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(vaxDay.value, { vaxDay.value = it }, label = { androidx.compose.material3.Text("Vaccination date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    run {
                                        ops.recordVaccination(
                                            RecordPoultryVaccination(
                                                UUID.randomUUID().toString(),
                                                vaxGroup.value,
                                                poultryKind.value,
                                                vaxForm.value,
                                                LocalDate.parse(vaxDay.value).toEpochDay(),
                                            ),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && vaxGroup.value.isNotBlank() && vaxForm.value.isNotBlank() && vaxDay.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Record vaccination") }
                            val placeGroup = remember { mutableStateOf("") }
                            val placeHouse = remember { mutableStateOf("") }
                            val heads = remember { mutableStateOf("") }
                            val findings = remember { mutableStateOf("") }
                            androidx.compose.material3.OutlinedTextField(placeGroup.value, { placeGroup.value = it }, label = { androidx.compose.material3.Text("Place flock id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(placeHouse.value, { placeHouse.value = it }, label = { androidx.compose.material3.Text("House id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(heads.value, { heads.value = it }, label = { androidx.compose.material3.Text("Head count") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    run {
                                        ops.placeFlock(
                                            PlacePoultryFlock(
                                                UUID.randomUUID().toString(),
                                                placeGroup.value,
                                                placeHouse.value,
                                                poultryKind.value,
                                                heads.value.toIntOrNull() ?: 0,
                                                LocalDate.parse(setDay.value).toEpochDay(),
                                                UUID.randomUUID().toString(),
                                                UUID.randomUUID().toString(),
                                            ),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && placeGroup.value.isNotBlank() && placeHouse.value.isNotBlank() && heads.value.isNotBlank() && setDay.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Place flock") }
                            androidx.compose.material3.OutlinedTextField(findings.value, { findings.value = it }, label = { androidx.compose.material3.Text("Biosecurity findings") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            androidx.compose.material3.Button(
                                onClick = {
                                    run {
                                        ops.recordBiosecurity(
                                            RecordPoultryBiosecurity(
                                                walkId = UUID.randomUUID().toString(),
                                                houseId = placeHouse.value.trim().ifBlank { null },
                                                groupId = placeGroup.value.trim().ifBlank { null },
                                                findings = findings.value,
                                                occurredEpochDay = LocalDate.parse(setDay.value).toEpochDay(),
                                            ),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && findings.value.isNotBlank() && setDay.value.isNotBlank() && (placeHouse.value.isNotBlank() || placeGroup.value.isNotBlank()),
                            ) { androidx.compose.material3.Text("Record biosecurity walk") }
                            androidx.compose.material3.Button(
                                onClick = {
                                    run {
                                        ops.enablePoultryKind(
                                            EnablePoultryKind(poultryKind.value),
                                            newContext(),
                                        )
                                    }
                                },
                                enabled = !busy && poultryKind.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Enable poultry kind") }
                        }
                        else -> Unit
                    }
                },
            )
        }
        FarmModule.GROUPS -> {
            val name = remember { mutableStateOf("") }
            val species = remember { mutableStateOf("goat") }
            val heads = remember { mutableStateOf("") }
            val eggs = remember { mutableStateOf("") }
            val dead = remember { mutableStateOf("") }
            val feed = remember { mutableStateOf("") }
            val day = remember { mutableStateOf("") }
            val groupId = remember { mutableStateOf("") }
            SimpleCaptureScreen(
                title = "Groups",
                help = "Groups hold a census for grazing, feed issue, and poultry flock sheets.",
                empty = "No groups on this device.",
                rows = groupRows + flockRows,
                busy = busy,
                error = error,
                fields = listOf("Species" to species, "Name" to name, "Head count" to heads),
                actionLabel = "Create group",
                onSubmit = {
                    run {
                        ops.createGroup(
                            CreateAnimalGroup(UUID.randomUUID().toString(), species.value, name.value, heads.value.toIntOrNull() ?: 0),
                            newContext(),
                        )
                    }
                },
                onBack = onBack,
                extra = {
                    androidx.compose.material3.OutlinedTextField(groupId.value, { groupId.value = it }, label = { androidx.compose.material3.Text("Poultry group id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(eggs.value, { eggs.value = it }, label = { androidx.compose.material3.Text("Eggs") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(dead.value, { dead.value = it }, label = { androidx.compose.material3.Text("Dead") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(feed.value, { feed.value = it }, label = { androidx.compose.material3.Text("Feed grams") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(day.value, { day.value = it }, label = { androidx.compose.material3.Text("Flock day") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.Button(
                        onClick = {
                            run {
                                ops.recordFlockDay(
                                    RecordPoultryFlockDay(
                                        dayId = UUID.randomUUID().toString(),
                                        groupId = groupId.value,
                                        eggs = eggs.value.toIntOrNull() ?: 0,
                                        dead = dead.value.toIntOrNull() ?: 0,
                                        culls = 0,
                                        feedGrams = feed.value.toLongOrNull() ?: 0L,
                                        occurredEpochDay = LocalDate.parse(day.value).toEpochDay(),
                                    ),
                                    newContext(),
                                )
                            }
                        },
                        enabled = !busy && groupId.value.isNotBlank() && day.value.isNotBlank(),
                    ) { androidx.compose.material3.Text("Record poultry flock day") }
                    androidx.compose.material3.OutlinedTextField(heads.value, { heads.value = it }, label = { androidx.compose.material3.Text("Census head count") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.Button(
                        onClick = {
                            run {
                                ops.recordCensus(
                                    RecordGroupCensus(
                                        UUID.randomUUID().toString(),
                                        groupId.value,
                                        heads.value.toIntOrNull() ?: -1,
                                        LocalDate.parse(day.value).toEpochDay(),
                                    ),
                                    newContext(),
                                )
                            }
                        },
                        enabled = !busy && groupId.value.isNotBlank() && heads.value.isNotBlank() && day.value.isNotBlank(),
                    ) { androidx.compose.material3.Text("Record census") }
                },
            )
        }
        FarmModule.PASTURE -> {
            val code = remember { mutableStateOf("") }
            val display = remember { mutableStateOf("") }
            val water = remember { mutableStateOf("trough") }
            val paddockId = remember { mutableStateOf("") }
            val groupId = remember { mutableStateOf("") }
            val heads = remember { mutableStateOf("") }
            val entered = remember { mutableStateOf("") }
            val sessionId = remember { mutableStateOf("") }
            val exited = remember { mutableStateOf("") }
            SimpleCaptureScreen(
                title = "Pasture",
                help = "One group grazes one paddock at a time. Rest starts when the session ends.",
                empty = "No paddocks on this device.",
                rows = paddockRows + grazingRows,
                busy = busy,
                error = error,
                fields = listOf("Code" to code, "Name" to display, "Water source" to water),
                actionLabel = "Create paddock",
                onSubmit = {
                    run {
                        ops.createPaddock(
                            CreatePaddock(UUID.randomUUID().toString(), code.value, display.value.ifBlank { code.value }, waterSource = water.value, shade = true),
                            newContext(),
                        )
                    }
                },
                onBack = onBack,
                extra = {
                    androidx.compose.material3.OutlinedTextField(paddockId.value, { paddockId.value = it }, label = { androidx.compose.material3.Text("Paddock id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(groupId.value, { groupId.value = it }, label = { androidx.compose.material3.Text("Group id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(heads.value, { heads.value = it }, label = { androidx.compose.material3.Text("Head count") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(entered.value, { entered.value = it }, label = { androidx.compose.material3.Text("Enter date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.Button(
                        onClick = {
                            run {
                                ops.startGrazing(
                                    StartGrazing(UUID.randomUUID().toString(), paddockId.value, groupId.value, heads.value.toIntOrNull() ?: 0, LocalDate.parse(entered.value).toEpochDay()),
                                    newContext(),
                                )
                            }
                        },
                        enabled = !busy && paddockId.value.isNotBlank() && groupId.value.isNotBlank() && entered.value.isNotBlank(),
                    ) { androidx.compose.material3.Text("Start grazing") }
                    androidx.compose.material3.OutlinedTextField(sessionId.value, { sessionId.value = it }, label = { androidx.compose.material3.Text("Session id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(exited.value, { exited.value = it }, label = { androidx.compose.material3.Text("Exit date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.Button(
                        onClick = {
                            run {
                                ops.endGrazing(EndGrazing(sessionId.value, LocalDate.parse(exited.value).toEpochDay()), newContext())
                            }
                        },
                        enabled = !busy && sessionId.value.isNotBlank() && exited.value.isNotBlank(),
                    ) { androidx.compose.material3.Text("End grazing") }
                },
            )
        }
        FarmModule.LABOUR -> {
            val worker = remember { mutableStateOf("") }
            val code = remember { mutableStateOf("CHECK") }
            val minutes = remember { mutableStateOf("") }
            val day = remember { mutableStateOf("") }
            SimpleCaptureScreen(
                title = "Labour",
                help = "Minutes are whole figures. Worker name is a farm label, not a login.",
                empty = "No labour entries on this device.",
                rows = labourRows,
                busy = busy,
                error = error,
                fields = listOf("Worker" to worker, "Task code" to code, "Minutes" to minutes, "Date" to day),
                actionLabel = "Record labour",
                onSubmit = {
                    run {
                        ops.recordLabour(
                            RecordLabour(UUID.randomUUID().toString(), worker.value, code.value, minutes.value.toIntOrNull() ?: 0, LocalDate.parse(day.value).toEpochDay()),
                            newContext(),
                        )
                    }
                },
                onBack = onBack,
            )
        }
        FarmModule.ASSETS -> {
            val code = remember { mutableStateOf("") }
            val name = remember { mutableStateOf("") }
            val kind = remember { mutableStateOf("equipment") }
            val assetId = remember { mutableStateOf("") }
            val title = remember { mutableStateOf("") }
            val day = remember { mutableStateOf("") }
            SimpleCaptureScreen(
                title = "Assets",
                help = "Record equipment and maintenance. This is not a depreciation ledger.",
                empty = "No assets on this device.",
                rows = assetRows,
                busy = busy,
                error = error,
                fields = listOf("Code" to code, "Name" to name, "Kind" to kind),
                actionLabel = "Create asset",
                onSubmit = {
                    run {
                        ops.createAsset(CreateFarmAsset(UUID.randomUUID().toString(), code.value, name.value, kind.value), newContext())
                    }
                },
                onBack = onBack,
                extra = {
                    androidx.compose.material3.OutlinedTextField(assetId.value, { assetId.value = it }, label = { androidx.compose.material3.Text("Asset id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(title.value, { title.value = it }, label = { androidx.compose.material3.Text("Maintenance title") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(day.value, { day.value = it }, label = { androidx.compose.material3.Text("Date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.Button(
                        onClick = {
                            run {
                                ops.recordMaintenance(
                                    RecordMaintenance(UUID.randomUUID().toString(), assetId.value, title.value, LocalDate.parse(day.value).toEpochDay()),
                                    newContext(),
                                )
                            }
                        },
                        enabled = !busy && assetId.value.isNotBlank() && title.value.isNotBlank() && day.value.isNotBlank(),
                    ) { androidx.compose.material3.Text("Record maintenance") }
                },
            )
        }
        FarmModule.FEED -> {
            val itemId = remember { mutableStateOf("") }
            val qty = remember { mutableStateOf("") }
            val day = remember { mutableStateOf("") }
            SimpleCaptureScreen(
                title = "Feed",
                help = "Issuing feed deducts inventory in milli-units. Ration percentages stay advisory drafts.",
                empty = "No feed issues on this device.",
                rows = feedRows + inventoryRows,
                busy = busy,
                error = error,
                fields = listOf("Item id" to itemId, "Quantity" to qty, "Date" to day),
                actionLabel = "Issue feed",
                onSubmit = {
                    run {
                        val amount = qty.value.replace(',', '.').toDoubleOrNull() ?: error("Enter a quantity")
                        ops.issueFeed(
                            IssueFeed(UUID.randomUUID().toString(), itemId.value, null, (amount * 1000.0).toLong(), LocalDate.parse(day.value).toEpochDay()),
                            newContext(),
                        )
                    }
                },
                onBack = onBack,
            )
        }
        FarmModule.WATER -> {
            val source = remember { mutableStateOf("trough") }
            val litres = remember { mutableStateOf("") }
            val day = remember { mutableStateOf("") }
            SimpleCaptureScreen(
                title = "Water",
                help = "Enter litres as a figure. The device stores milli-litres.",
                empty = "No water records on this device.",
                rows = waterRows,
                busy = busy,
                error = error,
                fields = listOf("Source" to source, "Litres" to litres, "Date" to day),
                actionLabel = "Record water",
                onSubmit = {
                    run {
                        val amount = litres.value.replace(',', '.').toDoubleOrNull() ?: error("Enter litres")
                        ops.recordWater(
                            RecordWater(UUID.randomUUID().toString(), source.value, (amount * 1000.0).toLong(), LocalDate.parse(day.value).toEpochDay()),
                            newContext(),
                        )
                    }
                },
                onBack = onBack,
            )
        }
        FarmModule.WAITLIST -> {
            val contact = remember { mutableStateOf("") }
            val qty = remember { mutableStateOf("1") }
            val waitlistId = remember { mutableStateOf("") }
            val kitId = remember { mutableStateOf("") }
            val waveId = remember { mutableStateOf("") }
            val label = remember { mutableStateOf("") }
            val sex = remember { mutableStateOf("unknown") }
            val tag = remember { mutableStateOf("") }
            val decision = remember { mutableStateOf("sale_pet") }
            val buyer = remember { mutableStateOf("") }
            val amount = remember { mutableStateOf("") }
            val purpose = remember { mutableStateOf("meat") }
            val grams = remember { mutableStateOf("") }
            val day = remember { mutableStateOf("") }
            SimpleCaptureScreen(
                title = "Rabbit waitlist",
                help = "Enqueue a buyer, mark a kit sale_pet, then fulfill. A contract posts income in minor units.",
                empty = "No waitlist rows on this device.",
                rows = waitlistRows + kitRows,
                busy = busy,
                error = error,
                fields = listOf("Buyer" to contact, "Quantity" to qty),
                actionLabel = "Enqueue waitlist",
                onSubmit = {
                    run {
                        ops.enqueueWaitlist(
                            EnqueueRabbitWaitlist(UUID.randomUUID().toString(), contact.value, qty = qty.value.toIntOrNull() ?: 0),
                            newContext(),
                        )
                    }
                },
                onBack = onBack,
                extra = {
                    androidx.compose.material3.OutlinedTextField(waveId.value, { waveId.value = it }, label = { androidx.compose.material3.Text("Wave id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(label.value, { label.value = it }, label = { androidx.compose.material3.Text("Kit label") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(sex.value, { sex.value = it }, label = { androidx.compose.material3.Text("Kit sex") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.Button(
                        onClick = {
                            run {
                                ops.registerKit(RegisterRabbitKit(UUID.randomUUID().toString(), waveId.value, label.value, sex.value), newContext())
                            }
                        },
                        enabled = !busy && waveId.value.isNotBlank() && label.value.isNotBlank(),
                    ) { androidx.compose.material3.Text("Register kit") }
                    androidx.compose.material3.OutlinedTextField(kitId.value, { kitId.value = it }, label = { androidx.compose.material3.Text("Kit id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(decision.value, { decision.value = it }, label = { androidx.compose.material3.Text("Retention") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(day.value, { day.value = it }, label = { androidx.compose.material3.Text("Date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.Button(
                        onClick = {
                            run {
                                ops.decideRetention(
                                    DecideRabbitRetention(UUID.randomUUID().toString(), kitId.value, decision.value, LocalDate.parse(day.value).toEpochDay()),
                                    newContext(),
                                )
                            }
                        },
                        enabled = !busy && kitId.value.isNotBlank() && day.value.isNotBlank(),
                    ) { androidx.compose.material3.Text("Record retention") }
                    androidx.compose.material3.OutlinedTextField(tag.value, { tag.value = it }, label = { androidx.compose.material3.Text("Promote tag") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.Button(
                        onClick = {
                            run {
                                ops.promoteKit(PromoteRabbitKit(kitId.value, UUID.randomUUID().toString(), tag.value, "FEMALE"), newContext())
                            }
                        },
                        enabled = !busy && kitId.value.isNotBlank() && tag.value.isNotBlank(),
                    ) { androidx.compose.material3.Text("Promote kit") }
                    androidx.compose.material3.OutlinedTextField(waitlistId.value, { waitlistId.value = it }, label = { androidx.compose.material3.Text("Waitlist id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.Button(
                        onClick = {
                            run { ops.fulfillWaitlist(FulfillRabbitWaitlist(waitlistId.value, kitId.value), newContext()) }
                        },
                        enabled = !busy && waitlistId.value.isNotBlank() && kitId.value.isNotBlank(),
                    ) { androidx.compose.material3.Text("Fulfill waitlist") }
                    androidx.compose.material3.OutlinedTextField(buyer.value, { buyer.value = it }, label = { androidx.compose.material3.Text("Contract buyer") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(amount.value, { amount.value = it }, label = { androidx.compose.material3.Text("Amount") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.Button(
                        onClick = {
                            run {
                                val major = amount.value.replace(',', '.').toDoubleOrNull() ?: error("Enter an amount")
                                ops.agreeContract(
                                    AgreeRabbitContract(
                                        contractId = UUID.randomUUID().toString(),
                                        waitlistId = waitlistId.value.trim().ifBlank { null },
                                        buyerName = buyer.value,
                                        amountMinor = (major * 100.0).toLong(),
                                        occurredEpochDay = LocalDate.parse(day.value).toEpochDay(),
                                    ),
                                    newContext(),
                                )
                            }
                        },
                        enabled = !busy && buyer.value.isNotBlank() && amount.value.isNotBlank() && day.value.isNotBlank(),
                    ) { androidx.compose.material3.Text("Agree contract") }
                    androidx.compose.material3.OutlinedTextField(purpose.value, { purpose.value = it }, label = { androidx.compose.material3.Text("Plan purpose") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(grams.value, { grams.value = it }, label = { androidx.compose.material3.Text("Target grams") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.Button(
                        onClick = {
                            run {
                                ops.recordPlan(
                                    RecordRabbitMarketPlan(
                                        planId = UUID.randomUUID().toString(),
                                        kitId = kitId.value.trim().ifBlank { null },
                                        waveId = waveId.value.trim().ifBlank { null },
                                        targetWeightGrams = grams.value.toIntOrNull() ?: 0,
                                        targetEpochDay = LocalDate.parse(day.value).toEpochDay(),
                                        purpose = purpose.value,
                                    ),
                                    newContext(),
                                )
                            }
                        },
                        enabled = !busy && grams.value.isNotBlank() && day.value.isNotBlank(),
                    ) { androidx.compose.material3.Text("Record market plan") }
                    androidx.compose.material3.Text(beddingLine)
                    androidx.compose.material3.Text("Bind a bedding SKU. Nest place does not issue stock.")
                    val bedItem = remember { mutableStateOf("") }
                    val bedQty = remember { mutableStateOf("1000") }
                    androidx.compose.material3.OutlinedTextField(bedItem.value, { bedItem.value = it }, label = { androidx.compose.material3.Text("Bedding item id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(bedQty.value, { bedQty.value = it }, label = { androidx.compose.material3.Text("Qty milli") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.Button(
                        onClick = {
                            run {
                                ops.bindBedding(
                                    BindRabbitBedding(bedItem.value.trim().ifBlank { null }, bedQty.value.toLongOrNull() ?: 0L),
                                    newContext(),
                                )
                            }
                        },
                        enabled = !busy && bedQty.value.isNotBlank(),
                    ) { androidx.compose.material3.Text("Bind bedding") }
                },
            )
        }
        FarmModule.PROCUREMENT -> {
            val name = remember { mutableStateOf("") }
            val lead = remember { mutableStateOf("0") }
            val supplierId = remember { mutableStateOf("") }
            val itemId = remember { mutableStateOf("") }
            val qty = remember { mutableStateOf("") }
            val amount = remember { mutableStateOf("") }
            val day = remember { mutableStateOf("") }
            SimpleCaptureScreen(
                title = "Procurement",
                help = "A purchase receives inventory and posts an expense in integer minor units.",
                empty = "No suppliers on this device.",
                rows = supplierRows + purchaseRows,
                busy = busy,
                error = error,
                fields = listOf("Supplier name" to name, "Lead time days" to lead),
                actionLabel = "Create supplier",
                onSubmit = {
                    run {
                        ops.createSupplier(
                            CreateSupplier(UUID.randomUUID().toString(), name.value, lead.value.toIntOrNull() ?: 0),
                            newContext(),
                        )
                    }
                },
                onBack = onBack,
                extra = {
                    androidx.compose.material3.OutlinedTextField(supplierId.value, { supplierId.value = it }, label = { androidx.compose.material3.Text("Supplier id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(itemId.value, { itemId.value = it }, label = { androidx.compose.material3.Text("Inventory item id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(qty.value, { qty.value = it }, label = { androidx.compose.material3.Text("Quantity") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(amount.value, { amount.value = it }, label = { androidx.compose.material3.Text("Amount") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(day.value, { day.value = it }, label = { androidx.compose.material3.Text("Date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.Button(
                        onClick = {
                            run {
                                val quantity = qty.value.replace(',', '.').toDoubleOrNull() ?: error("Enter a quantity")
                                val major = amount.value.replace(',', '.').toDoubleOrNull() ?: error("Enter an amount")
                                ops.recordPurchase(
                                    RecordPurchase(
                                        purchaseId = UUID.randomUUID().toString(),
                                        supplierId = supplierId.value,
                                        itemId = itemId.value,
                                        quantityMilli = (quantity * 1000.0).toLong(),
                                        amountMinor = (major * 100.0).toLong(),
                                        occurredEpochDay = LocalDate.parse(day.value).toEpochDay(),
                                    ),
                                    newContext(),
                                )
                            }
                        },
                        enabled = !busy && supplierId.value.isNotBlank() && itemId.value.isNotBlank() && qty.value.isNotBlank() && amount.value.isNotBlank() && day.value.isNotBlank(),
                    ) { androidx.compose.material3.Text("Record purchase") }
                },
            )
        }
        FarmModule.SALES -> {
            val kind = remember { mutableStateOf("live_goat") }
            val qty = remember { mutableStateOf("1") }
            val amount = remember { mutableStateOf("") }
            val day = remember { mutableStateOf("") }
            SimpleCaptureScreen(
                title = "Sales",
                help = "A sale posts income in integer minor units. This is farm unit economics, not a statutory ledger.",
                empty = "No sales on this device.",
                rows = saleRows,
                busy = busy,
                error = error,
                fields = listOf("Item kind" to kind, "Quantity" to qty, "Amount" to amount, "Date" to day),
                actionLabel = "Record sale",
                onSubmit = {
                    run {
                        val major = amount.value.replace(',', '.').toDoubleOrNull() ?: error("Enter an amount")
                        val quantity = qty.value.replace(',', '.').toDoubleOrNull() ?: error("Enter a quantity")
                        ops.recordSale(
                            RecordSale(
                                saleId = UUID.randomUUID().toString(),
                                itemKind = kind.value,
                                quantityMilli = (quantity * 1000.0).toLong(),
                                amountMinor = (major * 100.0).toLong(),
                                occurredEpochDay = LocalDate.parse(day.value).toEpochDay(),
                            ),
                            newContext(),
                        )
                    }
                },
                onBack = onBack,
            )
        }
        else -> Unit
    }
}
