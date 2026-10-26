package com.farmos.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.*
import com.farmos.feature.ops.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Owner's installed-device feedback,26October2026: these supersede only the original Home headline. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[36],qualifiers="en-rUS-w390dp-h900dp-mdpi")
class OwnerFieldFeedbackTest {
    @get:Rule val compose=createComposeRule()

    @Test fun homeShowsOnlyFarmIdentityAndCommandCenterNotPulseOrHeaderDate() {
        compose.setContent { FarmOsTheme(mode=AnimalFarmThemeMode.DARK) {
            ManagementControlRoomScreen("Premier Farm",true,FarmHomeSummary(),{},{},{},LocalDate.of(2026,10,26))
        } }
        compose.onNodeWithText("Premier Farm").assertIsDisplayed()
        compose.onNodeWithText("Command center").assertIsDisplayed()
        compose.onNodeWithText("Farm pulse").assertDoesNotExist()
        compose.onNodeWithText("Mon, 26 Oct").assertDoesNotExist()
    }

    @Test fun weightShortcutDoesNotSilentlySelectGoats() {
        var opened:FarmDestination?=null
        compose.setContent { FarmOsTheme(mode=AnimalFarmThemeMode.LIGHT) {
            HomeReferenceQuickRow({opened=it},{})
        } }
        compose.onNodeWithTag("home-quick:Weight").performClick()
        compose.runOnIdle { assertNull("Choose the species before entering a recording owner",opened) }
        compose.onNodeWithTag("home-sheet:Record weight").assertIsDisplayed()
        listOf("Goats","Rabbits","Sheep","Cattle","Poultry").forEach {
            compose.onNodeWithTag("weight-family:$it").performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun repeatChoicesAreCollapsedUntilTheirPickerIsOpened() {
        compose.setContent { FarmOsTheme(mode=AnimalFarmThemeMode.LIGHT) {
            CreateTaskScreen(false,null,{_,_,_,_->},{},canPlanWork=true)
        } }
        compose.onNodeWithTag("task-repeat:field").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("task-repeat:DAILY").assertDoesNotExist()
        compose.onNodeWithTag("task-repeat:field").performClick()
        compose.onNodeWithTag("task-repeat:DAILY").performScrollTo().performClick()
        compose.onNodeWithTag("task-repeat:DAILY").assertDoesNotExist()
        compose.onNodeWithTag("task-repeat:field").assertTextContains("Daily")
    }

    @Test fun assigneeChoicesAreInAPickerNotTheCreateForm() {
        compose.setContent { FarmOsTheme(mode=AnimalFarmThemeMode.LIGHT) {
            CreateTaskScreen(false,null,{_,_,_,_->},{},canPlanWork=true,
                assignees=listOf(TaskAssigneeOption("alice","Alice"),TaskAssigneeOption("bob","Bob")))
        } }
        compose.onNodeWithTag("task-assignee:field").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("task-assignee:bob").assertDoesNotExist()
        compose.onNodeWithTag("task-assignee:field").performClick()
        compose.onNodeWithTag("task-assignee:bob").performScrollTo().performClick()
        compose.onNodeWithTag("task-assignee:bob").assertDoesNotExist()
        compose.onNodeWithTag("task-assignee:field").assertTextContains("Bob")
    }

    @Test fun creatingATaskDoesNotLeaveTheWholeBoardBelowTheForm() {
        compose.setContent { FarmOsTheme(mode=AnimalFarmThemeMode.DARK) {
            TasksBoardScreen(emptyList(),false,null,{_,_,_,_->},{},{},canPlanWork=true)
        } }
        compose.onNodeWithText("Add task").performClick()
        compose.onNodeWithTag("farm-screen:FOS-TASK-004").assertExists()
        compose.onNodeWithText("No tasks due today").assertDoesNotExist()
        compose.onNodeWithTag("task-filter:TODAY").assertDoesNotExist()
    }
}
