package com.farmos.app

import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.core.view.WindowCompat
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.AnimalFarmThemeMode

/** One root inset owner for every route, including onboarding. Children consume only remaining insets. */
@Composable
internal fun FarmAppViewport(window: Window? = null, insets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable () -> Unit) {
    val mode = AnimalFarmTheme.mode
    SideEffect {
        window?.let {
            val controller = WindowCompat.getInsetsController(it, it.decorView)
            controller.isAppearanceLightStatusBars = mode != AnimalFarmThemeMode.DARK
            controller.isAppearanceLightNavigationBars = mode != AnimalFarmThemeMode.DARK
        }
    }
    Box(Modifier.fillMaxSize().background(AnimalFarmTheme.colors.background)
        .windowInsetsPadding(insets).testTag("farm-app-safe-area")) { content() }
}
