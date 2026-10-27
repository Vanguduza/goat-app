package com.farmos.app

import com.farmos.core.design.FarmHealthSubjectKind
import com.farmos.feature.goat.GoatEntryPage

/** Exact profile return using recorded sex/species. No invented rabbit sex or individual flock identity. */
internal fun profileHealthReturnRoute(model:ProfileHealthModel):FarmDestination? {
    val subject=model.subject
    if(subject.kind!=FarmHealthSubjectKind.ANIMAL) return null
    return when(subject.speciesCode) {
        "goat"->FarmDestination.Goat(GoatEntryPage.PROFILE,subject.id)
        "sheep"->FarmDestination.AnimalProfile(AnimalProfileKind.SHEEP,subject.id)
        "cattle"->FarmDestination.AnimalProfile(AnimalProfileKind.CATTLE,subject.id)
        "rabbit"->when(model.animalSex?.lowercase()) {
            "female","f"->FarmDestination.AnimalProfile(AnimalProfileKind.RABBIT_DOE,subject.id)
            "male","m"->FarmDestination.AnimalProfile(AnimalProfileKind.RABBIT_BUCK,subject.id)
            else->null
        }
        else->null
    }
}
