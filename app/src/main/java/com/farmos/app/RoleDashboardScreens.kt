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

internal enum class FarmHomePersona {
    OWNER, MANAGER, SUPERVISOR, WORKER, BREEDING, VET, FINANCE, BUYER, GENERAL,
}

internal fun resolveFarmHomePersona(role: String): FarmHomePersona = when (role.lowercase()) {
    "owner" -> FarmHomePersona.OWNER
    "farm_manager", "manager" -> FarmHomePersona.MANAGER
    "supervisor" -> FarmHomePersona.SUPERVISOR
    "worker", "operator", "farm_worker" -> FarmHomePersona.WORKER
    "breeding", "breeding_manager" -> FarmHomePersona.BREEDING
    "vet", "health", "vet_health" -> FarmHomePersona.VET
    "finance", "finance_admin" -> FarmHomePersona.FINANCE
    "buyer", "read_only" -> FarmHomePersona.BUYER
    else -> FarmHomePersona.GENERAL
}

private data class RoleAction(
    val module: FarmModule,
    val title: String,
    val description: String,
)

/**
 * FOS-HOME-012 — role-tailored home family.
 * Visual lineage: locked session 01a04... Farm Home + Tasks references.
 */
@Composable
fun RoleAwareFarmHomeScreen(
    role: String,
    farmName: String?,
    summary: FarmHomeSummary,
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
    val onAnimals = { destination = "animals" }
    val onMore = { destination = "more" }

    when (resolveFarmHomePersona(role)) {
        FarmHomePersona.OWNER -> ManagementDashboardScreen(
            farmName = farmName,
            summary = summary,
            ownerMode = true,
            onOpen = onOpen,
            onAnimals = onAnimals,
            onMore = onMore,
        )
        FarmHomePersona.MANAGER -> ManagementDashboardScreen(
            farmName = farmName,
            summary = summary,
            ownerMode = false,
            onOpen = onOpen,
            onAnimals = onAnimals,
            onMore = onMore,
        )
        FarmHomePersona.SUPERVISOR -> SupervisorDashboardScreen(farmName, summary, onOpen, onAnimals, onMore)
        FarmHomePersona.WORKER -> WorkerDashboardScreen(farmName, summary, onOpen, onAnimals, onMore)
        FarmHomePersona.BREEDING -> BreedingDashboardScreen(farmName, summary, onOpen, onAnimals, onMore)
        FarmHomePersona.VET -> VetDashboardScreen(farmName, summary, onOpen, onAnimals, onMore)
        FarmHomePersona.FINANCE -> FinanceDashboardScreen(farmName, summary, onOpen, onAnimals, onMore)
        FarmHomePersona.BUYER -> BuyerDashboardScreen(farmName, summary, onOpen, onAnimals, onMore)
        FarmHomePersona.GENERAL -> FarmHomeScreen(farmName, summary, onOpen, onSignOut)
    }
}

/** FOS-HOME-012-A/B — owner / farm-manager operating cockpit. */
@Composable
private fun ManagementDashboardScreen(
    farmName: String?,
    summary: FarmHomeSummary,
    ownerMode: Boolean,
    onOpen: (FarmModule) -> Unit,
    onAnimals: () -> Unit,
    onMore: () -> Unit,
) {
    val roleLabel = if (ownerMode) "Owner overview" else "Management overview"
    FarmPastoralBackdrop(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(FosDimens.SectionGap),
            ) {
                FarmStorySurface(Modifier.fillMaxWidth()) {
                    Text("Good Morning", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(farmName ?: "Your farm", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(roleLabel, style = FarmOsAccentMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Animals. Land. People. A Better Tomorrow.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RoleMetricStrip(summary, onOpen)
                Text("Farm pulse", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                RoleActionGrid(
                    listOf(
                        RoleAction(FarmModule.TASKS, "Work", "Tasks, overdue work and completion"),
                        RoleAction(FarmModule.HEALTH, "Health", "Alerts, treatments and withdrawals"),
                        RoleAction(FarmModule.INVENTORY, "Inventory", "Stock, FEFO and reorder pressure"),
                        RoleAction(FarmModule.LABOUR, "People", "Assignments and workload"),
                        RoleAction(FarmModule.ASSETS, "Assets", "Maintenance and breakdowns"),
                        RoleAction(FarmModule.PASTURE, "Land", "Paddocks, rotation and capacity"),
                    ),
                    onOpen,
                )
                ManagementSpeciesStrip(summary.goatCount, onOpen)
                FarmIllustratedSectionSurface {
                    Text("Business & stewardship", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { onOpen(FarmModule.MONEY) }) { Text("Finance") }
                        TextButton(onClick = { onOpen(FarmModule.SALES) }) { Text("Sales") }
                        TextButton(onClick = { onOpen(FarmModule.PROCUREMENT) }) { Text("Procurement") }
                    }
                }
            }
            RoleHomeBottomBar(onOpen = onOpen, onAnimals = onAnimals, onMore = onMore)
        }
    }
}

/** FOS-HOME-012-D — worker My Day dashboard; phone-first and task-first. */
@Composable
private fun WorkerDashboardScreen(
    farmName: String?,
    summary: FarmHomeSummary,
    onOpen: (FarmModule) -> Unit,
    onAnimals: () -> Unit,
    onMore: () -> Unit,
) {
    FarmPastoralBackdrop(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(FosDimens.SectionGap),
            ) {
                FarmStorySurface(Modifier.fillMaxWidth()) {
                    Text("Good Morning", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(farmName ?: "Your farm", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("My day", style = FarmOsAccentMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Do the next job. Record it once. Your entries stay safe on this device until synced.")
                }
                Text("Today", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoleMetric(summary.openTasks.toString(), "Tasks due", Modifier.weight(1f)) { onOpen(FarmModule.TASKS) }
                    RoleMetric(summary.activeWithdrawals.toString(), "Withdrawals", Modifier.weight(1f)) { onOpen(FarmModule.HEALTH) }
                    RoleMetric(summary.pendingSync.toString(), "To sync", Modifier.weight(1f)) { onOpen(FarmModule.TASKS) }
                }
                FarmIllustratedSectionSurface(Modifier.clickable { onOpen(FarmModule.TASKS) }) {
                    Text("My Tasks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(if (summary.openTasks == 0) "No open tasks loaded for this farm." else "${summary.openTasks} open task(s). Open your work queue.")
                    Text("Open task board", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
                WorkerQuickActions(onOpen)
                WorkerFieldAreas(summary.goatCount, onOpen)
            }
            RoleHomeBottomBar(onOpen = onOpen, onAnimals = onAnimals, onMore = onMore)
        }
    }
}

@Composable
private fun WorkerQuickActions(onOpen: (FarmModule) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Quick Actions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        RoleActionGrid(
            listOf(
                RoleAction(FarmModule.TASKS, "Add Task", "Create or open farm work"),
                RoleAction(FarmModule.GOAT, "Scan Animal", "Open animal identification and herd tools"),
                RoleAction(FarmModule.GOAT, "Record Weight", "Capture an animal weight"),
                RoleAction(FarmModule.HEALTH, "Add Treatment", "Open governed health recording"),
            ),
            onOpen,
        )
    }
}

@Composable
private fun WorkerFieldAreas(goatCount: Int, onOpen: (FarmModule) -> Unit) {
    FarmIllustratedSectionSurface {
        Text("Field Areas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Use the native module for the animal, place or job you are working on.")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { onOpen(FarmModule.GOAT) }) { Text("Animals ($goatCount goats)") }
            TextButton(onClick = { onOpen(FarmModule.FEED) }) { Text("Feed") }
            TextButton(onClick = { onOpen(FarmModule.WATER) }) { Text("Water") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { onOpen(FarmModule.PASTURE) }) { Text("Pasture") }
            TextButton(onClick = { onOpen(FarmModule.ASSETS) }) { Text("Assets") }
        }
    }
}

/** FOS-HOME-012-C — supervisor dashboard. */
@Composable
private fun SupervisorDashboardScreen(
    farmName: String?, summary: FarmHomeSummary, onOpen: (FarmModule) -> Unit,
    onAnimals: () -> Unit, onMore: () -> Unit,
) = SpecialistDashboardShell(
    farmName = farmName,
    title = "Team today",
    subtitle = "Assign, unblock and verify farm work.",
    summary = summary,
    actions = listOf(
        RoleAction(FarmModule.TASKS, "Team tasks", "Assign, review and complete work"),
        RoleAction(FarmModule.LABOUR, "People", "Workload and coverage"),
        RoleAction(FarmModule.HEALTH, "Health exceptions", "Escalate animal health work"),
        RoleAction(FarmModule.ASSETS, "Equipment", "Breakdowns and maintenance jobs"),
        RoleAction(FarmModule.FEED, "Feed", "Feeding work and stock pressure"),
        RoleAction(FarmModule.WATER, "Water", "Checks, issues and maintenance"),
    ), onOpen = onOpen, onAnimals = onAnimals, onMore = onMore,
)

/** FOS-HOME-012-E — breeding manager dashboard. */
@Composable
private fun BreedingDashboardScreen(
    farmName: String?, summary: FarmHomeSummary, onOpen: (FarmModule) -> Unit,
    onAnimals: () -> Unit, onMore: () -> Unit,
) = SpecialistDashboardShell(
    farmName = farmName,
    title = "Breeding programme",
    subtitle = "Animals, reproductive work and the next due events.",
    summary = summary,
    actions = listOf(
        RoleAction(FarmModule.GOAT, "Goats", "Heat, mating, pregnancy and kidding"),
        RoleAction(FarmModule.RABBIT, "Rabbits", "Waves, palpation, nests and kindling"),
        RoleAction(FarmModule.SHEEP, "Sheep", "Joining, scans and lambing"),
        RoleAction(FarmModule.CATTLE, "Cattle", "Heat, service, PD and calving"),
        RoleAction(FarmModule.POULTRY, "Poultry", "Flocks, hatchery and placement"),
        RoleAction(FarmModule.TASKS, "Due work", "Breeding tasks and follow-ups"),
    ), onOpen = onOpen, onAnimals = onAnimals, onMore = onMore,
)

/** FOS-HOME-012-F — vet/health reviewer dashboard. */
@Composable
private fun VetDashboardScreen(
    farmName: String?, summary: FarmHomeSummary, onOpen: (FarmModule) -> Unit,
    onAnimals: () -> Unit, onMore: () -> Unit,
) = SpecialistDashboardShell(
    farmName = farmName,
    title = "Health review",
    subtitle = "Review observations, treatments, withdrawals and follow-up work.",
    summary = summary,
    actions = listOf(
        RoleAction(FarmModule.HEALTH, "Health centre", "Observations, treatments and withdrawals"),
        RoleAction(FarmModule.TASKS, "Follow-up tasks", "Due checks and health actions"),
        RoleAction(FarmModule.GOAT, "Goats", "Goat health and history"),
        RoleAction(FarmModule.RABBIT, "Rabbits", "Rabbit health and red flags"),
        RoleAction(FarmModule.SHEEP, "Sheep", "FAMACHA, footrot and flystrike"),
        RoleAction(FarmModule.CATTLE, "Cattle", "Health, SCC and locomotion"),
    ), onOpen = onOpen, onAnimals = onAnimals, onMore = onMore,
)

/** FOS-HOME-012-G — finance/admin dashboard. */
@Composable
private fun FinanceDashboardScreen(
    farmName: String?, summary: FarmHomeSummary, onOpen: (FarmModule) -> Unit,
    onAnimals: () -> Unit, onMore: () -> Unit,
) = SpecialistDashboardShell(
    farmName = farmName,
    title = "Farm business",
    subtitle = "Income, costs, stock movement and commercial activity.",
    summary = summary,
    actions = listOf(
        RoleAction(FarmModule.MONEY, "Finance", "Income, expenses and performance"),
        RoleAction(FarmModule.SALES, "Sales", "Customers, animals, produce and orders"),
        RoleAction(FarmModule.PROCUREMENT, "Procurement", "Suppliers, purchases and receiving"),
        RoleAction(FarmModule.INVENTORY, "Inventory", "Stock, lots, FEFO and valuation inputs"),
        RoleAction(FarmModule.LABOUR, "Labour", "Work coverage and labour-cost context"),
        RoleAction(FarmModule.ASSETS, "Assets", "Equipment and maintenance-cost context"),
    ), onOpen = onOpen, onAnimals = onAnimals, onMore = onMore,
)

/** FOS-HOME-012-H — buyer/read-only procurement dashboard. */
@Composable
private fun BuyerDashboardScreen(
    farmName: String?, summary: FarmHomeSummary, onOpen: (FarmModule) -> Unit,
    onAnimals: () -> Unit, onMore: () -> Unit,
) = SpecialistDashboardShell(
    farmName = farmName,
    title = "Purchasing",
    subtitle = "Review supply needs, stock pressure and purchasing records.",
    summary = summary,
    actions = listOf(
        RoleAction(FarmModule.PROCUREMENT, "Purchases", "Suppliers, orders and receiving"),
        RoleAction(FarmModule.INVENTORY, "Inventory", "Stock position, lots and reorder needs"),
        RoleAction(FarmModule.FEED, "Feed", "Feed inventory and consumption context"),
        RoleAction(FarmModule.ASSETS, "Assets", "Maintenance parts and equipment context"),
    ), onOpen = onOpen, onAnimals = onAnimals, onMore = onMore,
)

@Composable
private fun SpecialistDashboardShell(
    farmName: String?,
    title: String,
    subtitle: String,
    summary: FarmHomeSummary,
    actions: List<RoleAction>,
    onOpen: (FarmModule) -> Unit,
    onAnimals: () -> Unit,
    onMore: () -> Unit,
) {
    FarmPastoralBackdrop(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(FosDimens.SectionGap),
            ) {
                FarmStorySurface(Modifier.fillMaxWidth()) {
                    Text("Good Morning", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(farmName ?: "Your farm", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(title, style = FarmOsAccentMedium, color = MaterialTheme.colorScheme.primary)
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RoleMetricStrip(summary, onOpen)
                RoleActionGrid(actions, onOpen)
            }
            RoleHomeBottomBar(onOpen = onOpen, onAnimals = onAnimals, onMore = onMore)
        }
    }
}

@Composable
private fun RoleMetricStrip(summary: FarmHomeSummary, onOpen: (FarmModule) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Today", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoleMetric(summary.openTasks.toString(), "Tasks due", Modifier.weight(1f)) { onOpen(FarmModule.TASKS) }
            RoleMetric(summary.activeWithdrawals.toString(), "Withdrawals", Modifier.weight(1f)) { onOpen(FarmModule.HEALTH) }
            RoleMetric(summary.pendingSync.toString(), "To sync", Modifier.weight(1f)) { onOpen(FarmModule.TASKS) }
        }
    }
}

@Composable
private fun RoleMetric(value: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    FarmIllustratedSectionSurface(modifier.clickable(onClick = onClick)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun RoleActionGrid(actions: List<RoleAction>, onOpen: (FarmModule) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        actions.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { action ->
                    FarmIllustratedSectionSurface(
                        Modifier.weight(1f).clickable { onOpen(action.module) },
                    ) {
                        Text(action.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(action.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ManagementSpeciesStrip(goatCount: Int, onOpen: (FarmModule) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Animals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoleSpeciesTile("Goats", "$goatCount head", FarmSpeciesVisual.GOAT, Modifier.weight(1f)) { onOpen(FarmModule.GOAT) }
            RoleSpeciesTile("Cattle", "Open herd", FarmSpeciesVisual.CATTLE, Modifier.weight(1f)) { onOpen(FarmModule.CATTLE) }
            RoleSpeciesTile("Sheep", "Open flock", FarmSpeciesVisual.SHEEP, Modifier.weight(1f)) { onOpen(FarmModule.SHEEP) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoleSpeciesTile("Poultry", "Open flocks", FarmSpeciesVisual.POULTRY, Modifier.weight(1f)) { onOpen(FarmModule.POULTRY) }
            RoleSpeciesTile("Rabbits", "Open rabbitry", FarmSpeciesVisual.RABBIT, Modifier.weight(1f)) { onOpen(FarmModule.RABBIT) }
            Box(Modifier.weight(1f))
        }
    }
}

@Composable
private fun RoleSpeciesTile(
    title: String,
    subtitle: String,
    visual: FarmSpeciesVisual,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    FarmPastoralBackdrop(
        modifier = modifier.height(132.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        heroSpecies = visual,
    ) {
        Surface(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .92f),
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun RoleHomeBottomBar(
    onOpen: (FarmModule) -> Unit,
    onAnimals: () -> Unit,
    onMore: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = .98f), shadowElevation = 8.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            TextButton(onClick = {}) { Text("Home") }
            TextButton(onClick = onAnimals) { Text("Animals") }
            TextButton(onClick = { onOpen(FarmModule.TASKS) }) { Text("Tasks") }
            TextButton(onClick = onMore) { Text("More") }
        }
    }
}
