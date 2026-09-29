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
import com.farmos.feature.ops.SheepAnimalRecords
import com.farmos.feature.ops.SheepOperationsActions
import com.farmos.feature.ops.SheepOperationsScreen
import com.farmos.feature.ops.SheepTimelineRow
import com.farmos.feature.ops.SheepWithdrawalRow
import com.farmos.feature.ops.SheepWoolRecords
import com.farmos.feature.ops.SheepWoolRow
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rendered destination traversal for the read-only sheep record pages: each opens from the
 * sheep operations home, loads the selected sheep's or the farm's records, and Back restores home.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class SheepRecordRuntimeNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private val homeTag = "farm-screen:" + "FOS-" + "SHEEP-" + "010"
    private val today = LocalDate.of(2026, 9, 24)
    private fun day(m: Int, d: Int) = LocalDate.of(2026, m, d).toEpochDay()

    @Test
    fun sheepRecordPagesTraverseFromTheHomeAndRestoreIt() {
        val loaded = AtomicReference<String?>(null)
        render("ewe-44", load = { id -> loaded.set(id); records() })

        traverse("Health summary", "FOS-SHEEP-025") {
            compose.onNodeWithTag("sheep-active-withdrawal").assertExists()
            compose.onNodeWithText("meat · Ivermectin · until 2026-10-08").assertExists()
            compose.onNodeWithText("Score 3 (breech)").assertExists()
        }
        compose.runOnIdle { assertEquals("ewe-44", loaded.get()) }
        traverse("Timeline", "FOS-SHEEP-031") {
            compose.onNodeWithText("Events · 2").assertExists()
            compose.onNodeWithText("2026-09-02 · Lambing").assertExists()
        }
        traverse("Wool dashboard", "FOS-SHEEP-018") {
            compose.onNodeWithText("7.3 kg").assertExists()
            compose.onNodeWithTag("sheep-wool-shearing:sh1").assertExists()
            compose.onNodeWithText("19.5 µm").assertExists()
        }
    }

    @Test
    fun animalPagesNeedASelectionButWoolDashboardDoesNot() {
        var loads = 0
        render(null, load = { loads++; records() })
        traverse("Timeline", "FOS-SHEEP-031") {
            compose.onNodeWithText("Select a sheep from the flock first.").assertExists()
        }
        traverse("Wool dashboard", "FOS-SHEEP-018") {
            compose.onNodeWithText("No wool records on this device.").assertExists()
        }
        compose.runOnIdle { assertEquals(0, loads) }
    }

    @Test
    fun noActiveWithdrawalIsStatedWithoutClaimingClearance() {
        render("ewe-44", load = { SheepAnimalRecords() })
        traverse("Health summary", "FOS-SHEEP-025") {
            compose.onNodeWithText("No active withdrawal windows recorded on this device.").assertExists()
            compose.onNodeWithTag("sheep-active-withdrawal").assertDoesNotExist()
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

    private fun records() = SheepAnimalRecords(
        withdrawals = listOf(
            SheepWithdrawalRow("w1", "Ivermectin", "meat", day(10, 8)),
            SheepWithdrawalRow("w0", "Ivermectin", "milk", day(9, 1)),
        ),
        treatmentCount = 1,
        observationCount = 2,
        latestFamacha = 3,
        latestFlystrike = "Score 3 (breech)",
        timeline = listOf(
            SheepTimelineRow("f1", day(9, 10), "Flystrike", "Score 3 · breech"),
            SheepTimelineRow("l1", day(9, 2), "Lambing", "2 born · 2 live · 0 dead"),
        ),
    )

    private fun wool() = SheepWoolRecords(
        clips = listOf(SheepWoolRow("c1", day(9, 5), "SH-044", "4.2 kg"), SheepWoolRow("c2", day(9, 5), "Mob m1", "3.1 kg")),
        clipGreasyGramsTotal = 7_300,
        shearing = listOf(SheepWoolRow("sh1", day(9, 5), "Mob m1", "shearing")),
        micron = listOf(SheepWoolRow("mi1", day(9, 6), "SH-044", "19.5 µm")),
    )

    private fun render(selected: String?, load: suspend (String) -> SheepAnimalRecords) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                SheepOperationsScreen(
                    selectedAnimalId = selected,
                    busy = false,
                    error = null,
                    actions = SheepOperationsActions(
                        onJoining = { _, _ -> },
                        onScan = { _, _, _ -> },
                        onLambing = { _, _, _, _, _ -> },
                        onMarking = { _, _, _, _ -> },
                        onWeaning = { _, _, _, _ -> },
                        onWool = { _, _, _, _ -> },
                        onShearing = { _, _, _, _, _ -> },
                        onMicron = { _, _, _, _ -> },
                        onFamacha = { _, _, _ -> },
                        onDag = { _, _, _ -> },
                        onFootrot = { _, _, _ -> },
                        onFlystrike = { _, _, _ -> },
                        onIdentifier = { _, _, _, _ -> },
                        onMovement = { _, _, _, _, _ -> },
                        onPedigree = { _, _, _ -> },
                    ),
                    onBack = {},
                    loadRecords = load,
                    loadWool = { if (selected == null) SheepWoolRecords() else wool() },
                    today = today,
                )
            }
        }
        compose.onNodeWithTag(homeTag).assertExists()
    }
}
