package com.farmos.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.feature.ops.PoultryDayView
import com.farmos.feature.ops.PoultryExperienceScreen
import com.farmos.feature.ops.PoultryFlockView
import com.farmos.feature.ops.PoultryHatchView
import com.farmos.feature.ops.PoultryHouseView
import com.farmos.feature.ops.PoultryPlacementView
import com.farmos.feature.ops.PoultryRecords
import com.farmos.feature.ops.PoultryVaccinationView
import com.farmos.feature.ops.PoultryWalkView
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rendered destination traversal for the read-only poultry record pages: each opens from the
 * poultry dashboard action, renders its exact Screen ID with local records, and Back restores
 * the dashboard.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class PoultryRecordRuntimeNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private fun day(m: Int, d: Int) = LocalDate.of(2026, m, d).toEpochDay()

    @Test
    fun poultryRecordPagesTraverseFromTheDashboardAndRestoreIt() {
        render(records())
        traverse("Flock profiles", "FOS-POULTRY-005") {
            compose.onNodeWithTag("poultry-flock-losses").assertExists()
            compose.onNodeWithText("5").assertExists()
            compose.onNodeWithText("995").assertExists()
            compose.onNodeWithText("1850").assertExists()
            compose.onNodeWithText("22.5 kg").assertExists()
            compose.onNodeWithTag("poultry-flock-vaccination:v1").assertExists()
            compose.onNodeWithText("Vaccinations · 1").assertExists()
            compose.onNodeWithTag("poultry-option:layers-b").performScrollTo().performClick()
            compose.onNodeWithText("No vaccinations recorded for this flock.").assertExists()
        }
        traverse("House details", "FOS-POULTRY-007") {
            compose.onNodeWithTag("poultry-house-placement:p1").assertExists()
            compose.onNodeWithTag("poultry-house-walk:bw1").assertExists()
            compose.onNodeWithText("Placements · latest 1 of 530").assertExists()
        }
        traverse("Egg production", "FOS-POULTRY-010") {
            compose.onNodeWithText("layers-a · chicken · 1850 eggs").assertExists()
            compose.onNodeWithText("940 eggs").assertExists()
        }
        traverse("Incubation batches", "FOS-POULTRY-019") {
            compose.onNodeWithText("21 days").assertExists()
            compose.onNodeWithText("88 / 7 / 3").assertExists()
            compose.onNodeWithText("No hatch result recorded.").assertExists()
        }
        traverse("Biosecurity records", "FOS-POULTRY-015") {
            compose.onNodeWithTag("poultry-biosecurity-mixed").assertExists()
            compose.onNodeWithText("Mixed species recorded on 4 walk(s)").assertExists()
            compose.onNodeWithText("Walks · latest 1 of 620").assertExists()
            compose.onNodeWithText("Wild bird droppings near feeder").assertExists()
        }
    }

    @Test
    fun emptyRecordsSayNothingIsRecorded() {
        render(PoultryRecords())
        traverse("Flock profiles", "FOS-POULTRY-005") {
            compose.onNodeWithText("No flocks placed on this device.").assertExists()
        }
        traverse("Egg production", "FOS-POULTRY-010") {
            compose.onNodeWithText("No eggs recorded on this device.").assertExists()
        }
        traverse("Biosecurity records", "FOS-POULTRY-015") {
            compose.onNodeWithText("No biosecurity walks recorded on this device.").assertExists()
        }
    }

    private fun traverse(label: String, screenId: String, assertions: () -> Unit) {
        compose.onNode(hasClickAction() and hasText("Open $label")).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:$screenId").assertExists()
        assertions()
        compose.onNode(hasClickAction() and hasText("Farm home")).performScrollTo().performClick()
        compose.onNode(hasClickAction() and hasText("Open $label")).assertExists()
        compose.onNodeWithTag("farm-screen:$screenId").assertDoesNotExist()
    }

    private fun records(): PoultryRecords {
        val walk = PoultryWalkView("bw1", day(9, 18), "House H1", "Wild bird droppings near feeder", mixedSpecies = true)
        return PoultryRecords(
            flocks = listOf(
                PoultryFlockView(
                    groupId = "layers-a",
                    poultryKind = "chicken",
                    houseLabel = "H1",
                    placedHeads = 1_000,
                    firstPlacedEpochDay = day(8, 1),
                    days = listOf(
                        PoultryDayView(day(9, 21), 940, 2, 1, 12_000),
                        PoultryDayView(day(9, 20), 910, 1, 1, 10_500),
                    ),
                    vaccinations = listOf(PoultryVaccinationView("v1", day(8, 3), "Marek's vaccine")),
                    vaccinationCount = 1,
                ),
                PoultryFlockView("layers-b", "duck", "H2", 200, day(9, 1), emptyList(), emptyList()),
            ),
            houses = listOf(
                PoultryHouseView("h1", "H1", "layer_barn", "chicken", listOf(PoultryPlacementView("p1", "layers-a", "chicken", 1_000, day(8, 1))), listOf(walk), placementCount = 530, walkCount = 1),
            ),
            hatches = listOf(
                PoultryHatchView("b1", "chicken", day(9, 1), 100, 21, "candled", "House H1", 88, 7, 3, null, null, null),
            ),
            walks = listOf(walk),
            walkCount = 620,
            mixedSpeciesWalkCount = 4,
        )
    }

    private fun render(records: PoultryRecords) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                PoultryExperienceScreen(
                    enabledKinds = emptyList(),
                    houses = emptyList(),
                    placements = emptyList(),
                    flockDays = emptyList(),
                    hatches = emptyList(),
                    vaccinations = emptyList(),
                    busy = false,
                    error = null,
                    onEnableKind = {},
                    onCreateHouse = { _, _, _ -> },
                    onPlaceFlock = { _, _, _, _, _ -> },
                    onRecordFlockDay = { _, _, _, _, _, _ -> },
                    onSetEggs = { _, _, _, _, _ -> },
                    onCandle = { _, _, _, _, _ -> },
                    onRecordHatch = { _, _, _, _ -> },
                    onVaccinate = { _, _, _, _ -> },
                    onBiosecurity = { _, _, _, _, _ -> },
                    onBack = {},
                    records = records,
                )
            }
        }
    }
}
