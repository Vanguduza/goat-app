package com.farmos.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.farmos.core.design.FarmOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One selectable farm record: its id is what the owning command receives, never the label. */
data class FarmSelectorOption(val id: String, val label: String, val detail: String? = null)

/** P13 selection atoms; each tag names the canonical atom Screen ID the selector realises. */
object FarmSelectionAtoms {
    const val WORKER_SELECTOR = "farm-atom:FOS-ATOM-013"
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

/** Selection over the caller's exact option set. A query filters before presentation is capped. */
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
    var query by rememberSaveable(atomTag) { mutableStateOf("") }
    Column(modifier.fillMaxWidth().testTag(atomTag), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (options.isEmpty()) {
            Text(emptyText, color = AnimalFarmTheme.colors.mutedInk)
            return@Column
        }
        if (options.size > 6) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, label = { Text("Find " + title.lowercase()) },
                modifier = Modifier.fillMaxWidth().testTag("$atomTag:search"), enabled = enabled, singleLine = true,
                shape = RoundedCornerShape(AnimalFarmHomeMetrics.fieldRadius),
            )
        }
        val needle = if (options.size > 6) query.trim() else ""
        val matches = options.filter {
            needle.isEmpty() || it.label.contains(needle, ignoreCase = true) || it.detail?.contains(needle, ignoreCase = true) == true
        }
        val shown = matches.sortedBy { if (it.id == selectedId) 0 else 1 }.take(50)
        if (shown.isEmpty()) Text("No matching " + title.lowercase(), color = AnimalFarmTheme.colors.mutedInk)
        shown.forEach { option ->
            FarmSelectorOptionRow(atomTag, option, option.id == selectedId, enabled) { onSelect(option.id) }
        }
        if (shown.size < matches.size) Text("Showing " + shown.size + " of " + matches.size + ". Refine your search.", style = MaterialTheme.typography.bodySmall, color = AnimalFarmTheme.colors.mutedInk)
    }
}

/** Visible single-choice control, shared by the fixed and asynchronous searchable selectors. */
@Composable
internal fun FarmSelectorOptionRow(atomTag: String, option: FarmSelectorOption, selected: Boolean, enabled: Boolean, optionTag: String = atomTag + ":option:" + option.id, onSelect: () -> Unit) {
    val colors = AnimalFarmTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AnimalFarmHomeMetrics.secondaryActionRadius),
        color = if (selected) colors.lime else colors.surface,
        contentColor = if (selected) colors.onLime else colors.ink,
        border = BorderStroke(FosDimens.Hairline, colors.divider),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
                .padding(AnimalFarmHomeMetrics.rowPadding).testTag(optionTag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(option.label + if (selected) " · selected" else "", style = AnimalFarmHtmlType.action, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
                option.detail?.let { Text(it, color = if (selected) colors.onLime else colors.mutedInk, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
