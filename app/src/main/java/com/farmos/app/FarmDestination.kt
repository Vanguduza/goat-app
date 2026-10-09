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

    data class AnimalProfile(
        val kind: AnimalProfileKind,
        val animalId: String,
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

/** Existing individual-animal profile owners; poultry's flock profile is a different subject. */
enum class AnimalProfileKind(val module: FarmModule, val speciesCode: String, val screenId: String, val routeKey: String) {
    SHEEP(FarmModule.SHEEP, "sheep", "FOS-SHEEP-003", "sheep.profile"),
    CATTLE(FarmModule.CATTLE, "cattle", "FOS-CATTLE-003", "cattle.profile"),
    RABBIT_DOE(FarmModule.RABBIT, "rabbit", "FOS-RABBIT-003", "rabbit.doe_profile"),
    RABBIT_BUCK(FarmModule.RABBIT, "rabbit", "FOS-RABBIT-004", "rabbit.buck_profile"),
}
