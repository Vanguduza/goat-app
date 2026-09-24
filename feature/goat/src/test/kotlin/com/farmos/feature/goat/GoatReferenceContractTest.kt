package com.farmos.feature.goat

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.GoatSnapshot
import com.farmos.domain.goat.GoatStatus
import com.farmos.domain.goat.WeightSample
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class GoatReferenceContractTest {
    @get:Rule
    val compose = createComposeRule()

    private val fixedDate = LocalDate.of(2026, 9, 24)
    private val state = referenceState()

    @Test
    fun dashboardRoutesToTheScopedHerdSurface() {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                GoatExperienceScreen(
                    state = state,
                    actions = noOpActions(),
                    onBackToFarm = {},
                    onSignOut = {},
                    initialPage = GoatPage.DASHBOARD,
                    today = fixedDate,
                )
            }
        }

        compose.onNode(hasClickAction() and hasText("Herd")).performClick()
        compose.onNodeWithText("Register goat").assertExists()
        assertNamedClickTargets()
    }

    @Test
    fun herdSelectionOpensTheSelectedGoatProfile() {
        var selectedId: String? = null
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                GoatExperienceScreen(
                    state = state,
                    actions = noOpActions(onSelect = { selectedId = it }),
                    onBackToFarm = {},
                    onSignOut = {},
                    initialPage = GoatPage.HERD,
                    today = fixedDate,
                )
            }
        }

        compose.onNodeWithText("Nala").performClick()

        assertEquals("goat-nala", selectedId)
        compose.onNodeWithText("Goat profile").assertIsDisplayed()
        assertNamedClickTargets()
    }

    @Test
    fun profileRoutesToWeightAndRecordsTheEnteredMeasurement() {
        var weight: String? = null
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                GoatExperienceScreen(
                    state = state,
                    actions = noOpActions(onWeight = { weight = it }),
                    onBackToFarm = {},
                    onSignOut = {},
                    initialPage = GoatPage.PROFILE,
                    today = fixedDate,
                )
            }
        }

        compose.onNode(hasClickAction() and hasText("Weight")).performClick()
        compose.onNodeWithText("Saving succeeds when the measurement is durable on this device. Sync can happen later.").assertExists()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("55.4")
        compose.onNode(hasClickAction() and hasText("Record weight")).assertIsEnabled().performClick()

        assertEquals("55.4", weight)
        assertNamedClickTargets()
    }

    @Test
    fun lifecycleChangeRequiresExplicitConfirmationAndCancelIsNonDestructive() {
        var status: GoatStatus? = null
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                GoatExperienceScreen(
                    state = state,
                    actions = noOpActions(onStatus = { status = it }),
                    onBackToFarm = {},
                    onSignOut = {},
                    initialPage = GoatPage.STATUS_CHANGE,
                    today = fixedDate,
                )
            }
        }

        compose.onNode(hasClickAction() and hasText("Mark sold")).performClick()
        assertNull(status)
        compose.onNodeWithText("Confirm sold").assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText("Cancel")).performClick()
        compose.waitForIdle()
        assertNull(status)

        compose.onNode(hasClickAction() and hasText("Mark sold")).performClick()
        compose.onNode(hasClickAction() and hasText("Confirm status change")).performClick()
        compose.waitForIdle()

        assertEquals(GoatStatus.SOLD, status)
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

    private fun referenceState(): GoatSliceUiState {
        val nala = GoatSnapshot(
            animalId = "goat-nala",
            farmId = "farm-reference",
            tag = "GT-024",
            name = "Nala",
            sex = GoatSex.FEMALE,
            status = GoatStatus.ACTIVE,
            dateOfBirthEpochDay = LocalDate.of(2024, 4, 14).toEpochDay(),
            latestWeightGrams = 54_250,
            averageDailyGainGrams = 118,
            weightHistory = listOf(
                WeightSample("w1", 48_100, LocalDate.of(2026, 7, 1).toEpochDay() * 86_400_000L),
                WeightSample("w2", 51_700, LocalDate.of(2026, 8, 1).toEpochDay() * 86_400_000L),
                WeightSample("w3", 54_250, LocalDate.of(2026, 9, 20).toEpochDay() * 86_400_000L),
            ),
            syncPending = true,
        )
        val buck = GoatSnapshot(
            animalId = "goat-kito",
            farmId = "farm-reference",
            tag = "GT-011",
            name = "Kito",
            sex = GoatSex.MALE,
            status = GoatStatus.ACTIVE,
            dateOfBirthEpochDay = LocalDate.of(2023, 9, 2).toEpochDay(),
            latestWeightGrams = 72_400,
            syncPending = false,
        )
        return GoatSliceUiState(
            farmName = "Premier Farm",
            herd = listOf(nala, buck),
            herdState = LoadableSurfaceState.IDLE,
            selected = nala,
            animalId = nala.animalId,
            pendingSyncCount = 2,
            goatSummary = "24 active goats",
            syncMessage = "2 local changes waiting to sync",
            searchMessage = "Search this herd",
            busy = false,
            error = null,
        )
    }

    private fun noOpActions(
        onWeight: (String) -> Unit = {},
        onStatus: (GoatStatus) -> Unit = {},
        onSelect: (String) -> Unit = {},
    ) = GoatExperienceActions(
        onRegister = { _, _, _, _ -> },
        onRecordWeight = onWeight,
        onRecordKidding = { _, _, _, _ -> },
        onRegisterKid = { _, _, _ -> },
        onRecordFamacha = { _, _ -> },
        onRecordMilk = { _, _ -> },
        onRecordBcs = { _, _ -> },
        onRecordScc = { _, _, _ -> },
        onRecordHeat = {},
        onRecordMating = { _, _, _ -> },
        onRecordPregnancy = { _, _ -> },
        onPlanLactation = {},
        onSetStatus = onStatus,
        onSelectGoat = onSelect,
        onSyncNow = {},
        onSearch = {},
    )
}
