package com.farmos.app

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.HerdRegisterRow
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Rendered Reports (FOS-REPORT-001) and Export (FOS-REPORT-011), owner decision D-026 (R10). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class ReportsScreensTest {
    @get:Rule(order = 0) val nativeReview = NativeModuleReviewRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    private val rows = listOf(
        HerdRegisterRow("s1", "sheep", "SH-1", "Dora", "FEMALE", "active", null, null, 42_500),
        HerdRegisterRow("s2", "sheep", "SH-2", null, "MALE", "active", null, null, null),
        HerdRegisterRow("s3", "sheep", "SH-3", null, "FEMALE", "sold", null, null, null),
    )

    @Test
    fun eachMetricShowsHowItIsWorkedOutAndAPartialFigureSaysSo() {
        compose.setContent {
            NativeModuleReviewTheme() {
                ReportsScreen(herdMetrics(rows), failure = null, canExport = false, exporting = false, exportMessage = null, onExportHerdRegister = {}, onBack = {})
            }
        }
        compose.onNodeWithTag("farm-screen:FOS-REPORT-001").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithTag("farm-section:Farm figures").performScrollTo().performClick()
        compose.onNodeWithTag("report-metric:active-sheep").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithText("2 animals").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithTag("report-calculation:active-sheep").performScrollTo().performClick()
        compose.onNodeWithText("How: Count of sheep records whose status is active").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithText("42.5 kg").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithText("Partial: 1 of 2 record(s) have no value").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithText("Exports are made by farm management.").assertExists().also { compose.captureObservedNativeOwners() }
        assertTrue(compose.onAllNodes(hasText("Export records")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun managementOpensTheExportSheetAndExportsTheHerdRegister() {
        var exports = 0
        compose.setContent {
            NativeModuleReviewTheme() {
                ReportsScreen(herdMetrics(rows), failure = null, canExport = true, exporting = false, exportMessage = "Herd register exported: 3 animal(s).", onExportHerdRegister = { exports++ }, onExportMoney = { exports++ }, onExportSummary = { exports++ }, onBack = {})
            }
        }
        compose.onNodeWithTag("report-open-export").performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-REPORT-011").assertExists().also { compose.captureObservedNativeOwners() }
        compose.onNodeWithTag("report-export-herd-register").performScrollTo().performClick()
        compose.onNodeWithTag("report-export-money").performScrollTo().performClick()
        compose.onNodeWithTag("report-export-summary").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(3, exports) }
        compose.onNodeWithText("Herd register exported: 3 animal(s).").assertExists().also { compose.captureObservedNativeOwners() }
    }

    @Test
    fun anEmptyFarmIsShownTruthfully() {
        compose.setContent {
            NativeModuleReviewTheme() {
                ReportsScreen(emptyList(), failure = null, canExport = true, exporting = false, exportMessage = null, onExportHerdRegister = {}, onBack = {})
            }
        }
        compose.onNodeWithText("No report figures are recorded on this device yet.").assertExists().also { compose.captureObservedNativeOwners() }
    }
}
