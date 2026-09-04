package com.farmos.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.farmos.core.design.FarmOsAccentMedium
import com.farmos.core.design.FarmOsWordmark
import com.farmos.core.design.FarmPastoralBackdrop
import com.farmos.core.design.FarmStorySurface

/** FOS-GLOBAL-001 — canonical illustrated Farm OS entrance. */
@Composable
fun FarmOsSplashScreen() {
    FarmPastoralBackdrop(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            FarmStorySurface(Modifier.fillMaxWidth()) {
                FarmOsWordmark()
                Text(
                    "Animals. Land. People. A Better Tomorrow.",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Real Farms. Brighter Futures.",
                    style = FarmOsAccentMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("Plan · Monitor · Grow · Sustain", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
