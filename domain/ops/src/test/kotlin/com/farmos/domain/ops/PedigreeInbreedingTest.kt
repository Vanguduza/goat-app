package com.farmos.domain.ops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PedigreeInbreedingTest {
    private fun p(sire: String?, dam: String?) = PedigreeParents(sire, dam)

    @Test
    fun unrelatedParentsGiveZeroAndAThinPedigreeIsReported() {
        val result = PedigreeInbreeding(mapOf("s" to p("a", "b"), "d" to p("c", null))).mating("s", "d")
        assertEquals(0.0, result.coefficient)
        assertEquals(1, result.generationsKnown)
        assertEquals(emptySet(), result.commonAncestors)
        assertEquals(5, result.ancestorsKnown)
    }

    @Test
    fun textbookMatingsMatchThePathMethod() {
        // Full siblings: F = 1/4.
        val sibs = PedigreeInbreeding(mapOf("s" to p("A", "B"), "d" to p("A", "B"))).mating("s", "d")
        assertEquals(0.25, sibs.coefficient, 1e-12)
        assertEquals(setOf("A", "B"), sibs.commonAncestors)
        assertEquals(2, sibs.generationsKnown)
        // Half siblings: F = 1/8.
        assertEquals(0.125, PedigreeInbreeding(mapOf("s" to p("A", "B"), "d" to p("A", "C"))).mating("s", "d").coefficient, 1e-12)
        // Sire to his own daughter: F = 1/4.
        assertEquals(0.25, PedigreeInbreeding(mapOf("d" to p("s", "X"))).mating("s", "d").coefficient, 1e-12)
        // First cousins: F = 1/16.
        val cousins = mapOf("s" to p("P1", "M1"), "d" to p("P2", "M2"), "P1" to p("G", "H"), "P2" to p("G", "H"))
        assertEquals(0.0625, PedigreeInbreeding(cousins).mating("s", "d").coefficient, 1e-12)
    }

    @Test
    fun anInbredCommonAncestorRaisesTheCoefficient() {
        // Half siblings through A, where A is from a full-sibling mating (F_A = 1/4): F = 1/8 * (1 + 1/4).
        val pedigree = mapOf("s" to p("A", "B"), "d" to p("A", "C"), "A" to p("X", "Y"), "X" to p("G", "H"), "Y" to p("G", "H"))
        assertEquals(0.125 * 1.25, PedigreeInbreeding(pedigree).mating("s", "d").coefficient, 1e-12)
        assertEquals(0.25, PedigreeInbreeding(pedigree).inbreeding("A"), 1e-12)
    }

    @Test
    fun cyclesAndSelfMatingAreRefused() {
        assertFailsWith<IllegalArgumentException> { PedigreeInbreeding(mapOf("a" to p("b", null), "b" to p("a", null))).mating("a", "c") }
        assertFailsWith<IllegalArgumentException> { PedigreeInbreeding(emptyMap()).mating("a", "a") }
    }
}
