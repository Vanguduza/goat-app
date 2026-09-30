package com.farmos.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.feature.ops.InventoryScreen
import com.farmos.feature.ops.StockAdjustmentScreen
import com.farmos.feature.ops.StockCountItem
import com.farmos.feature.ops.StockCountLineView
import com.farmos.feature.ops.StockCountScreen
import com.farmos.feature.ops.StockCountView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Rendered stock count (FOS-INV-016) and adjustment review (FOS-INV-015), owner decision D-021. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class StockCountScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val items = listOf(StockCountItem("mash", "Layer mash · MASH", "kg", 10_000), StockCountItem("salt", "Salt lick · SALT", "each", 4_000))

    private fun counting(lines: List<StockCountLineView> = emptyList()) = StockCountView("c1", "COUNTING", "2026-10-01", null, null, null, lines)

    private fun submitted() = StockCountView(
        "c2", "SUBMITTED", "2026-10-01", "2026-10-02", null, null,
        listOf(
            StockCountLineView("mash", "Layer mash · MASH", "kg", 10_000, 12_500, "worker-1"),
            StockCountLineView("salt", "Salt lick · SALT", "each", 4_000, 4_000, "worker-1"),
        ),
    )

    private fun assertAbsent(text: String) {
        compose.waitForIdle()
        assertTrue("Expected '$text' to be absent", compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aWorkerCountsBlindAndSubmitsForReview() {
        val recorded = mutableListOf<Triple<String, String, Long>>()
        val submittedIds = mutableListOf<String>()
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                StockCountScreen(
                    counts = listOf(counting(listOf(StockCountLineView("salt", "Salt lick · SALT", "each", 4_000, 3_000, "worker-1")))),
                    items = items,
                    canCount = true,
                    busy = false,
                    error = null,
                    onStart = {},
                    onRecord = { c, i, q -> recorded += Triple(c, i, q) },
                    onSubmit = { submittedIds += it },
                    onOpenReview = {},
                    onBack = {},
                )
            }
        }
        compose.onNodeWithTag("farm-screen:FOS-INV-016").assertExists()
        // Blind count: on-hand figures are never shown while counting.
        assertAbsent("on hand")
        assertAbsent("10 kg")
        assertAbsent("4 each")
        compose.onNodeWithText("Counted 3 each by worker-1").assertExists()
        compose.onNodeWithTag("stock-count-qty:mash").performScrollTo().performTextInput("12.5")
        compose.onNodeWithTag("stock-count-record:mash").performScrollTo().performClick()
        compose.onNodeWithTag("stock-count-submit").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(Triple("c1", "mash", 12_500L)), recorded)
            assertEquals(listOf("c1"), submittedIds)
        }
    }

    @Test
    fun aViewerCanNeitherStartNorRecordACount() {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                StockCountScreen(emptyList(), items, canCount = false, busy = false, error = null, onStart = {}, onRecord = { _, _, _ -> }, onSubmit = {}, onOpenReview = {}, onBack = {})
            }
        }
        compose.onNodeWithText("Stock counts are recorded by farm staff").assertExists()
        assertAbsent("Start a stock count")
    }

    @Test
    fun managementReviewsDifferencesAndPostsOrRejects() {
        val posted = mutableListOf<String>()
        val rejected = mutableListOf<Pair<String, String>>()
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                StockAdjustmentScreen(listOf(submitted()), canPost = true, busy = false, error = null, onPost = { posted += it }, onReject = { c, r -> rejected += c to r }, onBack = {})
            }
        }
        compose.onNodeWithTag("farm-screen:FOS-INV-015").assertExists()
        compose.onNodeWithText("2 item(s) counted · 1 with a difference").assertExists()
        compose.onNodeWithText("Layer mash · MASH: on hand 10 · counted 12.5 · difference +2.5 kg").assertExists()
        compose.onNodeWithText("Salt lick · SALT: on hand 4 · counted 4 · no difference").assertExists()
        compose.onNodeWithTag("stock-review-post:c2").performScrollTo().performClick()
        compose.onNodeWithTag("stock-review-reason:c2").performScrollTo().performTextInput("Recount the second shed")
        compose.onNodeWithTag("stock-review-reject:c2").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf("c2"), posted)
            assertEquals(listOf("c2" to "Recount the second shed"), rejected)
        }
    }

    @Test
    fun aWorkerSeesTheReviewButCannotPost() {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                StockAdjustmentScreen(listOf(submitted()), canPost = false, busy = false, error = null, onPost = {}, onReject = { _, _ -> }, onBack = {})
            }
        }
        compose.onNodeWithText("Adjustments are posted by farm management").assertExists()
        assertAbsent("Post 1 adjustment")
        assertAbsent("Reject count")
    }

    @Test
    fun theInventoryDashboardOpensStockCount() {
        var opened = 0
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                InventoryScreen(rows = emptyList(), busy = false, error = null, onCreate = { _, _, _ -> }, onMove = { _, _, _ -> }, onBack = {}, onOpenStockCount = { opened++ })
            }
        }
        compose.onNode(hasClickAction() and hasText("Count stock and review differences")).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }
}
