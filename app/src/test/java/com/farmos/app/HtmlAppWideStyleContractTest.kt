package com.farmos.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Shared-control contracts, not manufactured per-screen product coverage or independent approval. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h900dp-mdpi")
class HtmlAppWideStyleContractTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allFeatureTypographyUsesTheHtmlHierarchyAndNativeFont() {
        var checked = false
        compose.setContent { FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
            assertEquals(AnimalFarmHtmlType.pageTitle, MaterialTheme.typography.headlineMedium)
            assertEquals(AnimalFarmHtmlType.body, MaterialTheme.typography.bodyLarge)
            assertEquals(AnimalFarmHtmlType.action, MaterialTheme.typography.labelLarge)
            assertEquals(FarmOsInter, MaterialTheme.typography.bodyLarge.fontFamily)
            checked = true
        } }
        compose.runOnIdle { assertTrue(checked) }
    }

    @Test fun metricTilesKeepExactValuesAndOnlyTheOwnerEnabledDrilldown() {
        var opened = 0
        compose.setContent { FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
            FarmMetricStrip(listOf(
                FarmMetricValue("Water", "1.275 L", onClick = { opened++ }),
                FarmMetricValue("Unrecorded weight", "Not recorded"),
                FarmMetricValue("Net amount", "-12.345 KWD", "Original currency; not converted"),
            ))
        } }
        compose.onNodeWithText("1.275 L").assertExists()
        compose.onNodeWithText("-12.345 KWD").assertExists()
        compose.onNodeWithTag("farm-metric:Unrecorded weight").assertHasNoClickAction()
        compose.onNodeWithTag("farm-metric:Water").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
        compose.onNodeWithText("0 L").assertDoesNotExist()
    }

    @Test fun underlinedSectionNavigationRetainsSelectedIdentityAtLargeOutdoorText() {
        val selected = mutableStateOf("Records")
        val labels = listOf("Records", "Photos and documents", "Authorised lifecycle change", "Comparison")
        compose.setContent { FarmOsTheme(mode = AnimalFarmThemeMode.OUTDOOR) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                    FarmSectionTabs(labels, selected.value, { selected.value = it })
                }
            }
        } }
        labels.forEach { label ->
            compose.onNodeWithTag("farm-section:$label").performScrollTo().assertIsDisplayed()
                .assertHeightIsAtLeast(64.dp).assertWidthIsAtLeast(64.dp)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        }
        compose.onNodeWithTag("farm-section:Photos and documents").performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle { assertEquals("Photos and documents", selected.value) }
        compose.onNodeWithTag("farm-section:Records").assertIsNotSelected()
    }

    @Test fun resourceActionIsDisabledWithoutLosingIdentityOrInventingARecord() {
        var writes = 0
        compose.setContent { FarmOsTheme(mode = AnimalFarmThemeMode.DARK) {
            Column {
                FarmResourceAction("Inspect recorded stock", { writes++ }, detail = "31.005 kg · Lot A", enabled = false)
                FarmRecordRow("Original event\n1.275 L\nSaved on this device · waiting to sync")
            }
        } }
        compose.onNodeWithText("Inspect recorded stock").assertIsNotEnabled()
        compose.onNodeWithText("31.005 kg · Lot A").assertExists()
        compose.onNodeWithText("1.275 L", substring = true).assertExists()
        compose.onNodeWithText("Saved on this device · waiting to sync", substring = true).assertExists()
        compose.runOnIdle { assertEquals(0, writes) }
    }
}
