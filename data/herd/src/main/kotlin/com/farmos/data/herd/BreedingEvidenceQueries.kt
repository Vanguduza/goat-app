package com.farmos.data.herd

import com.farmos.core.database.*
import com.farmos.domain.ops.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class BreedingLineage(val rootId:String,val animals:Map<String,BreedingAnimal>,val parents:Map<String,PedigreeParents>,
    val conflicting:Set<String>,val missing:Set<String>,val offspring:List<BreedingAnimal>,val warnings:List<String>)
data class PairingComparison(val dam:BreedingAnimal,val criteria:PairingCriteria,val assessed:Int,
    val rows:List<PairingAssessment>,val offspring:List<BreedingOffspring>,val generatedEpochDay:Long)

/** Complete farm-scoped reads. Pagination may limit presentation, never the candidates being assessed. */
class BreedingEvidenceQueries(private val database:FarmOsDatabase,private val farmId:String) {
    private val pedigree=PedigreeQueries(database,farmId)
    suspend fun lineage(id:String,generations:Int=4):BreedingLineage=withContext(Dispatchers.IO) {
        require(generations in 1..10)
        val root=requireNotNull(database.animals().get(farmId,id)) { "Animal not on the current farm" }
        val raw=pedigree.graph(listOf(id),generations)
        val ids=(raw.parents.keys+raw.parents.values.flatMap{listOfNotNull(it.sireId,it.damId)}+id).toSet()
        val animals=readAnimals(ids).filterValues { it.speciesCode==root.speciesCode }
        val conflicts=raw.conflicting.toMutableSet();val warnings=mutableListOf<String>()
        val clean=raw.parents.mapValues { (child,p) ->
            fun parent(value:String?,sex:String):String? {
                if(value==null)return null
                val a=animals[value]
                if(a==null || a.sex.uppercase() !in if(sex=="M") setOf("MALE","M") else setOf("FEMALE","F")) {
                    conflicts+=child;return null
                }
                return value
            }
            PedigreeParents(parent(p.sireId,"M"),parent(p.damId,"F"))
        }
        if(ids.any{it !in animals}) warnings+="Some referenced ancestors are absent or belong to a different species; their identities are not inferred"
        try { PedigreeInbreeding(clean).inbreeding(id) } catch(_:IllegalArgumentException) { warnings+="Pedigree cycle requires review; repeated branches stop in the tree" }
        val children=geneticChildren(id,root.speciesCode)
        BreedingLineage(id,animals.mapValues{it.value.breeding()},clean,conflicts,ids-animals.keys,children.map{it.first.breeding()},warnings)
    }

    suspend fun compare(damId:String,criteria:PairingCriteria,today:Long=LocalDate.now().toEpochDay()):PairingComparison=withContext(Dispatchers.IO) {
        val dam=requireNotNull(database.animals().get(farmId,damId)) { "Dam not on current farm" }
        require(dam.sex.uppercase() in setOf("FEMALE","F") && dam.status=="active") { "Choose an active female" }
        val candidates=database.animals().search(farmId,dam.speciesCode,null,null,-1,0)
            .filter { it.status=="active" && it.sex.uppercase() in setOf("MALE","M") }
        val graph=pedigree.graph(candidates.map{it.id}+dam.id)
        val ids=(graph.parents.keys+graph.parents.values.flatMap{listOfNotNull(it.sireId,it.damId)}+candidates.map{it.id}+dam.id).toSet()
        val animals=readAnimals(ids)
        val conflicts=graph.conflicting.toMutableSet()
        val parents=graph.parents.mapValues { (child,p) ->
            fun valid(id:String?,male:Boolean):String? {
                if(id==null)return null
                val a=animals[id]
                if(a==null || a.speciesCode!=dam.speciesCode || a.sex.uppercase() !in if(male) setOf("MALE","M") else setOf("FEMALE","F")) {
                    conflicts+=child;return null
                }
                return id
            }
            PedigreeParents(valid(p.sireId,true),valid(p.damId,false))
        }
        val children=linkedMapOf<String,Pair<AnimalEntity,PedigreeParents>>()
        for(parent in candidates+dam) for(child in geneticChildren(parent.id,dam.speciesCode)) children[child.first.id]=child
        val zone=ZoneId.systemDefault()
        val offspring=children.values.map { (animal,p) ->
            val weights=if(criteria.growthStartAgeDays==null) emptyList() else {
                val generic=database.measurements().history(farmId,animal.id,"weight").filter{it.unit=="g"}.map {
                    OffspringWeight("measurement:${it.id}",it.valueLong,Instant.ofEpochMilli(it.measuredAtEpochMillis).atZone(zone).toLocalDate().toEpochDay())
                }
                generic+if(animal.speciesCode=="rabbit") database.lifecycle().rabbitWeightHistory(farmId,animal.id).mapNotNull {
                    runCatching{OffspringWeight("rabbit-weight:${it.id}",BigDecimal.valueOf(it.weightKg).movePointRight(3).longValueExact(),
                        Instant.ofEpochMilli(it.weighedAtEpochMillis).atZone(zone).toLocalDate().toEpochDay())}.getOrNull()
                } else emptyList()
            }
            BreedingOffspring(animal.breeding(),p.sireId,p.damId,weights)
        }
        val services=if(dam.speciesCode=="rabbit") database.rabbitry().services(farmId).filter{it.endedAtEpochMillis==null}.groupBy{it.buckId} else emptyMap()
        val rows=candidates.map { sire ->
            val warnings=mutableListOf<String>()
            val flagged=database.healthObservations().forAnimal(farmId,sire.id).filter{it.redFlag}
            if(flagged.isNotEmpty()) warnings+="${flagged.size} dated urgent concern(s) recorded for this sire; review current health before breeding"
            if(services[sire.id].orEmpty().isNotEmpty()) warnings+="This buck has an open recorded service interval; check the cage board before another service"
            BreedingEvidence.assess(sire.breeding(),dam.breeding(),parents,conflicts,offspring,criteria,today,warnings)
        }
        PairingComparison(dam.breeding(),criteria,candidates.size,BreedingEvidence.rank(rows,criteria),offspring,today)
    }

    private suspend fun geneticChildren(parentId:String,species:String):List<Pair<AnimalEntity,PedigreeParents>> {
        val refs=database.lifecycle().pedigreeChildren(farmId,parentId).filter{it.relationType in setOf("sire","dam","genetic_dam")}
        val animals=readAnimals(refs.map{it.animalId}.toSet())
        val found=mutableListOf<Pair<AnimalEntity,PedigreeParents>>()
        for(child in animals.values.filter{it.speciesCode==species}) {
            val rows=database.lifecycle().pedigreeParents(farmId,child.id)
            val sire=rows.filter{it.relationType=="sire"}.map{it.parentId}.distinct()
            val dam=rows.filter{it.relationType=="genetic_dam"}.ifEmpty{rows.filter{it.relationType=="dam"}}.map{it.parentId}.distinct()
            if(sire.size>1 || dam.size>1) continue
            val parents=PedigreeParents(sire.singleOrNull(),dam.singleOrNull())
            if(parentId==parents.sireId || parentId==parents.damId) found+=child to parents
        }
        return found.sortedWith(compareBy({it.first.tag},{it.first.id}))
    }
    private suspend fun readAnimals(ids:Set<String>):Map<String,AnimalEntity> = ids.toList().chunked(300).flatMap { database.animals().getMany(farmId,it) }.associateBy{it.id}
    private fun AnimalEntity.breeding()=BreedingAnimal(id,listOfNotNull(tag,name?.takeIf{it.isNotBlank()}).joinToString(" · "),speciesCode,sex,status,dateOfBirthEpochDay,poultryKindCode)
}
