package com.farmos.feature.goat

import androidx.compose.ui.test.assertIsDisplayed
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
import com.farmos.domain.goat.GoatObservationSample
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.GoatSnapshot
import com.farmos.domain.goat.GoatTreatmentSample
import com.farmos.domain.goat.GoatWithdrawalSample
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rendered destination traversal for read-only goat health records: each page opens from the real
 * goat profile Records action, renders its exact Screen ID with this goat's health ledger rows,
 * and Back restores the profile.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class GoatHealthRuntimeNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 9, 24)
    private val profileTag = "farm-screen:" + "FOS-" + "GOAT-" + "003"

    @Test
    fun healthRecordsTraverseFromTheProfileAndRestoreIt() {
        render(goatWithHealth())

        traverse("Health summary", "FOS-GOAT-025") {
            compose.onNodeWithTag("goat-health-active-withdrawal").assertExists()
            compose.onNodeWithText("Milk · Oxytet LA · until 2026-09-30").assertExists()
            compose.onNodeWithText("Red flag").assertExists()
            compose.onNodeWithText("Pale gums, weak").assertExists()
        }
        traverse("Treatment history", "FOS-GOAT-026") {
            compose.onNodeWithTag("goat-treatment:t-new").assertExists()
            compose.onNodeWithText("Oxytet LA").assertExists()
            compose.onNodeWithText("Withdrawal: meat 28 day(s) · milk 7 day(s)").assertExists()
            compose.onNodeWithText("Product not on this device").assertExists()
        }
        traverse("Withdrawal status", "FOS-GOAT-027") {
            compose.onNodeWithTag("goat-withdrawal-active:t-new:milk").assertExists()
            compose.onNodeWithTag("goat-withdrawal-active:t-new:meat").assertExists()
            compose.onNodeWithTag("goat-withdrawal-ended:t-old:milk").assertExists()
            compose.onNodeWithText("Ended 2026-08-20").assertExists()
        }
    }

    @Test
    fun summaryLinksOpenTheOwningRecordPages() {
        render(goatWithHealth())
        compose.onNode(hasClickAction() and hasText("Health summary")).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-GOAT-025").assertExists()
        compose.onNode(hasClickAction() and hasText("Withdrawal status")).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-GOAT-027").assertExists()
        compose.onNode(hasClickAction() and hasText("Back")).performScrollTo().performClick()
        compose.onNodeWithTag(profileTag).assertExists()
    }

    @Test
    fun noActiveWithdrawalIsStatedWithoutClaimingClearance() {
        render(goatWithHealth().copy(withdrawalWindows = emptyList(), treatmentHistory = emptyList(), observationHistory = emptyList()))
        traverse("Withdrawal status", "FOS-GOAT-027") {
            compose.onNodeWithText("No active withdrawal windows recorded on this device.").assertExists()
        }
        traverse("Treatment history", "FOS-GOAT-026") {
            compose.onNodeWithText("No treatments recorded for this goat on this device.").assertExists()
        }
        traverse("Health summary", "FOS-GOAT-025") {
            compose.onNodeWithTag("goat-health-active-withdrawal").assertDoesNotExist()
        }
    }

    @Test
    fun withdrawalWindowIsActiveThroughItsEndDay() {
        val window = GoatWithdrawalSample("w", "t", "P", "milk", today.toEpochDay())
        assertTrue(GoatHealthRecords.isActive(window, today))
        assertFalse(GoatHealthRecords.isActive(window, today.plusDays(1)))
        val goat = goatWithHealth()
        assertEquals(listOf("t-new:milk", "t-new:meat"), GoatHealthRecords.activeWindows(goat, today).map { it.windowId })
        assertEquals(listOf("t-old:milk"), GoatHealthRecords.endedWindows(goat, today).map { it.windowId })
        assertNull(GoatHealthRecords.withdrawalDaysLabel(null, null))
        assertEquals("Eggs", GoatHealthRecords.windowKindLabel("egg"))
    }

    private fun render(goat: GoatSnapshot) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                GoatExperienceScreen(
                    state = GoatSliceUiState(farmName = "Premier Farm", herd = listOf(goat), selected = goat, animalId = goat.animalId),
                    actions = GoatExperienceActions(
                        onRegister = { _, _, _, _ -> },
                        onRecordWeight = {},
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
                        onSetStatus = {},
                        onSelectGoat = {},
                        onSyncNow = {},
                        onSearch = {},
                    ),
                    onBackToFarm = {},
                    onSignOut = {},
                    initialPage = GoatPage.PROFILE,
                    today = today,
                )
            }
        }
        compose.onNodeWithTag(profileTag).assertIsDisplayed()
    }

    private fun traverse(label: String, screenId: String, assertions: () -> Unit) {
        compose.onNode(hasClickAction() and hasText(label)).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:$screenId").assertExists()
        compose.onNodeWithText("Nala · GT-024").assertExists()
        assertions()
        compose.onNode(hasClickAction() and hasText("Back")).performScrollTo().performClick()
        compose.onNodeWithTag(profileTag).assertExists()
        compose.onNodeWithTag("farm-screen:$screenId").assertDoesNotExist()
    }

    private fun goatWithHealth(): GoatSnapshot {
        fun millis(day: LocalDate) = day.toEpochDay() * 86_400_000L
        return GoatSnapshot(
            animalId = "goat-nala",
            farmId = "farm-reference",
            tag = "GT-024",
            name = "Nala",
            sex = GoatSex.FEMALE,
            latestWeightGrams = null,
            treatmentHistory = listOf(
                GoatTreatmentSample("t-new", "Foot abscess", "Oxytet LA", 28, 7, millis(LocalDate.of(2026, 9, 23))),
                GoatTreatmentSample("t-old", "Mastitis", null, null, 3, millis(LocalDate.of(2026, 8, 17))),
            ),
            withdrawalWindows = listOf(
                GoatWithdrawalSample("t-new:meat", "t-new", "Oxytet LA", "meat", LocalDate.of(2026, 10, 21).toEpochDay()),
                GoatWithdrawalSample("t-new:milk", "t-new", "Oxytet LA", "milk", LocalDate.of(2026, 9, 30).toEpochDay()),
                GoatWithdrawalSample("t-old:milk", "t-old", "Intramammary", "milk", LocalDate.of(2026, 8, 20).toEpochDay()),
            ),
            observationHistory = listOf(
                GoatObservationSample("o1", "Pale gums, weak", null, true, millis(LocalDate.of(2026, 9, 22))),
            ),
            syncPending = false,
        )
    }
}
