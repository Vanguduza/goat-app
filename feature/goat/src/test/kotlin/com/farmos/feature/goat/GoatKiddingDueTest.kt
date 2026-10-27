package com.farmos.feature.goat

import com.farmos.review.NativeModuleReviewRule
import com.farmos.review.NativeModuleReviewTheme
import com.farmos.review.captureObservedNativeOwners

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.domain.goat.GoatKiddingDue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Kidding due (FOS-GOAT-036) groups every expected doe by how close her window is. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class GoatKiddingDueTest {
    @get:Rule(order = 0) val nativeReview = NativeModuleReviewRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    private val today = 20_500L

    private fun due(id: String, service: Long, confirmed: Boolean = true) =
        GoatKiddingDue(id, "T-$id", "Doe $id", service, confirmed, service + 145, service + 150, service + 155)

    @Test
    fun doesAreGroupedByWindowAndOpenTheirReproductionRecord() {
        var selected: String? = null
        val rows = listOf(
            due("overdue", today - 160),
            due("now", today - 148),
            due("soon", today - 140, confirmed = false),
            due("later", today - 60),
        )
        compose.setContent {
            NativeModuleReviewTheme() {
                GoatKiddingDueScreen(GoatKiddingDueState.Loaded(rows, 150), today, onSelectDoe = { selected = it }, onBack = {})
            }
        }
        compose.onNodeWithTag("farm-screen:FOS-GOAT-036").assertExists().also { compose.captureObservedNativeOwners() }
        listOf("OVERDUE" to "overdue", "DUE_NOW" to "now", "NEXT_TWO_WEEKS" to "soon", "LATER" to "later").forEach { (window, id) ->
            compose.onNodeWithTag("goat-due-group:$window").assertExists().also { compose.captureObservedNativeOwners() }
            assertEquals(GoatDueWindow.valueOf(window), goatDueWindow(rows.single { it.animalId == id }, today))
        }
        compose.onNodeWithText("Bred, not checked", substring = true).assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithTag("goat-due:now").performScrollTo().performClick()
        assertEquals("now", selected)
    }

    @Test
    fun anEmptyFarmSaysNoDoesAreExpected() {
        compose.setContent {
            NativeModuleReviewTheme() {
                GoatKiddingDueScreen(GoatKiddingDueState.Loaded(emptyList(), 150), today, onSelectDoe = {}, onBack = {})
            }
        }
        compose.onNodeWithText("No does are expected to kid.").assertExists().also { compose.captureObservedNativeOwners() }
        assertEquals(0, compose.onAllNodesWithTag("goat-due-group:DUE_NOW").fetchSemanticsNodes().size)
    }
}
