package com.farmos.feature.ops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.AnimalFarmWarningSurface
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmPermissionExplanation
import com.farmos.core.design.FarmVisualClass
import java.math.BigDecimal

/** An inventory item that can be counted. [onHandMilli] is never shown while counting. */
data class StockCountItem(val itemId: String, val label: String, val unit: String, val onHandMilli: Long)

data class StockCountLineView(val itemId: String, val itemLabel: String, val unit: String, val onHandAtCountMilli: Long, val countedMilli: Long, val countedBy: String) {
    val varianceMilli: Long get() = countedMilli - onHandAtCountMilli
}

/** One stock count and its lines (D-021). [status] is a StockCountStatus name. */
data class StockCountView(
    val countId: String,
    val status: String,
    val started: String,
    val submitted: String?,
    val decided: String?,
    val rejectionReason: String?,
    val lines: List<StockCountLineView>,
)

private fun quantity(milli: Long): String = BigDecimal.valueOf(milli, 3).stripTrailingZeros().toPlainString()

private fun signed(milli: Long): String = (if (milli > 0) "+" else "") + quantity(milli)

internal fun parsedMilli(text: String): Long? =
    runCatching { BigDecimal(text.trim().replace(',', '.')).movePointRight(3).longValueExact() }.getOrNull()?.takeIf { it >= 0 }

private fun statusLabel(status: String) = when (status) {
    "COUNTING" -> "Counting"
    "SUBMITTED" -> "Waiting for review"
    "POSTED" -> "Posted"
    "REJECTED" -> "Rejected"
    else -> status
}

/**
 * FOS-INV-016 — stock count (D-021). A worker records what was found for each item; on-hand figures are not
 * shown while counting, so the count is blind. Counting ends with submitting it for management review.
 */
@Composable
fun StockCountScreen(
    counts: List<StockCountView>,
    items: List<StockCountItem>,
    canCount: Boolean,
    busy: Boolean,
    error: String?,
    onStart: () -> Unit,
    onRecord: (countId: String, itemId: String, countedMilli: Long) -> Unit,
    onSubmit: (countId: String) -> Unit,
    onOpenReview: () -> Unit,
    onBack: () -> Unit,
) {
    FarmOperationalPage("FOS-INV-016", "Stock count", "Record what is on the shelf; management reviews the difference.", FarmVisualClass.I4, onBack, backLabel = "Inventory") {
        if (!canCount) {
            FarmPermissionExplanation("Stock counts are recorded by farm staff", "Your role can view counts but not record them.")
        }
        val open = counts.firstOrNull { it.status == "COUNTING" }
        if (open == null) {
            if (canCount) Button(onClick = onStart, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("stock-count-start")) { Text("Start a stock count") }
        } else {
            FarmOperationalSection("Counting · started ${open.started}") {
                if (items.isEmpty()) Text("No inventory items to count.", color = AnimalFarmTheme.colors.mutedInk)
                items.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider()
                    CountItemRow(item, open.lines.firstOrNull { it.itemId == item.itemId }, canCount && !busy) { counted -> onRecord(open.countId, item.itemId, counted) }
                }
                if (canCount) {
                    Button(
                        onClick = { onSubmit(open.countId) },
                        enabled = !busy && open.lines.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().testTag("stock-count-submit"),
                    ) { Text("Submit for review · ${open.lines.size} item(s) counted") }
                }
            }
        }
        val history = counts.filter { it.status != "COUNTING" }
        FarmOperationalSection("Counts · ${history.size}") {
            if (history.isEmpty()) Text("No finished counts.", color = AnimalFarmTheme.colors.mutedInk)
            history.forEach { count ->
                Text(
                    listOfNotNull(statusLabel(count.status), "started ${count.started}", count.decided?.let { "decided $it" }, count.rejectionReason?.let { "reason: $it" }).joinToString(" · "),
                    modifier = Modifier.testTag("stock-count-history:${count.countId}"),
                )
            }
            if (history.any { it.status == "SUBMITTED" }) {
                TextButton(onClick = onOpenReview, modifier = Modifier.testTag("stock-count-open-review")) { Text("Review submitted counts") }
            }
        }
        error?.let { Text(it, color = AnimalFarmTheme.colors.critical) }
    }
}

@Composable
private fun CountItemRow(item: StockCountItem, line: StockCountLineView?, enabled: Boolean, onRecord: (Long) -> Unit) {
    var text by remember(item.itemId, line?.countedMilli) { mutableStateOf(line?.let { quantity(it.countedMilli) } ?: "") }
    val parsed = parsedMilli(text)
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("stock-count-item:${item.itemId}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(item.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        line?.let { Text("Counted ${quantity(it.countedMilli)} ${item.unit} by ${it.countedBy}", color = AnimalFarmTheme.colors.mutedInk) }
        OutlinedTextField(
            text, { text = it },
            label = { Text("Counted (${item.unit})") },
            isError = text.isNotBlank() && parsed == null,
            enabled = enabled, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("stock-count-qty:${item.itemId}"),
        )
        TextButton(onClick = { parsed?.let(onRecord) }, enabled = enabled && parsed != null, modifier = Modifier.testTag("stock-count-record:${item.itemId}")) {
            Text(if (line == null) "Record count" else "Replace count")
        }
    }
}

/**
 * FOS-INV-015 — stock adjustment review (D-021). Management sees each submitted count's variances and
 * either posts them as stock adjustments or rejects the count with a reason. Nothing changes stock until
 * posting; a rejected count is kept.
 */
@Composable
fun StockAdjustmentScreen(
    counts: List<StockCountView>,
    canPost: Boolean,
    busy: Boolean,
    error: String?,
    onPost: (countId: String) -> Unit,
    onReject: (countId: String, reason: String) -> Unit,
    onBack: () -> Unit,
) {
    FarmOperationalPage("FOS-INV-015", "Stock adjustment", "Review counted differences before stock changes.", FarmVisualClass.I4, onBack, backLabel = "Stock count") {
        if (!canPost) {
            FarmPermissionExplanation("Adjustments are posted by farm management", "Only Owner and Manager accounts post or reject a stock count.")
        }
        val submitted = counts.filter { it.status == "SUBMITTED" }
        if (submitted.isEmpty()) {
            AnimalFarmEmptyState("No counts are waiting for review.")
        }
        submitted.forEach { count ->
            var reason by remember(count.countId) { mutableStateOf("") }
            val differences = count.lines.filter { it.varianceMilli != 0L }
            FarmOperationalSection("Count started ${count.started}" + (count.submitted?.let { " · submitted $it" } ?: "")) {
                Text("${count.lines.size} item(s) counted · ${differences.size} with a difference", modifier = Modifier.testTag("stock-review-summary:${count.countId}"))
                count.lines.forEach { line ->
                    Text(
                        "${line.itemLabel}: on hand ${quantity(line.onHandAtCountMilli)} · counted ${quantity(line.countedMilli)} · " +
                            if (line.varianceMilli == 0L) "no difference" else "difference ${signed(line.varianceMilli)} ${line.unit}",
                        modifier = Modifier.testTag("stock-review-line:${count.countId}:${line.itemId}"),
                    )
                }
                if (differences.isNotEmpty()) {
                    AnimalFarmWarningSurface { Text("Posting changes stock by these differences. It cannot be undone except by another count.") }
                }
                if (canPost) {
                    Button(onClick = { onPost(count.countId) }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("stock-review-post:${count.countId}")) {
                        Text(if (differences.isEmpty()) "Post count · no stock change" else "Post ${differences.size} adjustment(s)")
                    }
                    OutlinedTextField(reason, { reason = it }, label = { Text("Reason to reject") }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("stock-review-reason:${count.countId}"))
                    TextButton(onClick = { onReject(count.countId, reason) }, enabled = !busy && reason.isNotBlank(), modifier = Modifier.testTag("stock-review-reject:${count.countId}")) { Text("Reject count") }
                }
            }
        }
        error?.let { Text(it, color = AnimalFarmTheme.colors.critical) }
    }
}
