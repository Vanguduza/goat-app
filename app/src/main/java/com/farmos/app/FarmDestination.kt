package com.farmos.app

import com.farmos.feature.goat.GoatEntryPage
import com.farmos.feature.ops.HealthEntryPage

sealed class FarmDestination {
    data object Home : FarmDestination()

    data class Module(
        val module: FarmModule,
    ) : FarmDestination()

    data class Goat(
        val entry: GoatEntryPage = GoatEntryPage.DASHBOARD,
    ) : FarmDestination()

    data class Health(
        val entry: HealthEntryPage = HealthEntryPage.DASHBOARD,
    ) : FarmDestination()

    data class Task(
        val taskId: String,
    ) : FarmDestination()
}

fun FarmModule.toDestination(): FarmDestination =
    when (this) {
        FarmModule.HOME -> FarmDestination.Home
        FarmModule.GOAT -> FarmDestination.Goat()
        FarmModule.HEALTH -> FarmDestination.Health()
        else -> FarmDestination.Module(this)
    }
