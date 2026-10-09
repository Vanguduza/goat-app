package com.farmos.app

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.domain.access.LocalAccount
import com.farmos.domain.access.LocalRole
import com.farmos.domain.access.Permission
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** FOS-GLOBAL-002 and the real Reports owner observe accepted Room changes during a local session. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class LocalFarmSessionAuthorityTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var fixture: LocalSessionAuthorityFixture
    private val mounted = mutableStateOf(true)
    private val started = AtomicInteger()
    private val closed = AtomicInteger()

    @Before
    fun setUp() {
        fixture = LocalSessionAuthorityFixture(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() {
        compose.runOnIdle { mounted.value = false }
        compose.waitForIdle()
        fixture.close()
    }

    @Test
    fun receivedRoleDowngradeRefreshesMembershipAndClearsTheNestedManagementRouteWithoutRestartingCarriers() {
        render()
        signIn(fixture.pin)
        openReport("FOS-REPORT-006")
        fixture.changeRoleRemotely(LocalRole.WORKER)

        awaitText("Current role: WORKER")
        awaitTag("farm-screen:FOS-REPORT-001")
        compose.onNodeWithTag("farm-screen:FOS-REPORT-006").assertDoesNotExist()
        compose.onNodeWithTag("report-open-export").assertDoesNotExist()
        compose.onNodeWithTag("report-open:FOS-REPORT-014").assertDoesNotExist()
        assertEquals(1, started.get())
        assertEquals(0, closed.get())
        assertLocalAnimalRetained()
    }

    @Test
    fun receivedCredentialResetEndsTheSessionAndOnlyTheNewPinCanOpenItAgain() {
        render()
        signIn(fixture.pin)
        fixture.resetCredentialRemotely()
        awaitSignIn()
        awaitText("Your sign-in credential has changed. Sign in again with your current PIN or password.")
        compose.onNodeWithTag("farm-screen:FOS-REPORT-001").assertDoesNotExist()
        assertEquals(1, closed.get())
        assertLocalAnimalRetained()

        type("Username", fixture.account.username)
        type("PIN", fixture.pin)
        click("Sign in")
        awaitText("The username or PIN is not correct.")
        assertEquals(1, started.get())
        compose.onNode(hasSetTextAction() and hasText("PIN")).performScrollTo().performTextClearance()
        type("PIN", fixture.nextPin)
        click("Sign in")
        awaitTag("farm-screen:FOS-REPORT-001")
        assertEquals(2, started.get())
        assertEquals(1, closed.get())
    }

    @Test
    fun receivedDeviceRevocationDisposesTheCarriersAndFarmContent() {
        render()
        signIn(fixture.pin)
        fixture.revokeDeviceRemotely()
        awaitSignIn()
        awaitText("This device is no longer active for this farm. Ask farm management to review its access.")
        compose.onNodeWithTag("farm-screen:FOS-REPORT-001").assertDoesNotExist()
        assertEquals(1, started.get())
        assertEquals(1, closed.get())
        assertLocalAnimalRetained()
    }

    @Test
    fun disablingTheCurrentAccountEndsTheSignedInComposition() {
        render()
        signIn(fixture.pin)
        fixture.disableRemotely()
        awaitSignIn()
        awaitText("Your farm account is disabled. Ask farm management to restore access, then sign in again.")
        compose.onNodeWithTag("farm-screen:FOS-REPORT-001").assertDoesNotExist()
        assertEquals(1, closed.get())
        assertLocalAnimalRetained()
    }

    @Test
    fun aSignInResultThatBecameDisabledBeforeAdmissionNeverStartsCarriersOrFarmContent() {
        val authenticated = fixture.signIn()
        fixture.disableRemotely()
        render(staleResult = authenticated)
        // This delivery control is outside the scrolling production sign-in form.
        compose.onNode(hasClickAction() and hasText("Deliver completed sign-in"))
            .assertIsDisplayed().assertIsEnabled().performClick()
        awaitText("Your farm account is disabled. Ask farm management to restore access, then sign in again.")
        compose.onNodeWithTag("farm-screen:FOS-REPORT-001").assertDoesNotExist()
        assertEquals(0, started.get())
        assertEquals(0, closed.get())
    }

    @Test
    fun unavailableCarrierKeysKeepLocalRecordsOpenAndCloseAnythingAlreadyStarted() {
        render(carrierFailure = true)
        signIn(fixture.pin)
        awaitText("Synchronisation could not start on this device. Your local farm records are still available. Review Storage and backup.")
        assertEquals(1, started.get())
        assertEquals(1, closed.get())
        openReport("FOS-REPORT-003")
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("SESSION-001 · Session goat · active", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        assertLocalAnimalRetained()
    }

    private fun render(staleResult: LocalAccount? = null, carrierFailure: Boolean = false) {
        compose.setContent {
            if (mounted.value) FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                // The stale-result case uses a real completed sign-in from before the accepted disable.
                val delivered = remember { mutableStateOf(false) }
                LocalFarmSessionHost(
                    fixture.database, fixture.directory, fixture.deviceId,
                    entry = { message, onSignedIn ->
                        if (staleResult != null && !delivered.value) {
                            Button(onClick = {
                                delivered.value = true
                                onSignedIn(staleResult, "Command test farm")
                            }) { Text("Deliver completed sign-in") }
                        } else {
                            LocalFarmEntry(fixture.directory, onSignedIn, initialMessage = message)
                        }
                    },
                    startCarriers = {
                        started.incrementAndGet()
                        startOwnedCarriers { own ->
                            own(Closeable { closed.incrementAndGet() })
                            if (carrierFailure) error("Farm key material is unavailable")
                        }
                    },
                ) { current, authority, _ ->
                    Column(Modifier.fillMaxSize()) {
                        Text("Current role: ${current.account.role.name}")
                        Box(Modifier.weight(1f)) {
                            ReportsModuleHost(
                                fixture.database, current.account.farmId,
                                canExport = rolePermits(current.account.membership().role, Permission.EXPORT_FARM_DATA),
                                onBack = {}, exportAuthority = authority,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun signIn(pin: String) {
        awaitSignIn()
        assertEquals(0, started.get())
        type("Username", fixture.account.username)
        type("PIN", pin)
        click("Sign in")
        awaitTag("farm-screen:FOS-REPORT-001")
        assertEquals(1, started.get())
    }

    private fun assertLocalAnimalRetained() = runBlocking(Dispatchers.IO) {
        assertEquals(listOf("SESSION-001"), fixture.database.reports().herdRegister(fixture.farmId).map { it.tag })
    }

    private fun openReport(id: String) {
        // The hub exists before its Room summary arrives; wait for data before scrolling past it.
        awaitTag("report-metric:active-goat")
        compose.onNodeWithTag("report-metric:active-goat").assertTextEquals("1 animals")
        clickTag("report-open:$id")
        awaitTag("farm-screen:$id")
    }
    private fun awaitSignIn() = compose.waitUntil(10_000) {
        compose.onAllNodes(hasSetTextAction() and hasText("Username")).fetchSemanticsNodes().isNotEmpty()
    }
    private fun type(label: String, value: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasSetTextAction() and hasText(label) and isEnabled()).fetchSemanticsNodes().size == 1
        }
        compose.onNode(hasSetTextAction() and hasText(label)).performScrollTo().assertIsDisplayed().assertIsEnabled().performTextInput(value)
    }
    private fun click(label: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasClickAction() and hasText(label) and isEnabled()).fetchSemanticsNodes().size == 1
        }
        compose.onNode(hasClickAction() and hasText(label)).performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
    }
    private fun clickTag(tag: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag(tag) and hasClickAction() and isEnabled()).fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
    }
    private fun awaitTag(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    private fun awaitText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }
}
