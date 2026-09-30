package com.farmos.app

import androidx.compose.runtime.CompositionLocalProvider
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
import com.farmos.feature.ops.LocalOpsAnimalSearch
import com.farmos.feature.ops.MateCoiAnalysis
import com.farmos.feature.ops.MateCoiView
import com.farmos.feature.ops.OpsAnimalSearch
import com.farmos.feature.ops.SheepOperationsActions
import com.farmos.feature.ops.SheepOperationsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Rendered COI analysis (FOS-GEN-006) from sheep operations, owner decision D-023. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class OpsCoiAnalysisTest {
    @get:Rule
    val compose = createComposeRule()

    private fun searchOf(vararg options: FarmSelectorOption) = FarmSelectorSearch { query, offset, limit ->
        val matches = options.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
        FarmSearchPage(matches.drop(offset).take(limit), hasMore = matches.size > offset + limit)
    }

    @Test
    fun choosingAnEweAndARamShowsTheLambsInbreeding() {
        val ewes = searchOf(FarmSelectorOption("ewe-1", "SH-1 · Dora"))
        val rams = searchOf(FarmSelectorOption("ram-1", "SH-9 · Brutus"))
        val analysis = MateCoiAnalysis { sire, dam ->
            check(sire == "ram-1" && dam == "ewe-1")
            MateCoiView(0.125, 2, listOf("SH-3 · Old Ram"), 0)
        }
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                CompositionLocalProvider(LocalOpsAnimalSearch provides OpsAnimalSearch(searchOf(), ewes, rams, mateCoi = analysis)) {
                    SheepOperationsScreen(selectedAnimalId = null, busy = false, error = null, actions = sheepActions(), onBack = {})
                }
            }
        }
        compose.onNode(hasClickAction() and hasText("Inbreeding check")).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-GEN-006").assertExists()
        compose.onNodeWithText("Choose a ewe and a ram.").assertExists()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-atom:FOS-ATOM-004:option:ewe-1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("farm-atom:FOS-ATOM-004:option:ewe-1").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-atom:FOS-ATOM-008:option:ram-1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("farm-atom:FOS-ATOM-008:option:ram-1").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("mate-coi").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Inbreeding of the lambs (COI) 12.50% · 2 complete generations recorded").assertExists()
        compose.onNodeWithText("Common ancestors: SH-3 · Old Ram").assertExists()
    }

    private fun sheepActions() = SheepOperationsActions(
        onJoining = { _, _ -> }, onScan = { _, _, _ -> }, onLambing = { _, _, _, _, _ -> }, onMarking = { _, _, _, _ -> },
        onWeaning = { _, _, _, _ -> }, onWool = { _, _, _, _ -> }, onShearing = { _, _, _, _, _ -> }, onMicron = { _, _, _, _ -> },
        onFamacha = { _, _, _ -> }, onDag = { _, _, _ -> }, onFootrot = { _, _, _ -> }, onFlystrike = { _, _, _ -> },
        onIdentifier = { _, _, _, _ -> }, onMovement = { _, _, _, _, _ -> }, onPedigree = { _, _, _ -> },
    )
}
