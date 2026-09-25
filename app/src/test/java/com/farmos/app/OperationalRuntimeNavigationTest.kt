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
import com.farmos.feature.ops.CattleOperationsActions
import com.farmos.feature.ops.CattleOperationsScreen
import com.farmos.feature.ops.HealthObservationScreen
import com.farmos.feature.ops.InventoryScreen
import com.farmos.feature.ops.MoneyCaptureScreen
import com.farmos.feature.ops.PoultryExperienceScreen
import com.farmos.feature.ops.SheepOperationsActions
import com.farmos.feature.ops.SheepOperationsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class OperationalRuntimeNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private fun exercise(routes: List<Pair<String, String>>) {
        routes.forEach { (action, screenId) ->
            compose.onNode(hasClickAction() and hasText(action))
                .performScrollTo()
                .performClick()
            compose.onNodeWithTag("farm-screen:$screenId").assertExists()
            compose.onNode(hasClickAction() and hasText("Farm home"))
                .performScrollTo()
                .performClick()
            compose.onNode(hasClickAction() and hasText(action)).assertExists()
        }
    }

    @Test
    fun inventoryDashboardTraversesAllOwnedScreensAndReturns() {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                InventoryScreen(
                    rows = emptyList(),
                    busy = false,
                    error = null,
                    onCreate = { _, _, _ -> },
                    onMove = { _, _, _ -> },
                    onBack = {},
                )
            }
        }
        exercise(
            listOf(
                "Open inventory list" to "FOS-INV-002",
                "Receive" to "FOS-INV-005",
                "Issue" to "FOS-INV-007",
                "Create inventory item" to "FOS-INV-004",
                "Receive dated lot" to "FOS-INV-006",
                "Issue oldest-expiry lot" to "FOS-INV-008",
                "Set reorder point" to "FOS-INV-012",
                "Record reorder alert" to "FOS-INV-013",
            ),
        )
    }

    @Test
    fun financeDashboardTraversesAllOwnedScreensAndReturns() {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                MoneyCaptureScreen(
                    rows = emptyList(),
                    busy = false,
                    error = null,
                    onRecord = { _, _, _, _ -> },
                    onBack = {},
                )
            }
        }
        exercise(
            listOf(
                "Record income" to "FOS-FIN-004",
                "Record expense" to "FOS-FIN-005",
                "View transactions" to "FOS-FIN-002",
            ),
        )
    }

    @Test
    fun healthDashboardTraversesAllOwnedScreensAndReturns() {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                HealthObservationScreen(
                    rows = emptyList(),
                    catalog = emptyList(),
                    treatments = emptyList(),
                    busy = false,
                    error = null,
                    onRecord = { _, _, _, _ -> },
                    onCreateFormulary = { _, _, _, _ -> },
                    onRecordTreatment = { _, _, _ -> },
                    onBack = {},
                )
            }
        }
        exercise(
            listOf(
                "Observation list" to "FOS-HEALTH-003",
                "Record observation" to "FOS-HEALTH-004",
                "Reference library" to "FOS-HEALTH-026",
                "Vet-approved formulary" to "FOS-HEALTH-013",
                "Record treatment" to "FOS-HEALTH-007",
                "Withdrawal windows" to "FOS-HEALTH-009",
                "Record vet visit" to "FOS-HEALTH-021",
                "Record lab result" to "FOS-HEALTH-024",
                "View protocol packs" to "FOS-HEALTH-015",
                "Accept protocol pack" to "FOS-HEALTH-017",
                "Add protocol slot" to "FOS-HEALTH-019",
                "Apply protocol pack" to "FOS-HEALTH-018",
            ),
        )
    }
    @Test
    fun poultryDashboardTraversesAllOwnedScreensAndReturns() {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                PoultryExperienceScreen(
                    enabledKinds = emptyList(),
                    houses = emptyList(),
                    placements = emptyList(),
                    flockDays = emptyList(),
                    hatches = emptyList(),
                    vaccinations = emptyList(),
                    busy = false,
                    error = null,
                    onEnableKind = {},
                    onCreateHouse = { _, _, _ -> },
                    onPlaceFlock = { _, _, _, _, _ -> },
                    onRecordFlockDay = { _, _, _, _, _, _ -> },
                    onSetEggs = { _, _, _, _, _ -> },
                    onCandle = { _, _, _, _, _ -> },
                    onRecordHatch = { _, _, _, _ -> },
                    onVaccinate = { _, _, _, _ -> },
                    onBiosecurity = { _, _, _, _, _ -> },
                    onBack = {},
                )
            }
        }
        exercise(
            listOf(
                "Open Enabled kinds" to "FOS-POULTRY-002",
                "Open Houses" to "FOS-POULTRY-006",
                "Open Flocks" to "FOS-POULTRY-004",
                "Open Place flock" to "FOS-POULTRY-008",
                "Open Daily flock record" to "FOS-POULTRY-009",
                "Open Hatchery" to "FOS-POULTRY-017",
                "Open Set eggs" to "FOS-POULTRY-018",
                "Open Candling" to "FOS-POULTRY-020",
                "Open Hatch result" to "FOS-POULTRY-021",
                "Open Vaccination" to "FOS-POULTRY-014",
                "Open Biosecurity" to "FOS-POULTRY-016",
            ),
        )
    }

    @Test
    fun sheepOperationsTraverseAllOwnedScreensAndReturn() {
        val actions = SheepOperationsActions(
            onJoining = { _, _ -> },
            onScan = { _, _, _ -> },
            onLambing = { _, _, _, _, _ -> },
            onMarking = { _, _, _, _ -> },
            onWeaning = { _, _, _, _ -> },
            onWool = { _, _, _, _ -> },
            onShearing = { _, _, _, _, _ -> },
            onMicron = { _, _, _, _ -> },
            onFamacha = { _, _, _ -> },
            onDag = { _, _, _ -> },
            onFootrot = { _, _, _ -> },
            onFlystrike = { _, _, _ -> },
            onIdentifier = { _, _, _, _ -> },
            onMovement = { _, _, _, _, _ -> },
            onPedigree = { _, _, _ -> },
        )
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                SheepOperationsScreen(
                    selectedAnimalId = "sheep-1",
                    busy = false,
                    error = null,
                    actions = actions,
                    onBack = {},
                )
            }
        }
        exercise(
            listOf(
                "Record joining" to "FOS-SHEEP-011",
                "Pregnancy scan" to "FOS-SHEEP-012",
                "Record lambing" to "FOS-SHEEP-014",
                "Lamb marking" to "FOS-SHEEP-016",
                "Weaning" to "FOS-SHEEP-017",
                "Fleece record" to "FOS-SHEEP-020",
                "Shearing" to "FOS-SHEEP-019",
                "Micron / fibre result" to "FOS-SHEEP-021",
                "FAMACHA" to "FOS-SHEEP-009",
                "Dag score" to "FOS-SHEEP-022",
                "Footrot" to "FOS-SHEEP-023",
                "Flystrike" to "FOS-SHEEP-024",
                "Official identifier" to "FOS-SHEEP-029",
                "Movement" to "FOS-SHEEP-027",
                "Pedigree link" to "FOS-SHEEP-026",
            ),
        )
    }

    @Test
    fun cattleOperationsTraverseAllOwnedScreensAndReturn() {
        val actions = CattleOperationsActions(
            onService = { _, _, _ -> },
            onPd = { _, _, _ -> },
            onCalving = { _, _, _, _, _ -> },
            onBcs = { _, _, _, _ -> },
            onMilk = { _, _, _ -> },
            onLocomotion = { _, _, _ -> },
            onScc = { _, _, _, _ -> },
            onDryOff = { _, _, _ -> },
            onWeaning = { _, _, _ -> },
            onIdentifier = { _, _, _, _ -> },
            onMovement = { _, _, _, _, _ -> },
            onPedigree = { _, _, _ -> },
            onPlaceLot = { _, _, _ -> },
            onDaysOnFeed = { _, _, _ -> },
            onCloseLot = { _, _, _, _, _ -> },
        )
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                CattleOperationsScreen(
                    selectedAnimalId = "cattle-1",
                    busy = false,
                    error = null,
                    actions = actions,
                    onBack = {},
                )
            }
        }
        exercise(
            listOf(
                "Service" to "FOS-CATTLE-010",
                "Pregnancy diagnosis" to "FOS-CATTLE-013",
                "Calving" to "FOS-CATTLE-015",
                "Weaning" to "FOS-CATTLE-017",
                "Body condition score" to "FOS-CATTLE-008",
                "Milk capture" to "FOS-CATTLE-019",
                "SCC" to "FOS-CATTLE-021",
                "Dry-off" to "FOS-CATTLE-024",
                "Locomotion" to "FOS-CATTLE-023",
                "Official identifier" to "FOS-CATTLE-005",
                "Movement" to "FOS-CATTLE-030",
                "Pedigree link" to "FOS-CATTLE-032",
                "Place lot on feed" to "FOS-CATTLE-026",
                "Days on feed" to "FOS-CATTLE-028",
                "Close-out" to "FOS-CATTLE-029",
            ),
        )
    }
}
