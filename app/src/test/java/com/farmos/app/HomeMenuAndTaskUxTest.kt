package com.farmos.app

import androidx.compose.ui.test.assertTextContains

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.feature.goat.GoatEntryPage
import com.farmos.feature.ops.TaskUiRow
import com.farmos.feature.ops.TasksBoardScreen
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeMenuAndTaskUxTest {
    @get:Rule(order = 0) val nativeReview = NativeModuleReviewRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test
    fun pendingSyncAndSecondaryUtilitiesDoNotOccupyManagementHome() {
        compose.setContent {
            NativeModuleReviewTheme() {
                ManagementControlRoomScreen(
                    farmName = "Premier Farm", ownerMode = false,
                    summary = FarmHomeSummary(goatCount = 12, pendingSync = 8),
                    onOpen = {}, onAnimals = {}, onMore = {},
                    today = LocalDate.of(2026, 10, 18),
                )
            }
        }
        compose.onNodeWithText("Farm modules").performScrollTo().assertIsDisplayed().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithTag("home-module:Animals").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Goats").performScrollTo().assertIsDisplayed().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithText("Sync", substring = true, ignoreCase = true).assertDoesNotExist()
        listOf("Search farm", "Today summary", "Farm alerts", "Farm activity", "Switch farm")
            .forEach { compose.onNodeWithText(it).assertDoesNotExist() }
    }

    @Test
    fun syncRemainsAnActionInMoreAndOpensItsExistingDestination() {
        var opened: FarmDestination? = null
        showMore(FarmHomePersona.MANAGER) { opened = it }
        compose.onNodeWithTag("farm-menu").assertIsDisplayed().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithTag("farm-screen:FOS-HOME-009")
            .performScrollTo().assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(FarmDestination.Goat(GoatEntryPage.SYNC), opened) }
    }

    @Test
    fun secondaryGroupsStartCollapsedAndAnExpandedActionStillNavigates() {
        var opened: FarmDestination? = null
        val health = farmMenuActions(FarmHomePersona.MANAGER)
            .first { (label, _) -> label.contains("health", ignoreCase = true) }
        showMore(FarmHomePersona.MANAGER) { opened = it }
        listOf("Farm operations", "Business", "Insights & planning").forEach { group ->
            compose.onNodeWithText(group).performScrollTo().assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"),
            )
        }
        compose.onNodeWithTag(actionTag(health.first, health.second)).assertDoesNotExist()
        compose.onNodeWithText("Farm operations").performScrollTo().performClick()
        compose.onNodeWithText("Farm operations").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"),
        )
        compose.onNodeWithTag(actionTag(health.first, health.second))
            .performScrollTo().assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(health.second, opened) }
    }

    @Test
    fun switchingBetweenWorkerAndBuyerDoesNotLeakManagerMenuActions() {
        var persona by mutableStateOf(FarmHomePersona.WORKER)
        var opened: FarmDestination? = null
        compose.setContent {
            NativeModuleReviewTheme() {
                FarmMoreScreen(
                    onOpen = {}, onBack = {}, onSignOut = {},
                    onNavigate = { opened = it }, persona = persona,
                    summary = homeSummary(), farmName = "Premier Farm",
                )
            }
        }
        val sync = FarmDestination.Goat(GoatEntryPage.SYNC)
        for (role in listOf(FarmHomePersona.WORKER, FarmHomePersona.BUYER)) {
            compose.runOnIdle { persona = role; opened = null }
            expandAllMenuGroups()
            val allowed = farmMenuActions(role)
            val allowedDestinations = allowed.map { it.second }.toSet()
            if (role == FarmHomePersona.BUYER) {
                assertFalse(sync in allowedDestinations)
                assertFalse(FarmDestination.HomePanel(HomeSurface.QUICK_CAPTURE) in allowedDestinations)
                assertFalse(FarmDestination.Health() in allowedDestinations)
                assertFalse(FarmDestination.Module(FarmModule.WATER) in allowedDestinations)
            } else {
                assertTrue(sync in allowedDestinations)
            }
            farmMenuActions(FarmHomePersona.MANAGER)
                .filter { it.second !in allowedDestinations }
                .forEach { (label, destination) ->
                    compose.onNodeWithTag(actionTag(label, destination)).assertDoesNotExist()
                }
            allowed.forEach { (label, expectedDestination) ->
                compose.runOnIdle { opened = null }
                compose.onNodeWithTag(actionTag(label, expectedDestination))
                    .performScrollTo().assertHasClickAction().performClick()
                compose.runOnIdle { assertEquals("$role action $label", expectedDestination, opened) }
            }
        }
    }

    @Test
    fun taskFiltersExposeSelectionAndChangeTheDisplayedWork() {
        showTasks(taskRows())
        compose.onNodeWithTag("task-filter:field").assertTextContains("Today")
        compose.onNodeWithText("Today checks").performScrollTo().assertIsDisplayed().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithText("Future feed").assertDoesNotExist()
        compose.onNodeWithTag("task-filter:field").performScrollTo().performClick()
        compose.onNodeWithTag("task-filter:SCHEDULED").performScrollTo().performClick()
        compose.onNodeWithTag("task-filter:field").assertTextContains("Scheduled")
        compose.onNodeWithText("Future feed").performScrollTo().assertIsDisplayed().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithText("Today checks").assertDoesNotExist()
        compose.onNodeWithTag("task-filter:field").performScrollTo().performClick()
        compose.onNodeWithTag("task-filter:OVERDUE").performScrollTo().performClick()
        compose.onNodeWithTag("task-filter:field").assertTextContains("Overdue")
        compose.onNodeWithText("Overdue inspection").performScrollTo().assertIsDisplayed().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithText("Future feed").assertDoesNotExist()
    }

    @Test
    fun completingATaskAndReturningHomeInvokeTheCorrectCallbacks() {
        var completedId: String? = null
        var openedDetail: String? = null
        var returnedHome = false
        showTasks(
            rows = taskRows().take(1),
            onComplete = { completedId = it }, onBack = { returnedHome = true },
            onOpenDetail = { openedDetail = it },
        )
        compose.onNodeWithText("Done").performScrollTo().assertHasClickAction().performClick()
        compose.runOnIdle {
            assertEquals("due-today", completedId)
            assertEquals(null, openedDetail)
        }
        compose.onNodeWithText("Farm home").performScrollTo().assertHasClickAction().performClick()
        compose.runOnIdle { assertTrue(returnedHome) }
    }

    @Test
    fun addTaskButtonSubmitsThroughTheExistingCreateCallback() {
        var created: List<String>? = null
        val due = LocalDate.now().toString()
        showTasks(emptyList(), onCreate = { title, module, code, date ->
            created = listOf(title, module, code, date)
        })
        compose.onNodeWithText("Add task").performScrollTo().assertHasClickAction().performClick()
        compose.onNode(hasSetTextAction() and hasText("Title"))
            .performScrollTo().performTextInput("Inspect water trough")
        compose.onNodeWithText("Create task").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf("Inspect water trough", "farm", "CHECK", due), created)
        }
        compose.onNodeWithText("Close task form").assertDoesNotExist()
    }

    private fun showMore(persona: FarmHomePersona, onNavigate: (FarmDestination) -> Unit) {
        compose.setContent {
            NativeModuleReviewTheme() {
                FarmMoreScreen(
                    onOpen = {}, onBack = {}, onSignOut = {}, onNavigate = onNavigate,
                    persona = persona, summary = homeSummary(), farmName = "Premier Farm",
                )
            }
        }
    }

    private fun showTasks(
        rows: List<TaskUiRow>,
        onComplete: (String) -> Unit = {},
        onBack: () -> Unit = {},
        onOpenDetail: (String) -> Unit = {},
        onCreate: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    ) {
        compose.setContent {
            NativeModuleReviewTheme() {
                TasksBoardScreen(
                    rows = rows, busy = false, error = null, onCreate = onCreate,
                    onComplete = onComplete, onBack = onBack, onOpenDetail = onOpenDetail,
                    canPlanWork = true, currentAccountId = "account-a",
                )
            }
        }
    }

    private fun expandAllMenuGroups() {
        val collapsed = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed")
        listOf("Farm tools", "Farm operations", "Business", "Insights & planning").forEach { label ->
            val nodes = compose.onAllNodesWithText(label).fetchSemanticsNodes()
            if (nodes.isNotEmpty() && collapsed.matches(nodes.single())) {
                compose.onNodeWithText(label).performScrollTo().performClick()
            }
        }
    }

    private fun actionTag(originalLabel: String, destination: FarmDestination): String =
        if (destination == FarmDestination.Goat(GoatEntryPage.SYNC)) {
            "farm-screen:FOS-HOME-009"
        } else {
            "farm-menu-action:$originalLabel"
        }

    private fun homeSummary() = FarmHomeSummary(
        goatCount = 12, openTasks = 3, overdueTasks = 1, upcomingTasks = 2, completedTasks = 4,
        activeWithdrawals = 1, pendingSync = 8, inventoryItemCount = 7, inventoryBelowReorder = 1,
    )

    private fun taskRows(): List<TaskUiRow> {
        val today = LocalDate.now().toEpochDay()
        return listOf(
            TaskUiRow("due-today", "Today checks", "goat", "CHECK", today, "open", assigneeAccountId = "account-a"),
            TaskUiRow("due-future", "Future feed", "goat", "FEED", today + 2, "open"),
            TaskUiRow("due-overdue", "Overdue inspection", "goat", "CHECK", today - 2, "open"),
        )
    }
}
