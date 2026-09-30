package com.farmos.feature.ops

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.farmos.core.design.FarmEntitySelector
import com.farmos.core.design.FarmSearchSelector
import com.farmos.core.design.FarmSelectionAtoms
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.design.FarmSelectorSearch
import com.farmos.core.design.NoFarmSelectorSearch

/**
 * Whole-farm animal searches for one species' operations (owner decision D-004): an animal is chosen from
 * every record on this device, never typed as an id or picked from a capped list. [selectedId] and
 * [selectedLabel] name the animal already chosen on the herd screen, which pickers start from.
 */
data class OpsAnimalSearch(
    val all: FarmSelectorSearch,
    val females: FarmSelectorSearch,
    val males: FarmSelectorSearch,
    val selectedId: String? = null,
    val selectedLabel: String? = null,
    /** Every group of this species on the farm, for lot and mob captures. */
    val groups: List<FarmSelectorOption> = emptyList(),
    /** Offspring inbreeding from recorded pedigree (D-023); null where the host provides none. */
    val mateCoi: MateCoiAnalysis? = null,
) {
    companion object {
        val None = OpsAnimalSearch(NoFarmSelectorSearch, NoFarmSelectorSearch, NoFarmSelectorSearch)
    }
}

/** The species searches the hosting module provides to its operations screens. */
val LocalOpsAnimalSearch = staticCompositionLocalOf { OpsAnimalSearch.None }

enum class OpsAnimalFilter { ANY, FEMALE, MALE }

private val NO_ANIMAL = FarmSelectorOption("no-animal", "No individual animal")

/**
 * Chooses one animal of this farm and species by search-as-you-type (FOS-ATOM-004; FOS-ATOM-008 for a
 * parent). [animalId] is the chosen id, empty when none; [optional] adds an explicit "no animal" choice.
 */
@Composable
internal fun OpsAnimalPicker(
    title: String,
    animalId: String,
    filter: OpsAnimalFilter,
    busy: Boolean,
    parent: Boolean = false,
    optional: Boolean = false,
    onChange: (String) -> Unit,
) {
    val search = LocalOpsAnimalSearch.current
    var picked by remember { mutableStateOf<FarmSelectorOption?>(null) }
    // The option shown follows the chosen id; a picked option keeps its label while it is still the choice.
    val shown = when {
        animalId.isBlank() -> if (optional) NO_ANIMAL else null
        picked?.id == animalId -> picked
        else -> FarmSelectorOption(animalId, if (animalId == search.selectedId) search.selectedLabel ?: "Selected animal" else "Selected animal")
    }
    FarmSearchSelector(
        atomTag = if (parent) FarmSelectionAtoms.SIRE_DAM_SELECTOR else FarmSelectionAtoms.ANIMAL_SELECTOR,
        title = title,
        search = when (filter) {
            OpsAnimalFilter.ANY -> search.all
            OpsAnimalFilter.FEMALE -> search.females
            OpsAnimalFilter.MALE -> search.males
        },
        selected = shown,
        onSelect = { option ->
            picked = option
            onChange(if (option.id == NO_ANIMAL.id) "" else option.id)
        },
        emptyText = "No animals match on this device",
        enabled = !busy,
        pinned = if (optional) listOf(NO_ANIMAL) else emptyList(),
    )
}

/** Chooses one of this species' groups (FOS-ATOM-005) from every group on the farm. */
@Composable
internal fun OpsGroupPicker(title: String, groupId: String, busy: Boolean, onChange: (String) -> Unit) {
    FarmEntitySelector(
        FarmSelectionAtoms.GROUP_SELECTOR,
        title,
        LocalOpsAnimalSearch.current.groups,
        groupId.ifBlank { null },
        onChange,
        "Create a group in Groups first.",
        enabled = !busy,
    )
}
