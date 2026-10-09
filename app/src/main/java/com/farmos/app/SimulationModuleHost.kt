package com.farmos.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
private enum class SimulationPage(val screenId: String, val title: String) {
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
private data class FarmScenario(
    val name: String,
    val kind: String,
    val speciesCode: String,
    val periodsMonths: Int,
    val birthRatePerFemalePerYear: Double,
    val deathRatePerYear: Double,
    val feedGramsPerHeadPerDay: Long,
    val createdEpochMillis: Long,
    val resultLines: List<String>,
)

private data class ScenarioParams(
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
    var page by remember(farmId) { mutableStateOf(SimulationPage.HUB) }
    var params by remember(farmId) { mutableStateOf(ScenarioParams()) }
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
        val p = params
        require(p.speciesCode in FarmSpeciesCodes.ALL) { "Select a supported species code" }
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
        lines += "Basis: " + head + " individually registered active " + p.speciesCode +
            ", " + females + " females, over " + p.periodsMonths + " months (" + horizonDays + " days)."
        lines += "Assumptions: " + p.birthRate + " births per female per year; " +
            p.deathRate + " annual death rate. Group-only head counts are not included."
        when (kind) {
            "GROWTH" -> {
                val births = females * p.birthRate * years
                val deaths = head * p.deathRate * years
                val projected = (head + births - deaths).toLong().coerceAtLeast(0)
                lines += "Projected births: ${births.toLong()}"
                lines += "Projected deaths: ${deaths.toLong()}"
                lines += "Projected head: $projected (from $head)"
            }
            "BREEDING" -> {
                val births = females * p.birthRate * years
                lines += "Expected offspring: ${births.toLong()} from $females females"
                lines += "At 50% female ratio: ${(births / 2).toLong()} replacement females"
            }
            "FEED" -> {
                val totalGrams = BigDecimal.valueOf(p.feedGramsPerHeadPerDay)
                    .multiply(BigDecimal.valueOf(head.toLong())).multiply(BigDecimal.valueOf(horizonDays))
                lines += "Entered feed assumption: " + p.feedGramsPerHeadPerDay + " g/head/day"
                lines += "Projected feed for " + head + " head: " + reportKg(totalGrams)
                lines += "Feed issues alone do not establish measured intake per animal per day."
            }
            "CAPACITY" -> {
                val births = females * p.birthRate * years
                val projected = (head + births - head * p.deathRate * years).toLong().coerceAtLeast(0)
                lines += "Projected head: $projected"
                if (p.speciesCode == "rabbit") {
                    val projectedFemales = (females + births / 2.0 - females * p.deathRate * years).toLong().coerceAtLeast(0)
                    lines += "Assuming 50% female offspring: " + projectedFemales + " projected females."
                    lines += "Recorded doe cage capacity: " + s.doeCageCapacity
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
                    lines += "Recorded net " + reportMoney(basis.netMinor, basis.currency) + " from " +
                        LocalDate.ofEpochDay(basis.firstEpochDay) + " to " + LocalDate.ofEpochDay(basis.lastEpochDay) +
                        " (" + observedDays + " days)."
                    lines += "If that daily net continues for " + horizonDays + " days: " + reportMoney(projected, basis.currency)
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
        )
    }

    var lastResult by remember(farmId) { mutableStateOf<FarmScenario?>(null) }

    fun saveScenario(name: String) {
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
        }
    }

    val current = page
    FarmOperationalPage(
        screenId = current.screenId,
        title = current.title,
        subtitle = "What-if projections from recorded figures. Nothing here changes farm records.",
        onBack = if (current == SimulationPage.HUB) onBack else ({ page = SimulationPage.HUB }),
        backLabel = if (current == SimulationPage.HUB) "Farm home" else "Simulation hub",
    ) {
        if (error != null) {
            FarmOperationalSection("Attention", error) {}
        }
        @Composable
        fun nav(target: SimulationPage, label: String) {
            TextButton(onClick = { page = target }, modifier = Modifier.fillMaxWidth()) { Text(label) }
        }
        when (current) {
            SimulationPage.HUB -> {
                FarmOperationalSection("Scenario types", "Each scenario projects recorded figures forward; parameters are yours to set.") {
                    nav(SimulationPage.GROWTH, "Herd growth scenario")
                    nav(SimulationPage.BREEDING, "Breeding scenario")
                    nav(SimulationPage.FEED, "Feed scenario")
                    nav(SimulationPage.CAPACITY, "Capacity scenario")
                    nav(SimulationPage.FINANCE, "Financial scenario")
                    nav(SimulationPage.COMPARE, "Compare saved scenarios")
                    nav(SimulationPage.SAVE, "Save a scenario")
                    nav(SimulationPage.REPORT, "Scenario report")
                }
                FarmOperationalSection("Saved scenarios") {
                    if (saved.isEmpty()) Text("None saved yet.", style = MaterialTheme.typography.bodyMedium)
                    saved.forEach { Text("${it.name} · ${it.kind} · ${it.speciesCode}", style = MaterialTheme.typography.bodyMedium) }
                }
            }
            SimulationPage.GROWTH,
            SimulationPage.BREEDING,
            SimulationPage.FEED,
            SimulationPage.CAPACITY,
            SimulationPage.FINANCE,
            -> {
                val kind = when (current) {
                    SimulationPage.GROWTH -> "GROWTH"
                    SimulationPage.BREEDING -> "BREEDING"
                    SimulationPage.FEED -> "FEED"
                    SimulationPage.CAPACITY -> "CAPACITY"
                    else -> "FINANCE"
                }
                ScenarioEditor(params = params, onParams = { params = it })
                Button(onClick = {
                    error = null
                    lastResult = runCatching { runScenario(kind) }.getOrElse {
                        error = it.message
                        null
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Run scenario") }
                lastResult?.takeIf { it.kind == kind }?.let { result ->
                    FarmOperationalSection("Projection") {
                        result.resultLines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                    TextButton(onClick = { page = SimulationPage.SAVE }, modifier = Modifier.fillMaxWidth()) {
                        Text("Save this scenario")
                    }
                }
            }
            SimulationPage.COMPARE -> {
                FarmOperationalSection("Scenario compare", "Saved scenarios side by side.") {
                    if (saved.isEmpty()) {
                        Text("No saved scenarios to compare.", style = MaterialTheme.typography.bodyMedium)
                    }
                    saved.forEach { scenario ->
                        Text(scenario.name + " · " + scenario.kind, style = MaterialTheme.typography.titleMedium)
                        scenario.resultLines.forEach { Text("  $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            SimulationPage.SAVE -> {
                var name by remember { mutableStateOf("") }
                FarmOperationalSection("Save scenario", "Save the last completed projection for this farm.") {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Scenario name") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { saveScenario(name) }, enabled = lastResult != null, modifier = Modifier.fillMaxWidth()) { Text("Save") }
                    if (lastResult == null) Text("Run a scenario first.", style = MaterialTheme.typography.bodyMedium)
                }
                FarmOperationalRows(
                    saved.map { "${it.name} · ${it.kind} · ${it.speciesCode} · ${it.periodsMonths} months" },
                    "No saved scenarios",
                    null,
                )
            }
            SimulationPage.REPORT -> {
                FarmOperationalSection("Scenario report") {
                    val result = lastResult
                    if (result == null) {
                        Text("Run a scenario to see its report.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("Kind: ${result.kind} · Species: ${result.speciesCode}", style = MaterialTheme.typography.titleMedium)
                        result.resultLines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScenarioEditor(params: ScenarioParams, onParams: (ScenarioParams) -> Unit) {
    FarmOperationalSection("Parameters") {
        OutlinedTextField(
            value = params.speciesCode,
            onValueChange = { onParams(params.copy(speciesCode = it.trim().lowercase())) },
            label = { Text("Species code") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = params.periodsMonths.toString(),
            onValueChange = { onParams(params.copy(periodsMonths = it.toIntOrNull()?.coerceIn(1, 120) ?: params.periodsMonths)) },
            label = { Text("Horizon (months)") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = params.birthRate.toString(),
            onValueChange = { onParams(params.copy(birthRate = it.toDoubleOrNull()?.takeIf { value -> value.isFinite() }?.coerceIn(0.0, 10.0) ?: params.birthRate)) },
            label = { Text("Births per female per year") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = params.deathRate.toString(),
            onValueChange = { onParams(params.copy(deathRate = it.toDoubleOrNull()?.takeIf { value -> value.isFinite() }?.coerceIn(0.0, 1.0) ?: params.deathRate)) },
            label = { Text("Death rate per year") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = params.feedGramsPerHeadPerDay.toString(),
            onValueChange = { onParams(params.copy(feedGramsPerHeadPerDay = it.toLongOrNull()?.coerceIn(0, 100000) ?: params.feedGramsPerHeadPerDay)) },
            label = { Text("Assumed feed g/head/day") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
