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
    SYNC,
    STATUS_CHANGE,
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

internal fun formatKg(grams: Long): String = "%.2f".format(grams / 1_000.0)

internal fun weightDate(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString().take(10)

internal fun herdSearchLabel(result: GoatSearchResult): String = buildString {
    append(result.tag)
    result.name?.let { append(" · ").append(it) }
    append(" · ").append(goatStatusLabel(result.status))
    append(" · ").append(result.source.name.lowercase())
}
