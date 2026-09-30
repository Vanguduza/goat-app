package com.farmos.app

import com.farmos.core.database.FarmOsDatabase
import com.farmos.data.herd.PedigreeQueries
import com.farmos.feature.ops.MateCoiAnalysis
import com.farmos.feature.ops.MateCoiView

/**
 * Offspring inbreeding for one species over the local pedigree (owner decision D-023, resolution R7): both
 * parents must be animals of [speciesCode] on this farm, the dam female and the sire male. Read-only.
 */
internal fun speciesMateCoi(database: FarmOsDatabase, farmId: String, speciesCode: String): MateCoiAnalysis {
    val pedigree = PedigreeQueries(database, farmId)
    return MateCoiAnalysis { sireId, damId ->
        val sire = requireNotNull(database.animals().get(farmId, sireId)) { "Sire not found on this farm" }
        val dam = requireNotNull(database.animals().get(farmId, damId)) { "Dam not found on this farm" }
        require(sire.speciesCode == speciesCode && dam.speciesCode == speciesCode) { "Choose two animals of this species" }
        require(sire.sex == "MALE" && dam.sex == "FEMALE") { "Choose a female dam and a male sire" }
        val analysis = pedigree.mating(sireId, damId)
        MateCoiView(
            coefficient = analysis.inbreeding.coefficient,
            generationsKnown = analysis.inbreeding.generationsKnown,
            commonAncestors = analysis.inbreeding.commonAncestors.map { id ->
                database.animals().get(farmId, id)?.let { listOfNotNull(it.tag, it.name).joinToString(" · ") } ?: "Unregistered animal"
            }.sorted(),
            conflictingParentage = analysis.conflictingParentage.size,
        )
    }
}
