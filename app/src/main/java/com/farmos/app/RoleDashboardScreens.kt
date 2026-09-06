package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.farmos.feature.goat.GoatEntryPage
import com.farmos.feature.ops.HealthEntryPage
import com.farmos.feature.ops.TaskEntryPage

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

/**
 * FOS-HOME-012 — role-tailored home family.
 * Management A / worker D+C use the locked Animal Farm composition.
 */
@Composable
internal fun RoleAwareFarmHomeScreen(
    role: String,
    farmName: String?,
    summary: FarmHomeSummary,
    onOpen: (FarmDestination) -> Unit,
    onSignOut: () -> Unit,
) {
    var destination by remember { mutableStateOf("home") }
    if (destination == "animals") {
        SpeciesNavigatorScreen(onOpen = { onOpen(it.toDestination()) }, onBack = { destination = "home" })
        return
    }
    if (destination == "more") {
        FarmMoreScreen(onOpen = { onOpen(it.toDestination()) }, onBack = { destination = "home" }, onSignOut = onSignOut)
        return
    }
    val onAnimals = { destination = "animals" }
    val onMore = { destination = "more" }

    when (resolveFarmHomePersona(role)) {
        FarmHomePersona.OWNER -> ManagementControlRoomScreen(farmName, true, summary, onOpen, onAnimals, onMore)
        FarmHomePersona.MANAGER -> ManagementControlRoomScreen(farmName, false, summary, onOpen, onAnimals, onMore)
        FarmHomePersona.WORKER -> WorkerWorkBoardScreen(farmName, summary, onOpen, onAnimals, onMore)
        FarmHomePersona.SUPERVISOR -> SpecialistRoleShell(
            farmName, "Team today",
            listOf(
                "Team tasks" to FarmDestination.Tasks(TaskEntryPage.BOARD),
                "People" to FarmDestination.Module(FarmModule.LABOUR),
                "Health exceptions" to FarmDestination.Health(),
                "Equipment" to FarmDestination.Module(FarmModule.ASSETS),
                "Feed" to FarmDestination.Module(FarmModule.FEED),
                "Water" to FarmDestination.Module(FarmModule.WATER),
            ),
            summary, onOpen, onAnimals, onMore,
        )
        FarmHomePersona.BREEDING -> SpecialistRoleShell(
            farmName, "Breeding programme",
            listOf(
                "Goats" to FarmDestination.Goat(),
                // FOS-GOAT-037
                "Record kidding" to FarmDestination.Goat(GoatEntryPage.KIDDING),
                // FOS-GOAT-032
                "Record mating" to FarmDestination.Goat(GoatEntryPage.REPRODUCTION),
                "Rabbits" to FarmDestination.Module(FarmModule.RABBIT),
                "Sheep" to FarmDestination.Module(FarmModule.SHEEP),
                "Cattle" to FarmDestination.Module(FarmModule.CATTLE),
                "Poultry" to FarmDestination.Module(FarmModule.POULTRY),
                "Due work" to FarmDestination.Tasks(TaskEntryPage.BOARD),
            ),
            summary, onOpen, onAnimals, onMore,
        )
        FarmHomePersona.VET -> SpecialistRoleShell(
            farmName, "Health review",
            listOf(
                "Health centre" to FarmDestination.Health(),
                "Open withdrawals" to FarmDestination.Health(HealthEntryPage.WITHDRAWALS),
                "Add Treatment" to FarmDestination.Health(HealthEntryPage.TREATMENT),
                // FOS-HEALTH-013
                "Open formulary" to FarmDestination.Health(HealthEntryPage.FORMULARY),
                // FOS-HEALTH-021
                "Record vet visit" to FarmDestination.Health(HealthEntryPage.VET_VISIT),
                // FOS-HEALTH-024
                "Record lab result" to FarmDestination.Health(HealthEntryPage.LAB_RESULT),
                "Follow-up tasks" to FarmDestination.Tasks(TaskEntryPage.BOARD),
                "Goats" to FarmDestination.Goat(),
                "Rabbits" to FarmDestination.Module(FarmModule.RABBIT),
                "Sheep" to FarmDestination.Module(FarmModule.SHEEP),
                "Cattle" to FarmDestination.Module(FarmModule.CATTLE),
            ),
            summary, onOpen, onAnimals, onMore,
        )
        FarmHomePersona.FINANCE -> SpecialistRoleShell(
            farmName, "Farm business",
            listOf(
                "Finance" to FarmDestination.Module(FarmModule.MONEY),
                "Sales" to FarmDestination.Module(FarmModule.SALES),
                "Procurement" to FarmDestination.Module(FarmModule.PROCUREMENT),
                "Inventory" to FarmDestination.Module(FarmModule.INVENTORY),
                "Labour" to FarmDestination.Module(FarmModule.LABOUR),
                "Assets" to FarmDestination.Module(FarmModule.ASSETS),
            ),
            summary, onOpen, onAnimals, onMore,
        )
        FarmHomePersona.BUYER -> SpecialistRoleShell(
            farmName, "Purchasing",
            listOf(
                "Purchases" to FarmDestination.Module(FarmModule.PROCUREMENT),
                "Inventory" to FarmDestination.Module(FarmModule.INVENTORY),
                "Feed" to FarmDestination.Module(FarmModule.FEED),
                "Assets" to FarmDestination.Module(FarmModule.ASSETS),
            ),
            summary, onOpen, onAnimals, onMore,
        )
        FarmHomePersona.GENERAL -> FarmHomeScreen(farmName, summary, onOpen, onSignOut)
    }
}
