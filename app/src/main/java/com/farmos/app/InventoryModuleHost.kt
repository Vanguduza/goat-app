package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.CreateInventoryItem
import com.farmos.domain.ops.IssueInventoryLot
import com.farmos.domain.ops.MoveInventory
import com.farmos.domain.ops.ReceiveInventoryLot
import com.farmos.domain.ops.RecordReorderAlert
import com.farmos.domain.ops.SetInventoryReorder
import com.farmos.feature.ops.InventoryReadModel
import com.farmos.feature.ops.InventoryScreen
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun InventoryModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadReadModel: suspend () -> InventoryReadModel = { InventoryReadModel() },
    /** Stock count and review (D-021), shown in place of the inventory pages while open. */
    stockCount: (@Composable (onBack: () -> Unit) -> Unit)? = null,
) {
    var counting by remember { mutableStateOf(false) }
    if (counting && stockCount != null) return stockCount { counting = false }
    val scope = rememberCoroutineScope()
    var rows by remember(farmId) { mutableStateOf(emptyList<String>()) }
    var itemOptions by remember(farmId) { mutableStateOf(emptyList<FarmSelectorOption>()) }
    var readModel by remember(farmId) { mutableStateOf(InventoryReadModel()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        val items = ops.items()
        rows = items.map { item ->
            "${item.id} ${item.sku} · ${item.name} · ${item.quantityMilli} ${item.unit}"
        }
        itemOptions = items.map { item ->
            FarmSelectorOption(item.id, "${item.name} · ${item.sku}", "${BigDecimal.valueOf(item.quantityMilli, 3).stripTrailingZeros().toPlainString()} ${item.unit} on hand")
        }
        readModel = loadReadModel()
    }

    fun quantityMilli(text: String): Long = BigDecimal(text.replace(',', '.'))
        .movePointRight(3)
        .longValueExact()

    fun runWrite(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            runCatching {
                block()
                refresh()
            }.onSuccess {
                enqueueSync()
            }.onFailure { failure ->
                error = failure.message
            }
            busy = false
        }
    }

    LaunchedEffect(farmId) {
        runCatching { refresh() }
            .onFailure { error = it.message }
    }

    InventoryScreen(
        rows = rows,
        itemOptions = itemOptions,
        busy = busy,
        error = error,
        onCreate = { sku, name, unit ->
            runWrite {
                ops.createItem(
                    CreateInventoryItem(UUID.randomUUID().toString(), sku, name, unit),
                    newContext(),
                )
            }
        },
        onMove = { itemId, direction, quantity ->
            runWrite {
                ops.move(
                    MoveInventory(
                        movementId = UUID.randomUUID().toString(),
                        itemId = itemId,
                        direction = direction,
                        quantityMilli = quantityMilli(quantity),
                        occurredAtEpochMillis = System.currentTimeMillis(),
                    ),
                    newContext(),
                )
            }
        },
        onReceiveLot = { itemId, lotCode, expiry, quantity ->
            runWrite {
                ops.receiveLot(
                    ReceiveInventoryLot(
                        lotId = UUID.randomUUID().toString(),
                        itemId = itemId,
                        lotCode = lotCode,
                        expiresEpochDay = LocalDate.parse(expiry).toEpochDay(),
                        quantityMilli = quantityMilli(quantity),
                    ),
                    newContext(),
                )
            }
        },
        onIssueLot = { itemId, quantity ->
            runWrite {
                ops.issueLot(
                    IssueInventoryLot(
                        issueId = UUID.randomUUID().toString(),
                        itemId = itemId,
                        quantityMilli = quantityMilli(quantity),
                    ),
                    newContext(),
                )
            }
        },
        onSetReorder = { itemId, quantity ->
            runWrite {
                ops.setReorder(
                    SetInventoryReorder(itemId, quantityMilli(quantity)),
                    newContext(),
                )
            }
        },
        onRecordReorder = { itemId, day ->
            runWrite {
                ops.recordReorderAlert(
                    RecordReorderAlert(
                        alertId = UUID.randomUUID().toString(),
                        itemId = itemId,
                        occurredEpochDay = LocalDate.parse(day).toEpochDay(),
                    ),
                    newContext(),
                )
            }
        },
        onBack = onBack,
        readModel = readModel,
        onOpenStockCount = stockCount?.let { { counting = true } },
    )
}
