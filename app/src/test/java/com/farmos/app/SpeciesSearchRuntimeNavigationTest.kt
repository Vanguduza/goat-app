package com.farmos.app

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.AnimalEntity
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.network.FarmMembership
import com.farmos.data.herd.RoomHerdRepository
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.access.LocalRole
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

/** Actual Room subjects and rendered search/profile/action/return paths, beyond the 200-row herd window. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = FarmOsApplication::class, sdk = [36], qualifiers = "en-rUS")
class SpeciesSearchRuntimeNavigationTest {
    @get:Rule val compose = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<FarmOsApplication>()
    private val farmA = UUID.randomUUID().toString()
    private val farmB = UUID.randomUUID().toString()
    private val sheepId = "$farmA-sheep"
    private val firstSheepId = "$farmA-first-sheep"
    private val cattleId = "$farmA-cattle"
    private val firstCattleId = "$farmA-first-cattle"
    private val goatId = "$farmA-goat"
    private val doeId = "$farmA-doe"
    private val buckId = "$farmA-buck"
    private val firstRabbitId = "$farmA-first-rabbit"
    private val foreignSheepId = "$farmB-sheep"
    private val foreignCattleId = "$farmB-cattle"
    private val foreignDoeId = "$farmB-doe"
    private lateinit var dispatcher: OnBackPressedDispatcher

    @Before
    fun seedDistinctSubjectsAndCurrentLocalOwners() {
        assertFalse(app.backendConfigured)
        seedCommandAuthority(app.database, farmA, "owner-a", app.deviceId, LocalRole.OWNER)
        seedCommandAuthority(app.database, farmB, "owner-b", app.deviceId, LocalRole.OWNER)
        io {
            app.database.withTransaction {
                animal(firstSheepId, farmA, "sheep", "AA-FIRST-S", "First ewe")
                animal(sheepId, farmA, "sheep", "ZZ-TARGET", "Target ewe")
                animal(firstCattleId, farmA, "cattle", "AA-FIRST-C", "First cow")
                animal(cattleId, farmA, "cattle", "ZZ-TARGET", "Target cow")
                animal(goatId, farmA, "goat", "ZZ-TARGET", "Same tag goat")
                animal(firstRabbitId, farmA, "rabbit", "AA-FIRST-R", "First rabbit")
                animal(doeId, farmA, "rabbit", "ZZ-RABBIT-D", "Target doe")
                animal(buckId, farmA, "rabbit", "ZZ-RABBIT-B", "Target buck", sex = "MALE")
                animal(foreignSheepId, farmB, "sheep", "ZZ-TARGET", "Other farm ewe")
                animal(foreignCattleId, farmB, "cattle", "ZZ-TARGET", "Other farm cow")
                animal(foreignDoeId, farmB, "rabbit", "ZZ-RABBIT-D", "Other farm doe")
            }
        }
    }

    @After
    fun closeDatabase() = app.database.close()

    @Test
    fun sheepSearchBeyondTheHerdWindowKeepsTheWeightSubjectAndReturnsThroughItsProfile() {
        exerciseWeightJourney(AnimalProfileKind.SHEEP, sheepId, firstSheepId, foreignSheepId, "Target ewe", "12.345", 12_345L)
    }

    @Test
    fun cattleSearchBeyondTheHerdWindowKeepsTheWeightSubjectAndReturnsThroughItsProfile() {
        exerciseWeightJourney(AnimalProfileKind.CATTLE, cattleId, firstCattleId, foreignCattleId, "Target cow", "456.789", 456_789L)
    }

    @Test
    fun rabbitSearchOpensTheRecordedDoeAndBuckProfilesAndRetainsItsReturnQuery() {
        seedBeyondHerdWindow("rabbit", listOf(doeId, buckId))
        content { session() }
        search("ZZ-RABBIT")
        awaitText("Rabbit · ZZ-RABBIT-D · Target doe · active · local")
        compose.onNodeWithText("Rabbit · ZZ-RABBIT-D · Other farm doe · active · local").assertDoesNotExist()

        click("Rabbit · ZZ-RABBIT-D · Target doe · active · local")
        awaitScreen("FOS-RABBIT-003")
        awaitText("Name Target doe")
        compose.onNodeWithTag("farm-runtime-route:rabbit.doe_profile:FOS-RABBIT-003").assertExists()
        compose.onNodeWithText("Doe · active").assertExists()
        compose.onNodeWithText("Name Target buck").assertDoesNotExist()
        compose.onNodeWithText("Name First rabbit").assertDoesNotExist()
        compose.onNodeWithText("Name Other farm doe").assertDoesNotExist()
        back()
        awaitScreen("FOS-HOME-006")
        assertQuery("ZZ-RABBIT")

        click("Rabbit · ZZ-RABBIT-B · Target buck · active · local")
        awaitScreen("FOS-RABBIT-004")
        awaitText("Name Target buck")
        compose.onNodeWithTag("farm-runtime-route:rabbit.buck_profile:FOS-RABBIT-004").assertExists()
        compose.onNodeWithText("Buck · active").assertExists()
        compose.onNodeWithText("Name Target doe").assertDoesNotExist()
        click("Back")
        awaitScreen("FOS-HOME-006")
        assertQuery("ZZ-RABBIT")
        compose.onNodeWithText("Rabbit · ZZ-RABBIT-D · Target doe · active · local").assertExists()
        back()
        awaitScreen("FOS-HOME-012-A")
        assertNoLocalOperations()
    }

    @Test
    fun wrongSpeciesForeignFarmAndWrongRabbitSexNeverSubstituteTheFirstAnimal() {
        val profile = mutableStateOf(FarmDestination.AnimalProfile(AnimalProfileKind.SHEEP, cattleId))
        var returned = 0
        io {
            val sheep = RoomHerdRepository(app.database, farmA, "sheep")
            assertNull(sheep.get(cattleId))
            assertNull(sheep.get(foreignSheepId))
            assertNull(RoomHerdRepository(app.database, farmA, "rabbit").get(sheepId))
        }
        content { directProfile(farmA, profile.value) { returned++ } }
        awaitText("The selected sheep is not available on this farm.")
        compose.onNodeWithText("Record weight").assertDoesNotExist()
        compose.onNodeWithText("AA-FIRST-S · First ewe · female · active").assertDoesNotExist()
        compose.onNodeWithText("ZZ-TARGET · Target cow · female · active").assertDoesNotExist()

        compose.runOnIdle { profile.value = FarmDestination.AnimalProfile(AnimalProfileKind.SHEEP, foreignSheepId) }
        compose.waitForIdle()
        awaitText("The selected sheep is not available on this farm.")
        compose.onNodeWithText("ZZ-TARGET · Other farm ewe · female · active").assertDoesNotExist()

        compose.runOnIdle { profile.value = FarmDestination.AnimalProfile(AnimalProfileKind.RABBIT_DOE, buckId) }
        awaitScreen("FOS-RABBIT-003")
        awaitText("The selected rabbit does not match this profile.")
        compose.onNodeWithText("Name Target buck").assertDoesNotExist()
        compose.onNodeWithText("Name First rabbit").assertDoesNotExist()
        compose.onNodeWithText("Leaving the rabbitry").assertDoesNotExist()
        back()
        compose.runOnIdle { assertEquals(1, returned) }
        assertNoLocalOperations()
    }

    @Test
    fun replacingOneValidProfileAndThenItsFarmDiscardsThePreviousSubject() {
        val farm = mutableStateOf(farmA)
        val profile = mutableStateOf(FarmDestination.AnimalProfile(AnimalProfileKind.SHEEP, sheepId))
        content { directProfile(farm.value, profile.value) {} }
        awaitText("ZZ-TARGET · Target ewe · female · active")
        click("Record weight")
        awaitScreen("FOS-SHEEP-006")
        field("Weight", "99.999")

        // A new valid entry must replace both selected identity and an unfinished child form.
        compose.runOnIdle { profile.value = FarmDestination.AnimalProfile(AnimalProfileKind.SHEEP, firstSheepId) }
        awaitScreen("FOS-SHEEP-003")
        awaitText("AA-FIRST-S · First ewe · female · active")
        compose.onNodeWithTag("farm-screen:FOS-SHEEP-006").assertDoesNotExist()
        compose.onNodeWithText("ZZ-TARGET · Target ewe · female · active").assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasText("99.999")).assertDoesNotExist()

        compose.runOnIdle {
            farm.value = farmB
            profile.value = FarmDestination.AnimalProfile(AnimalProfileKind.SHEEP, foreignSheepId)
        }
        awaitText("ZZ-TARGET · Other farm ewe · female · active")
        compose.onNodeWithText("AA-FIRST-S · First ewe · female · active").assertDoesNotExist()
        compose.onNodeWithText("ZZ-TARGET · Target ewe · female · active").assertDoesNotExist()
        assertNoLocalOperations()
    }

    @Test
    fun unsupportedIndividualProfilesRemainVisibleWithoutAnUnrelatedDestination() {
        io {
            animal("$farmA-unknown-rabbit", farmA, "rabbit", "UNSUPPORTED-R", "Unknown sex rabbit", sex = "UNKNOWN")
            animal("$farmA-bird", farmA, "poultry", "UNSUPPORTED-P", "Individual hen")
        }
        var opened = 0
        content { GlobalSearchHost(app.database, farmA, onOpen = { opened++ }, onBack = {}) }
        field("Tag, name, species or identifier", "UNSUPPORTED")
        click("Search farm")
        val rabbit = "Rabbit · UNSUPPORTED-R · Unknown sex rabbit · active · local"
        val bird = "Poultry · UNSUPPORTED-P · Individual hen · active · local"
        awaitText(rabbit)
        awaitText(bird)
        compose.onNodeWithText("Rabbit profile unavailable: recorded sex has no Doe or Buck profile.").assertExists()
        compose.onNodeWithText("Individual bird profiles are not available here.").assertExists()
        compose.onNode(hasClickAction() and hasText(rabbit)).assertDoesNotExist()
        compose.onNode(hasClickAction() and hasText(bird)).assertDoesNotExist()
        compose.onNodeWithTag("farm-screen:FOS-RABBIT-003").assertDoesNotExist()
        compose.onNodeWithTag("farm-screen:FOS-RABBIT-004").assertDoesNotExist()
        compose.onNodeWithTag("farm-screen:FOS-POULTRY-001").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, opened) }
        assertNoLocalOperations()
    }

    private fun exerciseWeightJourney(
        kind: AnimalProfileKind, targetId: String, firstId: String, foreignId: String,
        name: String, weight: String, grams: Long,
    ) {
        val species = kind.speciesCode
        val label = if (kind == AnimalProfileKind.SHEEP) "Sheep" else "Cattle"
        val prefix = "FOS-${label.uppercase()}"
        seedBeyondHerdWindow(species, listOf(targetId))
        content { session() }
        search("ZZ-TARGET")
        awaitText("$label · ZZ-TARGET · $name · active · local")
        click("Filter results")
        click(label)
        click("$label · ZZ-TARGET · $name · active · local")
        awaitScreen("$prefix-003")
        awaitText("ZZ-TARGET · $name · female · active")
        compose.onNodeWithTag("farm-runtime-route:${kind.routeKey}:$prefix-003").assertExists()
        compose.onNodeWithText("Same tag goat", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Other farm", substring = true).assertDoesNotExist()
        compose.onNodeWithText("AA-FIRST", substring = true).assertDoesNotExist()

        click("Record weight")
        awaitScreen("$prefix-006")
        compose.onNodeWithText("ZZ-TARGET · $name · female · active").assertExists()
        field("Weight", weight)
        click("Record weight")
        compose.waitUntil(10_000) { io { app.database.measurements().history(farmA, targetId, "weight").size == 1 } }
        io {
            assertEquals(grams, app.database.measurements().history(farmA, targetId, "weight").single().valueLong)
            assertTrue(app.database.measurements().history(farmA, firstId, "weight").isEmpty())
            assertTrue(app.database.measurements().history(farmA, goatId, "weight").isEmpty())
            assertTrue(app.database.measurements().history(farmB, foreignId, "weight").isEmpty())
            val operation = app.database.replication().operationsInRange(farmA, app.deviceId, 1, Long.MAX_VALUE).single()
            assertEquals("$species.record_weight.v1", operation.operationType)
            assertEquals(targetId, operation.entityId)
            assertEquals(farmA, operation.farmId)
            assertEquals("owner-a", operation.actorId)
            assertEquals(app.deviceId, operation.deviceId)
        }
        click("Profile")
        awaitScreen("$prefix-003")
        click("Lifecycle status")
        awaitScreen(if (kind == AnimalProfileKind.SHEEP) "FOS-SHEEP-030" else "FOS-CATTLE-034")
        compose.onNodeWithText("ZZ-TARGET · $name · female · active").assertExists()
        click("Profile")
        awaitScreen("$prefix-003")
        click(if (kind == AnimalProfileKind.SHEEP) "Breeding & wool operations" else "Breeding, dairy & beef operations")
        awaitScreen(if (kind == AnimalProfileKind.SHEEP) "FOS-SHEEP-010" else "FOS-CATTLE-018")
        compose.onNodeWithText(targetId).assertExists()
        click("Profile")
        awaitScreen("$prefix-003")
        back()
        awaitScreen("FOS-HOME-006")
        assertQuery("ZZ-TARGET")
        compose.onNodeWithText("$label (active)").assertExists()
        compose.onNodeWithText("$label · ZZ-TARGET · $name · active · local").assertExists()
        compose.onNodeWithText("Goat · ZZ-TARGET · Same tag goat · active · local").assertDoesNotExist()
        back()
        awaitScreen("FOS-HOME-012-A")
        io { assertEquals(1, app.database.replication().operationsInRange(farmA, app.deviceId, 1, Long.MAX_VALUE).size) }
    }

    private fun seedBeyondHerdWindow(species: String, targets: List<String>) {
        io {
            app.database.withTransaction {
                repeat(201) { index ->
                    animal("$farmA-$species-prefix-$index", farmA, species, "AB-${index.toString().padStart(3, '0')}", "Listed $species $index")
                }
            }
            val listed = RoomHerdRepository(app.database, farmA, species).list()
            assertEquals(200, listed.size)
            assertTrue(targets.none { id -> listed.any { it.id == id } })
        }
    }

    private suspend fun animal(id: String, farmId: String, species: String, tag: String, name: String, sex: String = "FEMALE") {
        app.database.animals().insert(
            AnimalEntity(
                id = id, farmId = farmId, speciesCode = species, tag = tag, name = name, sex = sex,
                status = "active", dateOfBirthEpochDay = null, updatedAtEpochMillis = 1,
            ),
        )
    }

    @Composable
    private fun session() {
        FarmSessionContent(
            app = app, membership = FarmMembership(farmA, "owner"), farmName = "Species search farm",
            memberships = listOf(FarmMembership(farmA, "owner"), FarmMembership(farmB, "owner")),
            farmNames = mapOf(farmA to "Species search farm", farmB to "Other farm"),
            onSwitchFarm = {}, onRequireReauth = {}, onRequireFarmReselection = { _, _ -> },
            onSignOut = {}, actorId = "owner-a",
        )
    }

    @Composable
    private fun directProfile(farmId: String, profile: FarmDestination.AnimalProfile, onBack: () -> Unit) {
        val ops = remember(farmId) { RoomOpsRepository(app.database, farmId) }
        FarmSpeciesModuleHost(
            module = profile.kind.module, database = app.database, farmId = farmId, ops = ops,
            newContext = {
                LocalCommandContext(farmId, if (farmId == farmA) "owner-a" else "owner-b", app.deviceId, UUID.randomUUID().toString(), System.currentTimeMillis())
            },
            enqueueSync = {}, onBack = onBack, profile = profile,
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
        field("Tag, name, species or identifier", query)
        click("Search farm")
        awaitScreen("FOS-HOME-007")
    }

    private fun assertQuery(query: String) {
        compose.onNode(hasSetTextAction() and hasText("Tag, name, species or identifier") and hasText(query)).assertExists()
    }

    private fun field(label: String, value: String) {
        compose.onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextInput(value)
    }

    private fun click(label: String) {
        val action = hasClickAction() and hasText(label)
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

    private fun assertNoLocalOperations() {
        io {
            assertTrue(app.database.replication().operationsInRange(farmA, app.deviceId, 1, Long.MAX_VALUE).isEmpty())
            assertTrue(app.database.replication().operationsInRange(farmB, app.deviceId, 1, Long.MAX_VALUE).isEmpty())
        }
    }

    private fun <T> io(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }
}
