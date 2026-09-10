package com.farmos.app

import com.farmos.domain.ops.HomeAttentionKind
import com.farmos.feature.goat.GoatEntryPage
import com.farmos.feature.ops.HealthEntryPage
import com.farmos.feature.ops.TaskEntryPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

    @Test
    fun workerQuickRecordUsesExactOwnersAndSentenceCase() {
        val actions = workerHomeQuickActions()
        assertEquals(
            FarmDestination.Tasks(TaskEntryPage.CREATE),
            actions.first { it.first == "Add task" }.second,
        )
        assertEquals(
            FarmDestination.Goat(GoatEntryPage.WEIGHT),
            actions.first { it.first == "Record weight" }.second,
        )
        assertEquals(
            FarmDestination.Health(HealthEntryPage.TREATMENT),
            actions.first { it.first == "Add treatment" }.second,
        )
        assertEquals(
            FarmDestination.Health(HealthEntryPage.RECORD_OBSERVATION),
            actions.first { it.first == "Record observation" }.second,
        )
        assertEquals(
            FarmDestination.Goat(GoatEntryPage.SEARCH),
            actions.first { it.first == "Scan animal" }.second,
        )
        actions.forEach { (label, _) ->
            label.split(' ').drop(1).forEach { word ->
                assertEquals(word.lowercase(), word, "button copy must be sentence case: $label")
            }
        }
    }

    @Test
    fun workerAreaGuidesOpenExistingModulesWithoutGroupsOrWaitlist() {
        val actions = workerHomeAreaActions()
        assertEquals(
            FarmDestination.Module(FarmModule.FEED),
            actions.first { it.first == "Open feed" }.second,
        )
        assertEquals(
            FarmDestination.Module(FarmModule.WATER),
            actions.first { it.first == "Open water" }.second,
        )
        assertEquals(
            FarmDestination.Module(FarmModule.PASTURE),
            actions.first { it.first == "Open pasture" }.second,
        )
        assertEquals(
            FarmDestination.Module(FarmModule.ASSETS),
            actions.first { it.first == "Open assets" }.second,
        )
        assertEquals(
            FarmDestination.Goat(GoatEntryPage.SYNC),
            actions.first { it.first == "Open sync status" }.second,
        )
        assertTrue(actions.none { it.second == FarmDestination.Module(FarmModule.GROUPS) })
        assertTrue(actions.none { it.second == FarmDestination.Module(FarmModule.WAITLIST) })
    }

    @Test
    fun managementAttentionUsesExactOwners() {
        assertEquals(
            "Open withdrawals" to FarmDestination.Health(HealthEntryPage.WITHDRAWALS),
            managementAttentionAction(HomeAttentionKind.WITHDRAWAL),
        )
        assertEquals(
            "Open tasks" to FarmDestination.Tasks(TaskEntryPage.BOARD),
            managementAttentionAction(HomeAttentionKind.OVERDUE_WORK),
        )
        assertEquals(
            "Open sync status" to FarmDestination.Goat(GoatEntryPage.SYNC),
            managementAttentionAction(HomeAttentionKind.PENDING_SYNC),
        )
        assertNull(managementAttentionAction(HomeAttentionKind.NONE))
    }

    @Test
    fun vetHomeOpensAddTreatment() {
        val dest = specialistHomeActions(FarmHomePersona.VET)
            .first { it.first == "Add treatment" }
            .second
        assertEquals(FarmDestination.Health(HealthEntryPage.TREATMENT), dest)
    }
}
