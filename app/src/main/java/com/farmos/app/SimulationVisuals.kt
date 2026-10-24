package com.farmos.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.farmos.core.design.*
import com.farmos.domain.ops.FarmSpeciesCodes
import kotlinx.serialization.Serializable

/** Exact display values remain authoritative; floating point is a plotting coordinate only. */
@Serializable
internal data class ScenarioMetric(val label: String, val displayValue: String, val plotValue: Double?, val unit: String)

internal data class ScenarioInputs(val species: String = "goat", val months: String = "12", val birthRate: String = "1.5", val deathRate: String = "0.05", val feedGrams: String = "1500") {
    fun parameters(kind: String): ScenarioParams = ScenarioParams(
        speciesCode = if (kind == "FINANCE") "farm" else species,
        periodsMonths = requireNotNull(months.toIntOrNull()) { "Enter a whole-number horizon between 1 and 120 months" },
        birthRate = if (kind in setOf("GROWTH", "BREEDING", "CAPACITY")) requireNotNull(birthRate.toDoubleOrNull()) { "Enter births per female per year" } else 0.0,
        deathRate = if (kind in setOf("GROWTH", "CAPACITY")) requireNotNull(deathRate.toDoubleOrNull()) { "Enter a death rate between 0 and 1" } else 0.0,
        feedGramsPerHeadPerDay = if (kind == "FEED") requireNotNull(feedGrams.toLongOrNull()) { "Enter whole grams per head per day" } else 0,
    )
}

/** FOS-SIM-001, P04: compact tools rather than a vertical feature catalogue. */
@Composable
internal fun SimulationOverview(saved: List<FarmScenario>, loaded: Boolean, onOpen: (SimulationPage) -> Unit, onReview: (FarmScenario) -> Unit) {
    FarmMetricStrip(listOf(FarmMetricValue("Saved scenarios", if (loaded) saved.size.toString() else "—")))
    FarmModuleTools(listOf(
        FarmToolGroup("Model a change", listOf(SimulationPage.GROWTH, SimulationPage.BREEDING, SimulationPage.FEED, SimulationPage.CAPACITY, SimulationPage.FINANCE).map { target -> FarmToolAction(target.title, { onOpen(target) }) }),
        FarmToolGroup("Review and save", listOf(
            FarmToolAction("Compare saved scenarios", { onOpen(SimulationPage.COMPARE) }),
            FarmToolAction("Save a scenario", { onOpen(SimulationPage.SAVE) }),
            FarmToolAction("Scenario report", { onOpen(SimulationPage.REPORT) }),
        )),
        FarmToolGroup("Saved scenarios", saved.map { scenario -> FarmToolAction(scenario.name, { onReview(scenario) }, "${scenario.kind.lowercase()} · ${scenario.periodsMonths} months") }),
    ).filter { it.actions.isNotEmpty() }, title = "Simulation tools")
    Text("What-if projections only. Running or saving a scenario does not change farm records.", style = MaterialTheme.typography.bodySmall)
}

/** FOS-SIM-002..006, P07. Keep entered drafts intact; validate rather than silently clamping values. */
@Composable
internal fun ScenarioEditor(inputs: ScenarioInputs, kind: String, onInputs: (ScenarioInputs) -> Unit) {
    FarmOperationalSection("Scenario inputs") {
        if (kind != "FINANCE") FarmEntitySelector("scenario-species", "Species", FarmSpeciesCodes.ALL.map { FarmSelectorOption(it, it.replaceFirstChar { c -> c.uppercase() }) }, inputs.species, { onInputs(inputs.copy(species = it)) }, "No species")
        else Text("Farm-wide money records · currencies stay separate", style = MaterialTheme.typography.bodySmall)
        FarmOutlinedTextField(inputs.months, { onInputs(inputs.copy(months = it)) }, label = { Text("Horizon (months)") }, supportingText = { Text("1 to 120 months") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (kind in setOf("GROWTH", "BREEDING", "CAPACITY")) FarmOutlinedTextField(inputs.birthRate, { onInputs(inputs.copy(birthRate = it)) }, label = { Text("Births per female per year") }, supportingText = { Text("Entered assumption · 0 to 10") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (kind in setOf("GROWTH", "CAPACITY")) FarmOutlinedTextField(inputs.deathRate, { onInputs(inputs.copy(deathRate = it)) }, label = { Text("Death rate per year") }, supportingText = { Text("Entered assumption · 0 to 1") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (kind == "FEED") FarmOutlinedTextField(inputs.feedGrams, { onInputs(inputs.copy(feedGrams = it)) }, label = { Text("Assumed feed g/head/day") }, supportingText = { Text("Entered assumption · 0 to 100,000 g") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    }
}

/** P10, all scenario result surfaces. Old saved scenarios retain their original calculation text. */
@Composable
internal fun ScenarioResultVisual(scenario: FarmScenario, modifier: Modifier = Modifier) {
    var view by rememberSaveable(scenario.createdEpochMillis, scenario.kind) { mutableStateOf("Results") }
    Column(modifier.fillMaxWidth().testTag("scenario-results:${scenario.kind}"), verticalArrangement = Arrangement.spacedBy(FosDimens.IntraCardGap)) {
        Text(scenario.name.ifBlank { "Projection" }, style = MaterialTheme.typography.titleLarge)
        Text("${scenario.periodsMonths} months · ${if (scenario.kind == "FINANCE") "farm-wide" else scenario.speciesCode} · not a farm record", style = MaterialTheme.typography.bodySmall)
        FarmSectionTabs(listOf("Results", "Assumptions and calculation"), view, { view = it })
        if (view == "Results" && scenario.metrics.isNotEmpty()) {
            scenario.metrics.groupBy { it.unit }.forEach { (unit, rows) ->
                FarmMetricBars("${scenario.kind.lowercase().replaceFirstChar { it.uppercase() }} · $unit", rows.map { FarmBarValue(it.label, it.plotValue, it.displayValue) }, unit)
            }
            if (scenario.kind == "FINANCE") Text("Assumes the recorded first-to-last date span is complete. Missing entries change the projection.", style = MaterialTheme.typography.bodySmall)
            if (scenario.kind == "FEED") Text("The entered feed rate is an assumption, not measured intake.", style = MaterialTheme.typography.bodySmall)
        } else FarmOperationalRows(scenario.resultLines, "No calculated results", null)
    }
}

/** FOS-SIM-007, P10: real selected comparison panes on tablets, stacked at large text sizes. */
@Composable
internal fun ScenarioComparison(saved: List<FarmScenario>) {
    var first by rememberSaveable { mutableStateOf<String?>(null) }
    var second by rememberSaveable { mutableStateOf<String?>(null) }
    if (saved.isEmpty()) { AnimalFarmEmptyState("No saved scenarios to compare"); return }
    val a = saved.firstOrNull { it.name == first } ?: saved.first()
    val others = saved.filter { it.name != a.name }
    val b = others.firstOrNull { it.name == second } ?: others.firstOrNull()
    FarmEntitySelector("scenario-compare:first", "First scenario", saved.map { FarmSelectorOption(it.name, it.name, "${it.kind} · ${it.periodsMonths} months") }, a.name, { first = it }, "No scenarios")
    FarmEntitySelector("scenario-compare:second", "Second scenario", others.map { FarmSelectorOption(it.name, it.name, "${it.kind} · ${it.periodsMonths} months") }, b?.name, { second = it }, "Save another scenario to compare")
    Text("Compare like units and review each scenario's assumptions and horizon.", style = MaterialTheme.typography.bodySmall)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 600.dp && LocalDensity.current.fontScale < 1.5f && b != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(FosDimens.SectionGap)) {
                ScenarioResultVisual(a, Modifier.weight(1f))
                ScenarioResultVisual(b, Modifier.weight(1f))
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(FosDimens.SectionGap)) {
            ScenarioResultVisual(a)
            b?.let { ScenarioResultVisual(it) }
        }
    }
}

/** Exact native money display. Unrecognised currencies retain their original minor units. */
internal fun scenarioMoney(minor: java.math.BigDecimal, currency: String): String {
    val digits = runCatching { com.farmos.domain.ops.FarmCurrency.minorDigits(currency) }.getOrNull()
        ?: return "${minor.stripTrailingZeros().toPlainString()} $currency minor units"
    return minor.movePointLeft(digits).setScale(digits, java.math.RoundingMode.HALF_EVEN).toPlainString() + " " + currency
}
