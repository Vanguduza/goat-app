package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.rabbit.AgreeRabbitContract
import com.farmos.domain.rabbit.BindRabbitBedding
import com.farmos.domain.rabbit.DecideRabbitRetention
import com.farmos.domain.rabbit.EnqueueRabbitWaitlist
import com.farmos.domain.rabbit.FulfillRabbitWaitlist
import com.farmos.domain.rabbit.PromoteRabbitKit
import com.farmos.domain.rabbit.RecordRabbitMarketPlan
import com.farmos.domain.rabbit.RegisterRabbitKit
import com.farmos.feature.ops.SimpleCaptureScreen
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

/** Rabbit commerce/waitlist boundary kept separate from biological RabbitModuleHost to avoid another mega-host. */
@Composable
fun RabbitCommerceModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val busyState = remember { mutableStateOf(false) }
    val errorState = remember { mutableStateOf<String?>(null) }
    val kitRowsState = remember { mutableStateOf(emptyList<String>()) }
    val waitlistRowsState = remember { mutableStateOf(emptyList<String>()) }
    val beddingLineState = remember { mutableStateOf("No bedding item bound.") }
    suspend fun refresh() {
        kitRowsState.value = ops.kits().map { "${it.id} ${it.tempLabel} · ${it.sex} · ${it.retention} · ${it.status}" }
        waitlistRowsState.value = ops.waitlist().map { "${it.id} ${it.contactName} · qty ${it.qty} · ${it.status}" }
        beddingLineState.value = ops.inventoryLink()?.let { "Bedding ${it.nestBeddingItemId ?: "none"} · ${it.nestBeddingQtyMilli} milli" } ?: "No bedding item bound."
    }
    LaunchedEffect(farmId) { runCatching { refresh() } }
    fun run(block: suspend () -> Unit) {
        scope.launch {
            busyState.value = true; errorState.value = null
            runCatching { block(); refresh() }.onSuccess { enqueueSync() }.onFailure { errorState.value = it.message }
            busyState.value = false
        }
    }
    val busy = busyState.value
    val error = errorState.value
    val kitRows = kitRowsState.value
    val waitlistRows = waitlistRowsState.value
    val beddingLine = beddingLineState.value
            val contact = remember { mutableStateOf("") }
            val qty = remember { mutableStateOf("1") }
            val waitlistId = remember { mutableStateOf("") }
            val kitId = remember { mutableStateOf("") }
            val waveId = remember { mutableStateOf("") }
            val label = remember { mutableStateOf("") }
            val sex = remember { mutableStateOf("unknown") }
            val tag = remember { mutableStateOf("") }
            val decision = remember { mutableStateOf("sale_pet") }
            val buyer = remember { mutableStateOf("") }
            val amount = remember { mutableStateOf("") }
            val purpose = remember { mutableStateOf("meat") }
            val grams = remember { mutableStateOf("") }
            val day = remember { mutableStateOf("") }
            SimpleCaptureScreen(
                screenId = "FOS-RABBIT-027",
                title = "Rabbit waitlist",
                help = "Enqueue a buyer, mark a kit sale_pet, then fulfill. A contract posts income in minor units.",
                empty = "No waitlist rows on this device.",
                rows = waitlistRows + kitRows,
                busy = busy,
                error = error,
                fields = listOf("Buyer" to contact, "Quantity" to qty),
                actionLabel = "Enqueue waitlist",
                onSubmit = {
                    run {
                        ops.enqueueWaitlist(
                            EnqueueRabbitWaitlist(UUID.randomUUID().toString(), contact.value, qty = qty.value.toIntOrNull() ?: 0),
                            newContext(),
                        )
                    }
                },
                onBack = onBack,
                extra = {
                    androidx.compose.material3.OutlinedTextField(waveId.value, { waveId.value = it }, label = { androidx.compose.material3.Text("Wave id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(label.value, { label.value = it }, label = { androidx.compose.material3.Text("Kit label") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(sex.value, { sex.value = it }, label = { androidx.compose.material3.Text("Kit sex") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.Button(
                        onClick = {
                            run {
                                ops.registerKit(RegisterRabbitKit(UUID.randomUUID().toString(), waveId.value, label.value, sex.value), newContext())
                            }
                        },
                        enabled = !busy && waveId.value.isNotBlank() && label.value.isNotBlank(),
                    ) { androidx.compose.material3.Text("Register kit") }
                    androidx.compose.material3.OutlinedTextField(kitId.value, { kitId.value = it }, label = { androidx.compose.material3.Text("Kit id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(decision.value, { decision.value = it }, label = { androidx.compose.material3.Text("Retention") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(day.value, { day.value = it }, label = { androidx.compose.material3.Text("Date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.Button(
                        onClick = {
                            run {
                                ops.decideRetention(
                                    DecideRabbitRetention(UUID.randomUUID().toString(), kitId.value, decision.value, LocalDate.parse(day.value).toEpochDay()),
                                    newContext(),
                                )
                            }
                        },
                        enabled = !busy && kitId.value.isNotBlank() && day.value.isNotBlank(),
                    ) { androidx.compose.material3.Text("Record retention") }
                    androidx.compose.material3.OutlinedTextField(tag.value, { tag.value = it }, label = { androidx.compose.material3.Text("Promote tag") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.Button(
                        onClick = {
                            run {
                                ops.promoteKit(PromoteRabbitKit(kitId.value, UUID.randomUUID().toString(), tag.value, "FEMALE"), newContext())
                            }
                        },
                        enabled = !busy && kitId.value.isNotBlank() && tag.value.isNotBlank(),
                    ) { androidx.compose.material3.Text("Promote kit") }
                    androidx.compose.material3.OutlinedTextField(waitlistId.value, { waitlistId.value = it }, label = { androidx.compose.material3.Text("Waitlist id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.Button(
                        onClick = {
                            run { ops.fulfillWaitlist(FulfillRabbitWaitlist(waitlistId.value, kitId.value), newContext()) }
                        },
                        enabled = !busy && waitlistId.value.isNotBlank() && kitId.value.isNotBlank(),
                    ) { androidx.compose.material3.Text("Fulfill waitlist") }
                    androidx.compose.material3.OutlinedTextField(buyer.value, { buyer.value = it }, label = { androidx.compose.material3.Text("Contract buyer") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(amount.value, { amount.value = it }, label = { androidx.compose.material3.Text("Amount") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.Button(
                        onClick = {
                            run {
                                val major = amount.value.replace(',', '.').toDoubleOrNull() ?: error("Enter an amount")
                                ops.agreeContract(
                                    AgreeRabbitContract(
                                        contractId = UUID.randomUUID().toString(),
                                        waitlistId = waitlistId.value.trim().ifBlank { null },
                                        buyerName = buyer.value,
                                        amountMinor = (major * 100.0).toLong(),
                                        occurredEpochDay = LocalDate.parse(day.value).toEpochDay(),
                                    ),
                                    newContext(),
                                )
                            }
                        },
                        enabled = !busy && buyer.value.isNotBlank() && amount.value.isNotBlank() && day.value.isNotBlank(),
                    ) { androidx.compose.material3.Text("Agree contract") }
                    androidx.compose.material3.OutlinedTextField(purpose.value, { purpose.value = it }, label = { androidx.compose.material3.Text("Plan purpose") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(grams.value, { grams.value = it }, label = { androidx.compose.material3.Text("Target grams") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.Button(
                        onClick = {
                            run {
                                ops.recordPlan(
                                    RecordRabbitMarketPlan(
                                        planId = UUID.randomUUID().toString(),
                                        kitId = kitId.value.trim().ifBlank { null },
                                        waveId = waveId.value.trim().ifBlank { null },
                                        targetWeightGrams = grams.value.toIntOrNull() ?: 0,
                                        targetEpochDay = LocalDate.parse(day.value).toEpochDay(),
                                        purpose = purpose.value,
                                    ),
                                    newContext(),
                                )
                            }
                        },
                        enabled = !busy && grams.value.isNotBlank() && day.value.isNotBlank(),
                    ) { androidx.compose.material3.Text("Record market plan") }
                    androidx.compose.material3.Text(beddingLine)
                    androidx.compose.material3.Text("Bind a bedding SKU. Nest place does not issue stock.")
                    val bedItem = remember { mutableStateOf("") }
                    val bedQty = remember { mutableStateOf("1000") }
                    androidx.compose.material3.OutlinedTextField(bedItem.value, { bedItem.value = it }, label = { androidx.compose.material3.Text("Bedding item id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(bedQty.value, { bedQty.value = it }, label = { androidx.compose.material3.Text("Qty milli") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                    androidx.compose.material3.Button(
                        onClick = {
                            run {
                                ops.bindBedding(
                                    BindRabbitBedding(bedItem.value.trim().ifBlank { null }, bedQty.value.toLongOrNull() ?: 0L),
                                    newContext(),
                                )
                            }
                        },
                        enabled = !busy && bedQty.value.isNotBlank(),
                    ) { androidx.compose.material3.Text("Bind bedding") }
                },
            )
        
}
