package com.farmos.feature.goat

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
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
import com.farmos.domain.goat.FamachaSample
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.GoatSnapshot
import com.farmos.domain.goat.GoatStatus
import com.farmos.domain.goat.KiddingSample
import com.farmos.domain.goat.MilkSample
import com.farmos.domain.goat.SccSample
import com.farmos.domain.goat.WeightSample
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rendered destination traversal for the read-only goat record pages: each page is opened from
 * the real goat profile action, renders its exact Screen ID with the selected goat's records,
 * and Back restores the profile.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class GoatHistoryRuntimeNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private val profileTag = "farm-screen:" + "FOS-" + "GOAT-" + "003"

    @Test
    fun profileRecordLinksTraverseToEachHistoryAndRestoreTheProfile() {
        render(doe())

        traverse("Timeline", "FOS-GOAT-009") {
            compose.onNodeWithText("2026-09-20 · Weight").assertExists()
            compose.onNodeWithText("3 born · 2 live · 1 dead").assertExists()
        }
        traverse("Growth history", "FOS-GOAT-013") {
            compose.onNodeWithText("2026-08-01 · +3.60 kg").assertExists()
            compose.onNodeWithText("2026-07-01 · first weight").assertExists()
        }
        traverse("Growth chart", "FOS-GOAT-014") {
            compose.onNodeWithTag("goat-growth-chart").assertExists()
            compose.onNodeWithText("48.10 kg lowest · 54.25 kg highest").assertExists()
        }
        traverse("Average daily gain", "FOS-GOAT-015") {
            // (54 250 g - 48 100 g) over 81 days, truncated by the domain rule.
            compose.onNodeWithText("75 g/day").assertExists()
            compose.onNodeWithText("81").assertExists()
        }
        traverse("FAMACHA history", "FOS-GOAT-024") {
            compose.onNodeWithText("Score 2").assertExists()
            compose.onNodeWithText("Score 3").assertExists()
        }
        traverse("Milk history", "FOS-GOAT-019") {
            compose.onNodeWithText("5.30 L").assertExists()
            compose.onNodeWithText("2.60 L").assertExists()
        }
        traverse("SCC history", "FOS-GOAT-021") {
            compose.onNodeWithText("350,000 cells/mL · DIM 45").assertExists()
        }
        assertNamedClickTargets()
    }

    @Test
    fun timelineFilterNarrowsToOneRecordKind() {
        render(doe())
        traverse("Timeline", "FOS-GOAT-009") {
            compose.onNode(hasClickAction() and hasText("Milk")).performScrollTo().performClick()
            compose.onNodeWithTag("goat-timeline-entry:m1").assertExists()
            compose.onNodeWithTag("goat-timeline-entry:w3").assertDoesNotExist()
            compose.onNode(hasClickAction() and hasText("All")).performScrollTo().performClick()
            compose.onNodeWithTag("goat-timeline-entry:w3").assertExists()
        }
    }

    @Test
    fun bucksDoNotOfferDoeOnlyRecordsAndEmptyHistoriesStayEmpty() {
        render(doe().copy(sex = GoatSex.MALE, weightHistory = emptyList(), famachaHistory = emptyList(), milkHistory = emptyList(), sccHistory = emptyList(), kiddingHistory = emptyList()))

        compose.onNode(hasClickAction() and hasText("Milk history")).assertDoesNotExist()
        compose.onNode(hasClickAction() and hasText("SCC history")).assertDoesNotExist()
        traverse("Growth chart", "FOS-GOAT-014") {
            compose.onNodeWithText("Record at least two weights to draw a growth chart.").assertExists()
            compose.onNodeWithTag("goat-growth-chart").assertDoesNotExist()
        }
        traverse("Average daily gain", "FOS-GOAT-015") {
            compose.onNodeWithText("Average daily gain needs two weights recorded on different days.").assertExists()
            compose.onNodeWithTag("goat-adg-value").assertDoesNotExist()
        }
        traverse("Timeline", "FOS-GOAT-009") {
            compose.onNodeWithText("No records for this goat yet.").assertExists()
        }
    }

    @Test
    fun recordsRemainReadableAfterALifecycleExit() {
        render(doe().copy(status = GoatStatus.SOLD))
        traverse("Growth history", "FOS-GOAT-013") {
            compose.onNodeWithText("54.25 kg").assertExists()
        }
    }

    private fun render(goat: GoatSnapshot) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                GoatExperienceScreen(
                    state = GoatSliceUiState(farmName = "Premier Farm", herd = listOf(goat), selected = goat, animalId = goat.animalId),
                    actions = GoatExperienceActions(
                        onRegister = { _, _, _, _ -> },
                        onRecordWeight = {},
                        onRecordKidding = { _, _, _, _ -> },
                        onRegisterKid = { _, _, _ -> },
                        onRecordFamacha = { _, _ -> },
                        onRecordMilk = { _, _ -> },
                        onRecordBcs = { _, _ -> },
                        onRecordScc = { _, _, _ -> },
                        onRecordHeat = {},
                        onRecordMating = { _, _, _ -> },
                        onRecordPregnancy = { _, _ -> },
                        onPlanLactation = {},
                        onSetStatus = {},
                        onSelectGoat = {},
                        onSyncNow = {},
                        onSearch = {},
                    ),
                    onBackToFarm = {},
                    onSignOut = {},
                    initialPage = GoatPage.PROFILE,
                    today = LocalDate.of(2026, 9, 24),
                )
            }
        }
        compose.onNodeWithTag(profileTag).assertIsDisplayed()
    }

    private fun traverse(label: String, screenId: String, assertions: () -> Unit) {
        compose.onNode(hasClickAction() and hasText(label)).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:$screenId").assertExists()
        compose.onNodeWithText("Nala · GT-024").assertExists()
        assertions()
        compose.onNode(hasClickAction() and hasText("Back")).performScrollTo().performClick()
        compose.onNodeWithTag(profileTag).assertExists()
        compose.onNodeWithTag("farm-screen:$screenId").assertDoesNotExist()
    }

    private fun assertNamedClickTargets() {
        compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
            val named = node.config.contains(SemanticsProperties.Text) || node.config.contains(SemanticsProperties.ContentDescription)
            assertTrue("Unnamed click target: ${node.config}", named)
        }
    }

    private fun doe(): GoatSnapshot {
        fun millis(day: LocalDate) = day.toEpochDay() * 86_400_000L
        return GoatSnapshot(
            animalId = "goat-nala",
            farmId = "farm-reference",
            tag = "GT-024",
            name = "Nala",
            sex = GoatSex.FEMALE,
            status = GoatStatus.ACTIVE,
            dateOfBirthEpochDay = LocalDate.of(2024, 4, 14).toEpochDay(),
            latestWeightGrams = 54_250,
            averageDailyGainGrams = 75,
            weightHistory = listOf(
                WeightSample("w1", 48_100, millis(LocalDate.of(2026, 7, 1))),
                WeightSample("w2", 51_700, millis(LocalDate.of(2026, 8, 1))),
                WeightSample("w3", 54_250, millis(LocalDate.of(2026, 9, 20))),
            ),
            kiddingHistory = listOf(KiddingSample("k1", 3, 2, 1, LocalDate.of(2026, 3, 2).toEpochDay())),
            famachaHistory = listOf(
                FamachaSample("f2", 2, LocalDate.of(2026, 9, 1).toEpochDay()),
                FamachaSample("f1", 3, LocalDate.of(2026, 6, 1).toEpochDay()),
            ),
            milkHistory = listOf(
                MilkSample("m1", 2_600, LocalDate.of(2026, 9, 10).toEpochDay()),
                MilkSample("m2", 2_700, LocalDate.of(2026, 9, 11).toEpochDay()),
            ),
            sccHistory = listOf(SccSample("s1", 350_000, 45, LocalDate.of(2026, 9, 12).toEpochDay())),
            syncPending = false,
        )
    }
}
