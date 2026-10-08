package com.farmos.data.herd

import androidx.room.withTransaction
import com.farmos.core.database.AttachmentEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.journalLocalOperation
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.model.LocalCommandResult
import com.farmos.domain.ops.AttachFile
import com.farmos.domain.ops.AttachmentRules
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Attachment metadata (owner decision D-015), journalled for farm replication. An attachment is a new fact
 * and is never overwritten; the bytes are stored by the caller before the metadata is committed, so a record
 * on this device never names a file this device does not hold.
 */
class AttachmentCommands(
    private val database: FarmOsDatabase,
    private val farmId: String,
    /** Replays an operation received from another device: domain writes only, it is already journalled. */
    private val replaying: Boolean = false,
    private val json: Json = Json { encodeDefaults = true },
) {
    suspend fun attach(command: AttachFile, context: LocalCommandContext): LocalCommandResult {
        AttachmentRules.attach(command)?.let { error(it) }
        require(context.farmId == farmId) { "Farm context mismatch" }
        if (!replaying) when (command.ownerType) {
            "animal" -> requireNotNull(database.animals().get(farmId, command.ownerId)) { "Animal not found on this farm" }
            "task" -> requireNotNull(database.tasks().get(farmId, command.ownerId)) { "Task not found on this farm" }
            else -> error("Attachments can be added to animal and task records only")
        }
        val row = AttachmentEntity(
            command.attachmentId, farmId, command.ownerType, command.ownerId, command.contentSha256, command.byteSize,
            command.mediaType, command.displayName.trim(), context.occurredAtEpochMillis, context.actorId,
        )
        if (replaying) {
            database.attachments().insert(row)
        } else {
            database.withTransaction {
                database.attachments().insert(row)
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
                    payloadJson = json.encodeToString(command),
                    schemaVersion = 1,
                )
            }
        }
        return LocalCommandResult(context.mutationId, command.attachmentId, true)
    }

    companion object {
        const val ATTACH = "attachment.attach.v1"
    }
}
