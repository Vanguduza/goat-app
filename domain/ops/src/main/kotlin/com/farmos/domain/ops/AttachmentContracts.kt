package com.farmos.domain.ops

import kotlinx.serialization.Serializable

/**
 * Attaches a photo or document to a farm record (owner decision D-015). The file is identified by the
 * SHA-256 of its content, never its name, so the same bytes collapse to one stored file and a corrupted
 * copy is detectable. Only this metadata replicates as `attachment.attach.v1`; the bytes stay on the
 * device that captured them until they are transferred, and other devices show "Available when connected".
 */
@Serializable
data class AttachFile(
    val attachmentId: String,
    val ownerType: String,
    val ownerId: String,
    val contentSha256: String,
    val byteSize: Long,
    val mediaType: String,
    val displayName: String,
)

object AttachmentRules {
    /** Photos and documents up to 20 MiB are kept on the device. */
    const val MAX_BYTES: Long = 20L * 1024 * 1024
    const val MAX_NAME = 200

    /** What a farm record can carry: photos and PDF documents. */
    val MEDIA_TYPES: Set<String> = setOf("image/jpeg", "image/png", "image/webp", "application/pdf")

    /** Records that can carry attachments: animals and tasks (FOS-TASK-012). */
    val OWNER_TYPES: Set<String> = setOf("animal", "task")

    private val sha256 = Regex("^[0-9a-f]{64}$")

    fun attach(command: AttachFile): String? = when {
        command.ownerType !in OWNER_TYPES -> "Attachments can be added to animal and task records only"
        command.ownerId.isBlank() -> "Choose the record to attach to"
        !sha256.matches(command.contentSha256) -> "The file could not be identified"
        command.byteSize <= 0 -> "The file is empty"
        command.byteSize > MAX_BYTES -> "A file is at most 20 MB"
        command.mediaType !in MEDIA_TYPES -> "Attach a JPEG, PNG or WebP photo, or a PDF document"
        command.displayName.isBlank() -> "The file has no name"
        command.displayName.length > MAX_NAME -> "A file name is at most $MAX_NAME characters"
        else -> null
    }

    /**
     * The task create side (FOS-TASK-012). [sourceReadable] is whether the local file could be opened
     * and read; the caller establishes it by reading the bytes, for example through the document picker.
     */
    fun attachTask(command: AttachTaskAttachment, sourceReadable: Boolean): String? = when {
        command.taskId.isBlank() -> "Choose the task to attach to"
        !sourceReadable -> "The file could not be read"
        else -> attach(command.asAttachFile())
    }
}

/**
 * FOS-TASK-012 — attaches a photo or document to a task. The governed create side of a task attachment:
 * identical file identity rules to [AttachFile], fixed to ownerType "task". The repository handler turns
 * it into the shared attachment.attach.v1 operation, so task-owned attachments replicate through the same
 * journal layout and Drive blob path as animal attachments, keyed by content SHA-256, not owner.
 */
@Serializable
data class AttachTaskAttachment(
    val attachmentId: String,
    val taskId: String,
    val contentSha256: String,
    val byteSize: Long,
    val mediaType: String,
    val displayName: String,
) {
    fun asAttachFile(): AttachFile =
        AttachFile(attachmentId, "task", taskId, contentSha256, byteSize, mediaType, displayName)
}
