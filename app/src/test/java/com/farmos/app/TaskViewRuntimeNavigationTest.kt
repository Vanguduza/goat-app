package com.farmos.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.feature.ops.CreateTaskScreen
import com.farmos.feature.ops.EditTaskScreen
import com.farmos.feature.ops.TaskAssigneeOption
import com.farmos.feature.ops.TaskEditDraft
import com.farmos.feature.ops.TaskEditScope
import com.farmos.feature.ops.TaskRepeat
import com.farmos.feature.ops.TaskSeriesDraft
import com.farmos.feature.ops.TaskSeriesUiRow
import com.farmos.feature.ops.TaskUiRow
import com.farmos.feature.ops.TasksBoardScreen
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class TaskViewRuntimeNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun scheduledOverdueAndCompletedViewsAreDistinctAndRestoreToday() {
        val today = LocalDate.now().toEpochDay()
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                TasksBoardScreen(
                    rows = listOf(
                        TaskUiRow("today", "Today check", "goat", "CHECK", today, "open"),
                        TaskUiRow("future", "Future check", "goat", "CHECK", today + 2, "open"),
                        TaskUiRow("past", "Past check", "goat", "CHECK", today - 2, "open"),
                        TaskUiRow("done", "Done check", "goat", "CHECK", today - 1, "done"),
                    ),
                    busy = false,
                    error = null,
                    onCreate = { _, _, _, _ -> },
                    onComplete = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithTag("farm-screen:FOS-TASK-001").assertIsDisplayed()
        compose.onNodeWithText("Today check").assertIsDisplayed()
        assertTextAbsent("Past check")

        openTab("Scheduled")
        compose.onNodeWithTag("farm-screen:FOS-TASK-007").assertIsDisplayed()
        compose.onNodeWithText("Future check").assertIsDisplayed()
        assertTextAbsent("Past check")
        restoreToday()

        openTab("Overdue")
        compose.onNodeWithTag("farm-screen:FOS-TASK-008").assertIsDisplayed()
        compose.onNodeWithText("Past check").assertIsDisplayed()
        assertTextAbsent("Today check")
        restoreToday()

        openTab("Completed")
        compose.onNodeWithTag("farm-screen:FOS-TASK-009").assertIsDisplayed()
        compose.onNodeWithText("Done check").assertIsDisplayed()
        assertTextAbsent("Future check")
        restoreToday()
    }

    @Test
    fun allTasksViewListsOpenAndCompletedWorkWithTheExactCompletedTotal() {
        val today = LocalDate.now().toEpochDay()
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                TasksBoardScreen(
                    rows = listOf(
                        TaskUiRow("today", "Today check", "goat", "CHECK", today, "open"),
                        TaskUiRow("past", "Past check", "sheep", "DRENCH", today - 2, "open"),
                        TaskUiRow("done", "Done check", "goat", "CHECK", today - 1, "done"),
                    ),
                    busy = false,
                    error = null,
                    onCreate = { _, _, _, _ -> },
                    onComplete = {},
                    onBack = {},
                    completedCount = 140,
                )
            }
        }

        openTab("All")
        compose.onNodeWithTag("farm-screen:FOS-TASK-002").assertIsDisplayed()
        compose.onNodeWithText("Today check").assertExists()
        compose.onNodeWithText("Past check").assertExists()
        compose.onNodeWithText("Done check").assertExists()
        compose.onNodeWithText("Completed tasks · latest 1 of 140").assertExists()
        restoreToday()
        assertTextAbsent("Completed tasks · latest 1 of 140")
    }

    @Test
    fun calendarGroupsOpenTasksUnderTheirDueDates() {
        val today = LocalDate.now().toEpochDay()
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                TasksBoardScreen(
                    rows = listOf(
                        TaskUiRow("today", "Today check", "goat", "CHECK", today, "open"),
                        TaskUiRow("future-b", "Fence walk", "pasture", "WALK", today + 2, "open"),
                        TaskUiRow("future-a", "Drench ewes", "sheep", "DRENCH", today + 2, "open"),
                        TaskUiRow("past", "Past check", "goat", "CHECK", today - 2, "open"),
                        TaskUiRow("done", "Done check", "goat", "CHECK", today - 1, "done"),
                    ),
                    busy = false,
                    error = null,
                    onCreate = { _, _, _, _ -> },
                    onComplete = {},
                    onBack = {},
                )
            }
        }

        openTab("Calendar")
        compose.onNodeWithTag("farm-screen:FOS-TASK-010").assertIsDisplayed()
        compose.onNodeWithText("${LocalDate.ofEpochDay(today - 2)} · Overdue · 1 open task").assertExists()
        compose.onNodeWithText("${LocalDate.ofEpochDay(today)} · Today · 1 open task").assertExists()
        compose.onNodeWithText("${LocalDate.ofEpochDay(today + 2)} · 2 open tasks").assertExists()
        compose.onNodeWithText("Drench ewes").assertExists()
        assertTextAbsent("Done check")
        restoreToday()
    }

    @Test
    fun emptyAllTasksViewSaysNothingIsRecorded() {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                TasksBoardScreen(
                    rows = emptyList(),
                    busy = false,
                    error = null,
                    onCreate = { _, _, _, _ -> },
                    onComplete = {},
                    onBack = {},
                    completedCount = 0,
                )
            }
        }
        openTab("All")
        compose.onNodeWithTag("farm-screen:FOS-TASK-002").assertIsDisplayed()
        compose.onNodeWithText("No tasks on this device").assertExists()
        assertTextAbsent("Completed tasks · latest 0 of 0")
    }

    @Test
    fun assignedToMeAndRepeatingViewsShowOnlyTheirOwnWork() {
        val today = LocalDate.now().toEpochDay()
        val ended = mutableListOf<String>()
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                TasksBoardScreen(
                    rows = listOf(
                        TaskUiRow("today", "Today check", "goat", "CHECK", today, "open"),
                        TaskUiRow("mine", "Check troughs", "ops", "WATER", today, "open", seriesId = "s1", occurrenceEpochDay = today, assigneeAccountId = "farai", assigneeLabel = "Farai", repeatLabel = "Daily"),
                        TaskUiRow("theirs", "Feed calves", "cattle", "FEED", today, "open", seriesId = "s2", occurrenceEpochDay = today, assigneeAccountId = "tendai", assigneeLabel = "Tendai", repeatLabel = "Weekdays"),
                    ),
                    busy = false,
                    error = null,
                    onCreate = { _, _, _, _ -> },
                    onComplete = {},
                    onBack = {},
                    canPlanWork = true,
                    currentAccountId = "farai",
                    series = listOf(
                        TaskSeriesUiRow("s1", "Check troughs", "Daily", today - 3, null, today, "Farai"),
                        TaskSeriesUiRow("s2", "Feed calves", "Weekdays", today - 3, today + 20, today, "Tendai"),
                    ),
                    onEndSeries = { ended += it },
                    repeatHorizonDays = 30,
                )
            }
        }

        compose.onNodeWithText("ops · WATER · Daily · Assigned to Farai").assertExists()
        openTab("Assigned to me")
        compose.onNodeWithTag("farm-screen:FOS-TASK-006").assertIsDisplayed()
        compose.onNodeWithText("Check troughs").assertExists()
        assertTextAbsent("Feed calves")
        assertTextAbsent("Today check")
        restoreToday()

        openTab("Repeating")
        compose.onNodeWithTag("farm-screen:FOS-TASK-011").assertIsDisplayed()
        compose.onNodeWithText("Weekdays · from ${LocalDate.ofEpochDay(today - 3)} · until ${LocalDate.ofEpochDay(today + 20)}").assertExists()
        compose.onNodeWithText("Next ${LocalDate.ofEpochDay(today)} · Assigned to Tendai").assertExists()
        compose.onNodeWithTag("task-series-end:s2").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(ended == listOf("s2")) }
        restoreToday()

        openTab("Scheduled")
        compose.onNodeWithTag("task-repeat-horizon").assertExists()
        restoreToday()
    }

    @Test
    fun onlyPlannersSeeRepeatAndAssignmentWhenCreatingATask() {
        var planner by mutableStateOf(false)
        val drafts = mutableListOf<TaskSeriesDraft>()
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                CreateTaskScreen(
                    busy = false,
                    error = null,
                    onCreate = { _, _, _, _ -> },
                    onBack = {},
                    canPlanWork = planner,
                    assignees = listOf(TaskAssigneeOption("farai", "Farai")),
                    onCreateSeries = { drafts += it },
                )
            }
        }
        compose.onNodeWithTag("farm-screen:FOS-TASK-004").assertExists()
        assertTextAbsent("Repeat")
        assertTextAbsent("Assign to")

        planner = true
        compose.onNodeWithText("Title").performTextInput("Check troughs")
        compose.onNodeWithTag("task-repeat:EVERY_N_DAYS").performScrollTo().performClick()
        compose.onNodeWithTag("task-repeat-interval").performTextReplacement("3")
        compose.onNodeWithTag("task-assignee:farai").performScrollTo().performClick()
        compose.onNode(hasClickAction() and hasText("Create task")).performScrollTo().performClick()
        compose.runOnIdle {
            val draft = drafts.single()
            assertTrue(draft.repeat == TaskRepeat.EVERY_N_DAYS && draft.interval == 3 && draft.assigneeAccountId == "farai" && draft.endDate == null)
        }
    }

    @Test
    fun editingARepeatingTaskOffersEachScopeAndMovesOnlyThisOccurrence() {
        val today = LocalDate.now().toEpochDay()
        val saved = mutableListOf<TaskEditDraft>()
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                EditTaskScreen(
                    task = TaskUiRow("o1", "Check troughs", "ops", "WATER", today, "open", seriesId = "s1", occurrenceEpochDay = today, repeatLabel = "Daily"),
                    assignees = listOf(TaskAssigneeOption("farai", "Farai")),
                    busy = false,
                    error = null,
                    onSave = { saved += it },
                    onBack = {},
                )
            }
        }
        compose.onNodeWithTag("farm-screen:FOS-TASK-005").assertExists()
        compose.onNodeWithTag("task-edit-due").performTextReplacement(LocalDate.ofEpochDay(today + 1).toString())
        compose.onNodeWithTag("task-edit-save").performScrollTo().performClick()
        compose.runOnIdle {
            val draft = saved.single()
            assertTrue(draft.scope == TaskEditScope.THIS && draft.movedTo == LocalDate.ofEpochDay(today + 1) && draft.repeat == null && draft.title == null)
        }

        // This and future: no due date to move; the repeat can change.
        compose.onNodeWithTag("task-edit-scope:THIS_AND_FUTURE").performScrollTo().performClick()
        assertTextAbsent("Due date")
        compose.onNodeWithTag("task-edit-repeat:WEEKLY").performScrollTo().performClick()
        compose.onNodeWithTag("task-edit-save").performScrollTo().performClick()
        compose.runOnIdle {
            val draft = saved.last()
            assertTrue(draft.scope == TaskEditScope.THIS_AND_FUTURE && draft.repeat == TaskRepeat.WEEKLY && draft.movedTo == null)
        }
    }

    private fun openTab(label: String) {
        compose.onNode(hasClickAction() and hasText(label))
            .performScrollTo()
            .performClick()
    }

    private fun restoreToday() {
        openTab("Today")
        compose.onNodeWithTag("farm-screen:FOS-TASK-001").assertIsDisplayed()
        compose.onNodeWithText("Today check").assertIsDisplayed()
    }

    private fun assertTextAbsent(text: String) {
        compose.waitForIdle()
        assertTrue(
            "Expected '$text' to be absent",
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty(),
        )
    }
}
