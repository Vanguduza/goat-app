package com.farmos.core.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One selectable farm record: its id is what the owning command receives, never the label. */
data class FarmSelectorOption(val id: String, val label: String, val detail: String? = null)

/** P13 selection atoms; each tag names the canonical atom Screen ID the selector realises. */
object FarmSelectionAtoms {
    const val INVENTORY_ITEM_SELECTOR = "farm-atom:FOS-ATOM-009"
    const val SUPPLIER_SELECTOR = "farm-atom:FOS-ATOM-011"
    const val GROUP_SELECTOR = "farm-atom:FOS-ATOM-005"
    const val LOCATION_SELECTOR = "farm-atom:FOS-ATOM-007"
    const val ASSET_SELECTOR = "farm-atom:FOS-ATOM-014"
    const val SPECIES_SELECTOR = "farm-atom:FOS-ATOM-003"
    const val FARM_SELECTOR = "farm-atom:FOS-ATOM-006"
    const val SIRE_DAM_SELECTOR = "farm-atom:FOS-ATOM-008"
    const val ANIMAL_SELECTOR = "farm-atom:FOS-ATOM-004"
    const val CUSTOMER_SELECTOR = "farm-atom:FOS-ATOM-012"
    const val ATTACHMENT_PICKER = "farm-atom:FOS-ATOM-016"
}

/**
 * Entity selector atom. Lists this farm's existing records and returns only the selected record id,
 * so a capture can never reference a mistyped or cross-farm id. Options are tagged
 * `<atomTag>:option:<id>` and expose single-choice selection semantics.
 */
@Composable
fun FarmEntitySelector(
    atomTag: String,
    title: String,
    options: List<FarmSelectorOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    emptyText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier.fillMaxWidth().testTag(atomTag), verticalArrangement = Arrangement.spacedBy(FosDimens.Grid)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (options.isEmpty()) {
            Text(emptyText, color = AnimalFarmTheme.colors.mutedInk)
            return@Column
        }
        options.forEach { option ->
            FarmSelectorOptionRow(atomTag, option, option.id == selectedId, enabled) { onSelect(option.id) }
        }
    }
}

/** One single-choice option row, tagged `<atomTag>:option:<id>`. Shared by every selector atom. */
@Composable
internal fun FarmSelectorOptionRow(atomTag: String, option: FarmSelectorOption, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = FosDimens.Grid)
            .testTag("$atomTag:option:${option.id}"),
    ) {
        Text(
            option.label + if (selected) " · selected" else "",
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        option.detail?.let { Text(it, color = AnimalFarmTheme.colors.mutedInk, style = MaterialTheme.typography.bodySmall) }
    }
}
