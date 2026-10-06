package com.farmos.feature.ops

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
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmVisualClass
import java.time.LocalDate

/** One recorded feed issue. [quantity] is the exact recorded quantity with the item's unit. */
data class FeedIssueView(
    val id: String,
    val itemId: String,
    val itemLabel: String,
    val quantity: String,
    val groupLabel: String,
    val epochDay: Long,
)

/**
 * Exhaustive issued total for one inventory item across every recorded feed issue. [totalIssued]
 * sums one item only, so one unit; [onHand] is the item's current recorded stock, or null when the
 * item is not on this device.
 */
data class FeedItemView(
    val itemId: String,
    val itemLabel: String,
    val totalIssued: String,
    val issueCount: Int,
    val firstEpochDay: Long,
    val latestEpochDay: Long,
    val onHand: String?,
)

/**
 * Farm-scoped feed records. [issues] may be only the latest rows; [issueCount] and [items] are
 * exhaustive aggregates. No ration, cost or consumption rate is derived here. Nothing here writes.
 */
data class FeedRecords(
    val issues: List<FeedIssueView> = emptyList(),
    val issueCount: Int? = null,
    val items: List<FeedItemView> = emptyList(),
)

enum class FeedRecordPage(val label: String) {
    ITEM("Feed item"),
    ISSUES("Feed issues"),
}

private fun issueCountLabel(count: Int) = "$count " + if (count == 1) "issue" else "issues"

@Composable
private fun FeedRow(primary: String, secondary: String, tag: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(tag), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(primary, color = AnimalFarmTheme.colors.mutedInk)
        Text(secondary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Feed record navigation. [home] renders the feed home (FOS-FEED-001) and places the supplied
 * record actions; each action opens a read-only record page whose back returns home.
 */
@Composable
fun FeedRecordNavigator(records: FeedRecords, home: @Composable (recordActions: @Composable () -> Unit) -> Unit) {
    var page by rememberSaveable { mutableStateOf<FeedRecordPage?>(null) }
    val back = { page = null }
    when (page) {
        null -> home {
            FarmOperationalSection("Records") {
                FeedRecordPage.entries.forEach { target ->
                    TextButton(onClick = { page = target }, modifier = Modifier.fillMaxWidth()) { Text("Open ${target.label}") }
                }
            }
        }
        FeedRecordPage.ITEM -> FeedItemScreen(records, back)
        FeedRecordPage.ISSUES -> FeedIssuesScreen(records, back)
    }
}

/** FOS-FEED-003 — one feed item: exhaustive issued total, current stock and its latest issues. */
@Composable
internal fun FeedItemScreen(records: FeedRecords, onBack: () -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf(records.items.firstOrNull()?.itemId) }
    FarmOperationalPage("FOS-FEED-003", "Feed item", "Recorded feed issued from one inventory item, summed across every issue.", FarmVisualClass.I3, onBack) {
        if (records.items.isEmpty()) {
            AnimalFarmEmptyState("No feed issued on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection("Items issued as feed") {
            records.items.forEach { item ->
                val selected = item.itemId == selectedId
                TextButton(
                    onClick = { selectedId = item.itemId },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                        .semantics { this.selected = selected }
                        .testTag("feed-option:${item.itemId}"),
                ) { Text(item.itemLabel, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
            }
        }
        val item = records.items.firstOrNull { it.itemId == selectedId } ?: return@FarmOperationalPage
        FarmOperationalSection(item.itemLabel) {
            FeedRow("Issued, all recorded issues", item.totalIssued, "feed-item-total")
            FeedRow("Issues recorded", item.issueCount.toString(), "feed-item-count")
            FeedRow("Recorded between", "${LocalDate.ofEpochDay(item.firstEpochDay)} and ${LocalDate.ofEpochDay(item.latestEpochDay)}", "feed-item-range")
            FeedRow("On hand now", item.onHand ?: "Item not on this device", "feed-item-on-hand")
        }
        val issues = records.issues.filter { it.itemId == item.itemId }
        FarmOperationalSection(recordListTitle("Issues", issues.size, item.issueCount)) {
            issues.forEach {
                FeedRow("${LocalDate.ofEpochDay(it.epochDay)} · ${it.groupLabel}", it.quantity, "feed-item-issue:${it.id}")
            }
        }
    }
}

/** FOS-FEED-006 — recorded feed issues, newest first, with exhaustive per-item totals. */
@Composable
internal fun FeedIssuesScreen(records: FeedRecords, onBack: () -> Unit) {
    FarmOperationalPage("FOS-FEED-006", "Feed issues", "Recorded feed issues on this device, newest first. Sums of recorded quantities only.", FarmVisualClass.I3, onBack) {
        if (records.issues.isEmpty()) {
            AnimalFarmEmptyState("No feed issued on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection("By item, all recorded issues") {
            records.items.forEach {
                FeedRow(it.itemLabel, "${it.totalIssued} · ${issueCountLabel(it.issueCount)}", "feed-item:${it.itemId}")
            }
        }
        FarmOperationalSection(recordListTitle("Issues", records.issues.size, records.issueCount)) {
            records.issues.forEachIndexed { index, issue ->
                if (index > 0) HorizontalDivider()
                FeedRow("${LocalDate.ofEpochDay(issue.epochDay)} · ${issue.groupLabel}", "${issue.itemLabel} · ${issue.quantity}", "feed-issue:${issue.id}")
            }
        }
    }
}
