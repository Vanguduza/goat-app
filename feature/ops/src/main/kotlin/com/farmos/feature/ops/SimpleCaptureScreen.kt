package com.farmos.feature.ops

import androidx.compose.foundation.layout.fillMaxWidth
import com.farmos.core.design.FarmPrimaryButton as Button
import androidx.compose.material3.MaterialTheme
import com.farmos.core.design.FarmOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import com.farmos.core.design.FarmOfflineSaveReceipt
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalRows
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmVisualClass

/** Generic plumbing only. Every call must provide a canonical Screen ID and module-specific copy. */
@Composable
fun SimpleCaptureScreen(
    screenId: String,
    title: String,
    help: String,
    empty: String,
    rows: List<String>,
    busy: Boolean,
    error: String?,
    fields: List<Pair<String, MutableState<String>>>,
    actionLabel: String,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    visualClass: FarmVisualClass = FarmVisualClass.I3,
    extra: @Composable () -> Unit = {},
    /** True after the latest capture committed locally; shows the FOS-ATOM-034 receipt. */
    saved: Boolean = false,
    overview: @Composable () -> Unit = {},
    navigation: @Composable () -> Unit = {},
    captureVisible: Boolean = true,
    recordsVisible: Boolean = true,
    recordTitle: String = actionLabel,
    showPrimaryAction: Boolean = true,
) {
    FarmOperationalPage(
        screenId = screenId,
        title = title,
        subtitle = help,
        visualClass = visualClass,
        onBack = onBack,
    ) {
        overview()
        navigation()
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (captureVisible) FarmOperationalSection(recordTitle) {
            extra()
            fields.forEach { (label, state) ->
                OutlinedTextField(
                    value = state.value,
                    onValueChange = { state.value = it },
                    label = { Text(label) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    singleLine = true,
                )
            }
            if (showPrimaryAction) Button(onClick = onSubmit, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(actionLabel)
            }
            if (saved && !busy) FarmOfflineSaveReceipt()
        }
        if (recordsVisible) FarmOperationalRows(rows = rows, emptyTitle = empty)
    }
}
