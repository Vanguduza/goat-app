package com.farmos.feature.goat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.domain.goat.GoatHeatSample
import com.farmos.domain.goat.GoatMatingSample
import com.farmos.domain.goat.GoatPedigree
import com.farmos.domain.goat.GoatPedigreeLink
import com.farmos.domain.goat.GoatPregnancySample
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.GoatSnapshot
import com.farmos.domain.goat.GoatStatus
import com.farmos.domain.goat.KiddingSample
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rendered destination traversal for read-only goat reproduction and pedigree records. Breeding
 * status is derived only from recorded events; no due date is computed.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class GoatReproductionRuntimeNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private val profileTag = "farm-screen:" + "FOS-" + "GOAT-" + "003"
    private val dashboardTag = "farm-screen:" + "FOS-" + "GOAT-" + "001"
    private fun day(m: Int, d: Int) = LocalDate.of(2026, m, d).toEpochDay()

    @Test
    fun breedingRecordsAndPedigreeTraverseFromTheProfile() {
        render(nala(), GoatPage.PROFILE)
        compose.onNodeWithTag(profileTag).assertIsDisplayed()

        traverse("Breeding records", "FOS-GOAT-030", profileTag) {
            compose.onNodeWithTag("goat-breeding-status").assertExists()
            compose.onNodeWithText("Confirmed pregnant").assertExists()
            compose.onNodeWithText("GT-011 · Kito").assertExists()
            compose.onNodeWithText("2026-08-01 · Natural").assertExists()
            compose.onNodeWithText("Heat observed").assertExists()
        }
        traverse("Pedigree", "FOS-GOAT-044", profileTag) {
            compose.onNodeWithTag("goat-pedigree:p-dam").assertExists()
            compose.onNodeWithText("GT-002 · Asha").assertExists()
            compose.onNodeWithTag("goat-pedigree:gp-dam").assertExists()
            compose.onNodeWithText("Not on this device").assertExists()
            compose.onNodeWithText("Offspring · 1").assertExists()
        }
    }

    @Test
    fun pregnancyDashboardGroupsDoesAndOpensTheSelectedDoe() {
        val selected = AtomicReference<String?>(null)
        render(nala(), GoatPage.DASHBOARD, others = listOf(openDoe(), buck()), onSelect = selected::set)
        compose.onNodeWithTag(dashboardTag).assertIsDisplayed()

        compose.onNode(hasClickAction() and hasText("Pregnancy")).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-GOAT-035").assertExists()
        compose.onNodeWithText("Confirmed pregnant · 1").assertExists()
        compose.onNodeWithText("Checked open · 1").assertExists()
        compose.onNodeWithText("Kito · GT-011").assertDoesNotExist()

        compose.onNode(hasClickAction() and hasText("Back")).performScrollTo().performClick()
        compose.onNodeWithTag(dashboardTag).assertExists()

        compose.onNode(hasClickAction() and hasText("Pregnancy")).performScrollTo().performClick()
        compose.onNodeWithText("Nala · GT-024").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("goat-nala", selected.get()) }
        compose.onNodeWithTag("farm-screen:FOS-GOAT-030").assertExists()
    }

    @Test
    fun matingSireIsChosenFromTheHerdThroughTheSireSelector() {
        val recorded = AtomicReference<Triple<String, String, String>?>(null)
        render(nala(), GoatPage.REPRODUCTION, others = listOf(openDoe(), buck())) { method, sire, day ->
            recorded.set(Triple(method, sire, day))
        }
        compose.onNodeWithTag("farm-screen:FOS-GOAT-032").assertExists()
        compose.onNodeWithTag("farm-screen:FOS-GOAT-033").assertExists()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-008").assertExists()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-008:option:goat-zuri").assertDoesNotExist()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-008:option:no-sire").assertIsSelected()

        compose.onNodeWithTag("farm-atom:FOS-ATOM-008:option:goat-kito").performScrollTo().performClick()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-008:option:goat-kito").assertIsSelected()
        compose.onNode(hasSetTextAction() and hasText("Mating date")).performScrollTo().performTextInput("2026-09-20")
        compose.onNode(hasClickAction() and hasText("Record mating")).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(Triple("natural", "goat-kito", "2026-09-20"), recorded.get()) }

        compose.onNodeWithTag("farm-atom:FOS-ATOM-008:option:no-sire").performScrollTo().performClick()
        compose.onNode(hasClickAction() and hasText("Record mating")).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(Triple("natural", "", "2026-09-20"), recorded.get()) }
    }

    @Test
    fun matingDateIsChosenThroughTheDatePickerAtomWithoutSubmitting() {
        val recorded = AtomicReference<Triple<String, String, String>?>(null)
        render(nala(), GoatPage.REPRODUCTION) { method, sire, day -> recorded.set(Triple(method, sire, day)) }
        val field = "farm-atom:FOS-ATOM-001:mating"
        compose.onNodeWithTag(field).assertExists()

        compose.onNodeWithTag("$field:open").performScrollTo().performClick()
        compose.onNodeWithTag("$field:calendar").assertExists()
        compose.onNodeWithTag("$field:cancel").performClick()
        compose.onNodeWithTag("$field:calendar").assertDoesNotExist()
        compose.onNode(hasClickAction() and hasText("Record mating")).assertIsNotEnabled()

        compose.onNodeWithTag("$field:open").performScrollTo().performClick()
        compose.onNodeWithTag("$field:confirm").performClick()
        compose.onNodeWithTag("$field:calendar").assertDoesNotExist()
        compose.onNodeWithTag(field).assertTextContains(LocalDate.now().toString())
        compose.runOnIdle { assertEquals(null, recorded.get()) }

        compose.onNodeWithTag(field).performTextClearance()
        compose.onNodeWithTag(field).performTextInput("2026-09-20")
        compose.onNodeWithTag("$field:open").performScrollTo().performClick()
        compose.onNodeWithTag("$field:confirm").performClick()
        compose.onNode(hasClickAction() and hasText("Record mating")).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(Triple("natural", "", "2026-09-20"), recorded.get()) }
    }

    @Test
    fun breedingStatusFollowsTheNewestRecordWithStableSameDayOrder() {
        val base = nala().copy(pregnancyHistory = emptyList(), matingHistory = emptyList(), kiddingHistory = emptyList(), heatHistory = emptyList())
        assertEquals(GoatBreedingStatus.NO_BREEDING_RECORD, GoatReproductionRecords.breedingSummary(base).status)

        val bred = base.copy(matingHistory = listOf(GoatMatingSample("m", null, null, "natural", day(8, 1))))
        assertEquals(GoatBreedingStatus.BRED_NOT_CHECKED, GoatReproductionRecords.breedingSummary(bred).status)

        val checkedSameDay = bred.copy(pregnancyHistory = listOf(GoatPregnancySample("c", "open", day(8, 1))))
        assertEquals(GoatBreedingStatus.OPEN, GoatReproductionRecords.breedingSummary(checkedSameDay).status)

        val reBred = checkedSameDay.copy(matingHistory = checkedSameDay.matingHistory + GoatMatingSample("m2", null, null, "ai", day(9, 1)))
        assertEquals(GoatBreedingStatus.BRED_NOT_CHECKED, GoatReproductionRecords.breedingSummary(reBred).status)

        val kidded = nala().copy(kiddingHistory = listOf(KiddingSample("k", 2, 2, 0, day(9, 20))))
        val summary = GoatReproductionRecords.breedingSummary(kidded)
        assertEquals(GoatBreedingStatus.KIDDED, summary.status)
        assertEquals(day(9, 20), summary.decidedOnEpochDay)
    }

    private fun render(
        selected: GoatSnapshot,
        page: GoatPage,
        others: List<GoatSnapshot> = emptyList(),
        onSelect: (String) -> Unit = {},
        onMating: (String, String, String) -> Unit = { _, _, _ -> },
    ) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                GoatExperienceScreen(
                    state = GoatSliceUiState(farmName = "Premier Farm", herd = listOf(selected) + others, selected = selected, animalId = selected.animalId),
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
                        onRecordMating = onMating,
                        onRecordPregnancy = { _, _ -> },
                        onPlanLactation = {},
                        onSetStatus = {},
                        onSelectGoat = onSelect,
                        onSyncNow = {},
                        onSearch = {},
                    ),
                    onBackToFarm = {},
                    onSignOut = {},
                    initialPage = page,
                    today = LocalDate.of(2026, 9, 24),
                )
            }
        }
    }

    private fun traverse(label: String, screenId: String, originTag: String, assertions: () -> Unit) {
        compose.onNode(hasClickAction() and hasText(label)).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:$screenId").assertExists()
        assertions()
        compose.onNode(hasClickAction() and hasText("Back")).performScrollTo().performClick()
        compose.onNodeWithTag(originTag).assertExists()
        compose.onNodeWithTag("farm-screen:$screenId").assertDoesNotExist()
    }

    private fun nala() = GoatSnapshot(
        animalId = "goat-nala",
        farmId = "farm-reference",
        tag = "GT-024",
        name = "Nala",
        sex = GoatSex.FEMALE,
        latestWeightGrams = null,
        heatHistory = listOf(GoatHeatSample("h1", day(7, 30))),
        matingHistory = listOf(GoatMatingSample("m1", "goat-kito", "GT-011 · Kito", "natural", day(8, 1))),
        pregnancyHistory = listOf(GoatPregnancySample("c1", "pregnant", day(9, 5))),
        pedigree = GoatPedigree(
            parents = listOf(GoatPedigreeLink("p-dam", "goat-asha", "GT-002 · Asha", "dam")),
            grandparents = mapOf("goat-asha" to listOf(GoatPedigreeLink("gp-dam", "goat-old", null, "dam"))),
            offspring = listOf(GoatPedigreeLink("o-1", "goat-kid", "GT-101", "dam")),
        ),
        syncPending = false,
    )

    private fun openDoe() = GoatSnapshot(
        animalId = "goat-zuri",
        farmId = "farm-reference",
        tag = "GT-030",
        name = "Zuri",
        sex = GoatSex.FEMALE,
        latestWeightGrams = null,
        pregnancyHistory = listOf(GoatPregnancySample("c2", "open", day(9, 10))),
        syncPending = false,
    )

    private fun buck() = GoatSnapshot(
        animalId = "goat-kito",
        farmId = "farm-reference",
        tag = "GT-011",
        name = "Kito",
        sex = GoatSex.MALE,
        status = GoatStatus.ACTIVE,
        latestWeightGrams = null,
        syncPending = false,
    )
}
