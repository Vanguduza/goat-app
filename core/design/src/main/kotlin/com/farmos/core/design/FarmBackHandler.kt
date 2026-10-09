package com.farmos.core.design

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable

/**
 * Gives Android Back the same return action as the visible page.
 *
 * Call before rendering child pages so the deepest enabled owner handles Back first.
 * A null action leaves the enclosing owner in control; context-free previews have no
 * Android dispatcher to register with. BackHandler keeps the latest callback.
 */
@Composable
fun FarmBackHandler(onBack: (() -> Unit)?) {
    if (LocalOnBackPressedDispatcherOwner.current != null) {
        BackHandler(enabled = onBack != null) { onBack?.invoke() }
    }
}
