package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.farmos.core.database.PoultryHatchEntity
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.CandlePoultryHatch
import com.farmos.domain.ops.CreatePoultryHouse
import com.farmos.domain.ops.EnablePoultryKind
import com.farmos.domain.ops.PlacePoultryFlock
import com.farmos.domain.ops.RecordPoultryBiosecurity
import com.farmos.domain.ops.RecordPoultryFlockDay
import com.farmos.domain.ops.RecordPoultryHatch
import com.farmos.domain.ops.RecordPoultryVaccination
import com.farmos.domain.ops.SetPoultryHatch
import com.farmos.feature.ops.PoultryExperienceScreen
import com.farmos.feature.ops.PoultryFlockRecords
import com.farmos.feature.ops.PoultryRecords
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

/** Dedicated poultry orchestration boundary preserving the existing governed command paths. */
@Composable
fun PoultryModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadRecords: suspend () -> PoultryRecords = { PoultryRecords() },
    loadFlock: suspend (String) -> PoultryFlockRecords = { PoultryFlockRecords() },
) {
    val scope = rememberCoroutineScope()
    var records by remember(farmId) { mutableStateOf(PoultryRecords()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var enabledKinds by remember { mutableStateOf(emptyList<String>()) }
    var houses by remember { mutableStateOf(emptyList<String>()) }
    var placements by remember { mutableStateOf(emptyList<String>()) }
    var flockDays by remember { mutableStateOf(emptyList<String>()) }
    var hatches by remember { mutableStateOf(emptyList<String>()) }
    var vaccinations by remember { mutableStateOf(emptyList<String>()) }
    var flockGroups by remember(farmId) { mutableStateOf(emptyList<FarmSelectorOption>()) }
    var houseOptions by remember(farmId) { mutableStateOf(emptyList<FarmSelectorOption>()) }
    var poultryFormulary by remember(farmId) { mutableStateOf(emptyList<FarmSelectorOption>()) }
    var setHatches by remember(farmId) { mutableStateOf(emptyList<FarmSelectorOption>()) }
    var candledHatches by remember(farmId) { mutableStateOf(emptyList<FarmSelectorOption>()) }

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

    LaunchedEffect(farmId) { runCatching { refresh() } }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            runCatching {
                block()
                refresh()
            }.onSuccess { enqueueSync() }
                .onFailure { error = it.message }
            busy = false
        }
    }

    PoultryExperienceScreen(
        flockGroups = flockGroups,
        houseOptions = houseOptions,
        poultryFormulary = poultryFormulary,
        setHatches = setHatches,
        candledHatches = candledHatches,
        enabledKinds = enabledKinds,
        houses = houses,
        placements = placements,
        flockDays = flockDays,
        hatches = hatches,
        vaccinations = vaccinations,
        busy = busy,
        error = error,
        onEnableKind = { kind -> run { ops.enablePoultryKind(EnablePoultryKind(kind), newContext()) } },
        onCreateHouse = { code, houseKind, poultryKind ->
            run { ops.createHouse(CreatePoultryHouse(UUID.randomUUID().toString(), code, houseKind, poultryKind), newContext()) }
        },
        onPlaceFlock = { groupId, houseId, poultryKind, heads, day ->
            run {
                ops.placeFlock(
                    PlacePoultryFlock(
                        UUID.randomUUID().toString(), groupId, houseId, poultryKind, heads.toIntOrNull() ?: 0,
                        LocalDate.parse(day).toEpochDay(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                    ),
                    newContext(),
                )
            }
        },
        onRecordFlockDay = { groupId, eggs, dead, culls, feedGrams, day ->
            run {
                ops.recordFlockDay(
                    RecordPoultryFlockDay(
                        UUID.randomUUID().toString(), groupId, eggs.toIntOrNull() ?: 0, dead.toIntOrNull() ?: 0,
                        culls.toIntOrNull() ?: 0, feedGrams.toLongOrNull() ?: 0L, LocalDate.parse(day).toEpochDay(),
                    ),
                    newContext(),
                )
            }
        },
        onSetEggs = { poultryKind, eggs, day, houseId, groupId ->
            run {
                ops.setHatch(
                    SetPoultryHatch(
                        hatchId = UUID.randomUUID().toString(),
                        poultryKindCode = poultryKind,
                        eggsSet = eggs.toIntOrNull() ?: 0,
                        setEpochDay = LocalDate.parse(day).toEpochDay(),
                        houseId = houseId.trim().ifBlank { null },
                        groupId = groupId.trim().ifBlank { null },
                        candleTaskId = UUID.randomUUID().toString(),
                        lockTaskId = UUID.randomUUID().toString(),
                        hatchTaskId = UUID.randomUUID().toString(),
                    ),
                    newContext(),
                )
            }
        },
        onCandle = { hatchId, fertile, infertile, midDead, day ->
            run {
                ops.candleHatch(
                    CandlePoultryHatch(
                        hatchId,
                        fertile.toIntOrNull() ?: 0,
                        infertile.toIntOrNull() ?: 0,
                        midDead.toIntOrNull() ?: 0,
                        LocalDate.parse(day).toEpochDay(),
                    ),
                    newContext(),
                )
            }
        },
        onRecordHatch = { hatchId, hatched, culls, day ->
            run {
                ops.recordHatch(
                    RecordPoultryHatch(
                        hatchId,
                        hatched.toIntOrNull() ?: 0,
                        culls.toIntOrNull() ?: 0,
                        occurredEpochDay = LocalDate.parse(day).toEpochDay(),
                    ),
                    newContext(),
                )
            }
        },
        onVaccinate = { groupId, poultryKind, formularyId, day ->
            run {
                ops.recordVaccination(
                    RecordPoultryVaccination(
                        UUID.randomUUID().toString(), groupId, poultryKind, formularyId, LocalDate.parse(day).toEpochDay(),
                    ),
                    newContext(),
                )
            }
        },
        onBiosecurity = { houseId, groupId, findings, mixedSpecies, day ->
            run {
                ops.recordBiosecurity(
                    RecordPoultryBiosecurity(
                        UUID.randomUUID().toString(),
                        houseId.trim().ifBlank { null },
                        groupId.trim().ifBlank { null },
                        findings,
                        mixedSpecies,
                        LocalDate.parse(day).toEpochDay(),
                    ),
                    newContext(),
                )
            }
        },
        onBack = onBack,
        records = records,
        loadFlock = loadFlock,
    )
}
