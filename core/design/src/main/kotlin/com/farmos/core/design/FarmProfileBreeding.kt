package com.farmos.core.design

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

enum class FarmBreedingView { PEDIGREE, PAIRINGS }
typealias FarmBreedingOpen=(String?,String?,FarmBreedingView)->Unit
val LocalFarmBreedingOpen=staticCompositionLocalOf<FarmBreedingOpen?> { null }
typealias FarmLineageContent = @Composable (String,String?)->Unit
val LocalFarmLineageContent=staticCompositionLocalOf<FarmLineageContent?> { null }

/** Contextual, same-species breeding evidence. It does not mate animals or write parentage. */
@Composable
fun FarmProfileBreeding(animalId:String,species:String) {
    val open=LocalFarmBreedingOpen.current ?: return
    FarmIllustratedSectionSurface(Modifier.fillMaxWidth().testTag("profile-breeding:$animalId")) {
        Text("Breeding & lineage",style=AnimalFarmHtmlType.sectionTitle)
        FarmResourceAction("Pedigree tree",{open(animalId,species,FarmBreedingView.PEDIGREE)},detail="Parents, ancestors and linked offspring")
        FarmResourceAction("Compare breeding pairings",{open(animalId,species,FarmBreedingView.PAIRINGS)},detail="Recorded relatedness, offspring evidence and your selected objective")
    }
}
