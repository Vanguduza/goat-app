package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.IssueFeed
import com.farmos.feature.ops.SimpleCaptureScreen
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

/** Dedicated feed orchestration. Inventory is read for selection/context; feed owns the feed issue mutation. */
@Composable
fun FeedModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by androidx.compose.runtime.remember { mutableStateOf(false) }
    var error by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    var feedRows by androidx.compose.runtime.remember { mutableStateOf(emptyList<String>()) }
    var inventoryRows by androidx.compose.runtime.remember { mutableStateOf(emptyList<String>()) }

    suspend fun refresh() {
        feedRows = ops.recentFeed().map { "${it.itemId} · ${it.quantityMilli} milli" }
        inventoryRows = ops.items().map { "${it.id} ${it.sku} · ${it.name} · ${it.quantityMilli} ${it.unit}" }
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
    SimpleCaptureScreen(
        screenId = "FOS-FEED-001",
        title = "Feed",
        help = "Issuing feed deducts inventory in milli-units. Ration percentages stay advisory drafts.",
        empty = "No feed issues on this device.",
        rows = feedRows + inventoryRows,
        busy = busy,
        error = error,
        fields = listOf("Item id" to itemId, "Quantity" to qty, "Date" to day),
        actionLabel = "Issue feed",
        onSubmit = {
            run {
                val amount = qty.value.replace(',', '.').toDoubleOrNull() ?: error("Enter a quantity")
                ops.issueFeed(
                    IssueFeed(UUID.randomUUID().toString(), itemId.value, null, (amount * 1000.0).toLong(), LocalDate.parse(day.value).toEpochDay()),
                    newContext(),
                )
            }
        },
        onBack = onBack,
    )
}
