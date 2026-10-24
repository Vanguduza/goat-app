package com.farmos.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.farmos.core.design.FarmActionButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.farmos.core.database.ExitKindCount
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.HealthTotalRow
import com.farmos.core.database.InventoryTotalRow
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalRows
import com.farmos.core.design.FarmOperationalSection
import java.time.LocalDate
import java.math.BigDecimal
import com.farmos.core.design.runSuspendCatching

/** Analytics module pages; each maps to one canonical FOS-AN-* screen. */
internal enum class AnalyticsPage(val screenId: String, val title: String) {
    OVERVIEW("FOS-AN-001", "Farm analytics"),
    SPECIES("FOS-AN-002", "Species performance"),
    REPRODUCTION("FOS-AN-003", "Reproduction analytics"),
    GROWTH("FOS-AN-004", "Growth analytics"),
    MORTALITY("FOS-AN-005", "Mortality analytics"),
    HEALTH("FOS-AN-006", "Health trends"),
    PRODUCTION("FOS-AN-007", "Production analytics"),
    FEED_EFFICIENCY("FOS-AN-008", "Feed efficiency"),
    FINANCE("FOS-AN-009", "Financial performance"),
    COMPARE("FOS-AN-010", "Benchmark compare"),
    ANOMALIES("FOS-AN-011", "Anomaly centre"),
    FORECASTS("FOS-AN-012", "Forecasts"),
    INSIGHT("FOS-AN-013", "Insight detail"),
    EVIDENCE("FOS-AN-014", "Evidence drilldown"),
}

internal data class Anomaly(val title: String, val detail: String, val evidence: String)

internal data class AnalyticsModel(
    val speciesLines: List<String>,
    val birthLines: List<String>,
    val exitKinds: List<ExitKindCount>,
    val health: HealthTotalRow?,
    val productionLines: List<String>,
    val moneyLines: List<String>,
    val inventory: InventoryTotalRow?,
    val feedLines: List<String>,
    val growthLines: List<String>,
    val anomalies: List<Anomaly>,
    val speciesBars: List<com.farmos.core.design.FarmBarValue>,
    val birthBars: List<com.farmos.core.design.FarmBarValue>,
    val growthBars: List<com.farmos.core.design.FarmBarValue>,
    val productionBars: Map<String, List<com.farmos.core.design.FarmBarValue>>,
    val moneyBars: Map<String, List<com.farmos.core.design.FarmBarValue>>,
    val feedBars: Map<String, List<com.farmos.core.design.FarmBarValue>>,
)

/**
 * Dedicated analytics orchestration. Every figure is a local Room aggregate; anomaly flags are
 * deterministic on-device checks, never model output.
 */
@Composable
fun AnalyticsModuleHost(
    database: FarmOsDatabase,
    farmId: String,
    onBack: () -> Unit,
) {
    var page by remember(farmId) { mutableStateOf(AnalyticsPage.OVERVIEW) }
    var selectedAnomaly by remember(farmId) { mutableStateOf<Anomaly?>(null) }
    var model by remember(farmId) { mutableStateOf<AnalyticsModel?>(null) }
    var error by remember(farmId) { mutableStateOf<String?>(null) }

    LaunchedEffect(farmId) {
        runSuspendCatching {
            val today = LocalDate.now().toEpochDay()
            val register = database.reports().herdRegister(farmId)
            val bySpecies = register.groupBy { it.speciesCode }
            val speciesLines = bySpecies.map { (species, rows) ->
                val active = rows.count { it.status == "active" }
                val weighed = rows.mapNotNull { it.latestWeightGrams }
                val avgWeight = if (weighed.isEmpty()) "no weights" else reportMilli(weighed.average().toLong()) + " kg avg latest"
                "${species.replaceFirstChar { c -> c.uppercase() }}: $active active of ${rows.size} · $avgWeight"
            }.sorted()
            val births = database.reports().birthTotals(farmId)
            val birthLines = births.map { "${it.speciesCode}: ${it.events} event(s) · ${it.live} live · ${it.dead} dead" }
            val exitKinds = database.animalExits().exitKindCounts(farmId)
            val health = database.reports().healthTotals(farmId, today)
            val productionRows = database.reports().productionTotals(farmId)
            val productionLines = productionRows.map {
                val quantity = when (it.product) {
                    "goat-milk", "cattle-milk" -> reportMilli(it.amount) + " L"
                    "sheep-wool" -> reportMilli(it.amount) + " kg"
                    "poultry-eggs" -> it.amount.toString() + " eggs"
                    else -> it.amount.toString() + " recorded units"
                }
                it.product + ": " + it.records + " record(s) · " + quantity + " total"
            }
            val moneyRows = database.reports().moneyTotals(farmId)
            val moneyLines = moneyRows.map {
                it.kind + " " + reportMoney(BigDecimal.valueOf(it.amountMinor), it.currency) +
                    " (" + it.records + " record(s))"
            }
            val inventory = database.reports().inventoryTotals(farmId)
            val feedBars = mutableMapOf<String, MutableList<com.farmos.core.design.FarmBarValue>>()
            val feedLines = database.feedIssues().totalsByItem(farmId).map {
                val item = database.inventory().item(farmId, it.itemId)
                val unit = item?.unit ?: "unit unavailable"
                feedBars.getOrPut(unit) { mutableListOf() }.add(com.farmos.core.design.FarmBarValue(item?.name ?: it.itemId, if (item == null) null else BigDecimal.valueOf(it.quantityMilli, 3).toDouble(), reportMilli(it.quantityMilli) + " " + unit, "${it.issueCount} issues"))
                (item?.name ?: it.itemId) + ": " + reportMilli(it.quantityMilli) + " " +
                    (item?.unit ?: "(unit unavailable)") + " in " + it.issueCount + " issue(s)"
            }
            val growthLines = bySpecies.map { (species, rows) ->
                val weighed = rows.mapNotNull { it.latestWeightGrams }
                "$species: ${weighed.size} of ${rows.size} animals have a recorded weight"
            }.sorted()

            val anomalies = mutableListOf<Anomaly>()
            val deaths = exitKinds.firstOrNull { it.kind == "DEATH" }?.exits ?: 0
            if (deaths > 0) anomalies += Anomaly(
                "Mortality recorded",
                "$deaths death exit(s) recorded on this farm.",
                "Evidence: animal_exits grouped by kind (AnimalExitDao.exitKindCounts).",
            )
            if ((inventory?.atOrBelowReorder ?: 0) > 0) anomalies += Anomaly(
                "Inventory at reorder level",
                "${inventory?.atOrBelowReorder} item(s) at or below reorder quantity.",
                "Evidence: inventory totals (FarmReportDao.inventoryTotals).",
            )
            if (health.activeWithdrawals > 0) anomalies += Anomaly(
                "Active withdrawal windows",
                "${health.activeWithdrawals} active medication withdrawal window(s).",
                "Evidence: health totals (FarmReportDao.healthTotals).",
            )
            if (anomalies.isEmpty()) anomalies += Anomaly(
                "No anomalies",
                "None of the on-device checks fired on current records.",
                "Evidence: exit kinds, inventory totals, health totals.",
            )

            AnalyticsModel(
                speciesLines = speciesLines,
                birthLines = birthLines,
                exitKinds = exitKinds,
                health = health,
                productionLines = productionLines,
                moneyLines = moneyLines,
                inventory = inventory,
                feedLines = feedLines,
                growthLines = growthLines,
                anomalies = anomalies,
                speciesBars = bySpecies.toSortedMap().map { (species, rows) -> com.farmos.core.design.FarmBarValue(species.replaceFirstChar { it.uppercase() }, rows.count { it.status == "active" }.toDouble(), "${rows.count { it.status == "active" }} active", "${rows.size} animals recorded") },
                birthBars = births.flatMap { row -> listOf(com.farmos.core.design.FarmBarValue("${row.speciesCode} · live", row.live.toDouble(), "${row.live} young", "${row.events} birth events"), com.farmos.core.design.FarmBarValue("${row.speciesCode} · dead", row.dead.toDouble(), "${row.dead} young", "${row.events} birth events")) },
                growthBars = bySpecies.toSortedMap().map { (species, rows) ->
                    val known = rows.count { it.latestWeightGrams != null }
                    com.farmos.core.design.FarmBarValue(species, if (rows.isEmpty()) null else 100.0 * known / rows.size, "$known of ${rows.size} animals", "A recorded weight; not a growth-rate estimate")
                },
                productionBars = productionRows.groupBy { when (it.product) { "goat-milk", "cattle-milk" -> "L"; "sheep-wool" -> "kg"; "poultry-eggs" -> "eggs"; else -> "recorded units · ${it.product}" } }.mapValues { (unit, rows) -> rows.map {
                    val milli = it.product in setOf("goat-milk", "cattle-milk", "sheep-wool")
                    com.farmos.core.design.FarmBarValue(it.product, if (milli) BigDecimal.valueOf(it.amount, 3).toDouble() else it.amount.toDouble(), (if (milli) reportMilli(it.amount) else it.amount.toString()) + " " + unit, "${it.records} records")
                } },
                moneyBars = moneyRows.groupBy { it.currency }.mapValues { (currency, rows) -> rows.map { com.farmos.core.design.FarmBarValue(it.kind, BigDecimal.valueOf(it.amountMinor, com.farmos.domain.ops.FarmCurrency.minorDigits(currency)).toDouble(), reportMoney(BigDecimal.valueOf(it.amountMinor), currency), "${it.records} records") } },
                feedBars = feedBars,
            )
        }.onSuccess { model = it }.onFailure { error = it.message }
    }

    AnalyticsModuleScreen(page, model, error, selectedAnomaly, onBack, { page = it }, { selectedAnomaly = it; page = AnalyticsPage.INSIGHT })
}
