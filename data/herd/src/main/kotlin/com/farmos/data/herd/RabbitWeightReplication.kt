package com.farmos.data.herd

import com.farmos.core.database.COMMAND_PAYLOAD_KEY
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.OperationApplier
import com.farmos.core.model.LocalCommandContext
import com.farmos.domain.rabbit.RecordRabbitWeight
import com.farmos.domain.replication.OperationEnvelope
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Two historical writers used rabbit.record_weight.v1 with different units and table contracts.
 * Both registration maps share this decoder, so map merge order cannot select the wrong meaning.
 * New generic measurements use an explicit grams v2; the rabbit module's kg v1 remains unchanged.
 */
internal object RabbitWeightReplication {
    const val GRAMS_V2 = "rabbit.record_weight.v2"
    private val json = Json { encodeDefaults = true }

    val legacy = OperationApplier { database, operation ->
        require(operation.operationType == "rabbit.record_weight.v1" && operation.schemaVersion == 1) {
            "Unsupported historical rabbit weight version"
        }
        val raw = operation.payload.getValue(COMMAND_PAYLOAD_KEY)
        val fields = Json.parseToJsonElement(raw).jsonObject
        val grams = fields.keys.containsAll(setOf("measurementId", "weightGrams", "measuredAtEpochMillis"))
        val kilograms = fields.keys.containsAll(setOf("weightId", "weightKg", "weighedAtEpochMillis"))
        require(grams != kilograms) { "Rabbit weight payload must identify exactly one historical unit contract" }
        if (grams) {
            require(fields.keys.none { it in setOf("weightId", "weightKg", "weighedAtEpochMillis") }) {
                "Rabbit weight payload contains mixed unit contracts"
            }
            applyGrams(database, operation, fields)
        } else {
            require(fields.keys.none { it in setOf("measurementId", "weightGrams", "measuredAtEpochMillis") }) {
                "Rabbit weight payload contains mixed unit contracts"
            }
            RoomOpsRepository(database, operation.farmId, replaying = true)
                .recordRabbitWeight(json.decodeFromString<RecordRabbitWeight>(raw), context(operation))
        }
    }

    val gramsV2 = OperationApplier { database, operation ->
        require(operation.operationType == GRAMS_V2 && operation.schemaVersion == 2) {
            "Unsupported grams-based rabbit weight version"
        }
        val fields = Json.parseToJsonElement(operation.payload.getValue(COMMAND_PAYLOAD_KEY)).jsonObject
        applyGrams(database, operation, fields)
    }

    private suspend fun applyGrams(database: FarmOsDatabase, operation: OperationEnvelope, fields: JsonObject) {
        RoomHerdRepository(database, operation.farmId, "rabbit", replaying = true).recordWeight(
            animalId = fields.getValue("animalId").jsonPrimitive.content,
            measurementId = fields.getValue("measurementId").jsonPrimitive.content,
            weightGrams = fields.getValue("weightGrams").jsonPrimitive.long,
            measuredAtEpochMillis = fields.getValue("measuredAtEpochMillis").jsonPrimitive.long,
            context = context(operation),
        )
    }

    private fun context(operation: OperationEnvelope) = LocalCommandContext(
        operation.farmId, operation.actorId, operation.deviceId, operation.operationId, operation.businessTimeEpochMillis,
    )
}
