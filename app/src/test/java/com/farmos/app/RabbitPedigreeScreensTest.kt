package com.farmos.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.core.design.FarmSearchPage
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.design.FarmSelectorSearch
import com.farmos.feature.rabbit.RabbitCoiView
import com.farmos.feature.rabbit.RabbitParentView
import com.farmos.feature.rabbit.RabbitPedigreePorts
import com.farmos.feature.rabbit.RabbitProgrammeScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Rendered Rabbit pedigree (FOS-RABBIT-031) and Inbreeding check (FOS-GEN-006). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class RabbitPedigreeScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private fun searchOf(vararg options: FarmSelectorOption) = FarmSelectorSearch { query, offset, limit ->
        val matches = options.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
        FarmSearchPage(matches.drop(offset).take(limit), hasMore = matches.size > offset + limit)
    }

    private val luna = FarmSelectorOption("r1", "RB-1 · Luna")
    private val max = FarmSelectorOption("r2", "RB-2 · Max")
    private val bruno = FarmSelectorOption("r3", "RB-3 · Bruno")
    private val links = mutableListOf<Triple<String, String, String>>()
    private val ports = RabbitPedigreePorts(
        searchRabbits = searchOf(luna, max),
        searchDoes = searchOf(luna),
        searchBucks = searchOf(max, bruno),
        parents = { id -> if (links.any { it.first == id }) listOf(RabbitParentView("sire", "RB-2 · Max")) else emptyList() },
        link = { child, parent, relation -> links += Triple(child, parent, relation) },
        coi = { buck, _ -> if (buck == "r3") RabbitCoiView(0.0, 0, emptyList(), 0) else RabbitCoiView(0.25, 1, listOf("RB-9 · Old Buck"), 0, 19_500, 3_250) },
    )

    private fun render() {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                RabbitProgrammeScreen(
                    cages = emptyList(), waves = emptyList(), availableBoxes = 0, busy = false, error = null, does = emptyList(),
                    onRegisterDoe = { _, _, _ -> }, onCreateCage = {}, onCreateNestBox = { _, _ -> }, onCreateWave = { _, _, _ -> },
                    onPalpate = { _, _, _ -> }, onKindle = { _, _, _, _ -> }, onFoster = { _, _, _, _, _ -> }, onBack = {},
                    pedigree = ports,
                )
            }
        }
    }

    private fun waitForOption(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun aRabbitsSireIsLinkedFromTheWholeRabbitry() {
        render()
        compose.onNode(hasClickAction() and hasText("Open Rabbit pedigree")).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-RABBIT-031").assertExists()
        waitForOption("farm-atom:FOS-ATOM-004:option:r1")
        compose.onNodeWithTag("farm-atom:FOS-ATOM-004:option:r1").performScrollTo().performClick()
        compose.onNodeWithText("No parents recorded for RB-1 · Luna.").assertExists()
        waitForOption("farm-atom:FOS-ATOM-008:option:r2")
        compose.onNodeWithTag("farm-atom:FOS-ATOM-008:option:r2").performScrollTo().performClick()
        compose.onNodeWithTag("rabbit-link:sire").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("rabbit-parent:sire").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Sire: RB-2 · Max").assertExists()
        compose.runOnIdle { assertEquals(listOf(Triple("r1", "r2", "sire")), links) }
    }

    @Test
    fun theKitsInbreedingShowsForADoeAndBuck() {
        render()
        compose.onNode(hasClickAction() and hasText("Open Inbreeding check")).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-GEN-006").assertExists()
        compose.onNodeWithText("Choose a doe and a buck.").assertExists()
        waitForOption("farm-atom:FOS-ATOM-004:option:r1")
        compose.onNodeWithTag("farm-atom:FOS-ATOM-004:option:r1").performScrollTo().performClick()
        waitForOption("farm-atom:FOS-ATOM-008:option:r2")
        compose.onNodeWithTag("farm-atom:FOS-ATOM-008:option:r2").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("rabbit-coi").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Inbreeding of the kits (COI) 25.00% · 1 complete generation recorded").assertExists()
    }

    @Test
    fun bucksAreComparedSideBySideForADoeWithoutRanking() {
        render()
        compose.onNode(hasClickAction() and hasText("Open Compare bucks")).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-GEN-007").assertExists()
        compose.onNodeWithText("Choose a doe to compare bucks for.").assertExists()
        waitForOption("farm-atom:FOS-ATOM-004:option:r1")
        compose.onNodeWithTag("farm-atom:FOS-ATOM-004:option:r1").performScrollTo().performClick()
        listOf("r2", "r3").forEach { buck ->
            waitForOption("farm-atom:FOS-ATOM-008:option:$buck")
            compose.onNodeWithTag("farm-atom:FOS-ATOM-008:option:$buck").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("rabbit-candidate-coi:$buck").fetchSemanticsNodes().isNotEmpty() }
        }
        compose.onNodeWithText("Inbreeding of the kits (COI) 25.00% · 1 complete generation recorded").assertExists()
        compose.onNodeWithText("Latest weight: 3.25 kg").assertExists()
        compose.onNodeWithText("Born: 2023-05-23").assertExists()
        compose.onNodeWithText("Born: Not recorded").assertExists()
        compose.onNodeWithText("Inbreeding of the kits (COI) 0.00% · no complete generation recorded").assertExists()
        compose.onNodeWithTag("rabbit-candidate-remove:r2").performScrollTo().performClick()
        compose.onAllNodesWithTag("rabbit-candidate-coi:r2").assertCountEquals(0)
        compose.onNodeWithTag("rabbit-candidate-coi:r3").assertExists()
    }
}
