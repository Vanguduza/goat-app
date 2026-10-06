package com.farmos.feature.goat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.FarmIllustratedSectionSurface
import com.farmos.domain.goat.GoatRegisteredKid
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.GoatSnapshot
import java.time.LocalDate

private fun GoatRegisteredKid.sexLabel(): String = when (sex) {
    GoatSex.FEMALE -> "Doeling"
    GoatSex.MALE -> "Buckling"
    null -> "Sex not on this device"
}

/** FOS-GOAT-038 — one recorded kidding of the selected doe and the kids registered from it. */
@Composable
internal fun GoatKiddingDetailScreen(goat: GoatSnapshot?, kiddingId: String?, onBack: () -> Unit) {
    GoatHistoryFrame("Kidding", "FOS-GOAT-038", goat, onBack) { dam ->
        val kidding = dam.kiddingHistory.firstOrNull { it.kiddingId == kiddingId }
        if (kidding == null) {
            AnimalFarmEmptyState("This kidding is not recorded for this doe on this device.")
            return@GoatHistoryFrame
        }
        val kids = dam.kidsByKidding[kidding.kiddingId].orEmpty()
        FarmIllustratedSectionSurface {
            Text(LocalDate.ofEpochDay(kidding.occurredEpochDay).toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            GoatHistoryRow("Born", kidding.bornCount.toString())
            GoatHistoryRow("Live", kidding.liveCount.toString())
            GoatHistoryRow("Dead", kidding.deadCount.toString())
            GoatHistoryRow("Kids registered", "${kids.size} of ${kidding.liveCount} live", Modifier.testTag("goat-kidding-registered"))
        }
        FarmIllustratedSectionSurface {
            Text("Registered kids", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (kids.isEmpty()) Text("No kids registered from this kidding on this device.", color = AnimalFarmTheme.colors.mutedInk)
            kids.forEach { kid -> GoatHistoryRow(kid.label, kid.sexLabel(), Modifier.testTag("goat-kidding-kid:${kid.animalId}")) }
        }
    }
}

/** FOS-GOAT-041 — birth record of a goat registered as a kid. */
@Composable
internal fun GoatKidProfileScreen(goat: GoatSnapshot?, onBack: () -> Unit) {
    GoatHistoryFrame("Kid profile", "FOS-GOAT-041", goat, onBack) { kid ->
        val birth = kid.birthRecord
        if (birth == null) {
            AnimalFarmEmptyState("This goat was not registered from a recorded kidding on this device.")
            return@GoatHistoryFrame
        }
        FarmIllustratedSectionSurface {
            GoatHistoryRow("Dam", birth.damLabel ?: "Not on this device")
            GoatHistoryRow("Born", birth.kiddingEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: "Kidding not on this device")
            if (birth.bornCount != null && birth.liveCount != null) {
                GoatHistoryRow("Litter", "${birth.bornCount} born · ${birth.liveCount} live")
            }
            GoatRecordHistory.weightsAscending(kid).firstOrNull()?.let { first ->
                GoatHistoryRow(
                    "Earliest weight · ${GoatRecordHistory.weightDay(first.measuredAtEpochMillis)}",
                    "${formatKg(first.weightGrams)} kg",
                )
            }
        }
        FarmIllustratedSectionSurface {
            Text("Littermates · ${birth.littermates.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            birth.littermates.forEach { sibling -> GoatHistoryRow(sibling.label, sibling.sexLabel(), Modifier.testTag("goat-littermate:${sibling.animalId}")) }
        }
    }
}

/** FOS-GOAT-040 — registered kids grouped by the kidding they came from. */
@Composable
internal fun GoatKidCohortScreen(herd: List<GoatSnapshot>, onSelectKid: (String) -> Unit, onBack: () -> Unit, herdTotal: Int? = null) {
    IllustratedGoatPage("Kids", "FOS-GOAT-040", onBack) {
        goatHerdBoundNotice(herd.size, herdTotal)?.let {
            Text(it, color = AnimalFarmTheme.colors.mutedInk, modifier = Modifier.testTag("goat-kid-cohort-bounded"))
        }
        val cohorts = herd.filter { it.birthRecord != null }
            .groupBy { it.birthRecord!!.kiddingId }
            .values
            .sortedWith(compareByDescending<List<GoatSnapshot>> { it.first().birthRecord!!.kiddingEpochDay ?: Long.MIN_VALUE }.thenBy { it.first().birthRecord!!.kiddingId })
        if (cohorts.isEmpty()) {
            AnimalFarmEmptyState("No kids registered from recorded kiddings on this device.")
            return@IllustratedGoatPage
        }
        cohorts.forEach { kids ->
            val birth = kids.first().birthRecord!!
            FarmIllustratedSectionSurface(Modifier.testTag("goat-kid-cohort:${birth.kiddingId}")) {
                Text(
                    (birth.damLabel ?: "Dam not on this device") + " · " + (birth.kiddingEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: "date not on this device"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                kids.sortedBy { it.tag }.forEachIndexed { index, kid ->
                    if (index > 0) HorizontalDivider()
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                            .clickable(role = Role.Button) { onSelectKid(kid.animalId) }
                            .padding(vertical = 6.dp),
                    ) {
                        Text(goatDisplayName(kid) + " · " + kid.tag, fontWeight = FontWeight.SemiBold)
                        Text(goatStatusLabel(kid.status), color = AnimalFarmTheme.colors.mutedInk)
                    }
                }
            }
        }
    }
}
