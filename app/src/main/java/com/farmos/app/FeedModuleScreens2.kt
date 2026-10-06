package com.farmos.app

import androidx.compose.runtime.Composable
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
    var lines by androidx.compose.runtime.remember { mutableStateOf(emptyList<String>()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val issues = ops.recentFeed()
        val purchases = ops.purchases()
        val items = ops.items().associateBy { it.id }
        // Weighted-average cost per milli-unit per item from recorded purchases.
        val costPerMilli = purchases.groupBy { it.itemId }.mapValues { (_, rows) ->
            val qty = rows.sumOf { it.quantityMilli }
            val amt = rows.sumOf { it.amountMinor }
            if (qty > 0L) amt.toDouble() / qty.toDouble() else null
        }
        val issuedByItem = issues.groupBy { it.itemId }.mapValues { (_, rows) -> rows.sumOf { it.quantityMilli } }
        val out = mutableListOf<String>()
        var totalMinor = 0.0
        var costed = false
        issuedByItem.forEach { (itemId, milli) ->
            val unitCost = costPerMilli[itemId]
            val name = items[itemId]?.name ?: itemId
            if (unitCost == null) {
                out += "$name — ${"%.1f".format(milli / 1000.0)} kg issued · no purchase cost on record"
            } else {
                val cost = milli * unitCost
                totalMinor += cost
                costed = true
                out += "$name — ${"%.1f".format(milli / 1000.0)} kg issued · ${"%.2f".format(cost / 100.0)} cost"
            }
        }
        if (out.isEmpty()) out += "No feed issues recorded on this device."
        if (costed) out.add(0, "Total issued feed cost: ${"%.2f".format(totalMinor / 100.0)} (weighted-average purchase cost)")
        lines = out
    }
    FarmOperationalPage(
        screenId = "FOS-FEED-007",
        title = "Feed cost",
        subtitle = "Issued feed valued from recorded purchases. Estimates, not ledger postings.",
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
            androidx.compose.material3.Text("Whole-ration requirement: ${"%.1f".format(rationG * headCount * days / 1000.0)} kg over $days days")
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
        purchases = ops.purchases()
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
            val dailyGrams = plan.rationGramsPerHeadPerDay * plan.headCount
            val totalGrams = dailyGrams * days
            FarmOperationalSection("Analysis — ${plan.name}") {
                androidx.compose.material3.Text("Daily requirement: ${dailyKgLabel(plan.rationGramsPerHeadPerDay, plan.headCount)}")
                androidx.compose.material3.Text("Period: ${LocalDate.ofEpochDay(plan.startEpochDay)} → ${LocalDate.ofEpochDay(plan.endEpochDay)} ($days days)")
                androidx.compose.material3.Text("Total requirement: ${"%.1f".format(totalGrams / 1000.0)} kg")
            }
            FarmOperationalSection("Price against an inventory item (optional)") {
                items.forEach { item ->
                    androidx.compose.material3.TextButton(onClick = { selectedItem = item.id }) {
                        androidx.compose.material3.Text("${if (item.id == selectedItem) "● " else ""}${item.name}")
                    }
                }
                val itemId = selectedItem
                if (itemId != null) {
                    val rows = purchases.filter { it.itemId == itemId }
                    val qty = rows.sumOf { it.quantityMilli }
                    if (qty > 0L) {
                        val avgPerGram = rows.sumOf { it.amountMinor }.toDouble() / qty.toDouble()
                        androidx.compose.material3.Text(
                            "Estimated cost: ${"%.2f".format(totalGrams * avgPerGram / 100.0)} " +
                                "at the weighted-average purchase price of ${items.firstOrNull { it.id == itemId }?.name}.",
                        )
                    } else {
                        androidx.compose.material3.Text("No purchase cost on record for that item; cost estimate unavailable.")
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
            "Total: ${"%.1f".format(plan.rationGramsPerHeadPerDay * plan.headCount * days / 1000.0)} kg",
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
            val totalA = a.rationGramsPerHeadPerDay * a.headCount * (a.endEpochDay - a.startEpochDay + 1)
            val totalB = b.rationGramsPerHeadPerDay * b.headCount * (b.endEpochDay - b.startEpochDay + 1)
            FarmOperationalSection("Difference") {
                androidx.compose.material3.Text("B − A total requirement: ${"%.1f".format((totalB - totalA) / 1000.0)} kg")
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
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val issues = ops.recentFeed()
        val plans = ops.feedPlans()
        val items = ops.items()
        val today = LocalDate.now().toEpochDay()
        val issuedByItem = issues.groupBy { it.itemId }.mapValues { (_, rows) -> rows.sumOf { it.quantityMilli } }
        val itemNames = items.associateBy({ it.id }, { it.name })
        val out = mutableListOf(
            "Feed issues recorded: ${issues.size}",
            "Feed plans recorded: ${plans.size} (${plans.count { it.endEpochDay >= today }} active)",
            "Inventory feed items: ${items.size}",
        )
        issuedByItem.forEach { (itemId, milli) ->
            out += "Issued ${(itemNames[itemId] ?: itemId)}: ${"%.1f".format(milli / 1000.0)} kg"
        }
        lines = out
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
