package com.farmos.feature.ops

import androidx.compose.runtime.Composable
import com.farmos.core.design.FarmBarValue
import com.farmos.core.design.FarmMetricBars
import java.math.BigDecimal
import java.time.LocalDate

/** FOS-CATTLE-020. Chart and Table retain exact individual record values and identities. */
@Composable
fun CattleMilkRecordChart(rows: List<CattleMilkRow>) {
    FarmMetricBars("Milk records", rows.sortedWith(compareBy<CattleMilkRow> { it.epochDay }.thenBy { it.id }).map { row ->
        FarmBarValue(LocalDate.ofEpochDay(row.epochDay).toString(), BigDecimal.valueOf(row.litresMilli, 3).toDouble(), cattleLitres(row.litresMilli), tag = "cattle-milk:" + row.id)
    }, "Litres per recorded entry", caption = "${rows.size} recorded entries. Separate entries on the same date remain separate.")
}

/** FOS-CATTLE-022. No invented diagnostic threshold or implied treatment. */
@Composable
fun CattleSccRecordChart(rows: List<CattleSccRow>) {
    FarmMetricBars("SCC results", rows.sortedWith(compareBy<CattleSccRow> { it.epochDay }.thenBy { it.id }).map { row ->
        FarmBarValue(LocalDate.ofEpochDay(row.epochDay).toString(), row.cellsPerMl.toDouble(), "%,d cells/mL".format(row.cellsPerMl) + (row.dimDays?.let { " · DIM $it" } ?: ""), tag = "cattle-scc:" + row.id)
    }, "Cells per mL", caption = "Recorded results, not a diagnosis.")
}
