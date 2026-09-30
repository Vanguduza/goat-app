package com.farmos.data.herd

import com.farmos.core.database.COMMAND_PAYLOAD_KEY
import com.farmos.core.database.OperationApplier
import com.farmos.core.model.LocalCommandContext
import com.farmos.domain.replication.OperationEnvelope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Appliers for the species register, weight and lifecycle-status commands written through
 * [RoomHerdRepository]. A received operation is replayed through the same handler in replay mode, with
 * its original actor, device, identity and business time.
 */
object HerdReplicationAppliers {
    /** Species whose animals are registered through [RoomHerdRepository]; goats have their own repository. */
    val SPECIES = listOf("sheep", "cattle", "rabbit", "poultry")

    val all: Map<String, OperationApplier> = SPECIES.flatMap { species ->
        listOf(
            "$species.register.v1" to replay(species) { herd, op, fields ->
                herd.register(
                    animalId = fields.text("animalId"),
                    tag = fields.text("tag"),
                    name = fields.optional("name"),
                    sex = fields.text("sex"),
                    poultryKindCode = fields.optional("poultryKindCode"),
                    context = context(op),
                )
            },
            "$species.record_weight.v1" to replay(species) { herd, op, fields ->
                herd.recordWeight(
                    animalId = fields.text("animalId"),
                    measurementId = fields.text("measurementId"),
                    weightGrams = fields.getValue("weightGrams").jsonPrimitive.long,
                    measuredAtEpochMillis = fields.getValue("measuredAtEpochMillis").jsonPrimitive.long,
                    context = context(op),
                )
            },
            "$species.set_status.v1" to replay(species) { herd, op, fields ->
                herd.setStatus(fields.text("animalId"), fields.text("status"), context(op))
            },
        )
    }.toMap()

    private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.optional(key: String): String? = get(key)?.jsonPrimitive?.content

    private fun context(op: OperationEnvelope) =
        LocalCommandContext(op.farmId, op.actorId, op.deviceId, op.operationId, op.businessTimeEpochMillis)

    private fun replay(species: String, block: suspend (RoomHerdRepository, OperationEnvelope, JsonObject) -> Unit) =
        OperationApplier { database, op ->
            val fields = Json.parseToJsonElement(op.payload.getValue(COMMAND_PAYLOAD_KEY)).jsonObject
            block(RoomHerdRepository(database, op.farmId, species, replaying = true), op, fields)
        }
}
