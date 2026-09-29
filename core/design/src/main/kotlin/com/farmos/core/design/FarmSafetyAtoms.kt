package com.farmos.core.design

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** P12 safety review atoms; the `farm-atom:` tag names the canonical atom Screen ID realised. */
object FarmSafetyAtoms {
    const val IRREVERSIBLE_STATUS_CONFIRMATION = "farm-atom:FOS-ATOM-026"
}

/**
 * FOS-ATOM-026 — confirmation before a status change that cannot be undone from this surface.
 * It names the change and its consequence; confirming only calls [onConfirm], which must route
 * through the owning governed command. Cancel changes nothing. It never authorises on its own.
 */
@Composable
fun FarmIrreversibleConfirmation(
    title: String,
    consequence: String,
    confirmLabel: String,
    busy: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    confirmTag: String? = null,
) {
    AnimalFarmWarningSurface(modifier.testTag(FarmSafetyAtoms.IRREVERSIBLE_STATUS_CONFIRMATION)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(consequence)
        Button(
            onClick = onConfirm,
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                .let { if (confirmTag != null) it.testTag(confirmTag) else it },
        ) { Text(confirmLabel) }
        TextButton(onClick = onCancel, enabled = !busy, modifier = Modifier.heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)) { Text("Cancel") }
    }
}
