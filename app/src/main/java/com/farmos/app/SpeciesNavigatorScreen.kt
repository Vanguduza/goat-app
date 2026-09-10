package com.farmos.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmCanvas
import com.farmos.core.design.AnimalFarmContextHeader
import com.farmos.core.design.AnimalFarmFamily
import com.farmos.core.design.AnimalFarmFamilyLauncher
import com.farmos.core.design.AnimalFarmHomeMetrics
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** FOS-HOME-002 — species-native entrance; never a generic Animals CRUD list. */
@Composable
fun SpeciesNavigatorScreen(
    onOpen: (FarmModule) -> Unit,
    onBack: () -> Unit,
) {
    val families = listOf(
        Triple(AnimalFarmFamily.GOAT, FarmModule.GOAT, "Herd, growth, kidding"),
        Triple(AnimalFarmFamily.RABBIT, FarmModule.RABBIT, "Waves, cages, nests"),
        Triple(AnimalFarmFamily.SHEEP, FarmModule.SHEEP, "Flock, joining, wool"),
        Triple(AnimalFarmFamily.CATTLE, FarmModule.CATTLE, "Herd, calving, milk"),
        Triple(AnimalFarmFamily.POULTRY, FarmModule.POULTRY, "House, flock, hatch"),
    )
    AnimalFarmCanvas {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AnimalFarmContextHeader(
                farmName = null,
                dateLabel = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)),
                title = "Choose a species",
                modifier = Modifier.padding(top = 16.dp),
            )
            Column(
                Modifier.padding(horizontal = AnimalFarmHomeMetrics.pageInset),
                verticalArrangement = Arrangement.spacedBy(AnimalFarmHomeMetrics.moduleGap),
            ) {
                families.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AnimalFarmHomeMetrics.moduleGap)) {
                        pair.forEach { (family, module, subtitle) ->
                            AnimalFarmFamilyLauncher(family, subtitle, { onOpen(module) }, Modifier.weight(1f))
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                TextButton(onClick = onBack) { Text("Farm home") }
            }
        }
    }
}
