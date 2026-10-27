package com.farmos.app

import androidx.compose.foundation.layout.fillMaxWidth
import com.farmos.core.design.FarmPrimaryButton as Button
import androidx.compose.material3.MaterialTheme
import com.farmos.core.design.FarmOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Text
import com.farmos.core.design.FarmActionButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalRows
import com.farmos.core.design.FarmOperationalSection
import java.io.File
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import com.farmos.domain.ops.FarmSpeciesCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.farmos.core.design.runSuspendCatching

/** Simulation module pages; each maps to one canonical FOS-SIM-* screen. */
internal enum class SimulationPage(val screenId: String, val title: String) {
    HUB("FOS-SIM-001", "Simulation hub"),
    GROWTH("FOS-SIM-002", "Herd growth scenario"),
    BREEDING("FOS-SIM-003", "Breeding scenario"),
    FEED("FOS-SIM-004", "Feed scenario"),
    CAPACITY("FOS-SIM-005", "Capacity scenario"),
    FINANCE("FOS-SIM-006", "Financial scenario"),
    COMPARE("FOS-SIM-007", "Scenario compare"),
    SAVE("FOS-SIM-008", "Scenario save"),
    REPORT("FOS-SIM-009", "Scenario report"),
}

/** A saved what-if scenario. Parameters are owner-entered; results are deterministic projections. */
@Serializable
internal data class FarmScenario(
    val name: String,
    val kind: String,
    val speciesCode: String,
    val periodsMonths: Int,
    val birthRatePerFemalePerYear: Double,
    val deathRatePerYear: Double,
    val feedGramsPerHeadPerDay: Long,
    val createdEpochMillis: Long,
    val resultLines: List<String>,
    val metrics: List<ScenarioMetric> = emptyList(),
)

internal data class ScenarioParams(
    val speciesCode: String = "goat",
    val periodsMonths: Int = 12,
    val birthRate: Double = 1.5,
    val deathRate: Double = 0.05,
    val feedGramsPerHeadPerDay: Long = 1500,
)

private data class ScenarioFinanceBasis(
    val currency: String,
    val netMinor: BigDecimal,
    val firstEpochDay: Long,
    val lastEpochDay: Long,
)

private data class FarmStats(
    val activeBySpecies: Map<String, Int>,
    val femalesBySpecies: Map<String, Int>,
    val doeCageCapacity: Int,
    val finances: List<ScenarioFinanceBasis>,
)

private fun scenarioDirectory(filesDir: File, farmId: String): File =
    File(filesDir, "sim_scenarios/$farmId").also { it.mkdirs() }

private fun loadScenarios(filesDir: File, farmId: String): List<FarmScenario> =
    scenarioDirectory(filesDir, farmId).listFiles { f -> f.extension == "json" }.orEmpty()
        .mapNotNull { runCatching { Json.decodeFromString<FarmScenario>(it.readText()) }.getOrNull() }
        .sortedBy { it.name }

/**
 * Dedicated simulation orchestration. The engine is pure deterministic arithmetic over recorded
 * farm figures plus owner-entered parameters; scenarios persist as farm-scoped JSON in app-private
 * storage (never as business records).
 */
@Composable
fun SimulationModuleHost(
    database: FarmOsDatabase,
    farmId: String,
    filesDir: File,
    onBack: () -> Unit,
) {
    var page by rememberSaveable(farmId) { mutableStateOf(SimulationPage.HUB) }
    var inputs by rememberSaveable(farmId, stateSaver = listSaver<ScenarioInputs, String>(
        save = { listOf(it.species, it.months, it.birthRate, it.deathRate, it.feedGrams) },
        restore = { ScenarioInputs(it[0], it[1], it[2], it[3], it[4]) },
    )) { mutableStateOf(ScenarioInputs()) }
    var scenarioName by rememberSaveable(farmId) { mutableStateOf("") }
    var saving by remember(farmId) { mutableStateOf(false) }
    var stats by remember(farmId) { mutableStateOf<FarmStats?>(null) }
    var saved by remember(farmId) { mutableStateOf(listOf<FarmScenario>()) }
    var error by remember(farmId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        val register = database.reports().herdRegister(farmId)
        val active = register.filter { it.status == "active" }
        val activeBySpecies = active.groupBy { it.speciesCode }.mapValues { it.value.size }
        val femalesBySpecies = active.filter { it.sex.equals("FEMALE", ignoreCase = true) }
            .groupBy { it.speciesCode }.mapValues { it.value.size }
        val finances = database.reports().moneyRecords(farmId).groupBy { it.currency }.map { (currency, rows) ->
            ScenarioFinanceBasis(
                currency = currency,
                netMinor = rows.fold(BigDecimal.ZERO) { net, record ->
                    val amount = BigDecimal.valueOf(record.amountMinor)
                    when (record.kind) {
                        "income" -> net.add(amount)
                        "expense" -> net.subtract(amount)
                        else -> error("Unrecognised money record kind: " + record.kind)
                    }
                },
                firstEpochDay = rows.minOf { it.occurredEpochDay },
                lastEpochDay = rows.maxOf { it.occurredEpochDay },
            )
        }.sortedBy { it.currency }
        stats = FarmStats(
            activeBySpecies = activeBySpecies,
            femalesBySpecies = femalesBySpecies,
            doeCageCapacity = database.rabbitProgramme().cages(farmId).sumOf { it.doeCapacity },
            finances = finances,
        )
        saved = withContext(Dispatchers.IO) { loadScenarios(filesDir, farmId) }
    }

    LaunchedEffect(farmId) {
        runSuspendCatching { refresh() }.onFailure { error = it.message }
    }

    fun runScenario(kind: String): FarmScenario {
        val s = requireNotNull(stats) { "Farm figures are still loading" }
        val p = inputs.parameters(kind)
        require(kind == "FINANCE" || p.speciesCode in FarmSpeciesCodes.ALL) { "Select a supported species code" }
        require(p.periodsMonths in 1..120 && p.birthRate.isFinite() && p.birthRate in 0.0..10.0 &&
            p.deathRate.isFinite() && p.deathRate in 0.0..1.0 && p.feedGramsPerHeadPerDay in 0..100_000) {
            "Enter finite scenario parameters within their stated ranges"
        }
        val head = s.activeBySpecies[p.speciesCode] ?: 0
        val females = s.femalesBySpecies[p.speciesCode] ?: 0
        val years = p.periodsMonths / 12.0
        val today = LocalDate.now()
        val horizonDays = ChronoUnit.DAYS.between(today, today.plusMonths(p.periodsMonths.toLong()))
        val lines = mutableListOf<String>()
        val metrics = mutableListOf<ScenarioMetric>()
        if (kind != "FINANCE") lines += "Basis: " + head + " individually registered active " + p.speciesCode +
            ", " + females + " females, over " + p.periodsMonths + " months (" + horizonDays + " days)."
        if (kind in setOf("GROWTH", "BREEDING", "CAPACITY")) lines += "Assumptions: " + p.birthRate + " births per female per year; " +
            p.deathRate + " annual death rate. Group-only head counts are not included."
        when (kind) {
            "GROWTH" -> {
                val births = females * p.birthRate * years
                val deaths = head * p.deathRate * years
                val projected = (head + births - deaths).toLong().coerceAtLeast(0)
                lines += "Projected births: ${births.toLong()}"
                lines += "Projected deaths: ${deaths.toLong()}"
                lines += "Projected head: $projected (from $head)"
                metrics += ScenarioMetric("Recorded active head", "$head animals", head.toDouble(), "animals")
                metrics += ScenarioMetric("Projected births", "${births.toLong()} animals", births.toLong().toDouble(), "animals")
                metrics += ScenarioMetric("Projected deaths", "${deaths.toLong()} animals", deaths.toLong().toDouble(), "animals")
                metrics += ScenarioMetric("Projected head", "$projected animals", projected.toDouble(), "animals")
            }
            "BREEDING" -> {
                val births = females * p.birthRate * years
                lines += "Expected offspring: ${births.toLong()} from $females females"
                lines += "At 50% female ratio: ${(births / 2).toLong()} replacement females"
                metrics += ScenarioMetric("Recorded females", "$females females", females.toDouble(), "animals")
                metrics += ScenarioMetric("Projected offspring", "${births.toLong()} young", births.toLong().toDouble(), "animals")
                metrics += ScenarioMetric("At 50% female ratio", "${(births / 2).toLong()} replacement females", (births / 2).toLong().toDouble(), "animals")
            }
            "FEED" -> {
                val totalGrams = BigDecimal.valueOf(p.feedGramsPerHeadPerDay)
                    .multiply(BigDecimal.valueOf(head.toLong())).multiply(BigDecimal.valueOf(horizonDays))
                lines += "Entered feed assumption: " + p.feedGramsPerHeadPerDay + " g/head/day"
                lines += "Projected feed for " + head + " head: " + reportKg(totalGrams)
                lines += "Feed issues alone do not establish measured intake per animal per day."
                metrics += ScenarioMetric("Projected feed", reportKg(totalGrams), totalGrams.movePointLeft(3).toDouble(), "kg")
            }
            "CAPACITY" -> {
                val births = females * p.birthRate * years
                val projected = (head + births - head * p.deathRate * years).toLong().coerceAtLeast(0)
                lines += "Projected head: $projected"
                metrics += ScenarioMetric("Recorded active head", "$head animals", head.toDouble(), "animals")
                metrics += ScenarioMetric("Projected head", "$projected animals", projected.toDouble(), "animals")
                if (p.speciesCode == "rabbit") {
                    val projectedFemales = (females + births / 2.0 - females * p.deathRate * years).toLong().coerceAtLeast(0)
                    lines += "Assuming 50% female offspring: " + projectedFemales + " projected females."
                    lines += "Recorded doe cage capacity: " + s.doeCageCapacity
                    metrics += ScenarioMetric("Recorded doe capacity", "${s.doeCageCapacity} doe places", s.doeCageCapacity.toDouble(), "female places")
                    metrics += ScenarioMetric("Projected females needing places", "$projectedFemales females", projectedFemales.toDouble(), "female places")
                    if (projectedFemales > s.doeCageCapacity) {
                        lines += "If every projected female needs a doe place, shortfall: " + (projectedFemales - s.doeCageCapacity)
                    } else {
                        lines += "Projected females fit the recorded doe places under that assumption."
                    }
                    lines += "Doe capacity does not represent capacity for the whole rabbit population."
                } else {
                    lines += "Compare against recorded housing in the capacity module."
                }
            }
            "FINANCE" -> {
                lines += "Financial scope: all farm money records, separated by currency."
                if (s.finances.isEmpty()) lines += "Record dated income and expenses before projecting a financial baseline."
                s.finances.forEach { basis ->
                    val observedDays = Math.addExact(Math.subtractExact(basis.lastEpochDay, basis.firstEpochDay), 1L)
                    val projected = basis.netMinor.multiply(BigDecimal.valueOf(horizonDays))
                        .divide(BigDecimal.valueOf(observedDays), MathContext.DECIMAL128)
                    lines += "Recorded net " + scenarioMoney(basis.netMinor, basis.currency) + " from " +
                        LocalDate.ofEpochDay(basis.firstEpochDay) + " to " + LocalDate.ofEpochDay(basis.lastEpochDay) +
                        " (" + observedDays + " days)."
                    lines += "If that daily net continues for " + horizonDays + " days: " + scenarioMoney(projected, basis.currency)
                    val digits = runCatching { com.farmos.domain.ops.FarmCurrency.minorDigits(basis.currency) }.getOrNull()
                    metrics += ScenarioMetric("Recorded net", scenarioMoney(basis.netMinor, basis.currency), digits?.let { basis.netMinor.movePointLeft(it).toDouble() }, basis.currency)
                    metrics += ScenarioMetric("Projected net", scenarioMoney(projected, basis.currency), digits?.let { projected.movePointLeft(it).toDouble() }, basis.currency)
                }
                lines += "The baseline assumes records cover their first-to-last date span; missing entries change the result."
            }
        }
        lines += "Deterministic projection from recorded figures and entered parameters."
        return FarmScenario(
            name = "",
            kind = kind,
            speciesCode = p.speciesCode,
            periodsMonths = p.periodsMonths,
            birthRatePerFemalePerYear = p.birthRate,
            deathRatePerYear = p.deathRate,
            feedGramsPerHeadPerDay = p.feedGramsPerHeadPerDay,
            createdEpochMillis = System.currentTimeMillis(),
            resultLines = lines,
            metrics = metrics,
        )
    }

    var lastResult by remember(farmId) { mutableStateOf<FarmScenario?>(null) }

    fun saveScenario(name: String) {
        if (saving) return
        saving = true
        scope.launch {
            error = null
            runSuspendCatching {
                require(name.isNotBlank()) { "Scenario needs a name" }
                val scenario = requireNotNull(lastResult) { "Run a scenario before saving it" }.copy(name = name.trim())
                saved = withContext(Dispatchers.IO) {
                    val file = File(scenarioDirectory(filesDir, farmId), name.trim().replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")
                    require(!file.exists()) { "A saved scenario has this name; choose another name" }
                    file.writeText(Json.encodeToString(scenario))
                    loadScenarios(filesDir, farmId)
                }
            }.onFailure { error = it.message }
            saving = false
        }
    }

    val current = page
    FarmOperationalPage(current.screenId, current.title, "", onBack = if (current == SimulationPage.HUB) onBack else ({ page = SimulationPage.HUB }), backLabel = if (current == SimulationPage.HUB) "Farm home" else "Simulation hub") {
        error?.let { FarmOperationalSection("Needs attention", it) {} }
        when (current) {
            SimulationPage.HUB -> SimulationOverview(saved, stats != null, { page = it }, { lastResult = it; page = SimulationPage.REPORT })
            SimulationPage.GROWTH, SimulationPage.BREEDING, SimulationPage.FEED, SimulationPage.CAPACITY, SimulationPage.FINANCE -> {
                val kind = current.name
                ScenarioEditor(inputs, kind) { inputs = it }
                Button(onClick = {
                    error = null
                    lastResult = runCatching { runScenario(kind) }.getOrElse { error = it.message; null }
                }, enabled = stats != null, modifier = Modifier.fillMaxWidth()) { Text("Run scenario") }
                lastResult?.takeIf { it.kind == kind }?.let { result ->
                    ScenarioResultVisual(result)
                    TextButton(onClick = { page = SimulationPage.SAVE }, modifier = Modifier.fillMaxWidth()) { Text("Save this scenario") }
                }
            }
            SimulationPage.COMPARE -> ScenarioComparison(saved)
            SimulationPage.SAVE -> {
                FarmOperationalSection("Save scenario", "Keeps this projection separately from farm records.") {
                    OutlinedTextField(scenarioName, { scenarioName = it }, label = { Text("Scenario name") }, modifier = Modifier.fillMaxWidth(), enabled = !saving)
                    Button(onClick = { saveScenario(scenarioName) }, enabled = !saving && lastResult != null && scenarioName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (saving) "Saving" else "Save") }
                    if (lastResult == null) Text("Run a scenario first.")
                }
                FarmOperationalRows(saved.map { "${it.name} · ${it.kind} · ${it.periodsMonths} months" }, "No saved scenarios", null)
            }
            SimulationPage.REPORT -> lastResult?.let { ScenarioResultVisual(it) } ?: Text("Run or open a saved scenario to review it.")
        }
    }
}
