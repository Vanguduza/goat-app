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
import com.farmos.feature.rabbit.RabbitCommerceRecordNavigator
import com.farmos.feature.rabbit.RabbitCommerceRecords
import com.farmos.feature.rabbit.RabbitContractView
import com.farmos.feature.rabbit.RabbitMarketPlanView
import com.farmos.feature.rabbit.RabbitReservationView
import com.farmos.feature.rabbit.RabbitRetentionView
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rendered destination traversal for the read-only rabbit commerce record pages: each opens from
 * the waitlist home's record actions, renders its exact Screen ID, and Back restores the home.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class RabbitCommerceRecordRuntimeNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private val homeTag = "farm-screen:" + "FOS-" + "RABBIT-" + "027"
    private fun day(m: Int, d: Int) = LocalDate.of(2026, m, d).toEpochDay()

    @Test
    fun commerceRecordPagesTraverseFromTheWaitlistHomeAndRestoreIt() {
        render(records())
        traverse("Retention decisions", "FOS-RABBIT-024") {
            compose.onNodeWithTag("rabbit-retention-count:sale_pet").assertExists()
            compose.onNodeWithText("2026-09-12 · RB-7").assertExists()
            compose.onNodeWithTag("rabbit-retention:r2").assertExists()
            compose.onNodeWithTag("rabbit-retention-count:keep_breeder").assertExists()
            compose.onNodeWithText("2026-09-10 · Kit not on this device").assertExists()
        }
        traverse("Market plans", "FOS-RABBIT-026") {
            compose.onNodeWithText("K-3 · Meat · active").assertExists()
            compose.onNodeWithText("Target 2400 g by 2026-11-01").assertExists()
            compose.onNodeWithText("Wave mated 2026-08-01 · Pet sale · active").assertExists()
        }
        traverse("Reservations", "FOS-RABBIT-028") {
            compose.onNodeWithText("Thandi · fulfilled").assertExists()
            compose.onNodeWithText("Quantity 1 · sex female · matched K-3").assertExists()
            compose.onNodeWithText("Quantity 2 · sex not recorded").assertExists()
            compose.onNodeWithTag("rabbit-reservation-count:open").assertExists()
        }
        traverse("Contracts", "FOS-RABBIT-029") {
            compose.onNodeWithText("9500 USD minor units · 2 contracts").assertExists()
            compose.onNodeWithText("40000 ZAR minor units · 1 contract").assertExists()
            compose.onNodeWithText("2026-09-20 · Thandi · agreed").assertExists()
            compose.onNodeWithText("4500 USD minor units · reservation Thandi · animal RB-7").assertExists()
        }
    }

    @Test
    fun emptyRecordsSayNothingIsRecorded() {
        render(RabbitCommerceRecords())
        traverse("Retention decisions", "FOS-RABBIT-024") {
            compose.onNodeWithText("No retention decisions recorded on this device.").assertExists()
        }
        traverse("Market plans", "FOS-RABBIT-026") {
            compose.onNodeWithText("No market plans recorded on this device.").assertExists()
        }
        traverse("Reservations", "FOS-RABBIT-028") {
            compose.onNodeWithText("No reservations recorded on this device.").assertExists()
        }
        traverse("Contracts", "FOS-RABBIT-029") {
            compose.onNodeWithText("No contracts recorded on this device.").assertExists()
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

    private fun records() = RabbitCommerceRecords(
        reservations = listOf(
            RabbitReservationView("w1", "Thandi", "female", 1, "fulfilled", "K-3"),
            RabbitReservationView("w2", "Sipho", null, 2, "open", null),
        ),
        contracts = listOf(
            RabbitContractView("c3", "Thandi", "Thandi", "RB-7", 4_500, "USD", "agreed", day(9, 20)),
            RabbitContractView("c2", "Market stall", null, null, 5_000, "USD", "agreed", day(9, 15)),
            RabbitContractView("c1", "Butchery", null, null, 40_000, "ZAR", "agreed", day(9, 1)),
        ),
        retention = listOf(
            RabbitRetentionView("r2", "RB-7", "keep_breeder", day(9, 12)),
            RabbitRetentionView("r1", "Kit not on this device", "sale_pet", day(9, 10)),
        ),
        plans = listOf(
            RabbitMarketPlanView("p1", "K-3", 2_400, day(11, 1), "meat", "active"),
            RabbitMarketPlanView("p2", "Wave mated 2026-08-01", 1_800, day(11, 15), "pet_sale", "active"),
        ),
    )

    private fun render(records: RabbitCommerceRecords) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                RabbitCommerceRecordNavigator(records) { recordActions ->
                    SimpleCaptureScreen(
                        screenId = "FOS-" + "RABBIT-" + "027",
                        title = "Rabbit waitlist",
                        help = "Waitlist home",
                        empty = "No waitlist rows on this device.",
                        rows = emptyList(),
                        busy = false,
                        error = null,
                        fields = listOf("Buyer" to mutableStateOf("")),
                        actionLabel = "Enqueue waitlist",
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
