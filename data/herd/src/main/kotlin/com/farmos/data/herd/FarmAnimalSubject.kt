package com.farmos.data.herd

import com.farmos.core.database.AnimalEntity
import com.farmos.core.database.FarmOsDatabase

/** Call inside the existing command transaction. An ID is not authority to cross a farm/species boundary. */
internal suspend fun FarmOsDatabase.requireFarmAnimalSubject(farmId:String, animalId:String?, speciesCode:String?=null):AnimalEntity? {
    if(animalId==null) return null
    require(animalId.isNotBlank()) { "An animal subject must have an exact record ID" }
    val animal=requireNotNull(animals().get(farmId,animalId)) { "The selected animal is not on this farm" }
    require(speciesCode==null || animal.speciesCode==speciesCode) { "The selected animal belongs to a different species" }
    return animal
}
