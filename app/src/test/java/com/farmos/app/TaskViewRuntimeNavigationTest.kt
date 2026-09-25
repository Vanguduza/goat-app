package com.farmos.app

import androidx.compose.ui.test.assertDoesNotExist
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
import com.farmos.feature.ops.TaskUiRow
import com.farmos.feature.ops.TasksBoardScreen
import java.time.LocalDate
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
        compose.onNodeWithText("Past check").assertDoesNotExist()

        openTab("Scheduled")
        compose.onNodeWithTag("farm-screen:FOS-TASK-007").assertIsDisplayed()
        compose.onNodeWithText("Future check").assertIsDisplayed()
        compose.onNodeWithText("Past check").assertDoesNotExist()
        restoreToday()

        openTab("Overdue")
        compose.onNodeWithTag("farm-screen:FOS-TASK-008").assertIsDisplayed()
        compose.onNodeWithText("Past check").assertIsDisplayed()
        compose.onNodeWithText("Today check").assertDoesNotExist()
        restoreToday()

        openTab("Completed")
        compose.onNodeWithTag("farm-screen:FOS-TASK-009").assertIsDisplayed()
        compose.onNodeWithText("Done check").assertIsDisplayed()
        compose.onNodeWithText("Future check").assertDoesNotExist()
        restoreToday()
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
}
