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
import com.farmos.core.network.FarmMembership
import java.time.LocalDate
import org.junit.Assert.assertEquals
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
    fun homeUtilityPanelsTraverseAndRestoreWithoutCrossingAuthorityBoundaries() {
        val today = LocalDate.now().toEpochDay()
        val premier = FarmMembership("farm-premier", "owner")
        val irene = FarmMembership("farm-irene", "manager")
        var switched: FarmMembership? = null

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
                        tasks = listOf(
                            HomeTaskRow(
                                id = "task-feed",
                                title = "Review kid feed",
                                moduleCode = "feed",
                                taskCode = "FEED_REVIEW",
                                dueEpochDay = today + 1,
                                status = "open",
                                animalId = null,
                            ),
                            HomeTaskRow(
                                id = "task-health",
                                title = "Check Nala condition",
                                moduleCode = "goat",
                                taskCode = "HEALTH_CHECK",
                                dueEpochDay = today - 1,
                                status = "done",
                                animalId = "goat-nala",
                            ),
                        ),
                    ),
                    onOpen = {},
                    onSignOut = {},
                    memberships = listOf(premier, irene),
                    farmNames = mapOf(
                        premier.farmId to "Premier Farm",
                        irene.farmId to "Irene Farm",
                    ),
                    currentFarmId = premier.farmId,
                    onSwitchFarm = { switched = it },
                )
            }
        }

        open("Today summary")
        compose.onNodeWithTag("farm-screen:FOS-HOME-003").assertExists()
        compose.onNode(hasClickAction() and hasText("Open tasks")).assertExists()
        restoreHome()

        open("Farm alerts")
        compose.onNodeWithTag("farm-screen:FOS-HOME-004").assertExists()
        compose.onNodeWithText("• 1 overdue task(s)").assertExists()
        restoreHome()

        open("Farm activity")
        compose.onNodeWithTag("farm-screen:FOS-HOME-005").assertExists()
        compose.onNodeWithText("Review kid feed").assertExists()
        compose.onNodeWithText("Check Nala condition").assertExists()
        restoreHome()

        open("Notifications")
        compose.onNodeWithTag("farm-screen:FOS-HOME-011").assertExists()
        compose.onNodeWithText("• 2 local change(s) waiting to sync").assertExists()
        restoreHome()

        open("Quick capture")
        compose.onNodeWithTag("farm-screen:FOS-HOME-008").assertExists()
        compose.onNodeWithText("Record goat weight").assertExists()
        restoreHome()

        open("Switch farm")
        compose.onNodeWithTag("farm-screen:FOS-HOME-010").assertExists()
        compose.onNodeWithText("Premier Farm").assertExists()
        open("Switch to Irene Farm")
        compose.runOnIdle { assertEquals(irene, switched) }
        compose.onNodeWithTag("farm-screen:FOS-HOME-012-A").assertExists()
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
