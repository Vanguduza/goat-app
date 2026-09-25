package com.farmos.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithTag
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
    @get:Rule
    val compose = createComposeRule()

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
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                GlobalSearchScreen(
                    busy = false,
                    searched = true,
                    message = "1 local result(s)",
                    results = listOf(result),
                    onSearch = {},
                    onOpenResult = { opened = it },
                    onBack = {},
                )
            }
        }

        compose.onNodeWithTag("farm-screen:FOS-HOME-006").assertIsDisplayed()
        compose.onNodeWithTag("farm-screen:FOS-HOME-007").performScrollTo().assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText("Goat · GT-024 · Nala · active · local"))
            .performScrollTo()
            .performClick()
        compose.runOnIdle { assertEquals(result, opened) }
    }
}
