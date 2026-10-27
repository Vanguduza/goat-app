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

/** Typed intent callbacks. The host retains local command context, execution and refresh. */
internal class RabbitCommerceActions(
    val enqueueWaitlist: suspend (EnqueueRabbitWaitlist) -> Unit,
    val registerKit: suspend (RegisterRabbitKit) -> Unit,
    val decideRetention: suspend (DecideRabbitRetention) -> Unit,
    val promoteKit: suspend (PromoteRabbitKit) -> Unit,
    val fulfillWaitlist: suspend (FulfillRabbitWaitlist) -> Unit,
    val agreeContract: suspend (AgreeRabbitContract) -> Unit,
    val recordPlan: suspend (RecordRabbitMarketPlan) -> Unit,
    val bindBedding: suspend (BindRabbitBedding) -> Unit,
)

/** FOS-RABBIT-027 capture modes: one job and its fields, with drafts outside the selected branch. */
@Composable
internal fun RabbitCommerceContent(
    currency: String?,
    busy: Boolean,
    error: String?,
    kitRows: List<String>,
    waitlistRows: List<String>,
    beddingLine: String,
    records: RabbitCommerceRecords,
    actions: RabbitCommerceActions,
    runAction: (suspend () -> Unit) -> Unit,
    onBack: () -> Unit,
) {
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
            var activeTool by rememberSaveable { mutableStateOf("Overview") }
            val bedItem = remember { mutableStateOf("") }
            val bedQty = remember { mutableStateOf("1000") }
            RabbitCommerceRecordNavigator(records) { recordActions -> SimpleCaptureScreen(
                screenId = "FOS-RABBIT-027",
                title = "Rabbit waitlist",
                help = "",
                empty = "No waitlist rows on this device.",
                rows = waitlistRows + kitRows,
                busy = busy,
                error = error,
                fields = if (activeTool == "Waitlist") listOf("Buyer" to contact, "Quantity" to qty) else emptyList(),
                actionLabel = "Enqueue waitlist",
                onSubmit = {
                    runAction {
                        actions.enqueueWaitlist(
                            EnqueueRabbitWaitlist(UUID.randomUUID().toString(), contact.value, qty = qty.value.toIntOrNull() ?: 0)
                        )
                    }
                },
                onBack = onBack,
                captureVisible = activeTool !in setOf("Overview", "Records"),
                recordsVisible = activeTool == "Records",
                showPrimaryAction = activeTool == "Waitlist",
                recordTitle = if (activeTool == "Waitlist") "Add buyer" else activeTool,
                overview = {
                    if (activeTool == "Overview") RabbitCommerceOverview(records) { activeTool = it }
                },
                navigation = {
                    if (activeTool != "Overview") FarmActionButton(onClick = { activeTool = "Overview" }) { Text("Waitlist overview") }
                    if (activeTool == "Records") recordActions()
                },
                extra = {
                    when (activeTool) {
                        "Register kit" -> {
                            com.farmos.core.design.FarmOutlinedTextField(waveId.value, { waveId.value = it }, label = { androidx.compose.material3.Text("Wave id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            com.farmos.core.design.FarmOutlinedTextField(label.value, { label.value = it }, label = { androidx.compose.material3.Text("Kit label") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            com.farmos.core.design.FarmOutlinedTextField(sex.value, { sex.value = it }, label = { androidx.compose.material3.Text("Kit sex") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            com.farmos.core.design.FarmPrimaryButton(
                                onClick = {
                                    runAction {
                                        actions.registerKit(RegisterRabbitKit(UUID.randomUUID().toString(), waveId.value, label.value, sex.value))
                                    }
                                },
                                enabled = !busy && waveId.value.isNotBlank() && label.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Register kit") }

                        }
                        "Retention" -> {
                            com.farmos.core.design.FarmOutlinedTextField(kitId.value, { kitId.value = it }, label = { androidx.compose.material3.Text("Kit id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            com.farmos.core.design.FarmOutlinedTextField(decision.value, { decision.value = it }, label = { androidx.compose.material3.Text("Retention") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            com.farmos.core.design.FarmOutlinedTextField(day.value, { day.value = it }, label = { androidx.compose.material3.Text("Date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            com.farmos.core.design.FarmPrimaryButton(
                                onClick = {
                                    runAction {
                                        actions.decideRetention(
                                            DecideRabbitRetention(UUID.randomUUID().toString(), kitId.value, decision.value, LocalDate.parse(day.value).toEpochDay())
                                        )
                                    }
                                },
                                enabled = !busy && kitId.value.isNotBlank() && day.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Record retention") }

                        }
                        "Promote kit" -> {
                            com.farmos.core.design.FarmOutlinedTextField(kitId.value, { kitId.value = it }, label = { androidx.compose.material3.Text("Kit id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            com.farmos.core.design.FarmOutlinedTextField(tag.value, { tag.value = it }, label = { androidx.compose.material3.Text("Promote tag") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            com.farmos.core.design.FarmPrimaryButton(
                                onClick = {
                                    runAction {
                                        actions.promoteKit(PromoteRabbitKit(kitId.value, UUID.randomUUID().toString(), tag.value, "FEMALE"))
                                    }
                                },
                                enabled = !busy && kitId.value.isNotBlank() && tag.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Promote kit") }

                        }
                        "Fulfill" -> {
                            com.farmos.core.design.FarmOutlinedTextField(kitId.value, { kitId.value = it }, label = { androidx.compose.material3.Text("Kit id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            com.farmos.core.design.FarmOutlinedTextField(waitlistId.value, { waitlistId.value = it }, label = { androidx.compose.material3.Text("Waitlist id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            com.farmos.core.design.FarmPrimaryButton(
                                onClick = {
                                    runAction { actions.fulfillWaitlist(FulfillRabbitWaitlist(waitlistId.value, kitId.value)) }
                                },
                                enabled = !busy && waitlistId.value.isNotBlank() && kitId.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Fulfill waitlist") }

                        }
                        "Contract" -> {
                            com.farmos.core.design.FarmOutlinedTextField(waitlistId.value, { waitlistId.value = it }, label = { androidx.compose.material3.Text("Waitlist id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            com.farmos.core.design.FarmOutlinedTextField(day.value, { day.value = it }, label = { androidx.compose.material3.Text("Date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            com.farmos.core.design.FarmOutlinedTextField(buyer.value, { buyer.value = it }, label = { androidx.compose.material3.Text("Contract buyer") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            com.farmos.core.design.FarmOutlinedTextField(amount.value, { amount.value = it }, label = { androidx.compose.material3.Text("Amount") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            com.farmos.core.design.FarmPrimaryButton(
                                onClick = {
                                    runAction {
                                        val code = checkNotNull(currency) { "The farm currency is still loading" }
                                        val amountMinor = amount.value.toScaledLongExact(FarmCurrency.minorDigits(code), "Amount")
                                        actions.agreeContract(
                                            AgreeRabbitContract(
                                                contractId = UUID.randomUUID().toString(),
                                                waitlistId = waitlistId.value.trim().ifBlank { null },
                                                buyerName = buyer.value,
                                                amountMinor = amountMinor,
                                                currency = code,
                                                occurredEpochDay = LocalDate.parse(day.value).toEpochDay(),
                                            )
                                        )
                                    }
                                },
                                enabled = !busy && buyer.value.isNotBlank() && amount.value.isNotBlank() && day.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Agree contract") }

                        }
                        "Market plan" -> {
                            com.farmos.core.design.FarmOutlinedTextField(kitId.value, { kitId.value = it }, label = { androidx.compose.material3.Text("Kit id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            com.farmos.core.design.FarmOutlinedTextField(waveId.value, { waveId.value = it }, label = { androidx.compose.material3.Text("Wave id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            com.farmos.core.design.FarmOutlinedTextField(day.value, { day.value = it }, label = { androidx.compose.material3.Text("Date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            com.farmos.core.design.FarmOutlinedTextField(purpose.value, { purpose.value = it }, label = { androidx.compose.material3.Text("Plan purpose") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            com.farmos.core.design.FarmOutlinedTextField(grams.value, { grams.value = it }, label = { androidx.compose.material3.Text("Target grams") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            com.farmos.core.design.FarmPrimaryButton(
                                onClick = {
                                    runAction {
                                        actions.recordPlan(
                                            RecordRabbitMarketPlan(
                                                planId = UUID.randomUUID().toString(),
                                                kitId = kitId.value.trim().ifBlank { null },
                                                waveId = waveId.value.trim().ifBlank { null },
                                                targetWeightGrams = grams.value.toIntOrNull() ?: 0,
                                                targetEpochDay = LocalDate.parse(day.value).toEpochDay(),
                                                purpose = purpose.value,
                                            )
                                        )
                                    }
                                },
                                enabled = !busy && grams.value.isNotBlank() && day.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Record market plan") }

                        }
                        "Bedding" -> {
                            androidx.compose.material3.Text(beddingLine)
                            androidx.compose.material3.Text("Bind a bedding SKU. Nest place does not issue stock.")
                            com.farmos.core.design.FarmOutlinedTextField(bedItem.value, { bedItem.value = it }, label = { androidx.compose.material3.Text("Bedding item id") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            com.farmos.core.design.FarmOutlinedTextField(bedQty.value, { bedQty.value = it }, label = { androidx.compose.material3.Text("Qty milli") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                            com.farmos.core.design.FarmPrimaryButton(
                                onClick = {
                                    runAction {
                                        actions.bindBedding(
                                            BindRabbitBedding(bedItem.value.trim().ifBlank { null }, bedQty.value.toLongOrNull() ?: 0L)
                                        )
                                    }
                                },
                                enabled = !busy && bedQty.value.isNotBlank(),
                            ) { androidx.compose.material3.Text("Bind bedding") }
                        }
                        else -> Unit
                    }
                },
            ) }


}
