package com.farmos.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.farmos.core.database.PoultryHatchEntity
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.feature.ops.PoultryFlockRecords
import com.farmos.feature.ops.PoultryRecords
import java.time.LocalDate
import kotlinx.coroutines.launch
import com.farmos.core.design.runSuspendCatching

internal class PoultryModuleState(
    val farmId: String,
    val ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadRecords: suspend () -> PoultryRecords,
    loadFlock: suspend (String) -> PoultryFlockRecords,
    val scope: kotlinx.coroutines.CoroutineScope,
) {
    var newContext: () -> LocalCommandContext by mutableStateOf(newContext)
    var enqueueSync: () -> Unit by mutableStateOf(enqueueSync)
    var onBack: () -> Unit by mutableStateOf(onBack)
    var loadRecords: suspend () -> PoultryRecords by mutableStateOf(loadRecords)
    var loadFlock: suspend (String) -> PoultryFlockRecords by mutableStateOf(loadFlock)

    var records by mutableStateOf(PoultryRecords())
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var enabledKinds by mutableStateOf(emptyList<String>())
    var houses by mutableStateOf(emptyList<String>())
    var placements by mutableStateOf(emptyList<String>())
    var flockDays by mutableStateOf(emptyList<String>())
    var hatches by mutableStateOf(emptyList<String>())
    var vaccinations by mutableStateOf(emptyList<String>())
    var flockGroups by mutableStateOf(emptyList<FarmSelectorOption>())
    var houseOptions by mutableStateOf(emptyList<FarmSelectorOption>())
    var poultryFormulary by mutableStateOf(emptyList<FarmSelectorOption>())
    var setHatches by mutableStateOf(emptyList<FarmSelectorOption>())
    var candledHatches by mutableStateOf(emptyList<FarmSelectorOption>())

    suspend fun refresh() {
        enabledKinds = ops.enabledPoultryKinds().map { it.poultryKindCode }
        val houseRows = ops.houses()
        houses = houseRows.map { "${it.id} ${it.code} · ${it.kind} · ${it.poultryKindCode}" }
        houseOptions = houseRows.map { FarmSelectorOption(it.id, it.code, "${it.kind} · ${it.poultryKindCode}") }
        poultryFormulary = ops.approvedFormulary().filter { it.speciesCode == "poultry" }
            .map { FarmSelectorOption(it.id, it.productName, it.vetClass) }
        placements = ops.placements().map { "${it.groupId} · ${it.poultryKindCode} · house ${it.houseId} · ${it.headCount} head" }
        flockDays = ops.recentFlockDays().map { "eggs ${it.eggs} · dead ${it.dead} · culls ${it.culls} · feed ${it.feedGrams} g" }
        val hatchRows = ops.hatches()
        hatches = hatchRows.map { "${it.id} ${it.poultryKindCode} · ${it.eggsSet} eggs · ${it.status}" }
        fun hatchOption(row: PoultryHatchEntity) =
            FarmSelectorOption(row.id, "${row.poultryKindCode} · ${row.eggsSet} eggs", "Set ${LocalDate.ofEpochDay(row.setEpochDay)}")
        setHatches = hatchRows.filter { it.status == "set" }.map(::hatchOption)
        candledHatches = hatchRows.filter { it.status == "candled" }.map(::hatchOption)
        vaccinations = ops.vaccinations().map { "${it.id} ${it.poultryKindCode} · flock ${it.groupId} · ${it.formularyItemId}" }
        flockGroups = ops.groups().filter { it.speciesCode == "poultry" }
            .map { FarmSelectorOption(it.id, it.name, "${it.headCount} head recorded") }
        records = loadRecords()
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
