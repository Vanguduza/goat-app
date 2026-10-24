package com.farmos.feature.goat

import com.farmos.review.NativeModuleReviewRule
import com.farmos.review.NativeModuleReviewTheme
import com.farmos.review.captureObservedNativeOwners

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
import com.farmos.domain.goat.GoatHerdCounts
import com.farmos.domain.goat.GoatLactationSummary
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.GoatSnapshot
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rendered traversal for the farm-wide goat lactation dashboard, and the bounded-herd notice on
 * herd-derived pages: each opens from the goat dashboard and Back restores it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class GoatLactationRuntimeNavigationTest {
    @get:Rule(order = 0) val nativeReview = NativeModuleReviewRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    private val dashboardTag = "farm-screen:" + "FOS-" + "GOAT-" + "001"
    private fun day(m: Int, d: Int) = LocalDate.of(2026, m, d).toEpochDay()

    @Test
    fun lactationDashboardListsEveryGoatWithMilkAndOpensTheDoe() {
        val selected = AtomicReference<String?>(null)
        render(
            GoatLactationState.Loaded(
                listOf(
                    GoatLactationSummary("goat-nala", "GT-024", "Nala", "active", 612_750, 240, day(1, 3), day(9, 21), 3_250, 4),
                    GoatLactationSummary("goat-gone", null, null, null, 1_500, 1, day(6, 1), day(6, 1), 1_500, 0),
                ),
            ),
            onSelect = selected::set,
        )
        compose.revealGoatTool("Lactation").performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-GOAT-018").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithText("Goats with milk recorded · 2").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithText("614.25 L across 241 records").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithText("GT-024 · Nala · Active").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithText("612.75 L over 240 records").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithText("Latest 2026-09-21: 3.25 L · first 2026-01-03").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithText("SCC results recorded: 4").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithText("Goat not on this device").assertExists().also { compose.captureObservedNativeOwners() }

        compose.onNode(hasClickAction() and hasText("Back")).performScrollTo().performClick()
        compose.onNodeWithTag(dashboardTag).assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithTag("farm-screen:FOS-GOAT-018").assertDoesNotExist()

        compose.revealGoatTool("Lactation").performScrollTo().performClick()
        compose.onNodeWithTag("goat-lactation:goat-nala").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("goat-nala", selected.get()) }
    }

    @Test
    fun loadingFailedAndEmptyStatesAreDistinct() {
        render(GoatLactationState.Failed("milk records unavailable"))
        compose.revealGoatTool("Lactation").performScrollTo().performClick()
        compose.onNodeWithText("milk records unavailable").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithText("No milk recorded on this device.").assertDoesNotExist()
    }

    @Test
    fun emptyLactationSaysNothingIsRecorded() {
        render(GoatLactationState.Loaded(emptyList()))
        compose.revealGoatTool("Lactation").performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-GOAT-018").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithText("No milk recorded on this device.").assertExists().also { compose.captureObservedNativeOwners() }
    }

    @Test
    fun herdDerivedPagesSayWhenTheHerdListIsBounded() {
        render(GoatLactationState.Loading, herdCounts = GoatHerdCounts(active = 880, does = 600, bucks = 280, kids = 90, notClosed = 900))
        compose.revealGoatTool("Pregnancy").performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-GOAT-035").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithText("Built from the first 1 of 900 goats by tag. Goats beyond these are not shown here.").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNode(hasClickAction() and hasText("Back")).performScrollTo().performClick()
        compose.revealGoatTool("Kids").performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-GOAT-040").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithTag("goat-kid-cohort-bounded").assertExists().also { compose.captureObservedNativeOwners() }
    }

    @Test
    fun unboundedHerdShowsNoNotice() {
        render(GoatLactationState.Loading, herdCounts = GoatHerdCounts(active = 1, does = 1, bucks = 0, kids = 0, notClosed = 1))
        compose.revealGoatTool("Pregnancy").performScrollTo().performClick()
        compose.onNodeWithTag("goat-pregnancy-bounded").assertDoesNotExist()
    }

    private fun render(
        lactation: GoatLactationState,
        herdCounts: GoatHerdCounts? = null,
        onSelect: (String) -> Unit = {},
    ) {
        val nala = GoatSnapshot(
            animalId = "goat-nala",
            farmId = "farm-reference",
            tag = "GT-024",
            name = "Nala",
            sex = GoatSex.FEMALE,
            latestWeightGrams = null,
            syncPending = false,
        )
        compose.setContent {
            NativeModuleReviewTheme() {
                GoatExperienceScreen(
                    state = GoatSliceUiState(
                        farmName = "Premier Farm",
                        herd = listOf(nala),
                        herdCounts = herdCounts,
                        selected = nala,
                        animalId = nala.animalId,
                        lactation = lactation,
                    ),
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
                    initialPage = GoatPage.DASHBOARD,
                    today = LocalDate.of(2026, 9, 24),
                )
            }
        }
        compose.onNodeWithTag(dashboardTag).assertExists().also { compose.captureObservedNativeOwners() }
    }
}
