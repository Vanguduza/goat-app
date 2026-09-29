package com.farmos.feature.ops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.FarmOperationalSection
import java.math.BigDecimal
import java.time.LocalDate

/** One recorded weight exactly as stored; [unit] is the stored unit ("g" for recorded weights). */
data class WeightView(val id: String, val epochDay: Long, val value: Long, val unit: String)

private fun WeightView.label(): String =
    if (unit == "g") BigDecimal.valueOf(value, 3).stripTrailingZeros().toPlainString() + " kg" else "$value $unit"

private fun changeLabel(previous: WeightView, current: WeightView): String? {
    if (previous.unit != "g" || current.unit != "g") return null
    val grams = current.value - previous.value
    val sign = if (grams > 0) "+" else ""
    val days = current.epochDay - previous.epochDay
    return "$sign${BigDecimal.valueOf(grams, 3).stripTrailingZeros().toPlainString()} kg over $days days"
}

/**
 * Recorded weights for one animal, oldest first as measured. Changes are simple differences
 * between consecutive recorded weights; no growth rate, target or projection is derived.
 */
@Composable
internal fun WeightHistoryContent(weights: List<WeightView>, emptyText: String) {
    if (weights.isEmpty()) {
        AnimalFarmEmptyState(emptyText)
        return
    }
    FarmOperationalSection("Summary") {
        WeightRow("Weights recorded", weights.size.toString(), "weight-count")
        if (weights.size > 1) WeightRow("First recorded", "${weights.first().label()} · ${LocalDate.ofEpochDay(weights.first().epochDay)}", "weight-first")
        WeightRow("Latest recorded", "${weights.last().label()} · ${LocalDate.ofEpochDay(weights.last().epochDay)}", "weight-latest")
        if (weights.size > 1) changeLabel(weights.first(), weights.last())?.let { WeightRow("Change, first to latest", it, "weight-change-total") }
    }
    FarmOperationalSection("Weights · ${weights.size}") {
        weights.forEachIndexed { index, weight ->
            if (index > 0) HorizontalDivider()
            val change = if (index > 0) changeLabel(weights[index - 1], weight)?.let { " · $it" } ?: "" else ""
            WeightRow(LocalDate.ofEpochDay(weight.epochDay).toString(), weight.label() + change, "weight:${weight.id}")
        }
    }
}

@Composable
private fun WeightRow(primary: String, secondary: String, tag: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(tag), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(primary, color = AnimalFarmTheme.colors.mutedInk)
        Text(secondary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
