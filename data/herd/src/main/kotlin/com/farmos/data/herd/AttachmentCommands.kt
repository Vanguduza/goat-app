package com.farmos.data.herd

import androidx.room.withTransaction
import com.farmos.core.database.AttachmentEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.journalLocalOperation
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.model.LocalCommandResult
import com.farmos.domain.ops.AttachFile
import com.farmos.domain.ops.AttachmentRules
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Attachment metadata (owner decision D-015), journalled for farm replication. An attachment is a new fact
 * and is never overwritten; the caller stores bytes before local metadata commits. Received metadata may
 * precede its bytes and remains an admitted historical operation.
 */
class AttachmentCommands(
    private val database: FarmOsDatabase,
    private val farmId: String,
    /** Replay must match an immutable operation already admitted to this farm's journal. */
    private val replaying: Boolean = false,
    private val json: Json = Json { encodeDefaults = true },
) {
    suspend fun attach(command: AttachFile, context: LocalCommandContext): LocalCommandResult {
        AttachmentRules.attach(command)?.let { error(it) }
        require(context.farmId == farmId) { "Farm context mismatch" }
        database.withTransaction {
            val payloadJson = json.encodeToString(command)
            val permission = OpsCommandPermissions.requiredFor(ATTACH, payloadJson)
            if (!replaying) database.requireLocalCommandAuthority(context, permission)
            val original = database.replication().operation(farmId, context.mutationId)
            if (original != null) {
                requireOriginalCommand(original, context, ATTACH, "attachment", command.attachmentId, {
                    json.decodeFromString<AttachFile>(it) == command
                })
                if (database.commandAlreadyApplied(original, replaying)) return@withTransaction
            } else {
                require(!replaying) { "A received change must be journalled before it is applied" }
            }
            if (!replaying) when (command.ownerType) {
                "animal" -> requireNotNull(database.animals().get(farmId, command.ownerId)) { "Animal not found on this farm" }
                "task" -> {
                    // A legitimately admitted attachment may arrive after its task was completed.
                    val task = requireNotNull(database.tasks().get(farmId, command.ownerId)) { "Task not found on this farm" }
                    require(task.status == "open") { "Only open tasks accept new files" }
                }
                else -> error("Attachments can be added to animal and task records only")
            }
            database.attachments().insert(
                AttachmentEntity(
                    command.attachmentId, farmId, command.ownerType, command.ownerId, command.contentSha256, command.byteSize,
                    command.mediaType, command.displayName.trim(), context.occurredAtEpochMillis, context.actorId,
                ),
            )
            if (replaying) return@withTransaction
            database.journalLocalOperation(
                operationId = context.mutationId,
                farmId = farmId,
                entityType = "attachment",
                entityId = command.attachmentId,
                actorId = context.actorId,
                deviceId = context.deviceId,
                businessTimeEpochMillis = context.occurredAtEpochMillis,
                createdAtEpochMillis = System.currentTimeMillis(),
                baseVersion = null,
                operationType = ATTACH,
                payloadJson = payloadJson,
                schemaVersion = 1,
            )
        }
        return LocalCommandResult(context.mutationId, command.attachmentId, true)
    }

    companion object {
        const val ATTACH = "attachment.attach.v1"
    }
}
