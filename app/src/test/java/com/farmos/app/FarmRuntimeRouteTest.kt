package com.farmos.app

import com.farmos.feature.goat.GoatEntryPage
import com.farmos.feature.ops.HealthEntryPage
import com.farmos.feature.ops.TaskEntryPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FarmRuntimeRouteTest {
    @Test
    fun everyFarmModuleResolvesToARegisteredRouteOwner() {
        val expected = mapOf(
            FarmModule.HOME to "FOS-HOME-001",
            FarmModule.GOAT to "FOS-GOAT-001",
            FarmModule.RABBIT to "FOS-RABBIT-001",
            FarmModule.SHEEP to "FOS-SHEEP-001",
            FarmModule.CATTLE to "FOS-CATTLE-001",
            FarmModule.POULTRY to "FOS-POULTRY-001",
            FarmModule.TASKS to "FOS-TASK-001",
            FarmModule.HEALTH to "FOS-HEALTH-001",
            FarmModule.MONEY to "FOS-FIN-001",
            FarmModule.INVENTORY to "FOS-INV-001",
            FarmModule.GROUPS to "FOS-GROUP-001",
            FarmModule.PASTURE to "FOS-PASTURE-001",
            FarmModule.LABOUR to "FOS-LABOUR-001",
            FarmModule.ASSETS to "FOS-ASSET-001",
            FarmModule.FEED to "FOS-FEED-001",
            FarmModule.WATER to "FOS-WATER-001",
            FarmModule.SALES to "FOS-SALES-001",
            FarmModule.PROCUREMENT to "FOS-PROC-001",
            FarmModule.WAITLIST to "FOS-RABBIT-027",
        )

        assertEquals(FarmModule.entries.toSet(), expected.keys)
        expected.forEach { (module, screenId) ->
            val route = FarmDestination.Module(module).runtimeRouteContract()
            assertEquals(screenId, route.screenId)
            assertEquals("module.${module.name.lowercase()}", route.routeKey)
            assertEquals(null, route.scopedParameter)
        }
    }

    @Test
    fun typedGoatEntriesResolveToExactOwners() {
        val expected = mapOf(
            GoatEntryPage.DASHBOARD to "FOS-GOAT-001",
            GoatEntryPage.WEIGHT to "FOS-GOAT-011",
            GoatEntryPage.SEARCH to "FOS-GOAT-006",
            GoatEntryPage.SYNC to "FOS-SYNC-002",
            GoatEntryPage.KIDDING to "FOS-GOAT-037",
            GoatEntryPage.REPRODUCTION to "FOS-GOAT-032",
        )
        expected.forEach { (entry, screenId) ->
            assertEquals(screenId, FarmDestination.Goat(entry).runtimeRouteContract().screenId)
        }
    }

    @Test
    fun typedHealthAndTaskEntriesResolveToExactOwners() {
        val health = mapOf(
            HealthEntryPage.DASHBOARD to "FOS-HEALTH-001",
            HealthEntryPage.TREATMENT to "FOS-HEALTH-007",
            HealthEntryPage.WITHDRAWALS to "FOS-HEALTH-009",
            HealthEntryPage.RECORD_OBSERVATION to "FOS-HEALTH-004",
            HealthEntryPage.VET_VISIT to "FOS-HEALTH-021",
            HealthEntryPage.LAB_RESULT to "FOS-HEALTH-024",
            HealthEntryPage.FORMULARY to "FOS-HEALTH-013",
        )
        health.forEach { (entry, screenId) ->
            assertEquals(screenId, FarmDestination.Health(entry).runtimeRouteContract().screenId)
        }
        assertEquals(
            "FOS-TASK-001",
            FarmDestination.Tasks(TaskEntryPage.BOARD).runtimeRouteContract().screenId,
        )
        assertEquals(
            "FOS-TASK-004",
            FarmDestination.Tasks(TaskEntryPage.CREATE).runtimeRouteContract().screenId,
        )
    }

    @Test
    fun focusedTaskPreservesExactScopeAndRejectsBlankIdentity() {
        val route = FarmDestination.Task("task-health-nala").runtimeRouteContract()
        assertEquals("task.detail", route.routeKey)
        assertEquals("FOS-TASK-003", route.screenId)
        assertEquals("taskId=task-health-nala", route.scopedParameter)
        assertTrue(route.testTag.endsWith(":FOS-TASK-003"))

        assertFailsWith<IllegalArgumentException> {
            FarmDestination.Task(" ").runtimeRouteContract()
        }
    }

    @Test
    fun homeHasStableRuntimeOwner() {
        assertEquals(
            FarmRuntimeRoute("home", "FOS-HOME-001"),
            FarmDestination.Home.runtimeRouteContract(),
        )
    }
}
