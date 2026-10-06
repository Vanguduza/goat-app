package com.farmos.data.goat

import com.farmos.core.database.COMMAND_PAYLOAD_KEY
import com.farmos.core.database.OperationApplier
import com.farmos.core.model.LocalCommandContext
import com.farmos.domain.goat.PlanGoatLactation
import com.farmos.domain.goat.AmendGoatIdentity
import com.farmos.domain.goat.RecordGoatBcs
import com.farmos.domain.goat.RecordGoatFamacha
import com.farmos.domain.goat.RecordGoatHeat
import com.farmos.domain.goat.RecordGoatKidding
import com.farmos.domain.goat.RecordGoatMating
import com.farmos.domain.goat.RecordGoatMilk
import com.farmos.domain.goat.RecordGoatPregnancy
import com.farmos.domain.goat.RecordGoatScc
import com.farmos.domain.goat.RecordGoatWeaning
import com.farmos.domain.goat.RecordGoatWeight
import com.farmos.domain.goat.RegisterGoat
import com.farmos.domain.goat.RegisterGoatKid
import com.farmos.domain.goat.SetGoatStatus
import com.farmos.domain.replication.OperationEnvelope
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Appliers for every goat command: a received operation is decoded and replayed through the same
 * [RoomGoatRepository] handler, with its original actor, device, identity and business time, in replay
 * mode so it is not journalled a second time.
 */
object GoatReplicationAppliers {
    private val json = Json { encodeDefaults = true }

    val all: Map<String, OperationApplier> = mapOf(
        "goat.amend_identity.v1" to replay { goats, op -> goats.amendIdentity(decode<AmendGoatIdentity>(op), context(op)) },
        "goat.plan_lactation.v1" to replay { goats, op -> goats.planLactation(decode<PlanGoatLactation>(op), context(op)) },
        "goat.record_bcs.v1" to replay { goats, op -> goats.recordBcs(decode<RecordGoatBcs>(op), context(op)) },
        "goat.record_famacha.v1" to replay { goats, op -> goats.recordFamacha(decode<RecordGoatFamacha>(op), context(op)) },
        "goat.record_heat.v1" to replay { goats, op -> goats.recordHeat(decode<RecordGoatHeat>(op), context(op)) },
        "goat.record_kidding.v1" to replay { goats, op -> goats.recordKidding(decode<RecordGoatKidding>(op), context(op)) },
        "goat.record_mating.v1" to replay { goats, op -> goats.recordMating(decode<RecordGoatMating>(op), context(op)) },
        "goat.record_milk.v1" to replay { goats, op -> goats.recordMilk(decode<RecordGoatMilk>(op), context(op)) },
        "goat.record_pregnancy.v1" to replay { goats, op -> goats.recordPregnancy(decode<RecordGoatPregnancy>(op), context(op)) },
        "goat.record_scc.v1" to replay { goats, op -> goats.recordScc(decode<RecordGoatScc>(op), context(op)) },
        "goat.record_weaning.v1" to replay { goats, op -> goats.recordWeaning(decode<RecordGoatWeaning>(op), context(op)) },
        "goat.record_weight.v1" to replay { goats, op -> goats.recordWeight(decode<RecordGoatWeight>(op), context(op)) },
        "goat.register.v1" to replay { goats, op -> goats.registerGoat(decode<RegisterGoat>(op), context(op)) },
        "goat.register_kid.v1" to replay { goats, op -> goats.registerKid(decode<RegisterGoatKid>(op), context(op)) },
        "goat.set_status.v1" to replay { goats, op -> goats.setStatus(decode<SetGoatStatus>(op), context(op)) },
    )

    private inline fun <reified T> decode(op: OperationEnvelope): T = json.decodeFromString(op.payload.getValue(COMMAND_PAYLOAD_KEY))

    private fun context(op: OperationEnvelope) =
        LocalCommandContext(op.farmId, op.actorId, op.deviceId, op.operationId, op.businessTimeEpochMillis)

    private fun replay(block: suspend (RoomGoatRepository, OperationEnvelope) -> Unit) =
        OperationApplier { database, op -> block(RoomGoatRepository(database, op.farmId, replaying = true), op) }
}
