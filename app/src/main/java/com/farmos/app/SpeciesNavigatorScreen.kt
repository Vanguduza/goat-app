package com.farmos.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.FarmIllustratedSectionSurface
import com.farmos.core.design.FarmPastoralBackdrop
import com.farmos.core.design.FarmStorySurface
import com.farmos.core.design.FosDimens

private data class SpeciesEntrance(
    val module: FarmModule,
    val name: String,
    val story: String,
    val biology: String,
)

/** FOS-HOME-002 — species-native entrance; never a generic Animals CRUD list. */
@Composable
fun SpeciesNavigatorScreen(
    onOpen: (FarmModule) -> Unit,
    onBack: () -> Unit,
) {
    val species = listOf(
        SpeciesEntrance(FarmModule.GOAT, "Goats", "Paddocks, browsing herds and kidding seasons", "Herd · growth · health · reproduction"),
        SpeciesEntrance(FarmModule.RABBIT, "Rabbits", "Rabbitry, cages, nests and breeding waves", "Does · bucks · litters · kits"),
        SpeciesEntrance(FarmModule.SHEEP, "Sheep", "Pasture mobs, lambing and wool production", "Flocks · joining · lambing · wool"),
        SpeciesEntrance(FarmModule.CATTLE, "Cattle", "Herd, dairy, beef and feedlot systems", "Breeding · calving · milk · growth"),
        SpeciesEntrance(FarmModule.POULTRY, "Poultry", "Flocks, houses, brooding and hatchery", "House-first · production · biosecurity"),
    )
    FarmPastoralBackdrop(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(FosDimens.IntraCardGap),
        ) {
            FarmStorySurface(Modifier.fillMaxWidth()) {
                Text("Animals", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Choose the biology you are working with. Farm OS does not flatten different species into one generic animal workflow.")
            }
            species.forEach { entry ->
                FarmIllustratedSectionSurface(Modifier.clickable { onOpen(entry.module) }) {
                    Text(entry.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(entry.story, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(entry.biology, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            TextButton(onClick = onBack) { Text("Back to farm home") }
        }
    }
}
