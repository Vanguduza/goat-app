package com.farmos.domain.ops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VaccinationRulesTest {
    private fun command(
        formularyItemId: String = "form-1",
        speciesCode: String = "goat",
        animalId: String? = "goat-1",
        groupId: String? = null,
    ) = RecordHealthVaccination(
        vaccinationId = "vax-1",
        speciesCode = speciesCode,
        formularyItemId = formularyItemId,
        animalId = animalId,
        groupId = groupId,
        occurredAtEpochMillis = 1_700_000_000_000L,
    )

    @Test
    fun vaccinationNeedsAFormularyItem() {
        assertEquals(
            "Vaccination needs a vet-approved formulary item",
            OpsValidator.vaccination(command(formularyItemId = "  ")),
        )
    }

    @Test
    fun vaccinationNeedsASpecies() {
        assertEquals(
            "Vaccination needs a species",
            OpsValidator.vaccination(command(speciesCode = " ")),
        )
    }

    @Test
    fun vaccinationNeedsATarget() {
        assertEquals(
            "Vaccination needs an animal or a group target",
            OpsValidator.vaccination(command(animalId = null, groupId = null)),
        )
    }

    @Test
    fun vaccinationTargetsExactlyOneOfAnimalOrGroup() {
        assertEquals(
            "Vaccination targets exactly one of animal or group",
            OpsValidator.vaccination(command(animalId = "goat-1", groupId = "group-1")),
        )
    }

    @Test
    fun validAnimalVaccinationPasses() {
        assertNull(OpsValidator.vaccination(command()))
    }

    @Test
    fun validGroupVaccinationPasses() {
        assertNull(OpsValidator.vaccination(command(animalId = null, groupId = "group-1")))
    }
}
