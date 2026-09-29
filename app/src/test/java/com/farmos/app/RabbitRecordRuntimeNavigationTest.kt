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
import com.farmos.feature.rabbit.RabbitCageView
import com.farmos.feature.rabbit.RabbitKitView
import com.farmos.feature.rabbit.RabbitNestBoxView
import com.farmos.feature.rabbit.RabbitProgrammeScreen
import com.farmos.feature.rabbit.RabbitRecords
import com.farmos.feature.rabbit.RabbitWaveEventView
import com.farmos.feature.rabbit.RabbitWaveView
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rendered destination traversal for the read-only rabbitry record pages: each opens from the
 * rabbitry dashboard action, renders its exact Screen ID with local records, and Back restores
 * the dashboard.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class RabbitRecordRuntimeNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 9, 24)
    private fun day(m: Int, d: Int) = LocalDate.of(2026, m, d).toEpochDay()

    @Test
    fun rabbitRecordPagesTraverseFromTheDashboardAndRestoreIt() {
        render(records())
        traverse("Cage occupancy", "FOS-RABBIT-008") {
            compose.onNodeWithText("Cage A1 · capacity 12 does").assertExists()
            compose.onNodeWithText("1 of 2 nest boxes available · 2 waves · latest 10 does mated 2026-09-10").assertExists()
            compose.onNodeWithTag("rabbit-cage:c2").assertExists()
        }
        traverse("Cage detail", "FOS-RABBIT-007") {
            compose.onNodeWithTag("rabbit-cage-box:b2").assertExists()
            compose.onNodeWithTag("rabbit-cage-wave:w1").assertExists()
            compose.onNodeWithTag("rabbit-option:c2").performScrollTo().performClick()
            compose.onNodeWithText("No nest boxes recorded for this cage.").assertExists()
            compose.onNodeWithText("No breeding waves recorded for this cage.").assertExists()
        }
        traverse("Kindling due", "FOS-RABBIT-016") {
            compose.onNodeWithTag("rabbit-kindling-past").assertExists()
            compose.onNodeWithText("Kindling 2026-09-20 · 4 days past scheduled date").assertExists()
            compose.onNodeWithText("Kindling 2026-10-11 · in 17 days").assertExists()
            compose.onNodeWithTag("rabbit-kindling-due:w0").assertDoesNotExist()
        }
        traverse("Litter profiles", "FOS-RABBIT-018") {
            compose.onNodeWithTag("rabbit-option:w0").performScrollTo().performClick()
            compose.onNodeWithText("2026-07-31").assertExists()
            compose.onNodeWithText("8 live · 1 dead").assertExists()
            compose.onNodeWithTag("rabbit-litter-event:f1:out").assertExists()
            compose.onNodeWithTag("rabbit-litter-kits:weaned").assertExists()
        }
        traverse("Kit census", "FOS-RABBIT-019") {
            compose.onNodeWithTag("rabbit-kits-total").assertExists()
            compose.onNodeWithText("weaned · retain · tag R-17").assertExists()
            compose.onNodeWithText("Wave not on this device · 1").assertExists()
        }
    }

    @Test
    fun emptyRecordsSayNothingIsRecorded() {
        render(RabbitRecords())
        traverse("Cage occupancy", "FOS-RABBIT-008") {
            compose.onNodeWithText("No cages on this device.").assertExists()
        }
        traverse("Kindling due", "FOS-RABBIT-016") {
            compose.onNodeWithText("No waves awaiting a kindling record on this device.").assertExists()
        }
        traverse("Litter profiles", "FOS-RABBIT-018") {
            compose.onNodeWithText("No breeding waves on this device.").assertExists()
        }
        traverse("Kit census", "FOS-RABBIT-019") {
            compose.onNodeWithText("No individual kits recorded on this device.").assertExists()
        }
    }

    private fun traverse(label: String, screenId: String, assertions: () -> Unit) {
        compose.onNode(hasClickAction() and hasText("Open $label")).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:$screenId").assertExists()
        assertions()
        compose.onNode(hasClickAction() and hasText("Farm home")).performScrollTo().performClick()
        compose.onNode(hasClickAction() and hasText("Open $label")).assertExists()
        compose.onNodeWithTag("farm-screen:$screenId").assertDoesNotExist()
    }

    private fun wave(id: String, mating: Long, kindling: Long, events: List<RabbitWaveEventView>, kindled: Boolean) =
        RabbitWaveView(id, "A1", if (id == "w2") 10 else 11, mating, mating + 28, kindling, kindling + 14, kindling + 11, kindling + 35, events, kindled)

    private fun records() = RabbitRecords(
        cages = listOf(
            RabbitCageView("c1", "A1", 12, listOf(RabbitNestBoxView("b1", "N1", "available"), RabbitNestBoxView("b2", "N2", "in_cage")), listOf("w2", "w1")),
            RabbitCageView("c2", "B1", 6, emptyList(), emptyList()),
        ),
        waves = listOf(
            wave("w2", day(9, 10), day(10, 11), emptyList(), kindled = false),
            wave("w1", day(8, 20), day(9, 20), emptyList(), kindled = false),
            wave(
                "w0",
                day(7, 1),
                day(7, 31),
                listOf(
                    RabbitWaveEventView("f1:out", day(8, 2), "Foster out", "2 kits"),
                    RabbitWaveEventView("k1", day(7, 31), "Kindling", "8 live · 1 dead"),
                ),
                kindled = true,
            ),
        ),
        kits = listOf(
            RabbitKitView("kit1", "w0", "W0-1", "female", "weaned", "retain", "R-17"),
            RabbitKitView("kit2", "w0", "W0-2", "male", "in_litter", "undecided", null),
            RabbitKitView("kit3", "gone", "X-1", "male", "sold", "sell", null),
        ),
    )

    private fun render(records: RabbitRecords) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                RabbitProgrammeScreen(
                    cages = emptyList(),
                    waves = emptyList(),
                    availableBoxes = 0,
                    busy = false,
                    error = null,
                    does = emptyList(),
                    onRegisterDoe = { _, _, _ -> },
                    onCreateCage = {},
                    onCreateNestBox = { _, _ -> },
                    onCreateWave = { _, _, _ -> },
                    onPalpate = { _, _, _ -> },
                    onKindle = { _, _, _, _ -> },
                    onFoster = { _, _, _, _, _ -> },
                    onBack = {},
                    records = records,
                    today = today,
                )
            }
        }
    }
}
