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
import com.farmos.feature.ops.CustomerScreens
import com.farmos.feature.ops.CustomerView
import com.farmos.feature.ops.SalesRecordNavigator
import com.farmos.feature.ops.SalesRecords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Rendered Customer list (FOS-SALES-002) and Customer detail (FOS-SALES-003). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class CustomerScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val customers = listOf(
        CustomerView("c1", "Moyo Butchery", "+263 77 123 4567", active = true),
        CustomerView("c2", "Rudo Market", null, active = false),
    )

    @Test
    fun staffAddAndUpdateCustomersWithoutDeletingThem() {
        val added = mutableListOf<Pair<String, String?>>()
        val updated = mutableListOf<Triple<String, String, String>>()
        val active = mutableListOf<Pair<String, Boolean>>()
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                CustomerScreens(customers, canManage = true, busy = false, error = null, onAdd = { n, p -> added += n to p }, onUpdate = { i, n, p -> updated += Triple(i, n, p) }, onSetActive = { i, a -> active += i to a }, onBack = {})
            }
        }
        compose.onNodeWithTag("farm-screen:FOS-SALES-002").assertExists()
        compose.onNodeWithText("Customers · 1 active").assertExists()
        compose.onNodeWithText("Inactive").assertExists()
        compose.onNodeWithTag("customer-add-name").performScrollTo().performTextInput("Chari Meats")
        compose.onNodeWithTag("customer-add").performScrollTo().performClick()
        compose.onNodeWithTag("customer:c1").performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-SALES-003").assertExists()
        compose.onNodeWithText("Phone +263 77 123 4567").assertExists()
        compose.onNodeWithTag("customer-edit-phone").performScrollTo().performTextReplacement("")
        compose.onNodeWithTag("customer-save").performScrollTo().performClick()
        compose.onNodeWithTag("customer-set-active").performScrollTo().performClick()
        assertTrue(compose.onAllNodes(hasText("Delete", substring = true)).fetchSemanticsNodes().isEmpty())
        compose.runOnIdle {
            assertEquals(listOf("Chari Meats" to null), added)
            assertEquals(listOf(Triple("c1", "Moyo Butchery", "")), updated)
            assertEquals(listOf("c1" to false), active)
        }
    }

    @Test
    fun aViewerSeesCustomersButCannotChangeThem() {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                CustomerScreens(customers, canManage = false, busy = false, error = null, onAdd = { _, _ -> }, onUpdate = { _, _, _ -> }, onSetActive = { _, _ -> }, onBack = {})
            }
        }
        compose.onNodeWithText("Customers are kept by farm staff").assertExists()
        assertTrue(compose.onAllNodes(hasText("Add customer")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun theSalesHomeOpensCustomersAndReturns() {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                SalesRecordNavigator(
                    SalesRecords(),
                    customers = { back -> CustomerScreens(customers, false, false, null, { _, _ -> }, { _, _, _ -> }, { _, _ -> }, back) },
                ) { actions -> androidx.compose.foundation.layout.Column { actions() } }
            }
        }
        compose.onNode(hasClickAction() and hasText("Customers")).performClick()
        compose.onNodeWithTag("farm-screen:FOS-SALES-002").assertExists()
        compose.onNode(hasClickAction() and hasText("Sales")).performScrollTo().performClick()
        compose.onNode(hasClickAction() and hasText("Customers")).assertExists()
    }
}
