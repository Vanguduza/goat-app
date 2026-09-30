package com.farmos.app

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.domain.access.AccessAction
import com.farmos.domain.access.Credential
import com.farmos.domain.access.CredentialHasher
import com.farmos.domain.access.CredentialKind
import com.farmos.domain.access.LocalAccount
import com.farmos.domain.access.LocalRole
import com.farmos.domain.access.SignInResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The server-free entrance on an in-memory farm database: first-run farm setup (FOS-GLOBAL-006), local
 * sign-in (FOS-GLOBAL-002) and owner recovery with the one-time code (FOS-GLOBAL-004).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class LocalFarmEntryTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var database: FarmOsDatabase
    private lateinit var directory: LocalFarmDirectory
    private var signedIn: Pair<LocalAccount, String>? = null

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        directory = LocalFarmDirectory(database, CredentialHasher(iterations = 1_000))
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun render() {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                // Unconfined keeps the entry's database work on the test's main thread, so Compose idling
                // covers it and nothing still holds the database when the test closes it.
                LocalFarmEntry(directory, onSignedIn = { account, farmName -> signedIn = account to farmName }, io = Dispatchers.Unconfined)
            }
        }
    }

    private fun type(label: String, value: String) {
        // The loading step shares the sign-in screen id, so wait for the field itself.
        compose.waitUntil(10_000) { compose.onAllNodes(hasSetTextAction() and hasText(label)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextInput(value)
    }

    private fun click(label: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasClickAction() and hasText(label)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasClickAction() and hasText(label)).performScrollTo().performClick()
    }

    private fun waitForTag(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun shownRecoveryCode(): String =
        compose.onNodeWithTag("local-recovery-code").fetchSemanticsNode().config[SemanticsProperties.Text].first().text

    private fun existingFarm(): String = runBlocking {
        var code = ""
        directory.createFarm("Premier Farm") { farmId ->
            code = directory.access.setUpFarm(farmId, "tendai", "Tendai Moyo", Credential(CredentialKind.PIN, "482913")).recoveryCode
        }
        code
    }

    @Test
    fun firstRunCreatesTheFarmAndOwnerOfflineAndShowsTheRecoveryCodeOnce() {
        render()
        waitForTag("farm-screen:FOS-GLOBAL-006")
        type("Farm name", "Premier Farm")
        type("Your name", "Tendai Moyo")
        type("Owner username", "Tendai")
        type("Owner PIN (6 to 12 digits)", "482913")
        type("Confirm PIN", "482913")
        click("Create farm")

        waitForTag("local-recovery-code")
        val code = shownRecoveryCode()
        assertTrue(Regex("[0-9A-Z]{4}(-[0-9A-Z]{4}){4}").matches(code))
        val farm = database.localAccess().farms().single()
        assertEquals("Premier Farm", farm.name)
        val owner = database.localAccess().accounts(farm.farmId).single()
        assertEquals(LocalRole.OWNER.name, owner.role)
        assertFalse(owner.credentialHash.contains("482913"))
        assertFalse(database.localAccess().recoveryHash(farm.farmId)!!.contains(code))

        click("I have saved the recovery code")
        compose.waitUntil(10_000) { signedIn != null }
        assertEquals(LocalRole.OWNER, signedIn!!.first.role)
        assertEquals("Premier Farm", signedIn!!.second)
        assertEquals("owner", signedIn!!.first.membership().role)
    }

    @Test
    fun localSignInRejectsAWrongPinAndAcceptsTheRightOne() {
        existingFarm()
        render()
        waitForTag("farm-screen:FOS-GLOBAL-002")
        type("Username", "tendai")
        type("PIN", "111222")
        click("Sign in")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("The username or PIN is not correct.").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(null, signedIn)

        compose.onNode(hasSetTextAction() and hasText("PIN")).performScrollTo().performTextClearance()
        type("PIN", "482913")
        click("Sign in")
        compose.waitUntil(10_000) { signedIn != null }
        assertEquals("Tendai Moyo", signedIn!!.first.displayName)
        val farmId = signedIn!!.first.farmId
        val actions = database.localAccess().audit(farmId, 10).map { it.action }
        assertTrue(AccessAction.SIGN_IN_FAILED.name in actions && AccessAction.SIGNED_IN.name in actions)
    }

    @Test
    fun ownerRecoveryReplacesThePinAndRotatesTheCode() {
        val original = existingFarm()
        render()
        waitForTag("farm-screen:FOS-GLOBAL-002")
        click("Recover the owner account")
        waitForTag("farm-screen:FOS-GLOBAL-004")
        type("Owner username", "tendai")
        type("Recovery code", original.lowercase())
        type("New owner PIN (6 to 12 digits)", "591047")
        type("Confirm new PIN", "591047")
        click("Recover owner")

        waitForTag("local-recovery-code")
        compose.onNodeWithTag("farm-screen:FOS-GLOBAL-004").assertExists()
        val rotated = shownRecoveryCode()
        assertNotEquals(original, rotated)
        click("I have saved the recovery code")
        compose.waitUntil(10_000) { signedIn != null }

        val farmId = signedIn!!.first.farmId
        assertTrue(directory.access.signIn(farmId, "tendai", "591047") is SignInResult.SignedIn)
        assertEquals(SignInResult.InvalidCredentials, directory.access.signIn(farmId, "tendai", "482913"))
    }
}
