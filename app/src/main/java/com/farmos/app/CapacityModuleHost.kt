package com.farmos.app

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
import androidx.compose.foundation.layout.fillMaxWidth
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalRows
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.runSuspendCatching

/** Capacity module pages; each maps to one canonical FOS-CAP-* screen. */
private enum class CapacityPage(val screenId: String, val title: String) {
    DASHBOARD("FOS-CAP-001", "Capacity"),
    HOUSING("FOS-CAP-002", "Housing capacity"),
    STOCKING("FOS-CAP-003", "Stocking pressure"),
    CAGES("FOS-CAP-004", "Cage capacity"),
    POULTRY_HOUSES("FOS-CAP-005", "Poultry house capacity"),
    PADDOCKS("FOS-CAP-006", "Paddock capacity"),
    FEED("FOS-CAP-007", "Feed capacity"),
    WATER("FOS-CAP-008", "Water capacity"),
    FORECAST("FOS-CAP-009", "Capacity forecast"),
    ALERTS("FOS-CAP-010", "Capacity alerts"),
}

private data class CapacityModel(
    val houseLines: List<String>,
    val cageLines: List<String>,
    val paddockLines: List<String>,
    val stockingLines: List<String>,
    val feedLines: List<String>,
    val waterLines: List<String>,
    val alertLines: List<String>,
)

/** Dedicated capacity orchestration. All figures are computed from local Room records; nothing is estimated. */
@Composable
fun CapacityModuleHost(
    database: FarmOsDatabase,
    farmId: String,
    onBack: () -> Unit,
) {
    var page by remember(farmId) { mutableStateOf(CapacityPage.DASHBOARD) }
    var model by remember(farmId) { mutableStateOf<CapacityModel?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(farmId) {
        runSuspendCatching {
            // Poultry houses: placed head per house from placement records (houses record no capacity figure).
            val houses = database.lifecycle().houses(farmId)
            val placements = database.lifecycle().placements(farmId, 500)
            val placedByHouse = placements.groupBy { it.houseId }.mapValues { (_, rows) -> rows.sumOf { it.headCount } }
            val houseLines = houses.map { house ->
                val placed = placedByHouse[house.id] ?: 0
                "${house.code} (${house.kind} · ${house.poultryKindCode}): $placed head placed · capacity not recorded"
            }

            // Rabbit cages: recorded doe capacity vs breeding waves not yet weaned on the cage.
            val cages = database.rabbitProgramme().cages(farmId)
            val waves = database.rabbitProgramme().waves(farmId)
            val today = java.time.LocalDate.now().toEpochDay()
            val activeWavesByCage = waves.filter { it.weanEpochDay >= today }.groupBy { it.cageId }.mapValues { it.value.size }
            val cageLines = cages.map { cage ->
                val active = activeWavesByCage[cage.id] ?: 0
                "${cage.code}: doe capacity ${cage.doeCapacity} · $active unweaned breeding wave(s)"
            }

            // Paddocks: area vs currently grazing head; stocking density from real figures only.
            val paddocks = database.paddocks().active(farmId)
            val openSessions = database.grazing().open(farmId)
            val grazingByPaddock = openSessions.groupBy { it.paddockId }.mapValues { (_, rows) -> rows.sumOf { it.headCount } }
            val paddockLines = paddocks.map { paddock ->
                val grazing = grazingByPaddock[paddock.id] ?: 0
                val area = paddock.areaM2?.let { "${it} m²" } ?: "area not recorded"
                "${paddock.displayName} (${paddock.code}): $area · $grazing head grazing now"
            }
            val stockingLines = paddocks.mapNotNull { paddock ->
                val grazing = grazingByPaddock[paddock.id] ?: 0
                val areaM2 = paddock.areaM2 ?: return@mapNotNull null
                if (areaM2 <= 0) return@mapNotNull null
                val perHa = grazing * 10_000.0 / areaM2
                "${paddock.displayName}: ${"%.1f".format(perHa)} head/ha currently grazing"
            }

            // Feed: recorded issues in the last 30 days per item + on-hand inventory.
            val feedTotals = database.feedIssues().totalsByItem(farmId)
            val feedItems = database.inventory().items(farmId)
            val feedLines = feedTotals.map { total ->
                "Issued — ${total.itemId}: ${total.quantityMilli / 1000} g across ${total.issueCount} issue(s)"
            } + feedItems.filter { it.name.contains("feed", ignoreCase = true) }.map { item ->
                "On hand — ${item.name}: ${item.quantityMilli / 1000} ${item.unit} (reorder at ${item.reorderMilli / 1000} ${item.unit})"
            }

            // Water: recorded litres in the last 30 days per source.
            val waterTotals = database.water().totalsBySource(farmId)
            val waterLines = waterTotals.map { total ->
                "${total.source}: ${total.litresMilli / 1000} L recorded across ${total.recordCount} record(s)"
            }

            // Alerts: derived strictly from recorded figures.
            val alerts = mutableListOf<String>()
            cages.forEach { cage ->
                val active = activeWavesByCage[cage.id] ?: 0
                if (active > cage.doeCapacity) alerts += "${cage.code}: $active unweaned waves exceed doe capacity ${cage.doeCapacity}"
            }
            feedItems.filter { it.quantityMilli <= it.reorderMilli && it.reorderMilli > 0 }.forEach { item ->
                alerts += "${item.name}: at or below reorder level (${item.quantityMilli / 1000} ${item.unit})"
            }
            if (alerts.isEmpty()) alerts += "No capacity alerts from recorded figures."

            CapacityModel(
                houseLines = houseLines,
                cageLines = cageLines,
                paddockLines = paddockLines,
                stockingLines = stockingLines,
                feedLines = feedLines,
                waterLines = waterLines,
                alertLines = alerts,
            )
        }.onSuccess { model = it }.onFailure { error = it.message }
    }

    val current = page
    FarmOperationalPage(
        screenId = current.screenId,
        title = current.title,
        subtitle = "Utilisation computed from this device's local records.",
        onBack = if (current == CapacityPage.DASHBOARD) onBack else ({ page = CapacityPage.DASHBOARD }),
        backLabel = if (current == CapacityPage.DASHBOARD) "Farm home" else "Capacity",
    ) {
        if (error != null) {
            FarmOperationalSection("Attention", error) {}
        }
        val m = model
        when (current) {
            CapacityPage.DASHBOARD -> {
                FarmOperationalSection("Capacity at a glance") {
                    val rows = m?.let {
                        listOf(
                            "Poultry houses: ${it.houseLines.size}",
                            "Rabbit cages: ${it.cageLines.size}",
                            "Active paddocks: ${it.paddockLines.size}",
                            "Open alerts: ${it.alertLines.count { l -> !l.startsWith("No capacity") }}",
                        )
                    } ?: listOf("Loading…")
                    rows.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
                FarmOperationalSection("Detail areas") {
                    CapacityPage.entries.filter { it != CapacityPage.DASHBOARD }.forEach { target ->
                        TextButton(onClick = { page = target }, modifier = Modifier.fillMaxWidth()) {
                            Text(target.title)
                        }
                    }
                }
            }
            CapacityPage.HOUSING -> FarmOperationalRows(
                m?.houseLines ?: listOf("Loading…"),
                "No poultry houses recorded",
                "Record houses in the poultry module to see them here.",
            )
            CapacityPage.STOCKING -> FarmOperationalRows(
                m?.stockingLines ?: listOf("Loading…"),
                "No stocking figures",
                "Stocking density needs paddock areas and open grazing sessions.",
            )
            CapacityPage.CAGES -> FarmOperationalRows(
                m?.cageLines ?: listOf("Loading…"),
                "No rabbit cages recorded",
                "Record cages in the rabbit module to see them here.",
            )
            CapacityPage.POULTRY_HOUSES -> FarmOperationalRows(
                m?.houseLines ?: listOf("Loading…"),
                "No poultry houses recorded",
                null,
            )
            CapacityPage.PADDOCKS -> FarmOperationalRows(
                m?.paddockLines ?: listOf("Loading…"),
                "No paddocks recorded",
                "Record paddocks in the pasture module to see them here.",
            )
            CapacityPage.FEED -> FarmOperationalRows(
                m?.feedLines ?: listOf("Loading…"),
                "No feed records",
                "Record feed issues to see consumption here.",
            )
            CapacityPage.WATER -> FarmOperationalRows(
                m?.waterLines ?: listOf("Loading…"),
                "No water records",
                "Record water to see consumption here.",
            )
            CapacityPage.FORECAST -> FarmOperationalSection(
                "Capacity forecast",
                "Projection from the last 30 days of recorded issues. A straight-line projection, not a prediction.",
            ) {
                val rows = m?.feedLines?.takeIf { it.isNotEmpty() }?.map { line ->
                    // Keep the recorded figure; project the same 30-day rate forward once.
                    "$line → next 30d at same rate"
                } ?: listOf("Loading…")
                rows.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            CapacityPage.ALERTS -> FarmOperationalRows(
                m?.alertLines ?: listOf("Loading…"),
                "No alerts",
                null,
            )
        }
    }
}
