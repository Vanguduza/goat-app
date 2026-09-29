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
import com.farmos.feature.ops.SimpleCaptureScreen
import com.farmos.feature.ops.WaterRecordNavigator
import com.farmos.feature.ops.WaterRecordView
import com.farmos.feature.ops.WaterRecords
import com.farmos.feature.ops.WaterSourceTotalView
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rendered destination traversal for the read-only water history: it opens from the water home's
 * record action, renders its exact Screen ID, and Back restores the home.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class WaterRecordRuntimeNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private val homeTag = "farm-screen:" + "FOS-" + "WATER-" + "001"
    private fun day(m: Int, d: Int) = LocalDate.of(2026, m, d).toEpochDay()

    @Test
    fun waterHistoryTraversesFromHomeAndRestoresIt() {
        render(
            WaterRecords(
                records = listOf(WaterRecordView("w2", "trough", 250_500, day(9, 21)), WaterRecordView("w1", "borehole", 1_000_000, day(9, 20))),
                recordCount = 612,
                sources = listOf(
                    WaterSourceTotalView("borehole", 1_000_000, 1, day(9, 20), day(9, 20)),
                    WaterSourceTotalView("trough", 150_300_000, 611, day(1, 2), day(9, 21)),
                ),
            ),
        )
        traverse("FOS-WATER-009") {
            compose.onNodeWithText("150300 L · 611 records · 2026-01-02 to 2026-09-21").assertExists()
            compose.onNodeWithText("1000 L · 1 record · 2026-09-20 to 2026-09-20").assertExists()
            compose.onNodeWithText("Records · latest 2 of 612").assertExists()
            compose.onNodeWithText("250.5 L").assertExists()
        }
    }

    @Test
    fun emptyRecordsSayNothingIsRecorded() {
        render(WaterRecords())
        traverse("FOS-WATER-009") {
            compose.onNodeWithText("No water recorded on this device.").assertExists()
        }
    }

    private fun traverse(screenId: String, assertions: () -> Unit) {
        compose.onNode(hasClickAction() and hasText("Open Water history")).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:$screenId").assertExists()
        assertions()
        compose.onNode(hasClickAction() and hasText("Farm home")).performScrollTo().performClick()
        compose.onNodeWithTag(homeTag).assertExists()
        compose.onNodeWithTag("farm-screen:$screenId").assertDoesNotExist()
    }

    private fun render(records: WaterRecords) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                WaterRecordNavigator(records) { recordActions ->
                    SimpleCaptureScreen(
                        screenId = "FOS-" + "WATER-" + "001",
                        title = "Water",
                        help = "Water home",
                        empty = "No water records on this device.",
                        rows = emptyList(),
                        busy = false,
                        error = null,
                        fields = listOf("Source" to mutableStateOf("")),
                        actionLabel = "Record water",
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
