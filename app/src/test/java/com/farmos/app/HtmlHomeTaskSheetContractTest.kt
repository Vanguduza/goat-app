package com.farmos.app

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.*
import com.farmos.feature.goat.GoatEntryPage
import java.time.LocalDate
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w390dp-h940dp-mdpi")
class HtmlHomeTaskSheetContractTest {
    @get:Rule(order = 0) val nativeReview = NativeModuleReviewRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val day = LocalDate.of(2026,8,30)
    private fun summary() = FarmHomeSummary(openTasks=4, overdueTasks=3, tasks=listOf(
        HomeTaskRow("target","Record a weight","goat","WEIGHT",day.toEpochDay(),"open","nala","Nala · G-001","goat")))

    @Test fun openingAndDismissingThePreviewDoesNotNavigateOrMutateTheTask() {
        var opened: FarmDestination? = null
        lateinit var dispatcher: OnBackPressedDispatcher
        val state = summary()
        compose.setContent { NativeModuleReviewTheme() {
            dispatcher=requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            WorkerWorkBoardScreen("Farm A",state,{opened=it},{},{},day)
        } }
        compose.onNodeWithTag("home-task-action").performScrollTo().performClick()
        compose.onNodeWithTag("home-task-sheet:target").assertIsDisplayed()
        compose.onNode(hasText("Nala · G-001") and hasAnyAncestor(hasTestTag("home-task-sheet:target"))).assertExists()
        compose.runOnIdle { assertNull(opened); assertEquals("open",state.tasks.single().status);dispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag("home-task-sheet:target").assertDoesNotExist()
        compose.onNodeWithTag("home-task-title",useUnmergedTree=true).assertTextEquals("Record a weight")
        compose.runOnIdle { assertNull(opened) }
    }

    @Test fun profileActionKeepsExactAnimalAndDoesNotGuessRabbitSex() {
        val task=summary().tasks.single()
        assertEquals(FarmDestination.Goat(GoatEntryPage.PROFILE,"nala"),homeTaskProfileDestination(task))
        assertNull(homeTaskProfileDestination(task.copy(subjectSpecies="rabbit",moduleCode="rabbit")))
        var opened:FarmDestination?=null
        compose.setContent { NativeModuleReviewTheme() {
            HomeTaskPreviewSheet(task.id,summary(),{}, {opened=it},{})
        } }
        compose.onNodeWithTag("home-task-preview-profile").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(FarmDestination.Goat(GoatEntryPage.PROFILE,"nala"),opened) }
    }

    @Test fun workStripReportsDueProportionNotTaskCompletion() {
        compose.setContent { NativeModuleReviewTheme() { AnimalFarmDueWorkStrip(3,4) } }
        compose.onNodeWithContentDescription("3 of 4 open tasks due now").assertExists().assertHeightIsEqualTo(5.dp)
        compose.onNodeWithContentDescription("3 tasks completed").assertDoesNotExist()
    }

    @Test fun aRemovedTaskCannotOpenOrMutateAnotherRecord() {
        var opened:FarmDestination?=null
        compose.setContent { NativeModuleReviewTheme() {
            HomeTaskPreviewSheet("removed-task",summary(),{}, {opened=it},{})
        } }
        compose.onNodeWithText("This task is no longer in the current farm’s work list.").assertExists()
        compose.onNodeWithTag("home-task-preview-open").assertDoesNotExist()
        compose.onNodeWithTag("home-task-preview-profile").assertDoesNotExist()
        compose.runOnIdle { assertNull(opened) }
    }
}
