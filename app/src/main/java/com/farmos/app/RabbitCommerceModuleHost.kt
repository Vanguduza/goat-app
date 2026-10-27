package com.farmos.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import com.farmos.core.design.FarmActionButton
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.FarmCurrency
import com.farmos.domain.rabbit.AgreeRabbitContract
import com.farmos.domain.rabbit.BindRabbitBedding
import com.farmos.domain.rabbit.DecideRabbitRetention
import com.farmos.domain.rabbit.EnqueueRabbitWaitlist
import com.farmos.domain.rabbit.FulfillRabbitWaitlist
import com.farmos.domain.rabbit.PromoteRabbitKit
import com.farmos.domain.rabbit.RecordRabbitMarketPlan
import com.farmos.domain.rabbit.RegisterRabbitKit
import com.farmos.feature.ops.SimpleCaptureScreen
import com.farmos.feature.rabbit.RabbitCommerceRecordNavigator
import com.farmos.feature.rabbit.RabbitCommerceRecords
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch
import com.farmos.core.design.runSuspendCatching

/** Rabbit commerce/waitlist boundary kept separate from biological RabbitModuleHost to avoid another mega-host. */
@Composable
fun RabbitCommerceModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadRecords: suspend () -> RabbitCommerceRecords = { RabbitCommerceRecords() },
    loadCurrency: suspend () -> String = { FarmCurrency.DEFAULT_CODE },
) {
    val currency by rememberFarmCurrency(farmId, loadCurrency)
    val scope = rememberCoroutineScope()
    val busyState = remember { mutableStateOf(false) }
    val errorState = remember { mutableStateOf<String?>(null) }
    val kitRowsState = remember { mutableStateOf(emptyList<String>()) }
    val waitlistRowsState = remember { mutableStateOf(emptyList<String>()) }
    val beddingLineState = remember { mutableStateOf("No bedding item bound.") }
    val recordsState = remember(farmId) { mutableStateOf(RabbitCommerceRecords()) }
    suspend fun refresh() {
        kitRowsState.value = ops.kits().map { "${it.id} ${it.tempLabel} · ${it.sex} · ${it.retention} · ${it.status}" }
        waitlistRowsState.value = ops.waitlist().map { "${it.id} ${it.contactName} · qty ${it.qty} · ${it.status}" }
        beddingLineState.value = ops.inventoryLink()?.let { "Bedding ${it.nestBeddingItemId ?: "none"} · ${it.nestBeddingQtyMilli} milli" } ?: "No bedding item bound."
        recordsState.value = loadRecords()
    }
    LaunchedEffect(farmId) { runSuspendCatching { refresh() } }
    fun run(block: suspend () -> Unit) {
        scope.launch {
            busyState.value = true; errorState.value = null
            runSuspendCatching { block(); refresh() }.onSuccess { enqueueSync() }.onFailure { errorState.value = it.message }
            busyState.value = false
        }
    }
    val busy = busyState.value
    val error = errorState.value
    val kitRows = kitRowsState.value
    val waitlistRows = waitlistRowsState.value
    val beddingLine = beddingLineState.value
    RabbitCommerceContent(
        currency = currency, busy = busy, error = error,
        kitRows = kitRows, waitlistRows = waitlistRows, beddingLine = beddingLine,
        records = recordsState.value, onBack = onBack, runAction = ::run,
        actions = RabbitCommerceActions(
            enqueueWaitlist = { ops.enqueueWaitlist(it, newContext()) },
            registerKit = { ops.registerKit(it, newContext()) },
            decideRetention = { ops.decideRetention(it, newContext()) },
            promoteKit = { ops.promoteKit(it, newContext()) },
            fulfillWaitlist = { ops.fulfillWaitlist(it, newContext()) },
            agreeContract = { ops.agreeContract(it, newContext()) },
            recordPlan = { ops.recordPlan(it, newContext()) },
            bindBedding = { ops.bindBedding(it, newContext()) },
        ),
    )
}
