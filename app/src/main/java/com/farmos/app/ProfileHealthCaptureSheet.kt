package com.farmos.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.farmos.core.design.*
import java.time.LocalDate

data class ProfileHealthCaptureDraft(val kind:String,val text:String,val day:LocalDate,val redFlag:Boolean)

@Composable
internal fun ProfileHealthCaptureSheet(model:ProfileHealthModel,kind:String,busy:Boolean,error:String?,
    onDismiss:()->Unit,onSave:(ProfileHealthCaptureDraft)->Unit) {
    var text by rememberSaveable(model.subject.id,kind) { mutableStateOf(if(kind=="follow-up") "Health review · ${model.label}" else "") }
    var date by rememberSaveable(model.subject.id,kind) { mutableStateOf(LocalDate.now().toString()) }
    var urgent by rememberSaveable(model.subject.id,kind) { mutableStateOf(false) }
    AnimalFarmSelectionSheet(if(kind=="observation") "Record health observation" else "Plan health follow-up",onDismiss) {
        Text(model.label,style=AnimalFarmHtmlType.sectionTitle)
        Text("${model.subject.speciesCode} · this individual animal",style=AnimalFarmHtmlType.caption)
        FarmOutlinedTextField(text,{text=it},label={Text(if(kind=="observation") "What did you observe?" else "Task title")},
            enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("profile-health-capture-text"))
        FarmDateField(if(kind=="observation") "Observation date" else "Due date",date,{date=it},enabled=!busy,key="profile-health-day")
        if(kind=="observation") FarmChoicePicker("Recorded concern",listOf(false to "Not marked urgent",true to "Flag for veterinary review"),
            urgent,{urgent=it},tag="profile-health-urgency",enabled=!busy)
        val day=runCatching { LocalDate.parse(date) }.getOrNull()
        FarmPrimaryButton({onSave(ProfileHealthCaptureDraft(kind,text.trim(),requireNotNull(day),urgent))},
            enabled=!busy && text.isNotBlank() && day!=null && (kind!="observation" || !day.isAfter(LocalDate.now())),
            modifier=Modifier.fillMaxWidth().testTag("profile-health-capture-save")) {Text(if(busy) "Saving…" else if(kind=="observation") "Save observation" else "Create follow-up task")}
        error?.let {Text(it,color=AnimalFarmTheme.colors.critical)}
    }
}
