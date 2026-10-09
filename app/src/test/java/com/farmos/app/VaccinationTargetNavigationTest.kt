package com.farmos.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.core.design.FarmSearchPage
import com.farmos.core.design.FarmSelectionAtoms
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.design.FarmSelectorSearch
import com.farmos.feature.ops.HealthObservationScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class VaccinationTargetNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun switchingTargetModesAlwaysSubmitsOnlyTheActiveTarget() {
        val recorded = mutableListOf<Pair<String?, String?>>()
        val search = FarmSelectorSearch { _, _, _ ->
            FarmSearchPage(listOf(FarmSelectorOption("animal", "Goat One", "Goat · Active")), false)
        }
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                HealthObservationScreen(
                    rows = emptyList(), catalog = emptyList(), treatments = emptyList(),
                    busy = false, error = null, onRecord = { _, _, _, _ -> },
                    onCreateFormulary = { _, _, _, _ -> }, onRecordTreatment = { _, _, _ -> }, onBack = {},
                    speciesCodes = listOf("goat"), searchAnimals = search,
                    groupOptions = listOf(FarmSelectorOption("group", "Goat Group", "goat")),
                    formularyOptions = listOf(FarmSelectorOption("vaccine", "Approved vaccine", "goat · vaccine")),
                    onRecordVaccination = { _, animal, group, _, _, _, _ -> recorded += animal to group },
                )
            }
        }
        click("Vaccination schedule")
        click("Record vaccination")
        compose.onNodeWithTag("${FarmSelectionAtoms.ANIMAL_SELECTOR}:option:animal").performScrollTo().performClick()
        click("Group")
        compose.onNodeWithTag("health-group-selector:option:group").performScrollTo().performClick()
        compose.onNodeWithTag("health-formulary-selector:option:vaccine").performScrollTo().performClick()
        click("Record vaccination")
        compose.runOnIdle { assertEquals(listOf(null to "group"), recorded) }
        click("Individual animal")
        click("Record vaccination")
        compose.runOnIdle { assertEquals(listOf(null to "group", "animal" to null), recorded) }
    }

    private fun click(label: String) {
        compose.onNode(hasClickAction() and hasText(label)).performScrollTo().performClick()
    }
}
