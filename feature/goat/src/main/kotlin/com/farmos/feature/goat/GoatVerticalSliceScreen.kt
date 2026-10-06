package com.farmos.feature.goat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.farmos.core.design.FarmSelectorSearch
import com.farmos.core.design.NoFarmSelectorSearch
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.GoatStatus

/**
 * Compatibility entrypoint for the proven goat architecture slice.
 *
 * The original proving mega-screen has been decomposed into the canonical Farm OS goat
 * experience without changing the command/state boundary consumed by FarmSessionContent.
 * Visual surfaces now map to atomic FOS-GOAT Screen IDs while writes still flow through
 * the existing Room/outbox/RPC/reconciliation architecture.
 */
@Composable
fun GoatVerticalSliceScreen(
    state: GoatSliceUiState,
    onRegister: (tag: String, name: String?, sex: GoatSex, dateOfBirthText: String) -> Unit,
    onRecordWeight: (weightKgText: String) -> Unit,
    onRecordKidding: (bornText: String, liveText: String, deadText: String, dayText: String) -> Unit,
    onRegisterKid: (kiddingId: String, tag: String, sex: GoatSex) -> Unit,
    onRecordFamacha: (scoreText: String, dayText: String) -> Unit,
    onRecordMilk: (litresText: String, dayText: String) -> Unit,
    onRecordBcs: (scoreTenthsText: String, dayText: String) -> Unit,
    onRecordScc: (cellsText: String, dimText: String, dayText: String) -> Unit,
    onRecordHeat: (dayText: String) -> Unit,
    onRecordMating: (method: String, sireId: String, dayText: String) -> Unit,
    onRecordPregnancy: (result: String, dayText: String) -> Unit,
    onPlanLactation: (dayText: String) -> Unit,
    onSetStatus: (GoatStatus) -> Unit,
    onSelectGoat: (animalId: String) -> Unit,
    onSyncNow: () -> Unit,
    onSearch: (query: String) -> Unit,
    onScanIdentifier: (identifier: String) -> Unit = onSearch,
    onSignOut: () -> Unit,
    onBack: () -> Unit = onSignOut,
    modifier: Modifier = Modifier,
    entryPage: GoatEntryPage = GoatEntryPage.DASHBOARD,
    searchSires: FarmSelectorSearch = NoFarmSelectorSearch,
    /** Records how the selected goat left the herd (D-022). */
    onRecordExit: (GoatExitDraft) -> Unit = {},
    /** Reverses the selected goat's standing exit with a reason. */
    onReverseExit: (exitId: String, reason: String) -> Unit = { _, _ -> },
    /** Reads a prospective mating from the local pedigree (D-023). */
    mateAnalysis: GoatMateAnalysis = NoGoatMateAnalysis,
    /** Photos and documents on a goat's profile (D-015). */
    profileAttachments: @Composable (animalId: String, active: Boolean) -> Unit = { _, _ -> },
    /** Whole-farm doe search for the dam selector; never the capped herd list. */
    searchDams: FarmSelectorSearch = NoFarmSelectorSearch,
    /** Amends the selected goat's tag/name/official identifier (FOS-GOAT-005). */
    onAmendIdentity: (tag: String, name: String, officialId: String) -> Unit = { _, _, _ -> },
    /** Records weaning for the selected goat (FOS-GOAT-042). */
    onRecordWeaning: (weightKgText: String, dayText: String) -> Unit = { _, _ -> },
    /** Records an official movement for the selected goat (FOS-GOAT-049). */
    onRecordMovement: (direction: String, fromPlace: String, toPlace: String, dayText: String) -> Unit = { _, _, _, _ -> },
    /** Assigns an identifier to the selected goat (FOS-GOAT-050). */
    onAssignIdentifier: (type: String, value: String) -> Unit = { _, _ -> },
    /** Links a parent to the selected goat (FOS-GOAT-045). */
    onLinkParentage: (parentId: String, relationType: String) -> Unit = { _, _ -> },
    weanings: List<GoatWeaningView> = emptyList(),
    movements: List<GoatMovementView> = emptyList(),
    identifiers: List<GoatIdentifierView> = emptyList(),
    goatGroups: List<GoatGroupView> = emptyList(),
    pedigreeParentLabels: List<String> = emptyList(),
) {
    GoatExperienceScreen(
        state = state,
        initialPage = entryPage.toGoatPage(),
        actions = GoatExperienceActions(
            onRegister = onRegister,
            onRecordWeight = onRecordWeight,
            onRecordKidding = onRecordKidding,
            onRegisterKid = onRegisterKid,
            onRecordFamacha = onRecordFamacha,
            onRecordMilk = onRecordMilk,
            onRecordBcs = onRecordBcs,
            onRecordScc = onRecordScc,
            onRecordHeat = onRecordHeat,
            onRecordMating = onRecordMating,
            onRecordPregnancy = onRecordPregnancy,
            onPlanLactation = onPlanLactation,
            onSetStatus = onSetStatus,
            onSelectGoat = onSelectGoat,
            onSyncNow = onSyncNow,
            onSearch = onSearch,
            onScanIdentifier = onScanIdentifier,
            searchSires = searchSires,
            searchDams = searchDams,
            onRecordExit = onRecordExit,
            onReverseExit = onReverseExit,
            mateAnalysis = mateAnalysis,
            profileAttachments = profileAttachments,
            onAmendIdentity = onAmendIdentity,
            onRecordWeaning = onRecordWeaning,
            onRecordMovement = onRecordMovement,
            onAssignIdentifier = onAssignIdentifier,
            onLinkParentage = onLinkParentage,
        ),
        onBackToFarm = onBack,
        onSignOut = onSignOut,
        modifier = modifier,
        weanings = weanings,
        movements = movements,
        identifiers = identifiers,
        goatGroups = goatGroups,
        pedigreeParentLabels = pedigreeParentLabels,
    )
}

private fun GoatEntryPage.toGoatPage(): GoatPage =
    when (this) {
        GoatEntryPage.DASHBOARD -> GoatPage.DASHBOARD
        GoatEntryPage.WEIGHT -> GoatPage.WEIGHT
        GoatEntryPage.SEARCH -> GoatPage.SEARCH
        GoatEntryPage.SCAN -> GoatPage.SCAN
        GoatEntryPage.SYNC -> GoatPage.SYNC
        GoatEntryPage.KIDDING -> GoatPage.KIDDING
        GoatEntryPage.REPRODUCTION -> GoatPage.REPRODUCTION
    }
