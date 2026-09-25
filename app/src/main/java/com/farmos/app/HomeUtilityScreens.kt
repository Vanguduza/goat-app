package com.farmos.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.farmos.core.design.AnimalFarmCanvas
import com.farmos.core.design.AnimalFarmHomeMetrics
import com.farmos.core.design.AnimalFarmModuleHeader
import com.farmos.core.design.AnimalFarmQuickAction
import com.farmos.core.design.FarmIllustratedSectionSurface
import com.farmos.core.network.FarmMembership
import com.farmos.feature.goat.GoatEntryPage
import com.farmos.feature.ops.HealthEntryPage
import com.farmos.feature.ops.TaskEntryPage
import java.time.LocalDate

@Composable
internal fun HomeUtilityScreen(
    surface: HomeSurface,
    summary: FarmHomeSummary,
    onOpen: (FarmDestination) -> Unit,
    onBack: () -> Unit,
    memberships: List<FarmMembership> = emptyList(),
    farmNames: Map<String, String> = emptyMap(),
    currentFarmId: String? = null,
    onSwitchFarm: (FarmMembership) -> Unit = {},
) {
    val screenId = when (surface) {
        HomeSurface.TODAY_SUMMARY -> "FOS-HOME-003"
        HomeSurface.ALERTS -> "FOS-HOME-004"
        HomeSurface.ACTIVITY_STREAM -> "FOS-HOME-005"
        HomeSurface.FARM_SWITCHER -> "FOS-HOME-010"
        HomeSurface.NOTIFICATIONS -> "FOS-HOME-011"
        HomeSurface.QUICK_CAPTURE -> "FOS-HOME-008"
    }
    val title = when (surface) {
        HomeSurface.TODAY_SUMMARY -> "Today summary"
        HomeSurface.ALERTS -> "Farm alerts"
        HomeSurface.ACTIVITY_STREAM -> "Farm activity"
        HomeSurface.FARM_SWITCHER -> "Switch farm"
        HomeSurface.NOTIFICATIONS -> "Notifications"
        HomeSurface.QUICK_CAPTURE -> "Quick capture"
    }
    val subtitle = when (surface) {
        HomeSurface.TODAY_SUMMARY -> "Current work, health and sync state"
        HomeSurface.ALERTS -> "Items that need farm attention"
        HomeSurface.ACTIVITY_STREAM -> "Recent task and farm-work history on this device"
        HomeSurface.FARM_SWITCHER -> "Move between farms you are authorised to access"
        HomeSurface.NOTIFICATIONS -> "Current operational notices derived from farm state"
        HomeSurface.QUICK_CAPTURE -> "Record common farm work without hunting through modules"
    }

    AnimalFarmCanvas(Modifier.testTag("farm-screen:$screenId")) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(AnimalFarmHomeMetrics.pageInset),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AnimalFarmModuleHeader(title, subtitle)
            when (surface) {
                HomeSurface.TODAY_SUMMARY -> TodaySummaryContent(summary, onOpen)
                HomeSurface.ALERTS -> FarmAlertsContent(summary, onOpen)
                HomeSurface.ACTIVITY_STREAM -> FarmActivityContent(summary)
                HomeSurface.FARM_SWITCHER -> FarmSwitcherContent(
                    memberships = memberships,
                    farmNames = farmNames,
                    currentFarmId = currentFarmId,
                    onSwitchFarm = onSwitchFarm,
                )
                HomeSurface.NOTIFICATIONS -> NotificationCentreContent(summary, onOpen)
                HomeSurface.QUICK_CAPTURE -> QuickCaptureContent(onOpen)
            }
            TextButton(onClick = onBack) { Text("Farm home") }
        }
    }
}

@Composable
private fun TodaySummaryContent(
    summary: FarmHomeSummary,
    onOpen: (FarmDestination) -> Unit,
) {
    val today = LocalDate.now().toEpochDay()
    val dueToday = summary.tasks.count { it.status == "open" && it.dueEpochDay == today }
    val overdue = summary.tasks.count { it.status == "open" && it.dueEpochDay < today }

    FarmIllustratedSectionSurface {
        SummaryLine("Open tasks", summary.openTasks.toString())
        SummaryLine("Due today", dueToday.toString())
        SummaryLine("Overdue", overdue.toString())
        SummaryLine("Active withdrawals", summary.activeWithdrawals.toString())
        SummaryLine("Low-stock items", summary.inventoryBelowReorder.toString())
        SummaryLine("Waiting to sync", summary.pendingSync.toString())
    }
    AnimalFarmQuickAction("Open tasks", { onOpen(FarmDestination.Tasks(TaskEntryPage.BOARD)) })
    AnimalFarmQuickAction("Open health", { onOpen(FarmDestination.Health()) })
    AnimalFarmQuickAction("Open sync status", { onOpen(FarmDestination.Goat(GoatEntryPage.SYNC)) })
}

@Composable
private fun FarmAlertsContent(
    summary: FarmHomeSummary,
    onOpen: (FarmDestination) -> Unit,
) {
    val alerts = homeAttentionMessages(summary)

    FarmIllustratedSectionSurface {
        if (alerts.isEmpty()) {
            Text("No current farm alerts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Overdue work, withdrawals, low stock and pending sync will appear here.")
        } else {
            alerts.forEach { alert -> Text("• $alert") }
        }
    }
    AnimalFarmQuickAction("Review tasks", { onOpen(FarmDestination.Tasks(TaskEntryPage.BOARD)) })
    AnimalFarmQuickAction("Review withdrawals", { onOpen(FarmDestination.Health(HealthEntryPage.WITHDRAWALS)) })
    AnimalFarmQuickAction("Review inventory", { onOpen(FarmDestination.Module(FarmModule.INVENTORY)) })
    AnimalFarmQuickAction("Review sync", { onOpen(FarmDestination.Goat(GoatEntryPage.SYNC)) })
}

@Composable
private fun FarmActivityContent(summary: FarmHomeSummary) {
    val activity = summary.tasks
        .sortedWith(compareByDescending<HomeTaskRow> { it.dueEpochDay }.thenBy { it.title })
        .take(12)

    if (activity.isEmpty()) {
        FarmIllustratedSectionSurface {
            Text("No task activity on this device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Task creation, completion and due work will appear here as local records become available.")
        }
        return
    }

    activity.forEach { row ->
        FarmIllustratedSectionSurface {
            Text(row.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${row.moduleCode} · ${row.taskCode}")
            Text(
                when (row.status) {
                    "done" -> "Completed"
                    "open" -> "Open · due ${LocalDate.ofEpochDay(row.dueEpochDay)}"
                    else -> row.status.replace('_', ' ').replaceFirstChar { it.uppercase() }
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FarmSwitcherContent(
    memberships: List<FarmMembership>,
    farmNames: Map<String, String>,
    currentFarmId: String?,
    onSwitchFarm: (FarmMembership) -> Unit,
) {
    if (memberships.isEmpty()) {
        FarmIllustratedSectionSurface {
            Text("No farm memberships available", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Reconnect or re-authenticate to refresh authorised farm access.")
        }
        return
    }

    memberships.sortedBy { farmNames[it.farmId] ?: it.farmId }.forEach { membership ->
        val farmLabel = farmNames[membership.farmId] ?: "Farm ${membership.farmId.take(8)}"
        if (membership.farmId == currentFarmId) {
            FarmIllustratedSectionSurface {
                Text(farmLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Current farm · ${membership.role.replace('_', ' ')}")
            }
        } else {
            AnimalFarmQuickAction(
                label = "Switch to $farmLabel",
                onClick = { onSwitchFarm(membership) },
            )
        }
    }
}

@Composable
private fun NotificationCentreContent(
    summary: FarmHomeSummary,
    onOpen: (FarmDestination) -> Unit,
) {
    val notifications = homeAttentionMessages(summary)
    if (notifications.isEmpty()) {
        FarmIllustratedSectionSurface {
            Text("No current notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Operational notices will appear when farm state needs attention.")
        }
        return
    }

    FarmIllustratedSectionSurface {
        notifications.forEach { notification -> Text("• $notification") }
    }
    if (summary.overdueTasks > 0) {
        AnimalFarmQuickAction("Open overdue work", { onOpen(FarmDestination.Tasks(TaskEntryPage.BOARD)) })
    }
    if (summary.activeWithdrawals > 0) {
        AnimalFarmQuickAction("Open withdrawals", { onOpen(FarmDestination.Health(HealthEntryPage.WITHDRAWALS)) })
    }
    if (summary.inventoryBelowReorder > 0) {
        AnimalFarmQuickAction("Open low stock", { onOpen(FarmDestination.Module(FarmModule.INVENTORY)) })
    }
    if (summary.pendingSync > 0) {
        AnimalFarmQuickAction("Open pending sync", { onOpen(FarmDestination.Goat(GoatEntryPage.SYNC)) })
    }
}

private fun homeAttentionMessages(summary: FarmHomeSummary): List<String> =
    buildList {
        if (summary.overdueTasks > 0) add("${summary.overdueTasks} overdue task(s)")
        if (summary.activeWithdrawals > 0) add("${summary.activeWithdrawals} active withdrawal(s)")
        if (summary.inventoryBelowReorder > 0) add("${summary.inventoryBelowReorder} item(s) at or below reorder")
        if (summary.pendingSync > 0) add("${summary.pendingSync} local change(s) waiting to sync")
        summary.loadError?.takeIf { it.isNotBlank() }?.let { add("Home data could not fully refresh") }
    }

@Composable
private fun QuickCaptureContent(onOpen: (FarmDestination) -> Unit) {
    FarmIllustratedSectionSurface {
        Text("Common records", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Each action opens the existing authoritative capture surface.")
    }
    AnimalFarmQuickAction("Add task", { onOpen(FarmDestination.Tasks(TaskEntryPage.CREATE)) })
    AnimalFarmQuickAction("Record goat weight", { onOpen(FarmDestination.Goat(GoatEntryPage.WEIGHT)) })
    AnimalFarmQuickAction("Scan animal", { onOpen(FarmDestination.Goat(GoatEntryPage.SCAN)) })
    AnimalFarmQuickAction("Add treatment", { onOpen(FarmDestination.Health(HealthEntryPage.TREATMENT)) })
    AnimalFarmQuickAction("Record observation", { onOpen(FarmDestination.Health(HealthEntryPage.RECORD_OBSERVATION)) })
}

@Composable
private fun SummaryLine(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
