package com.farmos.app

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.ApplicationState
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Rendered Conflict Centre (FOS-SYNC-006), Conflict Detail (FOS-SYNC-007) and resolution sheet (FOS-ATOM-027). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class ConflictCentreScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val sale = ConflictItem("op-1", "animal.exit_record.v1", "Animal exit record", "Phone", "Rudo Chari", 1_790_000_000_000, ApplicationState.FAILED.name, "Only an animal still on the farm can leave it", 2)
    private val setAside = sale.copy(operationId = "op-0", state = ApplicationState.SET_ASIDE.name, reason = "Duplicate entry")

    @Test
    fun theCentreListsWaitingAndSetAsideChangesWithTheirProvenance() {
        val opened = mutableListOf<String>()
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                ConflictCentreScreen(ConflictReview(listOf(sale), 3, listOf(setAside)), busy = false, error = null, onOpen = { opened += it.operationId }, onBack = {})
            }
        }
        compose.onNodeWithTag("farm-screen:FOS-SYNC-006").assertExists()
        compose.onNodeWithText("Latest 1 of 3 waiting for review").assertExists()
        compose.onNodeWithText("Only an animal still on the farm can leave it").assertExists()
        compose.onNodeWithText("Set aside: Duplicate entry").assertExists()
        compose.onNodeWithTag("conflict:op-1").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("op-1"), opened) }
    }

    @Test
    fun managementSetsAChangeAsideOnlyWithAReasonAndSeesTheCorrection() {
        val setAsideWith = mutableListOf<String>()
        var retried = 0
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                ConflictDetailScreen(
                    ConflictDetail(sale, listOf("animalId" to "g1", "buyer" to "Moyo Butchery"), correctionFor(sale.operationType)),
                    canResolve = true, busy = false, error = null, onRetry = { retried++ }, onSetAside = { setAsideWith += it }, onBack = {},
                )
            }
        }
        compose.onNodeWithTag("farm-screen:FOS-SYNC-007").assertExists()
        compose.onNodeWithTag("conflict-provenance").assertExists()
        compose.onNodeWithText("buyer: Moyo Butchery").assertExists()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-027").assertExists()
        compose.onNode(hasText("also records its reversal", substring = true)).assertExists()
        compose.onNodeWithTag("conflict-set-aside").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("conflict-reason").performScrollTo().performTextInput("The goat died before the sale")
        compose.onNodeWithTag("conflict-set-aside").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("conflict-retry").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf("The goat died before the sale"), setAsideWith)
            assertEquals(1, retried)
        }
    }

    @Test
    fun withoutConflictPermissionTheDetailExplainsWhoResolves() {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                ConflictDetailScreen(ConflictDetail(sale, emptyList(), null), canResolve = false, busy = false, error = null, onRetry = {}, onSetAside = {}, onBack = {})
            }
        }
        compose.onNodeWithText("Conflicts are resolved by farm management").assertExists()
        assertTrue(compose.onAllNodes(hasText("Set aside")).fetchSemanticsNodes().isEmpty())
    }
}
