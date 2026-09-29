package com.farmos.app

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
import com.farmos.core.network.MutationTraceEvent
import com.farmos.core.network.MutationTraceResponse
import com.farmos.core.network.MutationTraceSearchJob
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rendered traversal for the mutation trace: it opens from a sync queue row, shows the change
 * exactly as this device stores it, checks the farm server only on request, and Back restores
 * the queue it came from.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class SyncMutationTraceRuntimeNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private val base = 1_790_000_000_000L // 2026-09-21T14:13:20Z
    private val traceScreen = "farm-screen:FOS-SYNC-018"

    @Test
    fun traceOpensFromTheDeadLetterQueueAndBackRestoresIt() {
        var serverCalls = 0
        render(SyncQueueView.DEAD_LETTER, row("m-dead", "DEAD_LETTER", error = "VALIDATION_REJECTED")) {
            serverCalls++
            ServerMutationTrace.NotFound
        }
        compose.onNodeWithTag("sync-queue-trace:m-dead").performScrollTo().performClick()
        compose.onNodeWithTag(traceScreen).assertExists()
        compose.onNodeWithText("m-dead").assertExists()
        compose.onNodeWithText("record_goat_weight · schema v2").assertExists()
        compose.onNodeWithText("goat · goat-nala").assertExists()
        compose.onNodeWithText("DEAD_LETTER").assertExists()
        compose.onNodeWithText("Failed server validation (VALIDATION_REJECTED)").assertExists()
        compose.onNodeWithText("None (new record)").assertExists()
        compose.onNodeWithText("Not scheduled").assertExists()
        compose.runOnIdle { assertEquals(0, serverCalls) }

        compose.onNode(hasClickAction() and hasText("Check the farm server")).performScrollTo().performClick()
        compose.onNodeWithText("The farm server has no record of this change for this farm.").assertExists()
        compose.runOnIdle { assertEquals(1, serverCalls) }

        compose.onNode(hasClickAction() and hasText("Back to Dead letter")).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-SYNC-009").assertExists()
        compose.onNodeWithTag(traceScreen).assertDoesNotExist()
        compose.onNodeWithTag("sync-queue-row:m-dead").assertExists()
    }

    @Test
    fun appliedServerTraceShowsTheEventAndSearchJobs() {
        val response = MutationTraceResponse(
            code = "FOUND",
            mutationId = "m-conflict",
            commandName = "record_goat_weight",
            appliedAt = "2026-09-21T14:14:02Z",
            event = MutationTraceEvent("evt-7", "goat_weight_recorded", 5, 812, "2026-09-21T14:14:02Z", "device-2"),
            searchJobs = listOf(MutationTraceSearchJob(41, "command", "completed", 1, 3, 90, "2026-09-21T14:14:02Z", "2026-09-21T14:14:05Z", false)),
        )
        render(SyncQueueView.CONFLICTS, row("m-conflict", "CONFLICT", error = "CONFLICT")) { response.toServerMutationTrace() }
        compose.onNodeWithTag("sync-queue-trace:m-conflict").performScrollTo().performClick()
        compose.onNode(hasClickAction() and hasText("Check the farm server")).performScrollTo().performClick()
        compose.onNodeWithText("goat_weight_recorded").assertExists()
        compose.onNodeWithText("812").assertExists()
        compose.onNodeWithText("completed · 1 attempt(s)").assertExists()
        compose.onNode(hasClickAction() and hasText("Check again")).assertExists()
    }

    @Test
    fun refusedAndFailedServerChecksAreShownNotHidden() {
        render(SyncQueueView.REJECTED, row("m-rejected", "REJECTED", error = "AUTH_REJECTED")) { error("Authentication required") }
        compose.onNodeWithTag("sync-queue-trace:m-rejected").performScrollTo().performClick()
        compose.onNode(hasClickAction() and hasText("Check the farm server")).performScrollTo().performClick()
        compose.onNodeWithText("Authentication required").assertExists()
        assertEquals(
            ServerMutationTrace.Refused("Farm access denied"),
            MutationTraceResponse(code = "AUTH_REJECTED", safeMessage = "Farm access denied").toServerMutationTrace(),
        )
        assertEquals(ServerMutationTrace.Refused("UNEXPECTED"), MutationTraceResponse(code = "UNEXPECTED").toServerMutationTrace())
    }

    @Test
    fun withoutAFarmServerOnlyTheDeviceRecordIsShown() {
        render(SyncQueueView.RETRY_WAITING, row("m-retry", "RETRY_WAIT", error = "TRANSPORT_FAILURE"), traceOnServer = null)
        compose.onNodeWithTag("sync-queue-trace:m-retry").performScrollTo().performClick()
        compose.onNodeWithTag(traceScreen).assertExists()
        compose.onNodeWithText("This app is not connected to a farm server, so only the device record is shown.").assertExists()
        compose.onNode(hasClickAction() and hasText("Check the farm server")).assertDoesNotExist()
    }

    private fun render(view: SyncQueueView, row: OutboxEntity, traceOnServer: (suspend (String) -> ServerMutationTrace)?) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                SyncQueueHost(
                    view = view,
                    permitted = true,
                    loadRows = { listOf(row) },
                    onSelectView = {},
                    onBack = {},
                    zone = ZoneOffset.UTC,
                    loadTotal = { 1L },
                    traceOnServer = traceOnServer,
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("farm-screen:${view.screenId}").assertExists()
    }

    private fun row(id: String, state: String, error: String?) = OutboxEntity(
        mutationId = id,
        farmId = "farm-a",
        actorId = "user-1",
        deviceId = "device-1",
        commandName = "record_goat_weight",
        commandSchemaVersion = 2,
        aggregateType = "goat",
        aggregateId = "goat-nala",
        aggregateOrdinal = 3,
        expectedStreamVersion = null,
        payloadJson = "{}",
        occurredAtEpochMillis = base,
        createdAtEpochMillis = base,
        state = state,
        attemptCount = 4,
        nextAttemptAtEpochMillis = null,
        lastErrorCode = error,
        serverEventId = null,
        serverStreamVersion = null,
    )
}
