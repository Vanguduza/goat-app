package com.farmos.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.FarmDateField
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Date selection remains an uncommitted draft until Use date, with the actual user text scale. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h900dp-mdpi")
class DatePickerAdaptiveReviewTest {
    @get:Rule(order = 0) val nativeReview = NativeModuleReviewRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val id = "farm-atom:FOS-ATOM-001:adaptive"

    @Test fun dialogRetainsTheUsersFontScaleAndAllFullyVisibleTouchTargets() {
        val date = mutableStateOf("2026-10-24")
        var writes = 0
        compose.setContent { NativeModuleReviewTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FarmDateField("Event date", date.value, { date.value = it; writes++ }, key = "adaptive", today = LocalDate.of(2026, 10, 24))
            }
        } }
        val field = compose.onNodeWithTag(id).fetchSemanticsNode()
        val expectedScale = field.layoutInfo.density.fontScale
        compose.onNodeWithTag("$id:open").performScrollTo().performClick()
        val dialog = compose.onNodeWithTag("$id:calendar").fetchSemanticsNode()
        assertEquals("Dialog may not reset enlarged text to the activity default", expectedScale, dialog.layoutInfo.density.fontScale, 0.001f)
        val minimum = if (System.getenv("GOAT_MODULE_REVIEW_MODE") == "OUTDOOR") 64f else 48f
        val root = generateSequence(dialog) { it.parent }.last()
        val controls = compose.onAllNodes(hasClickAction() and SemanticsMatcher("dialog window") {
            generateSequence(it) { n -> n.parent }.any { n -> n.id == root.id }
        }).fetchSemanticsNodes()
        assertTrue(controls.isNotEmpty())
        controls.forEach { node ->
            if (node.boundsInRoot.height + 0.5f >= node.layoutInfo.height && node.boundsInRoot.width + 0.5f >= node.layoutInfo.width) {
                val size = node.touchBoundsInRoot
                val density = node.layoutInfo.density.density
                assertTrue("Dialog touch target below minimum: ${node.config}", size.width / density + 0.5f >= minimum && size.height / density + 0.5f >= minimum)
            }
        }
        compose.runOnIdle { assertEquals(0, writes) }
        compose.captureObservedNativeOwners()
        compose.onNodeWithTag("$id:cancel").scrollWhenAvailable().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("2026-10-24", date.value); assertEquals(0, writes) }
    }

    @Test fun explicitDateConfirmationPreservesTheEnteredDayAndDoesNotSubmitFarmWork() {
        val date = mutableStateOf("2024-02-29")
        var changed = 0
        compose.setContent { NativeModuleReviewTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FarmDateField("Event date", date.value, { date.value = it; changed++ }, key = "adaptive", today = LocalDate.of(2026, 10, 24))
            }
        } }
        compose.onNodeWithTag("$id:open").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, changed) }
        compose.onNodeWithTag("$id:confirm").scrollWhenAvailable().assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("2024-02-29", date.value); assertEquals(1, changed) }
        compose.onNodeWithTag("$id:calendar").assertDoesNotExist()
    }
    private fun SemanticsNodeInteraction.scrollWhenAvailable(): SemanticsNodeInteraction {
        if (generateSequence(fetchSemanticsNode().parent) { it.parent }.any { it.config.contains(SemanticsActions.ScrollBy) }) performScrollTo()
        return this
    }

}
