package com.farmos.feature.ops

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.FarmEntitySelector
import com.farmos.core.design.FarmMetricStrip
import com.farmos.core.design.FarmMetricValue
import com.farmos.core.design.FarmModuleTools
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmPrimaryButton
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.design.FarmToolAction
import com.farmos.core.design.FarmToolGroup
import com.farmos.core.design.FarmVisualClass
import com.farmos.core.design.FarmWorkflowState
import com.farmos.core.design.FarmWorkflowStep
import com.farmos.core.design.FarmWorkflowTimeline
import java.time.LocalDate

/** Recorded counts, not elapsed days, advance incubation evidence. */
internal fun poultryHatchSteps(batch: PoultryHatchView): List<FarmWorkflowStep> = listOf(
    FarmWorkflowStep("Eggs set", "${batch.eggsSet} eggs", LocalDate.ofEpochDay(batch.setEpochDay).toString(), FarmWorkflowState.RECORDED),
    FarmWorkflowStep(
        "Candling",
        if (batch.fertile == null && batch.infertile == null && batch.midDead == null) "No result recorded"
        else "${batch.fertile ?: "—"} fertile · ${batch.infertile ?: "—"} infertile · ${batch.midDead ?: "—"} mid-dead",
        state = if (batch.fertile != null || batch.infertile != null || batch.midDead != null) FarmWorkflowState.RECORDED else FarmWorkflowState.NOT_RECORDED,
    ),
    FarmWorkflowStep(
        "Hatch result", batch.hatched?.let { "$it hatched · ${batch.culls ?: "—"} culled" } ?: "No result recorded",
        state = if (batch.hatched != null) FarmWorkflowState.RECORDED else FarmWorkflowState.NOT_RECORDED,
    ),
    FarmWorkflowStep(
        "Flock placement", batch.placementGroupId?.takeIf(String::isNotBlank)?.let { "Flock $it" } ?: "No placement recorded",
        state = if (batch.placementGroupId.isNullOrBlank()) FarmWorkflowState.NOT_RECORDED else FarmWorkflowState.RECORDED,
    ),
)

@Composable
internal fun PoultryHatchProgress(batch: PoultryHatchView, recordsContent: (@Composable () -> Unit)? = null) {
    val expected = LocalDate.ofEpochDay(batch.setEpochDay).plusDays(batch.incubationDays.toLong())
    FarmWorkflowTimeline(
        title = "Incubation progress",
        steps = poultryHatchSteps(batch),
        modifier = Modifier.testTag("poultry-hatch-workflow:" + batch.id),
        caption = "Expected hatch $expected · ${batch.incubationDays}-day recorded incubation period. Expected dates remain plans.",
        recordsContent = recordsContent,
    )
}

/** FOS-POULTRY-017. Selectable incubation board; existing capture commands retain authority. */
@Composable
internal fun PoultryHatcheryScreen(records: PoultryRecords, error: String?, onOpen: (PoultryPage) -> Unit, onBack: () -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = records.hatches.firstOrNull { it.id == selectedId } ?: records.hatches.firstOrNull()
    FarmOperationalPage("FOS-POULTRY-017", "Hatchery", "", FarmVisualClass.I2, onBack, backLabel = "Poultry") {
        if (records.hatches.isEmpty()) AnimalFarmEmptyState("No hatch batches yet")
        else {
            FarmMetricStrip(listOf(
                FarmMetricValue("Set", records.hatches.count { it.status == "set" }.toString()),
                FarmMetricValue("Candled", records.hatches.count { it.status == "candled" }.toString()),
                FarmMetricValue("Hatch results", records.hatches.count { it.hatched != null }.toString()),
                FarmMetricValue("Placed", records.hatches.count { !it.placementGroupId.isNullOrBlank() }.toString()),
            ))
            FarmEntitySelector(
                atomTag = "poultry-hatch-selector", title = "Incubation batch",
                options = records.hatches.map { FarmSelectorOption(it.id, "${it.poultryKind} · ${it.eggsSet} eggs", "Set ${LocalDate.ofEpochDay(it.setEpochDay)} · ${it.status}") },
                selectedId = selected?.id, onSelect = { selectedId = it }, emptyText = "No hatch batches yet",
            )
            selected?.let { PoultryHatchProgress(it) }
        }
        FarmPrimaryButton(onClick = { onOpen(PoultryPage.SET_EGGS) }, modifier = Modifier.fillMaxWidth()) { Text("Set eggs") }
        FarmModuleTools(listOf(FarmToolGroup("Record and review", listOf(
            FarmToolAction("Candling", { onOpen(PoultryPage.CANDLING) }),
            FarmToolAction("Hatch result", { onOpen(PoultryPage.HATCH_RESULT) }),
            FarmToolAction("Place flock", { onOpen(PoultryPage.PLACEMENT) }),
            FarmToolAction("Incubation batches", { onOpen(PoultryPage.INCUBATION_BATCH) }),
        ))), title = "Hatchery tools")
        error?.let { Text(it, color = AnimalFarmTheme.colors.critical) }
    }
}
