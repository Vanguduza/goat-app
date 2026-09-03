package com.farmos.domain.rabbit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class KudbatWaveScheduleTest {
    @Test
    fun `excel offsets match the locked nest-box schedule`() {
        val dates = KudbatSemiIntensiveExcel.schedule(matingEpochDay = 10_000)
        assertEquals(10_028, dates.nestInEpochDay)
        assertEquals(10_032, dates.kindlingEpochDay)
        assertEquals(10_043, dates.rebreedEpochDay)
        assertEquals(10_053, dates.nestOutEpochDay)
        assertEquals(10_067, dates.weanEpochDay)
    }

    @Test
    fun `eleven does require eleven nest boxes`() {
        assertEquals(11, KudbatSemiIntensiveExcel.nestBoxesRequired(11))
        assertEquals(11, KudbatSemiIntensiveExcel.NEST_BOXES_PER_CAGE)
        assertEquals(11, KudbatSemiIntensiveExcel.DOES_PER_CAGE)
    }

    @Test
    fun `waitlist needs a buyer and quantity`() {
        assertNotNull(RabbitProgrammeValidator.waitlist(EnqueueRabbitWaitlist("w1", "", qty = 2)))
        assertNotNull(RabbitProgrammeValidator.waitlist(EnqueueRabbitWaitlist("w1", "Nala buyer", qty = 0)))
        assertNull(RabbitProgrammeValidator.waitlist(EnqueueRabbitWaitlist("w1", "Nala buyer", qty = 2)))
    }

    @Test
    fun `kit register needs a temporary label and listed sex`() {
        assertNotNull(RabbitProgrammeValidator.kit(RegisterRabbitKit("k1", "wave-1", "", sex = "female")))
        assertNotNull(RabbitProgrammeValidator.kit(RegisterRabbitKit("k1", "wave-1", "Cage B kit A", sex = "doe")))
        assertNull(RabbitProgrammeValidator.kit(RegisterRabbitKit("k1", "wave-1", "Cage B kit A", sex = "female")))
    }

    @Test
    fun `bedding bind keeps milli-units`() {
        assertNotNull(RabbitProgrammeValidator.bedding(BindRabbitBedding(beddingQtyMilli = 0)))
        assertNull(RabbitProgrammeValidator.bedding(BindRabbitBedding("item-1", 1000)))
    }

    @Test
    fun `mating outcome stays on the listed results`() {
        assertNotNull(
            RabbitProgrammeValidator.matingOutcome(
                RecordRabbitMatingOutcome("o1", "wave-1", "maybe", 10_032),
            ),
        )
        assertNull(
            RabbitProgrammeValidator.matingOutcome(
                RecordRabbitMatingOutcome("o1", "wave-1", "false_pregnancy", 10_032),
            ),
        )
    }

    @Test
    fun `wave is rejected when the cage is short of nest boxes`() {
        val command = CreateRabbitWave(
            waveId = "wave-1",
            cageId = "cage-b",
            doeCount = 11,
            matingEpochDay = 10_000,
            placeTaskId = "t1",
            kindlingTaskId = "t2",
            removeTaskId = "t3",
            rebreedTaskId = "t4",
            weanTaskId = "t5",
        )
        assertNotNull(RabbitProgrammeValidator.wave(command, availableBoxes = 10))
        assertNull(RabbitProgrammeValidator.wave(command, availableBoxes = 11))
    }

    @Test
    fun `gi stasis flag needs signs`() {
        assertNotNull(RabbitProgrammeValidator.giStasis(RecordRabbitGiStasis("g1", "r1", " ", 10_032, "t1")))
        assertNull(RabbitProgrammeValidator.giStasis(RecordRabbitGiStasis("g1", "r1", "not eating", 10_032, "t1")))
    }
}
