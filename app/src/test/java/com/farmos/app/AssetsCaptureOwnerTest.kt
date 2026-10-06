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
import com.farmos.core.database.FarmAssetEntity
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
 * The real assets owner (FOS-ASSET-001) on an in-memory farm database: maintenance capture picks
 * its asset from this farm's assets through the asset selector atom and commits locally through
 * the governed command.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class AssetsCaptureOwnerTest {
    @get:Rule
    val compose = createComposeRule()

    private val farm = "11111111-1111-4111-8111-111111111111"
    private val otherFarm = "22222222-2222-4222-8222-222222222222"
    private lateinit var database: FarmOsDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java).build()
        runBlocking {
            database.assets().insert(FarmAssetEntity("asset-pump", farm, "PUMP-1", "Borehole pump", "equipment"))
            database.assets().insert(FarmAssetEntity("asset-other", otherFarm, "TRAC-9", "Other farm tractor", "vehicle"))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun maintenanceSelectsThisFarmsAssetAndCommitsLocally() {
        var syncRequests = 0
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                AssetsModuleHost(
                    farmId = farm,
                    ops = RoomOpsRepository(database, farm),
                    newContext = { LocalCommandContext(farm, "user-1", "device-1", UUID.randomUUID().toString(), 1_790_000_000_000L) },
                    enqueueSync = { syncRequests++ },
                    onBack = {},
                )
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-atom:FOS-ATOM-014:option:asset-pump").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("farm-screen:FOS-ASSET-001").assertExists()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-014").assertExists()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-014:option:asset-other").assertDoesNotExist()

        compose.onNodeWithTag("farm-atom:FOS-ATOM-014:option:asset-pump").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Maintenance title")).performScrollTo().performTextInput("Replaced impeller")
        compose.onNode(hasSetTextAction() and hasText("Date")).performScrollTo().performTextInput("2026-09-20")
        compose.onNode(hasClickAction() and hasText("Record maintenance")).performScrollTo().performClick()

        compose.waitUntil(10_000) { runBlocking { database.maintenance().recent(farm, 10) }.isNotEmpty() }
        val event = runBlocking { database.maintenance().recent(farm, 10) }.single()
        assertEquals("asset-pump", event.assetId)
        assertEquals("Replaced impeller", event.title)
        compose.waitUntil(10_000) { syncRequests == 1 }
    }
}
