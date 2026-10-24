package com.farmos.feature.ops

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.farmos.core.design.*

/** FOS-FEED-002, P05. Stock of items recorded as feed, not a fabricated feed classification. */
@Composable
internal fun FeedInventoryScreen(records: FeedRecords, onOpen: (String) -> Unit, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable(query) { mutableStateOf(50) }
    FarmOperationalPage("FOS-FEED-002", "Feed inventory", "", FarmVisualClass.I3, onBack) {
        Text("Items linked by recorded feed issues. Quantities retain each item's recorded unit.")
        if (records.issueCount == null && records.items.isEmpty()) {
            FarmLoadingSkeleton("Reading recorded feed inventory")
            return@FarmOperationalPage
        }
        if (records.items.isEmpty()) {
            AnimalFarmEmptyState("No items have been issued as feed yet. Unissued stock remains in Inventory.")
            return@FarmOperationalPage
        }
        FarmOutlinedTextField(query, { query = it }, label = { Text("Find feed item") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("feed-inventory-query"))
        val found = records.items.filter { it.itemLabel.contains(query.trim(), ignoreCase = true) || it.itemId == query.trim() }
        val visible = found.take(limit)
        FarmMetricStrip(listOf(FarmMetricValue("Recorded feed items", records.items.size.toString()), FarmMetricValue("Matches", found.size.toString())))
        visible.forEach { item ->
            FarmToolRow(FarmToolAction(item.itemLabel, { onOpen(item.itemId) },
                detail = "On hand: ${item.onHand ?: "item not on this device"} · ${item.issueCount} recorded issue(s)",
                tag = "feed-inventory:${item.itemId}"))
        }
        if (found.isEmpty()) FarmSearchEmptyState("No matching feed items", "Search the recorded item name or its exact identifier.")
        if (found.size > limit) FarmActionButton({ limit += 50 }, modifier = Modifier.fillMaxWidth()) {
            Text("Show more items · ${visible.size} of ${found.size}")
        }
    }
}
