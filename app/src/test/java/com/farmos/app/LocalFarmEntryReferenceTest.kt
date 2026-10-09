package com.farmos.app

import android.content.Context
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.domain.access.LocalAccount
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Guards the native reference against silently returning to the superseded server login. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class LocalFarmEntryReferenceTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var fixture: LocalFarmEntryReferenceFixture

    @Before
    fun setUp() {
        fixture = LocalFarmEntryReferenceFixture(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    @Test
    fun referenceShowsLocalCredentialsAndSignsIntoItsFarmWithoutAServer() {
        val signedIn = AtomicReference<Pair<LocalAccount, String>?>(null)
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                fixture.Content { account, farmName -> signedIn.set(account to farmName) }
            }
        }

        compose.onNodeWithTag("farm-screen:FOS-GLOBAL-002").assertExists()
        // A single local farm is selected implicitly; its identity is verified on handoff below.
        compose.onNode(hasClickAction() and hasText("Recover the owner account")).assertExists()
        compose.onNode(hasSetTextAction() and hasText("Email address")).assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasText("Password")).assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasText("Username"))
            .performScrollTo().performTextInput(fixture.username)
        compose.onNode(hasSetTextAction() and hasText("PIN"))
            .performScrollTo().performTextInput(fixture.pin)
        compose.onNode(hasClickAction() and hasText("Sign in"))
            .performScrollTo().performClick()
        val signInError = hasText("The username or PIN is not correct.") or
            hasText("This account is disabled. Ask a manager to enable it.") or
            hasText("Too many attempts. Try again after", substring = true)
        // Compose can be idle while Room finishes its transaction. Wait for the actual handoff,
        // observing the callback across threads, and fail directly if local sign-in is rejected.
        compose.waitUntil(timeoutMillis = 5_000) {
            signedIn.get() != null || compose.onAllNodes(signInError).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(signInError).assertDoesNotExist()
        val result = requireNotNull(signedIn.get())

        assertEquals(fixture.owner.accountId, result.first.accountId)
        assertEquals(fixture.owner.farmId, result.first.farmId)
        assertEquals(fixture.farmName, result.second)
    }
}
