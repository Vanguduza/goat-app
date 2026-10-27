package com.farmos.app

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import com.farmos.core.design.FarmActionButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** FOS-GLOBAL-020 — destructive session-exit confirmation. */
@Composable
fun SignOutConfirmationDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        modifier = Modifier.testTag("farm-screen:FOS-GLOBAL-020"),
        onDismissRequest = onDismiss,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(com.farmos.core.design.AnimalFarmHomeMetrics.tileRadius),
        containerColor = com.farmos.core.design.AnimalFarmTheme.colors.surface,
        titleContentColor = com.farmos.core.design.AnimalFarmTheme.colors.ink,
        textContentColor = com.farmos.core.design.AnimalFarmTheme.colors.mutedInk,
        tonalElevation = androidx.compose.ui.unit.Dp(0f),
        title = { Text("Sign out?") },
        text = { Text("Locally saved farm records stay on this device. Pending synchronization will resume after you sign in again.") },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Stay signed in") }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Sign out") }
        },
    )
}
