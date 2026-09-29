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
import com.farmos.domain.goat.GoatLactationSummary
import java.math.BigDecimal
import java.time.LocalDate

internal fun goatLitres(milli: Long): String = BigDecimal.valueOf(milli, 3).stripTrailingZeros().toPlainString() + " L"

/**
 * FOS-GOAT-018 — milk recorded for every goat on the farm, from exhaustive per-goat aggregates.
 * Records only: no yield curve, peak, persistency or projection is derived.
 */
@Composable
internal fun GoatLactationDashboardScreen(
    lactation: GoatLactationState,
    onSelectDoe: (String) -> Unit,
    onBack: () -> Unit,
) {
    IllustratedGoatPage("Lactation", "FOS-GOAT-018", onBack) {
        when (lactation) {
            GoatLactationState.Loading -> Text("Loading lactation records")
            is GoatLactationState.Failed -> AnimalFarmWarningSurface { Text(lactation.message) }
            is GoatLactationState.Loaded -> GoatLactationContent(lactation.rows, onSelectDoe)
        }
    }
}

@Composable
private fun GoatLactationContent(rows: List<GoatLactationSummary>, onSelectDoe: (String) -> Unit) {
    if (rows.isEmpty()) {
        AnimalFarmEmptyState("No milk recorded on this device.")
        return
    }
    FarmIllustratedSectionSurface(Modifier.testTag("goat-lactation-summary")) {
        Text("Goats with milk recorded · ${rows.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("${goatLitres(rows.sumOf { it.totalMilli })} across ${rows.sumOf { it.recordCount }} records", color = AnimalFarmTheme.colors.mutedInk)
    }
    val ordered = rows.sortedWith(compareByDescending<GoatLactationSummary> { it.latestEpochDay }.thenBy { it.tag ?: "" }.thenBy { it.animalId })
    FarmIllustratedSectionSurface {
        ordered.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider()
            val label = row.tag?.let { tag -> row.name?.takeIf { it.isNotBlank() }?.let { "$tag · $it" } ?: tag } ?: "Goat not on this device"
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                    .then(if (row.tag != null) Modifier.clickable(role = Role.Button) { onSelectDoe(row.animalId) } else Modifier)
                    .padding(vertical = 6.dp)
                    .testTag("goat-lactation:${row.animalId}"),
            ) {
                Text(label + (row.status?.let { " · ${goatStatusLabel(it)}" } ?: ""), fontWeight = FontWeight.SemiBold)
                Text("${goatLitres(row.totalMilli)} over ${row.recordCount} " + (if (row.recordCount == 1) "record" else "records"))
                Text(
                    "Latest ${LocalDate.ofEpochDay(row.latestEpochDay)}: ${goatLitres(row.latestDayMilli)} · first ${LocalDate.ofEpochDay(row.firstEpochDay)}",
                    color = AnimalFarmTheme.colors.mutedInk,
                )
                Text("SCC results recorded: ${row.sccCount}", color = AnimalFarmTheme.colors.mutedInk)
            }
        }
    }
}
