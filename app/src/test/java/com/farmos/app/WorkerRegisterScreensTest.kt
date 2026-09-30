package com.farmos.app

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
import com.farmos.feature.ops.LabourRecordNavigator
import com.farmos.feature.ops.LabourRecords
import com.farmos.feature.ops.WorkerRegisterScreens
import com.farmos.feature.ops.WorkerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Rendered worker register (FOS-LABOUR-002 / 003), resolution R1 of D-020. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class WorkerRegisterScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val workers = listOf(
        WorkerView("w1", "Tendai", active = true, linkedAccount = null),
        WorkerView("w2", "Farai", active = true, linkedAccount = "farai"),
        WorkerView("w3", "Chipo", active = false, linkedAccount = null),
    )

    @Test
    fun managersAddRenameAndDeactivateWorkers() {
        val added = mutableListOf<String>()
        val renamed = mutableListOf<Pair<String, String>>()
        val status = mutableListOf<Pair<String, Boolean>>()
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                WorkerRegisterScreens(workers, canManage = true, busy = false, error = null, onAdd = { added += it }, onRename = { i, n -> renamed += i to n }, onSetActive = { i, a -> status += i to a }, onBack = {})
            }
        }
        compose.onNodeWithTag("farm-screen:FOS-LABOUR-002").assertExists()
        compose.onNodeWithText("Workers · 2 active").assertExists()
        compose.onNodeWithText("Active · no login").assertExists()
        compose.onNodeWithText("Active · signs in as farai").assertExists()
        compose.onNodeWithText("Inactive · no login").assertExists()
        compose.onNodeWithTag("worker-add-name").performTextInput("Rudo")
        compose.onNodeWithTag("worker-add").performClick()

        compose.onNodeWithTag("worker:w1").performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-LABOUR-003").assertExists()
        compose.onNodeWithText("No login. Work can still be assigned and recorded for this worker.").assertExists()
        compose.onNodeWithTag("worker-rename-name").performTextReplacement("Tendai M.")
        compose.onNodeWithTag("worker-rename").performScrollTo().performClick()
        compose.onNodeWithTag("worker-set-active").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf("Rudo"), added)
            assertEquals(listOf("w1" to "Tendai M."), renamed)
            assertEquals(listOf("w1" to false), status)
        }
    }

    @Test
    fun othersSeeTheRegisterWithoutChangingIt() {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                WorkerRegisterScreens(workers, canManage = false, busy = false, error = null, onAdd = {}, onRename = { _, _ -> }, onSetActive = { _, _ -> }, onBack = {})
            }
        }
        compose.onNodeWithText("Workers are managed by supervisors and farm management").assertExists()
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasText("Add worker")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("worker:w1").performScrollTo().performClick()
        assertTrue(compose.onAllNodes(hasText("Make inactive")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun theLabourHomeOpensTheRegisterAndReturns() {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                LabourRecordNavigator(
                    LabourRecords(),
                    workers = { back -> WorkerRegisterScreens(workers, false, false, null, {}, { _, _ -> }, { _, _ -> }, back) },
                ) { actions -> actions() }
            }
        }
        // The test renders the labour actions directly, without a scrolling page.
        compose.onNode(hasClickAction() and hasText("Workers")).performClick()
        compose.onNodeWithTag("farm-screen:FOS-LABOUR-002").assertExists()
        // The register page's back control is at the end of its scrolling content.
        compose.onNode(hasClickAction() and hasText("Labour")).performScrollTo().performClick()
        compose.onNode(hasClickAction() and hasText("Open Work log")).assertExists()
    }
}
