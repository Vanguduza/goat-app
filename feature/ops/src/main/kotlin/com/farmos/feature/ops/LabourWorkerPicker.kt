package com.farmos.feature.ops

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    if (workers.isEmpty()) {
        Text("No workers are registered yet. Type who did the work, or add workers under Workers.", color = AnimalFarmTheme.colors.mutedInk)
        return
    }
    Text("Worker", fontWeight = FontWeight.SemiBold)
    workers.forEach { worker ->
        val chosen = worker.workerId == selectedWorkerId
        TextButton(
            onClick = { onSelect(worker.workerId) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = AnimalFarmTheme.minimumTouchDp.dp).semantics { selected = chosen }.testTag("labour-worker:${worker.workerId}"),
        ) { Text(if (chosen) "${worker.name} · selected" else worker.name) }
    }
}
