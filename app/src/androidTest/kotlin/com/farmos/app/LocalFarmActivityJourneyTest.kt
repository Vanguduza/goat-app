package com.farmos.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.domain.access.AccountStatus
import com.farmos.domain.access.LocalRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Actual MainActivity journey on a fresh isolated emulator installation using synthetic local data.
 * Activity recreation is explicit; file-backed Room reopen / LAN replication have separate tests.
 * This is not process death, radio/network isolation, Google consent, a physical-device reboot or owner acceptance.
 * The created fixture is retained for inspection; this test never clears an existing farm or private vault.
 */
@RunWith(AndroidJUnit4::class)
class LocalFarmActivityJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<Context>() as FarmOsApplication
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun launchFromAnEmptyTestInstallation() {
        assertFalse("This journey requires the canonical local-only build.", app.backendConfigured)
        assertTrue("Server-era provider inputs must be empty for this local-only journey.",
            BuildConfig.SUPABASE_URL.isBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isBlank())
        runBlocking(Dispatchers.IO) {
            // Never clear an owner's existing farm to make an instrumentation test pass.
            assertTrue("Use a fresh, isolated emulator app installation.", app.database.localAccess().farms().isEmpty())
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun closeActivityAndRetainTheSyntheticFixture() {
        scenario?.close()
        // A rerun needs a fresh isolated installation. Do not turn a test into an owner-data reset.
    }

    @Test
    fun localOnboardingWeightAndRecreatedActivityUseTheSameFarmAndAnimal() {
        awaitScreen("FOS-GLOBAL-014")
        click("Continue")
        awaitScreen("FOS-GLOBAL-006")
        field("Farm name", "Artemis journey farm")
        field("Your name", "Journey owner")
        field("Owner username", "journey-owner")
        field("Owner PIN (6 to 12 digits)", "482913")
        field("Confirm PIN", "482913")
        click("Create farm")
        awaitTag("local-recovery-code")
        val farm = runBlocking(Dispatchers.IO) { app.database.localAccess().farms().single() }
        assertEquals("Artemis journey farm", farm.name)
        val owner = runBlocking(Dispatchers.IO) { app.database.localAccess().accounts(farm.farmId).single() }
        assertEquals("journey-owner", owner.username)
        assertEquals(LocalRole.OWNER.name, owner.role)
        assertEquals(AccountStatus.ACTIVE.name, owner.status)

        click("I have saved the recovery code")
        awaitScreen("FOS-GLOBAL-008")
        compose.onNodeWithTag("local-recovery-code").assertDoesNotExist()
        click("Open my farm")
        awaitScreen("FOS-ADMIN-013")
        compose.onNode(hasClickAction() and hasText("Connect Google Drive")).assertIsNotEnabled()
        click("Skip for now")
        awaitScreen("FOS-HOME-012-A")
        assertTrue(DriveSetupFlags(app).isDismissed(farm.farmId))

        click("Animals", scroll = false)
        awaitScreen("FOS-HOME-002")
        click("Goats")
        awaitScreen("FOS-GOAT-001")
        click("Register goat")
        awaitScreen("FOS-GOAT-004")
        field("Tag", "ART-001")
        field("Name (optional)", "Journey doe")
        click("Register goat")
        compose.waitUntil(60_000) {
            runBlocking(Dispatchers.IO) { app.database.animals().searchAll(farm.farmId, "ART-001", 10) }.size == 1
        }
        val goat = runBlocking(Dispatchers.IO) { app.database.animals().searchAll(farm.farmId, "ART-001", 10).single() }
        assertEquals("goat", goat.speciesCode)
        assertEquals("FEMALE", goat.sex)
        assertEquals("active", goat.status)
        assertEquals("Journey doe", goat.name)
        click("Back")
        awaitScreen("FOS-GOAT-001")
        awaitText("Journey doe")
        click("Growth")
        awaitScreen("FOS-GOAT-011")
        awaitText("Journey doe")
        field("Weight", "45.125")
        click("Record weight")
        compose.waitUntil(60_000) {
            runBlocking(Dispatchers.IO) { app.database.measurements().history(farm.farmId, goat.id, "weight") }.size == 1
        }
        val weights = runBlocking(Dispatchers.IO) { app.database.measurements().history(farm.farmId, goat.id, "weight") }
        assertEquals(45_125L, weights.single().valueLong)
        click("Back")
        awaitScreen("FOS-GOAT-003")
        compose.onNodeWithText("Journey doe").assertExists()
        compose.onNodeWithText("Saved locally · waiting to sync").assertExists()

        val businessBefore = runBlocking(Dispatchers.IO) {
            app.database.replication().operationsInRange(farm.farmId, app.deviceId, 1, Long.MAX_VALUE)
                .filter { it.operationType in setOf("goat.register.v1", "goat.record_weight.v1") }
        }
        assertEquals(2, businessBefore.size)
        assertEquals(setOf(owner.accountId), businessBefore.map { it.actorId }.toSet())
        assertEquals(setOf(farm.farmId), businessBefore.map { it.farmId }.toSet())
        assertEquals(setOf(app.deviceId), businessBefore.map { it.deviceId }.toSet())
        assertEquals(setOf(goat.id), businessBefore.map { it.entityId }.toSet())

        requireNotNull(scenario).recreate()
        awaitScreen("FOS-GLOBAL-002")
        compose.onNodeWithTag("local-recovery-code").assertDoesNotExist()
        compose.onNodeWithTag("farm-screen:FOS-GLOBAL-014").assertDoesNotExist()
        field("Username", "journey-owner")
        field("PIN", "482913")
        click("Sign in")
        awaitScreen("FOS-HOME-012-A")
        compose.onNodeWithTag("farm-screen:FOS-ADMIN-013").assertDoesNotExist()
        click("Search farm")
        awaitScreen("FOS-HOME-006")
        field("Tag, name, species or identifier", "ART-001")
        click("Search farm")
        awaitText("Goat · ART-001 · Journey doe · active · local")
        // Opening a result must preserve the selected animal, rather than losing it at a module home.
        click("Goat · ART-001 · Journey doe · active · local")
        awaitScreen("FOS-GOAT-003")
        awaitText("Journey doe")
        compose.onNodeWithText("ART-001 · female · Active").assertExists()
        // The existing profile formats kilograms to two decimal places; Room retains all grams.
        compose.onNode(hasText("45.13 kg latest weight") or hasText("45,13 kg latest weight")).assertExists()

        val businessAfter = runBlocking(Dispatchers.IO) {
            app.database.replication().operationsInRange(farm.farmId, app.deviceId, 1, Long.MAX_VALUE)
                .filter { it.operationType in setOf("goat.register.v1", "goat.record_weight.v1") }
        }
        assertEquals(businessBefore, businessAfter)
        assertEquals(45_125L, runBlocking(Dispatchers.IO) {
            app.database.measurements().history(farm.farmId, goat.id, "weight").single().valueLong
        })
        activityBack()
        awaitScreen("FOS-HOME-006")
        compose.onNode(hasSetTextAction() and hasText("Tag, name, species or identifier") and hasText("ART-001")).assertExists()
        activityBack()
        awaitScreen("FOS-HOME-012-A")
    }

    private fun field(label: String, value: String) {
        compose.onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextInput(value)
    }

    private fun click(label: String, scroll: Boolean = true) {
        // Screen ownership can appear before its Room-backed action is ready.
        val action = hasClickAction() and hasText(label) and isEnabled()
        compose.waitUntil(60_000) { compose.onAllNodes(action).fetchSemanticsNodes().isNotEmpty() }
        val node = compose.onNode(action)
        if (scroll) node.performScrollTo()
        node.assertIsDisplayed().performClick()
    }

    private fun activityBack() {
        requireNotNull(scenario).onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun awaitScreen(screenId: String) = awaitTag("farm-screen:$screenId")
    private fun awaitTag(tag: String) = compose.waitUntil(60_000) {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    private fun awaitText(text: String) = compose.waitUntil(60_000) {
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }
}
