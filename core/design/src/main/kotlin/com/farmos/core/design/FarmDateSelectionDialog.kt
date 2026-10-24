package com.farmos.core.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** FOS-ATOM-001, P13. A native picker inside the shared visual surface; no record is submitted here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FarmDateSelectionDialog(
    label: String,
    initialDate: LocalDate,
    fieldTag: String,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val inheritedDensity = LocalDensity.current
    val configuration = LocalConfiguration.current
    val colors = AnimalFarmTheme.colors
    val calendarWidth = AnimalFarmTheme.minimumTouchDp.dp * 7 + FosDimens.CardPadding * 4
    val availableWidth = configuration.screenWidthDp.dp - FosDimens.ScreenMargin * 2
    val compact = availableWidth < calendarWidth || inheritedDensity.fontScale > 1.3f
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        initialDisplayMode = if (compact) DisplayMode.Input else DisplayMode.Picker,
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Separate Android windows must retain this screen's effective text scale.
        CompositionLocalProvider(LocalDensity provides inheritedDensity) {
            Box(Modifier.fillMaxWidth().imePadding().padding(FosDimens.ScreenMargin), contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.widthIn(max = calendarWidth).fillMaxWidth()
                        .heightIn(max = (configuration.screenHeightDp.dp - FosDimens.ScreenMargin * 2).coerceAtLeast(128.dp)),
                    color = colors.surface, contentColor = colors.ink,
                    shape = RoundedCornerShape(AnimalFarmHomeMetrics.tileRadius),
                ) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(label, Modifier.padding(FosDimens.CardPadding), style = MaterialTheme.typography.titleLarge)
                        DatePicker(
                            state = state,
                            modifier = Modifier.fillMaxWidth().testTag("$fieldTag:calendar"),
                            title = null, headline = null, showModeToggle = !compact,
                            colors = DatePickerDefaults.colors(containerColor = colors.surface),
                        )
                        Column(Modifier.padding(FosDimens.CardPadding), verticalArrangement = Arrangement.spacedBy(FosDimens.IntraCardGap)) {
                            FarmPrimaryButton(
                                onClick = { state.selectedDateMillis?.let { onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } },
                                modifier = Modifier.fillMaxWidth().testTag("$fieldTag:confirm"),
                                enabled = state.selectedDateMillis != null,
                            ) { Text("Use date") }
                            FarmActionButton(onDismiss, Modifier.fillMaxWidth().testTag("$fieldTag:cancel")) { Text("Cancel") }
                        }
                    }
                }
            }
        }
    }
}
