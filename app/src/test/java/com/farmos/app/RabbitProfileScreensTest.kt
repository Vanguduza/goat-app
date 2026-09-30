package com.farmos.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.domain.ops.AnimalExitKind
import com.farmos.feature.rabbit.RabbitAnimalView
import com.farmos.feature.rabbit.RabbitProgrammeScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Rendered Doe profile (FOS-RABBIT-003) and Buck profile (FOS-RABBIT-004) with the rabbit's exit, owner decision D-022. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class RabbitProfileScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val doe = RabbitAnimalView("r1", "RB-1", "Luna", isDoe = true, status = "active")
    private val buck = RabbitAnimalView("r2", "RB-2", null, isDoe = false, status = "culled")

    private fun render(onRecord: (SpeciesExitDraft) -> Unit = {}, onReverse: (String, String) -> Unit = { _, _ -> }) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                RabbitProgrammeScreen(
                    cages = emptyList(), waves = emptyList(), availableBoxes = 0, busy = false, error = null, does = listOf("RB-1 · Luna · doe · active"),
                    onRegisterDoe = { _, _, _ -> }, onCreateCage = {}, onCreateNestBox = { _, _ -> }, onCreateWave = { _, _, _ -> },
                    onPalpate = { _, _, _ -> }, onKindle = { _, _, _, _ -> }, onFoster = { _, _, _, _, _ -> }, onBack = {},
                    rabbits = listOf(doe, buck),
                    rabbitExit = { rabbit ->
                        val standing = if (rabbit.active) null else SpeciesStandingExit("exit-9", "Culled on 2026-09-20 · Poor growth")
                        SpeciesExitContent(SpeciesAnimalRow(rabbit.animalId, rabbit.label, rabbit.active), standing, "USD", false, null, onRecord, onReverse)
                    },
                    rabbitAttachments = { rabbit -> AttachmentsSection(emptyList(), canAttach = rabbit.active, busy = false, message = null, onAddPhoto = {}, onAddDocument = {}) },
                )
            }
        }
        compose.onNode(hasClickAction() and hasText("Open Breeding animals")).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-RABBIT-002").assertExists()
    }

    @Test
    fun aDoeProfileRecordsHerCullAfterConfirmation() {
        val recorded = mutableListOf<SpeciesExitDraft>()
        render(onRecord = { recorded += it })
        compose.onNodeWithTag("rabbit:r1").performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-RABBIT-003").assertExists()
        compose.onNodeWithText("In the rabbitry").assertExists()
        // Photos and documents of the doe (D-015) sit on her profile.
        compose.onNodeWithTag("farm-atom:FOS-ATOM-016:photo").performScrollTo().assertExists()
        compose.onNodeWithTag("species-exit-kind:CULL").performScrollTo().performClick()
        compose.onNodeWithTag("species-exit-reason").performScrollTo().performTextInput("Poor mothering")
        compose.onNodeWithTag("species-exit-continue").performScrollTo().performClick()
        compose.onNodeWithTag("species-exit-confirm").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(AnimalExitKind.CULL, recorded.single().kind)
            assertEquals("Poor mothering", recorded.single().reason)
        }
    }

    @Test
    fun aBuckThatLeftShowsHisExitWhichIsReversedWithAReason() {
        val reversed = mutableListOf<Pair<String, String>>()
        render(onReverse = { id, reason -> reversed += id to reason })
        compose.onNodeWithTag("rabbit:r2").performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-RABBIT-004").assertExists()
        compose.onNodeWithText("Left the rabbitry: culled").assertExists()
        compose.onNodeWithTag("species-standing-exit").assertExists()
        compose.onNodeWithTag("species-exit-reverse-reason").performScrollTo().performTextInput("Culled the wrong buck")
        compose.onNodeWithTag("species-exit-reverse").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("exit-9" to "Culled the wrong buck"), reversed) }
    }
}
