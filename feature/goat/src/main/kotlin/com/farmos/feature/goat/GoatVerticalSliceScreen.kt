package com.farmos.feature.goat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    onSignOut: () -> Unit,
    onBack: () -> Unit = onSignOut,
    modifier: Modifier = Modifier,
    entryPage: GoatEntryPage = GoatEntryPage.DASHBOARD,
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
        ),
        onBackToFarm = onBack,
        onSignOut = onSignOut,
        modifier = modifier,
    )
}

private fun GoatEntryPage.toGoatPage(): GoatPage =
    when (this) {
        GoatEntryPage.DASHBOARD -> GoatPage.DASHBOARD
        GoatEntryPage.WEIGHT -> GoatPage.WEIGHT
        GoatEntryPage.SEARCH -> GoatPage.SEARCH
    }
