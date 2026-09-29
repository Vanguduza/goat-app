package com.farmos.core.design

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** P13 date and time atoms; the `farm-atom:` tag names the canonical atom Screen ID realised. */
object FarmDateAtoms {
    const val DATE_PICKER = "farm-atom:FOS-ATOM-001"
}

/**
 * FOS-ATOM-001 — a calendar date field. The value stays an ISO `YYYY-MM-DD` string the owning
 * capture already validates, so typed entry keeps working offline and in the field; "Choose date"
 * opens a calendar that writes the same ISO string. The calendar starts on the typed date when it
 * parses, otherwise on [today]. Choosing a date never submits anything by itself. Each field is
 * tagged `farm-atom:FOS-ATOM-001:<key>` so several dates on one capture stay distinguishable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmDateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    today: LocalDate = LocalDate.now(),
    key: String = label,
) {
    val fieldTag = "${FarmDateAtoms.DATE_PICKER}:$key"
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text("YYYY-MM-DD") },
        singleLine = true,
        enabled = enabled,
        trailingIcon = {
            TextButton(
                onClick = { open = true },
                enabled = enabled,
                modifier = Modifier.heightIn(min = AnimalFarmTheme.minimumTouchDp.dp).testTag("$fieldTag:open"),
            ) { Text("Choose date") }
        },
        modifier = modifier.fillMaxWidth().testTag(fieldTag),
    )
    if (open) {
        val initial = runCatching { LocalDate.parse(value) }.getOrDefault(today)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            onValueChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                        }
                        open = false
                    },
                    modifier = Modifier.testTag("$fieldTag:confirm"),
                ) { Text("Use date") }
            },
            dismissButton = {
                TextButton(onClick = { open = false }, modifier = Modifier.testTag("$fieldTag:cancel")) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state, modifier = Modifier.testTag("$fieldTag:calendar"))
        }
    }
}
