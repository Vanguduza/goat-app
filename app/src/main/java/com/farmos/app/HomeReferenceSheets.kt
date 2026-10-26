package com.farmos.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.farmos.core.design.*
import com.farmos.feature.goat.GoatEntryPage
import com.farmos.feature.ops.HealthEntryPage
import com.farmos.feature.ops.TaskEntryPage

internal enum class HomeReferenceSheet { FAMILIES, MODULES, RECORD, RESOURCES, BREEDING, GUIDES }

@Composable
internal fun HomeReferenceModuleGrid(onOpen: (FarmDestination) -> Unit, onSheet: (HomeReferenceSheet) -> Unit) {
    val actions = listOf<Triple<String, String, () -> Unit>>(
        Triple("Animals", "paw-print", { onSheet(HomeReferenceSheet.FAMILIES) }),
        Triple("Team", "users", { onOpen(FarmDestination.Module(FarmModule.LABOUR)) }),
        Triple("Breeding", "heart", { onSheet(HomeReferenceSheet.BREEDING) }),
        Triple("Finance", "wallet", { onOpen(FarmDestination.Module(FarmModule.MONEY)) }),
        Triple("Inventory", "package", { onOpen(FarmDestination.Module(FarmModule.INVENTORY)) }),
        Triple("Reports", "chart-no-axes-combined", { onOpen(FarmDestination.Module(FarmModule.REPORTS)) }),
    )
    val columns = if (LocalDensity.current.fontScale >= 1.75f) 2 else 3
    Column(verticalArrangement = Arrangement.spacedBy(AnimalFarmHomeMetrics.moduleGap)) {
        actions.chunked(columns).forEach { items ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(AnimalFarmHomeMetrics.moduleGap)) {
                items.forEach { (label, icon, action) -> AnimalFarmModuleTile(label, icon, action, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

@Composable
internal fun HomeReferenceQuickRow(onOpen: (FarmDestination) -> Unit, onResources: () -> Unit) {
    var weightPicker by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if(weightPicker) WeightSpeciesSheet({weightPicker=false}) { species -> weightPicker=false;onOpen(FarmDestination.Weight(species)) }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimalFarmQuickCaptureTile("Weight", "scale", { weightPicker = true }, Modifier.weight(1f).fillMaxHeight())
        AnimalFarmQuickCaptureTile("Finding", "message-square-plus", { onOpen(FarmDestination.Health(HealthEntryPage.RECORD_OBSERVATION)) }, Modifier.weight(1f).fillMaxHeight())
        AnimalFarmQuickCaptureTile("Resources", "boxes", onResources, Modifier.weight(1f).fillMaxHeight())
    }
}

/** Exact native owning destinations; the browser's prototype confirms never create farm records. */
@Composable
internal fun HomeReferenceSheets(sheet: HomeReferenceSheet?, onDismiss: () -> Unit, onSheet: (HomeReferenceSheet) -> Unit,
    onOpen: (FarmDestination) -> Unit, onMore: () -> Unit, summary: FarmHomeSummary, worker: Boolean) {
    if (sheet == null) return
    val title = when (sheet) {
        HomeReferenceSheet.FAMILIES -> "Animal families"
        HomeReferenceSheet.MODULES -> "Farm modules"
        HomeReferenceSheet.RECORD -> "Quick record"
        HomeReferenceSheet.RESOURCES -> "Resources"
        HomeReferenceSheet.BREEDING -> "Breeding"
        HomeReferenceSheet.GUIDES -> "Guides"
    }
    fun navigate(destination: FarmDestination) { onDismiss(); onOpen(destination) }
    AnimalFarmSelectionSheet(title, onDismiss) {
        when (sheet) {
            HomeReferenceSheet.FAMILIES -> Column(Modifier.testTag("farm-screen:FOS-HOME-002"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val families = listOf(
                    AnimalFarmFamily.GOAT to FarmDestination.Goat(), AnimalFarmFamily.RABBIT to FarmDestination.Module(FarmModule.RABBIT),
                    AnimalFarmFamily.SHEEP to FarmDestination.Module(FarmModule.SHEEP), AnimalFarmFamily.CATTLE to FarmDestination.Module(FarmModule.CATTLE),
                    AnimalFarmFamily.POULTRY to FarmDestination.Module(FarmModule.POULTRY))
                val columns = if (LocalDensity.current.fontScale >= 1.75f) 2 else 3
                families.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (family, target) ->
                            AnimalFarmFamilyLauncher(family, "", { navigate(target) },
                                Modifier.weight(1f).fillMaxHeight().testTag("home-family:${animalFarmFamilyLabel(family)}"))
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            HomeReferenceSheet.MODULES -> {
                HomeReferenceModuleGrid({ navigate(it) }, onSheet)
                AnimalFarmQuickAction("Search farm", { navigate(FarmDestination.Search) })
                AnimalFarmQuickAction("All farm tools", { onDismiss(); onMore() })
            }
            HomeReferenceSheet.RECORD -> {
                HomeReferenceQuickRow({ navigate(it) }, { onSheet(HomeReferenceSheet.RESOURCES) })
                workerHomeQuickActions().filterNot { it.second is FarmDestination.HomePanel || it.first in listOf("Record weight", "Record observation") }
                    .forEach { (label, target) -> AnimalFarmQuickAction(label, { navigate(target) }) }
            }
            HomeReferenceSheet.RESOURCES -> {
                AnimalFarmQuickAction("Inventory", { navigate(FarmDestination.Module(FarmModule.INVENTORY)) })
                workerHomeAreaActions().filterNot { it.second == FarmDestination.Goat(GoatEntryPage.SYNC) }
                    .forEach { (label, target) -> AnimalFarmQuickAction(label.removePrefix("Open "), { navigate(target) }) }
            }
            HomeReferenceSheet.BREEDING -> {
                AnimalFarmQuickAction("Goat breeding records", { navigate(FarmDestination.Goat(GoatEntryPage.REPRODUCTION)) })
                AnimalFarmQuickAction("Rabbit breeding programme", { navigate(FarmDestination.Module(FarmModule.RABBIT)) })
                AnimalFarmQuickAction("Sheep records", { navigate(FarmDestination.Module(FarmModule.SHEEP)) })
                AnimalFarmQuickAction("Cattle records", { navigate(FarmDestination.Module(FarmModule.CATTLE)) })
                AnimalFarmQuickAction("Poultry breeding", { navigate(FarmDestination.Module(FarmModule.POULTRY)) })
                if (!worker) AnimalFarmQuickAction("Genetics", { navigate(FarmDestination.Module(FarmModule.GENETICS)) })
            }
            HomeReferenceSheet.GUIDES -> {
                // No fake field guide or approval is created from the HTML's demonstration content.
                Text("Open a task to view its recorded details and attachments.", style = AnimalFarmHtmlType.body)
                summary.tasks.filter { it.status != "done" }.take(8).forEach { task ->
                    AnimalFarmQuickAction(task.title, { navigate(FarmDestination.Task(task.id)) })
                }
                AnimalFarmQuickAction("All tasks", { navigate(FarmDestination.Tasks(TaskEntryPage.BOARD)) })
                AnimalFarmQuickAction("Animal families", { onSheet(HomeReferenceSheet.FAMILIES) })
                AnimalFarmQuickAction("Resources", { onSheet(HomeReferenceSheet.RESOURCES) })
                AnimalFarmQuickAction("Search farm", { navigate(FarmDestination.Search) })
                AnimalFarmQuickAction("More farm tools", { onDismiss(); onMore() })
            }
        }
    }
}
