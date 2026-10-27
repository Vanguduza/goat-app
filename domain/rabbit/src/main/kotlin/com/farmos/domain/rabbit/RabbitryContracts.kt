package com.farmos.domain.rabbit

import kotlinx.serialization.Serializable

/** Versioned actual housing, independent of a cage's planned programme or genetic parentage. */
@Serializable
data class SetRabbitHousing(val animalId:String,val cageId:String?,val role:String?,val expectedVersion:Long,
    val occurredAtEpochMillis:Long,val reason:String="")
@Serializable
data class StartRabbitBuckService(val serviceId:String,val cageId:String,val buckId:String,val waveId:String?=null,
    val startedAtEpochMillis:Long,val notes:String="")
@Serializable
data class EndRabbitBuckService(val serviceId:String,val expectedVersion:Long,val endedAtEpochMillis:Long)
@Serializable
data class RecordRabbitPairMating(val matingId:String,val cageId:String,val doeId:String,val buckId:String,
    val waveId:String?=null,val matedAtEpochMillis:Long,val notes:String="")
@Serializable
data class SetRabbitNestAssignment(val boxId:String,val doeId:String?,val waveId:String?=null,
    val expectedVersion:Long,val occurredAtEpochMillis:Long)

object RabbitryRules {
    val housingRoles=setOf("doe","buck","growout")
    fun sexAllowsRole(sex:String,role:String):Boolean=when(role) {
        "doe"->sex.equals("FEMALE",true) || sex.equals("F",true)
        "buck"->sex.equals("MALE",true) || sex.equals("M",true)
        "growout"->true
        else->false
    }
    fun requireRecordedTime(at:Long,commandTime:Long) {
        require(at>0 && at<=commandTime) { "A recorded event cannot be in the future of its original command" }
    }
}
