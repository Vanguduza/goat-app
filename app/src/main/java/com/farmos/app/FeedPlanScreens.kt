package com.farmos.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.farmos.core.database.FeedPlanEntity
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.runSuspendCatching
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.RecordFeedPlan
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

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
    androidx.compose.runtime.LaunchedEffect(planId) { plan = ops.feedPlan(planId) }
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
            val planGrams = feedPlanTotalGrams(current)
            FarmOperationalSection(current.name) {
                androidx.compose.material3.Text("Species: ${current.speciesCode}")
                androidx.compose.material3.Text("Ration: ${current.rationGramsPerHeadPerDay} g/head/day × ${current.headCount} head")
                androidx.compose.material3.Text("Daily requirement: ${dailyKgLabel(current.rationGramsPerHeadPerDay, current.headCount)}")
                androidx.compose.material3.Text("Period: ${LocalDate.ofEpochDay(current.startEpochDay)} → ${LocalDate.ofEpochDay(current.endEpochDay)} ($days days)")
                androidx.compose.material3.Text("Whole-plan requirement: ${reportKg(planGrams)}")
                current.note?.let { androidx.compose.material3.Text("Note: $it") }
            }
        }
    }
}
