package com.farmos.feature.ops

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.farmos.core.design.FarmWorkflowState
import com.farmos.core.design.FarmWorkflowStep
import com.farmos.core.design.FarmWorkflowTimeline
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun StockCountWorkflow(count: StockCountView) {
    val decision = when (count.status) { "POSTED" -> "Posted"; "REJECTED" -> "Rejected"; "SUBMITTED" -> "Waiting for management review"; "COUNTING" -> "Waiting for submission"; else -> count.status }
    val state = when (count.status) { "POSTED" -> FarmWorkflowState.RECORDED; "REJECTED" -> FarmWorkflowState.ATTENTION; "SUBMITTED" -> FarmWorkflowState.CURRENT; "COUNTING" -> FarmWorkflowState.NOT_RECORDED; else -> FarmWorkflowState.UNKNOWN }
    FarmWorkflowTimeline("Stock count progress", listOf(
        FarmWorkflowStep("Count started", count.started, "${count.lines.size} item(s) counted", FarmWorkflowState.RECORDED),
        FarmWorkflowStep("Submitted", count.submitted ?: "Not submitted", state = if (count.submitted == null) FarmWorkflowState.NOT_RECORDED else FarmWorkflowState.RECORDED),
        FarmWorkflowStep("Management decision", decision, listOfNotNull(count.decided, count.rejectionReason).joinToString(" · ").ifBlank { null }, state),
    ), modifier = Modifier.testTag("stock-count-workflow:" + count.countId), caption = "Stock changes only when management posts the count.")
}

@Composable
internal fun TaskStateWorkflow(task: TaskUiRow, today: Long) {
    FarmWorkflowTimeline("Task state", listOf(
        FarmWorkflowStep("Due date", LocalDate.ofEpochDay(task.dueEpochDay).toString(), task.repeatLabel, FarmWorkflowState.PLANNED),
        FarmWorkflowStep("Assignment", task.assigneeLabel ?: "No assignee recorded", state = if (task.assigneeLabel == null) FarmWorkflowState.NOT_RECORDED else FarmWorkflowState.CURRENT),
        FarmWorkflowStep("Completion", if (task.status == "done") "Marked done" else if (task.status == "open" && task.dueEpochDay < today) "Overdue; still open" else if (task.status == "open") "Not marked done" else task.status,
            state = if (task.status == "done") FarmWorkflowState.RECORDED else if (task.status == "open" && task.dueEpochDay < today) FarmWorkflowState.ATTENTION else if (task.status == "open") FarmWorkflowState.NOT_RECORDED else FarmWorkflowState.UNKNOWN),
    ), modifier = Modifier.testTag("task-state-workflow:" + task.id))
}

@Composable
internal fun WithdrawalWorkflow(window: HealthWithdrawalView, treatment: HealthTreatmentView?, today: LocalDate, recordsContent: @Composable () -> Unit) {
    val active = HealthRecords.isActive(window, today)
    FarmWorkflowTimeline("Withdrawal timeline", listOf(
        FarmWorkflowStep("Source treatment", treatment?.let { HealthRecords.day(it.occurredAtEpochMillis, ZoneId.systemDefault()).toString() } ?: "Treatment not on this device", treatment?.productName,
            if (treatment == null) FarmWorkflowState.UNKNOWN else FarmWorkflowState.RECORDED),
        FarmWorkflowStep("Current window", if (active) "Active ${HealthRecords.kindLabel(window.windowKind).lowercase()} withdrawal" else "Recorded end date has passed",
            state = if (active) FarmWorkflowState.ATTENTION else FarmWorkflowState.CURRENT),
        FarmWorkflowStep("Scheduled end", LocalDate.ofEpochDay(window.endsEpochDay).toString(), state = FarmWorkflowState.PLANNED),
    ), modifier = Modifier.testTag("health-withdrawal-workflow:" + window.id), recordsContent = recordsContent)
}
