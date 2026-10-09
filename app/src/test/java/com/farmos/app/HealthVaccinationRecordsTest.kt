package com.farmos.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthVaccinationRecordsTest {
    @Test
    fun anOldRecordNeverInventsAnAnnualClinicalDueDate() {
        val row = vaccinationRecordReview(
            "goat", "Goat", Instant.parse("2010-01-01T00:00:00Z").toEpochMilli(),
            LocalDate.of(2026, 10, 9), ZoneOffset.UTC,
        )
        assertEquals("Last individual record: 2010-01-01 · No next due date recorded", row.reason)
    }

    @Test
    fun noIndividualRecordDoesNotAssertUnvaccinatedGroupMembers() {
        val row = vaccinationRecordReview("goat", "Goat", null, LocalDate.of(2026, 10, 9), ZoneOffset.UTC)
        assertEquals("No individual vaccination record · No next due date recorded", row.reason)
        assertNull(row.daysSinceLast)
    }

    @Test
    fun recordedBusinessDateUsesTheFarmDeviceZone() {
        val row = vaccinationRecordReview(
            "goat", "Goat", Instant.parse("2026-10-08T22:30:00Z").toEpochMilli(),
            LocalDate.of(2026, 10, 9), ZoneId.of("Africa/Harare"),
        )
        assertEquals("Last individual record: 2026-10-09 · No next due date recorded", row.reason)
        assertEquals(0L, row.daysSinceLast ?: -1L)
    }
}
