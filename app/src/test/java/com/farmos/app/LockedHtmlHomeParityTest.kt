package com.farmos.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.*
import com.farmos.feature.ops.TaskEntryPage
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.time.LocalDate
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Assertions derived from the immutable HTML composition, not a self-approved screenshot baseline. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w411dp-h900dp-mdpi")
class LockedHtmlHomeParityTest {
    @get:Rule(order = 0) val name = TestName()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val day = LocalDate.of(2026, 8, 30)
    private fun summary() = FarmHomeSummary(goatCount = 24, openTasks = 3, overdueTasks = 2, upcomingTasks = 1,
        inventoryItemCount = 12, inventoryBelowReorder = 2,
        tasks = listOf(
            HomeTaskRow("nest", "Place nest boxes", "rabbit", "NEST_PLACEMENT", day.toEpochDay(), "open", null),
            HomeTaskRow("weight", "Record Nala’s weight", "goat", "WEIGHT", day.toEpochDay(), "open", "nala"),
            HomeTaskRow("kindling", "Kindling check", "rabbit", "KINDLING", day.plusDays(4).toEpochDay(), "open", null),
        ))

    @After fun retainActualNativeCandidate() {
        val dir = System.getenv("GOAT_HTML_PARITY_DIR") ?: return
        File(dir).mkdirs()
        compose.onRoot().captureRoboImage(File(dir, name.methodName + ".png").absolutePath)
    }

    @Test fun managementMatchesTheReferenceHierarchyAndCompactModuleGrid() {
        compose.setContent { FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
            ManagementControlRoomScreen("Premier Farm", true, summary(), {}, {}, {}, day)
        } }
        compose.onNodeWithText("Farm pulse").assertIsDisplayed()
        compose.onNodeWithTag("home-attention").assertIsDisplayed().assertHasClickAction()
        val work = compose.onNodeWithTag("home-summary:Work").fetchSemanticsNode().boundsInRoot
        val resources = compose.onNodeWithTag("home-summary:Resources").fetchSemanticsNode().boundsInRoot
        assertEquals("Work and resources remain paired at normal phone scale", work.top, resources.top, 1f)
        assertTrue(work.right < resources.left)
        listOf("Animals", "Team", "Breeding", "Finance", "Inventory", "Reports").forEach { label ->
            compose.onNodeWithTag("home-module:$label").performScrollTo().assertIsDisplayed().assertHasClickAction()
        }
        compose.onNodeWithText("Waiting to sync").assertDoesNotExist()
    }

    @Test fun heroKeepsItsPortraitBesideTheTextAtPhoneWidthAndUsesOneReviewAction() {
        var opened: FarmDestination? = null
        compose.setContent { FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
            ManagementControlRoomScreen("Premier Farm", true, summary(), { opened = it }, {}, {}, day)
        } }
        val portrait = compose.onNodeWithTag("home-attention-portrait", useUnmergedTree = true)
        portrait.assertExists().assertWidthIsEqualTo(100.dp).assertHeightIsEqualTo(120.dp)
        val image = portrait.fetchSemanticsNode().boundsInRoot
        val text = compose.onNodeWithTag("home-attention-copy", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Image and text share a horizontal hero", image.left >= text.right)
        compose.onNodeWithTag("home-attention").performClick()
        compose.runOnIdle { assertEquals(FarmDestination.Task("nest"), opened) }
    }

    @Test fun managementAnimalsOpensTheNativeFamilySheetWithoutChangingItsRole() {
        var opened: FarmDestination? = null
        compose.setContent { FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
            ManagementControlRoomScreen("Premier Farm", true, summary(), { opened = it }, {}, {}, day)
        } }
        compose.onNodeWithTag("home-module:Animals").performScrollTo().performClick()
        compose.onNodeWithTag("home-sheet:Animal families").assertIsDisplayed()
        compose.onNodeWithTag("home-family:Rabbits").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(FarmDestination.Module(FarmModule.RABBIT), opened) }
        compose.onNodeWithTag("home-sheet:Animal families").assertDoesNotExist()
        compose.onNodeWithText("Farm pulse").assertExists()
    }

    @Test fun workerRetainsTheDayBannerImageCardAndActualTaskIdentity() {
        var opened: FarmDestination? = null
        compose.setContent { FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
            WorkerWorkBoardScreen("Premier Farm", summary(), { opened = it }, {}, {}, day)
        } }
        compose.onNodeWithText("My work").assertIsDisplayed()
        compose.onNodeWithTag("home-workday-banner").assertIsDisplayed()
        compose.onNodeWithTag("home-stage:0").assertIsSelected()
        compose.onNodeWithText("Review & decide").assertExists()
        compose.onNodeWithTag("home-task-title", useUnmergedTree = true).assertTextEquals("Place nest boxes")
        compose.onNodeWithContentDescription("Next task").performScrollTo().performClick()
        compose.onNodeWithTag("home-task-title", useUnmergedTree = true).assertTextEquals("Record Nala’s weight")
        compose.onNodeWithTag("home-task-action").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(FarmDestination.Task("weight"), opened) }
        compose.onNodeWithTag("home-task-title", useUnmergedTree = true).assertTextEquals("Record Nala’s weight")
        compose.onNodeWithTag("home-stage:1").performScrollTo().performClick()
        compose.onNodeWithTag("home-task-title", useUnmergedTree = true).assertTextEquals("Kindling check")
        compose.onNodeWithContentDescription("Previous task").assertIsNotEnabled()
    }

    @Test fun themeUsesTheReferenceBottomSheetAndDoesNotExposeAdministration() {
        val mode = mutableStateOf(AnimalFarmThemeMode.LIGHT)
        compose.setContent { FarmOsTheme(mode = mode.value, onModeChange = { mode.value = it }) {
            ManagementControlRoomScreen("Premier Farm", true, summary(), {}, {}, {}, day)
        } }
        compose.onNodeWithContentDescription("Theme").performClick()
        compose.onNodeWithTag("home-sheet:Theme").assertIsDisplayed()
        compose.onNodeWithText("Dark").performClick()
        compose.runOnIdle { assertEquals(AnimalFarmThemeMode.DARK, mode.value) }
        compose.onNodeWithText("Members").assertDoesNotExist()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithTag("home-sheet:Theme").assertDoesNotExist()
    }
}
