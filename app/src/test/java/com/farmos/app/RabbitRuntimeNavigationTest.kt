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
import com.farmos.feature.rabbit.RabbitProgrammeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class RabbitRuntimeNavigationTest {
    @get:Rule(order = 0) val nativeReview = NativeModuleReviewRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test
    fun dashboardActionsReachExactRegisteredOwnersAndReturn() {
        compose.setContent {
            NativeModuleReviewTheme() {
                RabbitProgrammeScreen(
                    cages = emptyList(),
                    waves = emptyList(),
                    availableBoxes = 0,
                    busy = false,
                    error = null,
                    does = emptyList(),
                    onRegisterDoe = { _, _, _ -> },
                    onCreateCage = {},
                    onCreateNestBox = { _, _ -> },
                    onCreateWave = { _, _, _ -> },
                    onPalpate = { _, _, _ -> },
                    onKindle = { _, _, _, _ -> },
                    onFoster = { _, _, _, _, _ -> },
                    onBack = {},
                )
            }
        }

        val routes = listOf(
            "Breeding animals" to "FOS-RABBIT-002",
            "Register rabbit" to "FOS-RABBIT-005",
            "Cages & nest boxes" to "FOS-RABBIT-006",
            "Breeding wave" to "FOS-RABBIT-009",
            "Palpation" to "FOS-RABBIT-011",
            "Kindling" to "FOS-RABBIT-017",
            "Foster kits" to "FOS-RABBIT-020",
            "Weaning" to "FOS-RABBIT-022",
            "Mating outcome" to "FOS-RABBIT-012",
            "Nest-box schedule" to "FOS-RABBIT-013",
            "GI-stasis red flag" to "FOS-RABBIT-034",
        )

        routes.forEach { (action, screenId) ->
            val section = when (action) {
                "Breeding animals", "Register rabbit", "GI-stasis red flag" -> "Animals"
                "Cages & nest boxes", "Nest-box schedule" -> "Housing"
                else -> "Breeding"
            }
            compose.onNodeWithTag("rabbit-section:$section").performScrollTo().performClick()
            compose.onNode(hasClickAction() and hasText(action))
                .performScrollTo()
                .performClick()
            compose.onNodeWithTag("farm-screen:$screenId").assertExists().also { compose.captureObservedNativeOwners() }
            compose.onNode(hasClickAction() and hasText("Farm home"))
                .performScrollTo()
                .performClick()
            compose.onNode(hasClickAction() and hasText(action)).assertExists().also { compose.captureObservedNativeOwners() }
        }
    }
}
