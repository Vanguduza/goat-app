package com.farmos.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.farmos.core.database.InventoryItemEntity
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.feature.ops.ProcurementPurchaseView
import com.farmos.feature.ops.ProcurementRecords
import java.math.BigDecimal
import kotlinx.coroutines.launch
import com.farmos.core.design.runSuspendCatching

internal class ProcurementModuleState(
    val farmId: String,
    val ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadRecords: suspend () -> ProcurementRecords,
    loadCurrency: suspend () -> String,
    val scope: kotlinx.coroutines.CoroutineScope,
    private val currencyState: androidx.compose.runtime.State<String?>,
) {
    var newContext: () -> LocalCommandContext by mutableStateOf(newContext)
    var enqueueSync: () -> Unit by mutableStateOf(enqueueSync)
    var onBack: () -> Unit by mutableStateOf(onBack)
    var loadRecords: suspend () -> ProcurementRecords by mutableStateOf(loadRecords)
    var loadCurrency: suspend () -> String by mutableStateOf(loadCurrency)

    val currency: String? get() = currencyState.value
    val busy = mutableStateOf(false)
    val saved = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    val rows = mutableStateOf(emptyList<String>())
    val records = mutableStateOf(ProcurementRecords())
    val supplierOptions = mutableStateOf(emptyList<FarmSelectorOption>())
    val itemOptions = mutableStateOf(emptyList<FarmSelectorOption>())
    val items = mutableStateOf(emptyList<InventoryItemEntity>())
    suspend fun refresh() {
        val supplierRows = ops.suppliers()
        supplierOptions.value = supplierRows.map { FarmSelectorOption(it.id, it.name, "Lead time ${it.leadTimeDays} days") }
        val itemRows = ops.items()
        items.value = itemRows
        itemOptions.value = itemRows.map { FarmSelectorOption(it.id, "${it.name} · ${it.sku}", "${BigDecimal.valueOf(it.quantityMilli, 3).stripTrailingZeros().toPlainString()} ${it.unit} on hand") }
        val suppliers = supplierRows.map { "${it.id} ${it.name} · lead ${it.leadTimeDays} d" }
        val purchases = ops.purchases().map { "${it.id} item ${it.itemId} · ${it.quantityMilli} milli · ${procurementMoney(it.amountMinor, it.currency)}" }
        rows.value = suppliers + purchases
        records.value = loadRecords()
    }
    fun run(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        error.value = null
        saved.value = false
        scope.launch {
            try {
                runSuspendCatching {
                    completeModuleWrite(
                        write = block,
                        onCommitted = { saved.value = true },
                        enqueueSync = { enqueueSync() },
                        refresh = ::refresh,
                    )
                }.onSuccess { warning -> error.value = warning }
                    .onFailure { error.value = it.message ?: "The change could not be saved on this device" }
            } finally {
                busy.value = false
            }
        }
    }
    val page = mutableStateOf(ProcurementModulePage.HOME)
    val selectedPurchase = mutableStateOf<ProcurementPurchaseView?>(null)
    val backHome = { page.value = ProcurementModulePage.HOME; selectedPurchase.value = null }


}
