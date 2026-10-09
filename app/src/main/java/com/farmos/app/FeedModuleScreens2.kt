package com.farmos.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.farmos.core.database.FeedPlanEntity
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.RecordFeedPlan
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch
import com.farmos.core.design.runSuspendCatching

/** FOS-FEED-007 — feed cost: issued feed valued at the farm's weighted-average purchase cost per item. */
@Composable
fun FeedCostScreen(
    ops: RoomOpsRepository,
    onBack: () -> Unit,
) {
    var lines by androidx.compose.runtime.remember { mutableStateOf(listOf("Reading feed costs…")) }
    androidx.compose.runtime.LaunchedEffect(ops) {
        lines = runSuspendCatching { feedCostLines(ops.feedTotalsByItem(), ops.allPurchases(), ops.items()) }
            .getOrElse { listOf("Feed costs could not be read: ${it.message.orEmpty()}") }
    }
    FarmOperationalPage(
        screenId = "FOS-FEED-007",
        title = "Feed cost",
        subtitle = "Separate estimates at each currency's purchase prices. Totals cover priced items only.",
        onBack = onBack,
    ) {
        FarmOperationalSection("Cost by item") {
            lines.forEach { androidx.compose.material3.Text(it) }
        }
    }
}

/** FOS-FEED-008 — ration builder: guided feed-plan capture with a live requirement preview. */
@Composable
fun RationBuilderScreen(
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var busy by androidx.compose.runtime.remember { mutableStateOf(false) }
    var error by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    val name = androidx.compose.runtime.remember { mutableStateOf("") }
    val species = androidx.compose.runtime.remember { mutableStateOf("goat") }
    val ration = androidx.compose.runtime.remember { mutableStateOf("") }
    val head = androidx.compose.runtime.remember { mutableStateOf("") }
    val start = androidx.compose.runtime.remember { mutableStateOf("") }
    val end = androidx.compose.runtime.remember { mutableStateOf("") }
    val note = androidx.compose.runtime.remember { mutableStateOf("") }
    FarmOperationalPage(
        screenId = "FOS-FEED-008",
        title = "Ration builder",
        subtitle = "Compose a ration plan. Plans are advisory drafts until recorded.",
        onBack = onBack,
    ) {
        FarmOperationalSection("Ration") {
            androidx.compose.material3.OutlinedTextField(name.value, { name.value = it }, label = { androidx.compose.material3.Text("Ration name") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(species.value, { species.value = it }, label = { androidx.compose.material3.Text("Species") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(ration.value, { ration.value = it }, label = { androidx.compose.material3.Text("Ration g/head/day") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(head.value, { head.value = it }, label = { androidx.compose.material3.Text("Head count") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(start.value, { start.value = it }, label = { androidx.compose.material3.Text("Start date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(end.value, { end.value = it }, label = { androidx.compose.material3.Text("End date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(note.value, { note.value = it }, label = { androidx.compose.material3.Text("Note") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
        }
        FarmOperationalSection("Preview") {
            val rationG = ration.value.toLongOrNull() ?: 0L
            val headCount = head.value.toIntOrNull() ?: 0
            val days = runCatching {
                LocalDate.parse(end.value).toEpochDay() - LocalDate.parse(start.value).toEpochDay() + 1
            }.getOrDefault(0L)
            androidx.compose.material3.Text("Daily requirement: ${dailyKgLabel(rationG, headCount)}")
            androidx.compose.material3.Text("Whole-ration requirement: ${reportKg(feedDailyGrams(rationG, headCount).multiply(java.math.BigDecimal.valueOf(days)))} over $days days")
            error?.let { androidx.compose.material3.Text(it) }
            androidx.compose.material3.Button(
                onClick = {
                    scope.launch {
                        busy = true; error = null
                        runSuspendCatching {
                            ops.recordFeedPlan(
                                RecordFeedPlan(
                                    UUID.randomUUID().toString(), name.value, species.value,
                                    rationG, headCount,
                                    LocalDate.parse(start.value).toEpochDay(), LocalDate.parse(end.value).toEpochDay(),
                                    note.value.ifBlank { null },
                                ),
                                newContext(),
                            )
                        }.onSuccess { enqueueSync() }.onFailure { error = it.message }
                        busy = false
                    }
                },
                enabled = !busy && name.value.isNotBlank() && start.value.isNotBlank() && end.value.isNotBlank(),
            ) { androidx.compose.material3.Text("Save ration plan") }
        }
    }
}

/** FOS-FEED-009 — ration analysis: one saved plan's requirements with an optional priced-item estimate. */
@Composable
fun RationAnalysisScreen(
    ops: RoomOpsRepository,
    onBack: () -> Unit,
) {
    var plans by androidx.compose.runtime.remember { mutableStateOf(emptyList<FeedPlanEntity>()) }
    var items by androidx.compose.runtime.remember { mutableStateOf(emptyList<com.farmos.core.database.InventoryItemEntity>()) }
    var purchases by androidx.compose.runtime.remember { mutableStateOf(emptyList<com.farmos.core.database.PurchaseEntity>()) }
    var selectedPlan by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    var selectedItem by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        plans = ops.feedPlans()
        items = ops.items()
        purchases = ops.allPurchases()
    }
    val plan = plans.firstOrNull { it.id == selectedPlan }
    FarmOperationalPage(
        screenId = "FOS-FEED-009",
        title = "Ration analysis",
        subtitle = "Requirements and cost estimate for one saved plan.",
        onBack = onBack,
    ) {
        FarmOperationalSection("Plan") {
            if (plans.isEmpty()) {
                androidx.compose.material3.Text("No feed plans recorded on this device.")
            } else {
                plans.forEach { p ->
                    androidx.compose.material3.TextButton(onClick = { selectedPlan = p.id }) {
                        androidx.compose.material3.Text("${if (p.id == selectedPlan) "● " else ""}${p.name} — ${p.speciesCode}")
                    }
                }
            }
        }
        if (plan != null) {
            val days = plan.endEpochDay - plan.startEpochDay + 1
            val totalGrams = feedPlanTotalGrams(plan)
            FarmOperationalSection("Analysis — ${plan.name}") {
                androidx.compose.material3.Text("Daily requirement: ${dailyKgLabel(plan.rationGramsPerHeadPerDay, plan.headCount)}")
                androidx.compose.material3.Text("Period: ${LocalDate.ofEpochDay(plan.startEpochDay)} → ${LocalDate.ofEpochDay(plan.endEpochDay)} ($days days)")
                androidx.compose.material3.Text("Total requirement: ${reportKg(totalGrams)}")
            }
            FarmOperationalSection("Price against an inventory item (optional)") {
                items.forEach { item ->
                    androidx.compose.material3.TextButton(onClick = { selectedItem = item.id }) {
                        androidx.compose.material3.Text("${if (item.id == selectedItem) "● " else ""}${item.name}")
                    }
                }
                val itemId = selectedItem
                if (itemId != null) {
                    val item = items.firstOrNull { it.id == itemId }
                    val quantityMilli = item?.let { rationQuantityMilli(totalGrams, it.unit) }
                    val rates = feedPurchaseRates(purchases).filter { it.itemId == itemId }
                    when {
                        quantityMilli == null -> androidx.compose.material3.Text("A ration in grams cannot be priced in ${item?.unit ?: "an unknown unit"} without a recorded mass conversion.")
                        rates.isEmpty() -> androidx.compose.material3.Text("No purchase cost on record for that item; cost estimate unavailable.")
                        else -> rates.forEach { rate ->
                            androidx.compose.material3.Text("Estimated cost: ${reportMoney(rate.estimateMinor(quantityMilli), rate.currency)} at ${rate.currency} purchase prices.")
                        }
                    }
                }
            }
        }
    }
}

/** FOS-FEED-010 — ration compare: two saved plans side by side. */
@Composable
fun RationCompareScreen(
    ops: RoomOpsRepository,
    onBack: () -> Unit,
) {
    var plans by androidx.compose.runtime.remember { mutableStateOf(emptyList<FeedPlanEntity>()) }
    var first by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    var second by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) { plans = ops.feedPlans() }
    fun summary(plan: FeedPlanEntity): List<String> {
        val days = plan.endEpochDay - plan.startEpochDay + 1
        return listOf(
            "Species: ${plan.speciesCode}",
            "Ration: ${plan.rationGramsPerHeadPerDay} g/head/day × ${plan.headCount} head",
            "Daily: ${dailyKgLabel(plan.rationGramsPerHeadPerDay, plan.headCount)}",
            "Period: $days days",
            "Total: ${reportKg(feedPlanTotalGrams(plan))}",
        )
    }
    FarmOperationalPage(
        screenId = "FOS-FEED-010",
        title = "Compare rations",
        subtitle = "Two saved plans side by side.",
        onBack = onBack,
    ) {
        FarmOperationalSection("Pick two plans") {
            if (plans.size < 2) {
                androidx.compose.material3.Text("Record at least two feed plans to compare them.")
            } else {
                plans.forEach { p ->
                    androidx.compose.foundation.layout.Row {
                        androidx.compose.material3.TextButton(onClick = { first = p.id }) {
                            androidx.compose.material3.Text("${if (p.id == first) "[A] " else ""}${p.name}")
                        }
                        androidx.compose.material3.TextButton(onClick = { second = p.id }) {
                            androidx.compose.material3.Text("${if (p.id == second) "[B] " else ""}${p.name}")
                        }
                    }
                }
            }
        }
        val a = plans.firstOrNull { it.id == first }
        val b = plans.firstOrNull { it.id == second }
        if (a != null && b != null && a.id != b.id) {
            FarmOperationalSection("A — ${a.name}") { summary(a).forEach { androidx.compose.material3.Text(it) } }
            FarmOperationalSection("B — ${b.name}") { summary(b).forEach { androidx.compose.material3.Text(it) } }
            val totalA = feedPlanTotalGrams(a)
            val totalB = feedPlanTotalGrams(b)
            FarmOperationalSection("Difference") {
                androidx.compose.material3.Text("B − A total requirement: ${reportKg(totalB.subtract(totalA))}")
            }
        }
    }
}

/** FOS-FEED-011 — feed alerts: deterministic checks over local records (low stock, expiring plans). */
@Composable
fun FeedAlertScreen(
    ops: RoomOpsRepository,
    onBack: () -> Unit,
) {
    var alerts by androidx.compose.runtime.remember { mutableStateOf(emptyList<String>()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val out = mutableListOf<String>()
        val today = LocalDate.now().toEpochDay()
        ops.items().forEach { item ->
            if (item.reorderMilli > 0L && item.quantityMilli <= item.reorderMilli) {
                out += "Low stock: ${item.name} — ${"%.1f".format(item.quantityMilli / 1000.0)} ${item.unit} on hand (reorder at ${"%.1f".format(item.reorderMilli / 1000.0)})"
            }
        }
        ops.feedPlans().forEach { plan ->
            val daysLeft = plan.endEpochDay - today
            if (daysLeft in 0..14) {
                out += "Plan expiring: ${plan.name} ends in $daysLeft days (${LocalDate.ofEpochDay(plan.endEpochDay)})"
            }
        }
        if (out.isEmpty()) out += "No feed alerts: stock above reorder levels and no plans expiring within 14 days."
        alerts = out
    }
    FarmOperationalPage(
        screenId = "FOS-FEED-011",
        title = "Feed alerts",
        subtitle = "Computed from local records only.",
        onBack = onBack,
    ) {
        FarmOperationalSection("Alerts") {
            alerts.forEach { androidx.compose.material3.Text(it) }
        }
    }
}

/** FOS-FEED-012 — feed report: read-only aggregates over issues, plans and inventory. */
@Composable
fun FeedReportScreen(
    ops: RoomOpsRepository,
    onBack: () -> Unit,
) {
    var lines by androidx.compose.runtime.remember { mutableStateOf(emptyList<String>()) }
    androidx.compose.runtime.LaunchedEffect(ops) {
        lines = runSuspendCatching {

        val issueCount = ops.feedIssueCount()
        val totals = ops.feedTotalsByItem()
        val plans = ops.feedPlans()
        val items = ops.items()
        val today = LocalDate.now().toEpochDay()
        val itemNames = items.associateBy { it.id }
        val out = mutableListOf(
            "Feed issues recorded: $issueCount",
            "Feed plans recorded: ${plans.size} (${plans.count { today in it.startEpochDay..it.endEpochDay }} active)",
            "Inventory items: ${items.size}",
        )
        totals.forEach { total ->
            val item = itemNames[total.itemId]
            out += "Issued ${item?.name ?: total.itemId}: ${reportMilli(total.quantityMilli)} ${item?.unit ?: "(unit unavailable)"}"
        }
        out
            }.getOrElse { listOf("Feed records could not be read: ${it.message.orEmpty()}") }
    }
    FarmOperationalPage(
        screenId = "FOS-FEED-012",
        title = "Feed report",
        subtitle = "Aggregates over this device's feed records.",
        onBack = onBack,
    ) {
        FarmOperationalSection("Summary") {
            lines.forEach { androidx.compose.material3.Text(it) }
        }
    }
}
