package com.farmos.core.design

import androidx.compose.ui.graphics.Color

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Native modal behaviour with the approved HTML sheet geometry; no HTML business logic is embedded. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimalFarmSelectionSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = AnimalFarmHomeMetrics.sheetRadius, topEnd = AnimalFarmHomeMetrics.sheetRadius),
        containerColor = AnimalFarmTheme.colors.surface, contentColor = AnimalFarmTheme.colors.ink, tonalElevation = 0.dp,
        scrimColor = if (AnimalFarmTheme.mode == AnimalFarmThemeMode.DARK) Color(0xAA050B09) else Color(0x80102819),
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 12.dp).size(34.dp, 4.dp).background(AnimalFarmTheme.colors.divider, RoundedCornerShape(4.dp))) }) {
        Column(Modifier.fillMaxWidth().imePadding().testTag("home-sheet:$title").verticalScroll(rememberScrollState())
            .padding(start = animalFarmHomeHeaderInset(), end = animalFarmHomeHeaderInset(), bottom = 25.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, Modifier.weight(1f).semantics { heading() }, style = AnimalFarmHtmlType.sheetTitle)
                AnimalFarmRoundIcon("x", "Close $title", onDismiss)
            }
            content()
        }
    }
}
