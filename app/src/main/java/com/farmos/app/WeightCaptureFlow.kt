package com.farmos.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.design.*
import java.time.LocalDate

internal val WEIGHT_SPECIES = listOf("goat","rabbit","sheep","cattle","poultry")
internal fun weightSpeciesLabel(species:String) = when(species) {
    "goat"->"Goats";"rabbit"->"Rabbits";"sheep"->"Sheep";"cattle"->"Cattle";"poultry"->"Poultry"
    else->error("Unsupported weighing species")
}

@Composable
internal fun WeightSpeciesSheet(onDismiss:()->Unit,onChoose:(String)->Unit) {
    AnimalFarmSelectionSheet("Record weight",onDismiss) {
        WEIGHT_SPECIES.forEach { species ->
            FarmResourceAction(weightSpeciesLabel(species),{onChoose(species)},
                modifier=Modifier.testTag("weight-family:${weightSpeciesLabel(species)}"),
                family=animalFarmFamilyFromKey(species),
                detail=if(species=="poultry") "A registered individual bird; not a flock average" else "Choose a registered animal")
        }
    }
}

/** Shared presentation, species-native command ownership. The selected ID never comes from a label. */
@Composable
internal fun SpeciesWeightCaptureHost(database:FarmOsDatabase,farmId:String,entry:FarmDestination.Weight,
    onRecord:suspend (String,String,String,LocalDate)->Unit,enqueueSync:()->Unit,onBack:()->Unit) {
    require(entry.speciesCode==null || entry.speciesCode in WEIGHT_SPECIES)
    require(entry.animalId==null || entry.speciesCode!=null)
    var species by rememberSaveable(farmId,entry) { mutableStateOf(entry.speciesCode) }
    var selectedId by rememberSaveable(farmId,entry) { mutableStateOf(entry.animalId) }
    var label by rememberSaveable(farmId,entry) { mutableStateOf("") }
    var weight by rememberSaveable(farmId,entry) { mutableStateOf("") }
    var date by rememberSaveable(farmId,entry) { mutableStateOf(LocalDate.now().toString()) }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    FarmBackHandler(if(busy) ({}) else onBack)
    val scope=rememberCoroutineScope()
    LaunchedEffect(farmId,entry.animalId,entry.speciesCode) {
        entry.animalId?.let { id ->
            runSuspendCatching {
                val animal=requireNotNull(database.animals().get(farmId,id)) { "Selected animal is not on this farm" }
                require(animal.speciesCode==entry.speciesCode) { "Selected animal species does not match this weighing" }
                label=listOfNotNull(animal.tag,animal.name).joinToString(" · ")
            }.onFailure { error=it.message;selectedId=null }
        }
    }
    FarmOperationalPage("FOS-HOME-008","Record weight","",onBack={if(!busy)onBack()}) {
        FarmChoicePicker("Animal species",WEIGHT_SPECIES.map { it to weightSpeciesLabel(it) },species ?: "",
            {chosen -> species=chosen;selectedId=null;label="";saved=false;error=null},"weight-species",
            enabled=!busy && entry.animalId==null)
        val chosenSpecies=species
        if(chosenSpecies!=null) {
            val search=remember(database,farmId,chosenSpecies) { animalSelectorSearch(database,farmId,chosenSpecies) }
            FarmSearchChoiceField("Animal",selectedId?.let { FarmSelectorOption(it,label) },search,
                {animal -> selectedId=animal?.id;label=animal?.label.orEmpty();saved=false;error=null},
                "weight-animal",enabled=!busy && entry.animalId==null,emptyText="No matching ${weightSpeciesLabel(chosenSpecies).lowercase()} on this farm")
            if(chosenSpecies=="poultry") Text("This records one tagged bird. Flock records and daily flock observations remain separate; this value is not a flock-average weight.",style=AnimalFarmHtmlType.caption)
            FarmOutlinedTextField(weight,{weight=it;saved=false},label={Text("Weight (kg)")},singleLine=true,enabled=!busy,
                modifier=Modifier.fillMaxWidth().testTag("species-weight-kg"))
            FarmDateField("Date",date,{date=it;saved=false},enabled=!busy,key="species-weight-date")
            val day=runCatching { LocalDate.parse(date) }.getOrNull()
            FarmPrimaryButton(onClick={
                val id=requireNotNull(selectedId);val measured=requireNotNull(day)
                launchCommittedModuleWrite(scope=scope,write={onRecord(chosenSpecies,id,weight,measured);saved=true},
                    isBusy={busy},setBusy={busy=it},setError={error=it},enqueueSync=enqueueSync,refresh={})
            },enabled=!busy && !saved && selectedId!=null && day!=null && !day.isAfter(LocalDate.now())
                && runCatching { weight.trim().toBigDecimal().movePointRight(3).longValueExact()>0 }.getOrDefault(false),
                modifier=Modifier.fillMaxWidth().testTag("species-weight-save")) {Text(if(busy) "Saving weight…" else "Save weight")}
            if(saved) FarmOfflineSaveReceipt()
        }
        error?.let { Text(it,color=AnimalFarmTheme.colors.critical) }
    }
}
