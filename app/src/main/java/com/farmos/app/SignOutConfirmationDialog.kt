package com.farmos.app

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** FOS-GLOBAL-020 — destructive session-exit confirmation. */
@Composable
fun SignOutConfirmationDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
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
