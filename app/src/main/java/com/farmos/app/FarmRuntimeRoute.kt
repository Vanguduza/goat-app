package com.farmos.app

import com.farmos.feature.goat.GoatEntryPage
import com.farmos.feature.ops.HealthEntryPage
import com.farmos.feature.ops.TaskEntryPage

/**
 * Runtime route ownership contract.
 *
 * Screen IDs are registry identities, not green claims. This contract is used by
 * the production host to expose the exact route owner exercised at runtime.
 */
data class FarmRuntimeRoute(
    val routeKey: String,
    val screenId: String,
    val scopedParameter: String? = null,
) {
    val testTag: String
        get() = "farm-runtime-route:$routeKey:$screenId"
}

fun FarmDestination.runtimeRouteContract(): FarmRuntimeRoute =
    when (this) {
        FarmDestination.Home -> FarmRuntimeRoute("home", "FOS-HOME-001")
        is FarmDestination.Module -> module.runtimeRouteContract()
        is FarmDestination.Goat -> entry.runtimeRouteContract()
        is FarmDestination.Health -> entry.runtimeRouteContract()
        is FarmDestination.Task -> {
            require(taskId.isNotBlank()) { "Task destination requires a non-blank task id" }
            FarmRuntimeRoute("task.detail", "FOS-TASK-003", "taskId=$taskId")
        }
        is FarmDestination.Tasks -> entry.runtimeRouteContract()
    }

private fun FarmModule.runtimeRouteContract(): FarmRuntimeRoute =
    FarmRuntimeRoute(
        routeKey = "module.${name.lowercase()}",
        screenId = when (this) {
            FarmModule.HOME -> "FOS-HOME-001"
            FarmModule.GOAT -> "FOS-GOAT-001"
            FarmModule.RABBIT -> "FOS-RABBIT-001"
            FarmModule.SHEEP -> "FOS-SHEEP-001"
            FarmModule.CATTLE -> "FOS-CATTLE-001"
            FarmModule.POULTRY -> "FOS-POULTRY-001"
            FarmModule.TASKS -> "FOS-TASK-001"
            FarmModule.HEALTH -> "FOS-HEALTH-001"
            FarmModule.MONEY -> "FOS-FIN-001"
            FarmModule.INVENTORY -> "FOS-INV-001"
            FarmModule.GROUPS -> "FOS-GROUP-001"
            FarmModule.PASTURE -> "FOS-PASTURE-001"
            FarmModule.LABOUR -> "FOS-LABOUR-001"
            FarmModule.ASSETS -> "FOS-ASSET-001"
            FarmModule.FEED -> "FOS-FEED-001"
            FarmModule.WATER -> "FOS-WATER-001"
            FarmModule.SALES -> "FOS-SALES-001"
            FarmModule.PROCUREMENT -> "FOS-PROC-001"
            FarmModule.WAITLIST -> "FOS-RABBIT-027"
        },
    )

private fun GoatEntryPage.runtimeRouteContract(): FarmRuntimeRoute =
    FarmRuntimeRoute(
        routeKey = "goat.${name.lowercase()}",
        screenId = when (this) {
            GoatEntryPage.DASHBOARD -> "FOS-GOAT-001"
            GoatEntryPage.WEIGHT -> "FOS-GOAT-011"
            GoatEntryPage.SEARCH -> "FOS-GOAT-006"
            GoatEntryPage.SYNC -> "FOS-SYNC-002"
            GoatEntryPage.KIDDING -> "FOS-GOAT-037"
            GoatEntryPage.REPRODUCTION -> "FOS-GOAT-032"
        },
    )

private fun HealthEntryPage.runtimeRouteContract(): FarmRuntimeRoute =
    FarmRuntimeRoute(
        routeKey = "health.${name.lowercase()}",
        screenId = when (this) {
            HealthEntryPage.DASHBOARD -> "FOS-HEALTH-001"
            HealthEntryPage.TREATMENT -> "FOS-HEALTH-007"
            HealthEntryPage.WITHDRAWALS -> "FOS-HEALTH-009"
            HealthEntryPage.RECORD_OBSERVATION -> "FOS-HEALTH-004"
            HealthEntryPage.VET_VISIT -> "FOS-HEALTH-021"
            HealthEntryPage.LAB_RESULT -> "FOS-HEALTH-024"
            HealthEntryPage.FORMULARY -> "FOS-HEALTH-013"
        },
    )

private fun TaskEntryPage.runtimeRouteContract(): FarmRuntimeRoute =
    FarmRuntimeRoute(
        routeKey = "tasks.${name.lowercase()}",
        screenId = when (this) {
            TaskEntryPage.BOARD -> "FOS-TASK-001"
            TaskEntryPage.CREATE -> "FOS-TASK-004"
        },
    )
