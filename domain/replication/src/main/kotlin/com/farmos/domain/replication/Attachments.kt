package com.farmos.domain.replication

/**
 * Attachment metadata. Its identity is the SHA-256 of the content, never the file name, so re-downloads
 * are safe, duplicates collapse and corruption is detectable. Metadata replicates as an ordinary
 * append-only operation and may arrive long before the bytes.
 */
data class AttachmentManifest(
    val contentSha256: String,
    val byteSize: Long,
    val mediaType: String,
    val displayName: String,
    val ownerEntity: EntityKey,
) {
    fun toPayload(): Map<String, String> = mapOf(
        "contentSha256" to contentSha256,
        "byteSize" to byteSize.toString(),
        "mediaType" to mediaType,
        "displayName" to displayName,
        "ownerType" to ownerEntity.entityType,
        "ownerId" to ownerEntity.entityId,
    )

    companion object {
        const val ENTITY_TYPE = "attachment"
        const val OPERATION_TYPE = "attachment.attach"

        fun fromPayload(payload: Map<String, String>): AttachmentManifest = AttachmentManifest(
            contentSha256 = payload.getValue("contentSha256"),
            byteSize = payload.getValue("byteSize").toLong(),
            mediaType = payload.getValue("mediaType"),
            displayName = payload.getValue("displayName"),
            ownerEntity = EntityKey(payload.getValue("ownerType"), payload.getValue("ownerId")),
        )

        fun describe(bytes: ByteArray, mediaType: String, displayName: String, ownerEntity: EntityKey): AttachmentManifest =
            AttachmentManifest(Sha256.hex(bytes), bytes.size.toLong(), mediaType, displayName, ownerEntity)
    }
}

enum class AttachmentAvailability {
    /** The verified bytes are on this device. */
    LOCAL,

    /** The farm knows the attachment exists; its bytes are not on this device yet. */
    AVAILABLE_WHEN_CONNECTED,
}

/** Local content-addressed attachment bytes. Only bytes matching their declared hash are stored. */
class AttachmentStore {
    private val blobs = HashMap<String, ByteArray>()

    fun availability(manifest: AttachmentManifest): AttachmentAvailability =
        if (blobs.containsKey(manifest.contentSha256)) AttachmentAvailability.LOCAL else AttachmentAvailability.AVAILABLE_WHEN_CONNECTED

    /** Stores bytes only when they hash to [manifest]'s identity; corrupted transfers are refused. */
    fun receive(manifest: AttachmentManifest, bytes: ByteArray): Boolean {
        if (Sha256.hex(bytes) != manifest.contentSha256 || bytes.size.toLong() != manifest.byteSize) return false
        blobs[manifest.contentSha256] = bytes.copyOf()
        return true
    }

    fun bytes(contentSha256: String): ByteArray? = blobs[contentSha256]?.copyOf()
}

/** Records an attachment's metadata as a replicated operation on [replica]. */
fun FarmReplica.recordAttachment(manifest: AttachmentManifest, actorId: String, businessTimeEpochMillis: Long): OperationEnvelope =
    record(
        entityType = AttachmentManifest.ENTITY_TYPE,
        entityId = manifest.contentSha256,
        actorId = actorId,
        businessTimeEpochMillis = businessTimeEpochMillis,
        baseVersion = null,
        operationType = AttachmentManifest.OPERATION_TYPE,
        mergeClass = MergeClass.APPEND_ONLY_EVENT,
        payload = manifest.toPayload(),
    )

/** Attachment manifests this replica knows about, whether or not their bytes are local. */
fun FarmReplica.attachmentManifests(): List<AttachmentManifest> =
    history().filter { it.operationType == AttachmentManifest.OPERATION_TYPE }
        .map { AttachmentManifest.fromPayload(it.payload) }
        .distinctBy { it.contentSha256 }
