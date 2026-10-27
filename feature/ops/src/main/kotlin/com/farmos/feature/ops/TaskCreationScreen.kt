package com.farmos.feature.ops

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.farmos.core.design.*
import java.time.LocalDate

/** UI draft; saving still goes through the existing local task command owner. */
data class TaskCreateDraft(val title:String,val moduleCode:String,val taskCode:String,val dueDate:LocalDate,
    val animalId:String?=null)

/** One focused screen. The task board and its view controls are not mounted below the form. */
@Composable
fun CreateTaskScreen(busy:Boolean,error:String?,onCreate:(String,String,String,String)->Unit,onBack:()->Unit,
    canPlanWork:Boolean=false,assignees:List<TaskAssigneeOption> = emptyList(),
    onCreateSeries:(TaskSeriesDraft)->Unit = {},
    searchAnimals:FarmSelectorSearch=NoFarmSelectorSearch,onCreateDetailed:((TaskCreateDraft)->Unit)?=null,
    savedRevision:Int=0) {
    FarmBackHandler(if(busy) ({}) else onBack)
    val initialRevision=remember { savedRevision }
    LaunchedEffect(savedRevision) { if(savedRevision>initialRevision) onBack() }
    FarmOperationalPage("FOS-TASK-004","Add task","",onBack={if(!busy)onBack()},backLabel="Tasks") {
        TaskCreationFields(busy,onCreate,canPlanWork,assignees,onCreateSeries,searchAnimals,onCreateDetailed)
        error?.let { Text(it,color=AnimalFarmTheme.colors.critical,modifier=Modifier.testTag("task-create-error")) }
    }
}

@Composable
private fun TaskCreationFields(busy:Boolean,onCreate:(String,String,String,String)->Unit,canPlanWork:Boolean,
    assignees:List<TaskAssigneeOption>,onCreateSeries:(TaskSeriesDraft)->Unit,
    searchAnimals:FarmSelectorSearch,onCreateDetailed:((TaskCreateDraft)->Unit)?) {
    var title by rememberSaveable { mutableStateOf("") }
    var module by rememberSaveable { mutableStateOf("farm") }
    var code by rememberSaveable { mutableStateOf("CHECK") }
    var due by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var repeat by rememberSaveable { mutableStateOf(TaskRepeat.NONE) }
    var interval by rememberSaveable { mutableStateOf("2") }
    var endDate by rememberSaveable { mutableStateOf("") }
    var assigneeId by rememberSaveable { mutableStateOf<String?>(null) }
    var animalId by rememberSaveable { mutableStateOf<String?>(null) }
    var animalLabel by rememberSaveable { mutableStateOf("") }
    var animalDetail by rememberSaveable { mutableStateOf<String?>(null) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val subject=animalId?.let { FarmSelectorOption(it,animalLabel,animalDetail) }
    val planning = canPlanWork && (repeat!=TaskRepeat.NONE || assigneeId!=null)
    FarmOutlinedTextField(title,{title=it},label={Text("Title")},singleLine=true,enabled=!busy,
        modifier=Modifier.fillMaxWidth().testTag("task-create-title"))
    FarmDateField(if(repeat==TaskRepeat.NONE) "Due date" else "First day",due,{due=it},enabled=!busy,key="task-due")
    FarmSearchChoiceField("Animal",subject,searchAnimals,{selected ->
        animalId=selected?.id;animalLabel=selected?.label.orEmpty();animalDetail=selected?.detail
    },tag="task-animal",enabled=!busy && searchAnimals!==NoFarmSelectorSearch,noneLabel="Whole farm / no individual animal",
        emptyText="No matching animals in this farm")
    if(canPlanWork) TaskPlanningFields(busy,assignees,repeat,{repeat=it},interval,{interval=it},endDate,{endDate=it},assigneeId,{assigneeId=it})
    FarmActionButton({advanced=!advanced},Modifier.fillMaxWidth().testTag("task-create-advanced"),enabled=!busy) {
        Text(if(advanced) "Hide task details" else "More task details")
    }
    if(advanced) {
        FarmOutlinedTextField(module,{module=it},label={Text("Module")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
        FarmOutlinedTextField(code,{code=it},label={Text("Task code")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
    }
    FarmPrimaryButton(onClick={
        val start=LocalDate.parse(due)
        if(planning) onCreateSeries(TaskSeriesDraft(title.trim(),module.trim(),code.trim(),start,repeat,
            if(repeat.takesInterval) interval.toInt() else 1,
            endDate.takeIf { repeat!=TaskRepeat.NONE && it.isNotBlank() }?.let(LocalDate::parse),
            assigneeChange(assigneeId).accountId,assigneeChange(assigneeId).workerId,animalId))
        else if(onCreateDetailed!=null) onCreateDetailed(TaskCreateDraft(title.trim(),module.trim(),code.trim(),start,animalId))
        else { check(animalId==null) { "An animal-linked task requires its governed command owner" };onCreate(title,module,code,due) }
    },enabled=!busy && title.isNotBlank() && module.isNotBlank() && code.isNotBlank()
        && planningValid(repeat,interval,due,endDate) && (animalId==null || planning || onCreateDetailed!=null),
        modifier=Modifier.fillMaxWidth().testTag("task-create-save")) { Text(if(busy) "Saving task…" else "Create task") }
}
