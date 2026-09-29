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
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.InventoryItemEntity
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
 * The real inventory owner on an in-memory farm database: stock receipt (FOS-INV-005) and the
 * reorder rule (FOS-INV-012) take their item from the inventory item selector atom, which lists
 * only this farm's items, and commit locally through the governed commands.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class InventoryCaptureOwnerTest {
    @get:Rule
    val compose = createComposeRule()

    private val farm = "11111111-1111-4111-8111-111111111111"
    private val otherFarm = "22222222-2222-4222-8222-222222222222"
    private lateinit var database: FarmOsDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java).build()
        runBlocking {
            database.inventory().insertItem(InventoryItemEntity("item-mash", farm, "MASH-20", "Layer mash", "kg", 12_500, 0L))
            database.inventory().insertItem(InventoryItemEntity("item-other", otherFarm, "OTHER-1", "Other farm feed", "kg", 1_000, 0L))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun receiptTakesItsItemFromThisFarmsItemSelector() {
        var syncRequests = 0
        render { syncRequests++ }
        compose.onNode(hasClickAction() and hasText("Receive")).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-INV-005").assertExists()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-atom:FOS-ATOM-009:option:item-mash").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("farm-atom:FOS-ATOM-009:option:item-other").assertDoesNotExist()

        compose.onNodeWithTag("farm-atom:FOS-ATOM-009:option:item-mash").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Quantity")).performScrollTo().performTextInput("2.5")
        compose.onNode(hasClickAction() and hasText("Receive stock")).performScrollTo().performClick()

        compose.waitUntil(10_000) { runBlocking { database.inventory().item(farm, "item-mash") }?.quantityMilli == 15_000L }
        compose.waitUntil(10_000) { syncRequests == 1 }
    }

    @Test
    fun reorderRuleTakesItsItemFromThisFarmsItemSelector() {
        var syncRequests = 0
        render { syncRequests++ }
        compose.onNode(hasClickAction() and hasText("Set reorder point")).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-INV-012").assertExists()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-atom:FOS-ATOM-009:option:item-mash").fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithTag("farm-atom:FOS-ATOM-009:option:item-mash").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Reorder quantity")).performScrollTo().performTextInput("4")
        compose.onNode(hasClickAction() and hasText("Set reorder point")).performScrollTo().performClick()

        compose.waitUntil(10_000) { runBlocking { database.inventory().item(farm, "item-mash") }?.reorderMilli == 4_000L }
        assertEquals(12_500L, runBlocking { database.inventory().item(farm, "item-mash") }?.quantityMilli)
        compose.waitUntil(10_000) { syncRequests == 1 }
    }

    private fun render(onSync: () -> Unit) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                InventoryModuleHost(
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
