package com.farmos.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.xmlpull.v1.XmlPullParser

/** Actual HTML CSS yields 16px Lucide strokes at1.6, and 90px family choice image windows. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w390dp-h940dp-mdpi")
class HtmlOpticalParityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nativeLineIconUsesTheMeasuredHtmlSizeAndStroke() {
        compose.setContent { FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) { FarmHtmlIcon("sun", "Sun symbol") } }
        compose.onNodeWithContentDescription("Sun symbol").assertWidthIsEqualTo(16.dp).assertHeightIsEqualTo(16.dp)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val xml = context.resources.getXml(com.farmos.core.design.R.drawable.af_html_sun)
        var paths = 0
        xml.use { parser ->
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "path") {
                    assertEquals(1.6f, parser.getAttributeFloatValue("http://schemas.android.com/apk/res/android", "strokeWidth", -1f), 0.001f)
                    paths++
                }
                parser.next()
            }
        }
        assertTrue(paths > 0)
    }

    @Test fun animalFamilyChooserKeepsTheReferenceNinetyDpPortraitAndRealAction() {
        var selected = false
        compose.setContent { FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
            AnimalFarmFamilyLauncher(AnimalFarmFamily.GOAT, "", { selected = true }, Modifier.width(110.dp))
        } }
        compose.onNodeWithTag("family-choice-portrait:Goats", useUnmergedTree = true).assertWidthIsEqualTo(110.dp).assertHeightIsEqualTo(90.dp)
        compose.onNodeWithText("Goats").performClick()
        compose.runOnIdle { assertTrue(selected) }
    }

    @Test fun themeSheetRetainsTheHtmlSymbolsAndNativeOutdoorChoice() {
        val theme = mutableStateOf(AnimalFarmThemeMode.LIGHT)
        compose.setContent { FarmOsTheme(mode = theme.value, onModeChange = { theme.value = it }) { HomeThemeButton() } }
        compose.onNodeWithContentDescription("Theme").performClick()
        compose.onNodeWithTag("theme-symbol:Light", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("theme-symbol:Dark", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Outdoor").assertExists()
        compose.onNodeWithText("Dark").performClick()
        compose.runOnIdle { assertEquals(AnimalFarmThemeMode.DARK, theme.value) }
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithTag("home-sheet:Theme").assertDoesNotExist()
    }
}
