@file:Suppress("ktlint:standard:function-naming")

package com.farmos.core.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Home-only settings affordance. It intentionally exposes Theme and nothing else. */
@Composable
fun HomeThemeButton(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val mode = AnimalFarmTheme.mode
    val onModeChange = AnimalFarmTheme.onModeChange
    val target = AnimalFarmTheme.minimumTouchDp.dp

    IconButton(
        onClick = { open = true },
        modifier = modifier.size(target),
    ) {
        Icon(Icons.Outlined.Settings, contentDescription = "Theme")
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Theme") },
            text = {
                Column {
                    ThemeChoice("Light", AnimalFarmThemeMode.LIGHT, mode, onModeChange)
                    ThemeChoice("Dark", AnimalFarmThemeMode.DARK, mode, onModeChange)
                    ThemeChoice("Outdoor", AnimalFarmThemeMode.OUTDOOR, mode, onModeChange)
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text("Done") }
            },
        )
    }
}

@Composable
private fun ThemeChoice(
    label: String,
    value: AnimalFarmThemeMode,
    selected: AnimalFarmThemeMode,
    onModeChange: (AnimalFarmThemeMode) -> Unit,
) {
    val suffix = if (value.requiresNativeAcceptance) " · native candidate" else ""
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.RadioButton) { onModeChange(value) }
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected == value, onClick = { onModeChange(value) })
        Text("$label$suffix", modifier = Modifier.padding(start = 8.dp))
    }
}
