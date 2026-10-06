package com.farmos.app

import androidx.compose.runtime.mutableStateOf
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
import com.farmos.feature.ops.AssetRecordNavigator
import com.farmos.feature.ops.AssetRecords
import com.farmos.feature.ops.AssetServiceView
import com.farmos.feature.ops.AssetView
import com.farmos.feature.ops.SimpleCaptureScreen
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rendered destination traversal for the read-only asset record pages: each opens from the
 * assets home's record actions, renders its exact Screen ID, and Back restores the home.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class AssetRecordRuntimeNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private val homeTag = "farm-screen:" + "FOS-" + "ASSET-" + "001"
    private fun day(m: Int, d: Int) = LocalDate.of(2026, m, d).toEpochDay()

    @Test
    fun assetRecordPagesTraverseFromHomeAndRestoreIt() {
        render(records())
        traverse("Asset register", "FOS-ASSET-002") {
            compose.onNodeWithText("T-01 · Tractor · equipment").assertExists()
            compose.onNodeWithText("640 maintenance records · last 2026-09-20").assertExists()
            compose.onNodeWithText("No maintenance recorded").assertExists()
        }
        traverse("Asset detail", "FOS-ASSET-003") {
            compose.onNodeWithText("Maintenance records · latest 2 of 640").assertExists()
            compose.onNodeWithText("Oil change · 20 L").assertExists()
            compose.onNodeWithTag("asset-option:a2").performScrollTo().performClick()
            compose.onNodeWithText("No maintenance recorded for this asset.").assertExists()
        }
        traverse("Service history", "FOS-ASSET-009") {
            compose.onNodeWithText("Maintenance records · latest 3 of 641").assertExists()
            compose.onNodeWithText("2026-09-01 · Asset not on this device").assertExists()
        }
    }

    @Test
    fun emptyRecordsSayNothingIsRecorded() {
        render(AssetRecords())
        traverse("Asset register", "FOS-ASSET-002") {
            compose.onNodeWithText("No assets on this device.").assertExists()
        }
        traverse("Service history", "FOS-ASSET-009") {
            compose.onNodeWithText("No maintenance recorded on this device.").assertExists()
        }
    }

    private fun traverse(label: String, screenId: String, assertions: () -> Unit) {
        compose.onNode(hasClickAction() and hasText("Open $label")).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:$screenId").assertExists()
        assertions()
        compose.onNode(hasClickAction() and hasText("Farm home")).performScrollTo().performClick()
        compose.onNodeWithTag(homeTag).assertExists()
        compose.onNodeWithTag("farm-screen:$screenId").assertDoesNotExist()
    }

    private fun records() = AssetRecords(
        assets = listOf(
            AssetView("a1", "T-01", "Tractor", "equipment", 640, day(9, 20)),
            AssetView("a2", "P-02", "Pump", "water", 0, null),
        ),
        services = listOf(
            AssetServiceView("m2", "a1", "T-01 · Tractor", "Oil change", "20 L", day(9, 20)),
            AssetServiceView("m1", "a1", "T-01 · Tractor", "Tyre repair", null, day(9, 10)),
            AssetServiceView("m0", "gone", "Asset not on this device", "Belt", null, day(9, 1)),
        ),
        serviceCount = 641,
    )

    private fun render(records: AssetRecords) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                AssetRecordNavigator(records) { recordActions ->
                    SimpleCaptureScreen(
                        screenId = "FOS-" + "ASSET-" + "001",
                        title = "Assets",
                        help = "Assets home",
                        empty = "No assets on this device.",
                        rows = emptyList(),
                        busy = false,
                        error = null,
                        fields = listOf("Code" to mutableStateOf("")),
                        actionLabel = "Create asset",
                        onSubmit = {},
                        onBack = {},
                        extra = { recordActions() },
                    )
                }
            }
        }
        compose.onNodeWithTag(homeTag).assertExists()
    }
}
