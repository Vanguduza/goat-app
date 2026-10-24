package com.farmos.app

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.design.FarmSyncPendingReceipt
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.access.LocalRole
import com.farmos.feature.ops.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real existing owners, not new test-only Screen-ID labels. All fixture values are synthetic. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class ResourceVisualOwnerCertificationTest {
    @get:Rule(order = 0) val nativeReview = NativeModuleReviewRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test fun waterCaptureCommitsExactLocalQuantityBeforeShowingItsReceipt() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(app, FarmOsDatabase::class.java).allowMainThreadQueries().build()
        val farm = UUID.randomUUID().toString()
        runBlocking { seedCommandAuthority(database, farm, "manager-1", "A", LocalRole.MANAGER) }
        val ops = RoomOpsRepository(database, farm)
        var syncRequests = 0
        try {
            compose.setContent { NativeModuleReviewTheme {
                WaterModuleHost(farm, ops, { LocalCommandContext(farm, "manager-1", "A", UUID.randomUUID().toString(), 1_790_000_000_000) }, { syncRequests++ }, {})
            } }
            compose.onNodeWithTag("farm-screen:FOS-WATER-004").assertDoesNotExist()
            compose.onNodeWithTag("farm-section:Record water").performScrollTo().performClick()
            compose.onNodeWithTag("farm-screen:FOS-WATER-004").assertExists()
            compose.onNodeWithTag("farm-screen:FOS-SYNC-014").assertDoesNotExist()
            compose.onNodeWithText("Source").performScrollTo().performTextReplacement("North tank")
            compose.onNodeWithText("Litres").performScrollTo().performTextReplacement("1.275")
            compose.onNodeWithText("Date").performScrollTo().performTextReplacement("2026-10-24")
            compose.formSubmit("Record water").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-screen:FOS-SYNC-014").fetchSemanticsNodes().isNotEmpty() }
            runBlocking {
                assertEquals(1, database.water().count(farm))
                assertEquals(1275L, database.water().totalsBySource(farm).single().litresMilli)
            }
            compose.captureModuleReview("FOS-WATER-004")
            compose.captureModuleReview("FOS-SYNC-014")
            compose.runOnIdle { assertEquals(1, syncRequests) }
        } finally { database.close() }
    }

    @Test fun workerSearchReachesBeyondTheInitialWindowAndKeepsIdentity() {
        val workers = (1..120).map { i -> LabourWorkerOption("w$i", if (i == 120) "Rudo Zebra" else "Worker $i") }
        val selected = mutableStateOf<String?>(null)
        compose.setContent { NativeModuleReviewTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                LabourWorkerPicker(workers, selected.value, true) { selected.value = it }
            }
        } }
        compose.onNodeWithTag("farm-atom:FOS-ATOM-013").assertExists()
        compose.onNodeWithTag("labour-worker:w120").assertDoesNotExist()
        compose.onNodeWithTag("labour-worker-query").performScrollTo().performTextInput("Zebra")
        compose.onNodeWithTag("labour-worker:w120").performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle { assertEquals("w120", selected.value) }
        compose.captureModuleReview("FOS-ATOM-013")
    }

    @Test fun feedInventorySearchOpensTheExactItemAndReturnsToTheSameQuery() {
        val day = LocalDate.of(2026, 10, 1).toEpochDay()
        val items = (1..120).map { i -> FeedItemView("item$i", if (i == 120) "Late hay" else "Feed item $i", "1.5 kg", 1, day, day, if (i == 120) "31.005 kg" else "10 kg") }
        val records = FeedRecords(items = items, issueCount = 120)
        compose.setContent { NativeModuleReviewTheme {
            FeedRecordNavigator(records) { actions -> Column(Modifier.verticalScroll(rememberScrollState())) { actions() } }
        } }
        compose.revealFarmTool("Open Feed inventory").performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-FEED-002").assertExists()
        compose.onNodeWithTag("feed-inventory:item120").assertDoesNotExist()
        compose.onNodeWithTag("feed-inventory-query").performScrollTo().performTextInput("Late hay")
        compose.onNodeWithTag("feed-inventory:item120").assertExists()
        compose.captureModuleReview("FOS-FEED-002")
        compose.onNodeWithTag("feed-inventory:item120").performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-FEED-003").assertExists()
        compose.onNodeWithText("31.005 kg").assertExists()
        compose.revealFarmTool("Feed inventory").performScrollTo().performClick()
        compose.onNodeWithTag("feed-inventory-query").assertTextContains("Late hay")
        compose.onNodeWithTag("feed-inventory:item120").assertExists()
    }

    @Test fun syncReceiptDoesNotPromiseBackupOrAnApplicationServer() {
        compose.setContent { NativeModuleReviewTheme { FarmSyncPendingReceipt(3) } }
        compose.onNodeWithText("Pending changes remain on this device. Sync resumes when a configured destination is reachable.").assertExists()
        compose.onNodeWithText("Nothing is lost", substring = true).assertDoesNotExist()
        compose.onNodeWithText("farm server", substring = true).assertDoesNotExist()
        compose.captureModuleReview("FOS-ATOM-035")
    }
}
