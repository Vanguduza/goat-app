package com.farmos.feature.rabbit

import androidx.compose.runtime.staticCompositionLocalOf
import com.farmos.core.design.FarmSelectorSearch
import java.time.LocalDate

data class RabbitResidentView(val animalId:String,val label:String,val sex:String,val status:String,val role:String,
    val housingVersion:Long,val housedAt:Long,val lastMatingAt:Long?=null,val lastMateLabel:String?=null)
data class RabbitServiceView(val id:String,val buckId:String,val buckLabel:String,val waveId:String?,val startedAt:Long,
    val endedAt:Long?,val version:Long,val notes:String)
data class RabbitPairMatingView(val id:String,val doeId:String,val doeLabel:String,val buckId:String,val buckLabel:String,
    val waveId:String?,val occurredAt:Long,val notes:String)
data class RabbitUnitNestView(val id:String,val code:String,val status:String,val doeId:String?,val doeLabel:String?,
    val waveId:String?,val assignmentVersion:Long,val assignedAt:Long?)
data class RabbitUnitView(val cageId:String,val code:String,val doeCapacity:Int,val residents:List<RabbitResidentView>,
    val nests:List<RabbitUnitNestView>,val services:List<RabbitServiceView>,val matings:List<RabbitPairMatingView>,val waveIds:List<String>) {
    val openServices get()=services.filter{it.endedAt==null}
    val recordedDoes get()=residents.count{it.role=="doe"}
}
data class RabbitHousingChoice(val animalId:String,val cageId:String?,val cageLabel:String?,val role:String?,val version:Long)
data class RabbitryAccess(val record:Boolean=false,val breed:Boolean=false)
val LocalRabbitryAccess=staticCompositionLocalOf { RabbitryAccess() }

/** Feature callbacks to the existing authenticated command/journal owner, never direct database access. */
data class RabbitryPorts(
    val access:RabbitryAccess,val searchRabbits:FarmSelectorSearch,val searchDoes:FarmSelectorSearch,val searchBucks:FarmSelectorSearch,
    val housing:suspend (String)->RabbitHousingChoice,
    val setHousing:suspend (String,String?,String?,Long,String)->String?,
    val startService:suspend (String,String,String?,String)->String?,
    val endService:suspend (String,Long)->String?,
    val recordMating:suspend (String,String,String,String?,LocalDate,String)->String?,
    val assignNest:suspend (String,String?,String?,Long)->String?,
    val setNestStatus:suspend (String,String)->String?,
)
val LocalRabbitryPorts=staticCompositionLocalOf<RabbitryPorts?> { null }

/** Describes recorded facts only. A passed planned date does not create a biological transition. */
fun rabbitUnitStage(unit:RabbitUnitView,records:RabbitRecords,today:LocalDate):String {
    if(unit.residents.any{it.status!="active"}) return "Placement needs review"
    if(unit.openServices.isNotEmpty()) return "Buck in service"
    if(unit.nests.any{it.status=="in_cage"}) return "Nest in cage"
    val waves=records.waves.filter{it.id in unit.waveIds}
    val current=rabbitRelevantWave(waves,today)
    if(current!=null) {
        if(current.events.any{it.kind=="Weaning" && it.epochDay<=today.toEpochDay()}) return "Weaning recorded"
        if(current.events.any{it.kind=="Kindling" && it.epochDay<=today.toEpochDay()}) return "Kindling recorded"
        if(current.events.any{it.kind=="Palpation" && it.epochDay<=today.toEpochDay()}) return "Wave palpation recorded"
        if(unit.matings.any{it.waveId==current.id}) return "Mating recorded"
    }
    if(unit.residents.any{it.role=="growout"}) return "Grow-out occupants"
    if(unit.residents.isNotEmpty()) return "Residents recorded"
    return "Occupants not recorded"
}
