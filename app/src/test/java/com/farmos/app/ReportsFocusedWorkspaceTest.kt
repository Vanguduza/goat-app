package com.farmos.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.domain.ops.MetricDefinition
import com.farmos.domain.ops.MetricResult
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** FOS-REPORT-001/011: navigation before detail, truthful missing values and retained query. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class ReportsFocusedWorkspaceTest {
    @get:Rule(order = 0) val nativeReview = NativeModuleReviewRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test fun reportActionsAreReachableBeforeAnyMetricDetailsEvenForManyMetrics() {
        var chosen: ReportDetail? = null
        render((1..120).map { metric("metric-$it", "Recorded figure $it") }, onOpen = { chosen = it })
        compose.onNodeWithTag("farm-tools-group:Animals").assertIsDisplayed()
        compose.onNodeWithTag("report-metric:metric-1").assertDoesNotExist()
        compose.captureModuleReview("FOS-REPORT-001")
        compose.revealFarmTool("Animal report").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(ReportDetail.Animal, chosen) }
    }

    @Test fun wholeFigureSearchFindsLateItemsAndSurvivesExportBackWithoutWriting() {
        var exports = 0
        val rows = (1..120).map { metric("figure-$it", if (it == 120) "Late hay" else "Recorded figure $it") }
        render(rows, onExport = { exports++ })
        figures()
        compose.onNodeWithTag("report-figures-query").performScrollTo().performTextInput("Late hay")
        compose.onNodeWithTag("report-metric:figure-120").assertTextEquals("3 animals")
        compose.onNodeWithTag("report-metric:figure-1").assertDoesNotExist()
        compose.captureModuleReview("FOS-REPORT-001")
        compose.onNodeWithTag("report-open-export").performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-REPORT-011").assertExists()
        compose.revealFarmTool("Reports").performScrollTo().performClick()
        compose.onNodeWithTag("farm-section:Farm figures").assertIsSelected()
        compose.onNodeWithTag("report-figures-query").assertTextContains("Late hay")
        compose.onNodeWithTag("report-metric:figure-120").assertTextEquals("3 animals")
        compose.runOnIdle { assertEquals(0, exports) }
    }

    @Test fun incompleteMetricKeepsItsWarningVisibleBeforeCalculationExpansion() {
        render(listOf(metric("weight-goat", "Goat live weight", "kg", 45500, 1, 2)))
        figures()
        compose.onNodeWithText("Partial: 2 of 3 record(s) have no value").assertExists()
        compose.onNodeWithTag("report-metric:weight-goat").assertTextEquals("45.5 kg")
        compose.onNodeWithText("How: Sum of recorded values").assertDoesNotExist()
        compose.onNodeWithTag("report-calculation:weight-goat").performScrollTo().performClick()
        compose.onNodeWithText("How: Sum of recorded values").assertExists()
        compose.onNodeWithText("Scope: This farm only").assertExists()
        compose.captureModuleReview("FOS-REPORT-001")
    }

    @Test fun noRecordedValuesAreNotPresentedAsZeroInScreenOrSharedSummary() {
        val unknown = metric("unknown-weight", "Goat live weight", "kg", 0, 0, 3)
        assertEquals("Not recorded (kg)", metricValueText(unknown))
        val text = buildFarmSummaryText(listOf(unknown), java.time.LocalDate.of(2026, 10, 24))
        assertTrue(text.contains("Not recorded (kg)"))
        assertTrue(text.contains("Partial: 3 of 3 record(s) have no value"))
        assertFalse(text.contains("0.0 kg"))
        render(listOf(unknown))
        figures()
        compose.onNodeWithTag("report-metric:unknown-weight").assertTextEquals("Not recorded (kg)")
        compose.captureModuleReview("FOS-REPORT-001")
    }

    @Test fun failedOrLoadingFiguresDoNotBlockReportNavigationOrInventZeros() {
        val failed = mutableStateOf<String?>(null)
        var chosen: ReportDetail? = null
        compose.setContent { NativeModuleReviewTheme {
            ReportsScreen(null, failed.value, false, false, null, {}, onOpenDetail = { chosen = it }, onBack = {})
        } }
        compose.onNodeWithTag("farm-tools-group:Animals").assertIsDisplayed()
        compose.onNodeWithText("Reading farm records").assertExists()
        compose.runOnIdle { failed.value = "Records could not be read" }
        compose.onNodeWithText("Records could not be read").assertExists()
        compose.onNodeWithText("No report figures are recorded on this device yet.").assertDoesNotExist()
        compose.revealFarmTool("Herd or flock report").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(ReportDetail.Herd, chosen) }
        compose.onNodeWithTag("report-open-export").assertDoesNotExist()
        compose.revealFarmTool("Generated documents").assertExists()
        compose.onNodeWithTag("report-open:FOS-REPORT-014").assertDoesNotExist()
    }

    @Test fun emptyFiguresAreNotMistakenForAnEmptyAnimalRegister() {
        render(emptyList())
        compose.onNodeWithText("No report figures are recorded on this device yet.").assertExists()
        compose.onNodeWithText("No animals are recorded on this device yet.").assertDoesNotExist()
        compose.revealFarmTool("Production report").performScrollTo().assertIsDisplayed()
        compose.captureModuleReview("FOS-REPORT-001")
    }

    private fun metric(id: String, name: String, unit: String = "animals", value: Long = 3, included: Int = 3, missing: Int = 0) =
        MetricResult(MetricDefinition(id, name, "Sum of recorded values", unit, "All recorded dates", "This farm only"), value, included, missing)

    private fun figures() = compose.onNodeWithTag("farm-section:Farm figures").performScrollTo().performClick()
    private fun render(metrics: List<MetricResult>, onOpen: (ReportDetail) -> Unit = {}, onExport: () -> Unit = {}) {
        compose.setContent { NativeModuleReviewTheme {
            ReportsScreen(metrics, null, true, false, null, onExport, onOpenDetail = onOpen, onBack = {})
        } }
    }
}
