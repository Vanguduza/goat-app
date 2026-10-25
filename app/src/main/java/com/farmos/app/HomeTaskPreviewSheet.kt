package com.farmos.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.farmos.core.design.*
import com.farmos.domain.ops.inferTaskSpeciesFamily
import com.farmos.feature.goat.GoatEntryPage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Reference-style preview of an existing farm task. This component never writes task state. */
@Composable
internal fun HomeTaskPreviewSheet(taskId: String, summary: FarmHomeSummary, onDismiss: () -> Unit,
    onOpen: (FarmDestination) -> Unit, onResources: () -> Unit) {
    val task = summary.tasks.singleOrNull { it.id == taskId }
    AnimalFarmSelectionSheet(task?.title ?: "Task review", onDismiss) {
        Column(Modifier.fillMaxWidth().testTag("home-task-sheet:$taskId"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when {
                summary.loading -> AnimalFarmEmptyState("Reading this farm’s task")
                summary.loadError != null -> AnimalFarmEmptyState("Task details are not available. Return to farm work and retry.")
                task == null -> AnimalFarmEmptyState("This task is no longer in the current farm’s work list.")
                else -> {
                    val colors = AnimalFarmTheme.colors
                    val family = animalFarmFamilyFromKey(task.subjectSpecies ?: inferTaskSpeciesFamily(task.moduleCode))
                    val due = LocalDate.ofEpochDay(task.dueEpochDay).format(DateTimeFormatter.ofPattern("d MMM", Locale.UK))
                    Surface(shape = RoundedCornerShape(18.dp), color = colors.softSurface, contentColor = colors.ink) {
                        Row(Modifier.fillMaxWidth().padding(15.dp), horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (family != null) AnimalFarmFamilyPortrait(family, Modifier.size(60.dp, 66.dp),
                                contentDescription = "${animalFarmFamilyLabel(family)} family illustration; not an individual photograph", cornerRadius = 14.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(task.subjectLabel?.takeIf { it.isNotBlank() } ?: family?.let(::animalFarmFamilyLabel) ?: "Farm work", style = AnimalFarmHtmlType.sectionTitle)
                                Text("Due $due", style = AnimalFarmHtmlType.caption, color = colors.mutedInk)
                                homeTaskProfileDestination(task)?.let { profile ->
                                    FarmActionButton({ onOpen(profile) }, modifier = Modifier.testTag("home-task-preview-profile")) {
                                        Text("View profile"); Spacer(Modifier.width(6.dp)); FarmHtmlIcon("arrow-up-right", null)
                                    }
                                }
                            }
                        }
                    }
                    val state = when (task.status) { "open" -> "Open"; "done" -> "Completed"; else -> task.status }
                    if (LocalDensity.current.fontScale >= 1.5f) {
                        HomeTaskPreviewFact("Due date", due, Modifier.fillMaxWidth())
                        HomeTaskPreviewFact("Recorded status", state, Modifier.fillMaxWidth())
                    } else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeTaskPreviewFact("Due date", due, Modifier.weight(1f).fillMaxHeight())
                        HomeTaskPreviewFact("Recorded status", state, Modifier.weight(1f).fillMaxHeight())
                    }
                    FarmActionButton(onResources, Modifier.fillMaxWidth().testTag("home-task-preview-resources")) {
                        FarmHtmlIcon("package", null); Spacer(Modifier.width(8.dp)); Text("Resources")
                    }
                    FarmPrimaryButton({ onOpen(FarmDestination.Task(task.id)) }, Modifier.fillMaxWidth().testTag("home-task-preview-open")) {
                        Text("Open task details"); Spacer(Modifier.width(8.dp)); FarmHtmlIcon("arrow-right", null)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTaskPreviewFact(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(13.dp), color = AnimalFarmTheme.colors.softSurface, contentColor = AnimalFarmTheme.colors.ink) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = AnimalFarmHtmlType.caption, color = AnimalFarmTheme.colors.mutedInk)
            Text(value, style = AnimalFarmHtmlType.sheetTitle)
        }
    }
}

/** Exact existing profile routes only. Rabbit sex and flock identity are never inferred. */
internal fun homeTaskProfileDestination(task: HomeTaskRow): FarmDestination? {
    val id = task.animalId?.takeIf { it.isNotBlank() } ?: return null
    return when (animalFarmFamilyFromKey(task.subjectSpecies ?: inferTaskSpeciesFamily(task.moduleCode))) {
        AnimalFarmFamily.GOAT -> FarmDestination.Goat(GoatEntryPage.PROFILE, id)
        AnimalFarmFamily.SHEEP -> FarmDestination.AnimalProfile(AnimalProfileKind.SHEEP, id)
        AnimalFarmFamily.CATTLE -> FarmDestination.AnimalProfile(AnimalProfileKind.CATTLE, id)
        else -> null
    }
}
