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
import com.farmos.data.herd.CustomerCommands
import com.farmos.domain.ops.FarmCurrency
import com.farmos.domain.ops.RecordExitSale
import com.farmos.feature.ops.AnimalSaleScreen
import com.farmos.feature.ops.SaleExitView
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch
import com.farmos.core.design.runSuspendCatching

/** Every sale exit still waiting for its sale money, newest first (D-022). */
internal suspend fun loadUnsettledSaleExits(database: FarmOsDatabase, farmId: String): List<SaleExitView> =
    database.animalExits().unsettledSaleExits(farmId).map { exit ->
        SaleExitView(
            exitId = exit.exitId,
            animalId = exit.animalId,
            animalLabel = listOfNotNull(exit.tag, exit.name?.takeIf { it.isNotBlank() }, exit.speciesCode).joinToString(" · "),
            soldTo = exit.buyer ?: "a buyer",
            soldOn = LocalDate.ofEpochDay(exit.occurredEpochDay),
            price = exit.priceMinor?.let { BigDecimal.valueOf(it, FarmCurrency.minorDigits(exit.currency.orEmpty())).toPlainString() },
        )
    }

/** Animal sale (FOS-SALES-007) for one farm: records the money for a sale exit once, in the farm currency. */
@Composable
internal fun AnimalSaleHost(database: FarmOsDatabase, farmId: String, newContext: () -> LocalCommandContext, enqueueSync: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val commands = remember(farmId) { CustomerCommands(database, farmId) }
    val currency by rememberFarmCurrency(farmId) { database.farmCurrency(farmId) }
    var exits by remember(farmId) { mutableStateOf(emptyList<SaleExitView>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(farmId) { runSuspendCatching { exits = loadUnsettledSaleExits(database, farmId) }.onFailure { error = it.message } }
    AnimalSaleScreen(
        exits = exits,
        currency = currency,
        customerSearch = remember(farmId) { customerSelectorSearch(database, farmId) },
        busy = busy,
        error = error,
        onRecord = { exit, amount, day, customerId ->
            scope.launch {
                busy = true
                error = null
                runSuspendCatching {
                    val code = checkNotNull(currency) { "The farm currency is still loading" }
                    commands.recordExitSale(
                        RecordExitSale(UUID.randomUUID().toString(), exit.exitId, exit.animalId, exit.animalLabel, amount.toScaledLongExact(FarmCurrency.minorDigits(code), "Amount"), code, day.toEpochDay(), customerId),
                        newContext(),
                    )
                    exits = loadUnsettledSaleExits(database, farmId)
                }.onSuccess { enqueueSync() }.onFailure { error = it.message }
                busy = false
            }
        },
        onBack = onBack,
    )
}
