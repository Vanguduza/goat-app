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

    /**
     * Currencies a farm can record in: active ISO 4217 codes with a defined minor unit, ordered by code.
     * The platform list also carries withdrawn currencies and fund or accounting units, which are excluded.
     */
    val recordable: List<Currency> by lazy {
        Currency.getAvailableCurrencies()
            .filter { it.defaultFractionDigits >= 0 && it.currencyCode !in NOT_FOR_RECORDING }
            .sortedBy { it.currencyCode }
    }

    /** Withdrawn ISO 4217 currencies and fund/accounting codes that platform lists still include. */
    private val NOT_FOR_RECORDING = setOf(
        // Withdrawn national currencies.
        "ADP", "AFA", "ATS", "AYM", "AZM", "BEF", "BGL", "BYB", "BYR", "CSD", "CUC", "CYP", "DEM", "EEK", "ESP",
        "FIM", "FRF", "GHC", "GRD", "GWP", "HRK", "IEP", "ITL", "LTL", "LUF", "LVL", "MGF", "MRO", "MTL", "MZM",
        "NLG", "PTE", "ROL", "RUR", "SDD", "SIT", "SKK", "SRG", "STD", "TMM", "TPE", "TRL", "VEB", "VEF",
        "XFO", "XFU", "YUM", "ZAL", "ZMK", "ZWD", "ZWN", "ZWR",
        // Fund, index and settlement units that are not a farm's trading currency.
        "BOV", "CHE", "CHW", "CLF", "COU", "MXV", "USN", "USS", "UYI", "UYW", "XSU", "XUA",
    )

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
