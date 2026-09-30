package com.farmos.data.herd

import androidx.room.withTransaction
import com.farmos.core.database.FarmCustomerEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.MoneyRecordEntity
import com.farmos.core.database.SaleRecordEntity
import com.farmos.core.database.journalLocalOperation
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.model.LocalCommandResult
import com.farmos.domain.ops.CreateFarmCustomer
import com.farmos.domain.ops.CustomerRules
import com.farmos.domain.ops.OpsValidator
import com.farmos.domain.ops.RecordCustomerSale
import com.farmos.domain.ops.RecordExitSale
import com.farmos.domain.ops.RecordSale
import com.farmos.domain.ops.UpdateFarmCustomer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The customer register and sales to its customers (D-004), journalled for farm replication. A customer is
 * never deleted; changes merge by later business time. A sale keeps the customer's name when sold.
 */
class CustomerCommands(
    private val database: FarmOsDatabase,
    private val farmId: String,
    /** Replays an operation received from another device: domain writes only, it is already journalled. */
    private val replaying: Boolean = false,
    private val json: Json = Json { encodeDefaults = true },
) {
    suspend fun create(command: CreateFarmCustomer, context: LocalCommandContext): LocalCommandResult {
        CustomerRules.create(command)?.let { error(it) }
        journal(context, CREATE, "farm_customer", command.customerId, json.encodeToString(command)) {
            if (database.customers().get(farmId, command.customerId) == null) {
                database.customers().upsert(
                    FarmCustomerEntity(command.customerId, farmId, command.name.trim(), command.phone?.trim()?.ifBlank { null }, true, context.occurredAtEpochMillis, context.actorId),
                )
            }
        }
        return LocalCommandResult(context.mutationId, command.customerId, true)
    }

    suspend fun update(command: UpdateFarmCustomer, context: LocalCommandContext): LocalCommandResult {
        CustomerRules.update(command)?.let { error(it) }
        requireNotNull(database.customers().get(farmId, command.customerId)) { "Customer not found" }
        journal(context, UPDATE, "farm_customer", command.customerId, json.encodeToString(command)) {
            val current = requireNotNull(database.customers().get(farmId, command.customerId)) { "Customer not found" }
            // A null phone keeps the number; an empty one clears it.
            val phone = command.phone
            // The later change by business time wins, whatever order changes arrive in.
            if (current.updatedAtEpochMillis <= context.occurredAtEpochMillis) {
                database.customers().upsert(
                    current.copy(
                        name = command.name?.trim() ?: current.name,
                        phone = if (phone == null) current.phone else phone.trim().ifBlank { null },
                        active = command.active ?: current.active,
                        updatedAtEpochMillis = context.occurredAtEpochMillis,
                        updatedByActorId = context.actorId,
                    ),
                )
            }
        }
        return LocalCommandResult(context.mutationId, command.customerId, true)
    }

    /** A sale to an active customer, posting its income like any sale. */
    suspend fun recordSale(command: RecordCustomerSale, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.sale(RecordSale(command.saleId, command.itemKind, command.quantityMilli, command.amountMinor, command.currency, command.occurredEpochDay))?.let { error(it) }
        if (!replaying) {
            val customer = database.customers().get(farmId, command.customerId)
            require(customer != null && customer.active) { "Choose an active customer on this farm" }
        }
        journal(context, SALE, "sale_record", command.saleId, json.encodeToString(command)) {
            val item = command.itemKind.trim()
            database.sales().insert(SaleRecordEntity(command.saleId, farmId, item, command.quantityMilli, command.amountMinor, command.currency, command.occurredEpochDay, command.customerId))
            database.money().insert(MoneyRecordEntity(command.saleId, farmId, "income", "sales", command.amountMinor, command.currency, command.occurredEpochDay, "$item · ${command.customerName.trim()}"))
        }
        return LocalCommandResult(context.mutationId, command.saleId, true)
    }

    /** The money for an animal sold through a sale exit; one sale per exit, and never for a reversed exit. */
    suspend fun recordExitSale(command: RecordExitSale, context: LocalCommandContext): LocalCommandResult {
        OpsValidator.sale(RecordSale(command.saleId, "animal", 1_000, command.amountMinor, command.currency, command.occurredEpochDay))?.let { error(it) }
        val exit = requireNotNull(database.animalExits().get(farmId, command.exitId)) { "Sale exit not found on this farm" }
        require(exit.kind == "SALE" && exit.animalId == command.animalId) { "Only a sale exit can be settled by a sale" }
        require(database.sales().forExit(farmId, command.exitId) == null) { "The money for this sale is already recorded" }
        val customerId = command.customerId
        if (!replaying) {
            require(database.animalExits().forAnimal(farmId, command.animalId).none { it.reversesExitId == command.exitId }) { "This sale exit was reversed" }
            if (customerId != null) {
                val customer = database.customers().get(farmId, customerId)
                require(customer != null && customer.active) { "Choose an active customer on this farm" }
            }
        }
        journal(context, EXIT_SALE, "sale_record", command.saleId, json.encodeToString(command)) {
            database.sales().insert(SaleRecordEntity(command.saleId, farmId, "animal", 1_000, command.amountMinor, command.currency, command.occurredEpochDay, customerId, command.exitId))
            database.money().insert(MoneyRecordEntity(command.saleId, farmId, "income", "sales", command.amountMinor, command.currency, command.occurredEpochDay, "Sale of ${command.animalLabel.trim()}"))
        }
        return LocalCommandResult(context.mutationId, command.saleId, true)
    }

    private suspend fun journal(context: LocalCommandContext, commandName: String, entityType: String, entityId: String, payloadJson: String, localWrite: suspend () -> Unit) {
        require(context.farmId == farmId) { "Farm context mismatch" }
        if (replaying) return database.withTransaction { localWrite() }
        database.withTransaction {
            localWrite()
            database.journalLocalOperation(
                operationId = context.mutationId,
                farmId = farmId,
                entityType = entityType,
                entityId = entityId,
                actorId = context.actorId,
                deviceId = context.deviceId,
                businessTimeEpochMillis = context.occurredAtEpochMillis,
                createdAtEpochMillis = System.currentTimeMillis(),
                baseVersion = null,
                operationType = commandName,
                payloadJson = payloadJson,
                schemaVersion = 1,
            )
        }
    }

    companion object {
        const val CREATE = "customer.create.v1"
        const val UPDATE = "customer.update.v1"
        const val SALE = "sale.record.v2"
        const val EXIT_SALE = "sale.record_exit.v1"
    }
}
