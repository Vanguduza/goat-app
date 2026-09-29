package com.farmos.app

import com.farmos.core.database.FarmOsDatabase
import com.farmos.feature.ops.InventoryItemView
import com.farmos.feature.ops.InventoryLotView
import com.farmos.feature.ops.InventoryMovementView
import com.farmos.feature.ops.InventoryReadModel

internal const val INVENTORY_MOVEMENT_LIMIT = 200

/** Farm-scoped read model for the read-only inventory record pages. Nothing here writes. */
internal suspend fun loadInventoryReadModel(database: FarmOsDatabase, farmId: String): InventoryReadModel {
    val items = database.inventory().items(farmId)
    val byId = items.associateBy { it.id }
    fun label(itemId: String) = byId[itemId]?.let { "${it.sku} · ${it.name}" } ?: "Item not on this device"
    fun unit(itemId: String) = byId[itemId]?.unit.orEmpty()
    return InventoryReadModel(
        items = items.map { InventoryItemView(it.id, it.sku, it.name, it.unit, it.quantityMilli, it.reorderMilli) },
        openLots = database.lifecycle().openLots(farmId).map {
            InventoryLotView(it.id, it.itemId, label(it.itemId), it.lotCode, it.expiresEpochDay, it.quantityMilli, unit(it.itemId))
        },
        movements = database.inventory().movements(farmId, INVENTORY_MOVEMENT_LIMIT).map {
            InventoryMovementView(it.id, it.itemId, label(it.itemId), it.direction, it.quantityMilli, unit(it.itemId), it.occurredAtEpochMillis)
        },
        movementLimit = INVENTORY_MOVEMENT_LIMIT,
    )
}
