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
import com.farmos.feature.ops.InventoryItemView
import com.farmos.feature.ops.InventoryLotView
import com.farmos.feature.ops.InventoryMovementView
import com.farmos.feature.ops.InventoryReadModel
import com.farmos.feature.ops.InventoryScreen
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rendered destination traversal for the read-only inventory record pages. Each page opens from a
 * real dashboard or drill-down action, renders its exact Screen ID with local records, and Back
 * restores the page it came from.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class InventoryRecordRuntimeNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private val dashboardTag = "farm-screen:" + "FOS-" + "INV-" + "001"
    private val today = LocalDate.of(2026, 9, 24)

    @Test
    fun dashboardRecordPagesTraverseAndRestoreTheDashboard() {
        render()
        traverseFromDashboard("Low stock", "FOS-INV-011") {
            compose.onNodeWithText("DW-01 · Dewormer").assertExists()
            compose.onNodeWithText("1.5 L on hand").assertExists()
            compose.onNodeWithText("Reorder point 2 L").assertExists()
            compose.onNodeWithText("1 item(s) have no reorder point.").assertExists()
            compose.onNodeWithText("FD-02 · Goat pellets").assertDoesNotExist()
        }
        traverseFromDashboard("Expiry queue", "FOS-INV-010") {
            compose.onNodeWithText("Expired 2026-09-01").assertExists()
            compose.onNodeWithText("Expires 2027-01-31").assertExists()
        }
        traverseFromDashboard("Movement history", "FOS-INV-014") {
            compose.onNodeWithText("Issued · FD-02 · Goat pellets").assertExists()
            compose.onNodeWithText("2026-09-20 08:30").assertExists()
        }
        traverseFromDashboard("Search inventory", "FOS-INV-017") {
            compose.onNodeWithText("Enter a SKU or item name.").assertExists()
        }
    }

    @Test
    fun drillDownsOpenItemAndLotRecordsAndReturnToTheirOrigin() {
        render()
        compose.onNode(hasClickAction() and hasText("Low stock")).performScrollTo().performClick()
        compose.onNodeWithTag("inventory-low-item:dw").performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-INV-003").assertExists()
        compose.onNodeWithText("At or below its reorder point").assertExists()
        compose.onNodeWithTag("inventory-item-movement:mv-2").assertExists()

        compose.onNodeWithTag("inventory-item-lot:lot-old").performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-INV-009").assertExists()
        compose.onNodeWithText("Expired 2026-09-01").assertExists()
        compose.onNodeWithText("0.75 L").assertExists()

        back()
        compose.onNodeWithTag("farm-screen:FOS-INV-003").assertExists()
        back()
        compose.onNodeWithTag("farm-screen:FOS-INV-011").assertExists()
        back()
        compose.onNodeWithTag(dashboardTag).assertExists()

        compose.onNode(hasClickAction() and hasText("Expiry queue")).performScrollTo().performClick()
        compose.onNodeWithTag("inventory-expiry-lot:lot-new").performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-INV-009").assertExists()
        back()
        compose.onNodeWithTag("farm-screen:FOS-INV-010").assertExists()
    }

    @Test
    fun searchMatchesSkuOrNameAndOpensTheItem() {
        render()
        compose.onNode(hasClickAction() and hasText("Search inventory")).performScrollTo().performClick()
        compose.onNodeWithTag("inventory-search-field").performTextInput("pellet")
        compose.onNodeWithText("1 match(es)").assertExists()
        compose.onNodeWithTag("inventory-search-item:fd").performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-INV-003").assertExists()
        compose.onNodeWithText("Not set").assertExists()
        back()
        compose.onNodeWithTag("farm-screen:FOS-INV-017").assertExists()
    }

    private fun back() {
        compose.onNode(hasClickAction() and hasText("Farm home")).performScrollTo().performClick()
    }

    private fun traverseFromDashboard(label: String, screenId: String, assertions: () -> Unit) {
        compose.onNode(hasClickAction() and hasText(label)).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:$screenId").assertExists()
        assertions()
        back()
        compose.onNodeWithTag(dashboardTag).assertExists()
        compose.onNodeWithTag("farm-screen:$screenId").assertDoesNotExist()
    }

    private fun render() {
        val dewormer = InventoryItemView("dw", "DW-01", "Dewormer", "L", 1_500, 2_000)
        val pellets = InventoryItemView("fd", "FD-02", "Goat pellets", "kg", 40_000, 0)
        val model = InventoryReadModel(
            items = listOf(dewormer, pellets),
            openLots = listOf(
                InventoryLotView("lot-old", "dw", "DW-01 · Dewormer", "A17", LocalDate.of(2026, 9, 1).toEpochDay(), 750, "L"),
                InventoryLotView("lot-new", "dw", "DW-01 · Dewormer", "B02", LocalDate.of(2027, 1, 31).toEpochDay(), 750, "L"),
            ),
            movements = listOf(
                InventoryMovementView("mv-1", "fd", "FD-02 · Goat pellets", "issue", 5_000, "kg", 1_789_893_000_000L),
                InventoryMovementView("mv-2", "dw", "DW-01 · Dewormer", "receive", 1_500, "L", 1_790_000_000_000L),
            ),
            movementLimit = 200,
        )
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                InventoryScreen(
                    rows = emptyList(),
                    busy = false,
                    error = null,
                    onCreate = { _, _, _ -> },
                    onMove = { _, _, _ -> },
                    onBack = {},
                    readModel = model,
                    today = today,
                    zone = ZoneOffset.UTC,
                )
            }
        }
        compose.onNodeWithTag(dashboardTag).assertExists()
    }
}
