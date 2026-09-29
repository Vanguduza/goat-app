package com.farmos.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.feature.ops.FeedIssueView
import com.farmos.feature.ops.FeedItemView
import com.farmos.feature.ops.FeedRecordNavigator
import com.farmos.feature.ops.FeedRecords
import com.farmos.feature.ops.SimpleCaptureScreen
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rendered destination traversal for the read-only feed record pages: each opens from the feed
 * home's record actions, renders its exact Screen ID, and Back restores the home.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class FeedRecordRuntimeNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private val homeTag = "farm-screen:" + "FOS-" + "FEED-" + "001"
    private fun day(m: Int, d: Int) = LocalDate.of(2026, m, d).toEpochDay()

    @Test
    fun feedRecordPagesTraverseFromHomeAndRestoreIt() {
        render(records())
        traverse("Feed item", "FOS-FEED-003") {
            compose.onNodeWithText("1812.5 kg").assertExists()
            compose.onNodeWithText("640").assertExists()
            compose.onNodeWithText("2026-01-04 and 2026-09-21").assertExists()
            compose.onNodeWithText("310 kg").assertExists()
            compose.onNodeWithText("Issues · latest 2 of 640").assertExists()
            compose.onNodeWithTag("feed-item-issue:f2").assertExists()
            compose.onNodeWithTag("feed-option:hay").performScrollTo().performClick()
            compose.onNode(hasTestTag("feed-item-on-hand") and hasAnyDescendant(hasText("Item not on this device"))).assertExists()
            compose.onNodeWithText("Issues · 1").assertExists()
        }
        traverse("Feed issues", "FOS-FEED-006") {
            compose.onNodeWithText("1812.5 kg · 640 issues").assertExists()
            compose.onNodeWithText("40 (unit not on this device) · 1 issue").assertExists()
            compose.onNodeWithText("Issues · latest 3 of 641").assertExists()
            compose.onNodeWithText("2026-09-21 · Weaner does").assertExists()
            compose.onNodeWithText("2026-09-20 · No group recorded").assertExists()
            compose.onNodeWithText("2026-09-19 · Group not on this device").assertExists()
        }
    }

    @Test
    fun emptyRecordsSayNothingIsRecorded() {
        render(FeedRecords())
        traverse("Feed item", "FOS-FEED-003") {
            compose.onNodeWithText("No feed issued on this device.").assertExists()
        }
        traverse("Feed issues", "FOS-FEED-006") {
            compose.onNodeWithText("No feed issued on this device.").assertExists()
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

    private fun records() = FeedRecords(
        issues = listOf(
            FeedIssueView("f2", "mash", "Grower mash", "12.5 kg", "Weaner does", day(9, 21)),
            FeedIssueView("f1", "mash", "Grower mash", "3 kg", "No group recorded", day(9, 20)),
            FeedIssueView("f0", "hay", "Item not on this device", "40 (unit not on this device)", "Group not on this device", day(9, 19)),
        ),
        issueCount = 641,
        items = listOf(
            FeedItemView("mash", "Grower mash", "1812.5 kg", 640, day(1, 4), day(9, 21), "310 kg"),
            FeedItemView("hay", "Item not on this device", "40 (unit not on this device)", 1, day(9, 19), day(9, 19), null),
        ),
    )

    private fun render(records: FeedRecords) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                FeedRecordNavigator(records) { recordActions ->
                    SimpleCaptureScreen(
                        screenId = "FOS-" + "FEED-" + "001",
                        title = "Feed",
                        help = "Feed home",
                        empty = "No feed issues on this device.",
                        rows = emptyList(),
                        busy = false,
                        error = null,
                        fields = listOf("Item id" to mutableStateOf("")),
                        actionLabel = "Issue feed",
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
