package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.farmos.core.design.FarmEntitySelector
import com.farmos.core.design.FarmSelectionAtoms
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.IssueFeed
import com.farmos.feature.ops.FeedRecordNavigator
import com.farmos.feature.ops.FeedRecords
import com.farmos.feature.ops.SimpleCaptureScreen
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import com.farmos.core.design.runSuspendCatching

/** Feed command orchestration and owning routes. FOS-FEED-001/002. */
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
    fun run(block: suspend () -> Unit) = launchCommittedModuleWrite(
        scope = scope,
        write = block,
        isBusy = { busy },
        setBusy = { busy = it },
        setError = { error = it },
        enqueueSync = enqueueSync,
        refresh = ::refresh,
    )

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
