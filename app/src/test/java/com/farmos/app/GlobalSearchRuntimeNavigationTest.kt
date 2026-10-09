package com.farmos.app

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.AnimalEntity
import com.farmos.core.database.AnimalIdentifierEntity
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.network.FarmMembership
import com.farmos.data.goat.RoomGoatRepository
import com.farmos.domain.access.LocalRole
import com.farmos.feature.goat.GoatEntryPage
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real farm-local queries and the production session dispatcher own these search/detail journeys. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = FarmOsApplication::class, sdk = [36], qualifiers = "en-rUS")
class GlobalSearchRuntimeNavigationTest {
    @get:Rule val compose = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<FarmOsApplication>()
    private val farmA = UUID.randomUUID().toString()
    private val farmB = UUID.randomUUID().toString()
    private val targetId = "$farmA-target"
    private val firstId = "$farmA-first"
    private val cowId = "$farmA-cow"
    private val foreignId = "$farmB-target"
    private lateinit var dispatcher: OnBackPressedDispatcher

    @Before
    fun seedLocalSubjectsAndCurrentAuthority() {
        assertFalse(app.backendConfigured)
        seedCommandAuthority(app.database, farmA, "owner-a", app.deviceId, LocalRole.OWNER)
        seedCommandAuthority(app.database, farmB, "owner-b", app.deviceId, LocalRole.OWNER)
        io {
            app.database.withTransaction {
                animal(firstId, farmA, "AA-FIRST", "First doe")
                animal(targetId, farmA, "ZZ-TARGET", "Target doe")
                animal(cowId, farmA, "ZZ-TARGET", "Target cow", species = "cattle")
                animal(foreignId, farmB, "ZZ-TARGET", "Other farm doe")
            }
        }
    }

    @After
    fun closeDatabase() = app.database.close()

    @Test
    fun anExactResultOutsideTheHerdWindowOpensThatGoatAndReturnsToItsSearch() {
        io {
            app.database.withTransaction {
                repeat(501) { index -> animal("$farmA-prefix-$index", farmA, "AB-${index.toString().padStart(3, '0')}", "Listed doe $index") }
            }
            assertFalse(app.database.animals().listBySpecies(farmA, "goat", 500).any { it.id == targetId })
        }
        content { session(FarmMembership(farmA, "owner")) }
        search("ZZ-TARGET")
        awaitText("Goat · ZZ-TARGET · Target doe · active · local")
        compose.onNodeWithText("Goat · ZZ-TARGET · Other farm doe · active · local").assertDoesNotExist()
        click("Filter results")
        click("Goat")
        compose.onNodeWithText("Goat (active)").assertExists()
        click("Goat · ZZ-TARGET · Target doe · active · local")
        awaitScreen("FOS-GOAT-003")
        awaitText("Target doe")
        compose.onNodeWithTag("farm-runtime-route:goat.profile:FOS-GOAT-003").assertExists()
        compose.onNodeWithText("First doe").assertDoesNotExist()
        compose.onNodeWithText("Target cow").assertDoesNotExist()
        compose.onNodeWithText("Other farm doe").assertDoesNotExist()
        click("Weight")
        awaitScreen("FOS-GOAT-011")
        field("Weight", "53.125")
        click("Record weight")
        compose.waitUntil(10_000) {
            io { app.database.measurements().history(farmA, targetId, "weight").size == 1 }
        }
        io {
            assertEquals(53_125L, app.database.measurements().history(farmA, targetId, "weight").single().valueLong)
            assertTrue(app.database.measurements().history(farmA, firstId, "weight").isEmpty())
            assertTrue(app.database.measurements().history(farmA, cowId, "weight").isEmpty())
            assertTrue(app.database.measurements().history(farmB, foreignId, "weight").isEmpty())
            val operation = app.database.replication().operationsInRange(farmA, app.deviceId, 1, Long.MAX_VALUE).single()
            assertEquals("goat.record_weight.v1", operation.operationType)
            assertEquals("owner-a", operation.actorId)
            assertEquals(farmA, operation.farmId)
            assertEquals(targetId, operation.entityId)
        }
        back()
        awaitScreen("FOS-GOAT-003")
        back()
        awaitScreen("FOS-HOME-006")
        compose.onNode(hasSetTextAction() and hasText("Tag, name, species or identifier") and hasText("ZZ-TARGET")).assertExists()
        compose.onNodeWithText("Goat (active)").assertExists()
        compose.onNodeWithText("Goat · ZZ-TARGET · Target doe · active · local").assertExists()
        compose.onNodeWithText("Cattle · ZZ-TARGET · Target cow · active · local").assertDoesNotExist()
        back()
        awaitScreen("FOS-HOME-012-A")
    }

    @Test
    fun retainedStatusFilterCanBeClearedAfterAQueryWithADifferentStatus() {
        val soldId = "$farmA-sold"
        io { animal(soldId, farmA, "SOLD-EXACT", "Sold doe", status = "sold") }
        content { session(FarmMembership(farmA, "owner")) }
        search("ZZ-TARGET")
        awaitText("Goat · ZZ-TARGET · Target doe · active · local")
        click("Filter results")
        click("Goat")
        click("active")
        compose.onNodeWithText("active (active)").assertExists()

        compose.onNode(hasSetTextAction() and hasText("Tag, name, species or identifier"))
            .performScrollTo().performTextReplacement("SOLD-EXACT")
        click("Search farm")
        awaitText("No animal records match the active filters.")
        compose.onNodeWithText("active (active)").assertDoesNotExist()
        compose.onNodeWithText("Clear filters to show the 1 local result(s) from this search.").assertExists()
        compose.onNodeWithText("Goat · SOLD-EXACT · Sold doe · sold · local").assertDoesNotExist()

        // A prior filter must remain resettable even when its status is absent from new results.
        click("Hide filters")
        click("Clear filters")
        awaitText("Goat · SOLD-EXACT · Sold doe · sold · local")
        compose.onNodeWithTag("farm-screen:FOS-SEARCH-004").assertDoesNotExist()
        compose.onNodeWithText("Clear filters").assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasText("Tag, name, species or identifier") and hasText("SOLD-EXACT")).assertExists()
        click("Goat · SOLD-EXACT · Sold doe · sold · local")
        awaitScreen("FOS-GOAT-003")
        awaitText("Sold doe")
        compose.onNodeWithText("Target doe").assertDoesNotExist()
        back()
        awaitScreen("FOS-HOME-006")
        compose.onNodeWithText("Goat · SOLD-EXACT · Sold doe · sold · local").assertExists()
        compose.onNodeWithText("Clear filters").assertDoesNotExist()
        io { assertTrue(app.database.replication().operationsInRange(farmA, app.deviceId, 1, Long.MAX_VALUE).isEmpty()) }
    }

    @Test
    fun switchingFarmsClearsSearchAndLoadsOnlyTheNewFarmsSubject() {
        val membership = mutableStateOf(FarmMembership(farmA, "owner"))
        content { session(membership.value) }
        search("ZZ-TARGET")
        awaitText("Goat · ZZ-TARGET · Target doe · active · local")
        click("Goat · ZZ-TARGET · Target doe · active · local")
        awaitScreen("FOS-GOAT-003")
        awaitText("Target doe")

        compose.runOnIdle { membership.value = FarmMembership(farmB, "owner") }
        awaitScreen("FOS-HOME-012-A")
        compose.onNodeWithTag("farm-screen:FOS-GOAT-003").assertDoesNotExist()
        click("Search farm")
        awaitScreen("FOS-HOME-006")
        compose.onNodeWithTag("farm-screen:FOS-HOME-007").assertDoesNotExist()
        compose.onNodeWithText("Recent searches in this farm session").assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasText("Tag, name, species or identifier") and hasText("ZZ-TARGET")).assertDoesNotExist()
        field("Tag, name, species or identifier", "ZZ-TARGET")
        click("Search farm")
        awaitText("Goat · ZZ-TARGET · Other farm doe · active · local")
        compose.onNodeWithText("Goat · ZZ-TARGET · Target doe · active · local").assertDoesNotExist()
        click("Goat · ZZ-TARGET · Other farm doe · active · local")
        awaitScreen("FOS-GOAT-003")
        awaitText("Other farm doe")
        compose.onNodeWithText("Target doe").assertDoesNotExist()
    }

    @Test
    fun profileEntriesRejectAnotherSpeciesAndAnotherFarmWithoutSelectingTheFirstGoat() {
        val entryId = mutableStateOf(cowId)
        io {
            val goats = RoomGoatRepository(app.database, farmA)
            assertNull(goats.getGoat(cowId))
            assertNull(goats.getGoat(foreignId))
        }
        var returned = 0
        content {
            GoatModuleHost(
                app = app, membership = FarmMembership(farmA, "owner"), farmName = "Search farm",
                newContext = { LocalCommandContext(farmA, "owner-a", app.deviceId, UUID.randomUUID().toString(), System.currentTimeMillis()) },
                enqueueSync = {}, onRequireReauth = {}, onRequireFarmReselection = { _, _ -> },
                onSignOut = {}, onBack = { returned++ },
                entryPage = GoatEntryPage.PROFILE, entryAnimalId = entryId.value,
            )
        }
        awaitText("The selected goat is not available on this farm.")
        compose.onNodeWithText("First doe").assertDoesNotExist()
        compose.onNodeWithText("Target cow").assertDoesNotExist()
        compose.onNodeWithText("Weight").assertDoesNotExist()
        compose.runOnIdle { entryId.value = foreignId }
        awaitText("The selected goat is not available on this farm.")
        compose.onNodeWithText("Other farm doe").assertDoesNotExist()
        back()
        compose.runOnIdle { assertEquals(1, returned) }
        io { assertTrue(app.database.replication().operationsInRange(farmA, app.deviceId, 1, Long.MAX_VALUE).isEmpty()) }
    }

    @Test
    fun aDatabaseFailureRemainsAnErrorAndDoesNotRenderTheNoResultsState() {
        app.database.close()
        var opened = 0
        content {
            GlobalSearchHost(
                database = app.database, farmId = farmA,
                onOpen = { opened++ }, onBack = {},
            )
        }
        field("Tag, name, species or identifier", "ZZ-TARGET")
        click("Search farm")
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Local search failed:", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("farm-screen:FOS-SEARCH-004").assertDoesNotExist()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-031").assertDoesNotExist()
        compose.onNodeWithText("No matching animal records.").assertDoesNotExist()
        compose.onNodeWithText("0 local result(s)", substring = true).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, opened) }
        compose.waitForIdle()
        compose.onNodeWithText("Local search failed:", substring = true).assertExists()
    }

    @Test
    fun exactIdentifiersPrecedePartialMatchesAndDuplicateIdentifiersDoNotDuplicateAnimals() {
        io {
            app.database.withTransaction {
                repeat(55) { index ->
                    val partialId = "$farmA-partial-$index"
                    animal(partialId, farmA, "ID-$index", "RFID-EXACT ZZ-TARGET partial $index")
                    app.database.lifecycle().insertIdentifier(
                        AnimalIdentifierEntity("$farmA-partial-rfid-$index", farmA, partialId, "rfid", "RFID-EXACT-$index", true, 100L + index),
                    )
                }
                app.database.lifecycle().insertIdentifier(AnimalIdentifierEntity("$farmA-rfid", farmA, targetId, "rfid", "RFID-EXACT", true, 10))
                app.database.lifecycle().insertIdentifier(AnimalIdentifierEntity("$farmA-eid", farmA, targetId, "eid", "RFID-EXACT", true, 11))
                app.database.lifecycle().insertIdentifier(AnimalIdentifierEntity("$farmB-rfid", farmB, foreignId, "rfid", "RFID-EXACT", true, 12))
                app.database.lifecycle().insertIdentifier(AnimalIdentifierEntity("$farmA-retired", farmA, firstId, "rfid", "RETIRED-ONLY", false, 13))
            }
            // Both Room queries must prioritise case-insensitive exact matches before their LIMIT;
            // in-memory ranking cannot recover a subject already discarded from either window.
            val tagWindow = app.database.animals().searchAll(farmA, "zz-target", GLOBAL_SEARCH_LIMIT + 1)
            assertEquals(GLOBAL_SEARCH_LIMIT + 1, tagWindow.size)
            assertEquals(setOf(targetId, cowId), tagWindow.take(2).map { it.id }.toSet())
            val tagResults = searchLocalFarmAnimals(app.database, farmA, "zz-target")
            assertEquals(GLOBAL_SEARCH_LIMIT, tagResults.size)
            assertEquals(setOf(targetId, cowId), tagResults.take(2).map { it.animalId }.toSet())
            val identifierWindow = app.database.lifecycle().searchActiveIdentifiers(farmA, "rfid-exact", GLOBAL_SEARCH_LIMIT + 1)
            assertEquals(GLOBAL_SEARCH_LIMIT + 1, identifierWindow.size)
            assertTrue(identifierWindow.take(2).all { it.animalId == targetId && it.value.equals("rfid-exact", ignoreCase = true) })
            val results = searchLocalFarmAnimals(app.database, farmA, "rfid-exact")
            assertEquals(GLOBAL_SEARCH_LIMIT, results.size)
            assertEquals(targetId, results.first().animalId)
            assertTrue(results.first().source.startsWith("identifier:"))
            assertEquals(results.size, results.map { it.animalId }.toSet().size)
            assertFalse(results.any { it.animalId == foreignId })
            assertTrue(results.all { app.database.animals().get(farmA, it.animalId) != null })
            assertTrue(searchLocalFarmAnimals(app.database, farmA, "RETIRED-ONLY").isEmpty())
        }
    }

    private suspend fun animal(id: String, farmId: String, tag: String, name: String, species: String = "goat", status: String = "active") {
        app.database.animals().insert(
            AnimalEntity(
                id = id, farmId = farmId, tag = tag, name = name, speciesCode = species,
                sex = "FEMALE", status = status, dateOfBirthEpochDay = null, updatedAtEpochMillis = 1,
            ),
        )
    }

    @Composable
    private fun session(membership: FarmMembership) {
        FarmSessionContent(
            app = app, membership = membership, farmName = if (membership.farmId == farmA) "Search farm A" else "Search farm B",
            memberships = listOf(FarmMembership(farmA, "owner"), FarmMembership(farmB, "owner")),
            farmNames = mapOf(farmA to "Search farm A", farmB to "Search farm B"),
            onSwitchFarm = {}, onRequireReauth = {}, onRequireFarmReselection = { _, _ -> },
            onSignOut = {}, actorId = if (membership.farmId == farmA) "owner-a" else "owner-b",
        )
    }

    private fun content(block: @Composable () -> Unit) {
        compose.setContent {
            dispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT, content = block)
        }
    }

    private fun search(query: String) {
        awaitScreen("FOS-HOME-012-A")
        click("Search farm")
        awaitScreen("FOS-HOME-006")
        compose.onNodeWithTag("farm-screen:FOS-SEARCH-005").assertExists()
        field("Tag, name, species or identifier", query)
        click("Search farm")
        awaitScreen("FOS-HOME-007")
        compose.onNodeWithTag("farm-screen:FOS-SEARCH-002").assertExists()
    }

    private fun field(label: String, value: String) {
        compose.onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextInput(value)
    }

    private fun click(label: String) {
        val action = hasClickAction() and hasText(label)
        // A route tag can render while its real Room-backed content is still loading.
        compose.waitUntil(10_000) { compose.onAllNodes(action).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(action).performScrollTo().assertIsDisplayed().performClick()
    }

    private fun back() {
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun awaitScreen(id: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithTag("farm-screen:$id").fetchSemanticsNodes().isNotEmpty()
    }

    private fun awaitText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun <T> io(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }
}
