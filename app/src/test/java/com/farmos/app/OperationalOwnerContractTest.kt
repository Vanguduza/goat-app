package com.farmos.app

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.feature.ops.CattleOperationsActions
import com.farmos.feature.ops.CattleOperationsScreen
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
class OperationalOwnerContractTest {
    @get:Rule(order = 0) val nativeReview = NativeModuleReviewRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test
    fun sheepOperationsHomeRendersCanonicalOwner() {
        compose.setContent {
            NativeModuleReviewTheme() {
                SheepOperationsScreen(
                    selectedAnimalId = "sheep-1",
                    busy = false,
                    error = null,
                    actions = sheepActions(),
                    onBack = {},
                )
            }
        }

        compose.onNodeWithTag("farm-screen:FOS-SHEEP-010").assertExists().also { compose.captureObservedNativeOwners() }
    }

    @Test
    fun cattleOperationsHomeRendersCanonicalOwner() {
        compose.setContent {
            NativeModuleReviewTheme() {
                CattleOperationsScreen(
                    selectedAnimalId = "cattle-1",
                    busy = false,
                    error = null,
                    actions = cattleActions(),
                    onBack = {},
                )
            }
        }

        compose.onNodeWithTag("farm-screen:FOS-CATTLE-018").assertExists().also { compose.captureObservedNativeOwners() }
    }

    private fun sheepActions() = SheepOperationsActions(
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

    private fun cattleActions() = CattleOperationsActions(
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
}
