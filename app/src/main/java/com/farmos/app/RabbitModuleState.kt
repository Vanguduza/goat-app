package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.design.FarmSelectorSearch
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomHerdRepository
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.feature.rabbit.RabbitAnimalView
import com.farmos.feature.rabbit.RabbitNestBoxChoice
import com.farmos.feature.rabbit.RabbitPedigreePorts
import com.farmos.feature.rabbit.RabbitRecords
import java.time.LocalDate
import kotlinx.coroutines.launch
import com.farmos.core.design.runSuspendCatching

internal class RabbitModuleState(
    val farmId: String,
    val ops: RoomOpsRepository,
    val rabbitHerd: RoomHerdRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadRecords: suspend () -> RabbitRecords,
    searchRabbits: FarmSelectorSearch,
    exitFor: @Composable (rabbit: RabbitAnimalView, onRecorded: () -> Unit) -> Unit,
    pedigree: RabbitPedigreePorts?,
    attachmentsFor: @Composable (rabbit: RabbitAnimalView) -> Unit,
    val scope: kotlinx.coroutines.CoroutineScope,
) {
    var newContext: () -> LocalCommandContext by mutableStateOf(newContext)
    var enqueueSync: () -> Unit by mutableStateOf(enqueueSync)
    var onBack: () -> Unit by mutableStateOf(onBack)
    var loadRecords: suspend () -> RabbitRecords by mutableStateOf(loadRecords)
    var searchRabbits: FarmSelectorSearch by mutableStateOf(searchRabbits)
    var exitFor: @Composable (rabbit: RabbitAnimalView, onRecorded: () -> Unit) -> Unit by mutableStateOf(exitFor)
    var pedigree: RabbitPedigreePorts? by mutableStateOf(pedigree)
    var attachmentsFor: @Composable (rabbit: RabbitAnimalView) -> Unit by mutableStateOf(attachmentsFor)

    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var cages by mutableStateOf(emptyList<String>())
    var waves by mutableStateOf(emptyList<String>())
    var waveOptions by mutableStateOf(emptyList<FarmSelectorOption>())
    var boxes by mutableStateOf(0L)
    var selectedCageId by mutableStateOf<String?>(null)
    var rabbitRows by mutableStateOf(emptyList<String>())
    var rabbits by mutableStateOf(emptyList<RabbitAnimalView>())
    var nestBoxRows by mutableStateOf(emptyList<String>())
    var nestBoxChoices by mutableStateOf(emptyList<RabbitNestBoxChoice>())
    var records by mutableStateOf(RabbitRecords())
    var rabbitCount by mutableStateOf<Int?>(null)

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


    fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                runSuspendCatching {
                    completeModuleWrite(
                        write = block,
                        onCommitted = {},
                        enqueueSync = { enqueueSync() },
                        refresh = ::refresh,
                    )
                }.onSuccess { warning -> error = warning }
                    .onFailure { error = it.message ?: "The change could not be saved on this device" }
            } finally {
                busy = false
            }
        }
    }


}
