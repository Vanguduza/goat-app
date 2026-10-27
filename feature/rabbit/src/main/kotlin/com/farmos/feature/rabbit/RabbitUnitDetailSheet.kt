package com.farmos.feature.rabbit

import androidx.compose.ui.unit.dp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.farmos.core.design.*
import java.time.LocalDate

@Composable
internal fun RabbitUnitDetailSheet(unit:RabbitUnitView,records:RabbitRecords,today:LocalDate,onDismiss:()->Unit) {
    var view by rememberSaveable(unit.cageId){mutableStateOf("Residents")}
    var residentId by rememberSaveable(unit.cageId){mutableStateOf<String?>(null)}
    var nestId by rememberSaveable(unit.cageId){mutableStateOf<String?>(null)}
    var manage by rememberSaveable(unit.cageId){mutableStateOf(false)}
    val ports=LocalRabbitryPorts.current
    if(manage && ports!=null) {
        RabbitUnitManagementSheet(unit,records,ports,{manage=false})
        return
    }
    val resident=unit.residents.firstOrNull{it.animalId==residentId}
    if(resident!=null) {
        AnimalFarmSelectionSheet(resident.label,{residentId=null}) {
            Text("Cage ${unit.code} · ${resident.role} · ${resident.status}",style=AnimalFarmHtmlType.body)
            Text("Placement recorded ${rabbitRecordDate(resident.housedAt)}",style=AnimalFarmHtmlType.caption)
            resident.lastMatingAt?.let {Text("Last observed mating ${rabbitRecordDate(it)} · ${resident.lastMateLabel ?: "mate unavailable"}",style=AnimalFarmHtmlType.body)}
            unit.openServices.filter{it.buckId==resident.animalId}.forEach {Text("Buck service open since ${rabbitRecordDate(it.startedAt)}",style=AnimalFarmHtmlType.body)}
            unit.nests.filter{it.doeId==resident.animalId}.forEach {Text("Nest ${it.code} · ${nestStateLabel(it.status)}",style=AnimalFarmHtmlType.body)}
            FarmProfileHealth(FarmHealthSubject(resident.animalId,"rabbit"))
            FarmProfileBreeding(resident.animalId,"rabbit")
        }
        return
    }
    val nest=unit.nests.firstOrNull{it.id==nestId}
    if(nest!=null) {
        AnimalFarmSelectionSheet("Nest ${nest.code}",{nestId=null}) {
            NestGlyph(nest.status,Modifier.size(52.dp))
            Text("Cage ${unit.code} · ${nestStateLabel(nest.status)}",style=AnimalFarmHtmlType.sectionTitle)
            Text(nest.doeLabel?.let{"Assigned doe: $it"} ?: "No doe assignment recorded",style=AnimalFarmHtmlType.body)
            nest.assignedAt?.let {Text("Assignment updated ${rabbitRecordDate(it)}",style=AnimalFarmHtmlType.caption)}
            val wave=records.waves.firstOrNull{it.id==nest.waveId}
            if(wave!=null) {
                RabbitWaveTimeline(wave,today)
                val kits=records.kits.count{it.waveId==wave.id}
                Text("$kits kit record(s) linked to this wave. Wave-level kit totals are not a count of occupants in this nest box.",style=AnimalFarmHtmlType.caption)
            }
            if(ports?.access?.record==true) FarmPrimaryButton({nestId=null;manage=true},Modifier.fillMaxWidth()) {Text("Manage nest assignment or state")}
        }
        return
    }
    AnimalFarmSelectionSheet("Cage ${unit.code}",onDismiss) {
        Text(rabbitUnitStage(unit,records,today),style=AnimalFarmHtmlType.sectionTitle)
        Text("${unit.recordedDoes} recorded does · capacity ${unit.doeCapacity}",style=AnimalFarmHtmlType.caption)
        FarmSectionTabs(listOf("Residents","Nest boxes","Services","Programme"),view,{view=it},tagPrefix="rabbit-unit-view:")
        when(view) {
            "Residents" -> {
                if(unit.residents.isEmpty()) AnimalFarmEmptyState("No occupants have been recorded. This is not a confirmed empty cage.")
                unit.residents.forEach { animal ->
                    val service=unit.openServices.any{it.buckId==animal.animalId}
                    FarmResourceAction(animal.label,{residentId=animal.animalId},detail="${animal.role} · ${animal.status}"+(if(service) " · in service" else ""),
                        modifier=Modifier.testTag("rabbit-unit-occupant:${animal.animalId}"),family=AnimalFarmFamily.RABBIT)
                }
            }
            "Nest boxes" -> {
                if(unit.nests.isEmpty()) AnimalFarmEmptyState("No nest boxes have been recorded for this cage")
                unit.nests.forEach { box -> FarmResourceAction(box.code,{nestId=box.id},detail=nestStateLabel(box.status)+" · "+(box.doeLabel ?: "Doe not assigned"),
                    modifier=Modifier.testTag("rabbit-unit-nest:${box.id}"),trailing={NestGlyph(box.status,Modifier.size(32.dp))}) }
            }
            "Services" -> {
                if(unit.services.isEmpty()) Text("No buck service intervals recorded",style=AnimalFarmHtmlType.body)
                unit.services.forEach { service ->
                    FarmRecordRow("${service.buckLabel} · ${if(service.endedAt==null) "In service" else "Ended"}\n${rabbitRecordDate(service.startedAt)}"+
                        (service.endedAt?.let{" – ${rabbitRecordDate(it)}"} ?: " · still open")+service.notes.takeIf{it.isNotBlank()}?.let{"\n$it"}.orEmpty(),
                        modifier=Modifier.testTag("rabbit-unit-service:${service.id}"))
                }
                Text("Observed matings",style=AnimalFarmHtmlType.sectionTitle)
                if(unit.matings.isEmpty()) Text("No individual pair mating has been recorded in this cage",style=AnimalFarmHtmlType.body)
                unit.matings.forEach { mating ->FarmRecordRow("${mating.doeLabel} × ${mating.buckLabel}\n${rabbitRecordDate(mating.occurredAt)} · ${mating.notes}",modifier=Modifier.testTag("rabbit-unit-mating:${mating.id}"))}
                Text("An observed mating is not confirmation of pregnancy or offspring parentage.",style=AnimalFarmHtmlType.caption,color=AnimalFarmTheme.colors.mutedInk)
            }
            else -> {
                val waves=records.waves.filter{it.id in unit.waveIds}.sortedByDescending{it.matingEpochDay}
                if(waves.isEmpty()) Text("No breeding wave plan linked to this cage",style=AnimalFarmHtmlType.body)
                waves.forEach {RabbitWaveTimeline(it,today)}
                Text("Wave outcomes belong to the recorded group. They are not automatically assigned to each resident doe.",style=AnimalFarmHtmlType.caption)
            }
        }
        if(ports!=null && (ports.access.record || ports.access.breed)) FarmPrimaryButton({manage=true},Modifier.fillMaxWidth().testTag("rabbit-unit-manage")) {Text("Manage this unit")}
    }
}
