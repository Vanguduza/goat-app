package com.farmos.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.core.design.FarmSearchPage
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.design.FarmSelectorSearch
import com.farmos.feature.ops.CattleOperationsActions
import com.farmos.feature.ops.CattleOperationsScreen
import com.farmos.feature.ops.LocalOpsAnimalSearch
import com.farmos.feature.ops.OpsAnimalSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Owner decision D-004: cattle operations choose a cow from the whole farm by search, never by typed id. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class OpsAnimalPickerTest {
    @get:Rule
    val compose = createComposeRule()

    private fun searchOf(vararg options: FarmSelectorOption) = FarmSelectorSearch { query, offset, limit ->
        val matches = options.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
        FarmSearchPage(matches.drop(offset).take(limit), hasMore = matches.size > offset + limit)
    }

    private val cows = arrayOf(FarmSelectorOption("cow-1", "CT-1 · Daisy"), FarmSelectorOption("cow-2", "CT-2 · Bella"))

    private fun render(selectedId: String?, onService: (String, String, String) -> Unit) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                CompositionLocalProvider(LocalOpsAnimalSearch provides OpsAnimalSearch(searchOf(*cows), searchOf(*cows), searchOf(), selectedId, "CT-1 · Daisy")) {
                    CattleOperationsScreen(selectedAnimalId = selectedId, busy = false, error = null, actions = actions(onService), onBack = {})
                }
            }
        }
        compose.onNode(hasClickAction() and hasText("Service")).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-CATTLE-010").assertExists()
    }

    @Test
    fun aCowIsChosenBySearchAndTheServiceRecordsHerId() {
        val recorded = mutableListOf<String>()
        render(selectedId = null) { id, _, _ -> recorded += id }
        assertTrue("No typed id field remains", compose.onAllNodes(hasText("Cow id")).fetchSemanticsNodes().isEmpty())
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-atom:FOS-ATOM-004:option:cow-2").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("farm-atom:FOS-ATOM-004:query").performScrollTo().performTextInput("Bella")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-atom:FOS-ATOM-004:option:cow-1").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("farm-atom:FOS-ATOM-004:option:cow-2").performScrollTo().performClick()
        compose.onNode(hasClickAction() and hasText("Record service")).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("cow-2"), recorded) }
    }

    @Test
    fun theHerdScreensChosenAnimalStartsSelected() {
        val recorded = mutableListOf<String>()
        render(selectedId = "cow-1") { id, _, _ -> recorded += id }
        compose.onNode(hasClickAction() and hasText("Record service")).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("cow-1"), recorded) }
    }

    private fun actions(onService: (String, String, String) -> Unit) = CattleOperationsActions(
        onService = onService,
        onPd = { _, _, _ -> }, onCalving = { _, _, _, _, _ -> }, onBcs = { _, _, _, _ -> }, onMilk = { _, _, _ -> },
        onLocomotion = { _, _, _ -> }, onScc = { _, _, _, _ -> }, onDryOff = { _, _, _ -> }, onWeaning = { _, _, _ -> },
        onIdentifier = { _, _, _, _ -> }, onMovement = { _, _, _, _, _ -> }, onPedigree = { _, _, _ -> },
        onPlaceLot = { _, _, _ -> }, onDaysOnFeed = { _, _, _ -> }, onCloseLot = { _, _, _, _, _ -> },
    )
}
