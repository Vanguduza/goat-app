package com.farmos.domain.ops

import java.util.Currency
import java.util.Locale

/**
 * The farm's recording currency (owner decision D-018): an ISO 4217 code, chosen per farm, USD until
 * the owner changes it. Amounts are stored as integer minor units of their own currency and are never
 * added across currencies; an amount whose currency is not known is not treated as zero.
 */
object FarmCurrency {
    const val DEFAULT_CODE = "USD"

    /** Currencies a farm can record in: ISO 4217 codes with a defined minor unit, ordered by code. */
    val recordable: List<Currency> by lazy {
        Currency.getAvailableCurrencies()
            .filter { it.defaultFractionDigits >= 0 }
            .sortedBy { it.currencyCode }
    }

    /** True for an upper-case ISO 4217 code a farm can record in. */
    fun isRecordable(code: String): Boolean =
        code.length == 3 && code == code.uppercase() && recordable.any { it.currencyCode == code }

    /** Decimal places of [code]'s minor unit: 2 for USD, 0 for JPY, 3 for BHD. */
    fun minorDigits(code: String): Int {
        require(isRecordable(code)) { "Currency must be an ISO 4217 code" }
        return Currency.getInstance(code).defaultFractionDigits
    }

    /** Currencies whose code or English name contains [query], case-insensitively, ordered by code. */
    fun search(query: String): List<Currency> {
        val needle = query.trim()
        if (needle.isEmpty()) return recordable
        return recordable.filter {
            it.currencyCode.contains(needle, ignoreCase = true) || it.getDisplayName(Locale.ENGLISH).contains(needle, ignoreCase = true)
        }
    }
}
