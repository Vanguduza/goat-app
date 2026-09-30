package com.farmos.app

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.feature.ops.AnimalSaleScreen
import com.farmos.feature.ops.SaleExitView
import com.farmos.feature.ops.SalesRecordNavigator
import com.farmos.feature.ops.SalesRecords
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Rendered Animal sale (FOS-SALES-007), owner decision D-022. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class AnimalSaleScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val exit = SaleExitView("x1", "g1", "GT-7 · goat", "Moyo Butchery", LocalDate.of(2026, 9, 20), "150.00")

    @Test
    fun aSoldAnimalsMoneyIsRecordedFromItsExitWithThePricePrefilled() {
        val recorded = mutableListOf<Pair<String, String>>()
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                AnimalSaleScreen(listOf(exit), "USD", customerSearch = null, busy = false, error = null, onRecord = { e, amount, _, _ -> recorded += e.exitId to amount }, onBack = {})
            }
        }
        compose.onNodeWithTag("farm-screen:FOS-SALES-007").assertExists()
        compose.onNodeWithText("Sold animals · 1 waiting").assertExists()
        compose.onNodeWithTag("animal-sale-exit:x1").performScrollTo().performClick()
        compose.onNodeWithTag("animal-sale-amount").performScrollTo().performTextReplacement("")
        compose.onNodeWithTag("animal-sale-record").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("animal-sale-amount").performScrollTo().performTextReplacement("145.50")
        compose.onNodeWithTag("animal-sale-record").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("x1" to "145.50"), recorded) }
    }

    @Test
    fun theSalesHomeOpensAnimalSale() {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                SalesRecordNavigator(
                    SalesRecords(),
                    animalSale = { back -> AnimalSaleScreen(emptyList(), "USD", null, false, null, { _, _, _, _ -> }, back) },
                ) { actions -> androidx.compose.foundation.layout.Column { actions() } }
            }
        }
        compose.onNode(hasClickAction() and hasText("Animal sale")).performClick()
        compose.onNodeWithText("No sold animals are waiting for their sale money.").assertExists()
    }
}
