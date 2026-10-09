package com.farmos.app

import android.content.Context
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.AnimalEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.FormularyItemEntity
import com.farmos.core.database.HealthPackEntity
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.access.LocalRole
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
 * vet-approved formulary item chosen from this farm's approved list, never a typed product or id,
 * and an observation (FOS-HEALTH-004) takes a governed species from the species selector atom.
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
        seedCommandAuthority(database, farm, "user-1", "device-1", LocalRole.OWNER)
        runBlocking {
            database.formulary().insert(FormularyItemEntity("form-ivo", farm, "Ivermectin 1%", "goat", "prescription", 35, 40, null, true))
            database.formulary().insert(FormularyItemEntity("form-draft", farm, "Unapproved drench", "goat", "prescription", 14, 14, null, false))
            database.lifecycle().insertPack(HealthPackEntity("pack-kid", farm, "goat", "Kid health", "vet_accepted", "Dr Moyo"))
            database.lifecycle().insertPack(HealthPackEntity("pack-draft", farm, "goat", "Draft pack", "draft", null))
            database.formulary().insert(FormularyItemEntity("form-other", otherFarm, "Other farm product", "goat", "prescription", 7, 7, null, true))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun ownerFormularyEntrySavesAnExplicitUnapprovedDraft() {
        var syncRequests = 0
        render(HealthEntryPage.FORMULARY) { syncRequests++ }
        compose.onNodeWithTag("farm-screen:FOS-HEALTH-013").assertExists()
        compose.onNode(hasSetTextAction() and hasText("Product")).performScrollTo().performTextInput("Draft vaccine")
        compose.onNode(hasClickAction() and hasText("Save draft item")).performScrollTo().performClick()
        compose.waitUntil(10_000) {
            runBlocking { database.formulary().forFarm(farm) }.any { it.productName == "Draft vaccine" }
        }
        val item = runBlocking { database.formulary().forFarm(farm) }.single { it.productName == "Draft vaccine" }
        assertEquals(false, item.vetApproved)
        compose.waitUntil(10_000) { syncRequests == 1 }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Draft vaccine", substring = true) and hasText("Draft · not vet approved", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText("Add approved item")).assertDoesNotExist()
    }

    @Test
    fun treatmentReferencesAnApprovedFormularyItemFromTheSelector() {
        var syncRequests = 0
        render(HealthEntryPage.TREATMENT) { syncRequests++ }
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

    @Test
    fun observationTakesItsSpeciesFromTheSpeciesSelector() {
        var syncRequests = 0
        render(HealthEntryPage.RECORD_OBSERVATION) { syncRequests++ }
        compose.onNodeWithTag("farm-screen:FOS-HEALTH-004").assertExists()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-003:option:goat").assertIsSelected()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-003:option:cattle").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Signs observed")).performScrollTo().performTextInput("Lame on left hind")
        compose.onNode(hasClickAction() and hasText("Save observation")).performScrollTo().performClick()

        compose.waitUntil(10_000) { runBlocking { database.healthObservations().recent(farm, 10) }.isNotEmpty() }
        assertEquals("cattle", runBlocking { database.healthObservations().recent(farm, 10) }.single().speciesCode)
        compose.waitUntil(10_000) { syncRequests == 1 }
    }

    @Test
    fun protocolSlotIsAddedOnlyToAVetAcceptedPack() {
        var syncRequests = 0
        render(HealthEntryPage.DASHBOARD) { syncRequests++ }
        compose.onNode(hasClickAction() and hasText("Add protocol slot")).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-HEALTH-019").assertExists()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("health-pack-selector:option:pack-kid").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("health-pack-selector:option:pack-draft").assertDoesNotExist()

        compose.onNodeWithTag("health-pack-selector:option:pack-kid").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Slot code")).performScrollTo().performTextInput("DEWORM")
        compose.onNode(hasSetTextAction() and hasText("Title")).performScrollTo().performTextInput("Deworm kids")
        compose.onNode(hasSetTextAction() and hasText("Offset days")).performScrollTo().performTextReplacement("14")
        compose.onNode(hasClickAction() and hasText("Add protocol slot")).performScrollTo().performClick()

        compose.waitUntil(10_000) { runBlocking { database.lifecycle().packSlots(farm) }.isNotEmpty() }
        val slot = runBlocking { database.lifecycle().packSlots(farm) }.single()
        assertEquals("pack-kid", slot.packId)
        assertEquals(14, slot.offsetDays)
        compose.waitUntil(10_000) { syncRequests == 1 }
    }

    @Test
    fun labResultSearchesEveryAnimalOnTheFarmNotACappedList() {
        seedAnimals()
        var syncRequests = 0
        render(HealthEntryPage.LAB_RESULT) { syncRequests++ }
        compose.onNodeWithTag("farm-screen:FOS-HEALTH-024").assertExists()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-004").assertExists()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-atom:FOS-ATOM-004:option:cow-1").fetchSemanticsNodes().isNotEmpty() }
        // One page of 25 in tag order: cattle C-0001 then G-0001..G-0024; the rest is one "Show more" away.
        compose.onNodeWithTag("farm-atom:FOS-ATOM-004:option:goat-0025").assertDoesNotExist()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-004:more").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-atom:FOS-ATOM-004:option:goat-0025").fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithTag("farm-atom:FOS-ATOM-004:query").performScrollTo().performTextInput("G-0055")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-atom:FOS-ATOM-004:option:goat-0055").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-atom:FOS-ATOM-004:option:goat-0001").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("farm-atom:FOS-ATOM-004:option:goat-other").assertDoesNotExist()

        compose.onNodeWithTag("farm-atom:FOS-ATOM-004:option:goat-0055").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Lab test")).performScrollTo().performTextInput("Faecal egg count")
        compose.onNode(hasSetTextAction() and hasText("Result")).performScrollTo().performTextInput("1200 epg")
        compose.onNode(hasClickAction() and hasText("Record lab result")).performScrollTo().performClick()

        compose.waitUntil(10_000) { runBlocking { database.lifecycle().labResults(farm, 10) }.isNotEmpty() }
        assertEquals("goat-0055", runBlocking { database.lifecycle().labResults(farm, 10) }.single().animalId)
        compose.waitUntil(10_000) { syncRequests == 1 }
    }

    @Test
    fun likeWildcardsTypedIntoSearchMatchLiterally() {
        assertEquals("50\\%", escapeLike("50%"))
        assertEquals("G\\_1", escapeLike("G_1"))
        assertEquals("a\\\\b", escapeLike("a\\b"))
    }

    private fun render(entryPage: HealthEntryPage, onSync: () -> Unit) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                HealthModuleHost(
                    farmId = farm,
                    ops = RoomOpsRepository(database, farm),
                    newContext = { LocalCommandContext(farm, "user-1", "device-1", UUID.randomUUID().toString(), 1_790_000_000_000L) },
                    enqueueSync = onSync,
                    onBack = {},
                    entryPage = entryPage,
                    searchAnimals = animalSelectorSearch(database, farm, speciesCode = null),
                )
            }
        }
    }

    private fun seedAnimals() = runBlocking {
        (1..60).forEach { n ->
            val tag = "G-%04d".format(n)
            database.animals().insert(AnimalEntity(id = "goat-%04d".format(n), farmId = farm, tag = tag, name = null, speciesCode = "goat", sex = "FEMALE", status = "active", dateOfBirthEpochDay = null, updatedAtEpochMillis = 1L))
        }
        database.animals().insert(AnimalEntity(id = "cow-1", farmId = farm, tag = "C-0001", name = "Daisy", speciesCode = "cattle", sex = "FEMALE", status = "active", dateOfBirthEpochDay = null, updatedAtEpochMillis = 1L))
        database.animals().insert(AnimalEntity(id = "goat-other", farmId = otherFarm, tag = "G-0055X", name = null, speciesCode = "goat", sex = "FEMALE", status = "active", dateOfBirthEpochDay = null, updatedAtEpochMillis = 1L))
    }
}
