package com.farmos.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real analytics owners opened through their existing menu, backed by isolated Room farm records. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class AnalyticsVisualNavigationTest {
    @get:Rule(order = 0) val nativeReview = NativeModuleReviewRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val farm = UUID.randomUUID().toString()
    private val other = UUID.randomUUID().toString()
    private lateinit var database: FarmOsDatabase

    @Before fun seed() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java).build()
        val day = LocalDate.of(2026, 10, 1).toEpochDay()
        database.animals().insert(AnimalEntity("g1", farm, "G-1", "Willow", "goat", "FEMALE", "active", null, updatedAtEpochMillis = 1))
        database.animals().insert(AnimalEntity("outside", other, "HIDDEN", "Private animal", "cattle", "FEMALE", "active", null, updatedAtEpochMillis = 1))
        database.inventory().insertItem(InventoryItemEntity("feed", farm, "FEED", "Hay", "kg", 250, 1, 500))
        database.feedIssues().insert(FeedIssueEntity("issue", farm, "feed", null, 1250, day))
        database.money().insert(MoneyRecordEntity("income", farm, "income", "produce", 1250, "USD", day, null))
        database.money().insert(MoneyRecordEntity("outside-money", other, "income", "private", 900000, "USD", day, null))
    }
    @After fun close() = database.close()

    @Test fun analyticsMenuOpensEverySummaryOwnerAndReturnsWithoutWrites() {
        compose.setContent { NativeModuleReviewTheme { AnalyticsModuleHost(database, farm, {}) } }
        compose.onNodeWithTag("farm-screen:FOS-AN-001").assertExists()
        open("Species performance", "FOS-AN-002")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("1 active").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Private animal", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Cattle").assertDoesNotExist()
        compose.captureModuleReview("FOS-AN-002")
        back()
        compose.captureModuleReview("FOS-AN-001")
        listOf(
            "Reproduction" to "FOS-AN-003", "Growth" to "FOS-AN-004", "Mortality" to "FOS-AN-005",
            "Health trends" to "FOS-AN-006", "Production" to "FOS-AN-007", "Feed efficiency" to "FOS-AN-008",
            "Financial performance" to "FOS-AN-009", "Benchmark compare" to "FOS-AN-010", "Forecasts" to "FOS-AN-012",
        ).forEach { (label, id) ->
            open(label, id)
            compose.captureModuleReview(id)
            compose.onNodeWithText("Private animal", substring = true).assertDoesNotExist()
            compose.onNodeWithText("private", substring = true).assertDoesNotExist()
            back()
        }
        runBlocking { assertEquals(1, database.reports().moneyRecords(farm).size); assertEquals(1, database.feedIssues().count(farm)) }
    }

    @Test fun anomalyOpensItsRecordedEvidenceRatherThanMakingAMutation() {
        compose.setContent { NativeModuleReviewTheme { AnalyticsModuleHost(database, farm, {}) } }
        open("Anomaly centre", "FOS-AN-011")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-tools-group:Review").fetchSemanticsNodes().isNotEmpty() }
        compose.captureModuleReview("FOS-AN-011")
        compose.revealFarmTool("Inventory at reorder level").performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-AN-013").assertExists()
        compose.onNodeWithText("1 item(s) at or below reorder quantity.").assertExists()
        compose.captureModuleReview("FOS-AN-013")
        compose.revealFarmTool("View evidence sources").performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-AN-014").assertExists()
        compose.onNodeWithText("Evidence: inventory totals (FarmReportDao.inventoryTotals).").assertExists()
        compose.captureModuleReview("FOS-AN-014")
        back()
        runBlocking { assertEquals(250L, database.inventory().item(farm, "feed")!!.quantityMilli) }
    }

    @Test fun anEmptyFarmIsNotFilledWithAnotherFarmsData() {
        compose.setContent { NativeModuleReviewTheme { AnalyticsModuleHost(database, UUID.randomUUID().toString(), {}) } }
        open("Species performance", "FOS-AN-002")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("No animals recorded").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("1 active").assertDoesNotExist()
        compose.captureModuleReview("FOS-AN-002")
        back()
        open("Financial performance", "FOS-AN-009")
        compose.onNodeWithText("No money records").assertExists()
        compose.captureModuleReview("FOS-AN-009")
    }

    private fun open(label: String, id: String) {
        compose.revealFarmTool(label).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:$id").assertExists()
    }
    private fun back() {
        compose.revealFarmTool("Analytics").performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-AN-001").assertExists()
    }
}
