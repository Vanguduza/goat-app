package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.FarmCurrency
import com.farmos.domain.ops.RecordMoney
import com.farmos.feature.ops.MoneyCaptureScreen
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun MoneyModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadCurrency: suspend () -> String = { FarmCurrency.DEFAULT_CODE },
) {
    val scope = rememberCoroutineScope()
    val currency by rememberFarmCurrency(farmId, loadCurrency)
    var rows by remember(farmId) { mutableStateOf(emptyList<String>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        rows = ops.recentMoney().map { row ->
            "${row.kind} ${row.categoryCode} ${row.amountMinor} ${row.currency}"
        }
    }

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

    MoneyCaptureScreen(
        rows = rows,
        busy = busy,
        error = error,
        onRecord = { kind, category, amount, day ->
            runWrite {
                val code = checkNotNull(currency) { "The farm currency is still loading" }
                val amountMinor = amount.toScaledLongExact(FarmCurrency.minorDigits(code), "Amount")
                ops.recordMoney(
                    RecordMoney(
                        recordId = UUID.randomUUID().toString(),
                        kind = kind.trim(),
                        categoryCode = category.trim(),
                        amountMinor = amountMinor,
                        currency = code,
                        occurredEpochDay = LocalDate.parse(day).toEpochDay(),
                    ),
                    newContext(),
                )
            }
        },
        onBack = onBack,
    )
}
