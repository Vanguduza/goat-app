package com.farmos.feature.goat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.AnimalFarmWarningSurface
import com.farmos.core.design.FarmIllustratedSectionSurface
import com.farmos.domain.goat.GoatKiddingDue
import java.time.LocalDate

/** Windows on the kidding list, relative to today. */
internal enum class GoatDueWindow(val label: String) {
    OVERDUE("Past the latest expected day"),
    DUE_NOW("In the expected window now"),
    NEXT_TWO_WEEKS("Window opens within two weeks"),
    LATER("Later"),
}

internal fun goatDueWindow(row: GoatKiddingDue, todayEpochDay: Long): GoatDueWindow = when {
    todayEpochDay > row.latestDueEpochDay -> GoatDueWindow.OVERDUE
    todayEpochDay >= row.earliestDueEpochDay -> GoatDueWindow.DUE_NOW
    row.earliestDueEpochDay - todayEpochDay <= 14 -> GoatDueWindow.NEXT_TWO_WEEKS
    else -> GoatDueWindow.LATER
}

/**
 * FOS-GOAT-036 — every active doe expected to kid, from her latest service and the farm's gestation
 * period, grouped by how close her window is. The list is exhaustive over the farm.
 */
@Composable
internal fun GoatKiddingDueScreen(
    due: GoatKiddingDueState,
    todayEpochDay: Long,
    onSelectDoe: (String) -> Unit,
    onBack: () -> Unit,
) {
    IllustratedGoatPage("Kidding due", "FOS-GOAT-036", onBack) {
        when (due) {
            GoatKiddingDueState.Loading -> Text("Loading does expected to kid")
            is GoatKiddingDueState.Failed -> AnimalFarmWarningSurface { Text(due.message) }
            is GoatKiddingDueState.Loaded -> {
                Text(
                    "Predicted from each doe's latest service with this farm's ${due.typicalDays}-day gestation. A recorded kidding removes her from the list; a doe checked open since her service is not listed.",
                    color = AnimalFarmTheme.colors.mutedInk,
                )
                if (due.rows.isEmpty()) {
                    AnimalFarmEmptyState("No does are expected to kid.")
                    return@IllustratedGoatPage
                }
                val grouped = due.rows.groupBy { goatDueWindow(it, todayEpochDay) }
                GoatDueWindow.entries.forEach { window ->
                    val rows = grouped[window].orEmpty()
                    if (rows.isEmpty()) return@forEach
                    FarmIllustratedSectionSurface(Modifier.testTag("goat-due-group:${window.name}")) {
                        Text("${window.label} · ${rows.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        rows.forEachIndexed { index, row ->
                            if (index > 0) HorizontalDivider()
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                                    .clickable(role = Role.Button) { onSelectDoe(row.animalId) }
                                    .padding(vertical = 6.dp)
                                    .testTag("goat-due:${row.animalId}"),
                            ) {
                                Text(listOfNotNull(row.name, row.tag).joinToString(" · "), fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Due about ${LocalDate.ofEpochDay(row.typicalDueEpochDay)} (${LocalDate.ofEpochDay(row.earliestDueEpochDay)} to ${LocalDate.ofEpochDay(row.latestDueEpochDay)})",
                                )
                                Text(
                                    (if (row.confirmedPregnant) "Confirmed pregnant" else "Bred, not checked") + " · served ${LocalDate.ofEpochDay(row.serviceEpochDay)}",
                                    color = AnimalFarmTheme.colors.mutedInk,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
