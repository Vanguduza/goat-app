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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class HomeUtilityRuntimeNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun todayAlertsAndQuickCaptureTraverseAndRestoreHome() {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                RoleAwareFarmHomeScreen(
                    role = "owner",
                    farmName = "Premier Farm",
                    summary = FarmHomeSummary(
                        openTasks = 3,
                        overdueTasks = 1,
                        activeWithdrawals = 1,
                        pendingSync = 2,
                        inventoryItemCount = 5,
                        inventoryBelowReorder = 1,
                    ),
                    onOpen = {},
                    onSignOut = {},
                )
            }
        }

        open("Today summary")
        compose.onNodeWithTag("farm-screen:FOS-HOME-003").assertExists()
        compose.onNodeWithText("Open tasks").assertExists()
        restoreHome()

        open("Farm alerts")
        compose.onNodeWithTag("farm-screen:FOS-HOME-004").assertExists()
        compose.onNodeWithText("1 overdue task(s)").assertExists()
        restoreHome()

        open("Quick capture")
        compose.onNodeWithTag("farm-screen:FOS-HOME-008").assertExists()
        compose.onNodeWithText("Record goat weight").assertExists()
        restoreHome()
    }

    private fun open(label: String) {
        compose.onNode(hasClickAction() and hasText(label))
            .performScrollTo()
            .performClick()
    }

    private fun restoreHome() {
        open("Farm home")
        compose.onNodeWithTag("farm-screen:FOS-HOME-012-A").assertExists()
    }
}
