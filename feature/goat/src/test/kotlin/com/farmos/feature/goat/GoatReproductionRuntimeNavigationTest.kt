package com.farmos.feature.goat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.core.design.FarmSearchPage
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.design.FarmSelectorSearch
import com.farmos.core.design.NoFarmSelectorSearch
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
    fun matingSireIsSearchedAcrossTheWholeFarmNotTheCappedHerdList() {
        val recorded = AtomicReference<Triple<String, String, String>?>(null)
        // The capped herd list holds no buck at all; the sires exist only in the whole-farm search.
        val bucks = listOf(
            FarmSelectorOption("goat-bako", "GT-044 · Bako", "Active"),
            FarmSelectorOption("goat-kito", "GT-011 · Kito", "Active"),
        )
        val wholeFarm = FarmSelectorSearch { query, offset, limit ->
            val matches = bucks.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
            FarmSearchPage(matches.drop(offset).take(limit), hasMore = matches.size > offset + limit)
        }
        render(nala(), GoatPage.REPRODUCTION, others = listOf(openDoe()), sires = wholeFarm) { method, sire, day ->
            recorded.set(Triple(method, sire, day))
        }
        compose.onNodeWithTag("farm-screen:FOS-GOAT-032").assertExists()
        compose.onNodeWithTag("farm-screen:FOS-GOAT-033").assertExists()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-008").assertExists()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-008:option:no-sire").assertIsSelected()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-atom:FOS-ATOM-008:option:goat-kito").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("farm-atom:FOS-ATOM-008:option:goat-zuri").assertDoesNotExist()

        compose.onNodeWithTag("farm-atom:FOS-ATOM-008:query").performScrollTo().performTextInput("Kito")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-atom:FOS-ATOM-008:option:goat-bako").fetchSemanticsNodes().isEmpty() }
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
    fun aChosenSireShowsTheKidsInbreedingAndBucksCompareWithoutARanking() {
        val bucks = listOf(FarmSelectorOption("goat-bako", "GT-044 · Bako", "Active"), FarmSelectorOption("goat-kito", "GT-011 · Kito", "Active"))
        val wholeFarm = FarmSelectorSearch { query, offset, limit ->
            val matches = bucks.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
            FarmSearchPage(matches.drop(offset).take(limit), hasMore = matches.size > offset + limit)
        }
        // Kito is Nala's half-brother through a shared sire; Bako is unrelated, with no weight recorded.
        val analysis = GoatMateAnalysis { _, buckId ->
            if (buckId == "goat-kito") {
                GoatMateCandidate(buckId, "GT-011 · Kito", day(3, 2), 61_500, 0.125, 2, listOf("GT-001 · Zeus"), 0)
            } else {
                GoatMateCandidate(buckId, "GT-044 · Bako", null, null, 0.0, 1, emptyList(), 1)
            }
        }
        render(nala(), GoatPage.REPRODUCTION, sires = wholeFarm, mateAnalysis = analysis)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-atom:FOS-ATOM-008:option:goat-kito").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("goat-mating-coi").assertDoesNotExist()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-008:option:goat-kito").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Kids' inbreeding (COI) 12.50% · 2 complete generations recorded")).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithTag("goat-compare-bucks").performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-GOAT-046").assertExists()
        compose.onNodeWithText("For Nala").assertExists()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-atom:FOS-ATOM-008:option:goat-bako").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("farm-atom:FOS-ATOM-008:option:goat-kito").performScrollTo().performClick()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-008:option:goat-bako").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Latest weight: Not recorded")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Latest weight: 61.5 kg").assertExists()
        compose.onNodeWithText("Common ancestors: GT-001 · Zeus").assertExists()
        compose.onNodeWithText("Common ancestors: none recorded").assertExists()
        compose.onNodeWithText("Kids' inbreeding (COI) 0.00% · 1 complete generation recorded").assertExists()
        compose.onNode(hasText("conflicting parentage", substring = true)).assertExists()
        compose.onNodeWithText("No ranking is shown", substring = true).assertExists()

        compose.onNodeWithTag("goat-mate-remove:goat-bako").performScrollTo().performClick()
        compose.onNodeWithTag("goat-mate-candidate:goat-bako").assertDoesNotExist()
        compose.onNodeWithTag("goat-mate-candidate:goat-kito").assertExists()
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
        sires: FarmSelectorSearch = NoFarmSelectorSearch,
        onMating: (String, String, String) -> Unit = { _, _, _ -> },
        mateAnalysis: GoatMateAnalysis = NoGoatMateAnalysis,
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
                        searchSires = sires,
                        mateAnalysis = mateAnalysis,
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
