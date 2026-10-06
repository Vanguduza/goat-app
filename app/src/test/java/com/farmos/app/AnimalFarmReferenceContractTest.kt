package com.farmos.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
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
import com.farmos.core.design.AnimalFarmThemeSelectionPanel
import com.farmos.core.design.FarmOsTheme
import com.farmos.feature.goat.GoatEntryPage
import com.farmos.feature.ops.TaskEntryPage
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class AnimalFarmReferenceContractTest {
    @get:Rule
    val compose = createComposeRule()

    private val fixedDate = LocalDate.of(2026, 9, 24)

    @Test
    fun splashRendersItsCanonicalOwner() {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                FarmOsSplashScreen()
            }
        }

        compose.onNodeWithTag("farm-screen:FOS-GLOBAL-001").assertIsDisplayed()
    }

    @Test
    fun loginSubmitsTheEnteredCredentialsAndKeepsControlsNamed() {
        var submitted: Pair<String, String>? = null
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                FoundationAuthScreen(
                    backendConfigured = true,
                    busy = false,
                    error = null,
                    sessionPresent = false,
                    memberships = emptyList(),
                    onSignIn = { email, password -> submitted = email to password },
                    onSelectFarm = {},
                    onCreateFarm = {},
                    onSignOut = {},
                )
            }
        }

        compose.onNodeWithTag("farm-screen:FOS-GLOBAL-002").assertIsDisplayed()
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("owner@example.com")
        fields[1].performTextInput("correct-horse")
        compose.onNode(hasClickAction() and hasText("Sign in"))
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        compose.runOnIdle {
            assertEquals("owner@example.com" to "correct-horse", submitted)
        }
        assertNamedClickTargets()
    }

    @Test
    fun managementControlRoomOpensTheRankedTaskOwner() {
        var opened: FarmDestination? = null
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                ManagementControlRoomScreen(
                    farmName = "Premier Farm",
                    ownerMode = true,
                    summary = referenceSummary(),
                    onOpen = { opened = it },
                    onAnimals = {},
                    onMore = {},
                    today = fixedDate,
                )
            }
        }

        compose.onNodeWithText("Open tasks").performClick()

        compose.runOnIdle {
            assertEquals(FarmDestination.Tasks(TaskEntryPage.BOARD), opened)
        }
        assertNamedClickTargets()
    }

    @Test
    fun managementSyncEntryRendersCanonicalOwnerAndOpensSync() {
        var opened: FarmDestination? = null
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                ManagementControlRoomScreen(
                    farmName = "Premier Farm",
                    ownerMode = true,
                    summary = referenceSummary(),
                    onOpen = { opened = it },
                    onAnimals = {},
                    onMore = {},
                    today = fixedDate,
                )
            }
        }

        compose.onNodeWithTag("farm-screen:FOS-HOME-009")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        compose.runOnIdle {
            assertEquals(FarmDestination.Goat(GoatEntryPage.SYNC), opened)
        }
        assertNamedClickTargets()
    }

    @Test
    fun workerBoardOpensTheExactVisibleTask() {
        var opened: FarmDestination? = null
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                WorkerWorkBoardScreen(
                    farmName = "Premier Farm",
                    summary = referenceSummary(),
                    onOpen = { opened = it },
                    onAnimals = {},
                    onMore = {},
                    today = fixedDate,
                )
            }
        }

        compose.onNode(hasClickAction() and hasText("Continue task"))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        compose.runOnIdle {
            assertEquals(FarmDestination.Task("task-health-nala"), opened)
        }
        assertNamedClickTargets()
    }

    @Test
    fun speciesNavigatorSelectsTheGoatModuleAndBackRemainsReachable() {
        var opened: FarmModule? = null
        var backed = false
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                SpeciesNavigatorScreen(
                    onOpen = { opened = it },
                    onBack = { backed = true },
                    today = fixedDate,
                )
            }
        }

        compose.onNodeWithTag("farm-screen:FOS-HOME-002").assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText("Goats"))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.runOnIdle {
            assertEquals(FarmModule.GOAT, opened)
        }

        compose.onNode(hasClickAction() and hasText("Farm home"))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.runOnIdle {
            assertTrue(backed)
        }
        assertNamedClickTargets()
    }

    @Test
    fun signOutConfirmationRendersItsCanonicalOwnerAndConfirmsExplicitly() {
        var confirmed = false
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                SignOutConfirmationDialog(
                    visible = true,
                    onDismiss = {},
                    onConfirm = { confirmed = true },
                )
            }
        }

        compose.onNodeWithTag("farm-screen:FOS-GLOBAL-020").assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText("Sign out")).performClick()
        compose.runOnIdle { assertTrue(confirmed) }
    }

    @Test
    fun themeSelectionChangesModeWithoutExposingAnotherSettingsSurface() {
        var selected = AnimalFarmThemeMode.LIGHT
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                AnimalFarmThemeSelectionPanel(
                    selected = selected,
                    onModeChange = { selected = it },
                )
            }
        }

        compose.onNodeWithText("Dark").performClick()

        compose.runOnIdle {
            assertEquals(AnimalFarmThemeMode.DARK, selected)
        }
        assertNamedClickTargets()
    }

    private fun assertNamedClickTargets() {
        val nodes = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue("Expected at least one interactive node", nodes.isNotEmpty())
        nodes.forEach { node ->
            val named =
                node.config.contains(SemanticsProperties.Text) ||
                    node.config.contains(SemanticsProperties.ContentDescription)
            assertTrue("Unnamed click target: ${node.config}", named)
        }
    }

    private fun referenceSummary(): FarmHomeSummary {
        val today = fixedDate.toEpochDay()
        return FarmHomeSummary(
            goatCount = 24,
            openTasks = 3,
            overdueTasks = 1,
            upcomingTasks = 1,
            completedTasks = 1,
            activeWithdrawals = 0,
            pendingSync = 0,
            inventoryItemCount = 12,
            inventoryBelowReorder = 2,
            tasks = listOf(
                HomeTaskRow(
                    id = "task-health-nala",
                    title = "Check Nala condition",
                    moduleCode = "goat",
                    taskCode = "HEALTH_CHECK",
                    dueEpochDay = today,
                    status = "open",
                    animalId = "goat-nala",
                ),
                HomeTaskRow(
                    id = "task-feed-kids",
                    title = "Review kid feed",
                    moduleCode = "feed",
                    taskCode = "FEED_REVIEW",
                    dueEpochDay = today + 1,
                    status = "open",
                    animalId = null,
                ),
                HomeTaskRow(
                    id = "task-weight",
                    title = "Record weekly weights",
                    moduleCode = "goat",
                    taskCode = "WEIGHT",
                    dueEpochDay = today - 1,
                    status = "done",
                    animalId = "goat-nala",
                ),
            ),
        )
    }
}
