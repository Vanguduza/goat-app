package com.farmos.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExactDecimalInputTest {
    @Test
    fun `money converts to minor units without floating point`() {
        assertEquals(1810L, "18.10".toScaledLongExact(2, "Amount"))
        assertEquals(1L, "0.01".toScaledLongExact(2, "Amount"))
        assertEquals(1810L, "18,10".toScaledLongExact(2, "Amount"))
    }

    @Test
    fun `quantities convert exactly to milli units`() {
        assertEquals(1L, "0.001".toScaledLongExact(3, "Quantity"))
        assertEquals(1234567L, "1234.567".toScaledLongExact(3, "Quantity"))
    }

    @Test
    fun `fractional storage units are rejected instead of truncated`() {
        assertFailsWith<IllegalArgumentException> {
            "1.001".toScaledLongExact(2, "Amount")
        }
        assertFailsWith<IllegalArgumentException> {
            "1.0001".toScaledLongExact(3, "Quantity")
        }
    }

    @Test
    fun `scientific notation and overflow are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            "1e3".toScaledLongExact(2, "Amount")
        }
        assertFailsWith<IllegalArgumentException> {
            "999999999999999999999999".toScaledLongExact(2, "Amount")
        }
    }
}
