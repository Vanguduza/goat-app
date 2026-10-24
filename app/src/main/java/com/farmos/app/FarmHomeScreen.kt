package com.farmos.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import com.farmos.core.design.FarmActionButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmCanvas
import com.farmos.core.design.AnimalFarmContextHeader
import com.farmos.core.design.AnimalFarmFamily
import com.farmos.core.design.AnimalFarmFamilyLauncher
import com.farmos.core.design.AnimalFarmHomeBottomBar
import com.farmos.core.design.AnimalFarmHomeMetrics
import com.farmos.core.design.AnimalFarmModuleHeader
import com.farmos.core.design.AnimalFarmQuickAction
import com.farmos.core.design.AnimalFarmSummaryTile
import com.farmos.core.design.FarmBackHandler
import com.farmos.feature.ops.TaskEntryPage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class FarmModule {
    HOME, GOAT, RABBIT, SHEEP, CATTLE, POULTRY, TASKS, HEALTH, MONEY, INVENTORY,
    GROUPS, PASTURE, LABOUR, ASSETS, FEED, WATER, SALES, PROCUREMENT, WAITLIST, REPORTS,
    GENETICS, CAPACITY, ANALYTICS, SIMULATION, AI,
}

/** FOS-HOME-001 — general farm home fallback when role is unknown. */
@Composable
internal fun FarmHomeScreen(
    farmName: String?,
    summary: FarmHomeSummary = FarmHomeSummary(),
    onOpen: (FarmDestination) -> Unit,
    onSignOut: () -> Unit,
) {
    var destination by remember { mutableStateOf("home") }
    if (destination == "animals") {
        SpeciesNavigatorScreen(onOpen = { onOpen(it.toDestination()) }, onBack = { destination = "home" })
        return
    }
    if (destination == "more") {
        FarmMoreScreen(
            onOpen = { onOpen(it.toDestination()) },
            onBack = { destination = "home" },
            onSignOut = onSignOut,
            onSettings = { onOpen(FarmDestination.Settings) },
            onNavigate = onOpen,
            summary = summary,
            farmName = farmName,
            onAnimals = { destination = "animals" },
        )
        return
    }

    val families = listOf(
        AnimalFarmFamily.GOAT to FarmDestination.Goat(),
        AnimalFarmFamily.CATTLE to FarmDestination.Module(FarmModule.CATTLE),
        AnimalFarmFamily.SHEEP to FarmDestination.Module(FarmModule.SHEEP),
        AnimalFarmFamily.POULTRY to FarmDestination.Module(FarmModule.POULTRY),
        AnimalFarmFamily.RABBIT to FarmDestination.Module(FarmModule.RABBIT),
    )

    AnimalFarmCanvas {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AnimalFarmContextHeader(
                farmName = farmName,
                dateLabel = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)),
                title = "Farm home",
                modifier = Modifier.padding(top = 16.dp),
            )
            Column(
                Modifier.padding(horizontal = AnimalFarmHomeMetrics.pageInset),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AnimalFarmSummaryTile(
                    title = "Today",
                    value = "${summary.openTasks} open tasks",
                    detail = "${summary.activeWithdrawals} withdrawal(s)",
                    onClick = { onOpen(FarmDestination.Tasks(TaskEntryPage.BOARD)) },
                    lime = true,
                )
                families.chunked(2).forEach { pair ->
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AnimalFarmHomeMetrics.moduleGap),
                    ) {
                        pair.forEach { (family, dest) ->
                            val subtitle = if (family == AnimalFarmFamily.GOAT) {
                                if (summary.goatCount == 0) "No goats" else "${summary.goatCount} on this device"
                            } else {
                                "Open"
                            }
                            AnimalFarmFamilyLauncher(family, subtitle, { onOpen(dest) }, Modifier.weight(1f))
                        }
                        if (pair.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        AnimalFarmHomeBottomBar(
            onHome = { destination = "home" },
            onAnimals = { destination = "animals" },
            onTasks = { onOpen(FarmDestination.Tasks(TaskEntryPage.BOARD)) },
            onMore = { destination = "more" },
        )
    }
}
