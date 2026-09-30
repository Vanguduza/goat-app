package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.room.withTransaction
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.FarmSettingsEntity
import com.farmos.core.database.journalLocalOperation
import com.farmos.domain.ops.FarmCurrency
import java.util.UUID
import org.json.JSONObject

/** Replicated field update that changes a farm's recording currency. */
internal const val SET_FARM_CURRENCY_COMMAND = "farm.set_currency.v1"

/** The farm's recording currency; a farm management has not configured records in USD. */
internal suspend fun FarmOsDatabase.farmCurrency(farmId: String): String =
    farmSettings().get(farmId)?.currencyCode ?: FarmCurrency.DEFAULT_CODE

/**
 * Sets the farm's recording currency for new records. Existing amounts keep the currency they were
 * recorded in. The setting and its journal entry commit together so the change replicates.
 */
internal suspend fun FarmOsDatabase.setFarmCurrency(
    farmId: String,
    currencyCode: String,
    actorId: String,
    deviceId: String,
    nowEpochMillis: Long = System.currentTimeMillis(),
) {
    require(FarmCurrency.isRecordable(currencyCode)) { "Currency must be an ISO 4217 code" }
    withTransaction {
        farmSettings().upsert(FarmSettingsEntity(farmId, currencyCode, nowEpochMillis, actorId))
        journalLocalOperation(
            operationId = UUID.randomUUID().toString(),
            farmId = farmId,
            entityType = "farm_settings",
            entityId = farmId,
            actorId = actorId,
            deviceId = deviceId,
            businessTimeEpochMillis = nowEpochMillis,
            createdAtEpochMillis = nowEpochMillis,
            baseVersion = null,
            operationType = SET_FARM_CURRENCY_COMMAND,
            payloadJson = JSONObject().put("farmId", farmId).put("currencyCode", currencyCode).toString(),
            schemaVersion = 1,
        )
    }
}

/** The farm currency for a capture screen; null until loaded, so nothing is recorded in a guessed currency. */
@Composable
internal fun rememberFarmCurrency(farmId: String, load: suspend () -> String): State<String?> =
    produceState<String?>(null, farmId) { value = load() }
