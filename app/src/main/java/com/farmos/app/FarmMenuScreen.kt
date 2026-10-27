package com.farmos.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmCanvas
import com.farmos.core.design.AnimalFarmHomeBottomBar
import com.farmos.core.design.AnimalFarmHomeMetrics
import com.farmos.core.design.AnimalFarmModuleHeader
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.FarmBackButton
import com.farmos.core.design.FarmBackHandler
import com.farmos.core.design.FosDimens
import com.farmos.feature.goat.GoatEntryPage

/** Secondary destinations retain their native route and the persona that already exposed them. */
internal fun farmMenuActions(persona: FarmHomePersona): List<Pair<String, FarmDestination>> {
    val personaActions = when (persona) {
        FarmHomePersona.OWNER, FarmHomePersona.MANAGER -> managementHomeActions()
        FarmHomePersona.WORKER -> workerHomeQuickActions() + workerHomeAreaActions()
        FarmHomePersona.GENERAL -> generalHomeActions()
        else -> specialistHomeActions(persona)
    }
    return (
        listOf("Search farm" to FarmDestination.Search) + personaActions +
            listOf(
                "Inventory" to FarmDestination.Module(FarmModule.INVENTORY),
                "Sales" to FarmDestination.Module(FarmModule.SALES),
                "Procurement" to FarmDestination.Module(FarmModule.PROCUREMENT),
                "Finance" to FarmDestination.Module(FarmModule.MONEY),
                "Pasture" to FarmDestination.Module(FarmModule.PASTURE),
                "Labour" to FarmDestination.Module(FarmModule.LABOUR),
                "Assets" to FarmDestination.Module(FarmModule.ASSETS),
            )
    ).distinctBy { it.second }
}

private fun farmMenuSection(destination: FarmDestination): String = when (destination) {
    is FarmDestination.HomePanel, FarmDestination.Search -> "Farm tools"
    is FarmDestination.Module -> when (destination.module) {
        FarmModule.MONEY, FarmModule.SALES, FarmModule.PROCUREMENT, FarmModule.WAITLIST -> "Business"
        FarmModule.REPORTS, FarmModule.GENETICS, FarmModule.CAPACITY,
        FarmModule.ANALYTICS, FarmModule.SIMULATION, FarmModule.AI -> "Insights & planning"
        else -> "Farm operations"
    }
    else -> "Farm operations"
}

/**
 * FOS-HOME-001/012 secondary navigation, FOS-HOME-003..011 and FOS-SYNC-002 entry.
 * P14 grouped rows use the approved A surfaces; this is navigation, never a business writer.
 */
@Composable
internal fun FarmMoreScreen(
    onOpen: (FarmModule) -> Unit,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onSettings: (() -> Unit)? = null,
    onNavigate: ((FarmDestination) -> Unit)? = null,
    persona: FarmHomePersona = FarmHomePersona.GENERAL,
    summary: FarmHomeSummary = FarmHomeSummary(),
    farmName: String? = null,
    onAnimals: (() -> Unit)? = null,
) {
    FarmBackHandler(onBack)
    var showSignOutConfirmation by rememberSaveable { mutableStateOf(false) }
    val allActions = farmMenuActions(persona)
    val syncDestination = FarmDestination.Goat(GoatEntryPage.SYNC)
    val open: (FarmDestination) -> Unit = { target ->
        if (onNavigate != null) onNavigate(target)
        else if (target is FarmDestination.Module) onOpen(target.module)
    }
    val available = allActions.filter { onNavigate != null || it.second is FarmDestination.Module }
    val grouped = available.filterNot { it.second == syncDestination || it.second == FarmDestination.Search }
        .groupBy { farmMenuSection(it.second) }
    AnimalFarmCanvas {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(AnimalFarmHomeMetrics.pageInset)
                .testTag("farm-menu"),
            verticalArrangement = Arrangement.spacedBy(FosDimens.IntraCardGap),
        ) {
            FarmBackButton("Farm home", onBack)
            AnimalFarmModuleHeader("More", farmName ?: "")
            if (onNavigate != null) {
                FarmMenuRow("Search farm", Icons.Rounded.Search, { open(FarmDestination.Search) }, modifier = Modifier.testTag("farm-menu-action:Search farm"), emphasized = true)
            }
            if (available.any { it.second == syncDestination }) {
                val syncDetail = when {
                    summary.loading -> "Checking local changes"
                    summary.loadError != null -> "Open to check status"
                    summary.pendingSync > 0 -> "${summary.pendingSync} changes waiting"
                    else -> "No local changes waiting"
                }
                FarmMenuRow(
                    "Sync", Icons.Rounded.Sync, { open(syncDestination) }, syncDetail,
                    Modifier.testTag("farm-screen:$SYNC_STATUS_HOME_ENTRY_SCREEN_ID"),
                )
            }
            listOf("Farm tools", "Farm operations", "Business", "Insights & planning").forEach { section ->
                grouped[section]?.takeIf { it.isNotEmpty() }?.let { actions ->
                    FarmMenuGroup(section, actions, open)
                }
            }
            onSettings?.let { FarmMenuRow("Farm settings", Icons.Rounded.Settings, it) }
            FarmMenuRow("Sign out", Icons.AutoMirrored.Rounded.Logout, { showSignOutConfirmation = true })
        }
        onAnimals?.let { animals ->
            AnimalFarmHomeBottomBar(
                onHome = onBack,
                onAnimals = animals,
                onTasks = { open(FarmDestination.Tasks(com.farmos.feature.ops.TaskEntryPage.BOARD)) },
                onMore = {},
            )
        }
    }
    SignOutConfirmationDialog(
        visible = showSignOutConfirmation,
        onDismiss = { showSignOutConfirmation = false },
        onConfirm = {
            showSignOutConfirmation = false
            onSignOut()
        },
    )
}

@Composable
private fun FarmMenuGroup(
    title: String,
    actions: List<Pair<String, FarmDestination>>,
    onOpen: (FarmDestination) -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(title == "Farm tools") }
    val colors = AnimalFarmTheme.colors
    val icon = when (title) {
        "Farm tools" -> Icons.AutoMirrored.Rounded.EventNote
        "Business" -> Icons.Rounded.AccountBalanceWallet
        "Insights & planning" -> Icons.Rounded.BarChart
        else -> Icons.Rounded.GridView
    }
    Surface(shape = RoundedCornerShape(AnimalFarmHomeMetrics.actionRadius), color = colors.surface) {
        Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                    .padding(FosDimens.IntraCardGap),
                horizontalArrangement = Arrangement.spacedBy(FosDimens.IntraCardGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = colors.primary, modifier = Modifier.size(24.dp))
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = colors.ink)
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = colors.mutedInk)
            }
            if (expanded) actions.forEach { (label, destination) ->
                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = FosDimens.IntraCardGap))
                FarmMenuRow(
                    label.removePrefix("Open ").replaceFirstChar { it.uppercase() },
                    null,
                    { onOpen(destination) },
                    modifier = Modifier.testTag("farm-menu-action:$label"),
                )
            }
        }
    }
}

@Composable
private fun FarmMenuRow(
    title: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    detail: String? = null,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val colors = AnimalFarmTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AnimalFarmHomeMetrics.actionRadius),
        color = if (emphasized) colors.lime else colors.surface,
        contentColor = if (emphasized) colors.onLime else colors.ink,
    ) {
        Row(
            modifier.fillMaxWidth().heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                .clickable(role = Role.Button, onClick = onClick).padding(FosDimens.IntraCardGap),
            horizontalArrangement = Arrangement.spacedBy(FosDimens.IntraCardGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let { Icon(it, null, modifier = Modifier.size(24.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FosDimens.Grid)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                detail?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = colors.mutedInk) }
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, modifier = Modifier.size(20.dp))
        }
    }
}
