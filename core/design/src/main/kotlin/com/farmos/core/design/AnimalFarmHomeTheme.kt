package com.farmos.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun HomeThemeButton(modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    AnimalFarmRoundIcon("settings-2", "Theme", { open = true }, modifier)
    if (open) AnimalFarmSelectionSheet("Theme", { open = false }) {
        AnimalFarmThemeSelectionPanel(AnimalFarmTheme.mode, LocalAnimalFarmThemeChange.current)
        FarmPrimaryButton({ open = false }, Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("Done") }
    }
}

/** Theme only. Outdoor remains a native accessibility variant; it does not change roles or authority. */
@Composable
fun AnimalFarmThemeSelectionPanel(selected: AnimalFarmThemeMode, onModeChange: (AnimalFarmThemeMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ThemeOption("Light", AnimalFarmThemeMode.LIGHT, selected, onModeChange, Modifier.weight(1f))
            ThemeOption("Dark", AnimalFarmThemeMode.DARK, selected, onModeChange, Modifier.weight(1f))
        }
        ThemeOption("Outdoor", AnimalFarmThemeMode.OUTDOOR, selected, onModeChange, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ThemeOption(label: String, mode: AnimalFarmThemeMode, selected: AnimalFarmThemeMode,
    onModeChange: (AnimalFarmThemeMode) -> Unit, modifier: Modifier) {
    val active = mode == selected
    Surface(modifier.heightIn(min = AnimalFarmTheme.minimumTouchDp.dp).semantics { this.selected = active }
        .clickable(role = Role.RadioButton, onClick = { onModeChange(mode) }), shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (active) AnimalFarmTheme.colors.primary else AnimalFarmTheme.colors.divider),
        color = if (active) AnimalFarmTheme.colors.primary else AnimalFarmTheme.colors.background,
        contentColor = if (active) AnimalFarmTheme.colors.onPrimary else AnimalFarmTheme.colors.ink) {
        Text(label, Modifier.padding(12.dp), style = AnimalFarmHtmlType.action)
    }
}
