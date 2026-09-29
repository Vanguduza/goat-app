package com.farmos.feature.goat

import com.farmos.domain.goat.GoatGrowth
import com.farmos.domain.goat.GoatSnapshot
import com.farmos.domain.goat.WeightSample
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Record families shown on the goat timeline (FOS-GOAT-009). */
internal enum class GoatRecordKind(val label: String) {
    WEIGHT("Weight"),
    MILK("Milk"),
    SCC("SCC"),
    BCS("BCS"),
    FAMACHA("FAMACHA"),
    KIDDING("Kidding"),
}

internal data class GoatTimelineEntry(
    val recordId: String,
    val kind: GoatRecordKind,
    val day: LocalDate,
    val summary: String,
)

/** Deterministic breakdown of the stored average-daily-gain rule (FOS-GOAT-015). */
internal data class GoatAdgBreakdown(
    val first: WeightSample,
    val last: WeightSample,
    val sampleCount: Int,
    val gainGrams: Long,
    val elapsedDays: Long,
    val averageDailyGainGrams: Long,
)

/**
 * Read-only projections over the local [GoatSnapshot]. These never write, and they never
 * substitute zero for a missing measurement: absent history stays absent.
 */
internal object GoatRecordHistory {
    fun weightDay(epochMillis: Long): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate()

    /** Oldest first, so growth reads left to right. */
    fun weightsAscending(goat: GoatSnapshot): List<WeightSample> =
        goat.weightHistory.sortedWith(compareBy<WeightSample> { it.measuredAtEpochMillis }.thenBy { it.measurementId })

    fun timeline(goat: GoatSnapshot): List<GoatTimelineEntry> {
        val entries = buildList {
            goat.weightHistory.forEach {
                add(GoatTimelineEntry(it.measurementId, GoatRecordKind.WEIGHT, weightDay(it.measuredAtEpochMillis), "${formatKg(it.weightGrams)} kg"))
            }
            goat.milkHistory.forEach {
                add(GoatTimelineEntry(it.milkId, GoatRecordKind.MILK, LocalDate.ofEpochDay(it.occurredEpochDay), "${formatLitres(it.litresMilli)} L"))
            }
            goat.sccHistory.forEach {
                add(GoatTimelineEntry(it.recordId, GoatRecordKind.SCC, LocalDate.ofEpochDay(it.occurredEpochDay), sccSummary(it.cellsPerMl, it.dimDays)))
            }
            goat.bcsHistory.forEach {
                add(GoatTimelineEntry(it.scoreId, GoatRecordKind.BCS, LocalDate.ofEpochDay(it.occurredEpochDay), "Score ${formatBcs(it.scoreTenths)}"))
            }
            goat.famachaHistory.forEach {
                add(GoatTimelineEntry(it.scoreId, GoatRecordKind.FAMACHA, LocalDate.ofEpochDay(it.occurredEpochDay), "Score ${it.score}"))
            }
            goat.kiddingHistory.forEach {
                add(
                    GoatTimelineEntry(
                        it.kiddingId,
                        GoatRecordKind.KIDDING,
                        LocalDate.ofEpochDay(it.occurredEpochDay),
                        "${it.bornCount} born · ${it.liveCount} live · ${it.deadCount} dead",
                    ),
                )
            }
        }
        return entries.sortedWith(
            compareByDescending<GoatTimelineEntry> { it.day }
                .thenBy { it.kind.ordinal }
                .thenBy { it.recordId },
        )
    }

    fun kindsPresent(entries: List<GoatTimelineEntry>): List<GoatRecordKind> =
        GoatRecordKind.entries.filter { kind -> entries.any { it.kind == kind } }

    fun adg(goat: GoatSnapshot): GoatAdgBreakdown? {
        val ordered = weightsAscending(goat)
        val adg = GoatGrowth.averageDailyGainGrams(ordered) ?: return null
        val first = ordered.first()
        val last = ordered.last()
        return GoatAdgBreakdown(
            first = first,
            last = last,
            sampleCount = ordered.size,
            gainGrams = last.weightGrams - first.weightGrams,
            elapsedDays = weightDay(last.measuredAtEpochMillis).toEpochDay() - weightDay(first.measuredAtEpochMillis).toEpochDay(),
            averageDailyGainGrams = adg,
        )
    }

    /** Change from the previous weight, or null for the first recorded weight. */
    fun weightChanges(ordered: List<WeightSample>): List<Long?> =
        ordered.mapIndexed { index, sample -> if (index == 0) null else sample.weightGrams - ordered[index - 1].weightGrams }
}

internal fun formatLitres(litresMilli: Long): String = "%.2f".format(litresMilli / 1_000.0)

internal fun formatBcs(scoreTenths: Int): String = "${scoreTenths / 10}.${scoreTenths % 10}"

internal fun formatSignedKg(grams: Long): String = (if (grams > 0) "+" else "") + formatKg(grams)

internal fun sccSummary(cellsPerMl: Int, dimDays: Int?): String =
    "%,d cells/mL".format(cellsPerMl) + (dimDays?.let { " · DIM $it" } ?: "")
