package com.farmos.domain.ops

import java.math.BigDecimal

/** Evidence review, not diagnosis: no cross-species score thresholds or medication decisions. */
data class HealthWeightEvidence(val recordId:String,val grams:Long,val occurredAt:Long)
data class HealthReviewSignal(val code:String,val title:String,val explanation:String,val evidenceIds:List<String>,val action:HealthReviewAction)
enum class HealthReviewAction { RECORD_WEIGHT, RECORD_OBSERVATION, REVIEW_RECORDS, REVIEW_TASKS, REVIEW_PROTOCOLS }
data class AnimalHealthReviewInput(
    val speciesCode:String,val now:Long,val weights:List<HealthWeightEvidence>,
    val observationIds:List<String>,val recordedRedFlagIds:List<String>,val activeWithdrawalIds:List<String>,
    val dueTaskIds:List<String>,val vaccinationIds:List<String>,val acceptedProtocolIds:List<String>,
)

/** Product research: Health Module §§4–8 and Vet Intelligence §§4,6,8. Authoritative facts stay in records. */
object AnimalHealthReview {
    const val VERSION="record-evidence-review.v1"
    val supportedSpecies=setOf("goat","rabbit","sheep","cattle","poultry")
    fun signals(input:AnimalHealthReviewInput):List<HealthReviewSignal> {
        require(input.speciesCode in supportedSpecies)
        val result=mutableListOf<HealthReviewSignal>()
        fun signal(code:String,title:String,text:String,ids:List<String>,action:HealthReviewAction) {
            result+=HealthReviewSignal(code,title,text,ids,action)
        }
        if(input.recordedRedFlagIds.isNotEmpty()) signal("recorded_red_flag","Urgent concern was recorded",
            "Review the dated observation and confirm the animal’s current condition. A historical flag is not proof that a problem remains active or has resolved.",
            input.recordedRedFlagIds,HealthReviewAction.REVIEW_RECORDS)
        if(input.activeWithdrawalIds.isNotEmpty()) signal("withdrawal","Withdrawal restrictions are recorded",
            "Review each affected product and recorded end date. This review does not clear a product for use or sale.",input.activeWithdrawalIds,HealthReviewAction.REVIEW_RECORDS)
        if(input.dueTaskIds.isNotEmpty()) signal("due_follow_up","Linked follow-up is due",
            "Open the actual animal-linked task before performing or completing the work.",input.dueTaskIds,HealthReviewAction.REVIEW_TASKS)
        val valid=input.weights.filter { it.grams>0 && it.occurredAt<=input.now }.sortedWith(compareByDescending<HealthWeightEvidence>{it.occurredAt}.thenBy{it.recordId})
        if(valid.isEmpty()) signal("weight_baseline","Record a weight baseline",
            "No valid weight recorded on or before this review time is available for this animal. Missing weight is not zero weight.",emptyList(),HealthReviewAction.RECORD_WEIGHT)
        else {
            val latestAt=valid.first().occurredAt
            val sameTime=valid.filter { it.occurredAt==latestAt }
            if(sameTime.map{it.grams}.distinct().size>1) signal("weight_conflict","Review same-time weights",
                "Different weights share the latest timestamp. Resolve the source records before interpreting a trend.",sameTime.map{it.recordId},HealthReviewAction.REVIEW_RECORDS)
            else {
                val last=valid.first();val prior=valid.firstOrNull { it.occurredAt<last.occurredAt }
                if(prior!=null && last.grams<prior.grams) {
                    val change=BigDecimal.valueOf(prior.grams-last.grams,3).stripTrailingZeros().toPlainString()
                    signal("weight_decrease","Lower weight was recorded",
                        "The latest recorded weight is $change kg lower than the previous dated weight. Check the measurements and record current observations; this is not a diagnosis.",
                        listOf(prior.recordId,last.recordId),HealthReviewAction.RECORD_OBSERVATION)
                }
            }
        }
        if(input.observationIds.isEmpty()) signal("health_baseline","Add an animal-linked health observation",
            "There is no health observation linked to this individual. The app cannot infer that the animal is healthy from missing records.",emptyList(),HealthReviewAction.RECORD_OBSERVATION)
        if(input.vaccinationIds.isEmpty()) signal("vaccination_history","Check vaccination records",
            "No individual vaccination record is linked here. Check the applicable farm or group programme before assuming a vaccination was missed.",emptyList(),HealthReviewAction.REVIEW_PROTOCOLS)
        if(input.acceptedProtocolIds.isEmpty()) signal("protocol_review","Review the species health plan",
            "No vet-accepted protocol for this species is recorded on this farm. A qualified reviewer should establish the applicable plan; no schedule or treatment is generated automatically.",emptyList(),HealthReviewAction.REVIEW_PROTOCOLS)
        return result
    }
}
