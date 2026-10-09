package com.farmos.data.herd

import androidx.room.withTransaction
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.FormularyItemEntity
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.model.LocalCommandResult
import com.farmos.domain.ops.CreateFormularyItem
import com.farmos.domain.ops.OpsValidator
import kotlinx.serialization.json.Json

/** Health §9: entering a product is not evidence of veterinary approval. */
internal class FormularyCommands(
    private val database: FarmOsDatabase,
    private val farmId: String,
    private val json: Json,
    private val journal: OpsCommandJournal,
) {
    suspend fun create(command: CreateFormularyItem, context: LocalCommandContext): LocalCommandResult = database.withTransaction {
        require(context.farmId == farmId) { "Farm context mismatch" }
        // A historical v1 receipt retains what its origin recorded. New v2 records are drafts on
        // every device until a separately governed veterinary-attestation workflow is implemented.
        val legacyApproval =
            database.replication().operation(farmId, context.mutationId)?.operationType == LEGACY_CREATE
        val commandName = if (legacyApproval) LEGACY_CREATE else CREATE_DRAFT
        // v1's DTO default was approved. A v2 draft must also record the absence of approval in
        // its immutable payload; a false v2 approval claim cannot match this canonical command.
        val recorded = if (legacyApproval) command else command.copy(vetApproved = false)
        val validation = if (legacyApproval) OpsValidator.formulary(recorded) else OpsValidator.formularyDraft(recorded)
        validation?.let { error(it) }
        journal.enqueueCommand(json, context, commandName, "formulary_item", command.itemId, 0, recorded) {
            database.formulary().insert(
                FormularyItemEntity(
                    command.itemId, farmId, command.productName.trim(), command.speciesCode, command.vetClass,
                    command.meatWithdrawalDays, command.milkWithdrawalDays, command.eggWithdrawalDays,
                    vetApproved = legacyApproval,
                ),
            )
        }
        LocalCommandResult(context.mutationId, command.itemId, true)
    }

    companion object {
        const val LEGACY_CREATE = "formulary.item_create.v1"
        const val CREATE_DRAFT = "formulary.item_create.v2"
    }
}
