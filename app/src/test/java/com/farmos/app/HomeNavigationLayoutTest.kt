package com.farmos.app

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.AnimalFarmHomeBottomBar
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** FOS-HOME-012-A and FOS-HOME-012-D share this production navigation component. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h860dp-mdpi")
class HomeNavigationLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val labels = listOf("Home", "Animals", "Tasks", "More")

    @Test
    fun outdoorPhoneShowsCompleteNavigationAtTwoHundredPercent() {
        val clicked = renderBar(widthDp = 360)
        val targets = assertCompleteLabelsAndTargets()

        val firstTop = targets.getValue("Home").top
        targets.forEach { (label, bounds) ->
            assertEquals("$label should fit on the same row at 360dp", firstTop, bounds.top, 0.5f)
        }
        labels.forEach { label ->
            compose.onNode(hasClickAction() and hasText(label)).performClick()
        }
        compose.runOnIdle { assertEquals(labels, clicked) }
    }

    @Test
    fun narrowLargeTextWrapsWholeTargetsWithoutClipping() {
        renderBar(widthDp = 240)
        val targets = assertCompleteLabelsAndTargets()

        assertTrue(
            "When full-sized labels cannot share a row, later buttons must wrap",
            targets.getValue("More").top > targets.getValue("Home").top,
        )
    }

    private fun renderBar(widthDp: Int): List<String> {
        val clicked = mutableListOf<String>()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                FarmOsTheme(mode = AnimalFarmThemeMode.OUTDOOR) {
                    AnimalFarmHomeBottomBar(
                        onHome = { clicked += "Home" },
                        onAnimals = { clicked += "Animals" },
                        onTasks = { clicked += "Tasks" },
                        onMore = { clicked += "More" },
                        modifier = Modifier.width(widthDp.dp).testTag("home-navigation"),
                    )
                }
            }
        }
        return clicked
    }

    private fun assertCompleteLabelsAndTargets(): Map<String, Rect> {
        val bar = compose.onNodeWithTag("home-navigation").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val minimumTouch = with(compose.density) { 64.dp.toPx() }
        val targets = labels.associateWith { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            val textNode = compose.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
            textNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { readLayout ->
                assertTrue("Expected the actual rendered layout for $label", readLayout(layouts))
            }
            val layout = layouts.single()
            assertEquals(label, layout.layoutInput.text.text)
            assertEquals("User text scaling must be retained", 2f, layout.layoutInput.density.fontScale, 0f)
            assertEquals("$label must remain on one complete line", 1, layout.lineCount)
            assertEquals("$label must include its final character", label.length, layout.getLineEnd(0, visibleEnd = true))
            assertFalse("$label must not be ellipsized", layout.isLineEllipsized(0))
            assertFalse("$label must not be clipped vertically", layout.didOverflowHeight)
            val textBounds = textNode.fetchSemanticsNode().boundsInRoot
            val visibleWidth = minOf(layout.size.width.toFloat(), textBounds.width)
            val visibleHeight = minOf(layout.size.height.toFloat(), textBounds.height)
            // String semantics may reconstruct a paragraph at the offered container width.
            // Check each character against the actual measured, visible text box instead.
            label.indices.forEach { index ->
                val glyph = layout.getBoundingBox(index)
                assertTrue(
                    "$label character $index is clipped: $glyph outside $visibleWidth x $visibleHeight",
                    glyph.left >= -0.5f && glyph.top >= -0.5f &&
                        glyph.right <= visibleWidth + 0.5f && glyph.bottom <= visibleHeight + 0.5f,
                )
            }

            val bounds = compose.onNode(hasClickAction() and hasText(label))
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("$label needs an outdoor-width touch target", bounds.width >= minimumTouch - 0.5f)
            assertTrue("$label needs an outdoor-height touch target", bounds.height >= minimumTouch - 0.5f)
            assertTrue("$label must remain inside the bar", contains(bar, bounds))
            assertTrue("$label text must remain inside its button", contains(bounds, textBounds))
            bounds
        }
        val entries = targets.entries.toList()
        entries.forEachIndexed { index, first ->
            entries.drop(index + 1).forEach { second ->
                assertFalse(
                    "${first.key} and ${second.key} touch targets overlap",
                    first.value.overlaps(second.value),
                )
            }
        }
        val theme = compose.onNode(hasClickAction() and hasContentDescription("Theme"))
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Theme must remain inside the bar", contains(bar, theme))
        assertTrue("Theme stays below navigation", theme.top >= targets.values.maxOf { it.bottom } - 0.5f)
        val rightInset = with(compose.density) { 8.dp.toPx() }
        assertEquals("Theme stays at the bottom right", bar.right - rightInset, theme.right, 0.5f)
        return targets
    }

    private fun contains(outer: Rect, inner: Rect): Boolean =
        inner.left >= outer.left - 0.5f && inner.right <= outer.right + 0.5f &&
            inner.top >= outer.top - 0.5f && inner.bottom <= outer.bottom + 0.5f
}
