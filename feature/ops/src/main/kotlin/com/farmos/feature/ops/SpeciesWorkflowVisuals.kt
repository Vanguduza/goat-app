package com.farmos.feature.ops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.farmos.core.design.FarmActionButton
import com.farmos.core.design.FarmEntitySelector
import com.farmos.core.design.FarmMetricStrip
import com.farmos.core.design.FarmMetricValue
import com.farmos.core.design.FarmSectionTabs
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.design.FarmWorkflowState
import com.farmos.core.design.FarmWorkflowStep
import com.farmos.core.design.FarmWorkflowTimeline
import com.farmos.core.design.FosDimens
import java.time.LocalDate

private fun workflowDate(day: Long) = LocalDate.ofEpochDay(day).toString()

/** Recorded ram-in is evidence; supplied dates remain predictions after they pass. */
fun sheepMobLambingSteps(mob: SheepMobLambingView): List<FarmWorkflowStep> = listOf(
    FarmWorkflowStep("Ram in", workflowDate(mob.ramInEpochDay), "${mob.headCount} head recorded", FarmWorkflowState.RECORDED),
    FarmWorkflowStep("Earliest lambing", workflowDate(mob.earliestEpochDay), "Estimated window opening", FarmWorkflowState.PLANNED),
    FarmWorkflowStep("Typical lambing", workflowDate(mob.typicalEpochDay), "Expected date, not a recorded birth", FarmWorkflowState.PLANNED),
)

/** No birth, marking or weaning event is inferred from age or another stage. */
fun sheepLambDevelopmentSteps(profile: SheepLambProfile): List<FarmWorkflowStep> = listOf(
    "Birth" to profile.birthEpochDay,
    "Marking" to profile.markingEpochDay,
    "Weaning" to profile.weaningEpochDay,
).map { (label, day) ->
    FarmWorkflowStep(label, day?.let(::workflowDate) ?: "Not recorded", state = if (day == null) FarmWorkflowState.NOT_RECORDED else FarmWorkflowState.RECORDED)
}

/** Uses supplied dates and provenance; does not recalculate biology or record a calving. */
fun cattleCalvingWorkflowSteps(cow: CattleCalvingDueView): List<FarmWorkflowStep> = listOf(
    FarmWorkflowStep("Service", workflowDate(cow.serviceEpochDay), cow.method, FarmWorkflowState.RECORDED),
    FarmWorkflowStep("Pregnancy check", cow.pdResult ?: "No pregnancy check recorded",
        state = if (cow.pdResult == null) FarmWorkflowState.NOT_RECORDED else FarmWorkflowState.RECORDED),
    FarmWorkflowStep("Expected calving", workflowDate(cow.typicalEpochDay),
        if (cow.source == CattleDueSource.STORED) "Expected date recorded at dry-off"
        else "Predicted window ${workflowDate(cow.earliestEpochDay)} to ${workflowDate(cow.latestEpochDay)}",
        FarmWorkflowState.PLANNED),
)

/** P08: subject and process use separate panes when space permits. */
@Composable
private fun SpeciesScheduleLayout(subject: @Composable () -> Unit, workflow: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 600.dp && LocalDensity.current.fontScale < 1.5f) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FosDimens.SectionGap)) {
                Column(Modifier.weight(0.4f), verticalArrangement = Arrangement.spacedBy(FosDimens.IntraCardGap)) { subject() }
                Column(Modifier.weight(0.6f)) { workflow() }
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FosDimens.SectionGap)) { subject(); workflow() }
        }
    }
}

@Composable
private fun WorkflowSubjectChoice(label: String, tag: String, options: List<FarmSelectorOption>, selected: String, onSelect: (String) -> Unit) {
    var choosing by rememberSaveable(tag) { mutableStateOf(false) }
    Text(options.firstOrNull { it.id == selected }?.label.orEmpty(), style = MaterialTheme.typography.titleLarge)
    if (options.size > 1) {
        FarmActionButton(onClick = { choosing = !choosing }, modifier = Modifier.fillMaxWidth()) {
            Text(if (choosing) "Close chooser" else "Choose $label")
        }
        if (choosing) FarmEntitySelector(tag, label.replaceFirstChar { it.uppercase() }, options, selected,
            { onSelect(it); choosing = false }, "No records")
    }
}

/** FOS-SHEEP-013. Selected mob's plan; the complete mob/ewe record view remains available. */
@Composable
fun SheepLambingWorkflow(due: SheepLambingDue, today: LocalDate, recordsContent: @Composable () -> Unit) {
    var section by rememberSaveable { mutableStateOf("Schedule") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = due.mobs.firstOrNull { it.groupId == selectedId } ?: due.mobs.minByOrNull { it.typicalEpochDay }
    FarmSectionTabs(listOf("Schedule", "Records"), section, { section = it })
    if (section == "Records" || selected == null) { recordsContent(); return }
    FarmMetricStrip(listOf(FarmMetricValue("Mobs scheduled", due.mobs.size.toString()), FarmMetricValue("Scanned in lamb", due.ewes.size.toString())))
    SpeciesScheduleLayout(subject = {
        WorkflowSubjectChoice("mob", "sheep-lambing-selector", due.mobs.map { FarmSelectorOption(it.groupId, it.groupName, "${it.headCount} head recorded") }, selected.groupId, { selectedId = it })
        Text(if (today.toEpochDay() < selected.earliestEpochDay) "Window opens in ${selected.earliestEpochDay - today.toEpochDay()} days" else "Expected lambing window open", style = MaterialTheme.typography.bodyMedium)
    }, workflow = {
        FarmWorkflowTimeline("Lambing plan", sheepMobLambingSteps(selected), Modifier.testTag("sheep-lambing-workflow:" + selected.groupId),
            caption = "${due.typicalDays}-day farm gestation setting. No ram-out is recorded, so there is no final lambing date.", showViewToggle = false)
    })
}

/** FOS-CATTLE-014. Stored dates remain distinct from predictions, never elapsed-time completion. */
@Composable
fun CattleCalvingWorkflow(due: CattleCalvingDue, today: LocalDate, recordsContent: @Composable () -> Unit) {
    var section by rememberSaveable { mutableStateOf("Schedule") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = due.rows.firstOrNull { it.animalId == selectedId } ?: due.rows.minByOrNull { it.typicalEpochDay }
    FarmSectionTabs(listOf("Schedule", "Records"), section, { section = it })
    if (section == "Records" || selected == null) { recordsContent(); return }
    FarmMetricStrip(listOf(FarmMetricValue("Expected cows", due.rows.size.toString()), FarmMetricValue("No pregnancy check", due.rows.count { it.pdResult == null }.toString())))
    SpeciesScheduleLayout(subject = {
        WorkflowSubjectChoice("cow", "cattle-calving-selector", due.rows.map { FarmSelectorOption(it.animalId, listOfNotNull(it.name, it.tag).joinToString(" · "), workflowDate(it.typicalEpochDay)) }, selected.animalId, { selectedId = it })
        Text(cattleDueWindow(selected, today.toEpochDay()).label, style = MaterialTheme.typography.bodyMedium)
    }, workflow = {
        FarmWorkflowTimeline("Calving plan", cattleCalvingWorkflowSteps(selected), Modifier.testTag("cattle-calving-workflow:" + selected.animalId),
            caption = "Expected dates are plans. A birth must be recorded separately.", showViewToggle = false)
    })
}

/** FOS-SHEEP-015. Actual early-life events with the complete recorded detail available. */
@Composable
fun SheepLambDevelopment(profile: SheepLambProfile, recordsContent: @Composable () -> Unit) {
    FarmWorkflowTimeline("Early life", sheepLambDevelopmentSteps(profile), Modifier.testTag("sheep-lamb-development:" + profile.lambId), recordsContent = recordsContent)
}
