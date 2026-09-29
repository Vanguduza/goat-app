package com.farmos.feature.goat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.FarmIllustratedSectionSurface
import com.farmos.domain.goat.GoatPedigreeLink
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.GoatSnapshot
import com.farmos.domain.goat.GoatStatus
import java.time.LocalDate

/** FOS-GOAT-030 — one doe's recorded heats, services, checks and kiddings. */
@Composable
internal fun GoatDoeReproductionScreen(
    goat: GoatSnapshot?,
    onOpen: (GoatPage) -> Unit,
    onOpenKidding: (String) -> Unit,
    onBack: () -> Unit,
) {
    GoatHistoryFrame("Doe reproduction", "FOS-GOAT-030", goat, onBack) { subject ->
        if (subject.sex != GoatSex.FEMALE) {
            AnimalFarmEmptyState("Reproduction records apply to does.")
            return@GoatHistoryFrame
        }
        val summary = GoatReproductionRecords.breedingSummary(subject)
        FarmIllustratedSectionSurface {
            Text(summary.status.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("goat-breeding-status"))
            summary.decidedOnEpochDay?.let { Text("Latest record ${LocalDate.ofEpochDay(it)}", color = AnimalFarmTheme.colors.mutedInk) }
        }
        GoatReproductionSection("Pregnancy checks", subject.pregnancyHistory.sortedByDescending { it.occurredEpochDay }.map {
            LocalDate.ofEpochDay(it.occurredEpochDay).toString() to if (it.result == "pregnant") "Pregnant" else "Open"
        })
        GoatReproductionSection("Services", subject.matingHistory.sortedByDescending { it.occurredEpochDay }.map {
            val sire = it.sireLabel ?: it.sireId?.let { "Sire not on this device" } ?: "Sire not recorded"
            LocalDate.ofEpochDay(it.occurredEpochDay).toString() + " · " + GoatReproductionRecords.matingMethodLabel(it.method) to sire
        })
        GoatReproductionSection("Heats", subject.heatHistory.sortedByDescending { it.occurredEpochDay }.map {
            LocalDate.ofEpochDay(it.occurredEpochDay).toString() to "Heat observed"
        })
        FarmIllustratedSectionSurface {
            Text("Kiddings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val kiddings = subject.kiddingHistory.sortedByDescending { it.occurredEpochDay }
            if (kiddings.isEmpty()) Text("None recorded on this device.", color = AnimalFarmTheme.colors.mutedInk)
            kiddings.forEach { kidding ->
                GoatHistoryRow(
                    LocalDate.ofEpochDay(kidding.occurredEpochDay).toString(),
                    "${kidding.bornCount} born · ${kidding.liveCount} live",
                    Modifier
                        .heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                        .clickable(role = Role.Button) { onOpenKidding(kidding.kiddingId) },
                )
            }
        }
        if (subject.status == GoatStatus.ACTIVE) {
            TextButton(
                onClick = { onOpen(GoatPage.REPRODUCTION) },
                modifier = Modifier.fillMaxWidth().heightIn(min = AnimalFarmTheme.minimumTouchDp.dp),
            ) { Text("Record heat, service or check") }
        }
    }
}

@Composable
private fun GoatReproductionSection(title: String, rows: List<Pair<String, String>>) {
    FarmIllustratedSectionSurface {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (rows.isEmpty()) Text("None recorded on this device.", color = AnimalFarmTheme.colors.mutedInk)
        rows.forEach { (primary, secondary) -> GoatHistoryRow(primary, secondary) }
    }
}

/** FOS-GOAT-035 — active does grouped by their recorded breeding status. */
@Composable
internal fun GoatPregnancyDashboardScreen(
    herd: List<GoatSnapshot>,
    onSelectDoe: (String) -> Unit,
    onBack: () -> Unit,
) {
    IllustratedGoatPage("Pregnancy", "FOS-GOAT-035", onBack) {
        val board = GoatReproductionRecords.pregnancyBoard(herd)
        if (board.isEmpty()) {
            AnimalFarmEmptyState("No active does on this device.")
            return@IllustratedGoatPage
        }
        board.forEach { (status, does) ->
            FarmIllustratedSectionSurface(Modifier.testTag("goat-pregnancy-group:${status.name}")) {
                Text("${status.label} · ${does.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                does.forEachIndexed { index, (doe, summary) ->
                    if (index > 0) HorizontalDivider()
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                            .clickable(role = Role.Button) { onSelectDoe(doe.animalId) }
                            .padding(vertical = 6.dp),
                    ) {
                        Text(goatDisplayName(doe) + " · " + doe.tag, fontWeight = FontWeight.SemiBold)
                        summary.decidedOnEpochDay?.let { Text("Latest record ${LocalDate.ofEpochDay(it)}", color = AnimalFarmTheme.colors.mutedInk) }
                    }
                }
            }
        }
    }
}

/** FOS-GOAT-044 — recorded parents, grandparents and offspring. */
@Composable
internal fun GoatPedigreeScreen(goat: GoatSnapshot?, onBack: () -> Unit) {
    GoatHistoryFrame("Pedigree", "FOS-GOAT-044", goat, onBack) { subject ->
        val pedigree = subject.pedigree
        if (pedigree.parents.isEmpty() && pedigree.offspring.isEmpty()) {
            AnimalFarmEmptyState("No pedigree links recorded for this goat on this device.")
            return@GoatHistoryFrame
        }
        FarmIllustratedSectionSurface {
            Text("Parents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (pedigree.parents.isEmpty()) Text("None recorded on this device.", color = AnimalFarmTheme.colors.mutedInk)
            pedigree.parents.forEach { parent ->
                GoatPedigreeRow(parent)
                pedigree.grandparents[parent.relativeId].orEmpty().forEach { grandparent ->
                    GoatHistoryRow(
                        "  ${GoatReproductionRecords.relationLabel(parent.relationType)}'s ${GoatReproductionRecords.relationLabel(grandparent.relationType).lowercase()}",
                        grandparent.label ?: "Not on this device",
                        Modifier.testTag("goat-pedigree:${grandparent.linkId}"),
                    )
                }
            }
        }
        FarmIllustratedSectionSurface {
            Text("Offspring · ${pedigree.offspring.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            pedigree.offspring.forEach { child ->
                GoatHistoryRow(child.label ?: "Not on this device", GoatReproductionRecords.relationLabel(child.relationType) + " link", Modifier.testTag("goat-pedigree:${child.linkId}"))
            }
        }
    }
}

@Composable
private fun GoatPedigreeRow(link: GoatPedigreeLink) {
    GoatHistoryRow(
        GoatReproductionRecords.relationLabel(link.relationType),
        link.label ?: "Not on this device",
        Modifier.testTag("goat-pedigree:${link.linkId}"),
    )
}
