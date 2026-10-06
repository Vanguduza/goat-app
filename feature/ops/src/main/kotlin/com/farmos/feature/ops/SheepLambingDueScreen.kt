package com.farmos.feature.ops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.AnimalFarmWarningSurface
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmVisualClass
import java.time.LocalDate

/**
 * One mob's latest joining. Lambing opens [earliestEpochDay] and is typical from [typicalEpochDay] after
 * ram-in; no ram-out is recorded, so no last lambing day is claimed.
 */
data class SheepMobLambingView(
    val groupId: String,
    val groupName: String,
    val headCount: Int,
    val ramInEpochDay: Long,
    val earliestEpochDay: Long,
    val typicalEpochDay: Long,
)

/** A ewe whose latest scan found her in lamb, with no lambing recorded since. */
data class SheepInLambView(val animalId: String, val tag: String, val name: String?, val result: String, val scanEpochDay: Long)

data class SheepLambingDue(val mobs: List<SheepMobLambingView>, val ewes: List<SheepInLambView>, val typicalDays: Int)

private sealed interface LambingDueState {
    data object Loading : LambingDueState
    data class Failed(val message: String) : LambingDueState
    data class Loaded(val due: SheepLambingDue) : LambingDueState
}

private fun lambDate(epochDay: Long) = LocalDate.ofEpochDay(epochDay).toString()

private fun scanLabel(result: String) = when (result) {
    "single" -> "Scanned single"
    "twin" -> "Scanned twins"
    "triplet" -> "Scanned triplets"
    else -> "Scanned $result"
}

/**
 * FOS-SHEEP-013 — lambing by mob from each mob's latest ram-in and the farm's sheep gestation period, and
 * every ewe scanned in lamb with no lambing recorded since. Joinings are recorded per mob, so no per-ewe
 * date is predicted. Read-only.
 */
@Composable
internal fun SheepLambingDueScreen(loadDue: suspend () -> SheepLambingDue, today: LocalDate, onBack: () -> Unit) {
    var state by remember { mutableStateOf<LambingDueState>(LambingDueState.Loading) }
    LaunchedEffect(Unit) {
        state = runCatching { loadDue() }.fold({ LambingDueState.Loaded(it) }, { LambingDueState.Failed(it.message ?: "Lambing schedule could not be loaded") })
    }
    FarmOperationalPage(
        "FOS-SHEEP-013",
        "Lambing due",
        "When each mob starts lambing, and the ewes scanned in lamb that have not lambed yet.",
        FarmVisualClass.I2,
        onBack,
    ) {
        when (val current = state) {
            LambingDueState.Loading -> Text("Loading lambing schedule")
            is LambingDueState.Failed -> AnimalFarmWarningSurface { Text(current.message) }
            is LambingDueState.Loaded -> {
                val due = current.due
                if (due.mobs.isEmpty() && due.ewes.isEmpty()) {
                    AnimalFarmEmptyState("No joinings or in-lamb scans recorded.")
                    return@FarmOperationalPage
                }
                val todayEpochDay = today.toEpochDay()
                FarmOperationalSection(
                    "Mobs · ${due.mobs.size}",
                    "From each mob's latest ram-in with this farm's ${due.typicalDays}-day gestation. Ram-out is not recorded, so no last lambing day is given.",
                ) {
                    if (due.mobs.isEmpty()) Text("No joinings recorded.", color = AnimalFarmTheme.colors.mutedInk)
                    due.mobs.forEachIndexed { index, mob ->
                        if (index > 0) HorizontalDivider()
                        val status = when {
                            todayEpochDay < mob.earliestEpochDay -> "Lambing opens in ${mob.earliestEpochDay - todayEpochDay} days"
                            else -> "Lambing window open since ${lambDate(mob.earliestEpochDay)}"
                        }
                        DueRow(
                            "${mob.groupName} · ${mob.headCount} head recorded",
                            "Ram in ${lambDate(mob.ramInEpochDay)} · lambing from about ${lambDate(mob.typicalEpochDay)} (earliest ${lambDate(mob.earliestEpochDay)})",
                            status,
                            "sheep-due-mob:${mob.groupId}",
                        )
                    }
                }
                FarmOperationalSection("Scanned in lamb, not yet lambed · ${due.ewes.size}") {
                    if (due.ewes.isEmpty()) Text("No ewes scanned in lamb are waiting to lamb.", color = AnimalFarmTheme.colors.mutedInk)
                    due.ewes.forEachIndexed { index, ewe ->
                        if (index > 0) HorizontalDivider()
                        DueRow(
                            listOfNotNull(ewe.name, ewe.tag).joinToString(" · "),
                            "${scanLabel(ewe.result)} on ${lambDate(ewe.scanEpochDay)}",
                            null,
                            "sheep-due-ewe:${ewe.animalId}",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DueRow(primary: String, secondary: String, detail: String?, tag: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(tag), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(primary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(secondary)
        detail?.let { Text(it, color = AnimalFarmTheme.colors.mutedInk) }
    }
}
