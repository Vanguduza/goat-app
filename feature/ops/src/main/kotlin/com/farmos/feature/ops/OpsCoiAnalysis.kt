package com.farmos.feature.ops

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.AnimalFarmWarningSurface
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmVisualClass

/** The offspring's coefficient of inbreeding from recorded pedigree (owner decision D-023, resolution R7). */
data class MateCoiView(val coefficient: Double, val generationsKnown: Int, val commonAncestors: List<String>, val conflictingParentage: Int)

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
