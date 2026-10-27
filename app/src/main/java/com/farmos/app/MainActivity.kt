package com.farmos.app

import androidx.activity.enableEdgeToEdge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as FarmOsApplication

        setContent {
            var savedThemeMode by remember { mutableStateOf(app.savedThemeMode()) }
            val systemThemeMode = if (isSystemInDarkTheme()) AnimalFarmThemeMode.DARK else AnimalFarmThemeMode.LIGHT
            val themeMode = savedThemeMode ?: systemThemeMode
            FarmOsTheme(
                mode = themeMode,
                onModeChange = { selected ->
                    savedThemeMode = selected
                    app.saveThemeMode(selected)
                },
            ) {
                FarmAppViewport(window) {
                    FarmRootContent(app)
                }
            }
        }
    }
}
