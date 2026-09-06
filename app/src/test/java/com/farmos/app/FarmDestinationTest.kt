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

    @Test
    fun managementHomeKeepsFeedAndWaterReachable() {
        val actions = managementHomeActions()
        assertEquals(
            FarmDestination.Module(FarmModule.FEED),
            actions.first { it.first == "Open feed" }.second,
        )
        assertEquals(
            FarmDestination.Module(FarmModule.WATER),
            actions.first { it.first == "Open water" }.second,
        )
    }

    @Test
    fun buyerHomeDoesNotGainWaterAction() {
        assertTrue(
            specialistHomeActions(FarmHomePersona.BUYER)
                .none { it.second == FarmDestination.Module(FarmModule.WATER) },
        )
    }

    @Test
    fun generalHomeKeepsFeedAndWaterReachable() {
        val actions = generalHomeActions()
        assertEquals(
            FarmDestination.Module(FarmModule.FEED),
            actions.first { it.first == "Open feed" }.second,
        )
        assertEquals(
            FarmDestination.Module(FarmModule.WATER),
            actions.first { it.first == "Open water" }.second,
        )
    }

    @Test
    fun managementHomeKeepsGroupsReachable() {
        val dest = managementHomeActions()
            .first { it.first == "Open groups" }
            .second
        assertEquals(FarmDestination.Module(FarmModule.GROUPS), dest)
    }

    @Test
    fun managementAndBreedingKeepWaitlistReachable() {
        val waitlist = FarmDestination.Module(FarmModule.WAITLIST)
        assertEquals(
            waitlist,
            managementHomeActions().first { it.first == "Open waitlist" }.second,
        )
        assertEquals(
            waitlist,
            specialistHomeActions(FarmHomePersona.BREEDING)
                .first { it.first == "Open waitlist" }
                .second,
        )
    }

    @Test
    fun buyerHomeDoesNotGainGroupsOrWaitlist() {
        val actions = specialistHomeActions(FarmHomePersona.BUYER)
        assertTrue(actions.none { it.second == FarmDestination.Module(FarmModule.GROUPS) })
        assertTrue(actions.none { it.second == FarmDestination.Module(FarmModule.WAITLIST) })
    }
}
