package com.farmos.core.design

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.farmos.core.design.FarmOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** P13 date and time atoms; the `farm-atom:` tag names the canonical atom Screen ID realised. */
object FarmDateAtoms {
    const val DATE_PICKER = "farm-atom:FOS-ATOM-001"
}

/**
 * FOS-ATOM-001 — a calendar date field. The value stays an ISO `YYYY-MM-DD` string the owning
 * capture already validates, so typed entry keeps working offline and in the field; "Choose date"
 * opens an adaptive date selector that writes the same ISO string. The selector starts on the typed date when it
 * parses, otherwise on [today]. A narrow or enlarged-text dialog uses native date input rather than a clipped seven-column calendar. Choosing a date never submits anything by itself. Each field is
 * tagged `farm-atom:FOS-ATOM-001:<key>` so several dates on one capture stay distinguishable.
 */
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
            IconButton(
                onClick = { open = true },
                enabled = enabled,
                modifier = Modifier.size(AnimalFarmTheme.minimumTouchDp.dp).testTag("$fieldTag:open"),
            ) { Icon(Icons.Rounded.CalendarMonth, contentDescription = "Choose date for $label") }
        },
        modifier = modifier.fillMaxWidth().testTag(fieldTag),
    )
    if (open) {
        val initial = runCatching { LocalDate.parse(value) }.getOrDefault(today)
        FarmDateSelectionDialog(
            label = label,
            initialDate = initial,
            fieldTag = fieldTag,
            onDismiss = { open = false },
            onConfirm = { selected -> onValueChange(selected.toString()); open = false },
        )
    }
}
