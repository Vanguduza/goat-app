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
import com.farmos.feature.ops.GrazingSessionView
import com.farmos.feature.ops.PaddockView
import com.farmos.feature.ops.PastureRecordNavigator
import com.farmos.feature.ops.PastureRecords
import com.farmos.feature.ops.SimpleCaptureScreen
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rendered destination traversal for the read-only pasture record pages: each opens from the
 * pasture home's record actions, renders its exact Screen ID, and Back restores the home.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class PastureRecordRuntimeNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private val homeTag = "farm-screen:" + "FOS-" + "PASTURE-" + "001"
    private fun day(m: Int, d: Int) = LocalDate.of(2026, m, d).toEpochDay()

    @Test
    fun pastureRecordPagesTraverseFromHomeAndRestoreIt() {
        render(records())
        traverse("Paddock list", "FOS-PASTURE-002") {
            compose.onNodeWithText("Grazing now · 42 head recorded at entry").assertExists()
            compose.onNodeWithText("Not grazed now · last exit 2026-09-10").assertExists()
            compose.onNodeWithText("P3 · Hill · inactive").assertExists()
            compose.onNodeWithText("No grazing recorded").assertExists()
        }
        traverse("Paddock detail", "FOS-PASTURE-003") {
            compose.onNodeWithText("12000 m²").assertExists()
            compose.onNodeWithText("Grazing sessions · latest 1 of 530").assertExists()
            compose.onNodeWithText("2026-09-20 to open").assertExists()
            compose.onNodeWithTag("paddock-option:p3").performScrollTo().performClick()
            compose.onNodeWithText("Not recorded").assertExists()
            compose.onNodeWithText("No grazing recorded for this paddock.").assertExists()
        }
        traverse("Grazing history", "FOS-PASTURE-009") {
            compose.onNodeWithText("Grazing sessions · latest 2 of 531").assertExists()
            compose.onNodeWithText("2026-09-01 to 2026-09-10 · P2 · River").assertExists()
            compose.onNodeWithText("Group not on this device · sheep · 30 head at entry").assertExists()
        }
    }

    @Test
    fun emptyRecordsSayNothingIsRecorded() {
        render(PastureRecords())
        traverse("Paddock list", "FOS-PASTURE-002") {
            compose.onNodeWithText("No paddocks on this device.").assertExists()
        }
        traverse("Grazing history", "FOS-PASTURE-009") {
            compose.onNodeWithText("No grazing recorded on this device.").assertExists()
        }
    }

    private fun traverse(label: String, screenId: String, assertions: () -> Unit) {
        compose.onNode(hasClickAction() and hasText("Open $label")).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:$screenId").assertExists()
        assertions()
        compose.onNode(hasClickAction() and hasText("Farm home")).performScrollTo().performClick()
        compose.onNodeWithTag(homeTag).assertExists()
        compose.onNodeWithTag("farm-screen:$screenId").assertDoesNotExist()
    }

    private fun records() = PastureRecords(
        paddocks = listOf(
            PaddockView("p1", "P1", "Flat", 12_000, "trough", true, true, 530, 1, 42, day(9, 20), day(9, 1)),
            PaddockView("p2", "P2", "River", null, "river", false, true, 1, 0, 0, day(9, 1), day(9, 10)),
            PaddockView("p3", "P3", "Hill", null, "none", false, false, 0, 0, 0, null, null),
        ),
        sessions = listOf(
            GrazingSessionView("g2", "p1", "P1 · Flat", "Does A", "goat", 42, day(9, 20), null),
            GrazingSessionView("g1", "p2", "P2 · River", "Group not on this device", "sheep", 30, day(9, 1), day(9, 10)),
        ),
        sessionCount = 531,
    )

    private fun render(records: PastureRecords) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                PastureRecordNavigator(records) { recordActions ->
                    SimpleCaptureScreen(
                        screenId = "FOS-" + "PASTURE-" + "001",
                        title = "Pasture",
                        help = "Pasture home",
                        empty = "No paddocks on this device.",
                        rows = emptyList(),
                        busy = false,
                        error = null,
                        fields = listOf("Code" to mutableStateOf("")),
                        actionLabel = "Create paddock",
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
