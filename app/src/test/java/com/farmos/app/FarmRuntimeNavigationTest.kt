package com.farmos.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.feature.goat.GoatEntryPage
import com.farmos.feature.ops.TaskEntryPage
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class FarmRuntimeNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun generalHomeTraversesSpeciesUtilitiesAndRestoresHome() {
        val opened = AtomicReference<FarmDestination?>(null)
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                FarmHomeScreen(
                    farmName = "Premier Farm",
                    summary = FarmHomeSummary(goatCount = 12, openTasks = 3, pendingSync = 2),
                    onOpen = opened::set,
                    onSignOut = {},
                )
            }
        }

        compose.onNode(hasClickAction() and hasText("Animals")).performClick()
        compose.onNodeWithText("Choose a species").assertIsDisplayed()

        val species = listOf(
            Triple("Goats", FarmDestination.Goat(), "FOS-GOAT-001"),
            Triple("Rabbits", FarmDestination.Module(FarmModule.RABBIT), "FOS-RABBIT-001"),
            Triple("Sheep", FarmDestination.Module(FarmModule.SHEEP), "FOS-SHEEP-001"),
            Triple("Cattle", FarmDestination.Module(FarmModule.CATTLE), "FOS-CATTLE-001"),
            Triple("Poultry", FarmDestination.Module(FarmModule.POULTRY), "FOS-POULTRY-001"),
        )
        species.forEach { (label, expected, screenId) ->
            opened.set(null)
            compose.onNode(hasClickAction() and hasText(label))
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            compose.runOnIdle {
                assertEquals(expected, opened.get())
                assertEquals(screenId, opened.get()!!.runtimeRouteContract().screenId)
            }
        }

        compose.onNode(hasClickAction() and hasText("Farm home"))
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Farm home").assertIsDisplayed()

        val general = listOf(
            Triple("Open tasks", FarmDestination.Tasks(TaskEntryPage.BOARD), "FOS-TASK-001"),
            Triple("Open health", FarmDestination.Health(), "FOS-HEALTH-001"),
            Triple("Open feed", FarmDestination.Module(FarmModule.FEED), "FOS-FEED-001"),
            Triple("Open water", FarmDestination.Module(FarmModule.WATER), "FOS-WATER-001"),
            Triple("Open sync status", FarmDestination.Goat(GoatEntryPage.SYNC), "FOS-SYNC-002"),
        )
        assertEquals(general.map { it.first to it.second }, generalHomeActions())
        general.forEach { (label, expected, screenId) ->
            opened.set(null)
            compose.onNode(hasClickAction() and hasText(label))
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            compose.runOnIdle {
                assertEquals(expected, opened.get())
                assertEquals(screenId, opened.get()!!.runtimeRouteContract().screenId)
            }
        }

        opened.set(null)
        compose.onNode(hasClickAction() and hasText("Tasks")).performClick()
        compose.runOnIdle {
            assertEquals(FarmDestination.Tasks(TaskEntryPage.BOARD), opened.get())
            assertEquals("FOS-TASK-001", opened.get()!!.runtimeRouteContract().screenId)
        }
        compose.onNode(hasClickAction() and hasText("More")).performClick()
        compose.onNodeWithText("Farm records and tools").assertIsDisplayed()

        val more = listOf(
            Triple("Inventory", FarmDestination.Module(FarmModule.INVENTORY), "FOS-INV-001"),
            Triple("Sales", FarmDestination.Module(FarmModule.SALES), "FOS-SALES-001"),
            Triple("Procurement", FarmDestination.Module(FarmModule.PROCUREMENT), "FOS-PROC-001"),
            Triple("Finance", FarmDestination.Module(FarmModule.MONEY), "FOS-FIN-001"),
            Triple("Pasture", FarmDestination.Module(FarmModule.PASTURE), "FOS-PASTURE-001"),
            Triple("Labour", FarmDestination.Module(FarmModule.LABOUR), "FOS-LABOUR-001"),
            Triple("Assets", FarmDestination.Module(FarmModule.ASSETS), "FOS-ASSET-001"),
        )
        more.forEach { (label, expected, screenId) ->
            opened.set(null)
            compose.onNode(hasClickAction() and hasText(label))
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            compose.runOnIdle {
                assertEquals(expected, opened.get())
                assertEquals(screenId, opened.get()!!.runtimeRouteContract().screenId)
            }
        }

        compose.onNode(hasClickAction() and hasText("Farm home"))
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Farm home").assertIsDisplayed()
    }
    @Test
    fun managementHomeExposesTheRemainingExactModuleOwners() {
        val opened = AtomicReference<FarmDestination?>(null)
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                ManagementControlRoomScreen(
                    farmName = "Premier Farm",
                    ownerMode = true,
                    summary = FarmHomeSummary(),
                    onOpen = opened::set,
                    onAnimals = {},
                    onMore = {},
                    today = LocalDate.of(2026, 9, 24),
                )
            }
        }

        val remaining = listOf(
            Triple("Open groups", FarmDestination.Module(FarmModule.GROUPS), "FOS-GROUP-001"),
            Triple("Open waitlist", FarmDestination.Module(FarmModule.WAITLIST), "FOS-RABBIT-027"),
            Triple("Open sync status", FarmDestination.Goat(GoatEntryPage.SYNC), "FOS-SYNC-002"),
        )
        remaining.forEach { (label, expected, screenId) ->
            opened.set(null)
            compose.onNode(hasClickAction() and hasText(label))
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            compose.runOnIdle {
                assertEquals(expected, opened.get())
                assertEquals(screenId, opened.get()!!.runtimeRouteContract().screenId)
            }
        }
    }
}
