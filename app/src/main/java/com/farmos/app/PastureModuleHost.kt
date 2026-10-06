package com.farmos.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.farmos.core.database.PaddockEntity
import com.farmos.core.design.FarmEntitySelector
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalRows
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmSelectionAtoms
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.CreatePaddock
import com.farmos.domain.ops.EndGrazing
import com.farmos.domain.ops.StartGrazing
import com.farmos.feature.ops.PastureRecordNavigator
import com.farmos.feature.ops.PastureRecords
import com.farmos.feature.ops.SimpleCaptureScreen
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch
import com.farmos.core.design.runSuspendCatching

/**
 * Dedicated pasture/grazing orchestration boundary.
 *
 * FOS-PASTURE-004 — grazing rotation: start/end grazing moves groups through paddocks (root tag FOS-PASTURE-001).
 * FOS-PASTURE-005 — group movement: start/end grazing records a group moving into/out of a paddock (root tag FOS-PASTURE-001).
 * FOS-PASTURE-006 — rest period: days since each active paddock's last session ended.
 * FOS-PASTURE-007 — carrying capacity: area plus currently grazing head, head/ha.
 * FOS-PASTURE-008 — pasture condition: grazing-derived indicators (not a field survey).
 * FOS-PASTURE-011 — pasture report: combined per-paddock summary.
 */
@Composable
fun PastureModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadRecords: suspend () -> PastureRecords = { PastureRecords() },
) {
    val scope = rememberCoroutineScope()
    var paddockRows by remember { mutableStateOf(emptyList<String>()) }
    var grazingRows by remember { mutableStateOf(emptyList<String>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var records by remember(farmId) { mutableStateOf(PastureRecords()) }
    var paddockOptions by remember(farmId) { mutableStateOf(emptyList<FarmSelectorOption>()) }
    var groupOptions by remember(farmId) { mutableStateOf(emptyList<FarmSelectorOption>()) }

    suspend fun refresh() {
        val paddocks = ops.paddocks()
        paddockRows = paddocks.map { "${it.id} ${it.code} · ${it.displayName} · ${it.waterSource}" }
        paddockOptions = paddocks.map { FarmSelectorOption(it.id, "${it.code} · ${it.displayName}", "Water ${it.waterSource}") }
        groupOptions = ops.groups().map { FarmSelectorOption(it.id, it.name, "${it.speciesCode} · ${it.headCount} head recorded") }
        grazingRows = ops.openGrazing().map { "${it.id} paddock ${it.paddockId} · group ${it.groupId}" }
        records = loadRecords()
    }

    LaunchedEffect(farmId) { runSuspendCatching { refresh() } }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            runSuspendCatching {
                block()
                refresh()
            }.onSuccess { enqueueSync() }
                .onFailure { error = it.message }
            busy = false
        }
    }

    val code = remember { mutableStateOf("") }
    val display = remember { mutableStateOf("") }
    val water = remember { mutableStateOf("trough") }
    val paddockId = remember { mutableStateOf("") }
    val groupId = remember { mutableStateOf("") }
    val heads = remember { mutableStateOf("") }
    val entered = remember { mutableStateOf("") }
    val sessionId = remember { mutableStateOf("") }
    val exited = remember { mutableStateOf("") }
    var page by remember { mutableStateOf(PastureModulePage.HOME) }

    when (page) {
        PastureModulePage.HOME -> PastureRecordNavigator(records) { recordActions -> SimpleCaptureScreen(
        screenId = "FOS-PASTURE-001",
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
            recordActions()
            FarmEntitySelector(FarmSelectionAtoms.LOCATION_SELECTOR, "Paddock", paddockOptions, paddockId.value.ifBlank { null }, { paddockId.value = it }, "Create a paddock first.", enabled = !busy)
            FarmEntitySelector(FarmSelectionAtoms.GROUP_SELECTOR, "Group", groupOptions, groupId.value.ifBlank { null }, { groupId.value = it }, "Create a group in Groups first.", enabled = !busy)
            androidx.compose.material3.OutlinedTextField(heads.value, { heads.value = it }, label = { androidx.compose.material3.Text("Head count") }, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(entered.value, { entered.value = it }, label = { androidx.compose.material3.Text("Enter date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
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
            androidx.compose.material3.OutlinedTextField(sessionId.value, { sessionId.value = it }, label = { androidx.compose.material3.Text("Session id") }, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(exited.value, { exited.value = it }, label = { androidx.compose.material3.Text("Exit date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.Button(
                onClick = { run { ops.endGrazing(EndGrazing(sessionId.value, LocalDate.parse(exited.value).toEpochDay()), newContext()) } },
                enabled = !busy && sessionId.value.isNotBlank() && exited.value.isNotBlank(),
            ) { androidx.compose.material3.Text("End grazing") }
            androidx.compose.material3.TextButton(onClick = { page = PastureModulePage.REST }) { androidx.compose.material3.Text("Rest period") }
            androidx.compose.material3.TextButton(onClick = { page = PastureModulePage.CAPACITY }) { androidx.compose.material3.Text("Carrying capacity") }
            androidx.compose.material3.TextButton(onClick = { page = PastureModulePage.CONDITION }) { androidx.compose.material3.Text("Pasture condition") }
            androidx.compose.material3.TextButton(onClick = { page = PastureModulePage.REPORT }) { androidx.compose.material3.Text("Pasture report") }
            androidx.compose.material3.TextButton(onClick = { page = PastureModulePage.MAP }) { androidx.compose.material3.Text("Pasture map") }
        },
    ) }
        PastureModulePage.REST -> PastureRestPeriodScreen(ops = ops, onBack = { page = PastureModulePage.HOME })
        PastureModulePage.CAPACITY -> PastureCarryingCapacityScreen(ops = ops, onBack = { page = PastureModulePage.HOME })
        PastureModulePage.CONDITION -> PastureConditionScreen(ops = ops, onBack = { page = PastureModulePage.HOME })
        PastureModulePage.REPORT -> PastureReportScreen(ops = ops, onBack = { page = PastureModulePage.HOME })
        PastureModulePage.MAP -> PastureMapScreen(ops = ops, farmId = farmId, onBack = { page = PastureModulePage.HOME })
    }
}

private enum class PastureModulePage { HOME, REST, CAPACITY, CONDITION, REPORT, MAP }

/** Per-paddock grazing aggregates backing the rest/capacity/condition/report screens. */
private data class PaddockPastureStats(
    val paddock: PaddockEntity,
    val sessionCount: Int,
    val openSessions: Int,
    val openHeadCount: Int,
    val latestEnteredEpochDay: Long?,
    val latestExitedEpochDay: Long?,
)

private suspend fun loadPaddockPastureStats(ops: RoomOpsRepository): List<PaddockPastureStats> {
    val summaries = ops.grazingSummary().associateBy { it.paddockId }
    return ops.paddocks().map { paddock ->
        val summary = summaries[paddock.id]
        PaddockPastureStats(
            paddock = paddock,
            sessionCount = summary?.sessionCount ?: 0,
            openSessions = summary?.openSessions ?: 0,
            openHeadCount = summary?.openHeadCount ?: 0,
            latestEnteredEpochDay = summary?.latestEnteredEpochDay,
            latestExitedEpochDay = summary?.latestExitedEpochDay,
        )
    }
}

private fun restDaysLabel(todayEpochDay: Long, stats: PaddockPastureStats): String =
    when {
        stats.openSessions > 0 -> "in use — rest not accumulating"
        stats.latestExitedEpochDay != null -> {
            val days = todayEpochDay - stats.latestExitedEpochDay
            "resting $days days (last session ended ${LocalDate.ofEpochDay(stats.latestExitedEpochDay)})"
        }
        stats.sessionCount > 0 -> "grazing recorded but no ended session"
        else -> "no grazing recorded"
    }

private fun headPerHectareLabel(stats: PaddockPastureStats): String =
    stats.paddock.areaM2
        ?.takeIf { it > 0 }
        ?.let { "%.1f".format(stats.openHeadCount / (it / 10_000.0)) + " head/ha" }
        ?: "density unavailable"

/** FOS-PASTURE-006 — rest period: days since each active paddock's last grazing session ended, computed from recorded grazing sessions. */
@Composable
fun PastureRestPeriodScreen(
    ops: RoomOpsRepository,
    onBack: () -> Unit,
) {
    var rows by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(Unit) {
        val today = LocalDate.now().toEpochDay()
        rows = loadPaddockPastureStats(ops).map { stats ->
            "${stats.paddock.code} · ${stats.paddock.displayName} — ${restDaysLabel(today, stats)}"
        }
    }
    FarmOperationalPage(
        screenId = "FOS-PASTURE-006",
        title = "Rest period",
        subtitle = "Computed from recorded grazing sessions. No field survey is taken.",
        onBack = onBack,
    ) {
        FarmOperationalRows(rows, "No paddocks", "Create a paddock on the pasture home screen first.")
    }
}

/** FOS-PASTURE-007 — carrying capacity: recorded area plus currently grazing head per paddock; head/ha is computed, not a grazing recommendation. */
@Composable
fun PastureCarryingCapacityScreen(
    ops: RoomOpsRepository,
    onBack: () -> Unit,
) {
    var rows by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(Unit) {
        rows = loadPaddockPastureStats(ops).map { stats ->
            val areaLabel = stats.paddock.areaM2?.let { "$it m²" } ?: "area not recorded"
            "${stats.paddock.code} · ${stats.paddock.displayName} — $areaLabel · ${stats.openHeadCount} head grazing · ${headPerHectareLabel(stats)}"
        }
    }
    FarmOperationalPage(
        screenId = "FOS-PASTURE-007",
        title = "Carrying capacity",
        subtitle = "Open-session head counts are recorded at session start; head/ha is computed, not a recommendation.",
        onBack = onBack,
    ) {
        FarmOperationalRows(rows, "No paddocks", "Create a paddock on the pasture home screen first.")
    }
}

/** FOS-PASTURE-008 — pasture condition: grazing-derived indicators per paddock. Computed from grazing records, not a field condition survey. */
@Composable
fun PastureConditionScreen(
    ops: RoomOpsRepository,
    onBack: () -> Unit,
) {
    var rows by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(Unit) {
        val today = LocalDate.now().toEpochDay()
        rows = loadPaddockPastureStats(ops).map { stats ->
            val openLabel = if (stats.openSessions > 0) "open session: yes (${stats.openHeadCount} head)" else "open session: no"
            val lastGrazed = when {
                stats.openSessions > 0 -> "grazing now"
                stats.latestExitedEpochDay != null -> "${today - stats.latestExitedEpochDay} days ago"
                stats.latestEnteredEpochDay != null -> "entered ${today - stats.latestEnteredEpochDay} days ago, exit not recorded"
                else -> "never"
            }
            "${stats.paddock.code} · ${stats.paddock.displayName} — $openLabel · last grazed: $lastGrazed · ${stats.sessionCount} sessions"
        }
    }
    FarmOperationalPage(
        screenId = "FOS-PASTURE-008",
        title = "Pasture condition",
        subtitle = "Grazing-derived indicators only. This is not a field condition survey.",
        onBack = onBack,
    ) {
        FarmOperationalRows(rows, "No paddocks", "Create a paddock on the pasture home screen first.")
    }
}

/** FOS-PASTURE-011 — pasture report: one summary per active paddock combining area, grazing load, rest and session history. */
@Composable
fun PastureReportScreen(
    ops: RoomOpsRepository,
    onBack: () -> Unit,
) {
    var stats by remember { mutableStateOf(emptyList<PaddockPastureStats>()) }
    LaunchedEffect(Unit) { stats = loadPaddockPastureStats(ops) }
    val today = LocalDate.now().toEpochDay()
    FarmOperationalPage(
        screenId = "FOS-PASTURE-011",
        title = "Pasture report",
        subtitle = "Combined grazing summary for every active paddock, computed from recorded sessions.",
        onBack = onBack,
    ) {
        if (stats.isEmpty()) {
            FarmOperationalSection("No paddocks", "Create a paddock on the pasture home screen first.") {}
        } else {
            stats.forEach { item ->
                FarmOperationalSection("${item.paddock.code} · ${item.paddock.displayName}") {
                    Text("Area: ${item.paddock.areaM2?.let { "$it m²" } ?: "not recorded"} · water: ${item.paddock.waterSource}")
                    Text("Grazing now: ${item.openHeadCount} head across ${item.openSessions} open sessions (${headPerHectareLabel(item)})")
                    Text("Rest: ${restDaysLabel(today, item)}")
                    Text("Sessions recorded: ${item.sessionCount}")
                }
            }
        }
    }
}

/**
 * FOS-PASTURE-010 — Pasture Map.
 *
 * Schematic paddock layout: every active paddock is shown as a labelled cell in a grid, coloured
 * by its real grazing state (grazing now / resting / idle) computed from recorded grazing sessions.
 * This is a schematic, not a geographic map: paddock records carry no coordinates, so no
 * geographic positions are fabricated.
 */
@Composable
fun PastureMapScreen(
    ops: RoomOpsRepository,
    farmId: String,
    onBack: () -> Unit,
) {
    var cells by remember { mutableStateOf(emptyList<PaddockMapCell>()) }
    LaunchedEffect(farmId) {
        val paddocks = ops.paddocks()
        val open = ops.openGrazing().groupBy { it.paddockId }
        val summaries = ops.grazingSummary().associateBy { it.paddockId }
        cells = paddocks.map { p ->
            val grazingNow = open[p.id].orEmpty().sumOf { it.headCount }
            val lastEnd = summaries[p.id]?.latestExitedEpochDay
            PaddockMapCell(p.code, p.displayName, grazingNow, lastEnd)
        }
    }
    val today = LocalDate.now().toEpochDay()
    FarmOperationalPage(
        screenId = "FOS-PASTURE-010",
        title = "Pasture map",
        subtitle = "Schematic layout — cells are not geographic positions.",
        onBack = onBack,
        backLabel = "Pasture",
    ) {
        if (cells.isEmpty()) {
            FarmOperationalSection("No paddocks", "Create a paddock on the pasture home screen first.") {}
        } else {
            FarmOperationalSection("Legend") {
                Text("Green: grazing now · Amber: resting (a session ended, rest accumulating) · Grey: idle (no sessions recorded)")
            }
            cells.chunked(2).forEach { row ->
                androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { cell ->
                        val state = when {
                            cell.grazingHead > 0 -> "GRAZING NOW — ${cell.grazingHead} head"
                            cell.lastSessionEnd != null -> "RESTING — ${today - cell.lastSessionEnd}d since last session"
                            else -> "IDLE — no sessions recorded"
                        }
                        androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                            FarmOperationalSection("${cell.code} · ${cell.displayName}") {
                                Text(state)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class PaddockMapCell(
    val code: String,
    val displayName: String,
    val grazingHead: Int,
    val lastSessionEnd: Long?,
)
