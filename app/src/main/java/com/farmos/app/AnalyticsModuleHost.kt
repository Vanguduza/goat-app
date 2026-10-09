package com.farmos.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
private enum class AnalyticsPage(val screenId: String, val title: String) {
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

private data class Anomaly(val title: String, val detail: String, val evidence: String)

private data class AnalyticsModel(
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
            val productionLines = database.reports().productionTotals(farmId).map {
                val quantity = when (it.product) {
                    "goat-milk", "cattle-milk" -> reportMilli(it.amount) + " L"
                    "sheep-wool" -> reportMilli(it.amount) + " kg"
                    "poultry-eggs" -> it.amount.toString() + " eggs"
                    else -> it.amount.toString() + " recorded units"
                }
                it.product + ": " + it.records + " record(s) · " + quantity + " total"
            }
            val moneyLines = database.reports().moneyTotals(farmId).map {
                it.kind + " " + reportMoney(BigDecimal.valueOf(it.amountMinor), it.currency) +
                    " (" + it.records + " record(s))"
            }
            val inventory = database.reports().inventoryTotals(farmId)
            val feedLines = database.feedIssues().totalsByItem(farmId).map {
                val item = database.inventory().item(farmId, it.itemId)
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
            )
        }.onSuccess { model = it }.onFailure { error = it.message }
    }

    val current = page
    FarmOperationalPage(
        screenId = current.screenId,
        title = current.title,
        subtitle = "Aggregates from this device's local database. Figures are recorded, not estimated.",
        onBack = if (current == AnalyticsPage.OVERVIEW) onBack else ({ page = AnalyticsPage.OVERVIEW }),
        backLabel = if (current == AnalyticsPage.OVERVIEW) "Farm home" else "Analytics",
    ) {
        if (error != null) {
            FarmOperationalSection("Attention", error) {}
        }
        val m = model
        @Composable
        fun nav(target: AnalyticsPage, label: String) {
            TextButton(onClick = { page = target }, modifier = Modifier.fillMaxWidth()) { Text(label) }
        }
        when (current) {
            AnalyticsPage.OVERVIEW -> {
                FarmOperationalSection("Farm at a glance") {
                    val rows = m?.let {
                        listOf(
                            "Species tracked: ${it.speciesLines.size}",
                            "Health: ${it.health?.observations} observations · ${it.health?.treatments} treatments · ${it.health?.activeWithdrawals} active withdrawals",
                            "Inventory items: ${it.inventory?.items} · ${it.inventory?.atOrBelowReorder} at/below reorder",
                            "Open anomaly flags: ${it.anomalies.count { a -> a.title != "No anomalies" }}",
                        )
                    } ?: listOf("Loading…")
                    rows.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
                FarmOperationalSection("Analyse") {
                    nav(AnalyticsPage.SPECIES, "Species performance")
                    nav(AnalyticsPage.REPRODUCTION, "Reproduction")
                    nav(AnalyticsPage.GROWTH, "Growth")
                    nav(AnalyticsPage.MORTALITY, "Mortality")
                    nav(AnalyticsPage.HEALTH, "Health trends")
                    nav(AnalyticsPage.PRODUCTION, "Production")
                    nav(AnalyticsPage.FEED_EFFICIENCY, "Feed efficiency")
                    nav(AnalyticsPage.FINANCE, "Financial performance")
                    nav(AnalyticsPage.COMPARE, "Benchmark compare")
                    nav(AnalyticsPage.ANOMALIES, "Anomaly centre")
                    nav(AnalyticsPage.FORECASTS, "Forecasts")
                }
            }
            AnalyticsPage.SPECIES -> FarmOperationalRows(
                m?.speciesLines ?: listOf("Loading…"), "No animals recorded",
                "Register animals to see species performance.",
            )
            AnalyticsPage.REPRODUCTION -> FarmOperationalRows(
                m?.birthLines ?: listOf("Loading…"), "No birth records",
                "Record kiddings, lambings or calvings to see reproduction analytics.",
            )
            AnalyticsPage.GROWTH -> FarmOperationalRows(
                m?.growthLines ?: listOf("Loading…"), "No animals recorded", null,
            )
            AnalyticsPage.MORTALITY -> {
                val rows = m?.exitKinds?.map { "${it.kind}: ${it.exits} exit(s)" } ?: listOf("Loading…")
                FarmOperationalRows(rows, "No exits recorded", "Record deaths, culls and sales to see mortality analytics.")
            }
            AnalyticsPage.HEALTH -> {
                val rows = m?.health?.let {
                    listOf(
                        "Observations recorded: ${it.observations}",
                        "Treatments recorded: ${it.treatments}",
                        "Active withdrawal windows: ${it.activeWithdrawals}",
                    )
                } ?: listOf("Loading…")
                FarmOperationalRows(rows, "No health records", null)
            }
            AnalyticsPage.PRODUCTION -> FarmOperationalRows(
                m?.productionLines ?: listOf("Loading…"), "No production records",
                "Record milk, eggs or other production to see trends.",
            )
            AnalyticsPage.FEED_EFFICIENCY -> {
                FarmOperationalSection(
                    "Feed efficiency",
                    "Recorded feed issued alongside recorded production. No conversion ratios are invented.",
                ) {
                    val rows = m?.let { it.feedLines + it.productionLines } ?: listOf("Loading…")
                    if (rows.isEmpty()) Text("No feed or production records.", style = MaterialTheme.typography.bodyMedium)
                    rows.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            AnalyticsPage.FINANCE -> FarmOperationalRows(
                m?.moneyLines ?: listOf("Loading…"), "No money records",
                "Record income and expenses to see financial performance.",
            )
            AnalyticsPage.COMPARE -> {
                FarmOperationalSection("Benchmark compare", "Species side-by-side from the same local herd register.") {
                    val rows = m?.speciesLines ?: listOf("Loading…")
                    rows.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            AnalyticsPage.ANOMALIES -> {
                FarmOperationalSection("Anomaly centre", "Deterministic on-device checks over local records.") {
                    (m?.anomalies ?: listOf()).forEach { anomaly ->
                        TextButton(onClick = { selectedAnomaly = anomaly; page = AnalyticsPage.INSIGHT }, modifier = Modifier.fillMaxWidth()) {
                            Text(anomaly.title)
                        }
                    }
                    if (m == null) Text("Loading…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            AnalyticsPage.FORECASTS -> {
                FarmOperationalSection(
                    "Forecasts",
                    "Whole-history totals do not establish a rate. A forecast needs a dated baseline and explicit assumptions.",
                ) {
                    val rows = m?.productionLines ?: listOf("Loading…")
                    if (rows.isEmpty()) Text("No production records available for a baseline.", style = MaterialTheme.typography.bodyMedium)
                    rows.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            AnalyticsPage.INSIGHT -> {
                val anomaly = selectedAnomaly
                FarmOperationalSection("Insight detail") {
                    if (anomaly == null) {
                        Text("Open an item from the anomaly centre to see its detail.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(anomaly.title, style = MaterialTheme.typography.titleMedium)
                        Text(anomaly.detail, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { page = AnalyticsPage.EVIDENCE }, modifier = Modifier.fillMaxWidth()) {
                            Text("View evidence sources")
                        }
                    }
                }
            }
            AnalyticsPage.EVIDENCE -> {
                val anomaly = selectedAnomaly
                FarmOperationalSection("Evidence drilldown", "The exact local queries behind the insight.") {
                    Text(anomaly?.evidence ?: "No insight selected.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
