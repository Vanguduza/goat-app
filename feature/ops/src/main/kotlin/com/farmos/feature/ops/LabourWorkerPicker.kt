package com.farmos.feature.ops

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.farmos.core.design.FarmSelectionAtoms
import com.farmos.core.design.FarmOutlinedTextField

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import com.farmos.core.design.FarmActionButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmTheme

/** An active worker in the register, offered on labour capture (resolution R1). */
data class LabourWorkerOption(val workerId: String, val name: String)

/**
 * The worker who did the work, chosen from every active worker in the register. Without registered
 * workers the capture keeps a typed label, so work can still be recorded before the register is set up.
 */
@Composable
fun LabourWorkerPicker(workers: List<LabourWorkerOption>, selectedWorkerId: String?, enabled: Boolean, onSelect: (String) -> Unit) {
    var query by rememberSaveable(workers.map { it.workerId }) { mutableStateOf("") }
    var visibleLimit by rememberSaveable(query) { mutableStateOf(50) }
    val matches = workers.filter { it.name.contains(query.trim(), ignoreCase = true) || it.workerId == query.trim() }
    val selectedWorker = workers.firstOrNull { it.workerId == selectedWorkerId }
    Column(Modifier.fillMaxWidth().testTag(FarmSelectionAtoms.WORKER_SELECTOR)) {
        Text("Worker", fontWeight = FontWeight.SemiBold)
        if (workers.isEmpty()) {
            Text("No workers are registered yet. Type who did the work, or add workers under Workers.", color = AnimalFarmTheme.colors.mutedInk)
            return@Column
        }
        if (workers.size > 6) FarmOutlinedTextField(query, { query = it }, label = { Text("Find worker") },
            enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("labour-worker-query"))
        val shown = matches.take(visibleLimit)
        val rows = if (selectedWorker != null && shown.none { it.workerId == selectedWorker.workerId }) listOf(selectedWorker) + shown else shown
        rows.forEach { worker ->
            val chosen = worker.workerId == selectedWorkerId
            TextButton(
                onClick = { onSelect(worker.workerId) }, enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                    .semantics { selected = chosen }.testTag("labour-worker:${worker.workerId}"),
            ) { Text(if (chosen) "${worker.name} · selected" else worker.name) }
        }
        if (matches.isEmpty()) Text("No matching workers", color = AnimalFarmTheme.colors.mutedInk)
        if (matches.size > visibleLimit) {
            Text("Showing $visibleLimit of ${matches.size} matching workers", color = AnimalFarmTheme.colors.mutedInk)
            TextButton(onClick = { visibleLimit += 50 }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Show more workers") }
        }
    }
}
