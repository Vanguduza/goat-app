package com.farmos.app

import androidx.compose.runtime.CompositionLocalProvider
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

    @Test
    fun comparingRamsForAnEweShowsEachRamsFactsAndLambsInbreedingWithoutRanking() {
        val ewes = searchOf(FarmSelectorOption("ewe-1", "SH-1 · Dora"))
        val rams = searchOf(FarmSelectorOption("ram-1", "SH-9 · Brutus"), FarmSelectorOption("ram-2", "SH-8 · Atlas"))
        val analysis = MateCoiAnalysis { sire, dam ->
            check(dam == "ewe-1")
            if (sire == "ram-1") MateCoiView(0.25, 3, listOf("SH-3 · Old Ram"), 0, sireDateOfBirthEpochDay = 19_000, sireLatestWeightGrams = 82_500)
            else MateCoiView(0.0, 1, emptyList(), 1)
        }
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                CompositionLocalProvider(LocalOpsAnimalSearch provides OpsAnimalSearch(searchOf(), ewes, rams, mateCoi = analysis)) {
                    SheepOperationsScreen(selectedAnimalId = null, busy = false, error = null, actions = sheepActions(), onBack = {})
                }
            }
        }
        compose.onNode(hasClickAction() and hasText("Compare rams")).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-GEN-007").assertExists()
        compose.onNodeWithText("Choose a ewe to compare rams for.").assertExists()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-atom:FOS-ATOM-004:option:ewe-1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("farm-atom:FOS-ATOM-004:option:ewe-1").performScrollTo().performClick()
        compose.onNodeWithText("Add up to 4 rams to compare.").assertExists()
        listOf("ram-1", "ram-2").forEach { ram ->
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-atom:FOS-ATOM-008:option:$ram").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("farm-atom:FOS-ATOM-008:option:$ram").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("mate-candidate-coi:$ram").fetchSemanticsNodes().isNotEmpty() }
        }
        compose.onNodeWithText("Inbreeding of the lambs (COI) 25.00% · 3 complete generations recorded").assertExists()
        compose.onNodeWithText("Born: 2022-01-08").assertExists()
        compose.onNodeWithText("Latest weight: 82.5 kg").assertExists()
        compose.onNodeWithText("Born: Not recorded").assertExists()
        compose.onNodeWithText("Latest weight: Not recorded").assertExists()
        compose.onNodeWithText("1 animal(s) have conflicting parentage recorded and were left out.").assertExists()
        compose.onNodeWithTag("mate-candidate-remove:ram-2").performScrollTo().performClick()
        compose.onAllNodesWithTag("mate-candidate-coi:ram-2").assertCountEquals(0)
        compose.onNodeWithTag("mate-candidate-coi:ram-1").assertExists()
    }

    private fun sheepActions() = SheepOperationsActions(
        onJoining = { _, _ -> }, onScan = { _, _, _ -> }, onLambing = { _, _, _, _, _ -> }, onMarking = { _, _, _, _ -> },
        onWeaning = { _, _, _, _ -> }, onWool = { _, _, _, _ -> }, onShearing = { _, _, _, _, _ -> }, onMicron = { _, _, _, _ -> },
        onFamacha = { _, _, _ -> }, onDag = { _, _, _ -> }, onFootrot = { _, _, _ -> }, onFlystrike = { _, _, _ -> },
        onIdentifier = { _, _, _, _ -> }, onMovement = { _, _, _, _, _ -> }, onPedigree = { _, _, _ -> },
    )
}
