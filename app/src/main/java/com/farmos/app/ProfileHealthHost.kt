package com.farmos.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.design.*
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Embedded in every species profile; all queries are tied to the session farm and actual subject. */
@Composable
internal fun ProfileHealthHost(database:FarmOsDatabase,farmId:String,subject:FarmHealthSubject,
    ops:RoomOpsRepository,newContext:()->LocalCommandContext,enqueueSync:()->Unit,
    canRecord:Boolean,canPlan:Boolean,onOpen:(FarmDestination,FarmDestination?)->Unit) {
    var model by remember(farmId,subject) { mutableStateOf<ProfileHealthModel?>(null) }
    var loading by remember(farmId,subject) { mutableStateOf(true) }
    var error by remember(farmId,subject) { mutableStateOf<String?>(null) }
    var revision by remember(farmId,subject) { mutableIntStateOf(0) }
    var section by rememberSaveable(farmId,subject.id,subject.kind) { mutableStateOf("Overview") }
    var showAll by rememberSaveable(farmId,subject.id) { mutableStateOf(false) }
    var capture by rememberSaveable(farmId,subject.id) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var receipt by remember(farmId,subject) { mutableStateOf<String?>(null) }
    val scope=rememberCoroutineScope()
    var selectedSignal by remember(farmId,subject) { mutableStateOf<HealthReviewSignal?>(null) }
    fun navigate(target:FarmDestination) { onOpen(target,model?.let(::profileHealthReturnRoute)) }
    LaunchedEffect(farmId,subject,revision) {
        loading=true;error=null
        runSuspendCatching { loadProfileHealth(database,farmId,subject) }.onSuccess { model=it }.onFailure { model=null;error=it.message }
        loading=false
    }
    FarmIllustratedSectionSurface(Modifier.fillMaxWidth().testTag("profile-health:${subject.speciesCode}:${subject.id}")) {
        Text("Health & care",style=AnimalFarmHtmlType.sectionTitle)
        if(loading) FarmLoadingSkeleton("Reading this subject’s health records")
        else {
            val data=model
            if(data==null) Text(error ?: "Health information is unavailable",color=AnimalFarmTheme.colors.critical)
            else {
                Text(data.label,style=AnimalFarmHtmlType.caption,color=AnimalFarmTheme.colors.mutedInk)
                FarmSectionTabs(listOf("Overview","History","Care plan"),section,{section=it},tagPrefix="profile-health-view:")
                when(section) {
                    "History" -> {
                        if(data.history.isEmpty()) Text("No linked health records. Missing records do not establish normal health.",style=AnimalFarmHtmlType.body)
                        val history=if(showAll) data.history else data.history.take(8)
                        history.forEach { record -> FarmRecordRow(listOfNotNull(record.epochDay?.let { LocalDate.ofEpochDay(it).toString() },record.title,record.detail).joinToString(" · "),
                            modifier=Modifier.testTag("profile-health-record:${record.id}")) }
                        if(data.history.size>8) FarmActionButton({showAll=!showAll},Modifier.fillMaxWidth()) {Text(if(showAll) "Show recent records" else "Show all ${data.history.size} records")}
                    }
                    "Care plan" -> {
                        Text(data.speciesFocus,style=AnimalFarmHtmlType.body)
                        Text("Due animal-linked work",style=AnimalFarmHtmlType.sectionTitle)
                        if(data.dueTasks.isEmpty()) Text("No linked due task is recorded. This does not establish that every required health check has been planned.",style=AnimalFarmHtmlType.caption)
                        data.dueTasks.forEach { task -> FarmResourceAction(task.title,{navigate(if(task.seriesId==null) FarmDestination.Task(task.id) else FarmDestination.Tasks(animalId=subject.id))},detail="Due ${LocalDate.ofEpochDay(task.dueOnEpochDay)}") }
                        Text("Vet-accepted species protocols",style=AnimalFarmHtmlType.sectionTitle)
                        if(data.protocols.isEmpty()) Text("No vet-accepted protocol for this species is recorded on this farm.",style=AnimalFarmHtmlType.caption)
                        data.protocols.forEach { pack -> Text("${pack.name} · accepted by ${pack.acceptedByVet ?: "reviewer not recorded"}",style=AnimalFarmHtmlType.body) }
                        Text("An accepted species protocol is not proof that it has been applied to this animal. Review its target and anchor event before scheduling.",style=AnimalFarmHtmlType.caption)
                    }
                    else -> {
                        val last=data.weights.filter { it.grams>0 && it.occurredAt<=data.reviewedAt }.maxByOrNull { it.occurredAt }
                        if(subject.kind==FarmHealthSubjectKind.ANIMAL) Text(if(last==null) "Weight: not recorded" else
                            "Weight: ${BigDecimal.valueOf(last.grams,3).stripTrailingZeros().toPlainString()} kg · ${Instant.ofEpochMilli(last.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()}",style=AnimalFarmHtmlType.body)
                        Text(if(subject.kind==FarmHealthSubjectKind.FLOCK)
                            "${data.observationCount} flock-day records · ${data.vaccinationCount} flock vaccinations. Individual treatments are not aggregated here."
                            else "${data.observationCount} observation records · ${data.treatmentCount ?: 0} treatment records · ${data.vaccinationCount} vaccination records",
                            style=AnimalFarmHtmlType.caption)
                        if(data.signals.isEmpty()) Text("No review prompt was generated from the available records. This is not a veterinary assessment or clearance.",style=AnimalFarmHtmlType.body)
                        (if(showAll) data.signals else data.signals.take(2)).forEach { signal ->
                            FarmResourceAction(signal.title,{selectedSignal=signal},
                                detail=if(signal.evidenceIds.isEmpty()) "Review missing records" else "Review ${signal.evidenceIds.size} supporting record(s)",
                                modifier=Modifier.testTag("profile-health-signal:${signal.code}"))
                        }
                        if(data.signals.size>2) FarmActionButton({showAll=!showAll},Modifier.fillMaxWidth()) {
                            Text(if(showAll) "Show priority prompts" else "Show all ${data.signals.size} prompts")
                        }
                    }
                }
                if(subject.kind==FarmHealthSubjectKind.ANIMAL) {
                    FarmPrimaryButton({capture="observation"},Modifier.fillMaxWidth().testTag("profile-health-observe"),enabled=canRecord && data.active && !busy) {Text("Record health observation")}
                    FarmModuleTools(title="Health actions",groups=listOf(FarmToolGroup("More actions",listOf(
                        FarmToolAction("Record weight",{navigate(FarmDestination.Weight(subject.speciesCode,subject.id))},enabled=canRecord && data.active),
                        FarmToolAction("Plan health follow-up",{capture="follow-up"},enabled=canPlan && data.active),
                        FarmToolAction("Open health workspace",{navigate(FarmDestination.Health())}),
                    ))))
                } else FarmActionButton({navigate(FarmDestination.Module(FarmModule.POULTRY))},Modifier.fillMaxWidth()) {Text("Open poultry workspace")}
                receipt?.let { FarmOfflineSaveReceipt(text=it) }
                Text("Record-based review, not a diagnosis. No treatment or schedule is generated without the existing approval and recording workflow.",style=AnimalFarmHtmlType.caption,color=AnimalFarmTheme.colors.mutedInk)
                FarmActionButton({revision++},Modifier.fillMaxWidth(),enabled=!busy) {Text("Refresh health records")}
            }
        }
    }
    val loaded=model
    selectedSignal?.let { signal ->
        AnimalFarmSelectionSheet(signal.title,{selectedSignal=null}) {
            Text(signal.explanation,style=AnimalFarmHtmlType.body)
            val records=loaded?.history.orEmpty().filter { it.id in signal.evidenceIds }
            records.forEach { Text(listOfNotNull(it.epochDay?.let { day -> LocalDate.ofEpochDay(day).toString() },it.title,it.detail).joinToString(" · "),style=AnimalFarmHtmlType.caption) }
            loaded?.weights.orEmpty().filter { it.recordId in signal.evidenceIds }.forEach {
                Text("${BigDecimal.valueOf(it.grams,3).stripTrailingZeros().toPlainString()} kg · ${Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()}",style=AnimalFarmHtmlType.caption)
            }
            val canCapture=canRecord && loaded?.active==true && subject.kind==FarmHealthSubjectKind.ANIMAL
            val action=when(signal.action) {
                HealthReviewAction.RECORD_WEIGHT -> if(canCapture) "Record weight" else "Review care plan"
                HealthReviewAction.RECORD_OBSERVATION -> if(canCapture) "Record observation" else "Review care plan"
                HealthReviewAction.REVIEW_RECORDS -> "Open health history"
                else -> "Open care plan"
            }
            FarmPrimaryButton({
                selectedSignal=null
                when(signal.action) {
                    HealthReviewAction.RECORD_WEIGHT -> if(canCapture) navigate(FarmDestination.Weight(subject.speciesCode,subject.id)) else section="Care plan"
                    HealthReviewAction.RECORD_OBSERVATION -> if(canCapture) capture="observation" else section="Care plan"
                    HealthReviewAction.REVIEW_RECORDS -> section="History"
                    else -> section="Care plan"
                }
            },Modifier.fillMaxWidth()) {Text(action)}
            Text("Based on this subject’s local records · ${AnimalHealthReview.VERSION}. A review prompt is not a diagnosis.",style=AnimalFarmHtmlType.caption,color=AnimalFarmTheme.colors.mutedInk)
        }
    }
    if(capture!=null && loaded!=null && loaded.subject.kind==FarmHealthSubjectKind.ANIMAL) {
        ProfileHealthCaptureSheet(loaded,capture!!,busy,error,{if(!busy)capture=null}) { draft ->
            launchCommittedModuleWrite(scope=scope,write={
                require(loaded.subject.id==subject.id && loaded.subject.speciesCode==subject.speciesCode)
                val ctx=newContext();require(ctx.farmId==farmId)
                val id=UUID.randomUUID().toString()
                if(draft.kind=="observation") {
                    check(canRecord)
                    ops.recordObservation(RecordHealthObservation(id,subject.speciesCode,subject.id,draft.text,
                        redFlag=draft.redFlag,occurredAtEpochMillis=if(draft.day==Instant.ofEpochMilli(ctx.occurredAtEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate()) ctx.occurredAtEpochMillis else draft.day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()),ctx)
                } else {
                    check(canPlan)
                    ops.createTask(CreateFarmTask(id,subject.speciesCode,"HEALTH_REVIEW",draft.text,draft.day.toEpochDay(),animalId=subject.id),ctx)
                }
            },isBusy={busy},setBusy={busy=it},setError={error=it},enqueueSync=enqueueSync,
                onCommitted={receipt=if(capture=="observation") "Animal-linked observation saved on this device" else "Animal-linked follow-up saved on this device";capture=null},refresh={revision++})
        }
    }
}
