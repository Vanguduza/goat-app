package com.farmos.domain.ops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnimalExitRulesTest {
    @Test
    fun theLatestUnreversedExitSetsTheStatus() {
        val sale = AnimalExitEvent("e1", AnimalExitKind.SALE, 100, recordedAtEpochMillis = 1)
        assertEquals("active", AnimalExitRules.status(emptyList()))
        assertEquals("sold", AnimalExitRules.status(listOf(sale)))

        // The sale was recorded in error and reversed; a later death stands.
        val reversal = AnimalExitEvent("r1", null, 101, recordedAtEpochMillis = 2, reversesExitId = "e1")
        assertEquals("active", AnimalExitRules.status(listOf(sale, reversal)))
        val death = AnimalExitEvent("e2", AnimalExitKind.DEATH, 105, recordedAtEpochMillis = 3)
        assertEquals("dead", AnimalExitRules.status(listOf(sale, reversal, death)))
        assertEquals("e2", AnimalExitRules.standing(listOf(death, reversal, sale))?.exitId)
    }

    @Test
    fun eachExitNeedsItsOwnFacts() {
        fun error(kind: AnimalExitKind, day: Long = 10, cause: String? = null, reason: String? = null, buyer: String? = null, price: Long? = null) =
            AnimalExitRules.exitError(kind, day, todayEpochDay = 10, deathCause = cause, reason = reason, buyer = buyer, priceMinor = price)
        assertEquals("An exit cannot be dated in the future", error(AnimalExitKind.CULL, day = 11, reason = "Lame"))
        assertEquals("Choose what the animal died of, or Unknown", error(AnimalExitKind.DEATH))
        assertNull(error(AnimalExitKind.DEATH, cause = "UNKNOWN"))
        assertEquals("Say why the animal is culled", error(AnimalExitKind.CULL, reason = " "))
        assertEquals("Enter the buyer", error(AnimalExitKind.SALE))
        assertNull(error(AnimalExitKind.SALE, buyer = "Moyo Butchery", price = 15_000))
        assertNull(error(AnimalExitKind.SALE, buyer = "Moyo Butchery"))
        assertEquals("A price cannot be negative", error(AnimalExitKind.SALE, buyer = "Moyo", price = -1))
        assertEquals("Only a sale has a price", error(AnimalExitKind.CULL, reason = "Lame", price = 100))
        assertEquals("Say why the exit is reversed", AnimalExitRules.reversalError(""))
    }
}
