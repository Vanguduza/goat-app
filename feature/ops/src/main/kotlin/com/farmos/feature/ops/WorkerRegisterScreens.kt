package com.farmos.feature.ops

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmPermissionExplanation
import com.farmos.core.design.FarmVisualClass

/** A worker in the farm's register (D-016, resolution R1). [linkedAccount] names a local account linked to them. */
data class WorkerView(val workerId: String, val name: String, val active: Boolean, val linkedAccount: String?)

private const val MAX_WORKER_NAME = 80

private fun nameValid(name: String) = name.isNotBlank() && name.trim().length <= MAX_WORKER_NAME

/**
 * FOS-LABOUR-002 and FOS-LABOUR-003 — the worker register: people who work on the farm, with or without a
 * login. Supervisors and management (MANAGE_WORKERS) add, rename and deactivate workers; nobody deletes one.
 */
@Composable
fun WorkerRegisterScreens(
    workers: List<WorkerView>,
    canManage: Boolean,
    busy: Boolean,
    error: String?,
    onAdd: (name: String) -> Unit,
    onRename: (workerId: String, name: String) -> Unit,
    onSetActive: (workerId: String, active: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = workers.firstOrNull { it.workerId == selectedId }
    if (selected != null) {
        WorkerDetail(selected, canManage, busy, error, onRename, onSetActive) { selectedId = null }
        return
    }
    var name by remember { mutableStateOf("") }
    FarmOperationalPage("FOS-LABOUR-002", "Workers", "People who work on this farm, with or without a login.", FarmVisualClass.I3, onBack, backLabel = "Labour") {
        if (canManage) {
            FarmOperationalSection("Add a worker") {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("worker-add-name"))
                Button(
                    onClick = {
                        onAdd(name.trim())
                        name = ""
                    },
                    enabled = !busy && nameValid(name),
                    modifier = Modifier.fillMaxWidth().testTag("worker-add"),
                ) { Text("Add worker") }
            }
        } else {
            FarmPermissionExplanation("Workers are managed by supervisors and farm management", "Your role can see the register but not change it.")
        }
        if (workers.isEmpty()) {
            AnimalFarmEmptyState("No workers recorded on this farm.")
        } else {
            FarmOperationalSection("Workers · ${workers.count { it.active }} active") {
                workers.forEachIndexed { index, worker ->
                    if (index > 0) HorizontalDivider()
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                            .clickable(role = Role.Button) { selectedId = worker.workerId }
                            .padding(vertical = 6.dp).testTag("worker:${worker.workerId}"),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(worker.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            listOfNotNull(if (worker.active) "Active" else "Inactive", worker.linkedAccount?.let { "signs in as $it" } ?: "no login").joinToString(" · "),
                            color = AnimalFarmTheme.colors.mutedInk,
                        )
                    }
                }
            }
        }
        error?.let { Text(it, color = AnimalFarmTheme.colors.critical) }
    }
}

@Composable
private fun WorkerDetail(
    worker: WorkerView,
    canManage: Boolean,
    busy: Boolean,
    error: String?,
    onRename: (String, String) -> Unit,
    onSetActive: (String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var name by remember(worker.workerId, worker.name) { mutableStateOf(worker.name) }
    FarmOperationalPage("FOS-LABOUR-003", worker.name, if (worker.active) "Active worker" else "Inactive worker", FarmVisualClass.I3, onBack, backLabel = "Workers") {
        FarmOperationalSection("Login") {
            Text(worker.linkedAccount?.let { "Signs in as $it" } ?: "No login. Work can still be assigned and recorded for this worker.")
        }
        if (canManage) {
            FarmOperationalSection("Name") {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("worker-rename-name"))
                TextButton(
                    onClick = { onRename(worker.workerId, name.trim()) },
                    enabled = !busy && nameValid(name) && name.trim() != worker.name,
                    modifier = Modifier.testTag("worker-rename"),
                ) { Text("Rename") }
            }
            FarmOperationalSection("Status") {
                Text(if (worker.active) "Inactive workers keep their history but cannot be given new work." else "Reactivating allows new work to be assigned again.")
                TextButton(onClick = { onSetActive(worker.workerId, !worker.active) }, enabled = !busy, modifier = Modifier.testTag("worker-set-active")) {
                    Text(if (worker.active) "Make inactive" else "Make active")
                }
            }
        }
        error?.let { Text(it, color = AnimalFarmTheme.colors.critical) }
    }
}
