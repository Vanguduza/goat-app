package com.farmos.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.design.*
import com.farmos.data.herd.BreedingEvidenceQueries
import com.farmos.data.herd.BreedingLineage
import java.time.LocalDate

@Composable
internal fun BreedingWorkspace(database:FarmOsDatabase,farmId:String,entry:FarmDestination.Breeding,onBack:()->Unit) {
    var view by rememberSaveable(farmId,entry) { mutableStateOf(entry.view) }
    var focus by rememberSaveable(farmId,entry) { mutableStateOf(entry.animalId) }
    FarmOperationalPage(if(view==FarmBreedingView.PEDIGREE) "FOS-GEN-002" else "FOS-GEN-008",
        "Breeding & pedigree","",onBack=onBack,backLabel="Back") {
        FarmSectionTabs(listOf("Pedigree tree","Pairing evidence"),if(view==FarmBreedingView.PEDIGREE) "Pedigree tree" else "Pairing evidence",
            {view=if(it=="Pedigree tree") FarmBreedingView.PEDIGREE else FarmBreedingView.PAIRINGS})
        if(view==FarmBreedingView.PEDIGREE) LineageExplorerContent(database,farmId,focus,entry.speciesCode)
        else PairingEvidenceContent(database,farmId,focus,entry.speciesCode) { id -> focus=id;view=FarmBreedingView.PEDIGREE }
    }
}

@Composable
internal fun LineageExplorerContent(database:FarmOsDatabase,farmId:String,initialId:String?=null,speciesCode:String?=null,
    showSelector:Boolean=true) {
    var selectedId by rememberSaveable(farmId,initialId) { mutableStateOf(initialId) }
    var selectedLabel by rememberSaveable(farmId,initialId) { mutableStateOf("") }
    var depth by rememberSaveable(farmId) { mutableIntStateOf(2) }
    var view by rememberSaveable(farmId) { mutableStateOf("Tree") }
    var refresh by remember { mutableIntStateOf(0) }
    var visibleChildren by rememberSaveable(selectedId) { mutableIntStateOf(12) }
    var lineage by remember(farmId,selectedId) { mutableStateOf<BreedingLineage?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val queries=remember(database,farmId) { BreedingEvidenceQueries(database,farmId) }
    val search=remember(database,farmId,speciesCode) { animalSelectorSearch(database,farmId,speciesCode) }
    LaunchedEffect(farmId,selectedId,depth,refresh) {
        val id=selectedId ?: return@LaunchedEffect
        loading=true;error=null;lineage=null
        runSuspendCatching { queries.lineage(id,maxOf(depth,3)).also { result ->
            require(speciesCode==null || result.animals[id]?.species==speciesCode) { "The chosen animal belongs to another species" }
        } }.onSuccess { result ->
            lineage=result;selectedLabel=result.animals[id]?.label.orEmpty()
        }.onFailure { error=it.message }
        loading=false
    }
    if(showSelector) FarmSearchChoiceField("Animal",selectedId?.let{FarmSelectorOption(it,selectedLabel)},search,
        {option -> selectedId=option?.id;selectedLabel=option?.label.orEmpty()},"lineage-animal")
    if(selectedId==null) AnimalFarmEmptyState("Choose an animal to follow its recorded genetic lineage.")
    else {
        FarmChoicePicker("Visible generations",listOf(1 to "Parents",2 to "Parents and grandparents",3 to "Three generations"),depth,{depth=it},"lineage-depth")
        FarmSectionTabs(listOf("Tree","Ancestors","Offspring"),view,{view=it},tagPrefix="lineage-view:")
        if(loading) FarmLoadingSkeleton("Reading the recorded lineage")
        error?.let { Text(it,color=AnimalFarmTheme.colors.critical) }
        lineage?.let { data ->
            Text(data.animals[data.rootId]?.label ?: "Animal unavailable",style=AnimalFarmHtmlType.sectionTitle)
            if(data.conflicting.isNotEmpty()) AnimalFarmWarningSurface { Text("${data.conflicting.size} animal(s) have conflicting or invalid parentage. Those branches are not guessed.") }
            data.warnings.forEach { Text(it,style=AnimalFarmHtmlType.caption,color=AnimalFarmTheme.colors.mutedInk) }
            when(view) {
                "Tree" -> FarmLineageTree(data.rootId,data.animals.mapValues { (id,a) ->
                    FarmLineageNode(id,a.label,listOf(a.sex,a.birthEpochDay?.let{LocalDate.ofEpochDay(it).toString()} ?: "Birth date unknown").joinToString(" · "),
                        data.parents[id]?.sireId,data.parents[id]?.damId,id in data.conflicting)
                },depth,{id -> selectedId=id})
                "Ancestors" -> data.animals.values.sortedWith(compareBy({it.label},{it.id})).forEach { animal ->
                    val parent=data.parents[animal.id]
                    fun label(id:String?)=id?.let{data.animals[it]?.label ?: "Record unavailable"} ?: "Not recorded"
                    FarmResourceAction(animal.label,{selectedId=animal.id},detail="Sire: ${label(parent?.sireId)} · Genetic dam: ${label(parent?.damId)}",
                        modifier=Modifier.testTag("lineage-ancestor:${animal.id}"))
                }
                else -> {
                    Text("${data.offspring.size} genetically linked offspring",style=AnimalFarmHtmlType.body)
                    if(data.offspring.isEmpty()) AnimalFarmEmptyState("No unambiguous genetic offspring links are recorded. This is not proof that the animal has produced no offspring.")
                    data.offspring.take(visibleChildren).forEach { child ->
                        FarmResourceAction(child.label,{selectedId=child.id;view="Tree"},detail=listOf(child.sex,child.status,child.birthEpochDay?.let{LocalDate.ofEpochDay(it).toString()} ?: "Birth date unknown").joinToString(" · "),
                            modifier=Modifier.testTag("lineage-offspring:${child.id}"))
                    }
                    if(data.offspring.size>visibleChildren) FarmActionButton({visibleChildren+=12},Modifier.fillMaxWidth()) {Text("Show more offspring")}
                }
            }
            Text("Only recorded genetic links are drawn. An explicit genetic dam overrides the gestational dam; foster care is not genetic parentage.",style=AnimalFarmHtmlType.caption,color=AnimalFarmTheme.colors.mutedInk)
            FarmActionButton({refresh++},Modifier.fillMaxWidth()) {Text("Refresh lineage")}
        }
    }
}
