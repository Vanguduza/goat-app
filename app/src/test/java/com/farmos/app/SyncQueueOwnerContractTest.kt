package com.farmos.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.OutboxEntity
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rendered surface ownership for the read-only sync queue views: each view renders its exact
 * Screen ID with this farm's rows in that sync state, plus empty, failed and denied states.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class SyncQueueOwnerContractTest {
    @get:Rule
    val compose = createComposeRule()

    private val base = 1_790_000_000_000L // 2026-09-21T14:13:20Z

    @Test
    fun inFlightQueueRendersClaimedRows() {
        render(SyncQueueView.IN_FLIGHT, listOf(row("m-flight", "IN_FLIGHT", attempts = 0, next = base + 300_000)))
        compose.onNodeWithTag("farm-screen:FOS-SYNC-004").assertIsDisplayed()
        compose.onNodeWithTag("sync-queue-row:m-flight").assertExists()
        compose.onNodeWithText("Claimed until 2026-09-21 14:18").assertExists()
    }

    @Test
    fun retryQueueRendersNextAttemptAndReason() {
        render(SyncQueueView.RETRY_WAITING, listOf(row("m-retry", "RETRY_WAIT", attempts = 2, error = "TRANSPORT_FAILURE", next = base + 8_000)))
        compose.onNodeWithTag("farm-screen:FOS-SYNC-005").assertIsDisplayed()
        compose.onNodeWithText("record_goat_weight").assertExists()
        compose.onNodeWithText("goat · goat-nala").assertExists()
        compose.onNodeWithText("Saved 2026-09-21 14:13 · 2 attempt(s)").assertExists()
        compose.onNodeWithText("Network unavailable").assertExists()
        compose.onNodeWithText("Next attempt after 2026-09-21 14:13").assertExists()
    }

    @Test
    fun conflictCentreStatesChangesWereNotApplied() {
        render(SyncQueueView.CONFLICTS, listOf(row("m-conflict", "CONFLICT", attempts = 1, error = "CONFLICT")))
        compose.onNodeWithTag("farm-screen:FOS-SYNC-006").assertIsDisplayed()
        compose.onNodeWithText("Kept on this device and not applied to the farm record.").assertExists()
        compose.onNodeWithText("Farm record changed on the server").assertExists()
    }

    @Test
    fun rejectedQueueShowsTheRejectionClass() {
        render(SyncQueueView.REJECTED, listOf(row("m-rejected", "REJECTED", attempts = 1, error = "AUTH_REJECTED")))
        compose.onNodeWithTag("farm-screen:FOS-SYNC-008").assertIsDisplayed()
        compose.onNodeWithText("Not authorised for this farm").assertExists()
        compose.onNodeWithText("Kept on this device and not applied to the farm record.").assertExists()
    }

    @Test
    fun deadLetterQueueRendersStoppedChanges() {
        render(SyncQueueView.DEAD_LETTER, listOf(row("m-dead", "DEAD_LETTER", attempts = 9, error = "VALIDATION_REJECTED")))
        compose.onNodeWithTag("farm-screen:FOS-SYNC-009").assertIsDisplayed()
        compose.onNodeWithTag("sync-queue-row:m-dead").assertExists()
        compose.onNodeWithText("Failed server validation").assertExists()
        compose.onNodeWithText("Kept on this device and not applied to the farm record.").assertExists()
    }

    @Test
    fun emptyDeadLetterQueueSaysSo() {
        render(SyncQueueView.DEAD_LETTER, emptyList())
        compose.onNodeWithTag("farm-screen:FOS-SYNC-009").assertIsDisplayed()
        compose.onNodeWithText("No changes have stopped retrying.").assertExists()
    }

    @Test
    fun boundedQueueStatesTheExhaustiveTotal() {
        render(SyncQueueView.RETRY_WAITING, listOf(row("m-retry", "RETRY_WAIT", attempts = 1)), total = 240L)
        compose.onNodeWithText("Latest 1 of 240 changes").assertExists()
        assertEquals("Latest 100 of 240 changes", syncQueueCountLabel(100, 240L))
        assertEquals("3 change(s)", syncQueueCountLabel(3, 3L))
        assertEquals("Latest 100 changes", syncQueueCountLabel(100, null))
        assertEquals("2 change(s)", syncQueueCountLabel(2, null))
    }

    @Test
    fun emptyQueueSaysSoAndSwitcherSelectsAnotherView() {
        val selected = AtomicReference<SyncQueueView?>(null)
        render(SyncQueueView.CONFLICTS, emptyList(), onSelect = selected::set)
        compose.onNodeWithText("No sync conflicts.").assertExists()
        compose.onNode(hasClickAction() and hasText("Rejected")).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(SyncQueueView.REJECTED, selected.get()) }
    }

    @Test
    fun failedLoadIsShownInsteadOfAnEmptyQueue() {
        render(SyncQueueView.RETRY_WAITING, rows = null)
        compose.onNodeWithText("database unavailable").assertExists()
        compose.onNodeWithText("No changes are waiting to retry.").assertDoesNotExist()
    }

    @Test
    fun deniedRoleNeverLoadsRows() {
        var loads = 0
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                SyncQueueHost(
                    view = SyncQueueView.REJECTED,
                    permitted = false,
                    loadRows = { loads++; emptyList() },
                    onSelectView = {},
                    onBack = {},
                    zone = ZoneOffset.UTC,
                )
            }
        }
        compose.onNodeWithText("Sync queues are not available for your farm role.").assertExists()
        compose.onNode(hasClickAction() and hasText("Conflicts")).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, loads) }
        assertFalse(syncQueuesPermitted("buyer"))
        assertFalse(syncQueuesPermitted("read_only"))
        assertTrue(syncQueuesPermitted("worker"))
        assertTrue(syncQueuesPermitted("owner"))
    }

    @Test
    fun queueCountsDefaultMissingStatesToZeroAndIgnoreOtherStates() {
        val counts = syncQueueCounts(mapOf("CONFLICT" to 2L, "ACKNOWLEDGED" to 40L, "PENDING" to 5L))
        assertEquals(2L, counts[SyncQueueView.CONFLICTS])
        assertEquals(0L, counts[SyncQueueView.REJECTED])
        assertEquals(SyncQueueView.entries.toSet(), counts.keys)
        assertNull(syncReasonLabel(null))
        assertEquals("UNMAPPED_CODE", syncReasonLabel("UNMAPPED_CODE"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rowsFromAnotherStateAreRefused() {
        row("m-x", "ACKNOWLEDGED", attempts = 1).toSyncQueueRow(SyncQueueView.REJECTED, ZoneOffset.UTC)
    }

    private fun render(
        view: SyncQueueView,
        rows: List<OutboxEntity>?,
        onSelect: (SyncQueueView) -> Unit = {},
        total: Long? = null,
    ) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                SyncQueueHost(
                    view = view,
                    permitted = true,
                    loadRows = { rows ?: error("database unavailable") },
                    onSelectView = onSelect,
                    onBack = {},
                    zone = ZoneOffset.UTC,
                    loadTotal = { total },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun row(
        id: String,
        state: String,
        attempts: Int,
        error: String? = null,
        next: Long? = null,
    ) = OutboxEntity(
        mutationId = id,
        farmId = "farm-a",
        actorId = "user-1",
        deviceId = "device-1",
        commandName = "record_goat_weight",
        commandSchemaVersion = 1,
        aggregateType = "goat",
        aggregateId = "goat-nala",
        aggregateOrdinal = 0,
        expectedStreamVersion = null,
        payloadJson = "{}",
        occurredAtEpochMillis = base,
        createdAtEpochMillis = base,
        state = state,
        attemptCount = attempts,
        nextAttemptAtEpochMillis = next,
        lastErrorCode = error,
        serverEventId = null,
        serverStreamVersion = null,
    )
}
