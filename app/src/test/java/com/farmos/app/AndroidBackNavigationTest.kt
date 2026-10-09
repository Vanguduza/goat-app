package com.farmos.app

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmBackHandler
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOsTheme
import com.farmos.feature.goat.GoatEntryPage
import com.farmos.feature.goat.GoatSliceUiState
import com.farmos.feature.goat.GoatVerticalSliceScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Android's dispatcher must follow the rendered page's return action before leaving its owner. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class AndroidBackNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var dispatcher: OnBackPressedDispatcher

    @Test
    fun exportSheetReturnsToReportsBeforeReturningToTheFarm() {
        var leftReports = 0
        var exports = 0
        content {
            ReportsScreen(
                metrics = emptyList(), failure = null, canExport = true,
                exporting = false, exportMessage = null,
                onExportHerdRegister = { exports++ },
                onBack = { leftReports++ },
            )
        }
        compose.onNodeWithTag("report-open-export").performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-REPORT-011").assertIsDisplayed()

        back()
        compose.onNodeWithTag("farm-screen:FOS-REPORT-001").assertIsDisplayed()
        compose.onNodeWithTag("farm-screen:FOS-REPORT-011").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, leftReports)
            assertEquals(0, exports)
        }

        back()
        compose.runOnIdle { assertEquals(1, leftReports) }
    }

    @Test
    fun registerReturnDoesNotSaveOrSignOutAndDashboardReturnsToTheFarm() {
        var registrations = 0
        var leftGoats = 0
        var signOuts = 0
        content {
            goat(
                entry = GoatEntryPage.DASHBOARD,
                onRegister = { registrations++ },
                onBack = { leftGoats++ },
                onSignOut = { signOuts++ },
            )
        }
        click("Register goat")
        compose.onNodeWithTag("farm-screen:FOS-GOAT-004").assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText("Tag")).performTextInput("UNSAVED-1")
        back()
        compose.onNodeWithTag("farm-screen:FOS-GOAT-001").assertIsDisplayed()
        compose.onNodeWithTag("farm-screen:FOS-GOAT-004").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, registrations)
            assertEquals(0, leftGoats)
            assertEquals(0, signOuts)
        }
        back()
        compose.runOnIdle {
            assertEquals(1, leftGoats)
            assertEquals(0, registrations)
            assertEquals(0, signOuts)
        }
    }

    @Test
    fun directWeightEntryReturnsToItsCallerWithoutCreatingAProfile() {
        var returned = 0
        content { goat(entry = GoatEntryPage.WEIGHT, onBack = { returned++ }) }
        compose.onNodeWithTag("farm-screen:FOS-GOAT-011").assertIsDisplayed()
        back()
        compose.runOnIdle { assertEquals(1, returned) }
        compose.onNodeWithTag("farm-screen:FOS-GOAT-003").assertDoesNotExist()
        compose.onNodeWithTag("farm-screen:FOS-GOAT-001").assertDoesNotExist()
    }

    @Test
    fun animalsAndMoreReturnToTheSameOwnerHomeWithoutSigningOut() {
        var signOuts = 0
        var opened = 0
        content {
            RoleAwareFarmHomeScreen(
                role = "owner", farmName = "Back route farm", summary = FarmHomeSummary(),
                onOpen = { opened++ }, onSignOut = { signOuts++ },
            )
        }
        compose.onNodeWithTag("farm-screen:FOS-HOME-012-A").assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText("Animals")).performClick()
        compose.onNodeWithTag("farm-screen:FOS-HOME-002").assertIsDisplayed()
        back()
        compose.onNodeWithTag("farm-screen:FOS-HOME-012-A").assertIsDisplayed()
        compose.onNodeWithTag("farm-screen:FOS-HOME-002").assertDoesNotExist()

        compose.onNode(hasClickAction() and hasText("More")).performClick()
        compose.onNodeWithText("Farm records and tools").assertIsDisplayed()
        back()
        compose.onNodeWithTag("farm-screen:FOS-HOME-012-A").assertIsDisplayed()
        compose.onNodeWithText("Farm records and tools").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, signOuts)
            assertEquals(0, opened)
        }
    }

    @Test
    fun sheepProfileAndWeightKeepTheSelectedAnimalAndReturnThroughTheirOwner() {
        var leftSheep = 0
        var weights = 0
        content {
            SpeciesHerdScreen(
                module = FarmModule.SHEEP, title = "Sheep flock",
                femaleLabel = "Ewe", maleLabel = "Ram", kindRequired = false,
                rows = listOf(
                    SpeciesAnimalRow("ewe-a", "SH-A · Fern · female · active", true),
                    SpeciesAnimalRow("ewe-b", "SH-B · Hazel · female · active", true),
                ),
                busy = false, error = null,
                onRegister = { _, _, _, _ -> },
                onRecordWeight = { _, _ -> weights++ },
                onSetStatus = { _, _ -> },
                onBack = { leftSheep++ },
            )
        }
        click("Open Sheep records")
        compose.onNodeWithTag("farm-screen:FOS-SHEEP-002").assertIsDisplayed()
        click("SH-B · Hazel · female · active")
        compose.onNodeWithTag("farm-screen:FOS-SHEEP-003").assertIsDisplayed()
        compose.onNodeWithText("SH-B · Hazel · female · active").assertExists()
        compose.onNodeWithText("SH-A · Fern · female · active").assertDoesNotExist()
        click("Record weight")
        compose.onNodeWithTag("farm-screen:FOS-SHEEP-006").assertIsDisplayed()
        compose.onNodeWithText("SH-B · Hazel · female · active").assertExists()
        back()
        // This page's rendered return action is the sheep dashboard.
        compose.onNodeWithText("Individual sheep records on this device").assertIsDisplayed()
        compose.onNodeWithTag("farm-screen:FOS-SHEEP-006").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, leftSheep)
            assertEquals(0, weights)
        }
        back()
        compose.runOnIdle { assertEquals(1, leftSheep) }
    }

    @Test
    fun theCurrentPageCallbackIsUsedAndANullReturnLeavesItsParentInControl() {
        val revision = mutableStateOf(0)
        val ownsReturn = mutableStateOf(true)
        var oldCallback = 0
        var newCallback = 0
        var parentCallback = 0
        content {
            FarmBackHandler { parentCallback++ }
            val onBack: (() -> Unit)? = when {
                !ownsReturn.value -> null
                revision.value == 0 -> ({ oldCallback++ })
                else -> ({ newCallback++ })
            }
            FarmOperationalPage(
                screenId = "FOS-REPORT-001", title = "Reports", subtitle = "Return ownership",
                onBack = onBack,
            ) { Text("Current farm report") }
        }
        back()
        compose.runOnIdle {
            assertEquals(1, oldCallback)
            assertEquals(0, parentCallback)
            revision.value = 1
        }
        back()
        compose.runOnIdle {
            assertEquals(1, oldCallback)
            assertEquals(1, newCallback)
            assertEquals(0, parentCallback)
            ownsReturn.value = false
        }
        back()
        compose.runOnIdle {
            assertEquals(1, oldCallback)
            assertEquals(1, newCallback)
            assertEquals(1, parentCallback)
        }
    }

    private fun content(block: @Composable () -> Unit) {
        compose.setContent {
            dispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT, content = block)
        }
    }

    private fun back() {
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun click(label: String) {
        compose.onNode(hasClickAction() and hasText(label)).performScrollTo().performClick()
    }

    @Composable
    private fun goat(
        entry: GoatEntryPage,
        onRegister: () -> Unit = {},
        onBack: () -> Unit,
        onSignOut: () -> Unit = {},
    ) {
        GoatVerticalSliceScreen(
            state = GoatSliceUiState(),
            entryPage = entry,
            onRegister = { _, _, _, _ -> onRegister() },
            onRecordWeight = {}, onRecordKidding = { _, _, _, _ -> },
            onRegisterKid = { _, _, _ -> }, onRecordFamacha = { _, _ -> },
            onRecordMilk = { _, _ -> }, onRecordBcs = { _, _ -> },
            onRecordScc = { _, _, _ -> }, onRecordHeat = {},
            onRecordMating = { _, _, _ -> }, onRecordPregnancy = { _, _ -> },
            onPlanLactation = {}, onSetStatus = {}, onSelectGoat = {},
            onSyncNow = {}, onSearch = {}, onSignOut = onSignOut, onBack = onBack,
        )
    }
}
