package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.design.FarmSelectorSearch
import com.farmos.core.design.NoFarmSelectorSearch
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomHerdRepository
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.RecordRabbitFoster
import com.farmos.domain.ops.RecordRabbitKindling
import com.farmos.domain.ops.RecordRabbitPalpation
import com.farmos.domain.rabbit.CreateRabbitCage
import com.farmos.domain.rabbit.CreateRabbitNestBox
import com.farmos.domain.rabbit.CreateRabbitWave
import com.farmos.domain.rabbit.RecordRabbitGiStasis
import com.farmos.feature.rabbit.RabbitAnimalView
import com.farmos.feature.rabbit.RabbitNestBoxChoice
import com.farmos.feature.rabbit.RabbitPedigreePorts
import com.farmos.feature.rabbit.RabbitProgrammeScreen
import com.farmos.feature.rabbit.RabbitRecords
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

/** Dedicated Rabbit presentation/orchestration boundary; preserves existing governed commands. */
@Composable
fun RabbitModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    rabbitHerd: RoomHerdRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadRecords: suspend () -> RabbitRecords = { RabbitRecords() },
    searchRabbits: FarmSelectorSearch = NoFarmSelectorSearch,
    /** A rabbit's exit record and reversal (D-022); onRecorded refreshes the rabbitry. */
    exitFor: @Composable (rabbit: RabbitAnimalView, onRecorded: () -> Unit) -> Unit = { _, _ -> },
    pedigree: RabbitPedigreePorts? = null,
    attachmentsFor: @Composable (rabbit: RabbitAnimalView) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cages by remember { mutableStateOf(emptyList<String>()) }
    var waves by remember { mutableStateOf(emptyList<String>()) }
    var waveOptions by remember(farmId) { mutableStateOf(emptyList<FarmSelectorOption>()) }
    var boxes by remember { mutableStateOf(0L) }
    var selectedCageId by remember { mutableStateOf<String?>(null) }
    var rabbitRows by remember { mutableStateOf(emptyList<String>()) }
    var rabbits by remember(farmId) { mutableStateOf(emptyList<RabbitAnimalView>()) }
    var nestBoxRows by remember { mutableStateOf(emptyList<String>()) }
    var nestBoxChoices by remember(farmId) { mutableStateOf(emptyList<RabbitNestBoxChoice>()) }
    var records by remember(farmId) { mutableStateOf(RabbitRecords()) }
    var rabbitCount by remember(farmId) { mutableStateOf<Int?>(null) }

    suspend fun refresh() {
        val cageEntities = ops.cages()
        cages = cageEntities.map { it.code }
        if (selectedCageId == null) selectedCageId = cageEntities.firstOrNull()?.id
        val waveRows = ops.waves()
        waves = waveRows.map { "${it.id} · ${it.doeCount} does · mating day ${it.matingEpochDay}" }
        waveOptions = waveRows.map { FarmSelectorOption(it.id, "Mated ${LocalDate.ofEpochDay(it.matingEpochDay)}", "${it.doeCount} does") }
        boxes = selectedCageId?.let { ops.availableBoxes(it) } ?: 0
        val boxRows = ops.nestBoxes()
        nestBoxRows = boxRows.map { "${it.id} ${it.code} · ${it.status}" }
        nestBoxChoices = boxRows.map { RabbitNestBoxChoice(it.id, it.code, it.status) }
        val herd = rabbitHerd.list()
        rabbits = herd.map { RabbitAnimalView(it.id, it.tag, it.name, it.sex == "FEMALE", it.status) }
        rabbitRows = herd.map { animal ->
            buildString {
                append(animal.tag)
                animal.name?.let { append(" · ").append(it) }
                append(" · ").append(if (animal.sex == "FEMALE") "doe" else "buck")
                append(" · ").append(animal.status)
            }
        }
        records = loadRecords()
        rabbitCount = rabbitHerd.listedTotal()
    }

    LaunchedEffect(farmId) { runCatching { refresh() } }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            runCatching {
                block()
                refresh()
            }.onSuccess {
                enqueueSync()
            }.onFailure {
                error = it.message
            }
            busy = false
        }
    }

    RabbitProgrammeScreen(
        rabbits = rabbits,
        rabbitExit = { rabbit -> exitFor(rabbit) { run { } } },
        rabbitAttachments = attachmentsFor,
        pedigree = pedigree,
        waveOptions = waveOptions,
        nestBoxChoices = nestBoxChoices,
        searchRabbits = searchRabbits,
        cages = cages,
        waves = waves,
        availableBoxes = boxes,
        busy = busy,
        error = error,
        does = rabbitRows,
        onRegisterDoe = { tag, name, sex ->
            run { rabbitHerd.register(UUID.randomUUID().toString(), tag, name, sex, null, newContext()) }
        },
        onCreateCage = { code ->
            run { ops.createCage(CreateRabbitCage(UUID.randomUUID().toString(), code), newContext()) }
        },
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
                        UUID.randomUUID().toString(), waveId, count.toIntOrNull() ?: 0, LocalDate.parse(day).toEpochDay(),
                    ),
                    newContext(),
                )
            }
        },
        onRecordOutcome = { waveId, outcome, day ->
            run {
                ops.recordMatingOutcome(
                    com.farmos.domain.rabbit.RecordRabbitMatingOutcome(
                        UUID.randomUUID().toString(), waveId, outcome, LocalDate.parse(day).toEpochDay(),
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
        records = records,
        rabbitCount = rabbitCount,
    )
}
