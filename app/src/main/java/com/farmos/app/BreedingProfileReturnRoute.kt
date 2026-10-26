package com.farmos.app

import com.farmos.core.database.AnimalEntity
import com.farmos.feature.goat.GoatEntryPage

/** Resolved from the exact farm record, not guessed from a candidate label or sex default. */
internal fun breedingProfileReturnRoute(animal:AnimalEntity):FarmDestination?=when(animal.speciesCode) {
    "goat"->FarmDestination.Goat(GoatEntryPage.PROFILE,animal.id)
    "sheep"->FarmDestination.AnimalProfile(AnimalProfileKind.SHEEP,animal.id)
    "cattle"->FarmDestination.AnimalProfile(AnimalProfileKind.CATTLE,animal.id)
    "rabbit"->when(animal.sex.uppercase()) {
        "FEMALE","F"->FarmDestination.AnimalProfile(AnimalProfileKind.RABBIT_DOE,animal.id)
        "MALE","M"->FarmDestination.AnimalProfile(AnimalProfileKind.RABBIT_BUCK,animal.id)
        else->null
    }
    else->null
}
