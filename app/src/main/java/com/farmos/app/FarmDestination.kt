package com.farmos.app

import com.farmos.feature.goat.GoatEntryPage
import com.farmos.feature.ops.HealthEntryPage
import com.farmos.feature.ops.TaskEntryPage

enum class HomeSurface {
    TODAY_SUMMARY,
    ALERTS,
    ACTIVITY_STREAM,
    FARM_SWITCHER,
    NOTIFICATIONS,
    QUICK_CAPTURE,
}

sealed class FarmDestination {
    data object Home : FarmDestination()
    data object Search : FarmDestination()
    data object Settings : FarmDestination()
    data class HomePanel(val surface: HomeSurface) : FarmDestination()

    data class Module(
        val module: FarmModule,
    ) : FarmDestination()

    data class Goat(
        val entry: GoatEntryPage = GoatEntryPage.DASHBOARD,
        /** Exact farm-local subject for a profile or focused capture; never a herd-list position. */
        val animalId: String? = null,
    ) : FarmDestination()

    data class Health(
        val entry: HealthEntryPage = HealthEntryPage.DASHBOARD,
    ) : FarmDestination()

    data class Task(
        val taskId: String,
    ) : FarmDestination()

    data class Tasks(
        val entry: TaskEntryPage = TaskEntryPage.BOARD,
    ) : FarmDestination()

    data class SyncQueue(
        val view: SyncQueueView,
    ) : FarmDestination()
}

fun FarmModule.toDestination(): FarmDestination =
    when (this) {
        FarmModule.HOME -> FarmDestination.Home
        FarmModule.GOAT -> FarmDestination.Goat()
        FarmModule.HEALTH -> FarmDestination.Health()
        FarmModule.TASKS -> FarmDestination.Tasks(TaskEntryPage.BOARD)
        else -> FarmDestination.Module(this)
    }
