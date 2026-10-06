package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.StockCountCommands
import com.farmos.domain.access.Permission
import com.farmos.domain.ops.PostStockCount
import com.farmos.domain.ops.RecordStockCountLine
import com.farmos.domain.ops.RejectStockCount
import com.farmos.domain.ops.StartStockCount
import com.farmos.domain.ops.StockCountAdjustment
import com.farmos.domain.ops.SubmitStockCount
import com.farmos.feature.ops.StockAdjustmentScreen
import com.farmos.feature.ops.StockCountItem
import com.farmos.feature.ops.StockCountLineView
import com.farmos.feature.ops.StockCountScreen
import com.farmos.feature.ops.StockCountView
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.launch

/** Every stock count on this farm with its lines, newest first, and the items that can be counted. */
internal suspend fun loadStockCounts(database: FarmOsDatabase, farmId: String): Pair<List<StockCountView>, List<StockCountItem>> {
    val items = database.inventory().items(farmId)
    val labels = items.associate { it.id to "${it.name} · ${it.sku}" }
    val units = items.associate { it.id to it.unit }
    fun at(millis: Long?) = millis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString() }
    val counts = database.stockCounts().all(farmId).map { count ->
        StockCountView(
            countId = count.id,
            status = count.status,
            started = at(count.startedAtEpochMillis)!!,
            submitted = at(count.submittedAtEpochMillis),
            decided = at(count.decidedAtEpochMillis),
            rejectionReason = count.rejectionReason,
            lines = database.stockCounts().lines(farmId, count.id).map {
                StockCountLineView(it.itemId, labels[it.itemId] ?: it.itemId, units[it.itemId].orEmpty(), it.onHandAtCountMilli, it.countedMilli, it.countedByActorId)
            },
        )
    }
    return counts to items.map { StockCountItem(it.id, "${it.name} · ${it.sku}", it.unit, it.quantityMilli) }
}

/**
 * Stock count (FOS-INV-016) and its review (FOS-INV-015), owner decision D-021: workers count
 * (CAPTURE_STOCK_COUNT); management posts or rejects (POST_STOCK_ADJUSTMENT).
 */
@Composable
fun StockCountHost(
    database: FarmOsDatabase,
    farmId: String,
    role: String,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val commands = remember(farmId) { StockCountCommands(database, farmId) }
    val canCount = rolePermits(role, Permission.CAPTURE_STOCK_COUNT)
    val canPost = rolePermits(role, Permission.POST_STOCK_ADJUSTMENT)
    var counts by remember(farmId) { mutableStateOf(emptyList<StockCountView>()) }
    var items by remember(farmId) { mutableStateOf(emptyList<StockCountItem>()) }
    var reviewing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        val (loadedCounts, loadedItems) = loadStockCounts(database, farmId)
        counts = loadedCounts
        items = loadedItems
    }

    fun runWrite(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            runCatching { block(); refresh() }
                .onSuccess { enqueueSync() }
                .onFailure { error = it.message }
            busy = false
        }
    }

    LaunchedEffect(farmId) { runCatching { refresh() }.onFailure { error = it.message } }

    if (reviewing) {
        StockAdjustmentScreen(
            counts = counts,
            canPost = canPost,
            busy = busy,
            error = error,
            onPost = { countId ->
                runWrite {
                    check(canPost) { "Only farm management posts stock adjustments" }
                    val count = counts.first { it.countId == countId }
                    val adjustments = count.lines.filter { it.varianceMilli != 0L }.map { StockCountAdjustment(it.itemId, it.varianceMilli) }
                    commands.post(PostStockCount(countId, adjustments), newContext())
                }
            },
            onReject = { countId, reason ->
                runWrite {
                    check(canPost) { "Only farm management rejects a stock count" }
                    commands.reject(RejectStockCount(countId, reason), newContext())
                }
            },
            onBack = { reviewing = false },
        )
        return
    }

    StockCountScreen(
        counts = counts,
        items = items,
        canCount = canCount,
        busy = busy,
        error = error,
        onStart = { runWrite { check(canCount) { "Your role cannot record stock counts" }; commands.start(StartStockCount(UUID.randomUUID().toString()), newContext()) } },
        onRecord = { countId, itemId, counted ->
            runWrite {
                check(canCount) { "Your role cannot record stock counts" }
                // On hand is read at the moment of counting and carried with the line.
                val onHand = requireNotNull(database.inventory().item(farmId, itemId)) { "Inventory item not found" }.quantityMilli
                commands.recordLine(RecordStockCountLine(countId, itemId, counted, onHand), newContext())
            }
        },
        onSubmit = { countId -> runWrite { check(canCount) { "Your role cannot record stock counts" }; commands.submit(SubmitStockCount(countId), newContext()) } },
        onOpenReview = { reviewing = true },
        onBack = onBack,
    )
}
