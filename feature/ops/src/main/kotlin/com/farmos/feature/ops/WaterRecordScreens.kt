package com.farmos.feature.ops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmVisualClass
import java.math.BigDecimal
import java.time.LocalDate

data class WaterRecordView(val id: String, val source: String, val litresMilli: Long, val epochDay: Long)

/** Exhaustive recorded total for one water source across every recorded row. */
data class WaterSourceTotalView(val source: String, val litresMilli: Long, val recordCount: Int, val firstEpochDay: Long, val latestEpochDay: Long)

/**
 * Farm-scoped water records. [records] may be only the latest rows; [recordCount] and [sources] are
 * exhaustive aggregates. Totals are sums of recorded litres only; no consumption rate is derived.
 */
data class WaterRecords(
    val records: List<WaterRecordView> = emptyList(),
    val recordCount: Int? = null,
    val sources: List<WaterSourceTotalView> = emptyList(),
)

internal fun waterLitres(milli: Long): String = BigDecimal.valueOf(milli, 3).stripTrailingZeros().toPlainString() + " L"

@Composable
private fun WaterRow(primary: String, secondary: String, tag: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(tag), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(primary, color = AnimalFarmTheme.colors.mutedInk)
        Text(secondary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Water record navigation. [home] renders the water home (FOS-WATER-001) and places the supplied
 * record action; the action opens the read-only history page whose back returns home.
 */
@Composable
fun WaterRecordNavigator(records: WaterRecords, home: @Composable (recordActions: @Composable () -> Unit) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    if (open) {
        WaterHistoryScreen(records) { open = false }
    } else {
        home {
            FarmOperationalSection("Records") {
                TextButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text("Open Water history") }
            }
        }
    }
}

/** FOS-WATER-009 — exhaustive per-source totals and recorded water, newest first. */
@Composable
internal fun WaterHistoryScreen(records: WaterRecords, onBack: () -> Unit) {
    FarmOperationalPage("FOS-WATER-009", "Water history", "Recorded water on this device, newest first. Sums of recorded litres only.", FarmVisualClass.I3, onBack) {
        if (records.records.isEmpty()) {
            AnimalFarmEmptyState("No water recorded on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection("By source, all recorded rows") {
            records.sources.forEach {
                WaterRow(
                    it.source,
                    "${waterLitres(it.litresMilli)} · ${it.recordCount} " + (if (it.recordCount == 1) "record" else "records") +
                        " · ${LocalDate.ofEpochDay(it.firstEpochDay)} to ${LocalDate.ofEpochDay(it.latestEpochDay)}",
                    "water-source:${it.source}",
                )
            }
        }
        FarmOperationalSection(recordListTitle("Records", records.records.size, records.recordCount)) {
            records.records.forEachIndexed { index, record ->
                if (index > 0) HorizontalDivider()
                WaterRow("${LocalDate.ofEpochDay(record.epochDay)} · ${record.source}", waterLitres(record.litresMilli), "water-record:${record.id}")
            }
        }
    }
}
