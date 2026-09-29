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
import com.farmos.core.database.FormularyItemEntity
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.feature.ops.HealthEntryPage
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
 * The real health owner on an in-memory farm database: a treatment (FOS-HEALTH-007) references a
 * vet-approved formulary item chosen from this farm's approved list, never a typed product or id.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class HealthCaptureOwnerTest {
    @get:Rule
    val compose = createComposeRule()

    private val farm = "11111111-1111-4111-8111-111111111111"
    private val otherFarm = "22222222-2222-4222-8222-222222222222"
    private lateinit var database: FarmOsDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java).build()
        runBlocking {
            database.formulary().insert(FormularyItemEntity("form-ivo", farm, "Ivermectin 1%", "goat", "prescription", 35, 40, null, true))
            database.formulary().insert(FormularyItemEntity("form-draft", farm, "Unapproved drench", "goat", "prescription", 14, 14, null, false))
            database.formulary().insert(FormularyItemEntity("form-other", otherFarm, "Other farm product", "goat", "prescription", 7, 7, null, true))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun treatmentReferencesAnApprovedFormularyItemFromTheSelector() {
        var syncRequests = 0
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                HealthModuleHost(
                    farmId = farm,
                    ops = RoomOpsRepository(database, farm),
                    newContext = { LocalCommandContext(farm, "user-1", "device-1", UUID.randomUUID().toString(), 1_790_000_000_000L) },
                    enqueueSync = { syncRequests++ },
                    onBack = {},
                    entryPage = HealthEntryPage.TREATMENT,
                )
            }
        }
        compose.onNodeWithTag("farm-screen:FOS-HEALTH-007").assertExists()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("health-formulary-selector:option:form-ivo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("health-formulary-selector:option:form-draft").assertDoesNotExist()
        compose.onNodeWithTag("health-formulary-selector:option:form-other").assertDoesNotExist()

        compose.onNodeWithTag("health-formulary-selector:option:form-ivo").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Reason")).performScrollTo().performTextInput("Worm burden on FAMACHA 4")
        compose.onNode(hasClickAction() and hasText("Record treatment")).performScrollTo().performClick()

        compose.waitUntil(10_000) { runBlocking { database.treatments().recent(farm, 10) }.isNotEmpty() }
        val treatment = runBlocking { database.treatments().recent(farm, 10) }.single()
        assertEquals("form-ivo", treatment.formularyItemId)
        assertEquals(35, treatment.meatWithdrawalDays)
        compose.waitUntil(10_000) { syncRequests == 1 }
    }
}
