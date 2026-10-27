package com.farmos.feature.rabbit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmHomeMetrics
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.FarmActionButton
import com.farmos.core.design.FarmFilterChip
import com.farmos.core.design.FarmOperationalSection
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A passed scheduled date does not establish that the corresponding operation happened. */
internal data class RabbitWorkflowMilestone(
    val key: String,
    val label: String,
    val scheduledEpochDay: Long,
    val hasRecordedActivity: Boolean = false,
    val recordedEpochDays: List<Long> = emptyList(),
)

internal fun rabbitWorkflowMilestones(wave: RabbitWaveView): List<RabbitWorkflowMilestone> {
    fun days(kind: String) = wave.events.filter { it.kind == kind }.map { it.epochDay }.distinct().sorted()
    val kindling = days("Kindling")
    val weaning = days("Weaning")
    return listOf(
        RabbitWorkflowMilestone("mating", "Mating", wave.matingEpochDay),
        RabbitWorkflowMilestone("nest_in", "Nest placement", wave.nestInEpochDay),
        RabbitWorkflowMilestone("kindling", "Kindling", wave.kindlingEpochDay, wave.kindlingRecorded || kindling.isNotEmpty(), kindling),
        RabbitWorkflowMilestone("nest_out", "Nest removal", wave.nestOutEpochDay),
        RabbitWorkflowMilestone("rebreed", "Rebreeding", wave.rebreedEpochDay),
        RabbitWorkflowMilestone("wean", "Weaning", wave.weanEpochDay, weaning.isNotEmpty(), weaning),
    ).sortedBy { it.scheduledEpochDay }
}

internal enum class RabbitScheduledTiming { PAST, TODAY, UPCOMING }
internal fun rabbitScheduledTiming(day: Long, today: LocalDate): RabbitScheduledTiming = when {
    day < today.toEpochDay() -> RabbitScheduledTiming.PAST
    day == today.toEpochDay() -> RabbitScheduledTiming.TODAY
    else -> RabbitScheduledTiming.UPCOMING
}

internal fun rabbitCageWaves(cage: RabbitCageView, waves: List<RabbitWaveView>): List<RabbitWaveView> {
    val byId = waves.associateBy { it.id }
    return cage.waveIds.distinct().mapNotNull(byId::get).sortedWith(compareBy<RabbitWaveView> { it.matingEpochDay }.thenBy { it.id })
}

/** Prefer a current or upcoming stored plan; fall back to the most recent history. */
internal fun rabbitRelevantWave(waves: List<RabbitWaveView>, today: LocalDate): RabbitWaveView? {
    val currentAndUpcoming = waves.filter { it.weanEpochDay >= today.toEpochDay() }
    return currentAndUpcoming.minWithOrNull(compareBy<RabbitWaveView> { it.matingEpochDay }.thenBy { it.id })
        ?: waves.maxWithOrNull(compareBy<RabbitWaveView> { it.weanEpochDay }.thenBy { it.id })
}

private val nestStages = listOf(
    Triple("ready", "Ready", setOf("available", "sanitized")),
    Triple("assigned", "Assigned", setOf("assigned")),
    Triple("in_cage", "In cage", setOf("in_cage")),
    Triple("dirty", "To clean", setOf("dirty")),
)
internal fun rabbitNestStageCounts(boxes: List<RabbitNestBoxView>): Map<String, Int> =
    nestStages.associate { (key, _, states) -> key to boxes.count { it.status in states } }

@Composable
fun RabbitWorkflowOverview(records: RabbitRecords, today: LocalDate, onBreeding: () -> Unit, onHousing: () -> Unit) {
    val colors = AnimalFarmTheme.colors
    FarmOperationalSection("Rabbit workflow") {
        if (LocalDensity.current.fontScale >= 1.5f) {
            listOf(
                records.waves.size to "Wave plans",
                records.waves.count { it.kindlingRecorded } to "Kindled waves",
                records.cages.size to "Cages",
            ).forEach { (count, label) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(count.toString(), style = MaterialTheme.typography.headlineSmall)
                    Text(label, Modifier.weight(1f), color = colors.mutedInk, style = MaterialTheme.typography.labelMedium)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WorkflowCount(records.waves.size, "Wave plans", Modifier.weight(1f))
                WorkflowCount(records.waves.count { it.kindlingRecorded }, "Kindled waves", Modifier.weight(1f))
                WorkflowCount(records.cages.size, "Cages", Modifier.weight(1f))
            }
        }
        val next = rabbitRelevantWave(records.waves, today)
        if (next == null) Text("No wave plans yet.", color = colors.mutedInk)
        else {
            Text("Cage ${next.cageLabel} · ${next.doeCount} does", style = MaterialTheme.typography.labelLarge)
            if (next.kindlingState !in setOf(RabbitKindlingState.AWAITING_KINDLING, RabbitKindlingState.KINDLED_RECORDED)) {
                Text(recordedWaveLabel(next), color = colors.ink, style = MaterialTheme.typography.labelMedium)
            }
            RabbitWavePreview(next)
            Text("Planned nest window · ${dayLabel(next.nestInEpochDay)} – ${dayLabel(next.nestOutEpochDay)}", color = colors.mutedInk, style = MaterialTheme.typography.labelMedium)
        }
        FarmActionButton(onClick = onBreeding, modifier = Modifier.fillMaxWidth()) { Text("Breeding & litters") }
        FarmActionButton(onClick = onHousing, modifier = Modifier.fillMaxWidth()) { Text("Cage rotation") }
    }
}


/** A labeled summary; the detail view retains all six stored milestones. */
@Composable
private fun RabbitWavePreview(wave: RabbitWaveView) {
    val colors = AnimalFarmTheme.colors
    val stages = rabbitWorkflowMilestones(wave).filter { it.key in setOf("mating", "kindling", "wean") }
    Text("Planned dates", color = colors.mutedInk, style = MaterialTheme.typography.labelMedium)
    if (LocalDensity.current.fontScale >= 1.5f) {
        Column {
            stages.forEachIndexed { index, stage ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Canvas(Modifier.width(24.dp).fillMaxHeight()) {
                        val center = Offset(size.width / 2, 12.dp.toPx())
                        if (index > 0) drawLine(colors.divider, Offset(center.x, 0f), center, 2.dp.toPx())
                        if (index < stages.lastIndex) drawLine(colors.divider, center, Offset(center.x, size.height), 2.dp.toPx())
                        drawCircle(if (stage.hasRecordedActivity) colors.primary else colors.surface, 6.dp.toPx(), center)
                        drawCircle(colors.primary, 6.dp.toPx(), center, style = Stroke(2.dp.toPx()))
                    }
                    Column(Modifier.weight(1f).padding(bottom = 10.dp)) {
                        Text(stage.label, style = MaterialTheme.typography.labelLarge)
                        Text(dayLabel(stage.scheduledEpochDay), color = colors.mutedInk, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    } else {
        Row(Modifier.fillMaxWidth()) {
            stages.forEachIndexed { index, stage ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                        val center = Offset(size.width / 2, size.height / 2)
                        if (index > 0) drawLine(colors.divider, Offset(0f, center.y), center, 2.dp.toPx())
                        if (index < stages.lastIndex) drawLine(colors.divider, center, Offset(size.width, center.y), 2.dp.toPx())
                        drawCircle(if (stage.hasRecordedActivity) colors.primary else colors.surface, 6.dp.toPx(), center)
                        drawCircle(colors.primary, 6.dp.toPx(), center, style = Stroke(2.dp.toPx()))
                    }
                    Text(stage.label, style = MaterialTheme.typography.labelMedium)
                    Text(dayLabel(stage.scheduledEpochDay), color = colors.mutedInk, style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
    if (stages.any { it.hasRecordedActivity }) {
        Text("Filled markers have recorded activity.", color = colors.mutedInk, style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RabbitWaveTimeline(wave: RabbitWaveView, today: LocalDate, showOutcome: Boolean = true) {
    val colors = AnimalFarmTheme.colors
    var showRecords by rememberSaveable(wave.id) { mutableStateOf(false) }
    val milestones = rabbitWorkflowMilestones(wave)
    FarmOperationalSection("Wave timeline", "Cage ${wave.cageLabel} · ${wave.doeCount} does") {
        if (showOutcome) Text(recordedWaveLabel(wave), color = colors.ink, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FarmFilterChip("Schedule", !showRecords, { showRecords = false })
            FarmFilterChip("Records (${wave.events.size})", showRecords, { showRecords = true })
        }
        if (!showRecords) {
            Column(Modifier.testTag("rabbit-wave-timeline:${wave.id}")) {
                milestones.forEachIndexed { index, stage ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Canvas(Modifier.width(24.dp).fillMaxHeight()) {
                            val center = Offset(size.width / 2, 11.dp.toPx())
                            if (index > 0) drawLine(colors.divider, Offset(center.x, 0f), center, 2.dp.toPx())
                            if (index < milestones.lastIndex) drawLine(colors.divider, center, Offset(center.x, size.height), 2.dp.toPx())
                            drawCircle(if (stage.hasRecordedActivity) colors.primary else colors.surface, 7.dp.toPx(), center)
                            drawCircle(colors.primary, 7.dp.toPx(), center, style = Stroke(2.dp.toPx()))
                        }
                        Column(Modifier.weight(1f).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(stage.label, style = MaterialTheme.typography.titleSmall)
                            Text("Planned ${dayLabel(stage.scheduledEpochDay)}", color = colors.mutedInk, style = MaterialTheme.typography.bodySmall)
                            val evidence = when {
                                stage.recordedEpochDays.isNotEmpty() -> "Recorded " + stage.recordedEpochDays.joinToString { dayLabel(it) }
                                stage.hasRecordedActivity -> "Record present"
                                else -> when (rabbitScheduledTiming(stage.scheduledEpochDay, today)) {
                                    RabbitScheduledTiming.PAST -> "Past scheduled date"
                                    RabbitScheduledTiming.TODAY -> "Scheduled today"
                                    RabbitScheduledTiming.UPCOMING -> "Upcoming schedule"
                                }
                            }
                            Text(evidence, color = if (stage.hasRecordedActivity) colors.primary else colors.mutedInk,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.testTag("rabbit-milestone:${wave.id}:${stage.key}"))
                        }
                    }
                }
            }
        } else {
            if (wave.events.isEmpty()) Text("No dated activity records yet.", color = colors.mutedInk)
            wave.events.sortedWith(compareByDescending<RabbitWaveEventView> { it.epochDay }.thenBy { it.id }).forEach { event ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("rabbit-event:${event.id}")) {
                    Text("${event.kind} · ${dayLabel(event.epochDay)}", style = MaterialTheme.typography.titleSmall)
                    Text(event.summary, color = colors.mutedInk)
                }
            }
        }
    }
}

@Composable
fun RabbitCageRotationBoard(records: RabbitRecords, today: LocalDate) {
    var view by rememberSaveable { mutableStateOf("Units") }
    com.farmos.core.design.FarmSectionTabs(listOf("Units","Wave calendar"),view,{view=it},tagPrefix="rabbit-housing-view:")
    if(view=="Units") RabbitUnitBoard(records,today) else RabbitWaveCalendar(records,today)
}

@Composable
private fun RabbitWaveCalendar(records: RabbitRecords, today: LocalDate) {
    val colors = AnimalFarmTheme.colors
    val fontScale = LocalDensity.current.fontScale
    var cageId by rememberSaveable { mutableStateOf<String?>(null) }
    var waveId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val cages = records.cages.sortedWith(compareBy<RabbitCageView> { it.code }.thenBy { it.id })
    val selected = cages.firstOrNull { it.id == cageId } ?: cages.firstOrNull()
    FarmOperationalSection("Cage rotation", "Planned wave calendar") {
        if (selected == null) Text("Add a cage to begin its rotation plan.", color = colors.mutedInk)
        else {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = if (maxWidth < 300.dp || fontScale >= 1.5f) 1 else 2
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (if (showAll) cages else cages.take(6)).chunked(columns).forEach { group ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            group.forEach { cage ->
                                val chosen = selected.id == cage.id
                                Surface(
                                    Modifier.weight(1f).heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                                        .testTag("rabbit-cage-tile:${cage.id}")
                                        .selectable(chosen, role = Role.RadioButton, onClick = { cageId = cage.id; waveId = null }),
                                    shape = RoundedCornerShape(AnimalFarmHomeMetrics.actionRadius),
                                    color = if (chosen) colors.softSurface else colors.surface,
                                    border = BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) colors.primary else colors.divider),
                                ) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            CageGlyph(Modifier.size(28.dp))
                                            Text(cage.code, style = MaterialTheme.typography.titleSmall)
                                        }
                                        Text("Capacity: ${cage.doeCapacity} does", color = colors.mutedInk, style = MaterialTheme.typography.bodySmall)
                                        val planCount = cage.waveIds.distinct().size
                                        Text("$planCount ${if (planCount == 1) "wave plan" else "wave plans"}", color = colors.primary, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            repeat(columns - group.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            if (cages.size > 6) FarmActionButton({ showAll = !showAll }) { Text(if (showAll) "Show fewer cages" else "Show all ${cages.size} cages") }
            val waves = rabbitCageWaves(selected, records.waves)
            Text("Cage ${selected.code}", style = MaterialTheme.typography.titleMedium)
            val missing = selected.waveIds.distinct().size - waves.size
            if (missing > 0) Text("$missing linked wave plans are unavailable.", color = colors.mutedInk)
            if (waves.isEmpty()) Text("No wave plans linked to this cage.", color = colors.mutedInk)
            else {
                val dates = waves.flatMap { rabbitWorkflowMilestones(it).map { stage -> stage.scheduledEpochDay } }
                val start = dates.min()
                val end = dates.max()
                if (fontScale >= 1.5f) {
                    Text(dayLabel(start) + " – " + dayLabel(end), color = colors.mutedInk, style = MaterialTheme.typography.labelSmall)
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(dayLabel(start), Modifier.weight(1f), color = colors.mutedInk, style = MaterialTheme.typography.labelSmall)
                        Text(dayLabel(end), Modifier.weight(1f), color = colors.mutedInk, style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }
                }
                val selectedWave = waves.firstOrNull { it.id == waveId } ?: rabbitRelevantWave(waves, today)!!
                waves.forEach { wave ->
                    val chosen = selectedWave.id == wave.id
                    Surface(
                        Modifier.fillMaxWidth().testTag("rabbit-wave-plan:${wave.id}")
                            .selectable(chosen, role = Role.RadioButton, onClick = { waveId = wave.id }),
                        shape = RoundedCornerShape(AnimalFarmHomeMetrics.actionRadius),
                        color = if (chosen) colors.softSurface else colors.surface,
                        border = BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) colors.primary else colors.divider),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${wave.doeCount} does · mating ${dayLabel(wave.matingEpochDay)}", style = MaterialTheme.typography.labelLarge)
                            ScheduleBar(wave, start, end, today)
                            Text("Nest: ${dayLabel(wave.nestInEpochDay)} – ${dayLabel(wave.nestOutEpochDay)}", color = colors.mutedInk, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Text(if (today.toEpochDay() in start..end) "Shaded band: planned nest window · vertical line: today" else "Shaded band: planned nest window", color = colors.mutedInk, style = MaterialTheme.typography.labelSmall)
                RabbitWaveTimeline(selectedWave, today)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RabbitNestCycle(boxes: List<RabbitNestBoxView>) {
    val colors = AnimalFarmTheme.colors
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val counts = rabbitNestStageCounts(boxes)
    val known = nestStages.flatMap { it.third }.toSet()
    val unknown = boxes.filter { it.status !in known }
    val stage = nestStages.firstOrNull { it.first == selected }
    val shown = (if (selected == "other") unknown else if (stage != null) boxes.filter { it.status in stage.third } else boxes)
        .sortedWith(compareBy<RabbitNestBoxView> { it.code }.thenBy { it.id })
    FarmOperationalSection("Nest-box cycle", "Current recorded states") {
        NestCycleGraphic()
        Text("Dashed paths: ready → in cage; assigned → to clean.", color = colors.mutedInk, style = MaterialTheme.typography.labelSmall)
        if (boxes.isEmpty()) Text("No nest boxes yet.", color = colors.mutedInk)
        else {
            // The connector includes the native skip transitions: ready → in cage, assigned → dirty.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FarmFilterChip("All boxes (${boxes.size})", selected == null, { selected = null; showAll = false })
                nestStages.forEach { (key, label, _) ->
                    FarmFilterChip("$label (${counts.getValue(key)})", selected == key, { selected = if (selected == key) null else key; showAll = false },
                        Modifier.testTag("rabbit-nest-stage:$key"))
                }
                if (unknown.isNotEmpty()) FarmFilterChip("Other states (${unknown.size})", selected == "other", { selected = "other"; showAll = false })
            }
            if (shown.isEmpty()) Text("No boxes in this state.", color = colors.mutedInk)
            (if (showAll) shown else shown.take(8)).forEach { box ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("rabbit-nest-box:${box.id}"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(box.code, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(humanStatus(box.status), color = colors.mutedInk, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (shown.size > 8) FarmActionButton({ showAll = !showAll }) { Text(if (showAll) "Show fewer boxes" else "Show all ${shown.size} boxes") }
        }
    }
}

@Composable
private fun NestCycleGraphic() {
    val colors = AnimalFarmTheme.colors
    // A compact exact graph has textual equivalents in the selectable state controls below.
    val largeText = LocalDensity.current.fontScale >= 1.5f
    Box(Modifier.fillMaxWidth()) {
    Canvas(Modifier.fillMaxWidth().height(if (largeText) 280.dp else 90.dp).semantics {
        contentDescription = "Nest cycle: ready to assigned or in cage; assigned to in cage or dirty; in cage to dirty; dirty to ready."
    }) {
        val y = size.height / 2
        val xs = (0..3).map { size.width * (it + .5f) / 4f }
        val radius = 9.dp.toPx()
        fun arrow(from: Offset, to: Offset, dashed: Boolean = false) {
            drawLine(colors.primary, from, to, 1.5.dp.toPx(), pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) else null)
            val angle = kotlin.math.atan2((to.y-from.y).toDouble(), (to.x-from.x).toDouble())
            val back = 5.dp.toPx()
            val path = Path().apply {
                moveTo(to.x, to.y)
                lineTo(to.x - back * kotlin.math.cos(angle - .55).toFloat(), to.y - back * kotlin.math.sin(angle - .55).toFloat())
                lineTo(to.x - back * kotlin.math.cos(angle + .55).toFloat(), to.y - back * kotlin.math.sin(angle + .55).toFloat())
                close()
            }
            drawPath(path, colors.primary)
        }
        if (largeText) {
            val x = 20.dp.toPx()
            val ys = (0..3).map { size.height * (it + .5f) / 4f }
            for (i in 0..2) arrow(Offset(x, ys[i] + radius), Offset(x, ys[i + 1] - radius - 3.dp.toPx()))
            val returnX = 44.dp.toPx()
            drawLine(colors.primary, Offset(x + radius, ys[3]), Offset(returnX, ys[3]), 1.5.dp.toPx())
            drawLine(colors.primary, Offset(returnX, ys[3]), Offset(returnX, ys[0]), 1.5.dp.toPx())
            arrow(Offset(returnX, ys[0]), Offset(x + radius + 3.dp.toPx(), ys[0]))
            listOf(0 to 2, 1 to 3).forEachIndexed { index, (from, to) ->
                val branchX = (4 + index * 4).dp.toPx()
                val skip = Path().apply {
                    moveTo(x - radius, ys[from])
                    cubicTo(branchX, ys[from], branchX, ys[to], x - radius - 3.dp.toPx(), ys[to])
                }
                drawPath(skip, colors.mutedInk, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
                arrow(Offset(branchX, ys[to]), Offset(x - radius - 3.dp.toPx(), ys[to]))
            }
            ys.forEach { stageY ->
                drawCircle(colors.softSurface, radius, Offset(x, stageY))
                drawCircle(colors.primary, radius, Offset(x, stageY), style = Stroke(2.dp.toPx()))
            }
            return@Canvas
        }
        for (i in 0..2) arrow(Offset(xs[i]+radius,y),Offset(xs[i+1]-radius-3.dp.toPx(),y))
        val loopY = 8.dp.toPx()
        drawLine(colors.primary,Offset(xs[3],y-radius),Offset(xs[3],loopY),1.5.dp.toPx())
        drawLine(colors.primary,Offset(xs[3],loopY),Offset(xs[0],loopY),1.5.dp.toPx())
        arrow(Offset(xs[0],loopY),Offset(xs[0],y-radius-3.dp.toPx()))
        // Skip paths correspond to RabbitNestBoxCycle.nextStatuses, not an invented sequence.
        listOf(0 to 2,1 to 3).forEach { (from,to) ->
            val curve = Path().apply {
                moveTo(xs[from],y+radius)
                cubicTo(xs[from],size.height-4.dp.toPx(),xs[to],size.height-4.dp.toPx(),xs[to],y+radius+3.dp.toPx())
            }
            drawPath(curve,colors.mutedInk,style=Stroke(1.5.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(),4.dp.toPx()))))
            arrow(Offset(xs[to],y+radius+9.dp.toPx()),Offset(xs[to],y+radius+3.dp.toPx()))
        }
        xs.forEach { x -> drawCircle(colors.softSurface,radius,Offset(x,y));drawCircle(colors.primary,radius,Offset(x,y),style=Stroke(2.dp.toPx())) }
    }
    if (largeText) {
        Column(Modifier.fillMaxWidth().padding(start = 64.dp)) {
            nestStages.forEach { (_, label, _) ->
                Box(Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.CenterStart) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(top = 92.dp)) {
            nestStages.forEach { (_, label, _) -> Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
    }
}

@Composable
private fun WorkflowCount(count: Int, label: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(count.toString(), style = MaterialTheme.typography.headlineSmall)
        Text(label, color = AnimalFarmTheme.colors.mutedInk, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CageGlyph(modifier: Modifier) {
    val color = AnimalFarmTheme.colors.primary
    Canvas(modifier) {
        val inset = 2.dp.toPx()
        drawRoundRect(color, Offset(inset,inset),Size(size.width-inset*2,size.height-inset*2),CornerRadius(4.dp.toPx()),style=Stroke(1.5.dp.toPx()))
        for(i in 1..2) drawLine(color,Offset(size.width*i/3,inset),Offset(size.width*i/3,size.height-inset),1.dp.toPx())
        drawLine(color,Offset(inset,size.height/2),Offset(size.width-inset,size.height/2),1.dp.toPx())
    }
}

@Composable
private fun ScheduleBar(wave: RabbitWaveView, start: Long, end: Long, today: LocalDate) {
    val colors = AnimalFarmTheme.colors
    val milestones = rabbitWorkflowMilestones(wave)
    Canvas(Modifier.fillMaxWidth().height(38.dp).semantics {
        contentDescription = milestones.joinToString { it.label + " planned " + dayLabel(it.scheduledEpochDay) }
    }) {
        val pad = 8.dp.toPx()
        val span = (end-start).coerceAtLeast(1L).toDouble()
        fun x(day:Long) = pad+((day-start).toDouble()/span).coerceIn(0.0,1.0).toFloat()*(size.width-pad*2)
        val y=size.height/2
        drawLine(colors.divider,Offset(pad,y),Offset(size.width-pad,y),2.dp.toPx())
        drawLine(colors.primary.copy(alpha=.3f),Offset(x(milestones.first().scheduledEpochDay),y),Offset(x(milestones.last().scheduledEpochDay),y),8.dp.toPx())
        if(wave.nestInEpochDay<=wave.nestOutEpochDay) drawLine(colors.lime,Offset(x(wave.nestInEpochDay),y),Offset(x(wave.nestOutEpochDay),y),10.dp.toPx())
        milestones.forEach { drawCircle(colors.surface,4.dp.toPx(),Offset(x(it.scheduledEpochDay),y));drawCircle(colors.primary,4.dp.toPx(),Offset(x(it.scheduledEpochDay),y),style=Stroke(1.5.dp.toPx())) }
        if(today.toEpochDay() in start..end) drawLine(colors.ink,Offset(x(today.toEpochDay()),2.dp.toPx()),Offset(x(today.toEpochDay()),size.height-2.dp.toPx()),1.5.dp.toPx())
    }
}

private fun recordedWaveLabel(wave: RabbitWaveView): String = when(wave.kindlingState) {
    RabbitKindlingState.AWAITING_KINDLING -> "Awaiting a kindling record"
    RabbitKindlingState.NOT_PREGNANT_RECORDED -> "Not pregnant · recorded outcome"
    RabbitKindlingState.KINDLED_RECORDED -> "Kindling recorded"
    RabbitKindlingState.KINDLED_OUTCOME_WITHOUT_KINDLING_RECORD -> "Kindled outcome · kindling record still needed"
    RabbitKindlingState.OUTCOME_NOT_RECOGNISED -> "Review the recorded outcome"
}
private fun humanStatus(value:String)=value.replace('_',' ').replaceFirstChar { it.uppercase() }
private fun dayLabel(day:Long)=LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
