package com.farmos.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Presentation intents only. The owning module retains all route and command permissions. */
data class FarmToolAction(
    val label: String,
    val onClick: () -> Unit,
    val detail: String? = null,
    val enabled: Boolean = true,
    val tag: String? = null,
)

data class FarmToolGroup(val label: String, val actions: List<FarmToolAction>)

/** P04/P14: tools have a compact, searchable home instead of a second dashboard of feature cards. */
@Composable
fun FarmModuleTools(
    groups: List<FarmToolGroup>,
    modifier: Modifier = Modifier,
    title: String = "Tools",
    initialGroup: String? = null,
) {
    var expanded by rememberSaveable { mutableStateOf(initialGroup) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val visibleGroups = groups.filter { it.actions.isNotEmpty() }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AnimalFarmHomeMetrics.tileGap)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = AnimalFarmHtmlType.sectionTitle)
            if (visibleGroups.sumOf { it.actions.size } > 6) FarmActionButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(if (searching) "Close" else "Find tool", Modifier.padding(start = 8.dp))
            }
        }
        if (searching) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Find a tool") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("farm-tools-search"),
                shape = RoundedCornerShape(AnimalFarmHomeMetrics.fieldRadius),
            )
        }
        val search = query.trim()
        if (search.isNotEmpty()) {
            val matches = visibleGroups.flatMap { it.actions }.distinctBy { it.tag ?: it.label }
                .filter { it.label.contains(search, ignoreCase = true) || it.detail?.contains(search, ignoreCase = true) == true }
            if (matches.isEmpty()) Text("No matching tools", color = AnimalFarmTheme.colors.mutedInk)
            else FarmIllustratedSectionSurface {
                matches.forEachIndexed { index, action ->
                    if (index > 0) HorizontalDivider(color = AnimalFarmTheme.colors.divider)
                    FarmToolRow(action)
                }
            }
        } else {
            visibleGroups.forEach { group ->
                val isExpanded = expanded == group.label
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AnimalFarmHomeMetrics.resourceRadius),
                    color = AnimalFarmTheme.colors.surface,
                    border = if (AnimalFarmTheme.mode == AnimalFarmThemeMode.OUTDOOR) BorderStroke(FosDimens.Hairline, AnimalFarmTheme.colors.divider) else null,
                ) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth()
                                .heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                                .clickable(role = Role.Button) { expanded = if (isExpanded) null else group.label }
                                .semantics { stateDescription = if (isExpanded) "Expanded" else "Collapsed" }
                                .testTag("farm-tools-group:" + group.label)
                                .padding(AnimalFarmHomeMetrics.rowPadding),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(group.label, Modifier.weight(1f), style = AnimalFarmHtmlType.sectionTitle)
                            Text(group.actions.size.toString(), style = AnimalFarmHtmlType.caption, color = AnimalFarmTheme.colors.mutedInk)
                            Icon(
                                if (isExpanded) Icons.Rounded.ExpandMore else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        if (isExpanded) {
                            HorizontalDivider(color = AnimalFarmTheme.colors.divider)
                            Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                group.actions.forEachIndexed { index, action ->
                                    if (index > 0) HorizontalDivider(color = AnimalFarmTheme.colors.divider)
                                    FarmToolRow(action)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FarmToolRow(action: FarmToolAction, modifier: Modifier = Modifier) {
    val colors = AnimalFarmTheme.colors
    Row(
        modifier.fillMaxWidth()
            .heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
            .clickable(enabled = action.enabled, role = Role.Button, onClick = action.onClick)
            .testTag(action.tag ?: ("farm-tool:" + action.label))
            .padding(horizontal = 12.dp, vertical = AnimalFarmHomeMetrics.rowPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(action.label, style = AnimalFarmHtmlType.action, color = if (action.enabled) colors.ink else colors.mutedInk)
            action.detail?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = AnimalFarmHtmlType.caption, color = colors.mutedInk)
            }
        }
        FarmHtmlIcon("arrow-up-right", null, Modifier.size(AnimalFarmHomeMetrics.lineIconSize).align(Alignment.CenterVertically), tint = colors.mutedInk)
    }
}
