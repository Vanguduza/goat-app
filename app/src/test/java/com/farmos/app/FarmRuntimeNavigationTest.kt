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
            "Goats" to FarmDestination.Goat(),
            "Rabbits" to FarmDestination.Module(FarmModule.RABBIT),
            "Sheep" to FarmDestination.Module(FarmModule.SHEEP),
            "Cattle" to FarmDestination.Module(FarmModule.CATTLE),
            "Poultry" to FarmDestination.Module(FarmModule.POULTRY),
        )
        species.forEach { (label, expected) ->
            opened.set(null)
            compose.onNode(hasClickAction() and hasText(label))
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            compose.runOnIdle { assertEquals(expected, opened.get()) }
        }

        compose.onNode(hasClickAction() and hasText("Farm home"))
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Farm home").assertIsDisplayed()

        val general = generalHomeActions()
        general.forEach { (label, expected) ->
            opened.set(null)
            compose.onNode(hasClickAction() and hasText(label))
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            compose.runOnIdle { assertEquals(expected, opened.get()) }
        }

        opened.set(null)
        compose.onNode(hasClickAction() and hasText("Tasks")).performClick()
        compose.runOnIdle {
            assertEquals(FarmDestination.Tasks(TaskEntryPage.BOARD), opened.get())
        }
        compose.onNode(hasClickAction() and hasText("More")).performClick()
        compose.onNodeWithText("Farm records and tools").assertIsDisplayed()

        val more = listOf(
            "Inventory" to FarmDestination.Module(FarmModule.INVENTORY),
            "Sales" to FarmDestination.Module(FarmModule.SALES),
            "Procurement" to FarmDestination.Module(FarmModule.PROCUREMENT),
            "Finance" to FarmDestination.Module(FarmModule.MONEY),
            "Pasture" to FarmDestination.Module(FarmModule.PASTURE),
            "Labour" to FarmDestination.Module(FarmModule.LABOUR),
            "Assets" to FarmDestination.Module(FarmModule.ASSETS),
        )
        more.forEach { (label, expected) ->
            opened.set(null)
            compose.onNode(hasClickAction() and hasText(label))
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            compose.runOnIdle { assertEquals(expected, opened.get()) }
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
            "Open groups" to FarmDestination.Module(FarmModule.GROUPS),
            "Open waitlist" to FarmDestination.Module(FarmModule.WAITLIST),
            "Open sync status" to FarmDestination.Goat(GoatEntryPage.SYNC),
        )
        remaining.forEach { (label, expected) ->
            opened.set(null)
            compose.onNode(hasClickAction() and hasText(label))
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            compose.runOnIdle { assertEquals(expected, opened.get()) }
        }
    }
}
