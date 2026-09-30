package com.farmos.app

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.domain.ops.AnimalExitKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Rendered sheep and cattle lifecycle status (FOS-SHEEP-030 / FOS-CATTLE-034), owner decision D-022 (R6). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class SpeciesExitScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val ewe = SpeciesAnimalRow("ewe-1", "SH-7 · Dora · female · active", active = true)

    @Test
    fun aSheepSaleNamesTheBuyerAndIsRecordedAfterConfirmationWithoutPostingMoney() {
        val recorded = mutableListOf<SpeciesExitDraft>()
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                SpeciesExitScreen("FOS-SHEEP-030", ewe, standing = null, currency = "USD", busy = false, error = null, onRecord = { recorded += it }, onReverse = { _, _ -> }, onBack = {})
            }
        }
        compose.onNodeWithTag("farm-screen:FOS-SHEEP-030").assertExists()
        compose.onNodeWithTag("species-exit-kind:SALE").performScrollTo().performClick()
        compose.onNodeWithText("Recording the sale here does not add income; record the sale money in Finance.").assertExists()
        compose.onNodeWithTag("species-exit-continue").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("species-exit-buyer").performScrollTo().performTextInput("Moyo Butchery")
        compose.onNodeWithTag("species-exit-continue").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(recorded.isEmpty()) }
        compose.onNodeWithTag("species-exit-confirm").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(AnimalExitKind.SALE, recorded.single().kind)
            assertEquals("Moyo Butchery", recorded.single().buyer)
        }
    }

    @Test
    fun aCattleDeathNeedsACause() {
        val recorded = mutableListOf<SpeciesExitDraft>()
        val cow = SpeciesAnimalRow("cow-1", "CT-3 · female · active", active = true)
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                SpeciesExitScreen("FOS-CATTLE-034", cow, standing = null, currency = null, busy = false, error = null, onRecord = { recorded += it }, onReverse = { _, _ -> }, onBack = {})
            }
        }
        compose.onNodeWithTag("farm-screen:FOS-CATTLE-034").assertExists()
        compose.onNodeWithTag("species-exit-kind:DEATH").performScrollTo().performClick()
        compose.onNodeWithTag("species-exit-continue").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("species-exit-cause:ILLNESS").performScrollTo().performClick()
        compose.onNodeWithTag("species-exit-continue").performScrollTo().performClick()
        compose.onNodeWithTag("species-exit-confirm").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(AnimalExitKind.DEATH, recorded.single().kind)
            assertEquals("ILLNESS", recorded.single().deathCause)
        }
    }

    @Test
    fun anAnimalThatHasLeftShowsItsExitAndReversesOnlyWithAReason() {
        val reversed = mutableListOf<Pair<String, String>>()
        val culled = ewe.copy(label = "SH-7 · Dora · female · culled", active = false)
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                SpeciesExitScreen("FOS-SHEEP-030", culled, SpeciesStandingExit("exit-9", "Culled on 2026-09-20: Chronic lameness"), "USD", busy = false, error = null, onRecord = {}, onReverse = { id, reason -> reversed += id to reason }, onBack = {})
            }
        }
        compose.onNodeWithTag("species-standing-exit").assertExists()
        assertTrue(compose.onAllNodes(hasText("How is it leaving?")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("species-exit-reverse").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("species-exit-reverse-reason").performScrollTo().performTextInput("Recorded against the wrong ewe")
        compose.onNodeWithTag("species-exit-reverse").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("exit-9" to "Recorded against the wrong ewe"), reversed) }
    }
}
