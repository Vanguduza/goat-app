package com.farmos.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.FarmIllustratedSectionSurface
import com.farmos.core.design.FarmPastoralBackdrop
import com.farmos.core.design.FarmStorySurface
import com.farmos.core.design.FosDimens

enum class FarmModule {
    HOME, GOAT, RABBIT, SHEEP, CATTLE, POULTRY, TASKS, HEALTH, MONEY, INVENTORY,
    GROUPS, PASTURE, LABOUR, ASSETS, FEED, WATER, SALES, PROCUREMENT, WAITLIST
}

private data class HomeModuleCard(val module: FarmModule, val title: String, val description: String)

/** FOS-HOME-001 / FOS-HOME-002 — illustrated farm operating entrance. */
@Composable
fun FarmHomeScreen(
    farmName: String?,
    onOpen: (FarmModule) -> Unit,
    onSignOut: () -> Unit,
) {
    var showSpeciesNavigator by remember { mutableStateOf(false) }
    if (showSpeciesNavigator) {
        SpeciesNavigatorScreen(
            onOpen = onOpen,
            onBack = { showSpeciesNavigator = false },
        )
        return
    }

    val species = listOf(
        HomeModuleCard(FarmModule.GOAT, "Goats", "Herd, growth, health and reproduction"),
        HomeModuleCard(FarmModule.RABBIT, "Rabbits", "Rabbitry, breeding waves, litters and market plans"),
        HomeModuleCard(FarmModule.SHEEP, "Sheep", "Flocks, joining, lambing, health and wool"),
        HomeModuleCard(FarmModule.CATTLE, "Cattle", "Herds, breeding, dairy, beef and movement"),
        HomeModuleCard(FarmModule.POULTRY, "Poultry", "Flocks, houses, production, hatchery and biosecurity"),
    )
    val work = listOf(
        HomeModuleCard(FarmModule.TASKS, "Tasks", "Today, scheduled and assigned work"),
        HomeModuleCard(FarmModule.HEALTH, "Health", "Observations, treatment, protocols and withdrawals"),
        HomeModuleCard(FarmModule.FEED, "Feed", "Stock, plans, schedules and consumption"),
        HomeModuleCard(FarmModule.WATER, "Water", "Water points, inspections and issues"),
        HomeModuleCard(FarmModule.PASTURE, "Pasture", "Paddocks, rotations, condition and capacity"),
        HomeModuleCard(FarmModule.LABOUR, "Labour", "People, assignments and work coverage"),
        HomeModuleCard(FarmModule.ASSETS, "Assets", "Equipment, maintenance and breakdowns"),
    )
    val business = listOf(
        HomeModuleCard(FarmModule.INVENTORY, "Inventory", "Stock, lots, FEFO and reorder"),
        HomeModuleCard(FarmModule.SALES, "Sales", "Customers, animals, produce and orders"),
        HomeModuleCard(FarmModule.PROCUREMENT, "Procurement", "Suppliers, purchases and receiving"),
        HomeModuleCard(FarmModule.MONEY, "Finance", "Income, expenses and farm performance"),
    )

    FarmPastoralBackdrop(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(FosDimens.SectionGap),
        ) {
            FarmStorySurface(Modifier.fillMaxWidth()) {
                Text("Farm OS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    farmName ?: "Your farm",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text("Plan. Monitor. Grow. Sustain.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Open the part of the farm you are working on. Each species keeps its own biology and workflows.",
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            HomeSection("Animals", "Species-native farm management", species, onOpen)
            TextButton(onClick = { showSpeciesNavigator = true }) { Text("Explore all species") }
            HomeSection("Work", "Run the farm day", work, onOpen)
            HomeSection("Business", "Stock, buying, selling and money", business, onOpen)

            FarmIllustratedSectionSurface {
                Text("More farm operations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onOpen(FarmModule.GROUPS) }) { Text("Groups") }
                    TextButton(onClick = { onOpen(FarmModule.WAITLIST) }) { Text("Waitlist") }
                    TextButton(onClick = onSignOut) { Text("Sign out") }
                }
            }
        }
    }
}

@Composable
private fun HomeSection(
    title: String,
    subtitle: String,
    cards: List<HomeModuleCard>,
    onOpen: (FarmModule) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
        cards.forEach { card ->
            FarmIllustratedSectionSurface(
                Modifier.clickable { onOpen(card.module) },
            ) {
                Text(card.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(card.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
