package com.farmos.feature.rabbit

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.AnimalFarmWarningSurface
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmSearchSelector
import com.farmos.core.design.FarmSelectionAtoms
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.design.FarmSelectorSearch
import com.farmos.core.design.FarmVisualClass
import kotlinx.coroutines.launch

/** One recorded parent of a rabbit. */
data class RabbitParentView(val relation: String, val label: String)

/** The kits' coefficient of inbreeding from recorded pedigree (owner decision D-023). */
data class RabbitCoiView(val coefficient: Double, val generationsKnown: Int, val commonAncestors: List<String>, val conflictingParentage: Int)

/**
 * Rabbit pedigree reads and writes, provided by the host over the whole local rabbitry (D-004, D-023).
 * [link] records a sire or dam and refuses a parent of the wrong sex or species.
 */
class RabbitPedigreePorts(
    val searchRabbits: FarmSelectorSearch,
    val searchDoes: FarmSelectorSearch,
    val searchBucks: FarmSelectorSearch,
    val parents: suspend (rabbitId: String) -> List<RabbitParentView>,
    val link: suspend (rabbitId: String, parentId: String, relation: String) -> Unit,
    val coi: suspend (buckId: String, doeId: String) -> RabbitCoiView,
)

/** FOS-RABBIT-031 — a rabbit's recorded sire and dam, and linking a parent chosen from the whole rabbitry. */
@Composable
internal fun RabbitPedigreeScreen(ports: RabbitPedigreePorts, busy: Boolean, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var rabbit by remember { mutableStateOf<FarmSelectorOption?>(null) }
    var parent by remember { mutableStateOf<FarmSelectorOption?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    FarmOperationalPage("FOS-RABBIT-031", "Rabbit pedigree", "Sire and dam recorded for each rabbit; kits' inbreeding uses them.", FarmVisualClass.I3, onBack) {
        FarmSearchSelector(FarmSelectionAtoms.ANIMAL_SELECTOR, "Rabbit", ports.searchRabbits, rabbit, { rabbit = it; parent = null }, "No rabbits match on this device", enabled = !busy && !working)
        val chosen = rabbit
        if (chosen != null) {
            val parents by produceState<List<RabbitParentView>?>(null, chosen.id, version) { value = runCatching { ports.parents(chosen.id) }.getOrElse { emptyList() } }
            FarmOperationalSection("Recorded parents") {
                val current = parents
                when {
                    current == null -> Text("Reading pedigree")
                    current.isEmpty() -> Text("No parents recorded for ${chosen.label}.")
                    else -> current.forEach { Text("${it.relation.replaceFirstChar { c -> c.uppercase() }}: ${it.label}", modifier = Modifier.testTag("rabbit-parent:${it.relation}")) }
                }
            }
            FarmOperationalSection("Link a parent") {
                FarmSearchSelector(FarmSelectionAtoms.SIRE_DAM_SELECTOR, "Parent", ports.searchRabbits, parent, { parent = it }, "No rabbits match on this device", enabled = !busy && !working)
                listOf("sire" to "Link as sire", "dam" to "Link as dam").forEach { (relation, label) ->
                    TextButton(
                        onClick = {
                            parent?.let { picked ->
                                scope.launch {
                                    working = true
                                    error = null
                                    runCatching { ports.link(chosen.id, picked.id, relation) }.onSuccess { version++; parent = null }.onFailure { error = it.message }
                                    working = false
                                }
                            }
                        },
                        enabled = !busy && !working && parent != null,
                        modifier = Modifier.fillMaxWidth().testTag("rabbit-link:$relation"),
                    ) { Text(label) }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

/** FOS-GEN-006 — the kits' inbreeding for a doe and buck chosen from the whole rabbitry. */
@Composable
internal fun RabbitCoiScreen(ports: RabbitPedigreePorts, busy: Boolean, onBack: () -> Unit) {
    var doe by remember { mutableStateOf<FarmSelectorOption?>(null) }
    var buck by remember { mutableStateOf<FarmSelectorOption?>(null) }
    FarmOperationalPage("FOS-GEN-006", "Inbreeding check", "Inbreeding of the kits from parentage recorded on this device.", FarmVisualClass.I3, onBack) {
        FarmSearchSelector(FarmSelectionAtoms.ANIMAL_SELECTOR, "Doe", ports.searchDoes, doe, { doe = it }, "No does match on this device", enabled = !busy)
        FarmSearchSelector(FarmSelectionAtoms.SIRE_DAM_SELECTOR, "Buck", ports.searchBucks, buck, { buck = it }, "No bucks match on this device", enabled = !busy)
        FarmOperationalSection("Result") {
            val d = doe
            val b = buck
            if (d == null || b == null) {
                Text("Choose a doe and a buck.")
            } else {
                val result by produceState<Result<RabbitCoiView>?>(null, d.id, b.id) { value = runCatching { ports.coi(b.id, d.id) } }
                val current = result
                when {
                    current == null -> Text("Reading pedigree")
                    current.isFailure -> AnimalFarmWarningSurface { Text(current.exceptionOrNull()?.message ?: "Pedigree could not be read") }
                    else -> {
                        val view = current.getOrThrow()
                        val depth = when (view.generationsKnown) {
                            0 -> "no complete generation recorded"
                            1 -> "1 complete generation recorded"
                            else -> "${view.generationsKnown} complete generations recorded"
                        }
                        Text("Inbreeding of the kits (COI) ${"%.2f".format(view.coefficient * 100)}% · $depth", fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("rabbit-coi"))
                        Text(if (view.commonAncestors.isEmpty()) "Common ancestors: none recorded" else "Common ancestors: ${view.commonAncestors.joinToString()}")
                        if (view.conflictingParentage > 0) {
                            AnimalFarmWarningSurface { Text("${view.conflictingParentage} rabbit(s) have conflicting parentage recorded and were left out.") }
                        }
                        Text("Only recorded parentage counts; a thin pedigree can hide relatedness.", color = AnimalFarmTheme.colors.mutedInk)
                    }
                }
            }
        }
    }
}
