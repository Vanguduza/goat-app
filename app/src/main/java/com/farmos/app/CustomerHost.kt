package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.design.FarmSearchPage
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.design.FarmSelectorSearch
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.CustomerCommands
import com.farmos.domain.access.Permission
import com.farmos.domain.ops.CreateFarmCustomer
import com.farmos.domain.ops.UpdateFarmCustomer
import com.farmos.feature.ops.CustomerScreens
import com.farmos.feature.ops.CustomerView
import java.util.UUID
import kotlinx.coroutines.launch

/** Every customer on the farm, active first. */
internal suspend fun loadCustomers(database: FarmOsDatabase, farmId: String): List<CustomerView> =
    database.customers().all(farmId).map { CustomerView(it.id, it.name, it.phone, it.active) }

/** Search-as-you-type over every active customer (D-004, FOS-ATOM-012); never a capped list. */
internal fun customerSelectorSearch(database: FarmOsDatabase, farmId: String): FarmSelectorSearch = FarmSelectorSearch { query, offset, limit ->
    val pattern = query.trim().takeIf { it.isNotEmpty() }?.let { "%${escapeLike(it)}%" }
    val rows = database.customers().searchActive(farmId, pattern, limit + 1, offset)
    FarmSearchPage(rows.take(limit).map { FarmSelectorOption(it.id, it.name, it.phone) }, hasMore = rows.size > limit)
}

/** The customer register (FOS-SALES-002/003): farm staff who record work keep it. */
@Composable
fun CustomerRegisterHost(
    database: FarmOsDatabase,
    farmId: String,
    role: String,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val commands = remember(farmId) { CustomerCommands(database, farmId) }
    val canManage = rolePermits(role, Permission.RECORD_FARM_WORK)
    var customers by remember(farmId) { mutableStateOf(emptyList<CustomerView>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun runWrite(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            runCatching {
                check(canManage) { "Customers are kept by farm staff" }
                block()
                customers = loadCustomers(database, farmId)
            }.onSuccess { enqueueSync() }.onFailure { error = it.message }
            busy = false
        }
    }

    LaunchedEffect(farmId) { runCatching { customers = loadCustomers(database, farmId) }.onFailure { error = it.message } }

    CustomerScreens(
        customers = customers,
        canManage = canManage,
        busy = busy,
        error = error,
        onAdd = { name, phone -> runWrite { commands.create(CreateFarmCustomer(UUID.randomUUID().toString(), name, phone), newContext()) } },
        onUpdate = { id, name, phone -> runWrite { commands.update(UpdateFarmCustomer(id, name = name, phone = phone), newContext()) } },
        onSetActive = { id, active -> runWrite { commands.update(UpdateFarmCustomer(id, active = active), newContext()) } },
        onBack = onBack,
    )
}
