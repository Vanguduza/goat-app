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
import com.farmos.feature.ops.GroupCensusView
import com.farmos.feature.ops.GroupFeedView
import com.farmos.feature.ops.GroupGrazingView
import com.farmos.feature.ops.GroupRecordNavigator
import com.farmos.feature.ops.GroupRecords
import com.farmos.feature.ops.GroupView
import com.farmos.feature.ops.SimpleCaptureScreen
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rendered destination traversal for the read-only group record pages: each opens from the groups
 * home's record actions, loads the selected group's records, renders its exact Screen ID, and Back
 * restores the home.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class GroupRecordRuntimeNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private val homeTag = "farm-screen:" + "FOS-" + "GROUP-" + "001"
    private fun day(m: Int, d: Int) = LocalDate.of(2026, m, d).toEpochDay()
    private val groups = listOf(GroupView("g1", "Does A", "goat", 42), GroupView("g2", "Wethers", "sheep", 10))

    @Test
    fun groupRecordPagesTraverseFromHomeAndRestoreIt() {
        val loaded = mutableListOf<String>()
        render(groups) { id -> loaded += id; if (id == "g1") records() else GroupRecords() }
        traverse("Group detail", "FOS-GROUP-002") {
            compose.onNodeWithText("42").assertExists()
            compose.onNodeWithText("40 head · 2026-09-15").assertExists()
            compose.onNodeWithText("P1 · Flat since 2026-09-20").assertExists()
            compose.onNodeWithTag("group-option:g2").performScrollTo().performClick()
            compose.waitForIdle()
            compose.onNodeWithText("No census recorded").assertExists()
            compose.onNodeWithText("No open grazing recorded").assertExists()
        }
        compose.runOnIdle { assertEquals(listOf("g1", "g2"), loaded) }
        traverse("Group census", "FOS-GROUP-006") {
            compose.onNodeWithText("Census records · 2").assertExists()
            compose.onNodeWithTag("group-census:c1").assertExists()
        }
        traverse("Group timeline", "FOS-GROUP-009") {
            compose.onNodeWithText("Events · 6").assertExists()
            compose.onNodeWithText("2026-09-20 · Entered paddock").assertExists()
            compose.onNodeWithText("2026-09-10 · Left paddock").assertExists()
            compose.onNodeWithText("Layer mash · 12.5 kg").assertExists()
        }
    }

    @Test
    fun withoutGroupsPagesSaySo() {
        render(emptyList()) { GroupRecords() }
        traverse("Group census", "FOS-GROUP-006") {
            compose.onNodeWithText("No groups on this device.").assertExists()
        }
    }

    @Test
    fun failedLoadIsShownNotEmpty() {
        render(groups) { error("records unavailable") }
        traverse("Group timeline", "FOS-GROUP-009") {
            compose.onNodeWithText("records unavailable").assertExists()
            compose.onNodeWithText("No records for this group on this device.").assertDoesNotExist()
        }
    }

    private fun traverse(label: String, screenId: String, assertions: () -> Unit) {
        compose.onNode(hasClickAction() and hasText("Open $label")).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:$screenId").assertExists()
        compose.waitForIdle()
        assertions()
        compose.onNode(hasClickAction() and hasText("Farm home")).performScrollTo().performClick()
        compose.onNodeWithTag(homeTag).assertExists()
        compose.onNodeWithTag("farm-screen:$screenId").assertDoesNotExist()
    }

    private fun records() = GroupRecords(
        census = listOf(GroupCensusView("c2", 40, day(9, 15)), GroupCensusView("c1", 44, day(8, 1))),
        grazing = listOf(
            GroupGrazingView("s2", "P1 · Flat", 40, day(9, 20), null),
            GroupGrazingView("s1", "P2 · River", 44, day(9, 1), day(9, 10)),
        ),
        feed = listOf(GroupFeedView("f1", "Layer mash", "12.5 kg", day(9, 12))),
    )

    private fun render(groups: List<GroupView>, load: suspend (String) -> GroupRecords) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                GroupRecordNavigator(groups, load) { recordActions ->
                    SimpleCaptureScreen(
                        screenId = "FOS-" + "GROUP-" + "001",
                        title = "Groups",
                        help = "Groups home",
                        empty = "No groups on this device.",
                        rows = emptyList(),
                        busy = false,
                        error = null,
                        fields = listOf("Name" to mutableStateOf("")),
                        actionLabel = "Create group",
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
