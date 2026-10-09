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
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.AnimalGroupEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.PaddockEntity
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
 * The real pasture owner (FOS-PASTURE-001) on an in-memory farm database: grazing capture picks its
 * paddock and group from this farm's records through the location and group selector atoms and
 * starts the session through the governed command.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class PastureCaptureOwnerTest {
    @get:Rule
    val compose = createComposeRule()

    private val farm = "11111111-1111-4111-8111-111111111111"
    private val otherFarm = "22222222-2222-4222-8222-222222222222"
    private lateinit var database: FarmOsDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java).build()
        runBlocking {
            database.seedCaptureOwner(farm)
            database.paddocks().insert(PaddockEntity("pad-north", farm, "N1", "North camp", null, "trough", true, true))
            database.paddocks().insert(PaddockEntity("pad-other", otherFarm, "X9", "Other farm camp", null, "dam", false, true))
            database.groups().insert(AnimalGroupEntity("grp-ewes", farm, "sheep", "Ewe flock", 120))
            database.groups().insert(AnimalGroupEntity("grp-other", otherFarm, "cattle", "Other farm herd", 30))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun grazingSelectsThisFarmsPaddockAndGroupAndStartsLocally() {
        var syncRequests = 0
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                PastureModuleHost(
                    farmId = farm,
                    ops = RoomOpsRepository(database, farm),
                    newContext = { LocalCommandContext(farm, "user-1", "device-1", UUID.randomUUID().toString(), 1_790_000_000_000L) },
                    enqueueSync = { syncRequests++ },
                    onBack = {},
                )
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-atom:FOS-ATOM-005:option:grp-ewes").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("farm-screen:FOS-PASTURE-001").assertExists()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-007").assertExists()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-007:option:pad-other").assertDoesNotExist()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-005:option:grp-other").assertDoesNotExist()

        compose.onNodeWithTag("farm-atom:FOS-ATOM-007:option:pad-north").performScrollTo().performClick()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-005:option:grp-ewes").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Head count")).performScrollTo().performTextInput("118")
        compose.onNode(hasSetTextAction() and hasText("Enter date")).performScrollTo().performTextInput("2026-09-20")
        compose.onNode(hasClickAction() and hasText("Start grazing")).performScrollTo().performClick()

        compose.waitUntil(10_000) { runBlocking { database.grazing().open(farm) }.isNotEmpty() }
        val session = runBlocking { database.grazing().open(farm) }.single()
        assertEquals("pad-north", session.paddockId)
        assertEquals("grp-ewes", session.groupId)
        assertEquals("sheep", session.speciesCode)
        assertEquals(118, session.headCount)
        compose.waitUntil(10_000) { syncRequests == 1 }
        runBlocking { database.assertLocalCaptureJournal(farm, "grazing.start.v1") }
    }
}
