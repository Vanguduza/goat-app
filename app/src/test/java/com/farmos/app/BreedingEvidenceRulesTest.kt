package com.farmos.app

import com.farmos.domain.ops.*
import org.junit.Assert.*
import org.junit.Test

class BreedingEvidenceRulesTest {
    private fun animal(id:String,sex:String="MALE",birth:Long?=0)=BreedingAnimal(id,id,"rabbit",sex,"active",birth)
    private val dam=animal("dam","FEMALE")
    private fun parents()=mapOf("dam" to PedigreeParents("ds","dd"),"sire" to PedigreeParents("ss","sd"))

    @Test fun unknownAncestryDoesNotOutrankACandidateWithRecordedParents() {
        val c=PairingCriteria(minimumCompleteGenerations=2)
        val known=BreedingEvidence.assess(animal("sire"),dam,parents(),emptySet(),emptyList(),c,100)
        val unknown=BreedingEvidence.assess(animal("unknown"),dam,parents(),emptySet(),emptyList(),c,100)
        val result=BreedingEvidence.rank(listOf(unknown,known),c)
        assertEquals("sire",result.first().sire.id)
        assertEquals(1,result.first().rank)
        assertNull(result.last().rank)
        assertTrue(result.last().blockers.any{it.contains("complete generations")})
    }

    @Test fun fullSiblingMatingComputesOneQuarterNotAFalseZero() {
        val graph=mapOf("sire" to PedigreeParents("p","q"),"dam" to PedigreeParents("p","q"))
        val result=BreedingEvidence.assess(animal("sire"),dam,graph,emptySet(),emptyList(),PairingCriteria(),100)
        assertEquals(0.25,result.pedigree!!.coefficient,0.000001)
        assertEquals(setOf("p","q"),requireNotNull(result.pedigree).commonAncestors)
    }

    @Test fun incompleteOrConflictingPedigreeAndCyclesStayUnranked() {
        val c=PairingCriteria()
        val conflict=BreedingEvidence.assess(animal("sire"),dam,parents(),setOf("sire"),emptyList(),c,100)
        assertTrue(conflict.blockers.any{it.contains("Conflicting")})
        val cycle=parents()+mapOf("ss" to PedigreeParents("sire",null))
        assertTrue(runCatching{PedigreeInbreeding(cycle).inbreeding("sire")}.isFailure)
        val invalid=BreedingEvidence.assess(animal("sire"),dam,cycle,emptySet(),emptyList(),c,100)
        assertNull(invalid.pedigree)
        assertNull(BreedingEvidence.rank(listOf(conflict,invalid),c).first().rank)
    }

    @Test fun growthRequiresAnExplicitAgeWindowAndInbreedingCeiling() {
        assertTrue(runCatching{PairingCriteria(PairingObjective.OFFSPRING_GROWTH)}.isFailure)
        assertTrue(runCatching{PairingCriteria(PairingObjective.OFFSPRING_GROWTH,growthStartAgeDays=28,growthEndAgeDays=56)}.isFailure)
        assertTrue(runCatching{PairingCriteria(growthStartAgeDays=56,growthEndAgeDays=28)}.isFailure)
    }

    @Test fun offspringGrowthUsesTwoRealMatchedAgesAndShowsSampleSize() {
        val c=PairingCriteria(PairingObjective.OFFSPRING_GROWTH,maximumRecordedCoefficient=0.1,
            growthStartAgeDays=28,growthEndAgeDays=56,ageToleranceDays=0,minimumMeasuredOffspring=1)
        val child=BreedingOffspring(animal("kit","FEMALE",10),"sire","dam",listOf(
            OffspringWeight("w28",500,38),OffspringWeight("w56",1200,66),OffspringWeight("adult",3000,200)))
        val evidence=BreedingEvidence.offspring("sire",listOf(child),c,250)
        assertEquals(1,evidence.linked);assertEquals(1,evidence.growthSamples.size)
        assertEquals(25.0,evidence.meanGramsPerDay!!,0.000001)
        assertEquals("w28",evidence.growthSamples.single().firstRecordId)
        assertEquals(28L,evidence.growthSamples.single().firstAgeDays)
        assertEquals(1,evidence.distinctOtherParents)
    }

    @Test fun ambiguousWeightsUnknownBirthAndFutureMeasurementsAreNotInventedSamples() {
        val c=PairingCriteria(PairingObjective.OFFSPRING_GROWTH,maximumRecordedCoefficient=0.1,
            growthStartAgeDays=10,growthEndAgeDays=20,ageToleranceDays=0,minimumMeasuredOffspring=1)
        val rows=listOf(
            BreedingOffspring(animal("conflict","FEMALE",0),"sire","dam",listOf(OffspringWeight("a",500,10),OffspringWeight("b",600,10),OffspringWeight("c",900,20))),
            BreedingOffspring(animal("unknown","MALE",null),"sire","dam",listOf(OffspringWeight("d",600,10),OffspringWeight("e",900,20))),
            BreedingOffspring(animal("future","FEMALE",100),"sire","dam",listOf(OffspringWeight("f",600,110),OffspringWeight("g",900,120))),
        )
        val evidence=BreedingEvidence.offspring("sire",rows,c,30)
        assertEquals(3,evidence.linked);assertEquals(3,evidence.excludedFromGrowth);assertNull(evidence.meanGramsPerDay)
        assertTrue(evidence.growthSamples.isEmpty())
    }

    @Test fun tiedEvidenceSharesRankInsteadOfInventingPrecision() {
        val c=PairingCriteria()
        val graph=parents()+mapOf("sire2" to PedigreeParents("s2p","s2q"))
        val a=BreedingEvidence.assess(animal("sire"),dam,graph,emptySet(),emptyList(),c,100)
        val b=BreedingEvidence.assess(animal("sire2"),dam,graph,emptySet(),emptyList(),c,100)
        assertEquals(listOf(1,1),BreedingEvidence.rank(listOf(b,a),c).map{it.rank})
    }

    @Test fun poultryKindsAndAnimalStatusRemainHardComparisonBoundaries() {
        val c=PairingCriteria(minimumCompleteGenerations=1)
        val a=animal("sire").copy(species="poultry",poultryKind="chicken")
        val b=dam.copy(species="poultry",poultryKind="turkey")
        assertTrue(BreedingEvidence.assess(a,b,emptyMap(),emptySet(),emptyList(),c,100).blockers.any{it.contains("Poultry kind")})
        assertTrue(BreedingEvidence.assess(animal("sire").copy(status="sold"),dam,parents(),emptySet(),emptyList(),c,100).blockers.any{it.contains("active")})
    }
}
