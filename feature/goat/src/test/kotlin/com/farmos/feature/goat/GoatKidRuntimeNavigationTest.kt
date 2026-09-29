package com.farmos.feature.goat

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
import com.farmos.domain.goat.GoatBirthRecord
import com.farmos.domain.goat.GoatRegisteredKid
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.GoatSnapshot
import com.farmos.domain.goat.KiddingSample
import com.farmos.domain.goat.WeightSample
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Rendered destination traversal for kidding detail, kid profile and kid cohort records. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class GoatKidRuntimeNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private fun tag(suffix: String) = "farm-screen:" + "FOS-" + "GOAT-" + suffix
    private fun day(m: Int, d: Int) = LocalDate.of(2026, m, d).toEpochDay()

    @Test
    fun kiddingDetailOpensFromTheDoeRecordAndReturnsToIt() {
        render(nala(), GoatPage.PROFILE)
        compose.onNode(hasClickAction() and hasText("Breeding records")).performScrollTo().performClick()
        compose.onNodeWithTag(tag("030")).assertExists()

        compose.onNode(hasClickAction() and hasText("2026-03-02")).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-GOAT-038").assertExists()
        compose.onNodeWithText("1 of 2 live").assertExists()
        compose.onNodeWithTag("goat-kidding-kid:goat-tumi").assertExists()
        compose.onNodeWithText("Doeling").assertExists()

        compose.onNode(hasClickAction() and hasText("Back")).performScrollTo().performClick()
        compose.onNodeWithTag(tag("030")).assertExists()
        compose.onNodeWithTag("farm-screen:FOS-GOAT-038").assertDoesNotExist()
    }

    @Test
    fun kidProfileShowsTheBirthRecordAndReturnsToTheProfile() {
        render(tumi(), GoatPage.PROFILE)
        compose.onNodeWithTag(tag("003")).assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText("Birth record")).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-GOAT-041").assertExists()
        compose.onNodeWithText("GT-024 · Nala").assertExists()
        compose.onNodeWithText("3 born · 2 live").assertExists()
        compose.onNodeWithText("3.20 kg").assertExists()
        compose.onNodeWithTag("goat-littermate:goat-sipho").assertExists()
        compose.onNode(hasClickAction() and hasText("Back")).performScrollTo().performClick()
        compose.onNodeWithTag(tag("003")).assertExists()
    }

    @Test
    fun goatsWithoutABirthRecordOfferNoBirthRecordLink() {
        render(nala(), GoatPage.PROFILE)
        compose.onNode(hasClickAction() and hasText("Birth record")).assertDoesNotExist()
    }

    @Test
    fun kidCohortGroupsKidsByKiddingAndOpensTheKid() {
        val selected = AtomicReference<String?>(null)
        render(tumi(), GoatPage.DASHBOARD, herd = listOf(nala(), tumi()), onSelect = selected::set)
        compose.onNode(hasClickAction() and hasText("Kids")).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-GOAT-040").assertExists()
        compose.onNodeWithTag("goat-kid-cohort:k1").assertExists()
        compose.onNodeWithText("GT-024 · Nala · 2026-03-02").assertExists()

        compose.onNode(hasClickAction() and hasText("Back")).performScrollTo().performClick()
        compose.onNodeWithTag(tag("001")).assertExists()

        compose.onNode(hasClickAction() and hasText("Kids")).performScrollTo().performClick()
        compose.onNodeWithText("Tumi · GT-101").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("goat-tumi", selected.get()) }
        compose.onNodeWithTag("farm-screen:FOS-GOAT-041").assertExists()
    }

    private fun render(
        selected: GoatSnapshot,
        page: GoatPage,
        herd: List<GoatSnapshot> = listOf(selected),
        onSelect: (String) -> Unit = {},
    ) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                GoatExperienceScreen(
                    state = GoatSliceUiState(farmName = "Premier Farm", herd = herd, selected = selected, animalId = selected.animalId),
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
                        onSelectGoat = onSelect,
                        onSyncNow = {},
                        onSearch = {},
                    ),
                    onBackToFarm = {},
                    onSignOut = {},
                    initialPage = page,
                    today = LocalDate.of(2026, 9, 24),
                )
            }
        }
    }

    private fun nala() = GoatSnapshot(
        animalId = "goat-nala",
        farmId = "farm-reference",
        tag = "GT-024",
        name = "Nala",
        sex = GoatSex.FEMALE,
        latestWeightGrams = null,
        kiddingHistory = listOf(KiddingSample("k1", 3, 2, 1, day(3, 2))),
        kidsByKidding = mapOf("k1" to listOf(GoatRegisteredKid("goat-tumi", "GT-101 · Tumi", GoatSex.FEMALE))),
        syncPending = false,
    )

    private fun tumi() = GoatSnapshot(
        animalId = "goat-tumi",
        farmId = "farm-reference",
        tag = "GT-101",
        name = "Tumi",
        sex = GoatSex.FEMALE,
        latestWeightGrams = 3_200,
        weightHistory = listOf(WeightSample("w1", 3_200, day(3, 3) * 86_400_000L)),
        birthRecord = GoatBirthRecord(
            kiddingId = "k1",
            damId = "goat-nala",
            damLabel = "GT-024 · Nala",
            kiddingEpochDay = day(3, 2),
            bornCount = 3,
            liveCount = 2,
            littermates = listOf(GoatRegisteredKid("goat-sipho", "GT-102 · Sipho", GoatSex.MALE)),
        ),
        syncPending = false,
    )
}
