package com.farmos.app

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.AnimalEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.GoatMilkEntity
import com.farmos.core.database.HealthObservationEntity
import com.farmos.core.database.InventoryItemEntity
import com.farmos.core.database.KiddingEntity
import com.farmos.core.database.MeasurementEntity
import com.farmos.core.database.MoneyRecordEntity
import com.farmos.core.database.TaskEntity
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.domain.access.LocalRole
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real report owners and Room queries, with farm isolation, exact totals, sharing authority and Back. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class ReportRuntimeNavigationTest {
    @get:Rule val compose = createComposeRule()

    private val farm = UUID.randomUUID().toString()
    private val otherFarm = UUID.randomUUID().toString()
    private val day = LocalDate.of(2026, 10, 1).toEpochDay()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: FarmOsDatabase
    private lateinit var exportAuthorities: Map<String, LocalSessionAuthority>

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, FarmOsDatabase::class.java).build()
        val deviceId = "report-device"
        val directory = LocalFarmDirectory(database, deviceId)
        exportAuthorities = listOf(farm, otherFarm).associateWith { id ->
            val actorId = "report-manager-$id"
            seedCommandAuthority(database, id, actorId, deviceId, LocalRole.MANAGER)
            runBlocking(Dispatchers.IO) {
                LocalSessionAuthority(database, directory, requireNotNull(directory.account(id, actorId)), deviceId)
            }
        }
        runBlocking {
            database.animals().insert(animal("goat-a", farm, "G-101", "Mango"))
            database.animals().insert(animal("goat-b", farm, "G-102", "Bramble"))
            database.animals().insert(animal("goat-x", otherFarm, "G-101", "Other farm Mango"))
            database.measurements().insert(MeasurementEntity("weight-a", farm, "goat-a", "weight", 45_500, "g", 2_000))
            database.measurements().insert(MeasurementEntity("weight-old", farm, "goat-a", "weight", 40_000, "g", 1_000))
            database.measurements().insert(MeasurementEntity("weight-x", otherFarm, "goat-x", "weight", 99_000, "g", 3_000))
            repeat(51) { index ->
                database.money().insert(MoneyRecordEntity("usd-$index", farm, "income", "produce", 125, "USD", day + index, null))
            }
            database.money().insert(MoneyRecordEntity("zar", farm, "expense", "feed", 9_900, "ZAR", day, null))
            database.money().insert(MoneyRecordEntity("foreign-money", otherFarm, "income", "private", 900_000, "USD", day, "Other farm sale"))
            database.inventory().insertItem(InventoryItemEntity("feed", farm, "HAY", "Hay", "kg", 1_250, 1, 2_750))
            database.inventory().insertItem(InventoryItemEntity("foreign-stock", otherFarm, "PRIVATE", "Other farm feed", "kg", 999_000, 1))
            database.healthObservations().insert(HealthObservationEntity("observation", farm, "goat-a", "goat", "Appetite checked", null, false, 2_000))
            database.healthObservations().insert(HealthObservationEntity("foreign-observation", otherFarm, "goat-x", "goat", "Private clinical finding", null, false, 2_000))
            database.lifecycle().insertMilk(GoatMilkEntity("milk", farm, "goat-a", 12_500, day))
            database.kidding().insert(KiddingEntity("birth", farm, "goat-a", 2, 2, 0, day))
            database.tasks().insert(TaskEntity("task", farm, "feed", "FEED_CHECK", "Check the hay rack", day, "open", null, null, null, 1))
        }
    }

    @After
    fun tearDown() {
        database.close()
        listOf(farm, otherFarm).forEach { File(context.filesDir, "report_exports/$it.jsonl").delete() }
    }

    @Test
    fun animalSearchSelectsOnlyThisFarmsRecordAndBackRestoresTheReportHub() {
        render()
        open("FOS-REPORT-002")
        compose.onNodeWithTag("animal-report-search").performScrollTo().performTextInput("G-101")
        clickTag("animal-report-search-go")
        awaitTag("animal-report-pick:goat-a")
        compose.onNodeWithTag("animal-report-pick:goat-x").assertDoesNotExist()
        clickTag("animal-report-pick:goat-a")
        awaitText("45.5 kg")
        compose.onNodeWithText("G-101 · Mango").assertExists()
        compose.onNodeWithText("99.0 kg").assertDoesNotExist()
        compose.onNodeWithText("No parentage recorded").assertExists()
        backToHub("FOS-REPORT-002")
    }

    @Test
    fun reportJourneysUseExhaustiveFarmRecordsAndKeepUnitsAndCurrenciesSeparate() {
        render()
        open("FOS-REPORT-003")
        awaitText("2 active of 2 recorded")
        compose.onNodeWithText("G-101 · Mango · active · 45.5 kg").assertExists()
        compose.onNodeWithText("Other farm Mango", substring = true).assertDoesNotExist()
        backToHub("FOS-REPORT-003")

        open("FOS-REPORT-004")
        awaitText("Observations: 1")
        compose.onNodeWithText("Appetite checked", substring = true).assertExists()
        compose.onNodeWithText("Private clinical finding", substring = true).assertDoesNotExist()
        backToHub("FOS-REPORT-004")

        open("FOS-REPORT-005")
        awaitTag("report-metric:production-goat-milk")
        compose.onNodeWithTag("report-metric:production-goat-milk").assertTextEquals("12.5 L")
        backToHub("FOS-REPORT-005")

        open("FOS-REPORT-006")
        awaitTag("report-metric:money-income-USD")
        compose.onNodeWithTag("report-metric:money-income-USD").assertTextEquals("63.75 USD")
        compose.onNodeWithTag("report-metric:money-expense-ZAR").assertTextEquals("99.00 ZAR")
        compose.onNodeWithText("Most recent 50 of 52.").assertExists()
        compose.onNodeWithText("Other farm sale", substring = true).assertDoesNotExist()
        backToHub("FOS-REPORT-006")

        open("FOS-REPORT-007")
        awaitText("Items tracked: 1")
        compose.onNodeWithText("Hay (HAY): 1.25 kg · reorder at 2.75 kg · at or below reorder").assertExists()
        compose.onNodeWithText("Other farm feed", substring = true).assertDoesNotExist()
        backToHub("FOS-REPORT-007")

        open("FOS-REPORT-008")
        awaitTag("report-metric:born-alive-goat")
        compose.onNodeWithTag("report-metric:born-alive-goat").assertTextEquals("2 animals")
        backToHub("FOS-REPORT-008")

        open("FOS-REPORT-009")
        awaitText("Animals on record: 2")
        compose.onNodeWithText("Animals with parentage: 0 (0%)").assertExists()
        backToHub("FOS-REPORT-009")

        open("FOS-REPORT-010")
        awaitText("Open: 1")
        compose.onNodeWithText("Check the hay rack · due 2026-10-01 · feed").assertExists()
        backToHub("FOS-REPORT-010")

        open("FOS-REPORT-015")
        awaitText("No official movement records on this device yet.")
        backToHub("FOS-REPORT-015")
    }

    @Test
    fun generatedDocumentsKeepTheSelectedExportAndFarmWhenReturningToTheList() {
        appendExportLog(context, farm, ExportLogEntry("first", "herd-register", "first.csv", 1_000, 2))
        appendExportLog(context, farm, ExportLogEntry("second", "money-records", "second.csv", 2_000, 52))
        appendExportLog(context, otherFarm, ExportLogEntry("foreign", "money-records", "private.csv", 3_000, 999))
        render()
        open("FOS-REPORT-012")
        awaitTag("report-document:first")
        compose.onNodeWithTag("report-document:foreign").assertDoesNotExist()
        clickTag("report-document:first")
        awaitText("Suggested filename: first.csv")
        compose.onNodeWithTag("farm-screen:FOS-REPORT-013").assertExists()
        compose.onNodeWithText("Records: 2").assertExists()
        compose.onNodeWithText("Suggested filename: second.csv").assertDoesNotExist()
        click("Reports")
        awaitTag("farm-screen:FOS-REPORT-012")
        clickTag("report-document:second")
        awaitText("Suggested filename: second.csv")
        compose.onNodeWithText("Records: 52").assertExists()
        click("Reports")
        backToHub("FOS-REPORT-012")
    }

    @Test
    fun sharingUsesThisFarmsActualSummaryAndRetainsMissingDataDisclosure() {
        render()
        open("FOS-REPORT-014")
        awaitTag("report-share")
        clickTag("report-share")
        var launchedChooser: Intent? = null
        compose.waitUntil(10_000) {
            // Room admission resumes on Android's main queue; the Compose clock alone does not
            // drain that queue in Robolectric. Observe the real chooser after main-thread idling.
            compose.runOnIdle {
                launchedChooser = launchedChooser ?: shadowOf(context as android.app.Application).nextStartedActivity
            }
            launchedChooser != null
        }
        val chooser = requireNotNull(launchedChooser)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = requireNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        val shared = requireNotNull(send.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue(shared.contains("63.75 USD"))
        assertTrue(shared.contains("99.00 ZAR"))
        assertTrue(shared.contains("Partial: 1 of 2 record(s) have no value"))
        assertFalse(shared.contains("9000.00 USD"))
        backToHub("FOS-REPORT-014")
    }

    @Test
    fun aRoleWithoutExportPermissionCanReadReportsButCannotOpenExportOrShare() {
        render(canExport = false)
        compose.onNodeWithText("Exports are made by farm management.").assertExists()
        compose.onNodeWithTag("report-open-export").assertDoesNotExist()
        compose.onNodeWithTag("report-open:FOS-REPORT-014").assertDoesNotExist()
        compose.onNodeWithTag("report-share").assertDoesNotExist()
        open("FOS-REPORT-003")
        awaitText("2 active of 2 recorded")
        backToHub("FOS-REPORT-003")
        assertEquals(null, shadowOf(context as android.app.Application).nextStartedActivity)
    }

    @Test
    fun revokingExportPermissionClosesAnAlreadyOpenShareRoute() {
        val allowed = mutableStateOf(true)
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                ReportsModuleHost(database, farm, allowed.value, onBack = {}, exportAuthority = exportAuthorities.getValue(farm))
            }
        }
        open("FOS-REPORT-014")
        awaitTag("report-share")
        compose.runOnIdle { allowed.value = false }
        awaitTag("farm-screen:FOS-REPORT-001")
        compose.onNodeWithTag("report-share").assertDoesNotExist()
        compose.onNodeWithTag("report-open:FOS-REPORT-014").assertDoesNotExist()
        assertEquals(null, shadowOf(context as android.app.Application).nextStartedActivity)
    }

    @Test
    fun revokingExportPermissionClosesTheExportSheetAndDoesNotRestoreItOnRegrant() {
        val allowed = mutableStateOf(true)
        var exports = 0
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                ReportsScreen(
                    metrics = emptyList(), failure = null, canExport = allowed.value, exporting = false,
                    exportMessage = null, onExportHerdRegister = { exports++ }, onBack = {},
                )
            }
        }
        clickTag("report-open-export")
        compose.onNodeWithTag("farm-screen:FOS-REPORT-011").assertExists()
        compose.runOnIdle { allowed.value = false }
        awaitTag("farm-screen:FOS-REPORT-001")
        compose.onNodeWithTag("report-export-herd-register").assertDoesNotExist()
        compose.runOnIdle { allowed.value = true }
        compose.onNodeWithTag("farm-screen:FOS-REPORT-001").assertExists()
        compose.onNodeWithTag("farm-screen:FOS-REPORT-011").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, exports) }
    }

    @Test
    fun switchingFarmsDiscardsThePreviousAnimalsReportAndSearchSelection() {
        val selectedFarm = mutableStateOf(farm)
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                ReportsModuleHost(database, selectedFarm.value, canExport = true, onBack = {}, exportAuthority = exportAuthorities.getValue(selectedFarm.value))
            }
        }
        open("FOS-REPORT-002")
        compose.onNodeWithTag("animal-report-search").performScrollTo().performTextInput("G-101")
        clickTag("animal-report-search-go")
        awaitTag("animal-report-pick:goat-a")
        clickTag("animal-report-pick:goat-a")
        awaitText("45.5 kg")
        compose.runOnIdle { selectedFarm.value = otherFarm }
        awaitTag("farm-screen:FOS-REPORT-001")
        compose.onNodeWithTag("farm-screen:FOS-REPORT-002").assertDoesNotExist()
        open("FOS-REPORT-002")
        compose.onNodeWithTag("animal-report-pick:goat-a").assertDoesNotExist()
        compose.onNodeWithText("45.5 kg").assertDoesNotExist()
        compose.onNodeWithTag("animal-report-search").performScrollTo().performTextInput("G-101")
        clickTag("animal-report-search-go")
        awaitTag("animal-report-pick:goat-x")
        compose.onNodeWithTag("animal-report-pick:goat-a").assertDoesNotExist()
        clickTag("animal-report-pick:goat-x")
        awaitText("99.0 kg")
        backToHub("FOS-REPORT-002")
    }

    private fun render(canExport: Boolean = true) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                ReportsModuleHost(database, farm, canExport, onBack = {}, exportAuthority = exportAuthorities.getValue(farm))
            }
        }
        awaitTag("farm-screen:FOS-REPORT-001")
    }

    private fun open(screenId: String) {
        // A mounted hub is not yet a stable navigation layout while its summary loads from Room.
        awaitTag("report-metric:active-goat")
        clickTag("report-open:$screenId")
        awaitTag("farm-screen:$screenId")
    }

    private fun backToHub(screenId: String) {
        click("Reports")
        awaitTag("farm-screen:FOS-REPORT-001")
        compose.onNodeWithTag("farm-screen:$screenId").assertDoesNotExist()
        compose.onNodeWithTag("report-open:$screenId").assertExists()
    }

    private fun click(text: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasClickAction() and hasText(text) and isEnabled()).fetchSemanticsNodes().size == 1
        }
        compose.onNode(hasClickAction() and hasText(text)).performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
    }
    private fun clickTag(tag: String) {
        // Returning to a list mounts its screen before the asynchronous entries are available.
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag(tag) and hasClickAction() and isEnabled()).fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
    }
    private fun awaitTag(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun awaitText(text: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    private fun animal(id: String, farmId: String, tag: String, name: String) =
        AnimalEntity(id, farmId, tag, name, "goat", "FEMALE", "active", null, updatedAtEpochMillis = 1)
}
