package com.farmos.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
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
    @get:Rule(order = 0) val nativeReview = NativeModuleReviewRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test
    fun homeUtilityPanelsTraverseAndRestoreWithoutCrossingAuthorityBoundaries() {
        val today = LocalDate.now().toEpochDay()
        val premier = FarmMembership("farm-premier", "owner")
        val irene = FarmMembership("farm-irene", "manager")
        var switched: FarmMembership? = null

        compose.setContent {
            NativeModuleReviewTheme() {
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

        openFromMenu("Today summary")
        compose.onNodeWithTag("farm-screen:FOS-HOME-003").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNode(hasClickAction() and hasText("Open tasks")).assertExists().also { compose.captureObservedNativeOwners() }
        restoreHome()

        openFromMenu("Farm alerts")
        compose.onNodeWithTag("farm-screen:FOS-HOME-004").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithText("• 1 overdue task(s)").assertExists().also { compose.captureObservedNativeOwners() }
        restoreHome()

        openFromMenu("Farm activity")
        compose.onNodeWithTag("farm-screen:FOS-HOME-005").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithText("Review kid feed").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithText("Check Nala condition").assertExists().also { compose.captureObservedNativeOwners() }
        restoreHome()

        openFromMenu("Notifications")
        compose.onNodeWithTag("farm-screen:FOS-HOME-011").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithText("• 2 local change(s) waiting to sync").assertExists().also { compose.captureObservedNativeOwners() }
        restoreHome()

        openFromMenu("Quick capture")
        compose.onNodeWithTag("farm-screen:FOS-HOME-008").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithText("Record goat weight").assertExists().also { compose.captureObservedNativeOwners() }
        restoreHome()

        openFromMenu("Switch farm")
        compose.onNodeWithTag("farm-screen:FOS-HOME-010").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithTag("farm-atom:FOS-ATOM-006").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithTag("farm-atom:FOS-ATOM-006:option:${premier.farmId}").assertIsSelected()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-006:option:${premier.farmId}").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(null, switched) }
        compose.onNodeWithTag("farm-atom:FOS-ATOM-006:option:${irene.farmId}")
            .assertIsNotSelected()
            .performScrollTo()
            .performClick()
        compose.runOnIdle { assertEquals(irene, switched) }
        compose.onNodeWithTag("farm-screen:FOS-HOME-012-A").assertExists().also { compose.captureObservedNativeOwners() }
    }

    private fun openFromMenu(label: String) {
        compose.onNode(hasClickAction() and hasText("More")).performClick()
        compose.onNodeWithTag("farm-menu").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithTag("farm-menu-action:$label").performScrollTo().performClick()
    }

    private fun open(label: String) {
        compose.onNode(hasClickAction() and hasText(label))
            .performScrollTo()
            .performClick()
    }

    private fun restoreHome() {
        open("Farm home")
        compose.onNodeWithTag("farm-screen:FOS-HOME-012-A").assertExists().also { compose.captureObservedNativeOwners() }
    }

    @Test
    fun todaySummaryShowsTheSyncPendingReceiptOnlyWhileChangesWait() {
        val pending = mutableStateOf(2L)
        compose.setContent {
            NativeModuleReviewTheme() {
                HomeUtilityScreen(
                    surface = HomeSurface.TODAY_SUMMARY,
                    summary = FarmHomeSummary(pendingSync = pending.value),
                    onOpen = {},
                    onBack = {},
                )
            }
        }
        compose.onNodeWithTag("farm-atom:FOS-ATOM-035").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithText("2 changes saved on this device are waiting to sync").assertExists().also { compose.captureObservedNativeOwners() }
        pending.value = 1L
        compose.onNodeWithText("1 change saved on this device is waiting to sync").assertExists().also { compose.captureObservedNativeOwners() }
        pending.value = 0L
        compose.onNodeWithTag("farm-atom:FOS-ATOM-035").assertDoesNotExist()
    }
}
