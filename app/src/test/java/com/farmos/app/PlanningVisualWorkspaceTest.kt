package com.farmos.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.*
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real Room-backed planning surfaces: exact units, explicit time bases and focused editors. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class PlanningVisualWorkspaceTest {
    @get:Rule(order = 0) val nativeReview = NativeModuleReviewRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val farm = UUID.randomUUID().toString()
    private val other = UUID.randomUUID().toString()
    private val day = LocalDate.of(2026, 9, 1).toEpochDay()
    private lateinit var db: FarmOsDatabase
    private lateinit var scratch: File

    @Before fun seed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FarmOsDatabase::class.java).build()
        scratch = File(context.cacheDir, "planning-review-$farm").also { it.mkdirs() }
        runBlocking {
            db.inventory().insertItem(InventoryItemEntity("hay", farm, "HAY", "Hay", "kg", 4250, 1, 500))
            db.inventory().insertItem(InventoryItemEntity("mineral", farm, "MIN", "Mineral", "bag", 5000, 1, 500))
            db.inventory().insertItem(InventoryItemEntity("private", other, "HIDDEN", "Private feed", "kg", 99000, 1, 500))
            db.feedIssues().insert(FeedIssueEntity("f1", farm, "hay", null, 625, day))
            db.feedIssues().insert(FeedIssueEntity("f2", farm, "hay", null, 625, day + 9))
            db.feedIssues().insert(FeedIssueEntity("f3", farm, "mineral", null, 2500, day))
            db.feedIssues().insert(FeedIssueEntity("fx", other, "private", null, 99000, day))
            db.water().insert(WaterRecordEntity("w1", farm, "North tank", 750, day))
            db.water().insert(WaterRecordEntity("wx", other, "Private tank", 99000, day))
            db.animals().insert(AnimalEntity("goat", farm, "G-001", "Willow", "goat", "FEMALE", "active", null, updatedAtEpochMillis = 1))
            db.money().insert(MoneyRecordEntity("m1", farm, "income", "produce", 1000, "USD", day, null))
            db.money().insert(MoneyRecordEntity("m2", farm, "expense", "feed", 200, "USD", day + 9, null))
            db.money().insert(MoneyRecordEntity("mx", other, "income", "private", 900000, "USD", day, null))
        }
    }

    @After fun close() { db.close(); scratch.deleteRecursively() }

    @Test fun capacityShowsExactRecordedQuantitiesWithoutMixingUnitsOrFarms() {
        compose.setContent { NativeModuleReviewTheme() { CapacityModuleHost(db, farm, {}) } }
        compose.revealFarmTool("Feed capacity").performScrollTo().performClick()
        awaitText("1.25 kg")
        compose.onNodeWithText("2.5 bag").assertExists()
        compose.captureModuleReview("FOS-CAP-007")
        compose.onNodeWithText("Private feed", substring = true).assertDoesNotExist()
        compose.onNodeWithText("1 g", substring = true).assertDoesNotExist()
        compose.revealFarmTool("Capacity").performScrollTo().performClick()
        compose.revealFarmTool("Water capacity").performScrollTo().performClick()
        awaitText("0.75 L")
        compose.captureModuleReview("FOS-CAP-008")
        compose.onNodeWithText("Private tank", substring = true).assertDoesNotExist()
        runBlocking { assertEquals(3, db.feedIssues().count(farm)); assertEquals(1, db.water().count(farm)) }
    }

    @Test fun capacityProjectionUsesItsActualDatedSpanRatherThanClaimingThirtyDaysOfHistory() {
        compose.setContent { NativeModuleReviewTheme() { CapacityModuleHost(db, farm, {}) } }
        compose.revealFarmTool("Capacity forecast").performScrollTo().performClick()
        awaitText("3.75 kg")
        compose.onNodeWithText("2026-09-01 to 2026-09-10 · 10-day recorded span").assertExists()
        compose.onNodeWithText("If the recorded daily issue rate continues for 30 days.").assertExists()
        compose.onNodeWithText("Projection from the last 30 days", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("capacity-forecast-hay").assertExists()
        compose.captureModuleReview("FOS-CAP-009")
    }

    @Test fun financialScenarioContainsOnlyRelevantInputsAndKeepsTheRecordedBaseline() {
        compose.setContent { NativeModuleReviewTheme() { SimulationModuleHost(db, farm, scratch, {}) } }
        compose.revealFarmTool("Financial scenario").performScrollTo().performClick()
        compose.onNodeWithText("Horizon (months)").assertExists()
        compose.onNodeWithText("Species code").assertDoesNotExist()
        compose.onNodeWithText("Births per female per year").assertDoesNotExist()
        compose.onNodeWithText("Death rate per year").assertDoesNotExist()
        compose.onNodeWithText("Assumed feed g/head/day").assertDoesNotExist()
        compose.revealFarmTool("Run scenario").performScrollTo().assertIsEnabled().performClick()
        awaitText("8.00 USD")
        compose.onNodeWithTag("scenario-results:FINANCE").assertExists()
        compose.captureModuleReview("FOS-SIM-006")
        compose.onNodeWithText("9000.00 USD", substring = true).assertDoesNotExist()
        runBlocking { assertEquals(2, db.reports().moneyRecords(farm).size) }
    }

    @Test fun feedScenarioRetainsItsDraftAndDoesNotPretendToRecordConsumption() {
        compose.setContent { NativeModuleReviewTheme() { SimulationModuleHost(db, farm, scratch, {}) } }
        compose.revealFarmTool("Feed scenario").performScrollTo().performClick()
        compose.onNodeWithText("Births per female per year").assertDoesNotExist()
        compose.onNodeWithText("Assumed feed g/head/day").performScrollTo().performTextReplacement("1250")
        compose.revealFarmTool("Simulation hub").performScrollTo().performClick()
        compose.revealFarmTool("Feed scenario").performScrollTo().performClick()
        compose.onNodeWithText("Assumed feed g/head/day").assertTextContains("1250")
        compose.revealFarmTool("Run scenario").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("scenario-results:FEED").assertExists()
        compose.captureModuleReview("FOS-SIM-004")
        runBlocking { assertEquals(3, db.feedIssues().count(farm)) }
    }

    private fun awaitText(value: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
    }
}
