package com.farmos.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class SpeciesHerdRuntimeNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun sheepIndividualAnimalSurfacesTraverseAndReturn() {
        renderSpecies(FarmModule.SHEEP, "Sheep One")

        open("Open Sheep records")
        assertScreen("FOS-SHEEP-002")
        open("Sheep One")
        assertScreen("FOS-SHEEP-003")
        home()

        open("Open Register sheep")
        assertScreen("FOS-SHEEP-004")
        home()

        open("Open profile")
        assertScreen("FOS-SHEEP-003")
        open("Record weight")
        assertScreen("FOS-SHEEP-006")
        home()

        open("Open profile")
        open("Lifecycle status")
        assertScreen("FOS-SHEEP-030")
        home()
    }

    @Test
    fun cattleIndividualAnimalSurfacesTraverseAndReturn() {
        renderSpecies(FarmModule.CATTLE, "Cattle One")

        open("Open Cattle records")
        assertScreen("FOS-CATTLE-002")
        open("Cattle One")
        assertScreen("FOS-CATTLE-003")
        home()

        open("Open Register cattle")
        assertScreen("FOS-CATTLE-004")
        home()

        open("Open profile")
        assertScreen("FOS-CATTLE-003")
        open("Record weight")
        assertScreen("FOS-CATTLE-006")
        home()

        open("Open profile")
        open("Lifecycle status")
        assertScreen("FOS-CATTLE-034")
        home()
    }

    private fun renderSpecies(module: FarmModule, label: String) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                SpeciesHerdScreen(
                    module = module,
                    title = module.name,
                    femaleLabel = if (module == FarmModule.SHEEP) "Ewe" else "Cow",
                    maleLabel = if (module == FarmModule.SHEEP) "Ram" else "Bull",
                    kindRequired = false,
                    rows = listOf(SpeciesAnimalRow("${module.name.lowercase()}-1", label, true)),
                    busy = false,
                    error = null,
                    onRegister = { _, _, _, _ -> },
                    onRecordWeight = { _, _ -> },
                    onSetStatus = { _, _ -> },
                    onBack = {},
                )
            }
        }
    }

    private fun open(label: String) {
        compose.onNode(hasClickAction() and hasText(label))
            .performScrollTo()
            .performClick()
    }

    private fun assertScreen(screenId: String) {
        compose.onNodeWithTag("farm-screen:$screenId").assertExists()
    }

    private fun home() {
        open("Farm home")
    }
}
