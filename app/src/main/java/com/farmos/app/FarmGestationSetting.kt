package com.farmos.app

import androidx.room.withTransaction
import com.farmos.core.database.COMMAND_PAYLOAD_KEY
import com.farmos.core.database.FarmGestationEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.OperationApplier
import com.farmos.core.database.journalLocalOperation
import com.farmos.domain.access.Permission
import com.farmos.domain.ops.GestationDefaults
import com.farmos.domain.ops.GestationPeriod
import com.farmos.domain.ops.GestationSpecies
import java.util.UUID
import org.json.JSONObject

/** Replicated field update that sets a farm's own gestation period for one species. */
internal const val SET_FARM_GESTATION_COMMAND = "farm.set_gestation.v1"

/** The farm's own gestation periods; species it has not set use [GestationDefaults]. */
internal suspend fun FarmOsDatabase.farmGestationOverrides(farmId: String): Map<GestationSpecies, GestationPeriod> =
    farmGestation().all(farmId).mapNotNull { row ->
        GestationSpecies.of(row.species)?.let { it to GestationPeriod(row.earliestDays, row.typicalDays, row.latestDays) }
    }.toMap()

internal suspend fun FarmOsDatabase.gestationPeriod(farmId: String, species: GestationSpecies): GestationPeriod =
    GestationDefaults.period(species, farmGestationOverrides(farmId))

/** Sets the farm's gestation period for [species]; the setting and its journal entry commit together. */
internal suspend fun FarmOsDatabase.setFarmGestation(
    farmId: String,
    species: GestationSpecies,
    period: GestationPeriod,
    actorId: String,
    deviceId: String,
    nowEpochMillis: Long = System.currentTimeMillis(),
) = withTransaction {
    requireLocalAppPermission(farmId, actorId, deviceId, Permission.MANAGE_FARM_SETTINGS)
    farmGestation().upsert(FarmGestationEntity(farmId, species.code, period.earliestDays, period.typicalDays, period.latestDays, nowEpochMillis, actorId))
    journalLocalOperation(
        operationId = UUID.randomUUID().toString(),
        farmId = farmId,
        entityType = "farm_gestation",
        entityId = "$farmId:${species.code}",
        actorId = actorId,
        deviceId = deviceId,
        businessTimeEpochMillis = nowEpochMillis,
        createdAtEpochMillis = nowEpochMillis,
        baseVersion = null,
        operationType = SET_FARM_GESTATION_COMMAND,
        payloadJson = JSONObject()
            .put("farmId", farmId)
            .put("species", species.code)
            .put("earliestDays", period.earliestDays)
            .put("typicalDays", period.typicalDays)
            .put("latestDays", period.latestDays)
            .toString(),
        schemaVersion = 1,
    )
}

/** Applies a gestation period set on another device; the later business time wins for each species. */
internal val FarmGestationApplier = OperationApplier { database, operation ->
    val payload = JSONObject(operation.payload.getValue(COMMAND_PAYLOAD_KEY))
    require(payload.getString("farmId") == operation.farmId) { "Gestation setting belongs to another farm" }
    val species = requireNotNull(GestationSpecies.of(payload.getString("species"))) { "Unknown gestation species" }
    val period = GestationPeriod(payload.getInt("earliestDays"), payload.getInt("typicalDays"), payload.getInt("latestDays"))
    val current = database.farmGestation().get(operation.farmId, species.code)
    if (current == null || current.updatedAtEpochMillis <= operation.businessTimeEpochMillis) {
        database.farmGestation().upsert(
            FarmGestationEntity(operation.farmId, species.code, period.earliestDays, period.typicalDays, period.latestDays, operation.businessTimeEpochMillis, operation.actorId),
        )
    }
}
