package com.farmos.app

import com.farmos.feature.goat.GoatEntryPage
import com.farmos.feature.ops.HealthEntryPage
import com.farmos.feature.ops.TaskEntryPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FarmDestinationTest {
    @Test
    fun tasksModuleUsesTypedTodayBoard() {
        assertEquals(FarmDestination.Tasks(TaskEntryPage.BOARD), FarmModule.TASKS.toDestination())
    }

    @Test
    fun goatAndHealthKeepTypedModuleEntries() {
        assertEquals(FarmDestination.Goat(), FarmModule.GOAT.toDestination())
        assertEquals(FarmDestination.Health(), FarmModule.HEALTH.toDestination())
    }

    @Test
    fun supervisorHealthExceptionsStayOnHealthDashboard() {
        val dest = specialistHomeActions(FarmHomePersona.SUPERVISOR)
            .first { it.first == "Health exceptions" }
            .second
        assertEquals(FarmDestination.Health(), dest)
    }

    @Test
    fun mutationSpecialistHomesKeepSyncEntryReachable() {
        val sync = FarmDestination.Goat(GoatEntryPage.SYNC)
        val personas = listOf(
            FarmHomePersona.SUPERVISOR,
            FarmHomePersona.BREEDING,
            FarmHomePersona.VET,
            FarmHomePersona.FINANCE,
        )
        personas.forEach { persona ->
            assertTrue(
                specialistHomeActions(persona).any { it.second == sync },
                "$persona home must keep FOS-SYNC-002 reachable",
            )
        }
    }

    @Test
    fun buyerHomeDoesNotGainSyncAction() {
        assertTrue(
            specialistHomeActions(FarmHomePersona.BUYER)
                .none { it.second == FarmDestination.Goat(GoatEntryPage.SYNC) },
        )
    }

    @Test
    fun vetHomeOpensRecordObservation() {
        val dest = specialistHomeActions(FarmHomePersona.VET)
            .first { it.first == "Record observation" }
            .second
        assertEquals(FarmDestination.Health(HealthEntryPage.RECORD_OBSERVATION), dest)
    }

    @Test
    fun managementHomeKeepsHealthDashboardReachable() {
        val dest = managementHomeActions()
            .first { it.first == "Open health" }
            .second
        assertEquals(FarmDestination.Health(), dest)
    }

    @Test
    fun buyerHomeDoesNotGainHealthAction() {
        assertTrue(
            specialistHomeActions(FarmHomePersona.BUYER)
                .none { it.second == FarmDestination.Health() },
        )
    }
}
