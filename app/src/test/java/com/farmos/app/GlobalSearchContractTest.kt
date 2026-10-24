package com.farmos.app

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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class GlobalSearchContractTest {
    @get:Rule(order = 0) val nativeReview = NativeModuleReviewRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test
    fun searchAndResultsRenderCanonicalOwnersAndOpenSpecies() {
        val result = GlobalSearchResultUi(
            animalId = "goat-nala",
            speciesCode = "goat",
            tag = "GT-024",
            displayName = "Nala",
            status = "active",
            source = "local",
        )
        var opened: GlobalSearchResultUi? = null

        compose.setContent {
            NativeModuleReviewTheme() {
                GlobalSearchScreen(
                    busy = false,
                    searched = true,
                    message = "",
                    results = listOf(result),
                    onSearch = {},
                    onOpenResult = { opened = it },
                    onBack = {},
                )
            }
        }

        compose.onNodeWithTag("farm-screen:FOS-HOME-006").assertIsDisplayed().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithTag("farm-screen:FOS-HOME-007").performScrollTo().assertIsDisplayed().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithText("GT-024 · Nala").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithText("Goat · active").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithTag("search-result:goat-nala")
            .performScrollTo()
            .performClick()
        compose.runOnIdle { assertEquals(result, opened) }
    }

    @Test
    fun searchWithNoMatchesShowsTheSearchEmptyState() {
        compose.setContent {
            NativeModuleReviewTheme() {
                GlobalSearchScreen(
                    busy = false,
                    searched = true,
                    message = "",
                    results = emptyList(),
                    onSearch = {},
                    onOpenResult = {},
                    onBack = {},
                )
            }
        }
        compose.onNodeWithTag("farm-screen:FOS-HOME-007").performScrollTo().assertIsDisplayed().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithTag("farm-screen:FOS-SEARCH-004").performScrollTo().assertIsDisplayed().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithTag("farm-atom:FOS-ATOM-031").performScrollTo().assertIsDisplayed().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithText("No matching animal records.").assertExists().also { compose.captureObservedNativeOwners() }
    }

    @Test
    fun searchWithMatchesShowsNoEmptyState() {
        compose.setContent {
            NativeModuleReviewTheme() {
                GlobalSearchScreen(
                    busy = false,
                    searched = true,
                    message = "",
                    results = listOf(GlobalSearchResultUi("goat-nala", "goat", "GT-024", "Nala", "active", "local")),
                    onSearch = {},
                    onOpenResult = {},
                    onBack = {},
                )
            }
        }
        compose.onNodeWithTag("farm-atom:FOS-ATOM-031").assertDoesNotExist()
    }
}
