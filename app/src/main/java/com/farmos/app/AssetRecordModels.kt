package com.farmos.app

import com.farmos.core.database.FarmOsDatabase
import com.farmos.feature.ops.AssetRecords
import com.farmos.feature.ops.AssetServiceView
import com.farmos.feature.ops.AssetView

private const val ASSET_SERVICE_LIMIT = 500

/**
 * Farm-scoped read model for the read-only asset record pages. Nothing here writes. Per-asset
 * maintenance counts and latest dates are exhaustive aggregates; the maintenance list is the latest
 * [ASSET_SERVICE_LIMIT] rows.
 */
internal suspend fun loadAssetRecords(database: FarmOsDatabase, farmId: String): AssetRecords {
    val assets = database.assets().forFarm(farmId)
    val labels = assets.associate { it.id to "${it.code} · ${it.name}" }
    val maintenance = database.maintenance()
    val summaries = maintenance.summaryByAsset(farmId).associateBy { it.assetId }
    return AssetRecords(
        assets = assets.map { asset ->
            val summary = summaries[asset.id]
            AssetView(asset.id, asset.code, asset.name, asset.kind, summary?.serviceCount ?: 0, summary?.latestEpochDay)
        },
        services = maintenance.recent(farmId, ASSET_SERVICE_LIMIT).map {
            AssetServiceView(it.id, it.assetId, labels[it.assetId] ?: "Asset not on this device", it.title, it.note, it.occurredEpochDay)
        },
        serviceCount = maintenance.count(farmId),
    )
}
