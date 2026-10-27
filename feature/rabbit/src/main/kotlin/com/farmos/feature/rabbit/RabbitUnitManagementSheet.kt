package com.farmos.feature.rabbit

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.farmos.core.design.*
import com.farmos.domain.rabbit.RabbitNestBoxCycle
import kotlinx.coroutines.launch
import java.time.LocalDate

/** A frozen reviewed snapshot supplies expected versions; concurrent edits require an explicit reload. */
@Composable
internal fun RabbitUnitManagementSheet(unit:RabbitUnitView,records:RabbitRecords,ports:RabbitryPorts,onDismiss:()->Unit) {
    val original=remember(unit.cageId){unit}
    val choices=buildList {
        if(ports.access.record){add("Place / move rabbit");add("Remove recorded occupant");add("Assign nest box");add("Update nest condition")}
        if(ports.access.breed){add("Start buck service");add("End buck service");add("Record observed mating")}
    }
    if(choices.isEmpty())return
    var action by rememberSaveable(unit.cageId){mutableStateOf(choices.first())}
    var busy by remember{mutableStateOf(false)}
    var saved by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    var warning by remember{mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    FarmBackHandler(if(busy)({})else onDismiss)
    AnimalFarmSelectionSheet("Manage cage ${unit.code}",{if(!busy)onDismiss()}) {
        if(saved) {
            FarmOfflineSaveReceipt(text="Rabbitry change saved on this device")
            warning?.let {Text(it,style=AnimalFarmHtmlType.caption)}
            FarmPrimaryButton(onDismiss,Modifier.fillMaxWidth()) {Text("Done")}
        } else {
            FarmChoicePicker("Action",choices.map{it to it},action,{action=it;error=null},"rabbit-unit-action",enabled=!busy)
            key(action) {
                var save:(suspend ()->String?)?=null
                when(action) {
                    "Place / move rabbit" -> save=HousingFields(original,ports,busy)
                    "Remove recorded occupant" -> save=RemoveHousingFields(original,ports,busy)
                    "Start buck service" -> save=StartServiceFields(original,records,ports,busy)
                    "End buck service" -> save=EndServiceFields(original,ports,busy)
                    "Record observed mating" -> save=MatingFields(original,records,ports,busy)
                    "Assign nest box" -> save=NestAssignmentFields(original,records,ports,busy)
                    "Update nest condition" -> save=NestConditionFields(original,ports,busy)
                }
                FarmPrimaryButton({
                    val commit=requireNotNull(save)
                    scope.launch {busy=true;error=null
                        try {runSuspendCatching {commit()}.onSuccess {warning=it;saved=true}.onFailure {error=it.message ?: "The change was not saved"}}
                        finally{busy=false}
                    }
                },Modifier.fillMaxWidth().testTag("rabbit-unit-save"),enabled=!busy && save!=null) {Text(if(busy)"Saving…" else "Save recorded change")}
                error?.let {Text(it,color=AnimalFarmTheme.colors.critical)}
            }
        }
    }
}

@Composable
private fun HousingFields(unit:RabbitUnitView,ports:RabbitryPorts,busy:Boolean):(suspend ()->String?)? {
    var animal by remember{mutableStateOf<FarmSelectorOption?>(null)}
    var role by rememberSaveable{mutableStateOf("")}
    var reason by rememberSaveable{mutableStateOf("")}
    var housing by remember{mutableStateOf<RabbitHousingChoice?>(null)}
    var failure by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(animal?.id){housing=null;failure=null;animal?.let { picked->
        runSuspendCatching{ports.housing(picked.id)}.onSuccess{housing=it}.onFailure{failure=it.message}
    }}
    FarmSearchChoiceField("Rabbit",animal,ports.searchRabbits,{animal=it},"rabbit-place-animal",enabled=!busy)
    FarmChoicePicker("Housing role",listOf("doe" to "Breeding doe","buck" to "Buck","growout" to "Grow-out rabbit"),role,{role=it},"rabbit-place-role",enabled=!busy)
    housing?.let {Text(it.cageLabel?.let{c->"Currently recorded in cage $c"} ?: "No current placement recorded",style=AnimalFarmHtmlType.caption)}
    FarmOutlinedTextField(reason,{reason=it.take(2000)},label={Text("Move notes · optional")},enabled=!busy,modifier=Modifier.fillMaxWidth())
    Text("This records an actual placement now. Capacity is ${unit.doeCapacity} breeding does; a planned wave does not move animals.",style=AnimalFarmHtmlType.caption)
    failure?.let {Text(it,color=AnimalFarmTheme.colors.critical)}
    val chosen=animal;val original=housing
    return if(chosen!=null && original?.animalId==chosen.id && role.isNotBlank()) ({ports.setHousing(chosen.id,unit.cageId,role,original.version,reason)}) else null
}

@Composable
private fun RemoveHousingFields(unit:RabbitUnitView,ports:RabbitryPorts,busy:Boolean):(suspend ()->String?)? {
    var id by rememberSaveable{mutableStateOf<String?>(null)}
    FarmChoicePicker("Recorded occupant",unit.residents.map{it.animalId as String? to it.label},id,{id=it},"rabbit-remove-animal",enabled=!busy)
    Text("Only the current cage placement is released. The rabbit and all historical records remain. End any open service or in-use nest first.",style=AnimalFarmHtmlType.caption)
    val row=unit.residents.firstOrNull{it.animalId==id}
    return row?.let { ({ports.setHousing(it.animalId,null,null,it.housingVersion,"Removed from cage ${unit.code}")}) }
}

@Composable
private fun StartServiceFields(unit:RabbitUnitView,records:RabbitRecords,ports:RabbitryPorts,busy:Boolean):(suspend ()->String?)? {
    var id by rememberSaveable{mutableStateOf<String?>(null)}
    var wave by rememberSaveable{mutableStateOf<String?>(null)}
    var notes by rememberSaveable{mutableStateOf("")}
    val bucks=unit.residents.filter{it.role=="buck" && it.status=="active"}
    if(bucks.isEmpty()) AnimalFarmEmptyState("Record the buck’s actual placement in this unit before starting service.")
    FarmChoicePicker("Resident buck",bucks.map{it.animalId as String? to it.label},id,{id=it},"rabbit-service-buck",enabled=!busy)
    WaveChoice(unit,records,wave,{wave=it},busy)
    FarmOutlinedTextField(notes,{notes=it.take(4000)},label={Text("Service notes · optional")},enabled=!busy,modifier=Modifier.fillMaxWidth())
    Text("Starts a recorded service interval now. It does not assert that any doe has mated or become pregnant.",style=AnimalFarmHtmlType.caption)
    return id?.takeIf{selected->bucks.any{it.animalId==selected}}?.let { ({ports.startService(unit.cageId,it,wave,notes)}) }
}

@Composable
private fun EndServiceFields(unit:RabbitUnitView,ports:RabbitryPorts,busy:Boolean):(suspend ()->String?)? {
    var id by rememberSaveable{mutableStateOf<String?>(null)}
    FarmChoicePicker("Open service",unit.openServices.map{it.id as String? to "${it.buckLabel} · since ${rabbitRecordDate(it.startedAt)}"},id,{id=it},"rabbit-end-service",enabled=!busy)
    if(unit.openServices.isEmpty()) AnimalFarmEmptyState("No open service is recorded for this cage")
    Text("Ends the selected recorded service now. The buck remains housed here until an actual move is recorded.",style=AnimalFarmHtmlType.caption)
    return unit.openServices.firstOrNull{it.id==id}?.let { ({ports.endService(it.id,it.version)}) }
}

@Composable
private fun MatingFields(unit:RabbitUnitView,records:RabbitRecords,ports:RabbitryPorts,busy:Boolean):(suspend ()->String?)? {
    var doe by remember{mutableStateOf<FarmSelectorOption?>(null)}
    var buck by remember{mutableStateOf<FarmSelectorOption?>(null)}
    var wave by rememberSaveable{mutableStateOf<String?>(null)}
    var date by rememberSaveable{mutableStateOf(LocalDate.now().toString())}
    var notes by rememberSaveable{mutableStateOf("")}
    FarmSearchChoiceField("Doe",doe,ports.searchDoes,{doe=it},"rabbit-mating-doe",enabled=!busy)
    FarmSearchChoiceField("Buck",buck,ports.searchBucks,{buck=it},"rabbit-mating-buck",enabled=!busy)
    WaveChoice(unit,records,wave,{wave=it},busy)
    FarmDateField("Observed mating date",date,{date=it},enabled=!busy,key="rabbit-pair-mating-date")
    FarmOutlinedTextField(notes,{notes=it.take(4000)},label={Text("Observation notes · optional")},enabled=!busy,modifier=Modifier.fillMaxWidth())
    Text("Record only an observed pairing. This does not confirm pregnancy or genetic parentage and does not create a duplicate wave schedule.",style=AnimalFarmHtmlType.caption)
    val day=runCatching{LocalDate.parse(date)}.getOrNull();val d=doe;val b=buck
    return if(d!=null && b!=null && day!=null && !day.isAfter(LocalDate.now())) ({ports.recordMating(unit.cageId,d.id,b.id,wave,day,notes)}) else null
}

@Composable
private fun NestAssignmentFields(unit:RabbitUnitView,records:RabbitRecords,ports:RabbitryPorts,busy:Boolean):(suspend ()->String?)? {
    var boxId by rememberSaveable{mutableStateOf<String?>(null)}
    var doeId by rememberSaveable{mutableStateOf<String?>(null)}
    var wave by rememberSaveable{mutableStateOf<String?>(null)}
    FarmChoicePicker("Nest box",unit.nests.map{it.id as String? to "${it.code} · ${nestStateLabel(it.status)}"},boxId,{boxId=it;doeId=null;wave=null},"rabbit-assign-box",enabled=!busy)
    FarmChoicePicker("Resident doe",listOf(null to "Release existing assignment")+unit.residents.filter{it.role=="doe" && it.status=="active"}.map{it.animalId as String? to it.label},
        doeId,{doeId=it;if(it==null)wave=null},"rabbit-assign-doe",enabled=!busy)
    if(doeId!=null) WaveChoice(unit,records,wave,{wave=it},busy)
    val box=unit.nests.firstOrNull{it.id==boxId}
    box?.let {Text(it.doeLabel?.let{label->"Current assignment: $label"} ?: "No current doe assignment recorded",style=AnimalFarmHtmlType.caption)}
    Text("A reservation is not physical nest placement. Record the box condition separately; an in-use nest cannot be released before removal.",style=AnimalFarmHtmlType.caption)
    return box?.takeIf{it.doeId!=doeId || it.waveId!=wave}?.let {({ports.assignNest(it.id,doeId,wave,it.assignmentVersion)})}
}

@Composable
private fun NestConditionFields(unit:RabbitUnitView,ports:RabbitryPorts,busy:Boolean):(suspend ()->String?)? {
    var id by rememberSaveable{mutableStateOf<String?>(null)}
    var state by rememberSaveable{mutableStateOf<String?>(null)}
    FarmChoicePicker("Nest box",unit.nests.map{it.id as String? to "${it.code} · ${nestStateLabel(it.status)}"},id,{id=it;state=null},"rabbit-condition-box",enabled=!busy)
    val box=unit.nests.firstOrNull{it.id==id}
    if(box!=null) {
        val allowed=RabbitNestBoxCycle.nextStatuses(box.status)
        FarmChoicePicker("New recorded condition",allowed.map{it as String? to nestStateLabel(it)},state,{state=it},"rabbit-condition-state",enabled=!busy)
        Text("Choose the condition after the physical action has happened. Scheduled nest dates do not change this state.",style=AnimalFarmHtmlType.caption)
    }
    return if(box!=null && state!=null && state in RabbitNestBoxCycle.nextStatuses(box.status)) ({ports.setNestStatus(box.id,state!!)}) else null
}

@Composable
private fun WaveChoice(unit:RabbitUnitView,records:RabbitRecords,selected:String?,onSelect:(String?)->Unit,busy:Boolean) {
    val choices=listOf(null to "No wave association")+records.waves.filter{it.id in unit.waveIds}.map{
        it.id as String? to "Plan ${LocalDate.ofEpochDay(it.matingEpochDay)} · ${it.doeCount} does"
    }
    FarmChoicePicker("Linked wave plan",choices,selected,onSelect,"rabbit-linked-wave",enabled=!busy)
}
