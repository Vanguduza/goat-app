package com.farmos.app

import android.content.Context
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.AnimalGroupEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.FormularyItemEntity
import com.farmos.core.database.PoultryHouseEntity
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The real poultry owner on an in-memory farm database. Placement, the daily flock record and
 * vaccination take their flock from the group selector atom (this farm's poultry groups only),
 * placement takes its house from the location selector, and vaccination offers only vet-approved
 * poultry formulary items, mirroring what each command accepts server-side.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class PoultryCaptureOwnerTest {
    @get:Rule
    val compose = createComposeRule()

    private val farm = "11111111-1111-4111-8111-111111111111"
    private val otherFarm = "22222222-2222-4222-8222-222222222222"
    private lateinit var database: FarmOsDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java).build()
        runBlocking {
            database.groups().insert(AnimalGroupEntity("grp-layers", farm, "poultry", "Layer flock A", 400))
            database.groups().insert(AnimalGroupEntity("grp-ewes", farm, "sheep", "Ewe flock", 120))
            database.groups().insert(AnimalGroupEntity("grp-other", otherFarm, "poultry", "Other farm layers", 300))
            database.lifecycle().insertHouse(PoultryHouseEntity("house-a", farm, "LH-1", "layer", "chicken"))
            database.lifecycle().insertHouse(PoultryHouseEntity("house-other", otherFarm, "LH-9", "layer", "chicken"))
            database.formulary().insert(FormularyItemEntity("form-nd", farm, "ND vaccine", "poultry", "vaccine", null, null, 0, true))
            database.formulary().insert(FormularyItemEntity("form-goat", farm, "Goat vaccine", "goat", "vaccine", 0, 0, null, true))
            database.formulary().insert(FormularyItemEntity("form-draft", farm, "Unapproved vaccine", "poultry", "vaccine", null, null, 0, false))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun dailyFlockRecordTakesItsFlockFromThisFarmsPoultryGroups() {
        var syncRequests = 0
        render { syncRequests++ }
        compose.onNode(hasClickAction() and hasText("Open Daily flock record")).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-POULTRY-009").assertExists()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-atom:FOS-ATOM-005:option:grp-layers").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("farm-atom:FOS-ATOM-005:option:grp-ewes").assertDoesNotExist()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-005:option:grp-other").assertDoesNotExist()

        compose.onNodeWithTag("farm-atom:FOS-ATOM-005:option:grp-layers").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Eggs")).performScrollTo().performTextReplacement("312")
        compose.onNode(hasSetTextAction() and hasText("Dead")).performScrollTo().performTextReplacement("2")
        compose.onNode(hasSetTextAction() and hasText("Date")).performScrollTo().performTextReplacement("2026-09-20")
        compose.onNode(hasClickAction() and hasText("Record flock day")).performScrollTo().performClick()

        compose.waitUntil(10_000) { runBlocking { database.poultryFlockDays().recent(farm, 50) }.isNotEmpty() }
        val day = runBlocking { database.poultryFlockDays().recent(farm, 50) }.single()
        assertEquals("grp-layers", day.groupId)
        assertEquals(312, day.eggs)
        assertEquals(2, day.dead)
        compose.waitUntil(10_000) { syncRequests == 1 }
    }

    @Test
    fun placementTakesItsFlockAndHouseFromThisFarmsSelectors() {
        var syncRequests = 0
        render { syncRequests++ }
        compose.onNode(hasClickAction() and hasText("Open Place flock")).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-POULTRY-008").assertExists()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-atom:FOS-ATOM-007:option:house-a").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("farm-atom:FOS-ATOM-007:option:house-other").assertDoesNotExist()

        compose.onNodeWithTag("farm-atom:FOS-ATOM-005:option:grp-layers").performScrollTo().performClick()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-007:option:house-a").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Head count")).performScrollTo().performTextReplacement("400")
        compose.onNode(hasClickAction() and hasText("Place flock")).performScrollTo().performClick()

        compose.waitUntil(10_000) { runBlocking { database.lifecycle().placements(farm) }.isNotEmpty() }
        val placement = runBlocking { database.lifecycle().placements(farm) }.single()
        assertEquals("grp-layers", placement.groupId)
        assertEquals("house-a", placement.houseId)
        assertEquals(400, placement.headCount)
        compose.waitUntil(10_000) { syncRequests == 1 }
    }

    @Test
    fun vaccinationOffersOnlyVetApprovedPoultryFormularyItems() {
        var syncRequests = 0
        render { syncRequests++ }
        compose.onNode(hasClickAction() and hasText("Open Vaccination")).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-POULTRY-014").assertExists()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("poultry-formulary-selector:option:form-nd").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("poultry-formulary-selector:option:form-goat").assertDoesNotExist()
        compose.onNodeWithTag("poultry-formulary-selector:option:form-draft").assertDoesNotExist()

        compose.onNodeWithTag("farm-atom:FOS-ATOM-005:option:grp-layers").performScrollTo().performClick()
        compose.onNodeWithTag("poultry-formulary-selector:option:form-nd").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Vaccination date")).performScrollTo().performTextReplacement("2026-09-20")
        compose.onNode(hasClickAction() and hasText("Record vaccination")).performScrollTo().performClick()

        compose.waitUntil(10_000) { runBlocking { database.lifecycle().vaccinations(farm) }.isNotEmpty() }
        val vaccination = runBlocking { database.lifecycle().vaccinations(farm) }.single()
        assertEquals("grp-layers", vaccination.groupId)
        assertEquals("form-nd", vaccination.formularyItemId)
        compose.waitUntil(10_000) { syncRequests == 1 }
    }

    private fun render(onSync: () -> Unit) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                PoultryModuleHost(
                    farmId = farm,
                    ops = RoomOpsRepository(database, farm),
                    newContext = { LocalCommandContext(farm, "user-1", "device-1", UUID.randomUUID().toString(), 1_790_000_000_000L) },
                    enqueueSync = onSync,
                    onBack = {},
                )
            }
        }
    }
}
