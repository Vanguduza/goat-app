package com.farmos.data.herd

import com.farmos.core.database.FarmOsDatabase
import com.farmos.domain.ops.MatingInbreeding
import com.farmos.domain.ops.PedigreeInbreeding
import com.farmos.domain.ops.PedigreeParents

/**
 * Recorded genetic parentage around some animals. [conflicting] lists animals with more than one different
 * sire or genetic dam recorded; their parentage is left unknown rather than guessed, and is reported.
 */
data class PedigreeGraph(val parents: Map<String, PedigreeParents>, val conflicting: Set<String>)

/** A prospective mating's inbreeding, with the parentage conflicts that were left out of it. */
data class MatingAnalysis(val inbreeding: MatingInbreeding, val conflictingParentage: Set<String>)

/** Pedigree reads for breeding analysis (owner decision D-023, resolution R7); read-only. */
class PedigreeQueries(private val database: FarmOsDatabase, private val farmId: String) {
    /** The coefficient of inbreeding of an offspring of [sireId] and [damId] from the recorded pedigree. */
    suspend fun mating(sireId: String, damId: String): MatingAnalysis {
        val graph = graph(listOf(sireId, damId))
        return MatingAnalysis(PedigreeInbreeding(graph.parents).mating(sireId, damId), graph.conflicting)
    }

    /** Reads parentage upward from [roots] for at most [generations] generations. */
    suspend fun graph(roots: Collection<String>, generations: Int = MAX_GENERATIONS): PedigreeGraph {
        val parents = HashMap<String, PedigreeParents>()
        val conflicting = HashSet<String>()
        val seen = HashSet<String>(roots)
        var frontier = roots.toList()
        repeat(generations) {
            val next = ArrayList<String>()
            for (id in frontier) {
                val rows = database.lifecycle().pedigreeParents(farmId, id)
                val sires = rows.filter { it.relationType == "sire" }.map { it.parentId }.distinct()
                // The genetic dam: a recorded genetic_dam (embryo transfer) outranks the dam who carried the kid.
                val damRows = rows.filter { it.relationType == "genetic_dam" }.ifEmpty { rows.filter { it.relationType == "dam" } }
                val dams = damRows.map { it.parentId }.distinct()
                if (sires.size > 1 || dams.size > 1) conflicting += id
                val sire = sires.singleOrNull()
                val dam = dams.singleOrNull()
                if (sire != null || dam != null) parents[id] = PedigreeParents(sire, dam)
                listOfNotNull(sire, dam).filter { seen.add(it) }.forEach { next += it }
            }
            frontier = next
        }
        return PedigreeGraph(parents, conflicting)
    }

    companion object {
        /** Generations read above each parent; older records change the coefficient very little. */
        const val MAX_GENERATIONS = 10
    }
}
