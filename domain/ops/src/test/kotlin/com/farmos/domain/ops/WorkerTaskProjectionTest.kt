package com.farmos.domain.ops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkerTaskProjectionTest {
    @Test
    fun dueNowIncludesTodayAndOverdueOpenTasks() {
        assertEquals(WorkerTaskStage.DUE_NOW, projectWorkerTaskStage("open", 100, 100))
        assertEquals(WorkerTaskStage.DUE_NOW, projectWorkerTaskStage("open", 90, 100))
    }

    @Test
    fun upcomingIsOpenWithALaterDueDate() {
        assertEquals(WorkerTaskStage.UPCOMING, projectWorkerTaskStage("open", 110, 100))
    }

    @Test
    fun completedUsesActualDoneStatusOnly() {
        assertEquals(WorkerTaskStage.COMPLETED, projectWorkerTaskStage("done", 90, 100))
        assertNull(projectWorkerTaskStage("cancelled", 90, 100))
        assertNull(projectWorkerTaskStage("open-ish", 90, 100))
    }

    @Test
    fun attentionRanksWithdrawalsThenOverdueThenPendingSync() {
        assertEquals(HomeAttentionKind.WITHDRAWAL, rankHomeAttention(1, 4, 9))
        assertEquals(HomeAttentionKind.OVERDUE_WORK, rankHomeAttention(0, 2, 9))
        assertEquals(HomeAttentionKind.PENDING_SYNC, rankHomeAttention(0, 0, 3))
        assertEquals(HomeAttentionKind.NONE, rankHomeAttention(0, 0, 0))
    }

    @Test
    fun speciesFamilyIsInferredOnlyFromExplicitModuleCodes() {
        assertEquals("goat", inferTaskSpeciesFamily("goat"))
        assertEquals("rabbit", inferTaskSpeciesFamily("RABBIT"))
        assertNull(inferTaskSpeciesFamily("tasks"))
        assertNull(inferTaskSpeciesFamily("health"))
        assertNull(inferTaskSpeciesFamily("inventory"))
    }
}
