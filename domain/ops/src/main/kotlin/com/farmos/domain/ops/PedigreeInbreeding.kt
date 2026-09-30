package com.farmos.domain.ops

/** The recorded parents of one animal; either may be unknown. */
data class PedigreeParents(val sireId: String?, val damId: String?)

/**
 * The coefficient of inbreeding of a prospective offspring (owner decision D-023, resolution R7).
 * [coefficient] is Wright's F in 0..1; [generationsKnown] counts the complete generations of the
 * offspring's recorded pedigree (both parents known at every position), so a low figure from a thin
 * pedigree is not mistaken for an unrelated mating.
 */
data class MatingInbreeding(
    val coefficient: Double,
    val generationsKnown: Int,
    val ancestorsKnown: Int,
    val commonAncestors: Set<String>,
)

/**
 * Wright's coefficient of inbreeding from recorded pedigree. The offspring's F is the coancestry of its
 * parents, computed by the recursive (tabular) form of the path method: coancestry(a, a) = (1 + F(a)) / 2
 * and, where a is not an ancestor of b, coancestry(a, b) = (coancestry(sire(a), b) + coancestry(dam(a), b)) / 2.
 * This sums every path through every common ancestor, which is what the path method counts. Unknown
 * parents contribute nothing, so F is a lower bound that grows as the pedigree is recorded.
 */
class PedigreeInbreeding(private val parentsOf: Map<String, PedigreeParents>) {
    private val depthCache = HashMap<String, Int>()
    private val coancestryCache = HashMap<Pair<String, String>, Double>()

    fun mating(sireId: String, damId: String): MatingInbreeding {
        require(sireId != damId) { "An animal cannot be mated with itself" }
        depth(sireId)
        depth(damId)
        val sireLine = ancestors(sireId) + sireId
        val damLine = ancestors(damId) + damId
        return MatingInbreeding(
            coefficient = coancestry(sireId, damId),
            generationsKnown = completeGenerations(PedigreeParents(sireId, damId)),
            ancestorsKnown = (sireLine + damLine).size,
            commonAncestors = sireLine.intersect(damLine),
        )
    }

    /** F of a recorded animal: the coancestry of its parents. */
    fun inbreeding(id: String): Double {
        val parents = parentsOf[id] ?: return 0.0
        val sire = parents.sireId ?: return 0.0
        val dam = parents.damId ?: return 0.0
        return coancestry(sire, dam)
    }

    private fun coancestry(a: String, b: String): Double {
        if (a == b) return 0.5 * (1.0 + inbreeding(a))
        val key = if (a < b) a to b else b to a
        coancestryCache[key]?.let { return it }
        // Expand the younger animal (the greater depth), which can never be an ancestor of the other.
        val (young, other) = if (depth(a) >= depth(b)) a to b else b to a
        val parents = parentsOf[young]
        val value = if (parents == null) {
            0.0
        } else {
            0.5 * ((parents.sireId?.let { coancestry(it, other) } ?: 0.0) + (parents.damId?.let { coancestry(it, other) } ?: 0.0))
        }
        coancestryCache[key] = value
        return value
    }

    /** Longest recorded path from the animal back to a founder; an ancestor always has a smaller depth. */
    private fun depth(id: String, visiting: MutableSet<String> = HashSet()): Int {
        depthCache[id]?.let { return it }
        require(visiting.add(id)) { "The recorded pedigree contains a cycle at $id" }
        val parents = parentsOf[id]
        val value = if (parents == null) 0 else 1 + maxOf(parents.sireId?.let { depth(it, visiting) } ?: -1, parents.damId?.let { depth(it, visiting) } ?: -1)
        visiting.remove(id)
        depthCache[id] = value
        return value
    }

    private fun ancestors(id: String): Set<String> {
        val found = LinkedHashSet<String>()
        var frontier = listOf(id)
        while (frontier.isNotEmpty()) {
            frontier = frontier.flatMap { listOfNotNull(parentsOf[it]?.sireId, parentsOf[it]?.damId) }.filter { found.add(it) }
        }
        return found
    }

    private fun completeGenerations(offspring: PedigreeParents): Int {
        var generation = listOf(offspring)
        var complete = 0
        while (generation.all { it.sireId != null && it.damId != null }) {
            complete++
            generation = generation.flatMap { listOf(it.sireId!!, it.damId!!) }.map { parentsOf[it] ?: PedigreeParents(null, null) }
        }
        return complete
    }
}
