package com.farmos.feature.rabbit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.AnimalFarmWarningSurface
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmVisualClass
import java.time.LocalDate

data class RabbitNestBoxView(val id: String, val code: String, val status: String)

/** One recorded wave event; [kind] is the record family, [summary] its recorded facts. */
data class RabbitWaveEventView(val id: String, val epochDay: Long, val kind: String, val summary: String)

/** A breeding wave with the schedule dates stored when it was created, and its recorded events. */
data class RabbitWaveView(
    val id: String,
    val cageLabel: String,
    val doeCount: Int,
    val matingEpochDay: Long,
    val nestInEpochDay: Long,
    val kindlingEpochDay: Long,
    val nestOutEpochDay: Long,
    val rebreedEpochDay: Long,
    val weanEpochDay: Long,
    val events: List<RabbitWaveEventView>,
    val kindlingRecorded: Boolean,
    /** Latest recorded palpation result, or null when none is recorded. */
    val latestPalpation: String? = null,
    /** Latest recorded mating outcome, or null when none is recorded. */
    val latestOutcome: String? = null,
)

/**
 * Read-model kindling classification of a wave, derived only from recorded facts. Not persisted.
 * Mating outcomes are the canonical false_pregnancy / open / pregnant / kindled; palpation is
 * supporting evidence only and never makes a wave pregnant on its own.
 */
enum class RabbitKindlingState {
    /** No kindling recorded and the latest outcome is pregnant, or no outcome is recorded. */
    AWAITING_KINDLING,

    /** Latest recorded outcome is false_pregnancy or open. */
    NOT_PREGNANT_RECORDED,

    /** A kindling event is recorded for the wave. */
    KINDLED_RECORDED,

    /** Latest outcome says kindled but no kindling event is recorded: a record inconsistency. */
    KINDLED_OUTCOME_WITHOUT_KINDLING_RECORD,

    /** Latest outcome is not one of the canonical values; shown as recorded, never treated as due. */
    OUTCOME_NOT_RECOGNISED,
}

fun rabbitKindlingState(kindlingRecorded: Boolean, latestOutcome: String?): RabbitKindlingState = when {
    kindlingRecorded -> RabbitKindlingState.KINDLED_RECORDED
    latestOutcome == null || latestOutcome == "pregnant" -> RabbitKindlingState.AWAITING_KINDLING
    latestOutcome == "false_pregnancy" || latestOutcome == "open" -> RabbitKindlingState.NOT_PREGNANT_RECORDED
    latestOutcome == "kindled" -> RabbitKindlingState.KINDLED_OUTCOME_WITHOUT_KINDLING_RECORD
    else -> RabbitKindlingState.OUTCOME_NOT_RECOGNISED
}

val RabbitWaveView.kindlingState: RabbitKindlingState get() = rabbitKindlingState(kindlingRecorded, latestOutcome)

private fun RabbitKindlingState.label(outcome: String?): String = when (this) {
    RabbitKindlingState.AWAITING_KINDLING -> if (outcome == null) "Awaiting kindling · no outcome recorded" else "Awaiting kindling · outcome pregnant"
    RabbitKindlingState.NOT_PREGNANT_RECORDED -> "Not pregnant · outcome $outcome"
    RabbitKindlingState.KINDLED_RECORDED -> "Kindling recorded"
    RabbitKindlingState.KINDLED_OUTCOME_WITHOUT_KINDLING_RECORD -> "Outcome kindled, but no kindling record on this device"
    RabbitKindlingState.OUTCOME_NOT_RECOGNISED -> "Outcome not recognised: $outcome"
}

data class RabbitCageView(val id: String, val code: String, val doeCapacity: Int, val nestBoxes: List<RabbitNestBoxView>, val waveIds: List<String>)

data class RabbitKitView(val id: String, val waveId: String, val label: String, val sex: String, val status: String, val retention: String, val earTag: String?)

/** FOS-RABBIT-032 — one governed rabbit weighing, read model for the weight record pages. */
data class RabbitWeightView(
    val id: String,
    val animalId: String,
    val animalLabel: String,
    val weightKg: Double,
    val weighedAtEpochMillis: Long,
    val notes: String?,
)

/** Local, farm-scoped rabbitry records for the read-only record pages. Nothing here writes. */
data class RabbitRecords(
    val cages: List<RabbitCageView> = emptyList(),
    val waves: List<RabbitWaveView> = emptyList(),
    val kits: List<RabbitKitView> = emptyList(),
    val weights: List<RabbitWeightView> = emptyList(),
)

private val AVAILABLE_BOX_STATES = setOf("available", "sanitized")

private fun date(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).toString()

private fun waveLabel(wave: RabbitWaveView): String = "${date(wave.matingEpochDay)} · cage ${wave.cageLabel} · ${wave.doeCount} does"

@Composable
private fun RabbitRecordRow(primary: String, secondary: String, tag: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(tag), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(primary, color = AnimalFarmTheme.colors.mutedInk)
        Text(secondary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RabbitSwitcher(title: String, options: List<Pair<String, String>>, selectedId: String?, onSelect: (String) -> Unit) {
    FarmOperationalSection(title) {
        options.forEach { (id, label) ->
            val selected = id == selectedId
            TextButton(
                onClick = { onSelect(id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                    .semantics { this.selected = selected }
                    .testTag("rabbit-option:$id"),
            ) { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
}

/** FOS-RABBIT-008 — every cage with its recorded capacity, nest boxes and waves. */
@Composable
internal fun RabbitCageOccupancyScreen(records: RabbitRecords, onBack: () -> Unit) {
    FarmOperationalPage("FOS-RABBIT-008", "Cage occupancy", "Recorded capacity, nest boxes and wave assignments per cage; not a physical head count.", FarmVisualClass.I3, onBack) {
        if (records.cages.isEmpty()) {
            AnimalFarmEmptyState("No cages on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection("Cages · ${records.cages.size}") {
            records.cages.forEachIndexed { index, cage ->
                if (index > 0) HorizontalDivider()
                val available = cage.nestBoxes.count { it.status in AVAILABLE_BOX_STATES }
                val latest = records.waves.filter { it.id in cage.waveIds }.maxByOrNull { it.matingEpochDay }
                RabbitRecordRow(
                    "Cage ${cage.code} · capacity ${cage.doeCapacity} does",
                    "$available of ${cage.nestBoxes.size} nest boxes available · ${cage.waveIds.size} waves" +
                        (latest?.let { " · latest ${it.doeCount} does mated ${date(it.matingEpochDay)}" } ?: ""),
                    "rabbit-cage:${cage.id}",
                )
            }
        }
    }
}

/** FOS-RABBIT-007 — one cage's nest boxes and breeding waves. */
@Composable
internal fun RabbitCageDetailScreen(records: RabbitRecords, onBack: () -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf(records.cages.firstOrNull()?.id) }
    FarmOperationalPage("FOS-RABBIT-007", "Cage detail", "Nest boxes and breeding waves for one cage.", FarmVisualClass.I3, onBack) {
        if (records.cages.isEmpty()) {
            AnimalFarmEmptyState("No cages on this device.")
            return@FarmOperationalPage
        }
        RabbitSwitcher("Cages", records.cages.map { it.id to "Cage ${it.code}" }, selectedId) { selectedId = it }
        val cage = records.cages.firstOrNull { it.id == selectedId } ?: return@FarmOperationalPage
        FarmOperationalSection("Cage ${cage.code}") {
            RabbitRecordRow("Doe capacity", cage.doeCapacity.toString(), "rabbit-cage-capacity")
        }
        FarmOperationalSection("Nest boxes · ${cage.nestBoxes.size}") {
            if (cage.nestBoxes.isEmpty()) Text("No nest boxes recorded for this cage.", color = AnimalFarmTheme.colors.mutedInk)
            cage.nestBoxes.forEach { RabbitRecordRow(it.code, it.status, "rabbit-cage-box:${it.id}") }
        }
        val waves = records.waves.filter { it.id in cage.waveIds }.sortedByDescending { it.matingEpochDay }
        FarmOperationalSection("Breeding waves · ${waves.size}") {
            if (waves.isEmpty()) Text("No breeding waves recorded for this cage.", color = AnimalFarmTheme.colors.mutedInk)
            waves.forEach { RabbitRecordRow("Mated ${date(it.matingEpochDay)}", "${it.doeCount} does · wean ${date(it.weanEpochDay)}", "rabbit-cage-wave:${it.id}") }
        }
    }
}

/** FOS-RABBIT-016 — waves awaiting a kindling record, by the kindling date stored on the wave. */
@Composable
internal fun RabbitKindlingDueScreen(records: RabbitRecords, today: LocalDate, onBack: () -> Unit) {
    FarmOperationalPage(
        "FOS-RABBIT-016",
        "Kindling due",
        "Waves with no kindling recorded whose latest outcome is pregnant or not yet recorded, by the kindling date stored on the wave.",
        FarmVisualClass.I2,
        onBack,
    ) {
        val due = records.waves.filter { it.kindlingState == RabbitKindlingState.AWAITING_KINDLING }.sortedWith(compareBy<RabbitWaveView>({ it.kindlingEpochDay }, { it.id }))
        val inconsistent = records.waves.count { it.kindlingState == RabbitKindlingState.KINDLED_OUTCOME_WITHOUT_KINDLING_RECORD }
        if (inconsistent > 0) {
            AnimalFarmWarningSurface(Modifier.testTag("rabbit-kindling-inconsistent")) {
                Text("$inconsistent wave(s) have a kindled outcome but no kindling record. See the litter profile.", fontWeight = FontWeight.Bold)
            }
        }
        if (due.isEmpty()) {
            AnimalFarmEmptyState("No waves awaiting a kindling record on this device.")
            return@FarmOperationalPage
        }
        val past = due.count { it.kindlingEpochDay < today.toEpochDay() }
        if (past > 0) {
            AnimalFarmWarningSurface(Modifier.testTag("rabbit-kindling-past")) {
                Text("$past wave(s) past the scheduled kindling date without a kindling record", fontWeight = FontWeight.Bold)
            }
        }
        FarmOperationalSection("Awaiting kindling record · ${due.size}") {
            due.forEachIndexed { index, wave ->
                if (index > 0) HorizontalDivider()
                val days = wave.kindlingEpochDay - today.toEpochDay()
                val relative = when {
                    days > 0 -> "in $days days"
                    days == 0L -> "scheduled today"
                    else -> "${-days} days past scheduled date"
                }
                val recorded = listOf(
                    wave.latestOutcome?.let { "outcome $it" } ?: "no outcome recorded",
                    wave.latestPalpation?.let { "palpation $it" } ?: "no palpation recorded",
                ).joinToString(" · ")
                RabbitRecordRow(waveLabel(wave), "Kindling ${date(wave.kindlingEpochDay)} · $relative · $recorded", "rabbit-kindling-due:${wave.id}")
            }
        }
    }
}

/** FOS-RABBIT-018 — one wave's stored schedule, recorded events and kits. */
/** FOS-RABBIT-035 — rabbit timeline: the recorded-events section is the wave's dated event timeline (root tag FOS-RABBIT-018). */
@Composable
internal fun RabbitLitterProfileScreen(records: RabbitRecords, onBack: () -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf(records.waves.firstOrNull()?.id) }
    FarmOperationalPage("FOS-RABBIT-018", "Litter profile", "Schedule, recorded events and kits for one breeding wave.", FarmVisualClass.I2, onBack) {
        if (records.waves.isEmpty()) {
            AnimalFarmEmptyState("No breeding waves on this device.")
            return@FarmOperationalPage
        }
        RabbitSwitcher("Waves", records.waves.map { it.id to waveLabel(it) }, selectedId) { selectedId = it }
        val wave = records.waves.firstOrNull { it.id == selectedId } ?: return@FarmOperationalPage
        val state = wave.kindlingState
        if (state == RabbitKindlingState.KINDLED_OUTCOME_WITHOUT_KINDLING_RECORD || state == RabbitKindlingState.OUTCOME_NOT_RECOGNISED) {
            AnimalFarmWarningSurface(Modifier.testTag("rabbit-litter-inconsistent")) {
                Text(state.label(wave.latestOutcome), fontWeight = FontWeight.Bold)
            }
        }
        FarmOperationalSection("Kindling status") {
            RabbitRecordRow("Recorded state", state.label(wave.latestOutcome), "rabbit-litter-state")
            RabbitRecordRow("Latest palpation", wave.latestPalpation ?: "No palpation recorded", "rabbit-litter-palpation")
        }
        FarmOperationalSection("Schedule stored on the wave") {
            RabbitRecordRow("Mating", date(wave.matingEpochDay), "rabbit-litter-mating")
            RabbitRecordRow("Nest in", date(wave.nestInEpochDay), "rabbit-litter-nest-in")
            RabbitRecordRow("Kindling", date(wave.kindlingEpochDay), "rabbit-litter-kindling")
            RabbitRecordRow("Nest out", date(wave.nestOutEpochDay), "rabbit-litter-nest-out")
            RabbitRecordRow("Rebreed", date(wave.rebreedEpochDay), "rabbit-litter-rebreed")
            RabbitRecordRow("Wean", date(wave.weanEpochDay), "rabbit-litter-wean")
        }
        FarmOperationalSection("Recorded events · ${wave.events.size}") {
            if (wave.events.isEmpty()) Text("No events recorded for this wave.", color = AnimalFarmTheme.colors.mutedInk)
            wave.events.forEachIndexed { index, event ->
                if (index > 0) HorizontalDivider()
                RabbitRecordRow("${date(event.epochDay)} · ${event.kind}", event.summary, "rabbit-litter-event:${event.id}")
            }
        }
        val kits = records.kits.filter { it.waveId == wave.id }
        FarmOperationalSection("Kits · ${kits.size}") {
            if (kits.isEmpty()) Text("No individual kits recorded for this wave.", color = AnimalFarmTheme.colors.mutedInk)
            kits.groupingBy { it.status }.eachCount().toSortedMap().forEach { (status, count) ->
                RabbitRecordRow(status, count.toString(), "rabbit-litter-kits:$status")
            }
        }
    }
}

/** FOS-RABBIT-019 — individual kits recorded on this device, grouped by wave. */
/** FOS-RABBIT-023 — individualise kits: this census is the individual-kit surface (root tag FOS-RABBIT-019). */
@Composable
internal fun RabbitKitCensusScreen(records: RabbitRecords, onBack: () -> Unit) {
    FarmOperationalPage("FOS-RABBIT-019", "Kit census", "Individual kits recorded on this device.", FarmVisualClass.I3, onBack) {
        if (records.kits.isEmpty()) {
            AnimalFarmEmptyState("No individual kits recorded on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection("Summary") {
            RabbitRecordRow("Kits recorded", records.kits.size.toString(), "rabbit-kits-total")
            records.kits.groupingBy { it.status }.eachCount().toSortedMap().forEach { (status, count) ->
                RabbitRecordRow(status, count.toString(), "rabbit-kits-status:$status")
            }
        }
        val waves = records.waves.associateBy { it.id }
        records.kits.groupBy { it.waveId }.toSortedMap().forEach { (waveId, kits) ->
            val title = waves[waveId]?.let(::waveLabel) ?: "Wave not on this device"
            FarmOperationalSection("$title · ${kits.size}") {
                kits.forEachIndexed { index, kit ->
                    if (index > 0) HorizontalDivider()
                    RabbitRecordRow(
                        "${kit.label} · ${kit.sex}",
                        "${kit.status} · ${kit.retention}" + (kit.earTag?.let { " · tag $it" } ?: ""),
                        "rabbit-kit:${kit.id}",
                    )
                }
            }
        }
    }
}
