package com.farmos.feature.goat

import com.farmos.domain.goat.GoatSearchResult
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.GoatSnapshot
import com.farmos.domain.goat.GoatStatus
import java.time.Instant

internal enum class GoatPage {
    DASHBOARD,
    HERD,
    PROFILE,
    REGISTER,
    WEIGHT,
    HEALTH,
    REPRODUCTION,
    KIDDING,
    SEARCH,
    SCAN,
    SYNC,
    STATUS_CHANGE,
    TIMELINE,
    GROWTH_HISTORY,
    GROWTH_CHART,
    ADG_DETAIL,
    LACTATION_HISTORY,
    SCC_HISTORY,
    FAMACHA_HISTORY,
    HEALTH_SUMMARY,
    TREATMENT_HISTORY,
    WITHDRAWAL_STATUS,
    VET_VISITS,
    LAB_RESULTS,
    DOE_REPRODUCTION,
    PREGNANCY_DASHBOARD,
    LACTATION_DASHBOARD,
    PEDIGREE,
    KIDDING_DETAIL,
    KID_COHORT,
    KID_PROFILE,
}

internal data class GoatExperienceActions(
    val onRegister: (String, String?, GoatSex, String) -> Unit,
    val onRecordWeight: (String) -> Unit,
    val onRecordKidding: (String, String, String, String) -> Unit,
    val onRegisterKid: (String, String, GoatSex) -> Unit,
    val onRecordFamacha: (String, String) -> Unit,
    val onRecordMilk: (String, String) -> Unit,
    val onRecordBcs: (String, String) -> Unit,
    val onRecordScc: (String, String, String) -> Unit,
    val onRecordHeat: (String) -> Unit,
    val onRecordMating: (String, String, String) -> Unit,
    val onRecordPregnancy: (String, String) -> Unit,
    val onPlanLactation: (String) -> Unit,
    val onSetStatus: (GoatStatus) -> Unit,
    val onSelectGoat: (String) -> Unit,
    val onSyncNow: () -> Unit,
    val onSearch: (String) -> Unit,
    val onScanIdentifier: (String) -> Unit = {},
)

internal fun goatDisplayName(goat: GoatSnapshot): String = goat.name?.takeIf { it.isNotBlank() } ?: goat.tag

internal fun goatStatusLabel(status: GoatStatus): String = when (status) {
    GoatStatus.ACTIVE -> "Active"
    GoatStatus.SOLD -> "Sold"
    GoatStatus.DEAD -> "Deceased"
    GoatStatus.CULLED -> "Culled"
    GoatStatus.CLOSED -> "Closed"
}

internal fun goatStatusLabel(raw: String): String =
    runCatching { goatStatusLabel(GoatStatus.fromWire(raw)) }.getOrDefault(raw)

/** States when a herd-derived page was built from the bounded herd list rather than every goat. */
internal fun goatHerdBoundNotice(shown: Int, total: Int?): String? =
    total?.takeIf { it > shown }?.let { "Built from the first $shown of $it goats by tag. Goats beyond these are not shown here." }

internal fun formatKg(grams: Long): String = "%.2f".format(grams / 1_000.0)

internal fun weightDate(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString().take(10)

internal fun herdSearchLabel(result: GoatSearchResult): String = buildString {
    append(result.tag)
    result.name?.let { append(" · ").append(it) }
    append(" · ").append(goatStatusLabel(result.status))
    append(" · ").append(result.source.name.lowercase())
}
