package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.farmos.core.database.LabourEntryEntity
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import java.time.LocalDate

/** Internal pages of the labour module. */
internal enum class LabourPage {
    HOME,
    ATTENDANCE,
    COST,
    REPORT,
}

/** Dedicated labour/work-log orchestration boundary. */



/**
 * FOS-LABOUR-006 — Attendance: days worked per worker, derived from the labour entries on this
 * device. A worker present on a day is a worker with entries that day; minutes are whole figures.
 */
@Composable
internal fun LabourAttendancePage(
    entries: List<LabourEntryEntity>,
    onBack: () -> Unit,
) {
    val byDay = entries.groupBy { it.occurredEpochDay }.toSortedMap(compareByDescending { it })
    FarmOperationalPage(
        screenId = "FOS-LABOUR-006",
        title = "Attendance",
        subtitle = "Days worked per worker, from the labour entries on this device.",
        onBack = onBack,
    ) {
        if (entries.isEmpty()) {
            androidx.compose.material3.Text("No labour entries on this device.")
            return@FarmOperationalPage
        }
        androidx.compose.material3.Text("From the ${entries.size} most recent entries on this device.")
        byDay.forEach { (day, dayEntries) ->
            FarmOperationalSection(LocalDate.ofEpochDay(day).toString()) {
                dayEntries.groupBy { it.workerName }.toSortedMap().forEach { (name, workerEntries) ->
                    val minutes = workerEntries.sumOf { it.minutes }
                    androidx.compose.material3.Text("$name · $minutes min · ${workerEntries.size} entries")
                }
            }
        }
    }
}

/**
 * FOS-LABOUR-007 — Labour Cost.
 *
 * GENUINE GAP — not implemented: labour entries carry minutes but no wage rate, and no worker
 * rate store exists in the schema. Cost cannot be computed from minutes alone.
 * Required domain piece: a wage-rate store (per worker or per task code) with a governed
 * record/revise command, joined to labour entries at report time.
 *
 * The surface below shows what IS recorded (hours by worker and task) with the gap stated
 * plainly, so the screen is honest rather than a fabricated cost.
 */
@Composable
internal fun LabourCostPage(
    entries: List<LabourEntryEntity>,
    onBack: () -> Unit,
) {
    FarmOperationalPage(
        screenId = "FOS-LABOUR-007",
        title = "Labour cost",
        subtitle = "Cost cannot be computed yet: no wage rates are recorded.",
        onBack = onBack,
    ) {
        FarmOperationalSection(
            title = "Hours on record",
            description = "From the labour entries on this device. Rates are not recorded, so these are hours only, not cost.",
        ) {
            if (entries.isEmpty()) {
                androidx.compose.material3.Text("No labour entries on this device.")
            } else {
                entries.groupBy { it.workerName }.toSortedMap().forEach { (name, workerEntries) ->
                    val minutes = workerEntries.sumOf { it.minutes }
                    val hours = minutes / 60.0
                    androidx.compose.material3.Text("$name · ${"%.1f".format(hours)} h · ${workerEntries.size} entries · cost: not recorded")
                }
            }
        }
        androidx.compose.material3.Text("Labour cost needs wage rates: there is no rate table or governed rate command in this build.")
    }
}

/**
 * FOS-LABOUR-009 — Labour Report: minutes by worker, by task code and by day, from the labour
 * entries on this device.
 */
@Composable
internal fun LabourReportPage(
    entries: List<LabourEntryEntity>,
    onBack: () -> Unit,
) {
    FarmOperationalPage(
        screenId = "FOS-LABOUR-009",
        title = "Labour report",
        subtitle = "Work recorded on this device, grouped by worker, task and day.",
        onBack = onBack,
    ) {
        if (entries.isEmpty()) {
            androidx.compose.material3.Text("No labour entries on this device.")
            return@FarmOperationalPage
        }
        val totalMinutes = entries.sumOf { it.minutes }
        androidx.compose.material3.Text("${entries.size} entries · ${"%.1f".format(totalMinutes / 60.0)} h total.")
        FarmOperationalSection("By worker") {
            entries.groupBy { it.workerName }.toSortedMap().forEach { (name, workerEntries) ->
                androidx.compose.material3.Text("$name · ${workerEntries.sumOf { it.minutes }} min · ${workerEntries.size} entries")
            }
        }
        FarmOperationalSection("By task") {
            entries.groupBy { it.taskCode }.toSortedMap().forEach { (code, taskEntries) ->
                androidx.compose.material3.Text("$code · ${taskEntries.sumOf { it.minutes }} min · ${taskEntries.size} entries")
            }
        }
        FarmOperationalSection("By day") {
            entries.groupBy { it.occurredEpochDay }.toSortedMap(compareByDescending { it }).forEach { (day, dayEntries) ->
                androidx.compose.material3.Text("${LocalDate.ofEpochDay(day)} · ${dayEntries.sumOf { it.minutes }} min · ${dayEntries.size} entries")
            }
        }
    }
}
