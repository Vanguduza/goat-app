package com.farmos.feature.ops

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
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
) {
    FarmOperationalPage(
        screenId = screenId,
        title = title,
        subtitle = help,
        visualClass = visualClass,
        onBack = onBack,
    ) {
        FarmOperationalRows(
            rows = rows,
            emptyTitle = empty,
            emptyHint = "Saved farm records for this area will appear here.",
        )
        FarmOperationalSection("Record") {
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
            Button(onClick = onSubmit, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(actionLabel)
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
