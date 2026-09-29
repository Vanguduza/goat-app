package com.farmos.app

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
import com.farmos.feature.ops.CattleMilkRow
import com.farmos.feature.ops.CattleObservationRow
import com.farmos.feature.ops.CattleOperationsActions
import com.farmos.feature.ops.CattleOperationsScreen
import com.farmos.feature.ops.CattleRecords
import com.farmos.feature.ops.CattleSccRow
import com.farmos.feature.ops.CattleTimelineRow
import com.farmos.feature.ops.CattleWithdrawalRow
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rendered destination traversal for the read-only cattle animal record pages: each opens from
 * the cattle operations home, loads the selected animal's records, and Back restores the home.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class CattleRecordRuntimeNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private val homeTag = "farm-screen:" + "FOS-" + "CATTLE-" + "018"
    private val today = LocalDate.of(2026, 9, 24)
    private fun day(m: Int, d: Int) = LocalDate.of(2026, m, d).toEpochDay()

    @Test
    fun animalRecordPagesTraverseFromCattleHomeAndRestoreIt() {
        val loaded = AtomicReference<String?>(null)
        render("cow-daisy") { id -> loaded.set(id); records() }

        traverse("Lactation history", "FOS-CATTLE-020") {
            compose.onNodeWithText("58.25 L").assertExists()
            compose.onNodeWithTag("cattle-milk:m2").assertExists()
        }
        compose.runOnIdle { assertEquals("cow-daisy", loaded.get()) }
        traverse("SCC history", "FOS-CATTLE-022") {
            compose.onNodeWithText("180,000 cells/mL · DIM 60").assertExists()
        }
        traverse("Health summary", "FOS-CATTLE-033") {
            compose.onNodeWithTag("cattle-active-withdrawal").assertExists()
            compose.onNodeWithText("milk · Penicillin · until 2026-09-27").assertExists()
            compose.onNodeWithText("Red flag").assertExists()
            compose.onNodeWithText("3.0 (5-point)").assertExists()
        }
        traverse("Timeline", "FOS-CATTLE-035") {
            compose.onNodeWithText("Events · 3").assertExists()
            compose.onNodeWithText("2026-09-20 · Calving").assertExists()
        }
    }

    @Test
    fun withoutASelectedAnimalRecordPagesSaySoAndLoadNothing() {
        var loads = 0
        render(null) { loads++; records() }
        traverse("Lactation history", "FOS-CATTLE-020") {
            compose.onNodeWithText("Select an animal from the cattle herd first.").assertExists()
        }
        compose.runOnIdle { assertEquals(0, loads) }
    }

    @Test
    fun emptyAndFailedLoadsAreDistinct() {
        render("cow-daisy") { CattleRecords() }
        traverse("SCC history", "FOS-CATTLE-022") {
            compose.onNodeWithText("No SCC results recorded for this animal on this device.").assertExists()
        }
    }

    @Test
    fun failedLoadIsShownNotEmpty() {
        render("cow-daisy") { error("records unavailable") }
        traverse("Timeline", "FOS-CATTLE-035") {
            compose.onNodeWithText("records unavailable").assertExists()
            compose.onNodeWithText("No records for this animal on this device.").assertDoesNotExist()
        }
    }

    private fun traverse(label: String, screenId: String, assertions: () -> Unit) {
        compose.onNode(hasClickAction() and hasText(label)).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:$screenId").assertExists()
        compose.waitForIdle()
        assertions()
        compose.onNode(hasClickAction() and hasText("Farm home")).performScrollTo().performClick()
        compose.onNodeWithTag(homeTag).assertExists()
        compose.onNodeWithTag("farm-screen:$screenId").assertDoesNotExist()
    }

    private fun records() = CattleRecords(
        milk = listOf(CattleMilkRow("m2", day(9, 21), 29_250), CattleMilkRow("m1", day(9, 20), 29_000)),
        scc = listOf(CattleSccRow("s1", day(9, 21), 180_000, 60)),
        withdrawals = listOf(
            CattleWithdrawalRow("w1", "Penicillin", "milk", day(9, 27)),
            CattleWithdrawalRow("w0", "Penicillin", "meat", day(9, 1)),
        ),
        observations = listOf(CattleObservationRow("o1", day(9, 19), "Hot quarter", true)),
        treatmentCount = 1,
        latestBcs = "3.0 (5-point)",
        timeline = listOf(
            CattleTimelineRow("m2", day(9, 21), "Milk", "29.25 L"),
            CattleTimelineRow("c1", day(9, 20), "Calving", "1 born · 1 live · 0 dead"),
            CattleTimelineRow("o1", day(9, 19), "Observation", "Red flag · Hot quarter"),
        ),
    )

    private fun render(selected: String?, load: suspend (String) -> CattleRecords) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                CattleOperationsScreen(
                    selectedAnimalId = selected,
                    busy = false,
                    error = null,
                    actions = CattleOperationsActions(
                        onService = { _, _, _ -> },
                        onPd = { _, _, _ -> },
                        onCalving = { _, _, _, _, _ -> },
                        onBcs = { _, _, _, _ -> },
                        onMilk = { _, _, _ -> },
                        onLocomotion = { _, _, _ -> },
                        onScc = { _, _, _, _ -> },
                        onDryOff = { _, _, _ -> },
                        onWeaning = { _, _, _ -> },
                        onIdentifier = { _, _, _, _ -> },
                        onMovement = { _, _, _, _, _ -> },
                        onPedigree = { _, _, _ -> },
                        onPlaceLot = { _, _, _ -> },
                        onDaysOnFeed = { _, _, _ -> },
                        onCloseLot = { _, _, _, _, _ -> },
                    ),
                    onBack = {},
                    loadRecords = load,
                    today = today,
                )
            }
        }
        compose.onNodeWithTag(homeTag).assertExists()
    }
}
