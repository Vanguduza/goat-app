package com.farmos.data.herd

import com.farmos.core.database.COMMAND_PAYLOAD_KEY
import com.farmos.core.database.OperationApplier
import com.farmos.core.model.LocalCommandContext
import com.farmos.domain.replication.OperationEnvelope
import com.farmos.domain.rabbit.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Reuses original actor/device/time and the same typed handlers. Never journals a received operation twice. */
object RabbitryReplication {
    private val json=Json { encodeDefaults=true }
    val all:Map<String,OperationApplier> = mapOf(
        RabbitryCommands.HOUSING to replay { c,op -> c.setHousing(decode<SetRabbitHousing>(op),context(op)) },
        RabbitryCommands.START_SERVICE to replay { c,op -> c.startService(decode<StartRabbitBuckService>(op),context(op)) },
        RabbitryCommands.END_SERVICE to replay { c,op -> c.endService(decode<EndRabbitBuckService>(op),context(op)) },
        RabbitryCommands.MATING to replay { c,op -> c.recordMating(decode<RecordRabbitPairMating>(op),context(op)) },
        RabbitryCommands.NEST to replay { c,op -> c.assignNest(decode<SetRabbitNestAssignment>(op),context(op)) },
    )
    private inline fun <reified T> decode(op:OperationEnvelope):T=json.decodeFromString(op.payload.getValue(COMMAND_PAYLOAD_KEY))
    private fun context(op:OperationEnvelope)=LocalCommandContext(op.farmId,op.actorId,op.deviceId,op.operationId,op.businessTimeEpochMillis)
    private fun replay(block:suspend (RabbitryCommands,OperationEnvelope)->Unit)=OperationApplier { db,op ->
        block(RabbitryCommands(db,op.farmId,replaying=true),op)
    }
}
