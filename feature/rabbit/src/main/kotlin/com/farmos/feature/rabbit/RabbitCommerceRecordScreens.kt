package com.farmos.feature.rabbit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmVisualClass
import java.time.LocalDate

/** One buyer reservation on the rabbit waitlist, exactly as recorded. */
data class RabbitReservationView(
    val id: String,
    val contactName: String,
    val desiredSex: String?,
    val qty: Int,
    val status: String,
    /** Label of the kit matched on fulfilment; null while unmatched. */
    val matchedKitLabel: String?,
)

/** One agreed rabbit sale contract. [amountMinor] is shown exactly as stored, in minor units of [currency]. */
data class RabbitContractView(
    val id: String,
    val buyerName: String,
    val reservationLabel: String?,
    val animalLabel: String?,
    val amountMinor: Long,
    val currency: String,
    val status: String,
    val epochDay: Long,
)

/** One recorded kit retention decision. */
data class RabbitRetentionView(val id: String, val kitLabel: String, val decision: String, val epochDay: Long)

/** One recorded market plan for a kit or a breeding wave. */
data class RabbitMarketPlanView(
    val id: String,
    val subjectLabel: String,
    val targetWeightGrams: Int,
    val targetEpochDay: Long,
    val purpose: String,
    val status: String,
)

/**
 * Every recorded rabbit commerce row on the farm, read exhaustively from local farm-scoped tables.
 * Nothing here writes, allocates or prices; contract amounts are never added across currencies.
 */
data class RabbitCommerceRecords(
    val reservations: List<RabbitReservationView> = emptyList(),
    val contracts: List<RabbitContractView> = emptyList(),
    val retention: List<RabbitRetentionView> = emptyList(),
    val plans: List<RabbitMarketPlanView> = emptyList(),
)

enum class RabbitCommercePage(val label: String) {
    RETENTION("Retention decisions"),
    MARKET_PLANS("Market plans"),
    RESERVATIONS("Reservations"),
    CONTRACTS("Contracts"),
}

/** Decision codes accepted by the retention command; any other stored value is shown as stored. */
fun rabbitRetentionLabel(decision: String): String = when (decision) {
    "keep_breeder" -> "Keep as breeder"
    "grow_meat" -> "Grow for meat"
    "sale_pet" -> "Sell as pet"
    "cull" -> "Cull"
    "undecided" -> "Undecided"
    else -> decision
}

internal fun rabbitPlanPurposeLabel(purpose: String): String = when (purpose) {
    "meat" -> "Meat"
    "pet_sale" -> "Pet sale"
    "show" -> "Show"
    else -> purpose
}

private fun minorAmount(amountMinor: Long, currency: String) = "$amountMinor $currency minor units"

@Composable
private fun CommerceRow(primary: String, secondary: String, tag: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(tag), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(primary, color = AnimalFarmTheme.colors.mutedInk)
        Text(secondary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Rabbit commerce record navigation. [home] renders the waitlist home (FOS-RABBIT-027) and places
 * the supplied record actions; each action opens a read-only record page whose back returns home.
 */
@Composable
fun RabbitCommerceRecordNavigator(records: RabbitCommerceRecords, home: @Composable (recordActions: @Composable () -> Unit) -> Unit) {
    var page by rememberSaveable { mutableStateOf<RabbitCommercePage?>(null) }
    val back = { page = null }
    when (page) {
        null -> home {
            FarmOperationalSection("Records") {
                RabbitCommercePage.entries.forEach { target ->
                    TextButton(onClick = { page = target }, modifier = Modifier.fillMaxWidth()) { Text("Open ${target.label}") }
                }
            }
        }
        RabbitCommercePage.RETENTION -> RabbitRetentionScreen(records, back)
        RabbitCommercePage.MARKET_PLANS -> RabbitMarketPlanScreen(records, back)
        RabbitCommercePage.RESERVATIONS -> RabbitReservationScreen(records, back)
        RabbitCommercePage.CONTRACTS -> RabbitContractScreen(records, back)
    }
}

/** FOS-RABBIT-024 — recorded kit retention decisions, newest first. */
@Composable
internal fun RabbitRetentionScreen(records: RabbitCommerceRecords, onBack: () -> Unit) {
    FarmOperationalPage("FOS-RABBIT-024", "Retention decisions", "Retention decisions recorded for kits on this device, newest first.", FarmVisualClass.I3, onBack) {
        if (records.retention.isEmpty()) {
            AnimalFarmEmptyState("No retention decisions recorded on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection("By decision") {
            records.retention.groupingBy { it.decision }.eachCount().toSortedMap().forEach { (decision, count) ->
                CommerceRow(rabbitRetentionLabel(decision), count.toString(), "rabbit-retention-count:$decision")
            }
        }
        FarmOperationalSection("Decisions · ${records.retention.size}") {
            records.retention.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider()
                CommerceRow("${LocalDate.ofEpochDay(row.epochDay)} · ${row.kitLabel}", rabbitRetentionLabel(row.decision), "rabbit-retention:${row.id}")
            }
        }
    }
}

/** FOS-RABBIT-026 — recorded market plans with their recorded targets. No readiness is predicted. */
@Composable
internal fun RabbitMarketPlanScreen(records: RabbitCommerceRecords, onBack: () -> Unit) {
    FarmOperationalPage("FOS-RABBIT-026", "Market plans", "Market plans recorded on this device with their recorded target weight and date.", FarmVisualClass.I3, onBack) {
        if (records.plans.isEmpty()) {
            AnimalFarmEmptyState("No market plans recorded on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection("Plans · ${records.plans.size}") {
            records.plans.forEachIndexed { index, plan ->
                if (index > 0) HorizontalDivider()
                CommerceRow(
                    "${plan.subjectLabel} · ${rabbitPlanPurposeLabel(plan.purpose)} · ${plan.status}",
                    "Target ${plan.targetWeightGrams} g by ${LocalDate.ofEpochDay(plan.targetEpochDay)}",
                    "rabbit-plan:${plan.id}",
                )
            }
        }
    }
}

/** FOS-RABBIT-028 — buyer reservations on the waitlist and their recorded matches. */
@Composable
internal fun RabbitReservationScreen(records: RabbitCommerceRecords, onBack: () -> Unit) {
    FarmOperationalPage("FOS-RABBIT-028", "Reservations", "Buyer reservations recorded on the waitlist on this device.", FarmVisualClass.I3, onBack) {
        if (records.reservations.isEmpty()) {
            AnimalFarmEmptyState("No reservations recorded on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection("By status") {
            records.reservations.groupingBy { it.status }.eachCount().toSortedMap().forEach { (status, count) ->
                CommerceRow(status, count.toString(), "rabbit-reservation-count:$status")
            }
        }
        FarmOperationalSection("Reservations · ${records.reservations.size}") {
            records.reservations.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider()
                CommerceRow(
                    "${row.contactName} · ${row.status}",
                    "Quantity ${row.qty} · sex ${row.desiredSex ?: "not recorded"}" + (row.matchedKitLabel?.let { " · matched $it" } ?: ""),
                    "rabbit-reservation:${row.id}",
                )
            }
        }
    }
}

/** FOS-RABBIT-029 — agreed contracts, newest first, totalled per currency. */
@Composable
internal fun RabbitContractScreen(records: RabbitCommerceRecords, onBack: () -> Unit) {
    FarmOperationalPage("FOS-RABBIT-029", "Contracts", "Rabbit sale contracts recorded on this device, newest first. Amounts are shown as stored.", FarmVisualClass.I3, onBack) {
        if (records.contracts.isEmpty()) {
            AnimalFarmEmptyState("No contracts recorded on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection("Totals by currency") {
            records.contracts.groupBy { it.currency }.toSortedMap().forEach { (currency, rows) ->
                CommerceRow(
                    currency,
                    "${minorAmount(rows.sumOf { it.amountMinor }, currency)} · ${rows.size} " + if (rows.size == 1) "contract" else "contracts",
                    "rabbit-contract-total:$currency",
                )
            }
        }
        FarmOperationalSection("Contracts · ${records.contracts.size}") {
            records.contracts.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider()
                val links = listOfNotNull(row.reservationLabel?.let { "reservation $it" }, row.animalLabel?.let { "animal $it" })
                CommerceRow(
                    "${LocalDate.ofEpochDay(row.epochDay)} · ${row.buyerName} · ${row.status}",
                    minorAmount(row.amountMinor, row.currency) + if (links.isEmpty()) "" else " · " + links.joinToString(" · "),
                    "rabbit-contract:${row.id}",
                )
            }
        }
    }
}
