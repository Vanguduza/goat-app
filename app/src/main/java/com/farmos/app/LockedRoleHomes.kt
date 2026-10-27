@file:Suppress("ktlint:standard:function-naming")
package com.farmos.app

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.farmos.core.design.*
import com.farmos.domain.ops.*
import com.farmos.feature.ops.TaskEntryPage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HomeDateFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.UK)

/** Management A from the actual approved HTML: priority, paired summaries, six compact tools. */
@Composable
internal fun ManagementControlRoomScreen(farmName: String?, ownerMode: Boolean, summary: FarmHomeSummary,
    onOpen: (FarmDestination) -> Unit, onAnimals: () -> Unit, onMore: () -> Unit, today: LocalDate = LocalDate.now()) {
    var sheet by rememberSaveable { mutableStateOf<HomeReferenceSheet?>(null) }
    var previewTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    val homeOpen: (FarmDestination) -> Unit = { destination ->
        if (destination is FarmDestination.Task && summary.tasks.any { it.id == destination.taskId }) {
            sheet = null; previewTaskId = destination.taskId
        } else onOpen(destination)
    }
    androidx.activity.compose.BackHandler(enabled = sheet != null || previewTaskId != null) { previewTaskId = null; sheet = null }
    AnimalFarmCanvas {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            AnimalFarmHomeHeader(farmName)
            Column(Modifier.padding(start = animalFarmHomePageInset(), end = animalFarmHomePageInset(), bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    summary.loading -> AnimalFarmEmptyState("Loading farm records")
                    summary.loadError != null -> AnimalFarmEmptyState("Farm records could not be loaded. ${summary.loadError}")
                    else -> {
                        ManagementAttention(summary, today.toEpochDay(), homeOpen)
                        ManagementSummaries(summary, onOpen, onResources = { sheet = HomeReferenceSheet.RESOURCES })
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 3.dp)) {
                            Text("Farm modules", style = AnimalFarmHtmlType.sectionTitle, modifier = Modifier.semantics { heading() })
                        }
                        HomeReferenceModuleGrid(onOpen = onOpen, onSheet = { sheet = it })
                    }
                }
            }
        }
        AnimalFarmHomeBottomBar(onHome = {}, onAnimals = onAnimals,
            onTasks = { onOpen(FarmDestination.Tasks(TaskEntryPage.BOARD)) },
            onMore = { sheet = HomeReferenceSheet.MODULES }, onRecord = { sheet = HomeReferenceSheet.RECORD })
    }
    HomeReferenceSheets(sheet, { sheet = null }, { sheet = it }, homeOpen, onMore, summary, worker = false)
    previewTaskId?.let { taskId ->
        HomeTaskPreviewSheet(taskId, summary, { previewTaskId = null },
            { destination -> previewTaskId = null; onOpen(destination) },
            { previewTaskId = null; sheet = HomeReferenceSheet.RESOURCES })
    }
}

@Composable
private fun ManagementAttention(summary: FarmHomeSummary, today: Long, onOpen: (FarmDestination) -> Unit) {
    val kind = rankHomeAttention(summary.activeWithdrawals, summary.overdueTasks, pendingSync = 0)
    val task = summary.tasks.firstOrNull { projectWorkerTaskStage(it.status, it.dueEpochDay, today) == WorkerTaskStage.DUE_NOW }
    // A generic withdrawal warning cannot borrow an unrelated task's animal portrait.
    val family = if (kind == HomeAttentionKind.OVERDUE_WORK) task?.let { animalFarmFamilyFromKey(it.subjectSpecies ?: inferTaskSpeciesFamily(it.moduleCode)) } else null
    val action = if (kind == HomeAttentionKind.OVERDUE_WORK && task != null) "Open review" to FarmDestination.Task(task.id) else managementAttentionAction(kind) ?: ("Open work" to FarmDestination.Tasks(TaskEntryPage.BOARD))
    AnimalFarmHeroCard(
        category = if (kind == HomeAttentionKind.NONE) "Farm attention" else "Needs attention",
        title = when (kind) {
            HomeAttentionKind.WITHDRAWAL -> "Withdrawal window open"
            HomeAttentionKind.OVERDUE_WORK -> task?.title ?: "Review due work"
            else -> "No tasks due"
        },
        context = when (kind) {
            HomeAttentionKind.WITHDRAWAL -> "${summary.activeWithdrawals} active window(s)"
            HomeAttentionKind.OVERDUE_WORK -> "${summary.overdueTasks} task(s) due today or earlier"
            else -> "${summary.openTasks} open task(s) on this device"
        },
        actionLabel = action.first, onAction = { onOpen(action.second) }, family = family,
    )
}

@Composable
private fun ManagementSummaries(summary: FarmHomeSummary, onOpen: (FarmDestination) -> Unit, onResources: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stack = maxWidth < 300.dp || LocalDensity.current.fontScale >= 1.5f
        val work: @Composable (Modifier) -> Unit = { modifier ->
            AnimalFarmSummaryTile("Work", summary.overdueTasks.toString(), "${summary.openTasks} open · ${summary.upcomingTasks} upcoming",
                { onOpen(FarmDestination.Tasks(TaskEntryPage.BOARD)) }, modifier, lime = true, valueSuffix = "due now", indicator = { AnimalFarmDueWorkStrip(summary.overdueTasks, summary.openTasks) })
        }
        val resources: @Composable (Modifier) -> Unit = { modifier -> ResourceSummaryTile(summary, onOpen, modifier, onResources) }
        if (stack) Column(verticalArrangement = Arrangement.spacedBy(AnimalFarmHomeMetrics.tileGap)) { work(Modifier.fillMaxWidth()); resources(Modifier.fillMaxWidth()) }
        else Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(AnimalFarmHomeMetrics.tileGap)) {
            work(Modifier.weight(1f).fillMaxHeight()); resources(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** D+C layout. Stage presentation is shared with the HTML; real open/done task states remain authoritative. */
@Composable
internal fun WorkerWorkBoardScreen(farmName: String?, summary: FarmHomeSummary, onOpen: (FarmDestination) -> Unit,
    onAnimals: () -> Unit, onMore: () -> Unit, today: LocalDate = LocalDate.now()) {
    val stages = WorkerTaskStage.entries
    var stageIndex by rememberSaveable { mutableIntStateOf(0) }
    var cardIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var sheet by rememberSaveable { mutableStateOf<HomeReferenceSheet?>(null) }
    var previewTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    val homeOpen: (FarmDestination) -> Unit = { destination ->
        if (destination is FarmDestination.Task && summary.tasks.any { it.id == destination.taskId }) {
            sheet = null; previewTaskId = destination.taskId
        } else onOpen(destination)
    }
    androidx.activity.compose.BackHandler(enabled = sheet != null || previewTaskId != null) { previewTaskId = null; sheet = null }
    val stage = stages[stageIndex.coerceIn(stages.indices)]
    val matching = summary.tasks.filter { projectWorkerTaskStage(it.status, it.dueEpochDay, today.toEpochDay()) == stage }
    val selectedIndex = matching.indexOfFirst { it.id == selectedTaskId }.takeIf { it >= 0 }
    val safeIndex = if (matching.isEmpty()) 0 else selectedIndex ?: cardIndex.coerceIn(matching.indices)
    val current = matching.getOrNull(safeIndex)
    LaunchedEffect(current?.id, stage, summary.loading, summary.loadError) {
        if (!summary.loading && summary.loadError == null) { selectedTaskId = current?.id; cardIndex = safeIndex }
    }
    fun move(delta: Int) { if (matching.isNotEmpty()) { cardIndex = (safeIndex + delta).coerceIn(matching.indices); selectedTaskId = matching[cardIndex].id } }
    AnimalFarmCanvas {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            AnimalFarmHomeHeader(farmName)
            Column(Modifier.padding(start = animalFarmHomePageInset(), end = animalFarmHomePageInset(), bottom = 18.dp)) {
                when {
                    summary.loading -> AnimalFarmEmptyState("Loading farm work")
                    summary.loadError != null -> AnimalFarmEmptyState("Work could not be loaded. ${summary.loadError}")
                    else -> {
                        AnimalFarmWorkSummaryBanner("${summary.overdueTasks} due now", "${summary.openTasks} open tasks", { sheet = HomeReferenceSheet.RECORD })
                        val counts = stages.map { projected -> summary.tasks.count { projectWorkerTaskStage(it.status, it.dueEpochDay, today.toEpochDay()) == projected } }
                        AnimalFarmStageSelector(listOf("To do", "Later", "Completed"), stageIndex, {
                            stageIndex = it; cardIndex = 0; selectedTaskId = null
                        }, Modifier.padding(top = 14.dp, bottom = 14.dp), counts = counts)
                        Text("Review & decide", Modifier.padding(top = 4.dp, bottom = 14.dp).semantics { heading() }, style = AnimalFarmHtmlType.reviewTitle)
                        if (current == null) AnimalFarmEmptyState(when (stage) {
                            WorkerTaskStage.DUE_NOW -> "No open tasks due today or overdue"
                            WorkerTaskStage.UPCOMING -> "No upcoming open tasks"
                            WorkerTaskStage.COMPLETED -> "No completed tasks on this device"
                        })
                        else {
                            val family = animalFarmFamilyFromKey(current.subjectSpecies ?: inferTaskSpeciesFamily(current.moduleCode))
                            val swipe = Modifier.pointerInput(stage, current.id, matching.map { it.id }) {
                                var distance = 0f
                                detectHorizontalDragGestures(onDragStart = { distance = 0f }, onDragCancel = { distance = 0f },
                                    onDragEnd = { if (distance < -64.dp.toPx()) move(1) else if (distance > 64.dp.toPx()) move(-1) },
                                    onHorizontalDrag = { change, delta -> change.consume(); distance += delta })
                            }
                            AnimalFarmReviewTaskCard(
                                category = current.taskCode.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                                dueLabel = LocalDate.ofEpochDay(current.dueEpochDay).format(DateTimeFormatter.ofPattern("d MMM", Locale.UK)),
                                subjectName = current.subjectLabel.orEmpty(), familyLabel = family?.let(::animalFarmFamilyLabel), title = current.title,
                                fact = if (current.status == "done") "Completed" else "Open · ${current.moduleCode}",
                                actionLabel = "Open task", onAction = { homeOpen(FarmDestination.Task(current.id)) }, family = family, modifier = swipe,
                            )
                            AnimalFarmCarouselControls("${safeIndex + 1} / ${matching.size}", { move(-1) }, { move(1) },
                                safeIndex > 0, safeIndex < matching.lastIndex, position = safeIndex, count = matching.size)
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 13.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${summary.tasks.mapNotNull { inferTaskSpeciesFamily(it.moduleCode) }.distinct().size} species", style = AnimalFarmHtmlType.caption, color = AnimalFarmTheme.colors.mutedInk)
                            Text("${summary.openTasks} open tasks", style = AnimalFarmHtmlType.caption, color = AnimalFarmTheme.colors.mutedInk)
                        }
                        HomeReferenceQuickRow(onOpen, { sheet = HomeReferenceSheet.RESOURCES })
                    }
                }
            }
        }
        AnimalFarmHomeBottomBar(onHome = {}, onAnimals = onAnimals, onTasks = { onOpen(FarmDestination.Tasks(TaskEntryPage.BOARD)) },
            onMore = { sheet = HomeReferenceSheet.GUIDES }, onRecord = { sheet = HomeReferenceSheet.RECORD }, worker = true)
    }
    HomeReferenceSheets(sheet, { sheet = null }, { sheet = it }, homeOpen, onMore, summary, worker = true)
    previewTaskId?.let { taskId ->
        HomeTaskPreviewSheet(taskId, summary, { previewTaskId = null },
            { destination -> previewTaskId = null; onOpen(destination) },
            { previewTaskId = null; sheet = HomeReferenceSheet.RESOURCES })
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
    today: LocalDate = LocalDate.now(),
) {
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
                    detail = "${summary.activeWithdrawals} withdrawal(s)",
                    onClick = { onOpen(FarmDestination.Tasks(TaskEntryPage.BOARD)) },
                    lime = true,
                )
                AnimalFarmQuickAction("Search farm", { onOpen(FarmDestination.Search) })
                actions.filterNot { it.second is FarmDestination.HomePanel || it.second == FarmDestination.Goat(com.farmos.feature.goat.GoatEntryPage.SYNC) }.forEach { (label, dest) ->
                    AnimalFarmQuickAction(
                        label,
                        { onOpen(dest) },
                        modifier = syncStatusEntryModifier(label),
                    )
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
private fun ResourceSummaryTile(summary: FarmHomeSummary, onOpen: (FarmDestination) -> Unit, modifier: Modifier = Modifier,
    onResources: () -> Unit = { onOpen(FarmDestination.Module(FarmModule.INVENTORY)) }) {
    AnimalFarmSummaryTile("Resources", summary.inventoryItemCount.toString(),
        if (summary.inventoryBelowReorder > 0) "${summary.inventoryBelowReorder} below reorder" else "Recorded inventory items",
        onResources, modifier, valueSuffix = "items")
}
