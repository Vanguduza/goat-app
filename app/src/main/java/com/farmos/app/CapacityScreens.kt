package com.farmos.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.farmos.core.design.*
import com.farmos.feature.ops.PastureOccupancyBoard
import java.math.BigDecimal
import java.time.LocalDate

/** FOS-CAP-001..010, P04/P10. Shared Animal Farm controls; recorded and modelled states stay separate. */
@Composable
internal fun CapacityWorkspace(page: CapacityPage, model: CapacityModel?, error: String?, onOpen: (CapacityPage) -> Unit, onBack: () -> Unit) {
    fun tool(target: CapacityPage) = FarmToolAction(target.title, { onOpen(target) })
    FarmOperationalPage(page.screenId, page.title, "", onBack = if (page == CapacityPage.DASHBOARD) onBack else ({ onOpen(CapacityPage.DASHBOARD) }), backLabel = if (page == CapacityPage.DASHBOARD) "Farm home" else "Capacity") {
        error?.let { FarmOperationalSection("Needs attention", it) {} }
        if (page == CapacityPage.DASHBOARD) {
            FarmMetricStrip(listOf(
                FarmMetricValue("Poultry houses", model?.houses?.size?.toString() ?: "—", onClick = { onOpen(CapacityPage.POULTRY_HOUSES) }),
                FarmMetricValue("Rabbit cages", model?.cages?.size?.toString() ?: "—", onClick = { onOpen(CapacityPage.CAGES) }),
                FarmMetricValue("Active paddocks", model?.pasture?.paddocks?.size?.toString() ?: "—", onClick = { onOpen(CapacityPage.PADDOCKS) }),
                FarmMetricValue("Review alerts", model?.alerts?.size?.toString() ?: "—", onClick = { onOpen(CapacityPage.ALERTS) }),
            ))
            FarmModuleTools(listOf(
                FarmToolGroup("Housing and land", listOf(CapacityPage.HOUSING, CapacityPage.CAGES, CapacityPage.POULTRY_HOUSES, CapacityPage.PADDOCKS, CapacityPage.STOCKING).map(::tool)),
                FarmToolGroup("Resources and planning", listOf(CapacityPage.FEED, CapacityPage.WATER, CapacityPage.FORECAST, CapacityPage.ALERTS).map(::tool)),
            ), title = "Capacity tools")
            return@FarmOperationalPage
        }
        val m = model
        if (m == null) { if (error == null) Text("Reading capacity records"); return@FarmOperationalPage }
        when (page) {
            CapacityPage.HOUSING, CapacityPage.POULTRY_HOUSES -> FarmMetricBars("Historical poultry placements", m.houses, "head recorded", caption = "Placement totals include transfers. They are not current occupancy; housing limits are not recorded.")
            CapacityPage.STOCKING -> FarmMetricBars("Recorded grazing density", m.stocking, "head/ha", caption = "Uses recorded entry head counts and known paddock areas, not a new census or carrying-capacity recommendation.")
            CapacityPage.CAGES -> CapacityCagePlan(m.cages)
            CapacityPage.PADDOCKS -> PastureOccupancyBoard(m.pasture)
            CapacityPage.FEED -> CapacityResourceView(m.feed, true)
            CapacityPage.WATER -> CapacityResourceView(m.water, false)
            CapacityPage.FORECAST -> CapacityProjection(m.feed + m.water)
            CapacityPage.ALERTS -> FarmOperationalRows(m.alerts, "No capacity alerts from recorded figures", null)
            CapacityPage.DASHBOARD -> Unit
        }
    }
}

@Composable
private fun CapacityResourceView(rows: List<CapacityUsage>, feed: Boolean) {
    var view by rememberSaveable { mutableStateOf("Recorded issues") }
    if (feed) FarmSectionTabs(listOf("Recorded issues", "Stock on hand"), view, { view = it })
    if (rows.isEmpty()) { AnimalFarmEmptyState(if (feed) "No feed issues recorded" else "No water records"); return }
    rows.groupBy { it.unit ?: "Unit unavailable" }.forEach { (unit, records) ->
        FarmMetricBars(if (feed && view == "Stock on hand") "Current stock · $unit" else "Recorded quantities · $unit", records.map { record ->
            val value = if (feed && view == "Stock on hand") record.onHandMilli else record.quantityMilli
            FarmBarValue(record.label, value?.takeIf { record.unit != null }?.let { BigDecimal.valueOf(it, 3).toDouble() },
                value?.let(record::amount) ?: "Item not on this device", if (feed && view == "Stock on hand") null else record.dateRange + " · ${record.records} records", "capacity-usage:${record.id}")
        }, unit, caption = if (feed) "Issued stock is not measured animal intake. Units remain separate." else "Logged quantities, not measured consumption.")
    }
}

@Composable
private fun CapacityCagePlan(cages: List<CapacityCage>) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val row = cages.firstOrNull { it.cage.id == selected } ?: cages.firstOrNull()
    if (row == null) { AnimalFarmEmptyState("No rabbit cages recorded"); return }
    FarmEntitySelector("capacity-cage-selector", "Cage", cages.map { FarmSelectorOption(it.cage.id, it.cage.code) }, row.cage.id, { selected = it }, "No cages")
    FarmMetricBars("${row.cage.code} · doe places", listOf(
        FarmBarValue("Recorded capacity", row.cage.doeCapacity.toDouble(), "${row.cage.doeCapacity} doe places"),
        FarmBarValue("Scheduled today", row.plannedDoesToday.toDouble(), "${row.plannedDoesToday} does in planned windows"),
    ), "doe places", caption = "Scheduled dates are plans. They do not prove occupancy or completed weaning.")
    FarmWorkflowTimeline("Breeding windows", row.plans.map { wave ->
        FarmWorkflowStep("${wave.doeCount} does", "${LocalDate.ofEpochDay(wave.matingEpochDay)} to ${LocalDate.ofEpochDay(wave.weanEpochDay)}", "Planned mating through weaning", FarmWorkflowState.PLANNED)
    })
}

@Composable
private fun CapacityProjection(rows: List<CapacityUsage>) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var daysText by rememberSaveable { mutableStateOf("30") }
    val row = rows.firstOrNull { it.id == selected } ?: rows.firstOrNull()
    if (row == null) { AnimalFarmEmptyState("Record dated feed issues or water quantities before projecting a baseline"); return }
    FarmEntitySelector("capacity-forecast-resource", "Recorded resource", rows.map { FarmSelectorOption(it.id, it.label, it.unit ?: "Unit unavailable") }, row.id, { selected = it }, "No records")
    val days = daysText.toIntOrNull()?.takeIf { it in 1..365 }
    FarmOutlinedTextField(daysText, { daysText = it }, label = { Text("Horizon (days)") }, isError = days == null, supportingText = { Text("1 to 365 days") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    val projection = days?.let(row::projection)
    if (projection == null) { Text("A projection needs a known unit, a valid recorded date span and a valid horizon."); return }
    FarmMetricBars("${row.label} · scenario", listOf(
        FarmBarValue("Recorded quantity", BigDecimal.valueOf(row.quantityMilli, 3).toDouble(), row.amount(row.quantityMilli)),
        FarmBarValue("Projected quantity", projection.toDouble(), capacityQuantity(projection, requireNotNull(row.unit)), tag = "capacity-forecast-${row.id}"),
    ), row.unit.orEmpty(), caption = row.dateRange)
    Text("If the recorded daily issue rate continues for $days days.")
    Text("Assumes the dated span is complete. Missing records change the result; this is not measured demand.")
}
