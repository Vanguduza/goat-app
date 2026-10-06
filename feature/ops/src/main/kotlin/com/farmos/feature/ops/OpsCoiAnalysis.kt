package com.farmos.feature.ops

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmSearchSelector
import com.farmos.core.design.FarmSelectionAtoms
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.design.FarmVisualClass
import java.time.LocalDate

/** The offspring's coefficient of inbreeding from recorded pedigree (owner decision D-023, resolution R7). */
data class MateCoiView(
    val coefficient: Double,
    val generationsKnown: Int,
    val commonAncestors: List<String>,
    val conflictingParentage: Int,
    /** The sire's recorded facts for candidate compare; null where not recorded. */
    val sireDateOfBirthEpochDay: Long? = null,
    val sireLatestWeightGrams: Long? = null,
)

/** Reads a prospective mating from the local pedigree; provided by the hosting module. */
fun interface MateCoiAnalysis {
    suspend fun analyse(sireId: String, damId: String): MateCoiView
}

/** The kids', lambs' or calves' inbreeding as a percentage, with how much pedigree it rests on. */
internal fun mateCoiText(view: MateCoiView, young: String): String {
    val depth = when (view.generationsKnown) {
        0 -> "no complete generation recorded"
        1 -> "1 complete generation recorded"
        else -> "${view.generationsKnown} complete generations recorded"
    }
    return "Inbreeding of the $young (COI) ${"%.2f".format(view.coefficient * 100)}% · $depth"
}

/**
 * FOS-GEN-006 — COI analysis for a prospective mating of this species: the dam and sire are chosen from every
 * animal on the farm, and the offspring's coefficient of inbreeding is shown with the pedigree it rests on.
 * A thin pedigree can hide relatedness, so the figure is a lower bound; nothing is ranked.
 */
@Composable
internal fun OpsCoiAnalysisPage(femaleLabel: String, maleLabel: String, young: String, busy: Boolean, onBack: () -> Unit) {
    var damId by remember { mutableStateOf("") }
    var sireId by remember { mutableStateOf("") }
    val analysis = LocalOpsAnimalSearch.current.mateCoi
    FarmOperationalPage("FOS-GEN-006", "Inbreeding check", "Inbreeding of the $young from parentage recorded on this device.", FarmVisualClass.I3, onBack) {
        OpsAnimalPicker(femaleLabel, damId, OpsAnimalFilter.FEMALE, busy) { damId = it }
        OpsAnimalPicker(maleLabel, sireId, OpsAnimalFilter.MALE, busy, parent = true) { sireId = it }
        FarmOperationalSection("Result") {
            when {
                analysis == null -> Text("Pedigree analysis is not available here.")
                damId.isBlank() || sireId.isBlank() -> Text("Choose a ${femaleLabel.lowercase()} and a ${maleLabel.lowercase()}.")
                else -> {
                    val result by produceState<Result<MateCoiView>?>(null, analysis, damId, sireId) { value = runCatching { analysis.analyse(sireId, damId) } }
                    val current = result
                    when {
                        current == null -> Text("Reading pedigree")
                        current.isFailure -> AnimalFarmWarningSurface { Text(current.exceptionOrNull()?.message ?: "Pedigree could not be read") }
                        else -> {
                            val view = current.getOrThrow()
                            Text(mateCoiText(view, young), fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("mate-coi"))
                            Text(if (view.commonAncestors.isEmpty()) "Common ancestors: none recorded" else "Common ancestors: ${view.commonAncestors.joinToString()}")
                            if (view.conflictingParentage > 0) {
                                AnimalFarmWarningSurface { Text("${view.conflictingParentage} animal(s) have conflicting parentage recorded and were left out.") }
                            }
                            Text("Only recorded parentage counts; a thin pedigree can hide relatedness.", color = AnimalFarmTheme.colors.mutedInk)
                        }
                    }
                }
            }
        }
    }
}

/**
 * FOS-GEN-007 — Mate comparison for this species (owner decision D-023): up to four sires chosen from every male
 * on the farm, side by side for one dam, with recorded facts ("Not recorded" where missing) and the offspring's
 * COI. Sires appear in the order they are added; nothing is ranked, because a composite rank needs criteria and
 * weights set by the farm.
 */
@Composable
internal fun OpsMateComparePage(femaleLabel: String, maleLabel: String, young: String, busy: Boolean, onBack: () -> Unit) {
    var damId by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf(emptyList<FarmSelectorOption>()) }
    val search = LocalOpsAnimalSearch.current
    val analysis = search.mateCoi
    val males = "${maleLabel.lowercase()}s"
    FarmOperationalPage("FOS-GEN-007", "Compare $males", "Recorded facts and inbreeding of the $young, side by side. No ranking is shown.", FarmVisualClass.I3, onBack) {
        OpsAnimalPicker(femaleLabel, damId, OpsAnimalFilter.FEMALE, busy) { damId = it }
        if (chosen.size < MAX_COMPARED) {
            FarmSearchSelector(
                atomTag = FarmSelectionAtoms.SIRE_DAM_SELECTOR,
                title = "Add a ${maleLabel.lowercase()}",
                search = search.males,
                selected = null,
                onSelect = { option -> if (chosen.none { it.id == option.id }) chosen = chosen + option },
                emptyText = "No animals match on this device",
                enabled = !busy,
            )
        }
        when {
            analysis == null -> AnimalFarmEmptyState("Pedigree analysis is not available here.")
            damId.isBlank() -> AnimalFarmEmptyState("Choose a ${femaleLabel.lowercase()} to compare $males for.")
            chosen.isEmpty() -> AnimalFarmEmptyState("Add up to $MAX_COMPARED $males to compare.")
            else -> chosen.forEach { sire ->
                key(sire.id) { OpsMateCandidate(analysis, damId, sire, young) { chosen = chosen.filterNot { it.id == sire.id } } }
            }
        }
        Text("Only recorded parentage counts; a thin pedigree can hide relatedness.", color = AnimalFarmTheme.colors.mutedInk)
    }
}

@Composable
private fun OpsMateCandidate(analysis: MateCoiAnalysis, damId: String, sire: FarmSelectorOption, young: String, onRemove: () -> Unit) {
    val result by produceState<Result<MateCoiView>?>(null, analysis, damId, sire.id) { value = runCatching { analysis.analyse(sire.id, damId) } }
    FarmOperationalSection(sire.label) {
        val current = result
        when {
            current == null -> Text("Reading pedigree")
            current.isFailure -> AnimalFarmWarningSurface { Text(current.exceptionOrNull()?.message ?: "Pedigree could not be read") }
            else -> {
                val view = current.getOrThrow()
                Text("Born: ${view.sireDateOfBirthEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: NOT_RECORDED}")
                Text("Latest weight: ${view.sireLatestWeightGrams?.let { "%.1f kg".format(it / 1000.0) } ?: NOT_RECORDED}")
                Text(mateCoiText(view, young), fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("mate-candidate-coi:${sire.id}"))
                Text(if (view.commonAncestors.isEmpty()) "Common ancestors: none recorded" else "Common ancestors: ${view.commonAncestors.joinToString()}")
                if (view.conflictingParentage > 0) {
                    AnimalFarmWarningSurface { Text("${view.conflictingParentage} animal(s) have conflicting parentage recorded and were left out.") }
                }
            }
        }
        TextButton(
            onClick = onRemove,
            modifier = Modifier.fillMaxWidth().heightIn(min = AnimalFarmTheme.minimumTouchDp.dp).testTag("mate-candidate-remove:${sire.id}"),
        ) { Text("Remove ${sire.label}") }
    }
}

private const val MAX_COMPARED = 4
private const val NOT_RECORDED = "Not recorded"
