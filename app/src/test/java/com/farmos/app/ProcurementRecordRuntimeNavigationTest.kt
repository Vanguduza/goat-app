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
import com.farmos.feature.ops.ProcurementCurrencyTotal
import com.farmos.feature.ops.ProcurementPurchaseView
import com.farmos.feature.ops.ProcurementRecordNavigator
import com.farmos.feature.ops.ProcurementRecords
import com.farmos.feature.ops.ProcurementSupplierView
import com.farmos.feature.ops.SimpleCaptureScreen
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rendered destination traversal for the read-only procurement record pages: each opens from the
 * procurement home's record actions, renders its exact Screen ID, and Back restores the home.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class ProcurementRecordRuntimeNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private val homeTag = "farm-screen:" + "FOS-" + "PROC-" + "001"
    private fun day(m: Int, d: Int) = LocalDate.of(2026, m, d).toEpochDay()

    @Test
    fun procurementRecordPagesTraverseFromHomeAndRestoreIt() {
        render(records())
        traverse("Supplier list", "FOS-PROC-002") {
            compose.onNodeWithText("Agri Supply · lead time 5 days").assertExists()
            compose.onNodeWithText("1250000 USD minor units · 612 purchases · 900 ZAR minor units · 1 purchase").assertExists()
            compose.onNodeWithText("No purchases recorded").assertExists()
        }
        traverse("Supplier detail", "FOS-PROC-003") {
            compose.onNodeWithText("613").assertExists()
            compose.onNodeWithText("Purchases · latest 2 of 613").assertExists()
            compose.onNodeWithTag("procurement-supplier-total:ZAR").assertExists()
            compose.onNodeWithTag("procurement-option:s2").performScrollTo().performClick()
            compose.onNodeWithText("No purchases recorded for this supplier.").assertExists()
        }
        traverse("Purchase detail", "FOS-PROC-005") {
            compose.onNodeWithText("12.5 kg").assertExists()
            compose.onNodeWithText("2500 USD minor units").assertExists()
            compose.onNodeWithTag("procurement-option:p2").performScrollTo().performClick()
            compose.onNodeWithText("Item not on this device").assertExists()
            compose.onNodeWithText("3 (unit not on this device)").assertExists()
        }
        traverse("Procurement history", "FOS-PROC-009") {
            compose.onNodeWithText("Purchases · latest 2 of 613").assertExists()
            compose.onNodeWithTag("procurement-purchase:p1").assertExists()
        }
    }

    @Test
    fun emptyRecordsSayNothingIsRecorded() {
        render(ProcurementRecords())
        traverse("Supplier list", "FOS-PROC-002") {
            compose.onNodeWithText("No suppliers on this device.").assertExists()
        }
        traverse("Procurement history", "FOS-PROC-009") {
            compose.onNodeWithText("No purchases recorded on this device.").assertExists()
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

    private fun records() = ProcurementRecords(
        suppliers = listOf(
            ProcurementSupplierView(
                "s1",
                "Agri Supply",
                5,
                listOf(ProcurementCurrencyTotal("USD", 1_250_000, 612), ProcurementCurrencyTotal("ZAR", 900, 1)),
                day(9, 20),
            ),
            ProcurementSupplierView("s2", "Feed Mill", 2, emptyList(), null),
        ),
        purchases = listOf(
            ProcurementPurchaseView("p1", "s1", "Agri Supply", "Layer mash", "12.5 kg", 2_500, "USD", day(9, 20)),
            ProcurementPurchaseView("p2", "s1", "Agri Supply", "Item not on this device", "3 (unit not on this device)", 900, "ZAR", day(9, 18)),
        ),
        purchaseCount = 613,
    )

    private fun render(records: ProcurementRecords) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                ProcurementRecordNavigator(records) { recordActions ->
                    SimpleCaptureScreen(
                        screenId = "FOS-" + "PROC-" + "001",
                        title = "Procurement",
                        help = "Procurement home",
                        empty = "No suppliers on this device.",
                        rows = emptyList(),
                        busy = false,
                        error = null,
                        fields = listOf("Supplier name" to mutableStateOf("")),
                        actionLabel = "Create supplier",
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
