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
import com.farmos.core.database.SupplierEntity
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import java.time.LocalDate
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
 * The real procurement owner (FOS-PROC-001) on an in-memory farm database: the purchase capture
 * picks its supplier and inventory item from this farm's records through the selector atoms, the
 * governed command commits locally, and the offline save receipt follows.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class ProcurementCaptureOwnerTest {
    @get:Rule
    val compose = createComposeRule()

    private val farm = "11111111-1111-4111-8111-111111111111"
    private val otherFarm = "22222222-2222-4222-8222-222222222222"
    private lateinit var database: FarmOsDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java).build()
        runBlocking {
            database.lifecycle().insertSupplier(SupplierEntity("sup-agri", farm, "Agri Supply", 5))
            database.lifecycle().insertSupplier(SupplierEntity("sup-other-farm", otherFarm, "Other Farm Supplier", 1))
            database.inventory().insertItem(InventoryItemEntity("item-mash", farm, "MASH-20", "Layer mash", "kg", 12_500, 0L))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun purchaseSelectsThisFarmsSupplierAndItemAndCommitsLocally() {
        var syncRequests = 0
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                ProcurementModuleHost(
                    farmId = farm,
                    ops = RoomOpsRepository(database, farm),
                    newContext = { LocalCommandContext(farm, "user-1", "device-1", UUID.randomUUID().toString(), 1_790_000_000_000L) },
                    enqueueSync = { syncRequests++ },
                    onBack = {},
                )
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-atom:FOS-ATOM-011:option:sup-agri").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("farm-screen:FOS-PROC-001").assertExists()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-011").assertExists()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-009").assertExists()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-011:option:sup-other-farm").assertDoesNotExist()

        compose.onNodeWithTag("farm-atom:FOS-ATOM-011:option:sup-agri").performScrollTo().performClick()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-009:option:item-mash").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Quantity")).performScrollTo().performTextInput("2.5")
        compose.onNode(hasSetTextAction() and hasText("Amount")).performScrollTo().performTextInput("12.50")
        compose.onNode(hasSetTextAction() and hasText("Date")).performScrollTo().performTextInput("2026-09-20")
        compose.onNode(hasClickAction() and hasText("Record purchase")).performScrollTo().performClick()

        compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-atom:FOS-ATOM-034").fetchSemanticsNodes().isNotEmpty() }
        val purchase = runBlocking { database.lifecycle().purchases(farm, 10) }.single()
        assertEquals("sup-agri", purchase.supplierId)
        assertEquals("item-mash", purchase.itemId)
        assertEquals(2_500L, purchase.quantityMilli)
        assertEquals(1_250L, purchase.amountMinor)
        assertEquals(LocalDate.of(2026, 9, 20).toEpochDay(), purchase.occurredEpochDay)
        compose.runOnIdle { assertEquals(1, syncRequests) }
    }

    @Test
    fun purchaseIsRecordedInTheFarmCurrencyWithThatCurrencysMinorUnit() {
        runBlocking { database.setFarmCurrency(farm, "JPY", actorId = "owner-1", deviceId = "device-1") }
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                ProcurementModuleHost(
                    farmId = farm,
                    ops = RoomOpsRepository(database, farm),
                    newContext = { LocalCommandContext(farm, "user-1", "device-1", UUID.randomUUID().toString(), 1_790_000_000_000L) },
                    enqueueSync = {},
                    onBack = {},
                    loadCurrency = { database.farmCurrency(farm) },
                )
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-atom:FOS-ATOM-011:option:sup-agri").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("integer minor units of JPY", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("farm-atom:FOS-ATOM-011:option:sup-agri").performScrollTo().performClick()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-009:option:item-mash").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Quantity")).performScrollTo().performTextInput("1")
        compose.onNode(hasSetTextAction() and hasText("Amount")).performScrollTo().performTextInput("1500")
        compose.onNode(hasSetTextAction() and hasText("Date")).performScrollTo().performTextInput("2026-09-21")
        compose.onNode(hasClickAction() and hasText("Record purchase")).performScrollTo().performClick()

        compose.waitUntil(10_000) { runBlocking { database.lifecycle().purchases(farm, 10) }.isNotEmpty() }
        val purchase = runBlocking { database.lifecycle().purchases(farm, 10) }.single()
        assertEquals("JPY", purchase.currency)
        assertEquals(1_500L, purchase.amountMinor)
    }
}
