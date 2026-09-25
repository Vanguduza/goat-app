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
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dashboardActionsReachExactRegisteredOwnersAndReturn() {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
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
            "Open Breeding animals" to "FOS-RABBIT-002",
            "Open Register rabbit" to "FOS-RABBIT-005",
            "Open Cages & nest boxes" to "FOS-RABBIT-006",
            "Open Breeding wave" to "FOS-RABBIT-009",
            "Open Palpation" to "FOS-RABBIT-011",
            "Open Kindling" to "FOS-RABBIT-017",
            "Open Foster kits" to "FOS-RABBIT-020",
            "Open Weaning" to "FOS-RABBIT-022",
            "Open Mating outcome" to "FOS-RABBIT-012",
            "Open Nest-box schedule" to "FOS-RABBIT-013",
            "Open GI-stasis red flag" to "FOS-RABBIT-034",
        )

        routes.forEach { (action, screenId) ->
            compose.onNode(hasClickAction() and hasText(action))
                .performScrollTo()
                .performClick()
            compose.onNodeWithTag("farm-screen:$screenId").assertExists()
            compose.onNode(hasClickAction() and hasText("Farm home"))
                .performScrollTo()
                .performClick()
            compose.onNode(hasClickAction() and hasText(action)).assertExists()
        }
    }
}
