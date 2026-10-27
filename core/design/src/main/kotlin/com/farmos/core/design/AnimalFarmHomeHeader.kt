package com.farmos.core.design

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Owner override,26October2026: farm identity + Command center; no date or Farm pulse headline. */
@Composable
fun AnimalFarmHomeHeader(farmName: String?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = animalFarmHomeHeaderInset(), vertical = 18.dp)
        .testTag("home-identity-header"), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(farmName?.takeIf { it.isNotBlank() } ?: "Animal Farm", style = AnimalFarmHtmlType.pageTitle,
            modifier = Modifier.testTag("home-farm-name").semantics { heading() })
        Text("Command center", style = AnimalFarmHtmlType.body, color = AnimalFarmTheme.colors.mutedInk)
    }
}

@Composable
fun AnimalFarmWorkSummaryBanner(due: String, open: String, onRecord: () -> Unit) {
    androidx.compose.material3.Surface(Modifier.fillMaxWidth().testTag("home-workday-banner"),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color = AnimalFarmTheme.colors.primary, contentColor = AnimalFarmTheme.colors.onPrimary) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("My work", style = AnimalFarmHtmlType.sectionTitle)
                Text("$due · $open", style = AnimalFarmHtmlType.caption)
            }
            AnimalFarmRoundIcon("plus", "Add a work update", onRecord, lime = true)
        }
    }
}
