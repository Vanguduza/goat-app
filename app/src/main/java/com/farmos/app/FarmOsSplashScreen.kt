package com.farmos.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.FarmAnimalLineup

/** FOS-GLOBAL-001 — canonical Animal Farm entrance. */
@Composable
fun FarmOsSplashScreen() {
    val colors = AnimalFarmTheme.colors
    Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Animal Farm",
                color = colors.ink,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.headlineMedium,
            )
            FarmAnimalLineup(Modifier.fillMaxWidth().padding(top = 16.dp))
        }
    }
}
