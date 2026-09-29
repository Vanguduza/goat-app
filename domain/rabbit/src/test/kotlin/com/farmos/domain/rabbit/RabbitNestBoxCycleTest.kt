package com.farmos.domain.rabbit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RabbitNestBoxCycleTest {
    @Test
    fun `next statuses follow the server nest-box cycle`() {
        assertEquals(listOf("assigned", "in_cage"), RabbitNestBoxCycle.nextStatuses("available"))
        assertEquals(listOf("assigned", "in_cage"), RabbitNestBoxCycle.nextStatuses("sanitized"))
        assertEquals(listOf("in_cage", "dirty"), RabbitNestBoxCycle.nextStatuses("assigned"))
        assertEquals(listOf("dirty"), RabbitNestBoxCycle.nextStatuses("in_cage"))
        assertEquals(listOf("sanitized", "available"), RabbitNestBoxCycle.nextStatuses("dirty"))
        assertEquals(emptyList(), RabbitNestBoxCycle.nextStatuses("retired"))
    }

    @Test
    fun `a box cannot skip cleaning or stay in place`() {
        assertFalse(RabbitNestBoxCycle.allowed("dirty", "assigned"))
        assertFalse(RabbitNestBoxCycle.allowed("in_cage", "available"))
        assertFalse(RabbitNestBoxCycle.allowed("available", "available"))
        assertTrue(RabbitNestBoxCycle.allowed("dirty", "sanitized"))
    }
}
