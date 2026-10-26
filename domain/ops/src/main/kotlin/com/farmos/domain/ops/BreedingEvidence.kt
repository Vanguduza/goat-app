package com.farmos.domain.ops

import kotlin.math.abs

enum class PairingObjective { LOWER_RECORDED_INBREEDING, OFFSPRING_GROWTH }

/** Explicit owner criteria, not a universal biological recommendation or an estimated breeding value. */
data class PairingCriteria(
    val objective:PairingObjective=PairingObjective.LOWER_RECORDED_INBREEDING,
    val minimumCompleteGenerations:Int=2,
    val maximumRecordedCoefficient:Double?=null,
    val growthStartAgeDays:Int?=null,val growthEndAgeDays:Int?=null,
    val ageToleranceDays:Int=3,val minimumMeasuredOffspring:Int=3,
) {
    init {
        require(minimumCompleteGenerations in 1..8)
        require(maximumRecordedCoefficient==null || maximumRecordedCoefficient.isFinite() && maximumRecordedCoefficient in 0.0..1.0)
        require(minimumMeasuredOffspring in 1..10000 && ageToleranceDays in 0..90)
        require((growthStartAgeDays==null)==(growthEndAgeDays==null))
        if(growthStartAgeDays!=null) require(growthStartAgeDays>=0 && requireNotNull(growthEndAgeDays)>growthStartAgeDays)
        if(objective==PairingObjective.OFFSPRING_GROWTH) require(growthStartAgeDays!=null && maximumRecordedCoefficient!=null) {
            "Choose growth ages and your recorded-inbreeding ceiling before ranking by offspring growth"
        }
    }
}

data class BreedingAnimal(val id:String,val label:String,val species:String,val sex:String,val status:String,
    val birthEpochDay:Long?,val poultryKind:String?=null)
data class OffspringWeight(val id:String,val grams:Long,val epochDay:Long)
data class BreedingOffspring(val animal:BreedingAnimal,val sireId:String?,val geneticDamId:String?,val weights:List<OffspringWeight>)
data class OffspringGrowthSample(val offspringId:String,val label:String,val firstRecordId:String,val lastRecordId:String,
    val firstAgeDays:Long,val lastAgeDays:Long,val gramsPerDay:Double,val otherParentId:String?)
data class OffspringEvidence(val linked:Int,val active:Int,val exitedOrOther:Int,val growthSamples:List<OffspringGrowthSample>,
    val excludedFromGrowth:Int,val meanGramsPerDay:Double?,val distinctOtherParents:Int)
data class PairingAssessment(val sire:BreedingAnimal,val dam:BreedingAnimal,val pedigree:MatingInbreeding?,
    val sireOffspring:OffspringEvidence,val damOffspring:OffspringEvidence,val sharedOffspring:Int,
    val blockers:List<String>,val warnings:List<String>,val sourceConflictIds:Set<String>,val rank:Int?=null)

/** Compares measured phenotypes only. Nutrition, management, sex and environment are not adjusted here. */
object BreedingEvidence {
    const val VERSION="pedigree-and-offspring.v1"
    fun offspring(parentId:String,rows:List<BreedingOffspring>,criteria:PairingCriteria,today:Long):OffspringEvidence {
        val linked=rows.filter { it.sireId==parentId || it.geneticDamId==parentId }.distinctBy{it.animal.id}
        val samples=linked.mapNotNull { growth(parentId,it,criteria,today) }
        return OffspringEvidence(linked.size,linked.count{it.animal.status=="active"},linked.count{it.animal.status!="active"},
            samples,linked.size-samples.size,samples.takeIf{it.isNotEmpty()}?.map{it.gramsPerDay}?.average(),
            samples.mapNotNull{it.otherParentId}.distinct().size)
    }

    private fun growth(parentId:String,child:BreedingOffspring,c:PairingCriteria,today:Long):OffspringGrowthSample? {
        val birth=child.animal.birthEpochDay ?: return null
        val from=c.growthStartAgeDays ?: return null;val to=c.growthEndAgeDays ?: return null
        if(birth>today) return null
        val weights=child.weights.filter{it.grams>0 && it.epochDay in birth..today}.distinctBy{it.id}
        // Conflicting values on a selected date are not silently averaged or picked by insertion order.
        fun nearest(target:Int):OffspringWeight? {
            val candidate=weights.filter{abs(it.epochDay-birth-target)<=c.ageToleranceDays}
                .minWithOrNull(compareBy<OffspringWeight>{abs(it.epochDay-birth-target)}.thenBy{it.epochDay}.thenBy{it.id}) ?: return null
            if(weights.filter{it.epochDay==candidate.epochDay}.map{it.grams}.distinct().size!=1) return null
            return candidate
        }
        val a=nearest(from) ?: return null;val b=nearest(to) ?: return null
        if(b.epochDay<=a.epochDay) return null
        val rate=(b.grams.toDouble()-a.grams)/(b.epochDay-a.epochDay)
        if(!rate.isFinite()) return null
        return OffspringGrowthSample(child.animal.id,child.animal.label,a.id,b.id,a.epochDay-birth,b.epochDay-birth,rate,
            if(child.sireId==parentId) child.geneticDamId else child.sireId)
    }

    fun assess(sire:BreedingAnimal,dam:BreedingAnimal,parents:Map<String,PedigreeParents>,conflicts:Set<String>,
        offspring:List<BreedingOffspring>,criteria:PairingCriteria,today:Long,sourceWarnings:List<String> = emptyList()):PairingAssessment {
        val issues=mutableListOf<String>();val warnings=sourceWarnings.toMutableList()
        if(sire.id==dam.id || sire.species!=dam.species) issues+="Select two different animals of the same species"
        if(sire.status!="active" || dam.status!="active") issues+="Only active candidate records may be compared for a new pairing"
        if(sire.sex.uppercase() !in setOf("MALE","M") || dam.sex.uppercase() !in setOf("FEMALE","F")) issues+="Recorded sire/dam sex must be confirmed"
        if(sire.species=="poultry" && (sire.poultryKind==null || dam.poultryKind==null || sire.poultryKind!=dam.poultryKind)) issues+="Poultry kind must be known and matching; unlike poultry kinds are not ranked together"
        if(sire.birthEpochDay==null || dam.birthEpochDay==null) warnings+="Birth date missing: breeding maturity cannot be inferred"
        if(listOfNotNull(sire.birthEpochDay,dam.birthEpochDay).any{it>today}) issues+="A parent birth date is in the future"
        fun ancestors(id:String):Set<String> {
            val seen=mutableSetOf<String>();val q=ArrayDeque<String>();q.add(id)
            while(q.isNotEmpty()) { val n=q.removeFirst();if(!seen.add(n))continue;parents[n]?.let { listOfNotNull(it.sireId,it.damId).forEach(q::add) } }
            return seen
        }
        val used=ancestors(sire.id)+ancestors(dam.id)
        val relevantConflicts=conflicts.intersect(used)
        if(relevantConflicts.isNotEmpty()) issues+="Conflicting or invalid parentage needs review before ranking"
        val pedigree=if(sire.id==dam.id) null else try { PedigreeInbreeding(parents).mating(sire.id,dam.id) }
            catch(_:IllegalArgumentException) { issues+="The pedigree contains a cycle; no coefficient is accepted";null }
        if(pedigree!=null) {
            if(pedigree.generationsKnown<criteria.minimumCompleteGenerations) issues+="Only ${pedigree.generationsKnown} complete generations; your minimum is ${criteria.minimumCompleteGenerations}"
            if(criteria.maximumRecordedCoefficient!=null && pedigree.coefficient>criteria.maximumRecordedCoefficient) issues+="Recorded offspring inbreeding exceeds your selected ceiling"
            if(sire.id in ancestors(dam.id) || dam.id in ancestors(sire.id)) warnings+="A direct ancestor/descendant relationship is recorded"
            if(pedigree.commonAncestors.isNotEmpty()) warnings+="${pedigree.commonAncestors.size} common recorded ancestor(s); inspect the connected pedigree"
        }
        val sireEvidence=BreedingEvidence.offspring(sire.id,offspring,criteria,today);val damEvidence=BreedingEvidence.offspring(dam.id,offspring,criteria,today)
        if(criteria.objective==PairingObjective.OFFSPRING_GROWTH && sireEvidence.growthSamples.size<criteria.minimumMeasuredOffspring)
            issues+="Only ${sireEvidence.growthSamples.size} offspring have comparable age-window weights; your minimum is ${criteria.minimumMeasuredOffspring}"
        warnings+="Recorded inbreeding assumes unknown founders are unrelated; a low value is not proof of unrelatedness"
        warnings+="Offspring performance is a recorded phenotype, not a genetic breeding value or a prediction for this mating"
        val shared=offspring.count { it.sireId==sire.id && it.geneticDamId==dam.id }
        return PairingAssessment(sire,dam,pedigree,sireEvidence,damEvidence,shared,issues.distinct(),warnings.distinct(),relevantConflicts)
    }

    fun rank(rows:List<PairingAssessment>,criteria:PairingCriteria):List<PairingAssessment> {
        val eligible=rows.filter{it.blockers.isEmpty() && it.pedigree!=null}
        val comparator=if(criteria.objective==PairingObjective.LOWER_RECORDED_INBREEDING)
            compareBy<PairingAssessment>{it.pedigree!!.coefficient}.thenByDescending{it.pedigree!!.generationsKnown}.thenBy{it.sire.label}.thenBy{it.sire.id}
        else compareByDescending<PairingAssessment>{it.sireOffspring.meanGramsPerDay ?: Double.NEGATIVE_INFINITY}
            .thenBy{it.pedigree!!.coefficient}.thenByDescending{it.sireOffspring.growthSamples.size}.thenBy{it.sire.label}.thenBy{it.sire.id}
        val ordered=eligible.sortedWith(comparator)
        // Equal scores share rank; stable labels are a display order, not evidence of superiority.
        var lastKey:List<Any?>?=null;var rank=0
        val ranked=ordered.mapIndexed { index,row ->
            val key=if(criteria.objective==PairingObjective.LOWER_RECORDED_INBREEDING) listOf(row.pedigree!!.coefficient)
                else listOf(row.sireOffspring.meanGramsPerDay,row.pedigree!!.coefficient)
            if(key!=lastKey){rank=index+1;lastKey=key};row.copy(rank=rank)
        }
        return ranked+rows.filter{it.blockers.isNotEmpty() || it.pedigree==null}.sortedWith(compareBy({it.sire.label},{it.sire.id})).map{it.copy(rank=null)}
    }
}
