package com.farmos.app

import com.farmos.core.database.FarmOsDatabase
import com.farmos.feature.ops.SaleCurrencyTotalView
import com.farmos.feature.ops.SaleView
import com.farmos.feature.ops.SalesRecords
import java.math.BigDecimal

private const val SALES_RECORD_LIMIT = 500

/**
 * Farm-scoped read model for the read-only sales record pages. Nothing here writes. Totals per
 * currency and the sale count are exhaustive aggregates; the list is the latest [SALES_RECORD_LIMIT].
 */
internal suspend fun loadSalesRecords(database: FarmOsDatabase, farmId: String): SalesRecords {    val sales = database.sales()
    return SalesRecords(
        sales = sales.recent(farmId, SALES_RECORD_LIMIT).map {
            SaleView(it.id, it.itemKind, BigDecimal.valueOf(it.quantityMilli, 3).stripTrailingZeros().toPlainString(), it.amountMinor, it.currency, it.occurredEpochDay)
        },
        saleCount = sales.count(farmId),
        totals = sales.totalsByCurrency(farmId).map { SaleCurrencyTotalView(it.currency, it.amountMinor, it.saleCount) },
    )
}

/** An agreed rabbit reservation (rabbit_sales_contracts) that a reservation sale can settle. */
internal data class RabbitContractView(
    val id: String,
    val buyerName: String,
    val amountMinor: Long,
    val currency: String,
    val status: String,
    val epochDay: Long,
)

/**
 * Farm-scoped agreed rabbit contracts, most recent first. Nothing here writes. Used by
 * FOS-SALES-009; the caller (which owns the database) supplies it to SalesModuleHost.
 */
internal suspend fun loadRabbitContracts(database: FarmOsDatabase, farmId: String): List<RabbitContractView> =
    database.lifecycle().rabbitContracts(farmId).map {
        RabbitContractView(it.id, it.buyerName, it.amountMinor, it.currency, it.status, it.occurredEpochDay)
    }
