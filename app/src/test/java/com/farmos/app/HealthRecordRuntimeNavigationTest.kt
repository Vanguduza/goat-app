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
import com.farmos.feature.ops.FormularyItemView
import com.farmos.feature.ops.HealthLabResultView
import com.farmos.feature.ops.HealthObservationScreen
import com.farmos.feature.ops.HealthReadModel
import com.farmos.feature.ops.HealthTimeline
import com.farmos.feature.ops.HealthTimelineEntry
import com.farmos.feature.ops.HealthTimelineKind
import com.farmos.feature.ops.HealthTreatmentView
import com.farmos.feature.ops.HealthVetVisitView
import com.farmos.feature.ops.HealthWithdrawalView
import com.farmos.feature.ops.ProtocolPackView
import com.farmos.feature.ops.ProtocolSlotView
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rendered destination traversal for read-only health records. Pages open from the Health
 * dashboard or a record drill-down, render their exact Screen ID, and Back restores the origin.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class HealthRecordRuntimeNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private val dashboardTag = "farm-screen:" + "FOS-" + "HEALTH-" + "001"
    private val today = LocalDate.of(2026, 9, 24)
    private fun millis(day: LocalDate) = day.toEpochDay() * 86_400_000L

    @Test
    fun treatmentRecordsDrillIntoWindowsAndBackToTheirOrigin() {
        render()
        open("Treatment records")
        compose.onNodeWithTag("farm-screen:FOS-HEALTH-006").assertExists()
        compose.onNodeWithText("Treatments · 2").assertExists()

        compose.onNodeWithTag("health-treatment:t1").performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-HEALTH-008").assertExists()
        compose.onNodeWithText("GT-024 · Nala").assertExists()
        compose.onNodeWithText("Meat 28 day(s) · Milk 7 day(s)").assertExists()
        compose.onNodeWithText("Active until 2026-09-30").assertExists()
        compose.onNodeWithText("Ended 2026-09-20").assertExists()

        compose.onNodeWithTag("health-treatment-window:t1:milk").performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-HEALTH-010").assertExists()
        compose.onNodeWithText("Active milk withdrawal").assertExists()

        back()
        compose.onNodeWithTag("farm-screen:FOS-HEALTH-008").assertExists()
        back()
        compose.onNodeWithTag("farm-screen:FOS-HEALTH-006").assertExists()
        back()
        compose.onNodeWithTag(dashboardTag).assertExists()
    }

    @Test
    fun endedWindowShowsNoActiveWarningAndLinksToItsTreatment() {
        render()
        open("Treatment records")
        compose.onNodeWithTag("health-treatment:t1").performScrollTo().performClick()
        compose.onNodeWithTag("health-treatment-window:t1:meat-old").performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-HEALTH-010").assertExists()
        compose.onNodeWithText("Active meat withdrawal").assertDoesNotExist()
        compose.onNodeWithText("Ended").assertExists()
        compose.onNodeWithTag("health-withdrawal-source").performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-HEALTH-008").assertExists()
    }

    @Test
    fun vetVisitsAndLabResultsTraverseListToDetailAndBack() {
        render()
        open("Vet visits")
        compose.onNodeWithTag("farm-screen:FOS-HEALTH-020").assertExists()
        compose.onNodeWithTag("health-vet-visit:v1").performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-HEALTH-022").assertExists()
        compose.onNodeWithTag("health-vet-visit-vet").assertExists()
        compose.onNodeWithText("Herd check").assertExists()
        compose.onNodeWithText("goat (no individual animal)").assertExists()
        back()
        compose.onNodeWithTag("farm-screen:FOS-HEALTH-020").assertExists()
        back()
        compose.onNodeWithTag(dashboardTag).assertExists()

        open("Lab results")
        compose.onNodeWithTag("farm-screen:FOS-HEALTH-023").assertExists()
        compose.onNodeWithTag("health-lab-result:l1").performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-HEALTH-025").assertExists()
        compose.onNodeWithText("Negative for CAE").assertExists()
        compose.onNodeWithText("350,000 cells/mL").assertExists()
        back()
        compose.onNodeWithTag("farm-screen:FOS-HEALTH-023").assertExists()
    }

    @Test
    fun dashboardShowsExhaustiveCountsAndBoundedListsSaySo() {
        render(model().copy(observationCount = 12, treatmentCount = 230, activeWithdrawalCount = 61, vetVisitCount = 1, labResultCount = 1))
        compose.onNodeWithText("230").assertExists()
        compose.onNodeWithText("61").assertExists()
        compose.onNodeWithText("12").assertExists()
        compose.onNodeWithText("Active withdrawals").assertExists()
        open("Treatment records")
        compose.onNodeWithText("Treatments · latest 2 of 230").assertExists()
    }

    @Test
    fun formularyItemsAndProtocolPacksShowRecordedFactsOnly() {
        render(
            model().copy(
                formulary = listOf(
                    FormularyItemView("f1", "Oxytet LA", "goat", "antibiotic", 28, 7, null, vetApproved = true, treatmentCount = 12),
                    FormularyItemView("f2", "Old drench", "sheep", "anthelmintic", null, null, null, vetApproved = false, treatmentCount = 0),
                ),
                packs = listOf(
                    ProtocolPackView(
                        "pk1",
                        "Kid health",
                        "goat",
                        "accepted",
                        "Dr Moyo",
                        listOf(ProtocolSlotView("s1", "CDT1", "First CDT", 28, "birth", isCore = true)),
                        applicationCount = 3,
                    ),
                ),
            ),
        )
        open("Formulary items")
        compose.onNodeWithTag("farm-screen:FOS-HEALTH-014").assertExists()
        compose.onNodeWithText("28 day(s)").assertExists()
        compose.onNodeWithText("12").assertExists()
        compose.onNodeWithTag("health-reference-option:f2").performScrollTo().performClick()
        compose.onNodeWithText("Not vet approved").assertExists()
        back()
        open("Protocol pack detail")
        compose.onNodeWithTag("farm-screen:FOS-HEALTH-016").assertExists()
        compose.onNodeWithText("CDT1 · day 28 from birth · core").assertExists()
        compose.onNodeWithText("3").assertExists()
        back()
        compose.onNodeWithTag("farm-screen:FOS-HEALTH-016").assertDoesNotExist()
    }

    @Test
    fun healthTimelineListsTheCompleteWindowAndRestoresTheDashboard() {
        render(
            model().copy(
                timeline = HealthTimeline(
                    entries = listOf(
                        HealthTimelineEntry(HealthTimelineKind.TREATMENT, "t1", LocalDate.of(2026, 9, 23).toEpochDay(), "GT-024 · Nala", "goat", "Oxytet LA · Foot abscess"),
                        HealthTimelineEntry(HealthTimelineKind.OBSERVATION, "o1", LocalDate.of(2026, 9, 18).toEpochDay(), null, "sheep", "Lame, near hind · Red flag"),
                    ),
                    completeFromEpochDay = LocalDate.of(2026, 9, 1).toEpochDay(),
                    totalCount = 431,
                ),
            ),
        )
        open("Health timeline")
        compose.onNodeWithTag("farm-screen:FOS-HEALTH-029").assertExists()
        compose.onNodeWithText("Every record from 2026-09-01 onward is listed. Earlier records are not shown here.").assertExists()
        compose.onNodeWithText("Records · latest 2 of 431").assertExists()
        compose.onNodeWithTag("health-timeline:treatment:t1").assertExists()
        compose.onNodeWithText("2026-09-23 · Treatment").assertExists()
        compose.onNodeWithText("Oxytet LA · Foot abscess").assertExists()
        compose.onNodeWithText("sheep (no individual animal)").assertExists()
        compose.onNodeWithText("Lame, near hind · Red flag").assertExists()
        back()
        compose.onNodeWithTag(dashboardTag).assertExists()
        compose.onNodeWithTag("farm-screen:FOS-HEALTH-029").assertDoesNotExist()
    }

    @Test
    fun emptyHealthTimelineSaysNothingIsRecorded() {
        render(HealthReadModel())
        open("Health timeline")
        compose.onNodeWithTag("farm-screen:FOS-HEALTH-029").assertExists()
        compose.onNodeWithText("No health records on this device.").assertExists()
    }

    @Test
    fun emptyRecordsSayNothingIsRecorded() {
        render(HealthReadModel())
        open("Treatment records")
        compose.onNodeWithText("No treatments recorded on this device.").assertExists()
        back()
        open("Lab results")
        compose.onNodeWithText("No lab results recorded on this device.").assertExists()
    }

    private fun open(label: String) {
        compose.onNode(hasClickAction() and hasText(label)).performScrollTo().performClick()
    }

    private fun back() {
        compose.onNode(hasClickAction() and hasText("Farm home")).performScrollTo().performClick()
    }

    private fun model() = HealthReadModel(
        treatments = listOf(
            HealthTreatmentView("t1", "goat", "GT-024 · Nala", "Oxytet LA", "Foot abscess", 28, 7, null, millis(LocalDate.of(2026, 9, 23))),
            HealthTreatmentView("t2", "sheep", null, null, "Flystrike", null, null, null, millis(LocalDate.of(2026, 9, 10))),
        ),
        withdrawals = listOf(
            HealthWithdrawalView("t1:milk", "t1", "Oxytet LA", "milk", LocalDate.of(2026, 9, 30).toEpochDay()),
            HealthWithdrawalView("t1:meat-old", "t1", "Oxytet LA", "meat", LocalDate.of(2026, 9, 20).toEpochDay()),
        ),
        vetVisits = listOf(HealthVetVisitView("v1", "goat", null, "Herd check", "Dr Moyo", LocalDate.of(2026, 9, 15).toEpochDay())),
        labResults = listOf(HealthLabResultView("l1", "GT-024 · Nala", "CAE ELISA", "Negative for CAE", 350_000, LocalDate.of(2026, 9, 12).toEpochDay())),
    )

    private fun render(readModel: HealthReadModel = model()) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                HealthObservationScreen(
                    rows = emptyList(),
                    catalog = emptyList(),
                    treatments = emptyList(),
                    busy = false,
                    error = null,
                    onRecord = { _, _, _, _ -> },
                    onCreateFormulary = { _, _, _, _ -> },
                    onRecordTreatment = { _, _, _ -> },
                    onBack = {},
                    readModel = readModel,
                    today = today,
                    zone = ZoneOffset.UTC,
                )
            }
        }
        compose.onNodeWithTag(dashboardTag).assertExists()
    }
}
