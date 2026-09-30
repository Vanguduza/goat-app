package com.farmos.domain.ops

import kotlinx.serialization.Serializable

/** Adds a customer to the farm's register (FOS-SALES-002). Replicated as `customer.create.v1`. */
@Serializable
data class CreateFarmCustomer(val customerId: String, val name: String, val phone: String? = null)

/**
 * Changes a customer; a null field keeps its value and an empty [phone] clears it. The later change by
 * business time wins on every device. Replicated as `customer.update.v1`.
 */
@Serializable
data class UpdateFarmCustomer(val customerId: String, val name: String? = null, val phone: String? = null, val active: Boolean? = null)

/**
 * A sale to a customer in the register (D-004). [customerName] is the name when sold, kept with the sale.
 * Posts income like `sale.record.v1`. Replicated as `sale.record.v2`.
 */
@Serializable
data class RecordCustomerSale(
    val saleId: String,
    val customerId: String,
    val customerName: String,
    val itemKind: String,
    val quantityMilli: Long,
    val amountMinor: Long,
    val currency: String,
    val occurredEpochDay: Long,
)

object CustomerRules {
    const val MAX_NAME = 120
    const val MAX_PHONE = 32

    fun name(name: String): String? = when {
        name.isBlank() -> "Enter the customer's name"
        name.trim().length > MAX_NAME -> "A customer's name is at most $MAX_NAME characters"
        else -> null
    }

    fun phone(phone: String?): String? = when {
        phone == null || phone.isBlank() -> null
        phone.trim().length > MAX_PHONE -> "A phone number is at most $MAX_PHONE characters"
        phone.trim().any { !(it.isDigit() || it in "+-() ") } -> "A phone number has digits, spaces, +, - and brackets only"
        else -> null
    }

    fun create(command: CreateFarmCustomer): String? = name(command.name) ?: phone(command.phone)

    fun update(command: UpdateFarmCustomer): String? = when {
        command.name == null && command.phone == null && command.active == null -> "Nothing to change"
        command.name != null -> name(command.name) ?: phone(command.phone)
        else -> phone(command.phone)
    }
}

/**
 * The sale money for an animal that left through a sale exit (D-022, resolution R6): posts the income and
 * links the sale to the exit, once per exit. [animalLabel] is the animal as sold, kept with the sale; the
 * customer is optional. Replicated as `sale.record_exit.v1`.
 */
@Serializable
data class RecordExitSale(
    val saleId: String,
    val exitId: String,
    val animalId: String,
    val animalLabel: String,
    val amountMinor: Long,
    val currency: String,
    val occurredEpochDay: Long,
    val customerId: String? = null,
)
