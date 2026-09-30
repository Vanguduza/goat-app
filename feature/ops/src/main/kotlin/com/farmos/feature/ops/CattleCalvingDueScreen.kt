package com.farmos.feature.ops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.AnimalFarmWarningSurface
import com.farmos.core.design.FarmIllustratedSectionSurface
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmVisualClass
import java.time.LocalDate

/** Where a cow's expected calving came from. */
enum class CattleDueSource {
    /** The expected calving date recorded at dry-off; it wins over the calculation. */
    STORED,

    /** The service date plus the farm's cattle gestation period. */
    PREDICTED,
}

/** One cow expected to calve from her latest service. [pdResult] is null when no PD is recorded since. */
data class CattleCalvingDueView(
    val animalId: String,
    val tag: String,
    val name: String?,
    val serviceEpochDay: Long,
    val method: String,
    val pdResult: String?,
    val source: CattleDueSource,
    val earliestEpochDay: Long,
    val typicalEpochDay: Long,
    val latestEpochDay: Long,
)

/** Every cow expected to calve, with the farm's typical cattle gestation used for predictions. */
data class CattleCalvingDue(val rows: List<CattleCalvingDueView>, val typicalDays: Int)

/** Windows on the calving list, relative to today. */
enum class CattleDueWindow(val label: String) {
    OVERDUE("Past the latest expected day"),
    DUE_NOW("In the expected window now"),
    NEXT_THREE_WEEKS("Window opens within three weeks"),
    LATER("Later"),
}

fun cattleDueWindow(row: CattleCalvingDueView, todayEpochDay: Long): CattleDueWindow = when {
    todayEpochDay > row.latestEpochDay -> CattleDueWindow.OVERDUE
    todayEpochDay >= row.earliestEpochDay -> CattleDueWindow.DUE_NOW
    row.earliestEpochDay - todayEpochDay <= 21 -> CattleDueWindow.NEXT_THREE_WEEKS
    else -> CattleDueWindow.LATER
}

private sealed interface CalvingDueState {
    data object Loading : CalvingDueState
    data class Failed(val message: String) : CalvingDueState
    data class Loaded(val due: CattleCalvingDue) : CalvingDueState
}

private fun dueDate(epochDay: Long) = LocalDate.ofEpochDay(epochDay).toString()

/**
 * FOS-CATTLE-014 — every active cow expected to calve, from her latest service: a recorded calving
 * removes her, a PD of open since the service leaves her out, a dry-off expected calving date wins over
 * the prediction, otherwise the farm's cattle gestation period dates her. Read-only.
 */
@Composable
internal fun CattleCalvingDueScreen(loadDue: suspend () -> CattleCalvingDue, today: LocalDate, onBack: () -> Unit) {
    var state by remember { mutableStateOf<CalvingDueState>(CalvingDueState.Loading) }
    LaunchedEffect(Unit) {
        state = runCatching { loadDue() }.fold({ CalvingDueState.Loaded(it) }, { CalvingDueState.Failed(it.message ?: "Cows expected to calve could not be loaded") })
    }
    FarmOperationalPage(
        "FOS-CATTLE-014",
        "Calving due",
        "Cows expected to calve from their latest service, grouped by how close the window is.",
        FarmVisualClass.I2,
        onBack,
    ) {
        when (val current = state) {
            CalvingDueState.Loading -> Text("Loading cows expected to calve")
            is CalvingDueState.Failed -> AnimalFarmWarningSurface { Text(current.message) }
            is CalvingDueState.Loaded -> {
                Text(
                    "Predicted from each cow's latest service with this farm's ${current.due.typicalDays}-day gestation unless an expected calving date was recorded at dry-off. " +
                        "A recorded calving removes her from the list; a cow diagnosed open since her service is not listed.",
                    color = AnimalFarmTheme.colors.mutedInk,
                )
                if (current.due.rows.isEmpty()) {
                    AnimalFarmEmptyState("No cows are expected to calve.")
                    return@FarmOperationalPage
                }
                val grouped = current.due.rows.groupBy { cattleDueWindow(it, today.toEpochDay()) }
                CattleDueWindow.entries.forEach { window ->
                    val rows = grouped[window].orEmpty()
                    if (rows.isEmpty()) return@forEach
                    FarmIllustratedSectionSurface(Modifier.testTag("cattle-due-group:${window.name}")) {
                        Text("${window.label} · ${rows.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        rows.forEachIndexed { index, row ->
                            if (index > 0) HorizontalDivider()
                            CalvingDueRow(row)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalvingDueRow(row: CattleCalvingDueView) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("cattle-due:${row.animalId}"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(listOfNotNull(row.name, row.tag).joinToString(" · "), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(
            when (row.source) {
                CattleDueSource.STORED -> "Expected ${dueDate(row.typicalEpochDay)} · recorded at dry-off"
                CattleDueSource.PREDICTED -> "Due about ${dueDate(row.typicalEpochDay)} (${dueDate(row.earliestEpochDay)} to ${dueDate(row.latestEpochDay)})"
            },
        )
        Text(
            (if (row.pdResult == "pregnant") "PD pregnant" else "Served, no PD recorded") + " · ${row.method} ${dueDate(row.serviceEpochDay)}",
            color = AnimalFarmTheme.colors.mutedInk,
        )
    }
}
