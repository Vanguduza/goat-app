@file:Suppress("ktlint:standard:function-naming")

package com.farmos.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmCanvas
import com.farmos.core.design.AnimalFarmCarouselControls
import com.farmos.core.design.AnimalFarmContextHeader
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmFamily
import com.farmos.core.design.AnimalFarmFamilyLauncher
import com.farmos.core.design.AnimalFarmHeroCard
import com.farmos.core.design.AnimalFarmHomeBottomBar
import com.farmos.core.design.AnimalFarmHomeMetrics
import com.farmos.core.design.AnimalFarmQuickAction
import com.farmos.core.design.AnimalFarmReviewTaskCard
import com.farmos.core.design.AnimalFarmStageSelector
import com.farmos.core.design.AnimalFarmSummaryTile
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.animalFarmFamilyFromKey
import com.farmos.core.design.animalFarmFamilyLabel
import com.farmos.domain.ops.HomeAttentionKind
import com.farmos.domain.ops.WorkerTaskStage
import com.farmos.domain.ops.inferTaskSpeciesFamily
import com.farmos.domain.ops.projectWorkerTaskStage
import com.farmos.domain.ops.rankHomeAttention
import com.farmos.feature.ops.TaskEntryPage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HomeDateFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)

@Composable
internal fun ManagementControlRoomScreen(
    farmName: String?,
    ownerMode: Boolean,
    summary: FarmHomeSummary,
    onOpen: (FarmDestination) -> Unit,
    onAnimals: () -> Unit,
    onMore: () -> Unit,
) {
    val today = LocalDate.now()
    val title = if (ownerMode) "Control room" else "Farm control"
    AnimalFarmCanvas {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AnimalFarmContextHeader(
                farmName = farmName,
                dateLabel = today.format(HomeDateFormat),
                title = title,
                modifier = Modifier.padding(top = 16.dp),
            )
            if (summary.loading) {
                AnimalFarmEmptyState("Loading farm records", Modifier.padding(horizontal = AnimalFarmHomeMetrics.pageInset))
            } else if (summary.loadError != null) {
                AnimalFarmEmptyState(
                    "Farm records could not be loaded. ${summary.loadError} Local records stay on this device.",
                    Modifier.padding(horizontal = AnimalFarmHomeMetrics.pageInset),
                )
            } else {
                ManagementAttention(summary, today.toEpochDay(), onOpen)
                ManagementSummaries(summary, onOpen)
                ManagementFamilies(summary.goatCount, onOpen)
            }
        }
        AnimalFarmHomeBottomBar(
            onHome = {},
            onAnimals = onAnimals,
            onTasks = { onOpen(FarmDestination.Tasks(TaskEntryPage.BOARD)) },
            onMore = onMore,
        )
    }
}

@Composable
private fun ManagementAttention(
    summary: FarmHomeSummary,
    today: Long,
    onOpen: (FarmDestination) -> Unit,
) {
    val kind = rankHomeAttention(summary.activeWithdrawals, summary.overdueTasks, summary.pendingSync)
    val family = summary.tasks.firstOrNull { projectWorkerTaskStage(it.status, it.dueEpochDay, today) == WorkerTaskStage.DUE_NOW }
        ?.let { animalFarmFamilyFromKey(inferTaskSpeciesFamily(it.moduleCode)) }
    Column(Modifier.padding(horizontal = AnimalFarmHomeMetrics.pageInset)) {
        when (kind) {
            HomeAttentionKind.WITHDRAWAL -> {
                val (label, dest) = managementAttentionAction(kind)!!
                AnimalFarmHeroCard(
                    category = "Health",
                    title = "Withdrawal window open",
                    context = "${summary.activeWithdrawals} active window(s)",
                    actionLabel = label,
                    onAction = { onOpen(dest) },
                    family = family,
                )
            }
            HomeAttentionKind.OVERDUE_WORK -> {
                val (label, dest) = managementAttentionAction(kind)!!
                AnimalFarmHeroCard(
                    category = "Work",
                    title = "Due or overdue work",
                    context = "${summary.overdueTasks} open task(s) due today or earlier",
                    actionLabel = label,
                    onAction = { onOpen(dest) },
                    family = family,
                )
            }
            HomeAttentionKind.PENDING_SYNC -> {
                val (label, dest) = managementAttentionAction(kind)!!
                AnimalFarmHeroCard(
                    category = "Sync",
                    title = "Waiting to sync",
                    context = "${summary.pendingSync} local change(s) stored on this device",
                    actionLabel = label,
                    onAction = { onOpen(dest) },
                )
            }
            HomeAttentionKind.NONE -> AnimalFarmEmptyState("No attention items on this device")
        }
    }
}

@Composable
private fun ManagementSummaries(
    summary: FarmHomeSummary,
    onOpen: (FarmDestination) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = AnimalFarmHomeMetrics.pageInset)) {
        val stacked = maxWidth < 360.dp
        val work: @Composable (Modifier) -> Unit = { modifier ->
            AnimalFarmSummaryTile(
                title = "Work",
                value = if (summary.openTasks == 0) "None due" else "${summary.openTasks} open",
                detail = "${summary.overdueTasks} due now · ${summary.upcomingTasks} upcoming",
                onClick = { onOpen(FarmDestination.Tasks(TaskEntryPage.BOARD)) },
                modifier = modifier,
                lime = true,
            )
        }
        val resources: @Composable (Modifier) -> Unit = { modifier ->
            ResourceSummaryTile(summary, onOpen, modifier)
        }
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(AnimalFarmHomeMetrics.tileGap)) {
                work(Modifier.fillMaxWidth())
                resources(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(AnimalFarmHomeMetrics.tileGap)) {
                work(Modifier.weight(1f))
                resources(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ManagementFamilies(
    goatCount: Int,
    onOpen: (FarmDestination) -> Unit,
) {
    val families = listOf(
        AnimalFarmFamily.GOAT to FarmDestination.Goat(),
        AnimalFarmFamily.RABBIT to FarmDestination.Module(FarmModule.RABBIT),
        AnimalFarmFamily.SHEEP to FarmDestination.Module(FarmModule.SHEEP),
        AnimalFarmFamily.CATTLE to FarmDestination.Module(FarmModule.CATTLE),
        AnimalFarmFamily.POULTRY to FarmDestination.Module(FarmModule.POULTRY),
    )
    Column(
        Modifier.padding(horizontal = AnimalFarmHomeMetrics.pageInset),
        verticalArrangement = Arrangement.spacedBy(AnimalFarmHomeMetrics.moduleGap),
    ) {
        Text("Modules", color = AnimalFarmTheme.colors.ink)
        families.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AnimalFarmHomeMetrics.moduleGap)) {
                row.forEach { (family, dest) ->
                    val subtitle = if (family == AnimalFarmFamily.GOAT) {
                        if (goatCount == 0) "No goats" else "$goatCount on this device"
                    } else {
                        "Open"
                    }
                    AnimalFarmFamilyLauncher(
                        family = family,
                        subtitle = subtitle,
                        onClick = { onOpen(dest) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
            }
        }
        managementHomeActions().forEach { (label, dest) ->
            AnimalFarmQuickAction(label, { onOpen(dest) })
        }
    }
}

@Composable
internal fun WorkerWorkBoardScreen(
    farmName: String?,
    summary: FarmHomeSummary,
    onOpen: (FarmDestination) -> Unit,
    onAnimals: () -> Unit,
    onMore: () -> Unit,
) {
    val today = LocalDate.now()
    val stages = WorkerTaskStage.entries
    var stageIndex by rememberSaveable { mutableIntStateOf(0) }
    var cardIndex by rememberSaveable { mutableIntStateOf(0) }
    val stage = stages[stageIndex]
    val visible = summary.tasks.mapNotNull { row ->
        val projected = projectWorkerTaskStage(row.status, row.dueEpochDay, today.toEpochDay()) ?: return@mapNotNull null
        if (projected == stage) row else null
    }
    val safeCardIndex = if (visible.isEmpty()) 0 else cardIndex.coerceIn(0, visible.lastIndex)
    AnimalFarmCanvas {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AnimalFarmContextHeader(
                farmName = farmName,
                dateLabel = today.format(HomeDateFormat),
                title = "My day",
                modifier = Modifier.padding(top = 16.dp),
            )
            Column(
                Modifier.padding(horizontal = AnimalFarmHomeMetrics.pageInset),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (summary.loading) {
                    AnimalFarmEmptyState("Loading assigned work")
                } else if (summary.loadError != null) {
                    AnimalFarmEmptyState("Work could not be loaded. ${summary.loadError} Saved records stay on this device.")
                } else {
                    AnimalFarmSummaryTile(
                        title = "Work",
                        value = "${summary.overdueTasks} due now",
                        detail = "${summary.upcomingTasks} upcoming · ${summary.completedTasks} completed",
                        onClick = { onOpen(FarmDestination.Tasks(TaskEntryPage.BOARD)) },
                        lime = true,
                    )
                    ResourceSummaryTile(summary, onOpen)
                    AnimalFarmStageSelector(
                        labels = listOf(
                            "Due now (${summary.overdueTasks})",
                            "Upcoming (${summary.upcomingTasks})",
                            "Completed (${summary.completedTasks})",
                        ),
                        selectedIndex = stageIndex,
                        onSelect = {
                            stageIndex = it
                            cardIndex = 0
                        },
                    )
                    Text("Review & decide", color = AnimalFarmTheme.colors.ink)
                    if (visible.isEmpty()) {
                        AnimalFarmEmptyState(
                            when (stage) {
                                WorkerTaskStage.DUE_NOW -> "No open tasks due today or overdue"
                                WorkerTaskStage.UPCOMING -> "No upcoming open tasks"
                                WorkerTaskStage.COMPLETED -> "No completed tasks on this device"
                            },
                        )
                    } else {
                        val row = visible[safeCardIndex]
                        val family = animalFarmFamilyFromKey(inferTaskSpeciesFamily(row.moduleCode))
                        AnimalFarmReviewTaskCard(
                            category = row.taskCode.ifBlank { row.moduleCode },
                            dueLabel = LocalDate.ofEpochDay(row.dueEpochDay).format(HomeDateFormat),
                            subjectName = row.title,
                            familyLabel = family?.let(::animalFarmFamilyLabel),
                            title = row.title,
                            fact = if (row.status == "done") "Completed" else "Open · ${row.moduleCode}",
                            actionLabel = if (row.status == "done") "Open task" else "Continue task",
                            onAction = { onOpen(FarmDestination.Task(row.id)) },
                            family = family,
                        )
                        AnimalFarmCarouselControls(
                            positionLabel = "${safeCardIndex + 1} of ${visible.size}",
                            onPrevious = { if (safeCardIndex > 0) cardIndex = safeCardIndex - 1 },
                            onNext = { if (safeCardIndex < visible.lastIndex) cardIndex = safeCardIndex + 1 },
                            previousEnabled = safeCardIndex > 0,
                            nextEnabled = safeCardIndex < visible.lastIndex,
                        )
                    }
                    Text("Quick record", color = AnimalFarmTheme.colors.ink)
                    workerHomeQuickActions().forEach { (label, dest) ->
                        AnimalFarmQuickAction(label, { onOpen(dest) })
                    }
                    Text("Guides and areas", color = AnimalFarmTheme.colors.ink)
                    AnimalFarmQuickAction("Open animals", onAnimals)
                    workerHomeAreaActions().forEach { (label, dest) ->
                        AnimalFarmQuickAction(label, { onOpen(dest) })
                    }
                }
            }
        }
        AnimalFarmHomeBottomBar(
            onHome = {},
            onAnimals = onAnimals,
            onTasks = { onOpen(FarmDestination.Tasks(TaskEntryPage.BOARD)) },
            onMore = onMore,
        )
    }
}

@Composable
internal fun SpecialistRoleShell(
    farmName: String?,
    title: String,
    actions: List<Pair<String, FarmDestination>>,
    summary: FarmHomeSummary,
    onOpen: (FarmDestination) -> Unit,
    onAnimals: () -> Unit,
    onMore: () -> Unit,
) {
    val today = LocalDate.now()
    AnimalFarmCanvas {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AnimalFarmContextHeader(
                farmName = farmName,
                dateLabel = today.format(HomeDateFormat),
                title = title,
                modifier = Modifier.padding(top = 16.dp),
            )
            Column(
                Modifier.padding(horizontal = AnimalFarmHomeMetrics.pageInset),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AnimalFarmSummaryTile(
                    title = "Today",
                    value = "${summary.openTasks} open tasks",
                    detail = "${summary.activeWithdrawals} withdrawal(s) · ${summary.pendingSync} waiting to sync",
                    onClick = { onOpen(FarmDestination.Tasks(TaskEntryPage.BOARD)) },
                    lime = true,
                )
                actions.forEach { (label, dest) ->
                    AnimalFarmQuickAction(label, { onOpen(dest) })
                }
            }
        }
        AnimalFarmHomeBottomBar(
            onHome = {},
            onAnimals = onAnimals,
            onTasks = { onOpen(FarmDestination.Tasks(TaskEntryPage.BOARD)) },
            onMore = onMore,
        )
    }
}

@Composable
private fun ResourceSummaryTile(
    summary: FarmHomeSummary,
    onOpen: (FarmDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resourceValue =
        when {
            summary.inventoryItemCount == 0 && summary.activeWithdrawals == 0 -> "No records"
            summary.inventoryBelowReorder > 0 -> "${summary.inventoryBelowReorder} below reorder"
            else -> "${summary.inventoryItemCount} item(s)"
        }
    AnimalFarmSummaryTile(
        title = "Resources",
        value = resourceValue,
        detail = "${summary.activeWithdrawals} withdrawal window(s)",
        onClick = { onOpen(FarmDestination.Module(FarmModule.INVENTORY)) },
        modifier = modifier,
    )
}
