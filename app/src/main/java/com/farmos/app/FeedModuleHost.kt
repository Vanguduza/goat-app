package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.farmos.core.design.FarmEntitySelector
import com.farmos.core.design.FarmSelectionAtoms
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.IssueFeed
import com.farmos.feature.ops.FeedRecordNavigator
import com.farmos.feature.ops.FeedRecords
import com.farmos.feature.ops.SimpleCaptureScreen
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * Dedicated feed orchestration. Inventory is read for selection/context; feed owns the feed issue mutation.
 *
 * FOS-FEED-002 — feed inventory: the home surface lists inventory items with on-hand stock (root tag FOS-FEED-001).
 */
@Composable
fun FeedModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadRecords: suspend () -> FeedRecords = { FeedRecords() },
) {
    val scope = rememberCoroutineScope()
    var busy by androidx.compose.runtime.remember { mutableStateOf(false) }
    var error by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    var feedRows by androidx.compose.runtime.remember { mutableStateOf(emptyList<String>()) }
    var inventoryRows by androidx.compose.runtime.remember { mutableStateOf(emptyList<String>()) }
    var records by androidx.compose.runtime.remember(farmId) { mutableStateOf(FeedRecords()) }
    var itemOptions by androidx.compose.runtime.remember(farmId) { mutableStateOf(emptyList<FarmSelectorOption>()) }

    suspend fun refresh() {
        feedRows = ops.recentFeed().map { "${it.itemId} · ${it.quantityMilli} milli" }
        val items = ops.items()
        inventoryRows = items.map { "${it.id} ${it.sku} · ${it.name} · ${it.quantityMilli} ${it.unit}" }
        itemOptions = items.map { FarmSelectorOption(it.id, "${it.name} · ${it.sku}", "${BigDecimal.valueOf(it.quantityMilli, 3).stripTrailingZeros().toPlainString()} ${it.unit} on hand") }
        records = loadRecords()
    }
    LaunchedEffect(farmId) { runCatching { refresh() } }
    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            runCatching { block(); refresh() }.onSuccess { enqueueSync() }.onFailure { error = it.message }
            busy = false
        }
    }

    val itemId = androidx.compose.runtime.remember { mutableStateOf("") }
    val qty = androidx.compose.runtime.remember { mutableStateOf("") }
    val day = androidx.compose.runtime.remember { mutableStateOf("") }
    FeedRecordNavigator(records) { recordActions -> SimpleCaptureScreen(
        screenId = "FOS-FEED-001",
        title = "Feed",
        help = "Issuing feed deducts inventory in milli-units. Ration percentages stay advisory drafts.",
        empty = "No feed issues on this device.",
        rows = feedRows + inventoryRows,
        busy = busy,
        error = error,
        fields = listOf("Quantity" to qty, "Date" to day),
        actionLabel = "Issue feed",
        onSubmit = {
            run {
                val quantityMilli = qty.value.toScaledLongExact(3, "Quantity")
                ops.issueFeed(
                    IssueFeed(UUID.randomUUID().toString(), itemId.value, null, quantityMilli, LocalDate.parse(day.value).toEpochDay()),
                    newContext(),
                )
            }
        },
        onBack = onBack,
        extra = {
            FarmEntitySelector(FarmSelectionAtoms.INVENTORY_ITEM_SELECTOR, "Feed item", itemOptions, itemId.value.ifBlank { null }, { itemId.value = it }, "No inventory items on this device.", enabled = !busy)
            recordActions()
        },
    ) }
}
