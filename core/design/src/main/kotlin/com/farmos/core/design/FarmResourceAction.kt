package com.farmos.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** P04/P05/P14: the HTML resource row, with the owning route/command and identity left to its caller. */
@Composable
fun FarmResourceAction(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, detail: String? = null,
    enabled: Boolean = true, family: AnimalFarmFamily? = null, trailing: (@Composable () -> Unit)? = null) {
    val colors = AnimalFarmTheme.colors
    val shape = RoundedCornerShape(AnimalFarmHomeMetrics.resourceRadius)
    Surface(modifier.fillMaxWidth().clip(shape).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = shape, color = colors.surface, contentColor = if (enabled) colors.ink else colors.mutedInk,
        border = if (AnimalFarmTheme.mode == AnimalFarmThemeMode.OUTDOOR) BorderStroke(FosDimens.Hairline, colors.divider) else null) {
        Row(Modifier.heightIn(min = AnimalFarmTheme.minimumTouchDp.dp).padding(AnimalFarmHomeMetrics.rowPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (family != null) AnimalFarmFamilyPortrait(family, Modifier.size(AnimalFarmHomeMetrics.identityThumbnail),
                contentDescription = "${animalFarmFamilyLabel(family)} family illustration; not an individual photograph")
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = AnimalFarmHtmlType.action)
                detail?.takeIf { it.isNotBlank() }?.let { Text(it, style = AnimalFarmHtmlType.caption, color = colors.mutedInk) }
            }
            if (trailing != null) trailing() else FarmHtmlIcon("arrow-up-right", null, Modifier.size(18.dp))
        }
    }
}
