package com.farmos.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.FarmIllustratedSectionSurface
import com.farmos.core.design.FarmOsAccentMedium
import com.farmos.core.design.FarmPastoralBackdrop
import com.farmos.core.design.FarmSpeciesVisual
import com.farmos.core.design.FarmStorySurface
import com.farmos.core.design.FosDimens

enum class FarmModule {
    HOME, GOAT, RABBIT, SHEEP, CATTLE, POULTRY, TASKS, HEALTH, MONEY, INVENTORY,
    GROUPS, PASTURE, LABOUR, ASSETS, FEED, WATER, SALES, PROCUREMENT, WAITLIST,
}

private data class HomeModuleCard(val module: FarmModule, val title: String, val description: String)
private data class SpeciesHomeCard(val module: FarmModule, val title: String, val visual: FarmSpeciesVisual)

/** FOS-HOME-001 — illustrated farm operating home derived from FOS-VREF-HOME-001. */
@Composable
fun FarmHomeScreen(
    farmName: String?,
    summary: FarmHomeSummary = FarmHomeSummary(),
    onOpen: (FarmModule) -> Unit,
    onSignOut: () -> Unit,
) {
    var destination by remember { mutableStateOf("home") }
    if (destination == "animals") {
        SpeciesNavigatorScreen(onOpen = onOpen, onBack = { destination = "home" })
        return
    }
    if (destination == "more") {
        FarmMoreScreen(onOpen = onOpen, onBack = { destination = "home" }, onSignOut = onSignOut)
        return
    }

    val species = listOf(
        SpeciesHomeCard(FarmModule.GOAT, "Goats", FarmSpeciesVisual.GOAT),
        SpeciesHomeCard(FarmModule.CATTLE, "Cattle", FarmSpeciesVisual.CATTLE),
        SpeciesHomeCard(FarmModule.SHEEP, "Sheep", FarmSpeciesVisual.SHEEP),
        SpeciesHomeCard(FarmModule.POULTRY, "Poultry", FarmSpeciesVisual.POULTRY),
        SpeciesHomeCard(FarmModule.RABBIT, "Rabbits", FarmSpeciesVisual.RABBIT),
    )

    FarmPastoralBackdrop(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(FosDimens.SectionGap),
            ) {
                FarmStorySurface(Modifier.fillMaxWidth()) {
                    Text("Farm OS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(
                        farmName ?: "Your farm",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("Real farms. Brighter futures.", style = FarmOsAccentMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Animals. Land. People. A Better Tomorrow.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                TodayStrip(summary = summary, onOpen = onOpen)

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Your Farm", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    species.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { card ->
                                SpeciesHomeTile(
                                    card = card,
                                    goatCount = summary.goatCount,
                                    modifier = Modifier.weight(1f),
                                    onOpen = onOpen,
                                )
                            }
                            if (pair.size == 1) Box(Modifier.weight(1f))
                        }
                    }
                }

                FarmIllustratedSectionSurface {
                    Text("Run the farm day", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Tasks, health, feed, water, pasture, labour and equipment stay farm-wide.")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { onOpen(FarmModule.TASKS) }) { Text("Tasks") }
                        TextButton(onClick = { onOpen(FarmModule.HEALTH) }) { Text("Health") }
                        TextButton(onClick = { onOpen(FarmModule.FEED) }) { Text("Feed") }
                    }
                }
            }

            FarmHomeBottomBar(
                onHome = { destination = "home" },
                onAnimals = { destination = "animals" },
                onTasks = { onOpen(FarmModule.TASKS) },
                onMore = { destination = "more" },
            )
        }
    }
}

@Composable
private fun TodayStrip(summary: FarmHomeSummary, onOpen: (FarmModule) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Today", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeMetric(summary.openTasks.toString(), "Tasks due", Modifier.weight(1f)) { onOpen(FarmModule.TASKS) }
            HomeMetric(summary.activeWithdrawals.toString(), "Withdrawals", Modifier.weight(1f)) { onOpen(FarmModule.HEALTH) }
            HomeMetric(summary.pendingSync.toString(), "To sync", Modifier.weight(1f)) { onOpen(FarmModule.GOAT) }
        }
    }
}

@Composable
private fun HomeMetric(value: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    FarmIllustratedSectionSurface(modifier.clickable(onClick = onClick)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SpeciesHomeTile(
    card: SpeciesHomeCard,
    goatCount: Int,
    modifier: Modifier,
    onOpen: (FarmModule) -> Unit,
) {
    FarmPastoralBackdrop(
        modifier = modifier.height(150.dp).clip(RoundedCornerShape(20.dp)).clickable { onOpen(card.module) },
        heroSpecies = card.visual,
    ) {
        Surface(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .92f),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(card.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (card.module == FarmModule.GOAT) "$goatCount head" else "Open ${card.title.lowercase()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun FarmHomeBottomBar(
    onHome: () -> Unit,
    onAnimals: () -> Unit,
    onTasks: () -> Unit,
    onMore: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = .98f), shadowElevation = 8.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            TextButton(onClick = onHome) { Text("Home") }
            TextButton(onClick = onAnimals) { Text("Animals") }
            TextButton(onClick = onTasks) { Text("Tasks") }
            TextButton(onClick = onMore) { Text("More") }
        }
    }
}

@Composable
private fun FarmMoreScreen(
    onOpen: (FarmModule) -> Unit,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
) {
    val cards = listOf(
        HomeModuleCard(FarmModule.INVENTORY, "Inventory", "Stock, lots, FEFO and reorder"),
        HomeModuleCard(FarmModule.SALES, "Sales", "Customers, animals, produce and orders"),
        HomeModuleCard(FarmModule.PROCUREMENT, "Procurement", "Suppliers, purchases and receiving"),
        HomeModuleCard(FarmModule.MONEY, "Finance", "Income, expenses and farm performance"),
        HomeModuleCard(FarmModule.PASTURE, "Pasture", "Paddocks, rotations, condition and capacity"),
        HomeModuleCard(FarmModule.LABOUR, "Labour", "People, assignments and coverage"),
        HomeModuleCard(FarmModule.ASSETS, "Assets", "Equipment, maintenance and breakdowns"),
    )
    FarmPastoralBackdrop(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FarmStorySurface(Modifier.fillMaxWidth()) {
                Text("More", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Business, land, people and equipment")
            }
            cards.forEach { card ->
                FarmIllustratedSectionSurface(Modifier.clickable { onOpen(card.module) }) {
                    Text(card.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(card.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onBack) { Text("Farm home") }
            TextButton(onClick = onSignOut) { Text("Sign out") }
        }
    }
}
