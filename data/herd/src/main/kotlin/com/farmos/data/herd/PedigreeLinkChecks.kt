package com.farmos.data.herd

import com.farmos.core.database.FarmOsDatabase
import com.farmos.domain.ops.LinkPedigree

internal suspend fun FarmOsDatabase.requireNewPedigreeLink(farmId:String,command:LinkPedigree) {
    val child=requireNotNull(animals().get(farmId,command.animalId)) { "Child is not on this farm" }
    val parent=requireNotNull(animals().get(farmId,command.parentId)) { "Parent is not on this farm" }
    require(child.id!=parent.id && child.speciesCode==parent.speciesCode) { "Parent and child must be different animals of the same species" }
    require(if(command.relationType=="sire") parent.sex.equals("MALE",true) || parent.sex.equals("M",true)
        else parent.sex.equals("FEMALE",true) || parent.sex.equals("F",true)) { "The parent’s recorded sex does not match this relationship" }
    val childDay=child.dateOfBirthEpochDay;val parentDay=parent.dateOfBirthEpochDay
    if(childDay!=null && parentDay!=null)
        require(parentDay<childDay) { "Parent birth date must precede the child’s" }
    val seen=mutableSetOf<String>();val pending=ArrayDeque<String>();pending.add(parent.id)
    while(pending.isNotEmpty()) {
        val id=pending.removeFirst();require(id!=child.id) { "This parent link would create a pedigree cycle" }
        if(!seen.add(id)) continue
        require(seen.size<=10000) { "The pedigree is too large for safe local linking; review its structure first" }
        lifecycle().pedigreeParents(farmId,id).filter { it.relationType in setOf("sire","dam","genetic_dam") }.forEach { pending.add(it.parentId) }
    }
}
