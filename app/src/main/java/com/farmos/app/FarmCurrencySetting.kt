package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.room.withTransaction
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.COMMAND_PAYLOAD_KEY
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

/**
 * Applies a farm currency change received from another device. Field-update semantics: the change with
 * the later business time wins, so a late-arriving older change never overwrites a newer one.
 */
internal val FarmCurrencyApplier = OperationApplier { database, operation ->
    val payload = JSONObject(operation.payload.getValue(COMMAND_PAYLOAD_KEY))
    val code = payload.getString("currencyCode")
    require(payload.getString("farmId") == operation.farmId && FarmCurrency.isRecordable(code)) { "Invalid farm currency change" }
    val current = database.farmSettings().get(operation.farmId)
    if (current == null || current.updatedAtEpochMillis <= operation.businessTimeEpochMillis) {
        database.farmSettings().upsert(FarmSettingsEntity(operation.farmId, code, operation.businessTimeEpochMillis, operation.actorId))
    }
}

/** Appliers for the operations this app can apply on receipt; others stay journalled until theirs exist. */
internal val replicationAppliers: Map<String, OperationApplier> = mapOf(SET_FARM_CURRENCY_COMMAND to FarmCurrencyApplier)

/** The farm currency for a capture screen; null until loaded, so nothing is recorded in a guessed currency. */
@Composable
internal fun rememberFarmCurrency(farmId: String, load: suspend () -> String): State<String?> =
    produceState<String?>(null, farmId) { value = load() }
