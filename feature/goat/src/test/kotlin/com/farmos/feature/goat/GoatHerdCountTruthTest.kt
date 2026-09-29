package com.farmos.feature.goat

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.domain.goat.GoatHerdCounts
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.GoatSnapshot
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The goat dashboard's herd metrics come from exhaustive counts, never from the bounded herd list,
 * and the herd list says when it shows only part of the herd.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class GoatHerdCountTruthTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 9, 24)
    private val listed = listOf(
        GoatSnapshot(animalId = "g1", farmId = "farm", tag = "GT-001", name = null, sex = GoatSex.FEMALE, latestWeightGrams = null, syncPending = false),
        GoatSnapshot(animalId = "g2", farmId = "farm", tag = "GT-002", name = null, sex = GoatSex.MALE, latestWeightGrams = null, syncPending = false),
    )
    private val counts = GoatHerdCounts(active = 612, does = 410, bucks = 202, kids = 97, notClosed = 640)

    @Test
    fun dashboardMetricsUseExhaustiveCounts() {
        render(GoatPage.DASHBOARD, counts)
        compose.onNodeWithText("612").assertExists()
        compose.onNodeWithText("410").assertExists()
        compose.onNodeWithText("202").assertExists()
        compose.onNodeWithText("97").assertExists()
    }

    @Test
    fun herdListSaysWhenItShowsOnlyPartOfTheHerd() {
        render(GoatPage.HERD, counts)
        compose.onNodeWithText("Showing the first 2 of 640 goats by tag.").assertExists()
    }

    @Test
    fun completeHerdListHasNoBoundNotice() {
        render(GoatPage.HERD, counts.copy(notClosed = 2))
        compose.onNodeWithTag("goat-herd-bounded").assertDoesNotExist()
    }

    private fun render(page: GoatPage, herdCounts: GoatHerdCounts) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                GoatExperienceScreen(
                    state = GoatSliceUiState(farmName = "Premier Farm", herd = listed, herdCounts = herdCounts),
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
                    initialPage = page,
                    today = today,
                )
            }
        }
    }
}
