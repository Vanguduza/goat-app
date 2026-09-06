package com.farmos.app

import com.farmos.feature.ops.TaskEntryPage
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
