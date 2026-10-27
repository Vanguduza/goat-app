package com.farmos.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** A planned date never becomes RECORDED just because the day has passed. */
enum class FarmWorkflowState { PLANNED, RECORDED, CURRENT, ATTENTION, NOT_RECORDED, UNKNOWN }

data class FarmWorkflowStep(
    val label: String,
    val value: String,
    val detail: String? = null,
    val state: FarmWorkflowState = FarmWorkflowState.PLANNED,
)

/** P08/P09: each caller provides actual records, explicitly named plans or observed state. */
@Composable
fun FarmWorkflowTimeline(
    title: String,
    steps: List<FarmWorkflowStep>,
    modifier: Modifier = Modifier,
    caption: String? = null,
    recordsContent: (@Composable () -> Unit)? = null,
    /** False only when the parent already supplies an equivalent full-record view. */
    showViewToggle: Boolean = true,
) {
    var showRecords by rememberSaveable(title) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FarmOperationalSection(title) {
            if (showViewToggle) FarmSectionTabs(listOf("Timeline", "Records"), if (showRecords) "Records" else "Timeline", { showRecords = it == "Records" })
            if (steps.isEmpty()) Text("No records yet", color = AnimalFarmTheme.colors.mutedInk)
            if (showRecords && recordsContent != null) recordsContent() else steps.forEachIndexed { index, step ->
                if (showRecords) {
                    if (index > 0) HorizontalDivider(color = AnimalFarmTheme.colors.divider)
                    FarmWorkflowRecord(step)
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val colors = AnimalFarmTheme.colors
                            val fill = when (step.state) {
                                FarmWorkflowState.RECORDED -> colors.primary
                                FarmWorkflowState.CURRENT -> colors.lime
                                FarmWorkflowState.ATTENTION -> colors.warningSurface
                                FarmWorkflowState.PLANNED, FarmWorkflowState.NOT_RECORDED, FarmWorkflowState.UNKNOWN -> colors.surface
                            }
                            val ink = when (step.state) {
                                FarmWorkflowState.RECORDED -> colors.onPrimary
                                FarmWorkflowState.CURRENT -> colors.onLime
                                FarmWorkflowState.ATTENTION -> colors.onWarning
                                FarmWorkflowState.PLANNED, FarmWorkflowState.NOT_RECORDED, FarmWorkflowState.UNKNOWN -> colors.mutedInk
                            }
                            Surface(Modifier.size(32.dp), shape = CircleShape, color = fill, border = BorderStroke(FosDimens.Hairline, colors.divider)) {
                                Box(contentAlignment = Alignment.Center) {
                                    when (step.state) {
                                        FarmWorkflowState.RECORDED -> Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = ink)
                                        FarmWorkflowState.ATTENTION -> Icon(Icons.Rounded.PriorityHigh, null, Modifier.size(18.dp), tint = ink)
                                        else -> Text((index + 1).toString(), color = ink, style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                            if (index < steps.lastIndex) Surface(Modifier.padding(top = 4.dp).size(width = 2.dp, height = 32.dp), color = colors.divider) {}
                        }
                        FarmWorkflowRecord(step, Modifier.weight(1f))
                    }
                }
            }
            caption?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AnimalFarmTheme.colors.mutedInk) }
        }
    }
}

@Composable
private fun FarmWorkflowRecord(step: FarmWorkflowStep, modifier: Modifier = Modifier) {
    val stateLabel = when (step.state) {
        FarmWorkflowState.PLANNED -> "Planned"
        FarmWorkflowState.NOT_RECORDED -> "Not recorded"
        FarmWorkflowState.UNKNOWN -> "Not available"
        FarmWorkflowState.RECORDED -> "Recorded"
        FarmWorkflowState.CURRENT -> "Current"
        FarmWorkflowState.ATTENTION -> "Needs attention"
    }
    Column(modifier.semantics { stateDescription = stateLabel }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(step.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(step.value, style = MaterialTheme.typography.bodyLarge)
        Text(stateLabel, style = MaterialTheme.typography.labelMedium, color = AnimalFarmTheme.colors.mutedInk)
        step.detail?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = AnimalFarmTheme.colors.mutedInk)
        }
    }
}
