package com.farmos.feature.rabbit

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.farmos.core.design.FarmSelectorSearch
import com.farmos.core.design.FarmVisualClass
import java.time.LocalDate
import kotlinx.coroutines.launch

/** One recorded parent of a rabbit. */
data class RabbitParentView(val relation: String, val label: String)

/** The kits' coefficient of inbreeding from recorded pedigree (owner decision D-023). */
data class RabbitCoiView(
    val coefficient: Double,
    val generationsKnown: Int,
    val commonAncestors: List<String>,
    val conflictingParentage: Int,
    /** The buck's recorded facts for candidate compare; null where not recorded. */
    val buckDateOfBirthEpochDay: Long? = null,
    val buckLatestWeightGrams: Long? = null,
)

/** The kits' inbreeding as a percentage, with how much pedigree it rests on. */
internal fun rabbitCoiText(view: RabbitCoiView): String {
    val depth = when (view.generationsKnown) {
        0 -> "no complete generation recorded"
        1 -> "1 complete generation recorded"
        else -> "${view.generationsKnown} complete generations recorded"
    }
    return "Inbreeding of the kits (COI) ${"%.2f".format(view.coefficient * 100)}% · $depth"
}

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
                        Text(rabbitCoiText(view), fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("rabbit-coi"))
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

/**
 * FOS-GEN-007 — Compare bucks for one doe (owner decision D-023): up to four bucks chosen from the whole rabbitry,
 * side by side with recorded facts ("Not recorded" where missing) and the kits' COI, in the order added. Nothing
 * is ranked, because a composite rank needs criteria and weights set by the farm.
 */
@Composable
internal fun RabbitMateCompareScreen(ports: RabbitPedigreePorts, busy: Boolean, onBack: () -> Unit) {
    var doe by remember { mutableStateOf<FarmSelectorOption?>(null) }
    var chosen by remember { mutableStateOf(emptyList<FarmSelectorOption>()) }
    FarmOperationalPage("FOS-GEN-007", "Compare bucks", "Recorded facts and inbreeding of the kits, side by side. No ranking is shown.", FarmVisualClass.I3, onBack) {
        FarmSearchSelector(FarmSelectionAtoms.ANIMAL_SELECTOR, "Doe", ports.searchDoes, doe, { doe = it }, "No does match on this device", enabled = !busy)
        if (chosen.size < MAX_COMPARED) {
            FarmSearchSelector(
                FarmSelectionAtoms.SIRE_DAM_SELECTOR, "Add a buck", ports.searchBucks, null,
                { option -> if (chosen.none { it.id == option.id }) chosen = chosen + option },
                "No bucks match on this device", enabled = !busy,
            )
        }
        val d = doe
        when {
            d == null -> AnimalFarmEmptyState("Choose a doe to compare bucks for.")
            chosen.isEmpty() -> AnimalFarmEmptyState("Add up to $MAX_COMPARED bucks to compare.")
            else -> chosen.forEach { buck ->
                key(buck.id) { RabbitMateCandidate(ports, d.id, buck) { chosen = chosen.filterNot { it.id == buck.id } } }
            }
        }
        Text("Only recorded parentage counts; a thin pedigree can hide relatedness.", color = AnimalFarmTheme.colors.mutedInk)
    }
}

@Composable
private fun RabbitMateCandidate(ports: RabbitPedigreePorts, doeId: String, buck: FarmSelectorOption, onRemove: () -> Unit) {
    val result by produceState<Result<RabbitCoiView>?>(null, doeId, buck.id) { value = runCatching { ports.coi(buck.id, doeId) } }
    FarmOperationalSection(buck.label) {
        val current = result
        when {
            current == null -> Text("Reading pedigree")
            current.isFailure -> AnimalFarmWarningSurface { Text(current.exceptionOrNull()?.message ?: "Pedigree could not be read") }
            else -> {
                val view = current.getOrThrow()
                Text("Born: ${view.buckDateOfBirthEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: NOT_RECORDED}")
                Text("Latest weight: ${view.buckLatestWeightGrams?.let { "%.2f kg".format(it / 1000.0) } ?: NOT_RECORDED}")
                Text(rabbitCoiText(view), fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("rabbit-candidate-coi:${buck.id}"))
                Text(if (view.commonAncestors.isEmpty()) "Common ancestors: none recorded" else "Common ancestors: ${view.commonAncestors.joinToString()}")
                if (view.conflictingParentage > 0) {
                    AnimalFarmWarningSurface { Text("${view.conflictingParentage} rabbit(s) have conflicting parentage recorded and were left out.") }
                }
            }
        }
        TextButton(
            onClick = onRemove,
            modifier = Modifier.fillMaxWidth().heightIn(min = AnimalFarmTheme.minimumTouchDp.dp).testTag("rabbit-candidate-remove:${buck.id}"),
        ) { Text("Remove ${buck.label}") }
    }
}

private const val MAX_COMPARED = 4
private const val NOT_RECORDED = "Not recorded"
