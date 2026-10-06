package com.farmos.feature.goat

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.farmos.core.design.FarmSearchSelector
import com.farmos.core.design.FarmSelectionAtoms
import com.farmos.core.design.FarmSelectorOption
import com.farmos.domain.goat.GoatSex
import java.time.LocalDate

/**
 * A buck as a prospective sire for a doe (owner decision D-023, resolution R7): recorded facts and the
 * coefficient of inbreeding of their kids from recorded pedigree, with how much pedigree is known.
 */
data class GoatMateCandidate(
    val buckId: String,
    val label: String,
    val dateOfBirthEpochDay: Long?,
    val latestWeightGrams: Long?,
    /** Wright's F of the kids, 0..1, from recorded pedigree; a lower bound where pedigree is missing. */
    val coefficient: Double,
    val generationsKnown: Int,
    val commonAncestors: List<String>,
    /** Animals whose recorded parentage conflicts and was left out of the figure. */
    val conflictingParentage: Int,
)

/** Reads a prospective mating from the local pedigree. */
fun interface GoatMateAnalysis {
    suspend fun candidate(doeId: String, buckId: String): GoatMateCandidate
}

val NoGoatMateAnalysis = GoatMateAnalysis { _, _ -> error("Pedigree analysis is not available here") }

/** The kids' coefficient of inbreeding as a percentage, with how much pedigree it rests on. */
internal fun goatCoiText(candidate: GoatMateCandidate): String {
    val percent = "%.2f".format(candidate.coefficient * 100)
    val depth = when (candidate.generationsKnown) {
        0 -> "no complete generation recorded"
        1 -> "1 complete generation recorded"
        else -> "${candidate.generationsKnown} complete generations recorded"
    }
    return "Kids' inbreeding (COI) $percent% · $depth"
}

/** COI of the selected doe with a chosen sire, shown under the sire selector (FOS-GOAT-033). */
@Composable
internal fun GoatMatingCoi(analysis: GoatMateAnalysis, doeId: String, buckId: String) {
    val result by produceState<Result<GoatMateCandidate>?>(null, analysis, doeId, buckId) {
        value = runCatching { analysis.candidate(doeId, buckId) }
    }
    val current = result
    Text(
        when {
            current == null -> "Reading pedigree"
            current.isFailure -> current.exceptionOrNull()?.message ?: "Pedigree could not be read"
            else -> goatCoiText(current.getOrThrow())
        },
        color = AnimalFarmTheme.colors.mutedInk,
        modifier = Modifier.testTag("goat-mating-coi"),
    )
}

/**
 * FOS-GOAT-046 — Breeding candidate compare: bucks chosen from the whole farm, side by side for the
 * selected doe. Facts are shown as recorded, with "Not recorded" where missing; there is no ranking,
 * because a composite rank needs criteria and weights set by the farm.
 */
@Composable
internal fun GoatMateCompareScreen(
    state: GoatSliceUiState,
    actions: GoatExperienceActions,
    onBack: () -> Unit,
) {
    var chosen by remember { mutableStateOf(emptyList<FarmSelectorOption>()) }
    IllustratedGoatPage("Compare bucks", "FOS-GOAT-046", onBack) {
        val doe = state.selected
        if (doe == null || doe.sex != GoatSex.FEMALE) {
            AnimalFarmEmptyState("Select a doe to compare bucks for her.")
            return@IllustratedGoatPage
        }
        Text("For ${goatDisplayName(doe)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Bucks appear in the order you add them. The inbreeding figure counts only recorded parentage, so a thin pedigree can hide relatedness. No ranking is shown.",
            color = AnimalFarmTheme.colors.mutedInk,
        )
        if (chosen.size < MAX_COMPARED) {
            FarmSearchSelector(
                atomTag = FarmSelectionAtoms.SIRE_DAM_SELECTOR,
                title = "Add a buck",
                search = actions.searchSires,
                selected = null,
                onSelect = { option -> if (chosen.none { it.id == option.id }) chosen = chosen + option },
                emptyText = "No bucks match on this device",
                enabled = !state.busy,
            )
        }
        if (chosen.isEmpty()) AnimalFarmEmptyState("Add up to $MAX_COMPARED bucks to compare.")
        chosen.forEach { buck ->
            GoatMateCandidateCard(actions.mateAnalysis, doe.animalId, buck) { chosen = chosen.filterNot { it.id == buck.id } }
        }
    }
}

@Composable
private fun GoatMateCandidateCard(analysis: GoatMateAnalysis, doeId: String, buck: FarmSelectorOption, onRemove: () -> Unit) {
    val result by produceState<Result<GoatMateCandidate>?>(null, analysis, doeId, buck.id) {
        value = runCatching { analysis.candidate(doeId, buck.id) }
    }
    FarmIllustratedSectionSurface(Modifier.testTag("goat-mate-candidate:${buck.id}")) {
        Text(buck.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        val current = result
        when {
            current == null -> Text("Reading pedigree")
            current.isFailure -> AnimalFarmWarningSurface { Text(current.exceptionOrNull()?.message ?: "Pedigree could not be read") }
            else -> {
                val candidate = current.getOrThrow()
                Text("Born: ${candidate.dateOfBirthEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: NOT_RECORDED}")
                Text("Latest weight: ${candidate.latestWeightGrams?.let { "%.1f kg".format(it / 1000.0) } ?: NOT_RECORDED}")
                Text(goatCoiText(candidate), fontWeight = FontWeight.SemiBold)
                Text(
                    if (candidate.commonAncestors.isEmpty()) "Common ancestors: none recorded" else "Common ancestors: ${candidate.commonAncestors.joinToString()}",
                )
                if (candidate.conflictingParentage > 0) {
                    AnimalFarmWarningSurface {
                        Text("${candidate.conflictingParentage} animal(s) have conflicting parentage recorded and were left out. Correct the pedigree for a complete figure.")
                    }
                }
            }
        }
        TextButton(
            onClick = onRemove,
            modifier = Modifier.fillMaxWidth().heightIn(min = AnimalFarmTheme.minimumTouchDp.dp).testTag("goat-mate-remove:${buck.id}"),
        ) { Text("Remove ${buck.label}") }
    }
}

private const val MAX_COMPARED = 4
private const val NOT_RECORDED = "Not recorded"
