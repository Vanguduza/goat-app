package com.farmos.app

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.farmos.core.database.AnimalEntity
import com.farmos.core.database.ExitKindCount
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.HealthObservationEntity
import com.farmos.core.database.HealthTreatmentEntity
import com.farmos.core.database.HerdRegisterRow
import com.farmos.core.database.InventoryItemEntity
import com.farmos.core.database.InventoryTotalRow
import com.farmos.core.database.MoneyRecordEntity
import com.farmos.core.database.OfficialMovementEntity
import com.farmos.core.database.TaskEntity
import com.farmos.core.database.WithdrawalWindowEntity
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.AnimalFarmWarningSurface
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmVisualClass
import com.farmos.domain.ops.FarmCurrency
import com.farmos.domain.ops.MetricResult
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Detail pages reachable from the Reports hub (FOS-REPORT-001). Back always returns to the hub. */
internal sealed interface ReportDetail {
    data object Animal : ReportDetail
    data object Herd : ReportDetail
    data object Health : ReportDetail
    data object Production : ReportDetail
    data object Finance : ReportDetail
    data object Inventory : ReportDetail
    data object Breeding : ReportDetail
    data object Genetics : ReportDetail
    data object Operations : ReportDetail
    data object Documents : ReportDetail
    /** One logged export; a null id selects the most recent export. */
    data class Document(val entryId: String?) : ReportDetail
    data object Share : ReportDetail
    data object Movements : ReportDetail
}

/** One entry in the hub's "Report types" section, in canonical screen order. */
internal data class ReportDetailEntry(val detail: ReportDetail, val screenId: String, val label: String)

/** The 13 report screens behind the hub, each opening its own page. */
internal val reportDetailEntries: List<ReportDetailEntry> = listOf(
    ReportDetailEntry(ReportDetail.Animal, "FOS-REPORT-002", "Animal report"),
    ReportDetailEntry(ReportDetail.Herd, "FOS-REPORT-003", "Herd or flock report"),
    ReportDetailEntry(ReportDetail.Health, "FOS-REPORT-004", "Health report"),
    ReportDetailEntry(ReportDetail.Production, "FOS-REPORT-005", "Production report"),
    ReportDetailEntry(ReportDetail.Finance, "FOS-REPORT-006", "Finance report"),
    ReportDetailEntry(ReportDetail.Inventory, "FOS-REPORT-007", "Inventory report"),
    ReportDetailEntry(ReportDetail.Breeding, "FOS-REPORT-008", "Breeding report"),
    ReportDetailEntry(ReportDetail.Genetics, "FOS-REPORT-009", "Genetics report"),
    ReportDetailEntry(ReportDetail.Operations, "FOS-REPORT-010", "Operations report"),
    ReportDetailEntry(ReportDetail.Documents, "FOS-REPORT-012", "Generated documents"),
    ReportDetailEntry(ReportDetail.Document(null), "FOS-REPORT-013", "Document viewer"),
    ReportDetailEntry(ReportDetail.Share, "FOS-REPORT-014", "Share or print"),
    ReportDetailEntry(ReportDetail.Movements, "FOS-REPORT-015", "Movement certificates"),
)

/** One export logged on this device: appended on every successful export from the export sheet. */
internal data class ExportLogEntry(
    val id: String,
    val kind: String,
    val filename: String,
    val timestamp: Long,
    val count: Int,
)

private fun exportLogFile(context: Context, farmId: String): File {
    val safe = farmId.replace(Regex("[^A-Za-z0-9_-]"), "_")
    return File(File(context.filesDir, "report_exports"), "$safe.jsonl")
}

/** Appends one entry to the farm-scoped export log in app-private storage. */
internal fun appendExportLog(context: Context, farmId: String, entry: ExportLogEntry) {
    val file = exportLogFile(context, farmId)
    file.parentFile?.mkdirs()
    val json = JSONObject()
        .put("id", entry.id)
        .put("kind", entry.kind)
        .put("filename", entry.filename)
        .put("timestamp", entry.timestamp)
        .put("count", entry.count)
    file.appendText(json.toString() + "\n")
}

/** Every logged export for the farm, newest first. Corrupt lines are skipped, never fatal. */
internal fun readExportLog(context: Context, farmId: String): List<ExportLogEntry> {
    val file = exportLogFile(context, farmId)
    if (!file.exists()) return emptyList()
    return file.readLines().mapNotNull { line ->
        runCatching {
            val json = JSONObject(line)
            ExportLogEntry(
                json.getString("id"), json.getString("kind"), json.getString("filename"),
                json.getLong("timestamp"), json.getInt("count"),
            )
        }.getOrNull()
    }.sortedByDescending { it.timestamp }
}

private val exportDateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** "2026-10-06 14:30" in the device timezone. */
internal fun exportDateTimeText(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(exportDateTimeFormat)

private fun epochDayText(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).toString()

private fun millisDateText(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

/** One metric rendered the hub way: figure, how it is worked out, period, scope and completeness. */
@Composable
private fun MetricSection(result: MetricResult) {
    FarmOperationalSection(result.definition.name) {
        Text(metricValueText(result), fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("report-metric:${result.definition.id}"))
        Text("How: ${result.definition.formula}", color = AnimalFarmTheme.colors.mutedInk)
        Text("Period: ${result.definition.period} · Scope: ${result.definition.scope}", color = AnimalFarmTheme.colors.mutedInk)
        Text(result.completeness, color = if (result.complete) AnimalFarmTheme.colors.mutedInk else AnimalFarmTheme.colors.critical)
    }
}

private data class AnimalReportDetail(
    val animal: AnimalEntity,
    val latestWeightGrams: Long?,
    val treatmentCount: Int,
    val recentTreatments: List<String>,
    val parents: List<String>,
)

/** FOS-REPORT-002 — Animal Report: one animal's identity, status, latest weight, treatment count and pedigree parents. */
@Composable
internal fun AnimalReportScreen(database: FarmOsDatabase, farmId: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<AnimalEntity>?>(null) }
    var detail by remember { mutableStateOf<AnimalReportDetail?>(null) }
    var working by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }

    fun search() {
        val text = query.trim()
        if (text.isEmpty()) return
        working = true
        failure = null
        scope.launch {
            try {
                matches = database.animals().search(farmId, null, null, "%${escapeLike(text)}%", 20, 0)
                detail = null
            } catch (e: Exception) {
                failure = e.message ?: "The search could not run"
            }
            working = false
        }
    }

    fun select(animal: AnimalEntity) {
        working = true
        failure = null
        scope.launch {
            try {
                val register = database.reports().herdRegister(farmId).firstOrNull { it.id == animal.id }
                val treatments = database.treatments().forAnimal(farmId, animal.id)
                val parents = database.lifecycle().pedigreeParents(farmId, animal.id).map { relation ->
                    val parent = database.animals().get(farmId, relation.parentId)
                    val label = parent?.let { "${it.tag}${it.name?.let { name -> " ($name)" } ?: ""}" } ?: relation.parentId
                    "${relation.relationType}: $label"
                }
                detail = AnimalReportDetail(
                    animal, register?.latestWeightGrams, treatments.size,
                    treatments.take(5).map { "${millisDateText(it.occurredAtEpochMillis)} · ${it.reason}" },
                    parents,
                )
            } catch (e: Exception) {
                failure = e.message ?: "The animal report could not be read"
            }
            working = false
        }
    }

    FarmOperationalPage("FOS-REPORT-002", "Animal report", "One animal's identity, status, latest weight, treatments and parentage.", FarmVisualClass.I3, onBack, backLabel = "Reports") {
        failure?.let { AnimalFarmWarningSurface { Text(it) } }
        FarmOperationalSection("Find animal", "Search by tag or name, then choose the animal.") {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, label = { Text("Tag or name") },
                singleLine = true, modifier = Modifier.fillMaxWidth().testTag("animal-report-search"),
            )
            Button(onClick = ::search, enabled = !working && query.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("animal-report-search-go")) {
                Text(if (working) "Working" else "Search")
            }
            val found = matches
            if (found != null) {
                if (found.isEmpty()) {
                    Text("No animals match.", color = AnimalFarmTheme.colors.mutedInk)
                } else {
                    found.forEach { animal ->
                        TextButton(onClick = { select(animal) }, modifier = Modifier.fillMaxWidth().testTag("animal-report-pick:${animal.id}")) {
                            Text("${animal.tag}${animal.name?.let { " · $it" } ?: ""} · ${speciesNames[animal.speciesCode] ?: animal.speciesCode}")
                        }
                    }
                }
            }
        }
        detail?.let { selected ->
            FarmOperationalSection("Identity") {
                Text("${selected.animal.tag}${selected.animal.name?.let { " · $it" } ?: ""}", fontWeight = FontWeight.SemiBold)
                Text("Species: ${speciesNames[selected.animal.speciesCode] ?: selected.animal.speciesCode}")
                Text("Sex: ${selected.animal.sex} · Status: ${selected.animal.status}")
                Text("Born: ${selected.animal.dateOfBirthEpochDay?.let { epochDayText(it) } ?: "not recorded"}")
            }
            FarmOperationalSection("Latest weight") {
                Text(selected.latestWeightGrams?.let { "%.1f kg".format(it / 1000.0) } ?: "No weight recorded")
            }
            FarmOperationalSection("Health treatments", "${selected.treatmentCount} treatment record(s) in total; latest five shown.") {
                if (selected.recentTreatments.isEmpty()) {
                    Text("None recorded", color = AnimalFarmTheme.colors.mutedInk)
                } else {
                    selected.recentTreatments.forEach { Text(it) }
                }
            }
            FarmOperationalSection("Pedigree parents") {
                if (selected.parents.isEmpty()) {
                    Text("No parentage recorded", color = AnimalFarmTheme.colors.mutedInk)
                } else {
                    selected.parents.forEach { Text(it) }
                }
            }
        }
    }
}

/** FOS-REPORT-003 — Herd or Flock Report: the herd register grouped by species with active counts and exits by kind. */
@Composable
internal fun HerdReportScreen(database: FarmOsDatabase, farmId: String, onBack: () -> Unit) {
    var register by remember { mutableStateOf<List<HerdRegisterRow>?>(null) }
    var exitKinds by remember { mutableStateOf<List<ExitKindCount>?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(farmId) {
        try {
            register = database.reports().herdRegister(farmId)
            exitKinds = database.animalExits().exitKindCounts(farmId)
        } catch (e: Exception) {
            failure = e.message ?: "The herd register could not be read"
        }
    }
    FarmOperationalPage("FOS-REPORT-003", "Herd or flock report", "Every animal on record, grouped by species, with exits by kind.", FarmVisualClass.I3, onBack, backLabel = "Reports") {
        failure?.let { AnimalFarmWarningSurface { Text(it) } }
        val rows = register
        when {
            rows == null -> Text("Reading the herd register")
            rows.isEmpty() -> AnimalFarmEmptyState("No animals are recorded on this farm yet.")
            else -> {
                val bySpecies = rows.groupBy { it.speciesCode }
                val ordered = speciesNames.keys.filter { it in bySpecies } + bySpecies.keys.filterNot { it in speciesNames }.sorted()
                ordered.forEach { code ->
                    val group = bySpecies.getValue(code)
                    val active = group.count { it.status == "active" }
                    FarmOperationalSection(speciesNames[code] ?: code, "$active active of ${group.size} recorded") {
                        group.forEach { row ->
                            Text("${row.tag}${row.name?.let { " · $it" } ?: ""} · ${row.status}${row.latestWeightGrams?.let { " · %.1f kg".format(it / 1000.0) } ?: ""}")
                        }
                    }
                }
                FarmOperationalSection("Exits by kind") {
                    val kinds = exitKinds
                    if (kinds.isNullOrEmpty()) {
                        Text("No exits recorded", color = AnimalFarmTheme.colors.mutedInk)
                    } else {
                        kinds.forEach { Text("${it.kind}: ${it.exits}") }
                    }
                }
            }
        }
    }
}

private data class HealthReportData(
    val observations: Int,
    val treatments: Int,
    val activeWithdrawals: Int,
    val latestObservations: List<HealthObservationEntity>,
    val latestTreatments: List<HealthTreatmentEntity>,
    val withdrawals: List<WithdrawalWindowEntity>,
    val animalTags: Map<String, String>,
    val treatmentAnimals: Map<String, String?>,
    val todayEpochDay: Long,
)

/** FOS-REPORT-004 — Health Report: observation and treatment totals with the latest records and running withdrawal windows. */
@Composable
internal fun HealthReportScreen(database: FarmOsDatabase, farmId: String, onBack: () -> Unit) {
    var data by remember { mutableStateOf<HealthReportData?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(farmId) {
        try {
            val today = LocalDate.now().toEpochDay()
            val totals = database.reports().healthTotals(farmId, today)
            val observations = database.healthObservations().recent(farmId, 25)
            val treatments = database.treatments().recent(farmId, 25)
            val withdrawals = database.lifecycle().withdrawals(farmId, 50)
            val treatmentAnimals = treatments.associate { it.id to it.animalId }
            val tags = mutableMapOf<String, String>()
            val animalIds = (observations.map { it.animalId } + treatments.map { it.animalId }).distinct()
            for (animalId in animalIds) {
                if (animalId != null && animalId !in tags) {
                    val animal = database.animals().get(farmId, animalId)
                    tags[animalId] = animal?.let { "${it.tag}${it.name?.let { name -> " ($name)" } ?: ""}" } ?: animalId
                }
            }
            data = HealthReportData(
                totals.observations, totals.treatments, totals.activeWithdrawals,
                observations, treatments, withdrawals, tags, treatmentAnimals, today,
            )
        } catch (e: Exception) {
            failure = e.message ?: "Health records could not be read"
        }
    }
    FarmOperationalPage("FOS-REPORT-004", "Health report", "Observations, treatments and withdrawal windows over every health record.", FarmVisualClass.I3, onBack, backLabel = "Reports") {
        failure?.let { AnimalFarmWarningSurface { Text(it) } }
        val report = data
        if (report == null) {
            Text("Reading health records")
            return@FarmOperationalPage
        }
        FarmOperationalSection("Totals") {
            Text("Observations: ${report.observations}")
            Text("Treatments: ${report.treatments}")
            Text("Withdrawals running today: ${report.activeWithdrawals}")
        }
        FarmOperationalSection("Latest observations", "Latest 25 over every record.") {
            if (report.latestObservations.isEmpty()) {
                Text("None recorded", color = AnimalFarmTheme.colors.mutedInk)
            } else {
                report.latestObservations.forEach { observation ->
                    val tag = report.animalTags[observation.animalId] ?: "group record"
                    Text("${millisDateText(observation.occurredAtEpochMillis)} · $tag · ${observation.signs}${if (observation.redFlag) " · red flag" else ""}")
                }
            }
        }
        FarmOperationalSection("Latest treatments", "Latest 25 over every record.") {
            if (report.latestTreatments.isEmpty()) {
                Text("None recorded", color = AnimalFarmTheme.colors.mutedInk)
            } else {
                report.latestTreatments.forEach { treatment ->
                    val tag = report.animalTags[treatment.animalId] ?: "group record"
                    Text("${millisDateText(treatment.occurredAtEpochMillis)} · $tag · ${treatment.reason}")
                }
            }
        }
        FarmOperationalSection("Withdrawal windows", "Latest 50; a window runs through its last day.") {
            if (report.withdrawals.isEmpty()) {
                Text("None recorded", color = AnimalFarmTheme.colors.mutedInk)
            } else {
                report.withdrawals.forEach { window ->
                    val animalId = report.treatmentAnimals[window.treatmentId]
                    val tag = report.animalTags[animalId] ?: "group record"
                    val state = if (window.endsEpochDay >= report.todayEpochDay) "running" else "ended"
                    Text("${window.product} (${window.windowKind}) · $tag · ends ${epochDayText(window.endsEpochDay)} · $state")
                }
            }
        }
    }
}

/** FOS-REPORT-005 — Production Report: production totals per product over every record. */
@Composable
internal fun ProductionReportScreen(database: FarmOsDatabase, farmId: String, onBack: () -> Unit) {
    var metrics by remember { mutableStateOf<List<MetricResult>?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(farmId) {
        try {
            metrics = productionMetrics(database.reports().productionTotals(farmId))
        } catch (e: Exception) {
            failure = e.message ?: "Production records could not be read"
        }
    }
    FarmOperationalPage("FOS-REPORT-005", "Production report", "Milk, wool and eggs over every record. A product with nothing recorded is left out.", FarmVisualClass.I3, onBack, backLabel = "Reports") {
        failure?.let { AnimalFarmWarningSurface { Text(it) } }
        when {
            metrics == null -> Text("Reading production records")
            metrics!!.isEmpty() -> AnimalFarmEmptyState("No production records on this device yet.")
            else -> metrics!!.forEach { MetricSection(it) }
        }
    }
}

private data class FinanceReportData(val metrics: List<MetricResult>, val records: List<MoneyRecordEntity>)

/** FOS-REPORT-006 — Finance Report: money totals per kind and currency with the most recent records. */
@Composable
internal fun FinanceReportScreen(database: FarmOsDatabase, farmId: String, onBack: () -> Unit) {
    var data by remember { mutableStateOf<FinanceReportData?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(farmId) {
        try {
            val totals = database.reports().moneyTotals(farmId)
            data = FinanceReportData(moneyMetrics(totals), database.reports().moneyRecords(farmId))
        } catch (e: Exception) {
            failure = e.message ?: "Money records could not be read"
        }
    }
    FarmOperationalPage("FOS-REPORT-006", "Finance report", "Income and expenses per currency over every money record. Currencies are never converted.", FarmVisualClass.I3, onBack, backLabel = "Reports") {
        failure?.let { AnimalFarmWarningSurface { Text(it) } }
        val report = data
        if (report == null) {
            Text("Reading money records")
            return@FarmOperationalPage
        }
        if (report.metrics.isEmpty()) {
            AnimalFarmEmptyState("No money records on this device yet.")
            return@FarmOperationalPage
        }
        report.metrics.forEach { MetricSection(it) }
        FarmOperationalSection("Recent records", "Most recent 50 of ${report.records.size}.") {
            report.records.takeLast(50).reversed().forEach { record ->
                val amount = BigDecimal.valueOf(record.amountMinor, FarmCurrency.minorDigits(record.currency)).toPlainString()
                Text("${epochDayText(record.occurredEpochDay)} · ${record.kind} · ${record.categoryCode} · $amount ${record.currency}${record.note?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}")
            }
        }
    }
}

private data class InventoryReportData(val totals: InventoryTotalRow, val items: List<InventoryItemEntity>)

/** FOS-REPORT-007 — Inventory Report: stock items with quantities and reorder flags. */
@Composable
internal fun InventoryReportScreen(database: FarmOsDatabase, farmId: String, onBack: () -> Unit) {
    var data by remember { mutableStateOf<InventoryReportData?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(farmId) {
        try {
            data = InventoryReportData(database.reports().inventoryTotals(farmId), database.inventory().items(farmId))
        } catch (e: Exception) {
            failure = e.message ?: "Inventory records could not be read"
        }
    }
    FarmOperationalPage("FOS-REPORT-007", "Inventory report", "Stock items with quantities and the reorder levels set for them.", FarmVisualClass.I3, onBack, backLabel = "Reports") {
        failure?.let { AnimalFarmWarningSurface { Text(it) } }
        val report = data
        if (report == null) {
            Text("Reading inventory records")
            return@FarmOperationalPage
        }
        FarmOperationalSection("Stock") {
            Text("Items tracked: ${report.totals.items}")
            Text("At or below reorder level: ${report.totals.atOrBelowReorder}")
        }
        FarmOperationalSection("Items") {
            if (report.items.isEmpty()) {
                Text("No stock items recorded", color = AnimalFarmTheme.colors.mutedInk)
            } else {
                report.items.forEach { item ->
                    val low = item.reorderMilli > 0 && item.quantityMilli <= item.reorderMilli
                    Text(
                        "${item.name} (${item.sku}): ${BigDecimal.valueOf(item.quantityMilli, 3).stripTrailingZeros().toPlainString()} ${item.unit}" +
                            if (item.reorderMilli > 0) " · reorder at ${BigDecimal.valueOf(item.reorderMilli, 3).stripTrailingZeros().toPlainString()} ${item.unit}${if (low) " · at or below reorder" else ""}" else "",
                        color = if (low) AnimalFarmTheme.colors.critical else AnimalFarmTheme.colors.ink,
                    )
                }
            }
        }
    }
}

/** FOS-REPORT-008 — Breeding Report: birth totals per species over every recorded birth event. */
@Composable
internal fun BreedingReportScreen(database: FarmOsDatabase, farmId: String, onBack: () -> Unit) {
    var metrics by remember { mutableStateOf<List<MetricResult>?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(farmId) {
        try {
            metrics = birthMetrics(database.reports().birthTotals(farmId))
        } catch (e: Exception) {
            failure = e.message ?: "Birth records could not be read"
        }
    }
    FarmOperationalPage("FOS-REPORT-008", "Breeding report", "Kiddings, lambings, calvings and kindlings over every recorded birth event.", FarmVisualClass.I3, onBack, backLabel = "Reports") {
        failure?.let { AnimalFarmWarningSurface { Text(it) } }
        when {
            metrics == null -> Text("Reading birth records")
            metrics!!.isEmpty() -> AnimalFarmEmptyState("No birth events recorded yet.")
            else -> metrics!!.forEach { MetricSection(it) }
        }
    }
}

private data class GeneticsReportData(
    val totalAnimals: Int,
    val relationCount: Int,
    val withParentage: Int,
    val conflicts: List<String>,
)

/** FOS-REPORT-009 — Genetics Report: pedigree coverage — animals, links, parentage and conflicts. */
@Composable
internal fun GeneticsReportScreen(database: FarmOsDatabase, farmId: String, onBack: () -> Unit) {
    var data by remember { mutableStateOf<GeneticsReportData?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(farmId) {
        try {
            val total = database.reports().herdRegister(farmId).size
            val relations = database.lifecycle().pedigreeRelationCount(farmId)
            val withParentage = database.lifecycle().animalsWithParentageCount(farmId)
            val conflicts = database.lifecycle().animalsWithConflictingParentage(farmId).map { animalId ->
                database.animals().get(farmId, animalId)?.let { "${it.tag}${it.name?.let { name -> " ($name)" } ?: ""}" } ?: animalId
            }
            data = GeneticsReportData(total, relations, withParentage, conflicts)
        } catch (e: Exception) {
            failure = e.message ?: "Pedigree records could not be read"
        }
    }
    FarmOperationalPage("FOS-REPORT-009", "Genetics report", "Pedigree coverage: how many animals have recorded parentage, and where it conflicts.", FarmVisualClass.I3, onBack, backLabel = "Reports") {
        failure?.let { AnimalFarmWarningSurface { Text(it) } }
        val report = data
        if (report == null) {
            Text("Reading pedigree records")
            return@FarmOperationalPage
        }
        FarmOperationalSection("Coverage") {
            Text("Animals on record: ${report.totalAnimals}")
            Text("Pedigree links: ${report.relationCount}")
            val coverage = if (report.totalAnimals > 0) " (${report.withParentage * 100 / report.totalAnimals}%)" else ""
            Text("Animals with parentage: ${report.withParentage}$coverage")
            Text("Animals with conflicting parentage: ${report.conflicts.size}")
        }
        FarmOperationalSection("Conflicting parentage", "Animals with more than one recorded sire or dam.") {
            if (report.conflicts.isEmpty()) {
                Text("None", color = AnimalFarmTheme.colors.mutedInk)
            } else {
                report.conflicts.forEach { Text(it) }
            }
        }
    }
}

private data class OperationsReportData(val openTasks: List<TaskEntity>, val doneCount: Int, val labourCount: Int)

/** FOS-REPORT-010 — Operations Report: open and done task counts with the open tasks, plus labour entries. */
@Composable
internal fun OperationsReportScreen(database: FarmOsDatabase, farmId: String, onBack: () -> Unit) {
    var data by remember { mutableStateOf<OperationsReportData?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(farmId) {
        try {
            data = OperationsReportData(
                database.tasks().openForFarm(farmId),
                database.tasks().countCompletedForFarm(farmId),
                database.labour().count(farmId),
            )
        } catch (e: Exception) {
            failure = e.message ?: "Operations records could not be read"
        }
    }
    FarmOperationalPage("FOS-REPORT-010", "Operations report", "Tasks and labour entries over every record.", FarmVisualClass.I3, onBack, backLabel = "Reports") {
        failure?.let { AnimalFarmWarningSurface { Text(it) } }
        val report = data
        if (report == null) {
            Text("Reading operations records")
            return@FarmOperationalPage
        }
        FarmOperationalSection("Tasks") {
            Text("Open: ${report.openTasks.size}")
            Text("Done: ${report.doneCount}")
        }
        FarmOperationalSection("Open tasks") {
            if (report.openTasks.isEmpty()) {
                Text("No open tasks", color = AnimalFarmTheme.colors.mutedInk)
            } else {
                report.openTasks.forEach { task ->
                    Text("${task.title} · due ${epochDayText(task.dueOnEpochDay)} · ${task.moduleCode}")
                }
            }
        }
        FarmOperationalSection("Labour") {
            Text("Labour entries recorded: ${report.labourCount}")
        }
    }
}

/** FOS-REPORT-012 — Generated Documents: the log of exports made on this device, newest first. */
@Composable
internal fun GeneratedDocumentsScreen(farmId: String, onOpenDocument: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<ExportLogEntry>?>(null) }
    LaunchedEffect(farmId) {
        entries = withContext(Dispatchers.IO) { readExportLog(context, farmId) }
    }
    FarmOperationalPage("FOS-REPORT-012", "Generated documents", "Every export made on this device. Filenames are the suggested names; the file may have been saved under a different name.", FarmVisualClass.I3, onBack, backLabel = "Reports") {
        val logged = entries
        when {
            logged == null -> Text("Reading the export log")
            logged.isEmpty() -> AnimalFarmEmptyState("No exports have been made on this device yet.")
            else -> FarmOperationalSection("Exports") {
                logged.forEach { entry ->
                    TextButton(onClick = { onOpenDocument(entry.id) }, modifier = Modifier.fillMaxWidth().testTag("report-document:${entry.id}")) {
                        Text("${exportDateTimeText(entry.timestamp)} · ${entry.kind} · ${entry.count} record(s)")
                    }
                }
            }
        }
    }
}

/** FOS-REPORT-013 — Document Viewer: the detail of one logged export. */
@Composable
internal fun ExportDocumentScreen(farmId: String, entryId: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<ExportLogEntry>?>(null) }
    LaunchedEffect(farmId) {
        entries = withContext(Dispatchers.IO) { readExportLog(context, farmId) }
    }
    val entry = entries?.let { logged -> if (entryId != null) logged.firstOrNull { it.id == entryId } else logged.firstOrNull() }
    FarmOperationalPage("FOS-REPORT-013", "Document viewer", "The detail of one logged export.", FarmVisualClass.I3, onBack, backLabel = "Reports") {
        when {
            entries == null -> Text("Reading the export log")
            entry == null -> AnimalFarmEmptyState(
                if (entryId != null) "That export is no longer in the log." else "No exports have been made on this device yet.",
            )
            else -> FarmOperationalSection("Export", "The file itself lives where it was saved; this is the log entry.") {
                Text("Kind: ${entry.kind}", fontWeight = FontWeight.SemiBold)
                Text("Suggested filename: ${entry.filename}")
                Text("Made: ${exportDateTimeText(entry.timestamp)}")
                Text("Records: ${entry.count}")
            }
        }
    }
}

/** The farm summary as plain text, shared through the system share sheet. */
internal fun buildFarmSummaryText(metrics: List<MetricResult>, generatedOn: LocalDate): String = buildString {
    appendLine("Farm summary — $generatedOn")
    appendLine("Figures from every record on this device.")
    metrics.forEach { result ->
        appendLine()
        appendLine("${result.definition.name}: ${metricValueText(result)}")
        appendLine("How: ${result.definition.formula}")
        appendLine("Period: ${result.definition.period} · Scope: ${result.definition.scope}")
        appendLine("Completeness: ${result.completeness}")
    }
}

/** FOS-REPORT-014 — Share or Print: share the farm summary through the system share sheet. */
@Composable
internal fun ShareReportScreen(
    database: FarmOsDatabase,
    farmId: String,
    canShare: () -> Boolean,
    exportAuthority: LocalSessionAuthority?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(farmId) {
        try {
            summary = buildFarmSummaryText(farmMetrics(database, farmId, LocalDate.now()), LocalDate.now())
        } catch (e: Exception) {
            failure = e.message ?: "The farm summary could not be prepared"
        }
    }
    FarmOperationalPage("FOS-REPORT-014", "Share or print", "The farm summary as plain text, shared through the system share sheet.", FarmVisualClass.I3, onBack, backLabel = "Reports") {
        failure?.let { AnimalFarmWarningSurface { Text(it) } }
        val text = summary
        if (text == null) {
            Text("Preparing the summary")
        } else {
            FarmOperationalSection("Preview") {
                Text(text)
            }
            Button(
                onClick = {
                    if (!canShare() || sharing) return@Button
                    sharing = true
                    failure = null
                    scope.launch {
                        try {
                            withReportDisclosure(farmId, exportAuthority) {
                                withContext(Dispatchers.Main.immediate) {
                                    check(canShare()) { "Exports are made by farm management." }
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Farm summary")
                                        putExtra(Intent.EXTRA_TEXT, text)
                                    }
                                    context.startActivity(Intent.createChooser(send, "Share farm summary"))
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (e: Exception) {
                            failure = "Share failed: ${e.message ?: "the summary could not be shared"}"
                        } finally {
                            sharing = false
                        }
                    }
                },
                enabled = canShare() && !sharing,
                modifier = Modifier.fillMaxWidth().testTag("report-share"),
            ) {
                Text("Share")
            }
        }
    }
}

private data class MovementCertificate(val movement: OfficialMovementEntity, val tag: String, val name: String?)

private fun movementDirectionLabel(direction: String): String = when (direction) {
    "on" -> "On-farm"
    "off" -> "Off-farm"
    "transfer" -> "Transfer"
    else -> direction
}

/** FOS-REPORT-015 — Certificate Viewer: official animal movement records on this device. */
@Composable
internal fun MovementCertificatesScreen(database: FarmOsDatabase, farmId: String, onBack: () -> Unit) {
    var certificates by remember { mutableStateOf<List<MovementCertificate>?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(farmId) {
        try {
            val register = database.reports().herdRegister(farmId)
            val all = mutableListOf<MovementCertificate>()
            for (row in register) {
                database.lifecycle().movementsForAnimal(farmId, row.id).forEach { movement ->
                    all += MovementCertificate(movement, row.tag, row.name)
                }
            }
            certificates = all.sortedByDescending { it.movement.occurredEpochDay }
        } catch (e: Exception) {
            failure = e.message ?: "Movement records could not be read"
        }
    }
    FarmOperationalPage("FOS-REPORT-015", "Movement certificates", "Official movement records: every recorded on-farm, off-farm and transfer movement.", FarmVisualClass.I3, onBack, backLabel = "Reports") {
        failure?.let { AnimalFarmWarningSurface { Text(it) } }
        val list = certificates
        when {
            list == null -> Text("Reading movement records")
            list.isEmpty() -> AnimalFarmEmptyState("No official movement records on this device yet.")
            else -> FarmOperationalSection("Movements", "Newest first.") {
                list.forEach { certificate ->
                    val movement = certificate.movement
                    Text(
                        "${epochDayText(movement.occurredEpochDay)} · ${certificate.tag}${certificate.name?.let { " ($it)" } ?: ""} · " +
                            "${movementDirectionLabel(movement.direction)} · from ${movement.fromPlace ?: "—"} to ${movement.toPlace ?: "—"}",
                    )
                }
            }
        }
    }
}
