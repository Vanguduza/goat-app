package com.farmos.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.farmos.core.design.FarmActionButton
import com.farmos.core.design.FarmBarValue
import com.farmos.core.design.FarmMetricBars
import com.farmos.core.design.FarmMetricStrip
import com.farmos.core.design.FarmMetricValue
import com.farmos.core.design.FarmModuleTools
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmToolAction
import com.farmos.core.design.FarmToolGroup

/** FOS-AN-001..014; P10/P06 derived from the locked Animal Farm family. */
@Composable
internal fun AnalyticsModuleScreen(
    current: AnalyticsPage,
    model: AnalyticsModel?,
    error: String?,
    selectedAnomaly: Anomaly?,
    onBack: () -> Unit,
    onPage: (AnalyticsPage) -> Unit,
    onInsight: (Anomaly) -> Unit,
) {
    FarmOperationalPage(current.screenId, current.title, "", onBack = if (current == AnalyticsPage.OVERVIEW) onBack else ({ onPage(AnalyticsPage.OVERVIEW) }), backLabel = if (current == AnalyticsPage.OVERVIEW) "Farm home" else "Analytics") {
        error?.let { FarmOperationalSection("Needs attention") { Text(it) } }
        @Composable fun chart(title: String, values: List<FarmBarValue>?, unit: String, empty: String, caption: String = "All recorded data on this device") {
            when { values == null -> Text("Reading farm records"); values.isEmpty() -> FarmOperationalSection(empty) {}; else -> FarmMetricBars(title, values, unit, caption = caption) }
        }
        @Composable fun production() {
            val groups = model?.productionBars
            if (groups == null) Text("Reading production records")
            else if (groups.isEmpty()) Text("No production records")
            else groups.forEach { (unit, values) -> chart("Recorded production · $unit", values, unit, "No production records") }
        }
        when (current) {
            AnalyticsPage.OVERVIEW -> {
                FarmMetricStrip(listOf(
                    FarmMetricValue("Species", model?.speciesBars?.size?.toString() ?: "—"),
                    FarmMetricValue("Withdrawals", model?.health?.activeWithdrawals?.toString() ?: "—", onClick = { onPage(AnalyticsPage.HEALTH) }),
                    FarmMetricValue("Stock alerts", model?.inventory?.atOrBelowReorder?.toString() ?: "—", onClick = { onPage(AnalyticsPage.ANOMALIES) }),
                    FarmMetricValue("Anomaly flags", model?.anomalies?.count { it.title != "No anomalies" }?.toString() ?: "—", onClick = { onPage(AnalyticsPage.ANOMALIES) }),
                ))
                FarmModuleTools(listOf(
                    FarmToolGroup("Animals", listOf(
                        FarmToolAction("Species performance", { onPage(AnalyticsPage.SPECIES) }),
                        FarmToolAction("Reproduction", { onPage(AnalyticsPage.REPRODUCTION) }),
                        FarmToolAction("Growth", { onPage(AnalyticsPage.GROWTH) }),
                        FarmToolAction("Mortality", { onPage(AnalyticsPage.MORTALITY) }),
                    )),
                    FarmToolGroup("Operations", listOf(
                        FarmToolAction("Health trends", { onPage(AnalyticsPage.HEALTH) }),
                        FarmToolAction("Production", { onPage(AnalyticsPage.PRODUCTION) }),
                        FarmToolAction("Feed efficiency", { onPage(AnalyticsPage.FEED_EFFICIENCY) }),
                        FarmToolAction("Financial performance", { onPage(AnalyticsPage.FINANCE) }),
                    )),
                    FarmToolGroup("Insights", listOf(
                        FarmToolAction("Benchmark compare", { onPage(AnalyticsPage.COMPARE) }),
                        FarmToolAction("Anomaly centre", { onPage(AnalyticsPage.ANOMALIES) }),
                        FarmToolAction("Forecasts", { onPage(AnalyticsPage.FORECASTS) }),
                    )),
                ), title = "Analyse farm records")
            }
            AnalyticsPage.SPECIES -> chart("Active animals by species", model?.speciesBars, "animals", "No animals recorded")
            AnalyticsPage.REPRODUCTION -> chart("Recorded birth outcomes", model?.birthBars, "young", "No birth records", "Outcomes are shown separately for each species.")
            AnalyticsPage.GROWTH -> chart("Animals with a recorded weight", model?.growthBars, "% of registered animals", "No animals recorded", "Coverage of recorded weights. This does not establish a growth rate.")
            AnalyticsPage.MORTALITY -> chart("Recorded exits", model?.exitKinds?.map { FarmBarValue(it.kind, it.exits.toDouble(), "${it.exits} exits") }, "exits", "No exits recorded")
            AnalyticsPage.HEALTH -> chart("Health records", model?.health?.let { listOf(FarmBarValue("Observations", it.observations.toDouble(), it.observations.toString()), FarmBarValue("Treatments", it.treatments.toDouble(), it.treatments.toString()), FarmBarValue("Active withdrawal windows", it.activeWithdrawals.toDouble(), it.activeWithdrawals.toString())) }, "records", "No health records", "Observations and treatments are all-time; withdrawal windows are active today.")
            AnalyticsPage.PRODUCTION -> production()
            AnalyticsPage.FEED_EFFICIENCY -> {
                Text("Feed issues and production totals. A conversion ratio needs a shared period and subject.", style = MaterialTheme.typography.bodySmall)
                val groups = model?.feedBars
                if (groups == null) Text("Reading feed records") else if (groups.isEmpty()) Text("No feed issues recorded")
                else groups.forEach { (unit, values) -> chart("Feed issued · $unit", values, unit, "No feed issues") }
                production()
            }
            AnalyticsPage.FINANCE -> {
                val groups = model?.moneyBars
                if (groups == null) Text("Reading money records") else if (groups.isEmpty()) Text("No money records")
                else groups.forEach { (currency, values) -> chart("Income and expense · $currency", values, currency, "No money records") }
            }
            AnalyticsPage.COMPARE -> {
                chart("Active animals", model?.speciesBars, "animals", "No animals recorded")
                chart("Weight record coverage", model?.growthBars, "% of registered animals", "No weights recorded")
            }
            AnalyticsPage.ANOMALIES -> {
                val anomalies = model?.anomalies?.filter { it.title != "No anomalies" }
                if (anomalies == null) Text("Reading farm records") else if (anomalies.isEmpty()) Text("No flags from these checks")
                else FarmModuleTools(listOf(FarmToolGroup("Review", anomalies.map { anomaly -> FarmToolAction(anomaly.title, { onInsight(anomaly) }, anomaly.detail) })), title = "Anomaly centre")
            }
            AnalyticsPage.FORECASTS -> {
                FarmOperationalSection("Recorded baseline", "A forecast needs a dated baseline and explicit assumptions.") { }
                production()
            }
            AnalyticsPage.INSIGHT -> {
                val anomaly = selectedAnomaly
                if (anomaly == null) Text("Select an anomaly to review it") else FarmOperationalSection(anomaly.title) {
                    Text(anomaly.detail)
                    FarmActionButton(onClick = { onPage(AnalyticsPage.EVIDENCE) }) { Text("View evidence sources") }
                }
            }
            AnalyticsPage.EVIDENCE -> FarmOperationalSection("Evidence sources") { Text(selectedAnomaly?.evidence ?: "No insight selected") }
        }
    }
}
