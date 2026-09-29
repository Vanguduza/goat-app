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
import com.farmos.feature.ops.SaleCurrencyTotalView
import com.farmos.feature.ops.SaleView
import com.farmos.feature.ops.SalesRecordNavigator
import com.farmos.feature.ops.SalesRecords
import com.farmos.feature.ops.SimpleCaptureScreen
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rendered destination traversal for the read-only sales record pages: each opens from the sales
 * home's record actions, renders its exact Screen ID, and Back restores the home.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class SalesRecordRuntimeNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private val homeTag = "farm-screen:" + "FOS-" + "SALES-" + "001"
    private fun day(m: Int, d: Int) = LocalDate.of(2026, m, d).toEpochDay()

    @Test
    fun salesRecordPagesTraverseFromHomeAndRestoreIt() {
        render(records())
        traverse("Sales history", "FOS-SALES-011") {
            compose.onNodeWithText("2500000 USD minor units · 730 sales").assertExists()
            compose.onNodeWithText("4000 KES minor units · 1 sale").assertExists()
            compose.onNodeWithText("Sales · latest 2 of 731").assertExists()
            compose.onNodeWithText("quantity 2.5 · 4000 KES minor units").assertExists()
        }
        traverse("Sale detail", "FOS-SALES-005") {
            compose.onNodeWithText("live_goat").assertExists()
            compose.onNodeWithText("1500 USD minor units").assertExists()
            compose.onNodeWithTag("sale-option:s1").performScrollTo().performClick()
            compose.onNodeWithText("2.5").assertExists()
        }
    }

    @Test
    fun emptyRecordsSayNothingIsRecorded() {
        render(SalesRecords())
        traverse("Sales history", "FOS-SALES-011") {
            compose.onNodeWithText("No sales recorded on this device.").assertExists()
        }
        traverse("Sale detail", "FOS-SALES-005") {
            compose.onNodeWithText("No sales recorded on this device.").assertExists()
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

    private fun records() = SalesRecords(
        sales = listOf(
            SaleView("s2", "live_goat", "1", 1_500, "USD", day(9, 21)),
            SaleView("s1", "milk", "2.5", 4_000, "KES", day(9, 20)),
        ),
        saleCount = 731,
        totals = listOf(SaleCurrencyTotalView("KES", 4_000, 1), SaleCurrencyTotalView("USD", 2_500_000, 730)),
    )

    private fun render(records: SalesRecords) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                SalesRecordNavigator(records) { recordActions ->
                    SimpleCaptureScreen(
                        screenId = "FOS-" + "SALES-" + "001",
                        title = "Sales",
                        help = "Sales home",
                        empty = "No sales on this device.",
                        rows = emptyList(),
                        busy = false,
                        error = null,
                        fields = listOf("Item kind" to mutableStateOf("")),
                        actionLabel = "Record sale",
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
