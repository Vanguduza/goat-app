package com.farmos.feature.ops

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmCanvas
import com.farmos.core.design.AnimalFarmModuleHeader
import com.farmos.core.design.FarmIllustratedSectionSurface
import com.farmos.core.design.FosDimens
import java.time.LocalDate

/** UI projection only; authoritative task state remains in Room/Supabase. */
data class TaskUiRow(
    val id: String,
    val title: String,
    val moduleCode: String,
    val taskCode: String,
    val dueEpochDay: Long,
    val status: String,
)

private enum class TaskTab { TODAY, UPCOMING, COMPLETED }

/** FOS-TASK-001 / 002 / 003 / 004 — shared Farm OS task reference family. */
@Composable
fun TasksBoardScreen(
    rows: List<TaskUiRow>,
    busy: Boolean,
    error: String?,
    onCreate: (title: String, module: String, code: String, due: String) -> Unit,
    onComplete: (taskId: String) -> Unit,
    onBack: () -> Unit,
) {
    var tab by remember { mutableStateOf(TaskTab.TODAY) }
    var showCreate by remember { mutableStateOf(false) }
    val today = LocalDate.now().toEpochDay()
    val visible = when (tab) {
        TaskTab.TODAY -> rows.filter { it.status == "open" && it.dueEpochDay <= today }
        TaskTab.UPCOMING -> rows.filter { it.status == "open" && it.dueEpochDay > today }
        TaskTab.COMPLETED -> rows.filter { it.status == "done" }
    }

    AnimalFarmCanvas {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(FosDimens.IntraCardGap),
        ) {
            AnimalFarmModuleHeader(
                title = "Tasks",
                subtitle = "${rows.count { it.status == "open" }} open task(s)",
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TaskTab.entries.forEach { option ->
                    TextButton(onClick = { tab = option }, modifier = Modifier.weight(1f)) {
                        Text(taskTabLabel(option, selected = tab == option))
                    }
                }
            }

            if (visible.isEmpty()) {
                FarmIllustratedSectionSurface {
                    Text(emptyTaskMessage(tab), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(emptyTaskHint(tab), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                visible.forEach { task ->
                    TaskCard(task = task, today = today, busy = busy, onComplete = onComplete)
                }
            }

            FarmIllustratedSectionSurface {
                Text("Quick action", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Create a farm task. Species-specific capture stays inside its native module.")
                Button(onClick = { showCreate = !showCreate }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showCreate) "Close task form" else "Add task")
                }
            }

            if (showCreate) {
                CreateTaskCard(busy = busy, onCreate = onCreate) { showCreate = false }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onBack, enabled = !busy) { Text("Farm home") }
        }
    }
}

@Composable
private fun TaskCard(
    task: TaskUiRow,
    today: Long,
    busy: Boolean,
    onComplete: (String) -> Unit,
) {
    val overdue = task.status == "open" && task.dueEpochDay < today
    FarmIllustratedSectionSurface(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${task.moduleCode} · ${task.taskCode}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    when {
                        task.status == "done" -> "Completed"
                        overdue -> "Overdue · ${LocalDate.ofEpochDay(task.dueEpochDay)}"
                        task.dueEpochDay == today -> "Due today"
                        else -> "Due ${LocalDate.ofEpochDay(task.dueEpochDay)}"
                    },
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (task.status == "open") {
                TextButton(onClick = { onComplete(task.id) }, enabled = !busy) { Text("Done") }
            }
        }
    }
}

@Composable
private fun CreateTaskCard(
    busy: Boolean,
    onCreate: (title: String, module: String, code: String, due: String) -> Unit,
    onCreated: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var module by remember { mutableStateOf("goat") }
    var code by remember { mutableStateOf("CHECK") }
    var due by remember { mutableStateOf(LocalDate.now().toString()) }
    FarmIllustratedSectionSurface {
        Text("Add task", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(module, { module = it }, label = { Text("Module") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(code, { code = it }, label = { Text("Task code") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(due, { due = it }, label = { Text("Due date") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        Button(
            onClick = {
                onCreate(title, module, code, due)
                onCreated()
            },
            enabled = !busy && title.isNotBlank() && module.isNotBlank() && code.isNotBlank() && runCatching { LocalDate.parse(due) }.isSuccess,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create task") }
    }
}

private fun taskTabLabel(tab: TaskTab, selected: Boolean): String {
    val label = when (tab) {
        TaskTab.TODAY -> "Today"
        TaskTab.UPCOMING -> "Upcoming"
        TaskTab.COMPLETED -> "Completed"
    }
    return if (selected) "$label · selected" else label
}

private fun emptyTaskMessage(tab: TaskTab): String = when (tab) {
    TaskTab.TODAY -> "No tasks due today"
    TaskTab.UPCOMING -> "No upcoming tasks"
    TaskTab.COMPLETED -> "No completed tasks yet"
}

private fun emptyTaskHint(tab: TaskTab): String = when (tab) {
    TaskTab.TODAY -> "Your open work for today will appear here."
    TaskTab.UPCOMING -> "Scheduled farm work will appear here."
    TaskTab.COMPLETED -> "Finished work remains visible for farm history."
}
