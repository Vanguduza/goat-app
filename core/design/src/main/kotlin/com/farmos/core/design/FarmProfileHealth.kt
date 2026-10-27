package com.farmos.core.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** A profile's existing subject identity. A flock ID is never an individual-animal ID. */
enum class FarmHealthSubjectKind { ANIMAL, FLOCK }
data class FarmHealthSubject(val id:String,val speciesCode:String,val kind:FarmHealthSubjectKind=FarmHealthSubjectKind.ANIMAL)
typealias FarmProfileHealthContent = @Composable (FarmHealthSubject)->Unit
val LocalFarmProfileHealth = staticCompositionLocalOf<FarmProfileHealthContent?> { null }

/** The app supplies authenticated farm-scoped reads and governed actions; design owns no data writes. */
@Composable
fun FarmProfileHealth(subject:FarmHealthSubject) {
    require(subject.id.isNotBlank())
    val content=LocalFarmProfileHealth.current
    if(content!=null) content(subject)
    else AnimalFarmEmptyState("Health information needs the current farm’s records. No health status has been inferred.")
}
