package com.farmos.app

import androidx.compose.runtime.mutableStateOf
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
import com.farmos.feature.ops.LabourEntryView
import com.farmos.feature.ops.LabourRecordNavigator
import com.farmos.feature.ops.LabourRecords
import com.farmos.feature.ops.LabourWorkerTotalView
import com.farmos.feature.ops.SimpleCaptureScreen
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rendered destination traversal for the read-only work log: it opens from the labour home's
 * record action, renders its exact Screen ID, and Back restores the home.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class LabourRecordRuntimeNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private val homeTag = "farm-screen:" + "FOS-" + "LABOUR-" + "001"
    private fun day(m: Int, d: Int) = LocalDate.of(2026, m, d).toEpochDay()

    @Test
    fun workLogTraversesFromHomeAndRestoresIt() {
        render(
            LabourRecords(
                entries = listOf(
                    LabourEntryView("e2", "Tendai", "MILK", 95, "parlour", day(9, 21)),
                    LabourEntryView("e1", "Rudo", "CHECK", 30, null, day(9, 20)),
                ),
                entryCount = 640,
                workers = listOf(LabourWorkerTotalView("Rudo", 30, 1, day(9, 20)), LabourWorkerTotalView("Tendai", 38_415, 639, day(9, 21))),
            ),
        )
        traverse("FOS-LABOUR-005") {
            compose.onNodeWithText("640 h 15 min · 639 entries · latest 2026-09-21").assertExists()
            compose.onNodeWithText("30 min · 1 entry · latest 2026-09-20").assertExists()
            compose.onNodeWithText("Entries · latest 2 of 640").assertExists()
            compose.onNodeWithText("MILK · 1 h 35 min · parlour").assertExists()
        }
    }

    @Test
    fun emptyRecordsSayNothingIsRecorded() {
        render(LabourRecords())
        traverse("FOS-LABOUR-005") {
            compose.onNodeWithText("No labour recorded on this device.").assertExists()
        }
    }

    private fun traverse(screenId: String, assertions: () -> Unit) {
        compose.onNode(hasClickAction() and hasText("Open Work log")).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:$screenId").assertExists()
        assertions()
        compose.onNode(hasClickAction() and hasText("Farm home")).performScrollTo().performClick()
        compose.onNodeWithTag(homeTag).assertExists()
        compose.onNodeWithTag("farm-screen:$screenId").assertDoesNotExist()
    }

    private fun render(records: LabourRecords) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                LabourRecordNavigator(records) { recordActions ->
                    SimpleCaptureScreen(
                        screenId = "FOS-" + "LABOUR-" + "001",
                        title = "Labour",
                        help = "Labour home",
                        empty = "No labour entries on this device.",
                        rows = emptyList(),
                        busy = false,
                        error = null,
                        fields = listOf("Worker" to mutableStateOf("")),
                        actionLabel = "Record labour",
                        onSubmit = {},
                        onBack = {},
                        extra = { recordActions() },
                    )
                }
            }
        }
        compose.onNodeWithTag(homeTag).assertExists()
    }
}
