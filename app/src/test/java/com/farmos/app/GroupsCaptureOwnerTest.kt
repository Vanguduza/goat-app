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
 * The real groups owner (FOS-GROUP-001) on an in-memory farm database: group creation takes its
 * species from the species selector atom, census takes its group from the group selector atom,
 * and both commit locally through the governed commands.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class GroupsCaptureOwnerTest {
    @get:Rule
    val compose = createComposeRule()

    private val farm = "11111111-1111-4111-8111-111111111111"
    private val otherFarm = "22222222-2222-4222-8222-222222222222"
    private lateinit var database: FarmOsDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java).build()
        runBlocking {
            database.groups().insert(AnimalGroupEntity("grp-ewes", farm, "sheep", "Ewe flock", 120))
            database.groups().insert(AnimalGroupEntity("grp-other", otherFarm, "cattle", "Other farm herd", 30))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun groupCreationTakesItsSpeciesFromTheSpeciesSelector() {
        var syncRequests = 0
        render { syncRequests++ }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-atom:FOS-ATOM-003:option:cattle").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("farm-screen:FOS-GROUP-001").assertExists()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-003:option:cattle").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Name")).performScrollTo().performTextInput("Weaner steers")
        compose.onNode(hasSetTextAction() and hasText("Head count")).performScrollTo().performTextInput("40")
        compose.onNode(hasClickAction() and hasText("Create group")).performScrollTo().performClick()

        compose.waitUntil(10_000) { runBlocking { database.groups().forFarm(farm) }.size == 2 }
        val created = runBlocking { database.groups().forFarm(farm) }.single { it.name == "Weaner steers" }
        assertEquals("cattle", created.speciesCode)
        assertEquals(40, created.headCount)
        compose.waitUntil(10_000) { syncRequests == 1 }
    }

    @Test
    fun censusTakesItsGroupFromThisFarmsGroupSelector() {
        var syncRequests = 0
        render { syncRequests++ }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-atom:FOS-ATOM-005:option:grp-ewes").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("farm-atom:FOS-ATOM-005:option:grp-other").assertDoesNotExist()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-005:option:grp-ewes").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Census day")).performScrollTo().performTextInput("2026-09-20")
        compose.onNode(hasSetTextAction() and hasText("Census head count")).performScrollTo().performTextInput("118")
        compose.onNode(hasClickAction() and hasText("Record census")).performScrollTo().performClick()

        compose.waitUntil(10_000) { runBlocking { database.lifecycle().censusForGroup(farm, "grp-ewes") }.isNotEmpty() }
        assertEquals(118, runBlocking { database.lifecycle().censusForGroup(farm, "grp-ewes") }.single().headCount)
        compose.waitUntil(10_000) { syncRequests == 1 }
    }

    private fun render(onSync: () -> Unit) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                GroupsModuleHost(
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
