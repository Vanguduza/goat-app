package com.farmos.app

import com.farmos.core.database.FarmOsDatabase
import com.farmos.data.herd.PedigreeQueries
import com.farmos.feature.goat.GoatMateAnalysis
import com.farmos.feature.goat.GoatMateCandidate

/**
 * The goat mate analysis over the local database (owner decision D-023, resolution R7): the buck's recorded
 * facts and the coefficient of inbreeding of the kids from recorded pedigree. Read-only.
 */
internal fun goatMateAnalysis(database: FarmOsDatabase, farmId: String): GoatMateAnalysis {
    val pedigree = PedigreeQueries(database, farmId)
    return GoatMateAnalysis { doeId, buckId ->
        val buck = requireNotNull(database.animals().get(farmId, buckId)) { "Buck not found on this farm" }
        require(buck.speciesCode == "goat" && buck.sex == "MALE") { "Choose a buck" }
        val analysis = pedigree.mating(buckId, doeId)
        val latestWeight = database.measurements().latest(farmId, buckId, "weight")
        GoatMateCandidate(
            buckId = buckId,
            label = listOfNotNull(buck.tag, buck.name).joinToString(" · "),
            dateOfBirthEpochDay = buck.dateOfBirthEpochDay,
            latestWeightGrams = latestWeight?.valueLong,
            coefficient = analysis.inbreeding.coefficient,
            generationsKnown = analysis.inbreeding.generationsKnown,
            commonAncestors = analysis.inbreeding.commonAncestors.map { id ->
                database.animals().get(farmId, id)?.let { listOfNotNull(it.tag, it.name).joinToString(" · ") } ?: "Unregistered animal"
            }.sorted(),
            conflictingParentage = analysis.conflictingParentage.size,
        )
    }
}
