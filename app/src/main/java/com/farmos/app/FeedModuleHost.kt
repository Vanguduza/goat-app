package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.farmos.core.design.FarmEntitySelector
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmSelectionAtoms
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.database.FeedPlanEntity
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.IssueFeed
import com.farmos.domain.ops.RecordFeedPlan
import com.farmos.feature.ops.FeedRecordNavigator
import com.farmos.feature.ops.FeedRecords
import com.farmos.feature.ops.SimpleCaptureScreen
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch
import com.farmos.core.design.runSuspendCatching

/**
 * Dedicated feed orchestration. Inventory is read for selection/context; feed owns the feed issue mutation.
 *
 * FOS-FEED-002 — feed inventory: the home surface lists inventory items with on-hand stock (root tag FOS-FEED-001).
 * FOS-FEED-004 — feed plans: recorded ration plans with computed daily totals.
 * FOS-FEED-005 — feeding schedule: the plan's dated period with per-day and whole-plan requirements.
 */
@Composable
fun FeedModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadRecords: suspend () -> FeedRecords = { FeedRecords() },
) {
    val scope = rememberCoroutineScope()
    var busy by androidx.compose.runtime.remember { mutableStateOf(false) }
    var error by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    var feedRows by androidx.compose.runtime.remember { mutableStateOf(emptyList<String>()) }
    var inventoryRows by androidx.compose.runtime.remember { mutableStateOf(emptyList<String>()) }
    var records by androidx.compose.runtime.remember(farmId) { mutableStateOf(FeedRecords()) }
    var itemOptions by androidx.compose.runtime.remember(farmId) { mutableStateOf(emptyList<FarmSelectorOption>()) }

    suspend fun refresh() {
        feedRows = ops.recentFeed().map { "${it.itemId} · ${it.quantityMilli} milli" }
        val items = ops.items()
        inventoryRows = items.map { "${it.id} ${it.sku} · ${it.name} · ${it.quantityMilli} ${it.unit}" }
        itemOptions = items.map { FarmSelectorOption(it.id, "${it.name} · ${it.sku}", "${BigDecimal.valueOf(it.quantityMilli, 3).stripTrailingZeros().toPlainString()} ${it.unit} on hand") }
        records = loadRecords()
    }
    LaunchedEffect(farmId) { runSuspendCatching { refresh() } }
    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            runSuspendCatching { block(); refresh() }.onSuccess { enqueueSync() }.onFailure { error = it.message }
            busy = false
        }
    }

    val itemId = androidx.compose.runtime.remember { mutableStateOf("") }
    val qty = androidx.compose.runtime.remember { mutableStateOf("") }
    val day = androidx.compose.runtime.remember { mutableStateOf("") }
    var page by androidx.compose.runtime.remember { mutableStateOf(FeedModulePage.HOME) }
    var selectedPlan by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }

    when (page) {
        FeedModulePage.HOME -> FeedRecordNavigator(records) { recordActions -> SimpleCaptureScreen(
        screenId = "FOS-FEED-001",
        title = "Feed",
        help = "Issuing feed deducts inventory in milli-units. Ration percentages stay advisory drafts.",
        empty = "No feed issues on this device.",
        rows = feedRows + inventoryRows,
        busy = busy,
        error = error,
        fields = listOf("Quantity" to qty, "Date" to day),
        actionLabel = "Issue feed",
        onSubmit = {
            run {
                val quantityMilli = qty.value.toScaledLongExact(3, "Quantity")
                ops.issueFeed(
                    IssueFeed(UUID.randomUUID().toString(), itemId.value, null, quantityMilli, LocalDate.parse(day.value).toEpochDay()),
                    newContext(),
                )
            }
        },
        onBack = onBack,
        extra = {
            FarmEntitySelector(FarmSelectionAtoms.INVENTORY_ITEM_SELECTOR, "Feed item", itemOptions, itemId.value.ifBlank { null }, { itemId.value = it }, "No inventory items on this device.", enabled = !busy)
            recordActions()
            androidx.compose.material3.TextButton(onClick = { page = FeedModulePage.PLANS }) { androidx.compose.material3.Text("Feed plans") }
            androidx.compose.material3.TextButton(onClick = { page = FeedModulePage.COST }) { androidx.compose.material3.Text("Feed cost") }
            androidx.compose.material3.TextButton(onClick = { page = FeedModulePage.RATION_BUILDER }) { androidx.compose.material3.Text("Ration builder") }
            androidx.compose.material3.TextButton(onClick = { page = FeedModulePage.RATION_ANALYSIS }) { androidx.compose.material3.Text("Ration analysis") }
            androidx.compose.material3.TextButton(onClick = { page = FeedModulePage.RATION_COMPARE }) { androidx.compose.material3.Text("Compare rations") }
            androidx.compose.material3.TextButton(onClick = { page = FeedModulePage.ALERTS }) { androidx.compose.material3.Text("Feed alerts") }
            androidx.compose.material3.TextButton(onClick = { page = FeedModulePage.REPORT }) { androidx.compose.material3.Text("Feed report") }
        },
    ) }
        FeedModulePage.PLANS -> FeedPlanListScreen(
            ops = ops,
            newContext = newContext,
            enqueueSync = enqueueSync,
            onSelect = { selectedPlan = it; page = FeedModulePage.PLAN_DETAIL },
            onBack = { page = FeedModulePage.HOME },
        )
        FeedModulePage.PLAN_DETAIL -> FeedPlanDetailScreen(
            ops = ops,
            planId = selectedPlan.orEmpty(),
            onBack = { page = FeedModulePage.PLANS },
        )
        FeedModulePage.COST -> FeedCostScreen(ops = ops, onBack = { page = FeedModulePage.HOME })
        FeedModulePage.RATION_BUILDER -> RationBuilderScreen(
            ops = ops,
            newContext = newContext,
            enqueueSync = enqueueSync,
            onBack = { page = FeedModulePage.HOME },
        )
        FeedModulePage.RATION_ANALYSIS -> RationAnalysisScreen(ops = ops, onBack = { page = FeedModulePage.HOME })
        FeedModulePage.RATION_COMPARE -> RationCompareScreen(ops = ops, onBack = { page = FeedModulePage.HOME })
        FeedModulePage.ALERTS -> FeedAlertScreen(ops = ops, onBack = { page = FeedModulePage.HOME })
        FeedModulePage.REPORT -> FeedReportScreen(ops = ops, onBack = { page = FeedModulePage.HOME })
    }
}

private enum class FeedModulePage { HOME, PLANS, PLAN_DETAIL, COST, RATION_BUILDER, RATION_ANALYSIS, RATION_COMPARE, ALERTS, REPORT }

internal fun dailyKgLabel(rationGramsPerHeadPerDay: Long, headCount: Int): String =
    "%.1f".format(rationGramsPerHeadPerDay * headCount / 1000.0) + " kg/day"

/** FOS-FEED-004 — feed plans: recorded ration plans with computed daily totals, plus plan capture. */
@Composable
fun FeedPlanListScreen(
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var busy by androidx.compose.runtime.remember { mutableStateOf(false) }
    var error by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    var plans by androidx.compose.runtime.remember { mutableStateOf(emptyList<FeedPlanEntity>()) }
    suspend fun refresh() {
        plans = ops.feedPlans()
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { runSuspendCatching { refresh() } }
    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            runSuspendCatching { block(); refresh() }.onSuccess { enqueueSync() }.onFailure { error = it.message }
            busy = false
        }
    }
    val name = androidx.compose.runtime.remember { mutableStateOf("") }
    val species = androidx.compose.runtime.remember { mutableStateOf("goat") }
    val ration = androidx.compose.runtime.remember { mutableStateOf("") }
    val head = androidx.compose.runtime.remember { mutableStateOf("") }
    val start = androidx.compose.runtime.remember { mutableStateOf("") }
    val end = androidx.compose.runtime.remember { mutableStateOf("") }
    val note = androidx.compose.runtime.remember { mutableStateOf("") }
    FarmOperationalPage(
        screenId = "FOS-FEED-004",
        title = "Feed plans",
        subtitle = "Ration plans with computed daily requirements. Plans are advisory drafts.",
        onBack = onBack,
    ) {
        FarmOperationalSection("Record a feed plan") {
            androidx.compose.material3.OutlinedTextField(name.value, { name.value = it }, label = { androidx.compose.material3.Text("Name") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(species.value, { species.value = it }, label = { androidx.compose.material3.Text("Species") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(ration.value, { ration.value = it }, label = { androidx.compose.material3.Text("Ration g/head/day") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(head.value, { head.value = it }, label = { androidx.compose.material3.Text("Head count") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(start.value, { start.value = it }, label = { androidx.compose.material3.Text("Start date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(end.value, { end.value = it }, label = { androidx.compose.material3.Text("End date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(note.value, { note.value = it }, label = { androidx.compose.material3.Text("Note") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
            error?.let { androidx.compose.material3.Text(it) }
            androidx.compose.material3.Button(
                onClick = {
                    run {
                        ops.recordFeedPlan(
                            RecordFeedPlan(
                                UUID.randomUUID().toString(), name.value, species.value,
                                ration.value.toLongOrNull() ?: 0L, head.value.toIntOrNull() ?: 0,
                                LocalDate.parse(start.value).toEpochDay(), LocalDate.parse(end.value).toEpochDay(),
                                note.value.ifBlank { null },
                            ),
                            newContext(),
                        )
                    }
                },
                enabled = !busy && name.value.isNotBlank() && start.value.isNotBlank() && end.value.isNotBlank(),
            ) { androidx.compose.material3.Text("Add feed plan") }
        }
        FarmOperationalSection("Feed plans") {
            if (plans.isEmpty()) {
                androidx.compose.material3.Text("No feed plans recorded on this device.")
            } else {
                plans.forEach { plan ->
                    androidx.compose.material3.TextButton(onClick = { onSelect(plan.id) }) {
                        androidx.compose.material3.Text("${plan.name} — ${plan.speciesCode} · ${plan.rationGramsPerHeadPerDay} g/head/day × ${plan.headCount} head = ${dailyKgLabel(plan.rationGramsPerHeadPerDay, plan.headCount)}")
                    }
                }
            }
        }
    }
}

/** FOS-FEED-005 — feeding schedule: the plan's dated period with per-day and whole-plan feed requirements, computed from the recorded plan. */
@Composable
fun FeedPlanDetailScreen(
    ops: RoomOpsRepository,
    planId: String,
    onBack: () -> Unit,
) {
    var plan by androidx.compose.runtime.remember { mutableStateOf<FeedPlanEntity?>(null) }
    androidx.compose.runtime.LaunchedEffect(planId) { plan.value = ops.feedPlan(planId) }
    FarmOperationalPage(
        screenId = "FOS-FEED-005",
        title = "Feeding schedule",
        subtitle = "Requirements computed from the recorded plan.",
        onBack = onBack,
    ) {
        val current = plan
        if (current == null) {
            FarmOperationalSection("Not found", "This feed plan is not on this device.") {}
        } else {
            val days = current.endEpochDay - current.startEpochDay + 1
            val dailyGrams = current.rationGramsPerHeadPerDay * current.headCount
            val planGrams = dailyGrams * days
            FarmOperationalSection(current.name) {
                androidx.compose.material3.Text("Species: ${current.speciesCode}")
                androidx.compose.material3.Text("Ration: ${current.rationGramsPerHeadPerDay} g/head/day × ${current.headCount} head")
                androidx.compose.material3.Text("Daily requirement: ${dailyKgLabel(current.rationGramsPerHeadPerDay, current.headCount)}")
                androidx.compose.material3.Text("Period: ${LocalDate.ofEpochDay(current.startEpochDay)} → ${LocalDate.ofEpochDay(current.endEpochDay)} ($days days)")
                androidx.compose.material3.Text("Whole-plan requirement: ${"%.1f".format(planGrams / 1000.0)} kg")
                current.note?.let { androidx.compose.material3.Text("Note: $it") }
            }
        }
    }
}

