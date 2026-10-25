package com.farmos.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp

/** HTML stage-lineage for alternate page views, not a new domain workflow or filter authority. */
@Composable
fun FarmSectionTabs(labels: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier,
    tagPrefix: String = "farm-section:") {
    require(labels.distinct().size == labels.size) { "Each section must have one identity" }
    if (labels.isEmpty()) return
    BoxWithConstraints(modifier.fillMaxWidth().selectableGroup()) {
        val scale = LocalDensity.current.fontScale
        val longest = labels.maxOf { it.length }
        val columns = when {
            scale >= 1.5f && maxWidth < 600.dp -> 1
            scale >= 1.5f -> 2
            maxWidth >= 600.dp -> 4
            longest > 15 -> 2
            longest > 9 -> 3
            else -> 4
        }.coerceAtMost(labels.size)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { label ->
                        val active = label == selected
                        Column(Modifier.weight(1f).fillMaxHeight().heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                            .testTag(tagPrefix + label).selectable(selected = active, role = Role.Tab, onClick = { onSelect(label) }),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                                Text(label, style = AnimalFarmHtmlType.subject,
                                    color = if (active) AnimalFarmTheme.colors.primary else AnimalFarmTheme.colors.mutedInk)
                            }
                            Box(Modifier.fillMaxWidth().height(if (active) 3.dp else 1.dp)
                                .background(if (active) AnimalFarmTheme.colors.primary else AnimalFarmTheme.colors.divider))
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
