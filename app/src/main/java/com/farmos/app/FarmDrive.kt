package com.farmos.app

import android.content.Context
import com.farmos.core.database.FarmOsDatabase
import com.farmos.domain.replication.BundleVerdict
import com.farmos.domain.replication.DriveJournalLayout
import com.farmos.domain.replication.MergeClass
import com.farmos.domain.replication.OperationBundle
import com.farmos.domain.replication.OperationEnvelope
import com.farmos.domain.replication.ReplicationTransport
import com.farmos.domain.replication.SequenceRange
import com.farmos.domain.replication.SyncOutcome
import com.farmos.domain.replication.SyncSession
import com.farmos.domain.replication.SyncVector
import com.farmos.domain.replication.TransportKind
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import kotlinx.coroutines.runBlocking

/**
 * Google Drive replication gateway boundary for one farm on this device (FOS-ADMIN-013, FOS-ADMIN-015).
 *
 * Drive is a dumb blob store: it holds immutable operation bundles and content-addressed attachment
 * bytes, nothing else. All authority stays local — operations are sealed before upload, verified on
 * download, and applied through the same deterministic journal ingest ([RoomReplicaEndpoint]) the LAN
 * uses, so receiving an operation twice converges to the same state and a conflicting operation id
 * with different content rejects the bundle instead of overwriting anything.
 *
 * Invariants, enforced in code:
 * - Operation-level journal replication ONLY. The gateway moves [OperationBundle] blobs and attachment
 *   bytes; it never copies or synchronises SQLite database files ([requireDriveSafePayload] refuses
 *   database paths and SQLite magic bytes with a hard error).
 * - Write-once objects: publishing a different bundle at an existing path is refused, so no published
 *   batch is ever rewritten ([DriveObjectStore.putIfAbsent]).
 * - Every journal operation carries one of four replication states, derived deterministically from the
 *   local journal plus the persisted cursor — Saved locally ([DriveOpState.LOCAL_ONLY]), uploaded
 *   ([DriveOpState.SYNCED]), independently verified present on Drive ([DriveOpState.BACKED_UP]), or
 *   failed and awaiting retry ([DriveOpState.FAILED]).
 * - The cursor (per-device upload and verification watermarks, failure state) is persisted in
 *   SharedPreferences, so retry and reconnect resume from the last acknowledged operation and survive
 *   process death.
 * - Attachments travel by content hash only: bytes are kept under their SHA-256, verified on receipt,
 *   and damaged content is rejected without entering local storage.
 *
 * Connecting or disconnecting Drive never deletes farm records on this device.
 */
internal data class DriveGatewayConfig(
    val accountEmail: String,
    val folderId: String,
    val folderName: String,
    val connectedAtEpochMillis: Long,
    val approvedByAccountId: String? = null,
    val approvedDeviceId: String? = null,
)

/**
 * Owner-configured Drive account and folder, farm-scoped. A missing row means Drive is not connected.
 *
 * Stores account/folder identity and an explicit local approver/device binding. Google Play services
 * owns its credential cache; tokens, PINs and recovery secrets never enter these preferences.
 */
internal class DriveConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences(DRIVE_PREFS, Context.MODE_PRIVATE)

    fun get(farmId: String): DriveGatewayConfig? = DriveGatewayConfigCodec.decode(
        mapOf(
            "account" to prefs.getString(DriveConfigKeys.configKey(farmId, "account"), null),
            "folder" to prefs.getString(DriveConfigKeys.configKey(farmId, "folder"), null),
            "folder_name" to prefs.getString(DriveConfigKeys.configKey(farmId, "folder_name"), null),
            "connected_at" to prefs.getLong(DriveConfigKeys.configKey(farmId, "connected_at"), 0).toString(),
            "approved_by" to prefs.getString(DriveConfigKeys.configKey(farmId, "approved_by"), null),
            "approved_device" to prefs.getString(DriveConfigKeys.configKey(farmId, "approved_device"), null),
        ),
    )

    fun save(farmId: String, config: DriveGatewayConfig) {
        val encoded = DriveGatewayConfigCodec.encode(config)
        prefs.edit()
            .putString(DriveConfigKeys.configKey(farmId, "account"), encoded.getValue("account"))
            .putString(DriveConfigKeys.configKey(farmId, "folder"), encoded.getValue("folder"))
            .putString(DriveConfigKeys.configKey(farmId, "folder_name"), encoded.getValue("folder_name"))
            .putLong(DriveConfigKeys.configKey(farmId, "connected_at"), encoded.getValue("connected_at").toLongOrNull() ?: 0)
            .putString(DriveConfigKeys.configKey(farmId, "approved_by"), encoded["approved_by"])
            .putString(DriveConfigKeys.configKey(farmId, "approved_device"), encoded["approved_device"])
            .commit().also { check(it) { "Drive configuration could not be saved" } }
    }

    fun clear(farmId: String) {
        prefs.edit()
            .remove(DriveConfigKeys.configKey(farmId, "account"))
            .remove(DriveConfigKeys.configKey(farmId, "folder"))
            .remove(DriveConfigKeys.configKey(farmId, "folder_name"))
            .remove(DriveConfigKeys.configKey(farmId, "connected_at"))
            .remove(DriveConfigKeys.configKey(farmId, "approved_by"))
            .remove(DriveConfigKeys.configKey(farmId, "approved_device"))
            .commit().also { check(it) { "Drive configuration could not be cleared" } }
    }

    private companion object {
        const val DRIVE_PREFS = "farm_drive"
    }
}

/**
 * Supplies a Google OAuth2 bearer token for Drive. The consent/refresh flow is a bounded platform
 * adapter outside this boundary. [GoogleDriveAuthorizer] delegates consent and token renewal to
 * Google Play services; [NoDriveAuthorizer] is retained for offline/test configurations.
 *
 * Owner lock: tokens are privileged credentials and MUST live in Keystore-backed storage, never in
 * SharedPreferences, never in a file beside the config, and never broadcast. This interface only
 * ever receives a short-lived bearer token in memory; persistence of refresh tokens is the
 * adapter's responsibility under the same lock.
 */
internal interface DriveAuthorizer {
    /** A valid bearer token, or null when the owner has not signed in. Never throws for auth state. */
    suspend fun accessToken(): String?

    /** Clear an invalid token from the platform cache; tokens are never logged or written by GOAT. */
    suspend fun invalidateToken(token: String) = Unit
}

internal object NoDriveAuthorizer : DriveAuthorizer {
    override suspend fun accessToken(): String? = null
}

/** Thrown when Drive cannot be reached because the owner has not signed in. Not a retryable failure. */
internal class DriveAuthNeededException : IOException("Google Drive sign-in is required")

/**
 * The dumb-blob port Drive (or a test double) implements. Paths are the deterministic journal layout;
 * objects are write-once.
 */
internal interface DriveObjectStore {
    /** Verify that the authenticated account can write to this actual folder. */
    suspend fun validateDestination(accountEmail: String) = Unit

    suspend fun ensureFarmFolder(farmId: String, folderName: String, accountEmail: String): String =
        throw IOException("This Drive carrier cannot create a folder")

    data class DriveObject(val path: String, val sizeBytes: Long, val sha256: String?)

    /** Every object whose path starts with [prefix], in path order. */
    suspend fun list(prefix: String): List<DriveObject>

    /** The object's bytes, or null when absent. */
    suspend fun read(path: String): ByteArray?

    /**
     * Stores [bytes] at [path] unless an object is already there. Returns true when the path now
     * holds exactly these bytes (already present with the same content counts as success, so uploads
     * are idempotent); returns false — without overwriting — when a different object occupies the path.
     */
    suspend fun putIfAbsent(path: String, bytes: ByteArray, sha256: String): Boolean
}

private val BUNDLE_PATH = Regex("^GOAT/farms/[A-Za-z0-9-]{1,64}/sync/[A-Za-z0-9-]{1,64}/[0-9]{12}-[0-9]{12}\\.bundle$")
private val ATTACHMENT_PATH = Regex("^GOAT/farms/[A-Za-z0-9-]{1,64}/attachments/[0-9a-f]{64}$")
private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

/**
 * Hard invariant: the Drive gateway moves operation bundles and content-addressed attachment bytes
 * plus signed singleton rotation/device-enrolment prerequisites only. Any other path or SQLite magic bytes is refused —
 * the gateway can never be used to copy or synchronise whole database files.
 */
internal fun requireDriveSafePayload(path: String, bytes: ByteArray) {
    require(BUNDLE_PATH.matches(path) || ATTACHMENT_PATH.matches(path) || DRIVE_ROTATION_BOOTSTRAP_PATH.matches(path)) {
        "Drive gateway refuses path outside the journal layout: $path"
    }
    var matches = bytes.size >= SQLITE_MAGIC.size
    if (matches) {
        for (i in SQLITE_MAGIC.indices) {
            if (bytes[i] != SQLITE_MAGIC[i]) {
                matches = false
                break
            }
        }
    }
    require(!matches) { "Drive gateway refuses SQLite database payloads" }
}

/**
 * Versioned binary encoding of one [OperationBundle] for Drive blobs. The bundle checksum is
 * re-verified on decode, and [RoomReplicaEndpoint] verifies it again on ingest — a damaged blob
 * reads as missing, never as farm data.
 */
internal object DriveBundleCodec {
    private val MAGIC = "GOATBNDL".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    private const val MAX_OPERATIONS = 10_000

    fun encode(bundle: OperationBundle): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { dos ->
            dos.write(MAGIC)
            dos.writeByte(VERSION.toInt())
            dos.writeUTF(bundle.farmId)
            dos.writeUTF(bundle.deviceId)
            dos.writeLong(bundle.fromSequence)
            dos.writeLong(bundle.toSequence)
            dos.writeInt(bundle.protocolVersion)
            dos.writeUTF(bundle.checksum)
            dos.writeInt(bundle.operations.size)
            bundle.operations.forEach { writeOperation(dos, it) }
            dos.flush()
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): OperationBundle {
        DataInputStream(bytes.inputStream()).use { dis ->
            val magic = ByteArray(MAGIC.size)
            dis.readFully(magic)
            require(magic.contentEquals(MAGIC)) { "Not a Farm OS Drive bundle" }
            val version = dis.readByte()
            require(version == VERSION) { "Unsupported Drive bundle version $version" }
            val farmId = dis.readUTF()
            val deviceId = dis.readUTF()
            val from = dis.readLong()
            val to = dis.readLong()
            val protocol = dis.readInt()
            val checksum = dis.readUTF()
            val count = dis.readInt()
            require(count in 1..MAX_OPERATIONS) { "Bundle operation count out of bounds: $count" }
            val operations = List(count) { readOperation(dis) }
            require(dis.read() == -1) { "Trailing bytes after Drive bundle" }
            val bundle = OperationBundle(farmId, deviceId, from, to, operations, protocol, checksum)
            when (val verdict = bundle.verify(farmId)) {
                is BundleVerdict.Rejected -> throw IOException("Drive bundle failed verification: ${verdict.reason}")
                BundleVerdict.Valid -> Unit
            }
            return bundle
        }
    }

    private fun writeOperation(dos: DataOutputStream, op: OperationEnvelope) {
        dos.writeUTF(op.operationId)
        dos.writeUTF(op.farmId)
        dos.writeUTF(op.entityType)
        dos.writeUTF(op.entityId)
        dos.writeUTF(op.actorId)
        dos.writeUTF(op.deviceId)
        dos.writeLong(op.deviceSequence)
        dos.writeLong(op.businessTimeEpochMillis)
        dos.writeLong(op.createdAtEpochMillis)
        val base = op.baseVersion
        dos.writeBoolean(base != null)
        if (base != null) dos.writeLong(base)
        dos.writeUTF(op.operationType)
        dos.writeUTF(op.mergeClass.name)
        dos.writeInt(op.payload.size)
        op.payload.toSortedMap().forEach { (key, value) ->
            dos.writeUTF(key)
            dos.writeUTF(value)
        }
        dos.writeInt(op.protocolVersion)
        dos.writeInt(op.schemaVersion)
        dos.writeUTF(op.provenance)
        dos.writeUTF(op.checksum)
    }

    private fun readOperation(dis: DataInputStream): OperationEnvelope {
        val operationId = dis.readUTF()
        val farmId = dis.readUTF()
        val entityType = dis.readUTF()
        val entityId = dis.readUTF()
        val actorId = dis.readUTF()
        val deviceId = dis.readUTF()
        val deviceSequence = dis.readLong()
        val businessTime = dis.readLong()
        val createdAt = dis.readLong()
        val baseVersion = if (dis.readBoolean()) dis.readLong() else null
        val operationType = dis.readUTF()
        val mergeClass = MergeClass.valueOf(dis.readUTF())
        val payloadSize = dis.readInt()
        require(payloadSize in 0..10_000) { "Operation payload out of bounds" }
        val payload = (0 until payloadSize).associate { dis.readUTF() to dis.readUTF() }
        val protocolVersion = dis.readInt()
        val schemaVersion = dis.readInt()
        val provenance = dis.readUTF()
        val checksum = dis.readUTF()
        return OperationEnvelope(
            operationId = operationId,
            farmId = farmId,
            entityType = entityType,
            entityId = entityId,
            actorId = actorId,
            deviceId = deviceId,
            deviceSequence = deviceSequence,
            businessTimeEpochMillis = businessTime,
            createdAtEpochMillis = createdAt,
            baseVersion = baseVersion,
            operationType = operationType,
            mergeClass = mergeClass,
            payload = payload,
            protocolVersion = protocolVersion,
            schemaVersion = schemaVersion,
            provenance = provenance,
            checksum = checksum,
        )
    }
}

/** One journal operation's Drive replication state, derived deterministically from the local journal. */
internal enum class DriveOpState {
    /** Saved on this device; Drive does not hold it yet. */
    LOCAL_ONLY,
    /** Uploaded to this farm's Drive folder; not yet independently verified. */
    SYNCED,
    /** Confirmed present on Drive by an independent listing after upload. */
    BACKED_UP,
    /** An upload attempt failed; the gateway retries with backoff. */
    FAILED,
}

/**
 * The durable Drive cursor for one farm on this device. Persisted in SharedPreferences so retry and
 * reconnect resume from the last acknowledged operation and survive process death.
 *
 * - [uploadedThrough]: per originating device, the highest sequence this device successfully uploaded.
 * - [verifiedThrough]: per originating device, the highest sequence confirmed present on Drive by an
 *   independent listing after upload (two-phase durability: SYNCED becomes BACKED_UP).
 * - [failedThrough]: per originating device, the highest sequence of a failed upload attempt; cleared
 *   on the next successful sync.
 */
internal data class DriveCursor(
    val uploadedThrough: Map<String, Long> = emptyMap(),
    val verifiedThrough: Map<String, Long> = emptyMap(),
    val failedThrough: Map<String, Long> = emptyMap(),
    val consecutiveFailures: Int = 0,
    val nextAttemptAtEpochMillis: Long = 0,
    val lastError: String? = null,
)

/**
 * Derives one operation's Drive state. Deterministic: the same journal, cursor and connection state
 * always yield the same answer, on any device.
 */
internal fun driveOpStateFor(
    deviceId: String,
    deviceSequence: Long,
    cursor: DriveCursor,
): DriveOpState {
    if (deviceSequence <= (cursor.verifiedThrough[deviceId] ?: 0)) return DriveOpState.BACKED_UP
    if (deviceSequence <= (cursor.uploadedThrough[deviceId] ?: 0)) return DriveOpState.SYNCED
    val failed = cursor.failedThrough[deviceId] ?: 0
    if (failed > (cursor.uploadedThrough[deviceId] ?: 0) && deviceSequence <= failed) return DriveOpState.FAILED
    return DriveOpState.LOCAL_ONLY
}

/** Cursor storage is specific to the farm and the verified Google account/folder destination. */
internal fun driveCursorScope(farmId: String, config: DriveGatewayConfig): String =
    farmId + "_" + sha256Hex((config.accountEmail.trim().lowercase(java.util.Locale.ROOT) + "\u0000" + config.folderId).toByteArray(Charsets.UTF_8))

internal fun confirmedDriveCursor(vector: SyncVector): DriveCursor =
    DriveCursor(uploadedThrough = vector.entries, verifiedThrough = vector.entries)

internal class DriveCursorStore(context: Context) {
    private val prefs = context.getSharedPreferences("farm_drive", Context.MODE_PRIVATE)

    fun load(farmId: String, config: DriveGatewayConfig): DriveCursor {
        val scope = driveCursorScope(farmId, config)
        return DriveCursor(
            uploadedThrough = readMap(scope, "up"), verifiedThrough = readMap(scope, "ver"),
            failedThrough = readMap(scope, "fail"),
            consecutiveFailures = prefs.getInt(key(scope, "n"), 0),
            nextAttemptAtEpochMillis = prefs.getLong(key(scope, "next"), 0),
            lastError = prefs.getString(key(scope, "err"), null),
        )
    }

    fun save(farmId: String, config: DriveGatewayConfig, cursor: DriveCursor) {
        val scope = driveCursorScope(farmId, config)
        check(prefs.edit()
            .putString(key(scope, "up"), writeMap(cursor.uploadedThrough))
            .putString(key(scope, "ver"), writeMap(cursor.verifiedThrough))
            .putString(key(scope, "fail"), writeMap(cursor.failedThrough))
            .putInt(key(scope, "n"), cursor.consecutiveFailures)
            .putLong(key(scope, "next"), cursor.nextAttemptAtEpochMillis)
            .putString(key(scope, "err"), cursor.lastError)
            .commit()) { "Drive progress could not be saved on this device" }
    }

    private fun key(scope: String, name: String) = "drive_cursor_v2_${scope}_$name"

    private fun readMap(scope: String, name: String): Map<String, Long> =
        prefs.getString(key(scope, name), null)?.split(";")?.mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size != 2) return@mapNotNull null
            val mark = parts[1].toLongOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
            parts[0] to mark
        }?.toMap() ?: emptyMap()

    private fun writeMap(map: Map<String, Long>): String =
        map.entries.joinToString(";") { "${it.key}:${it.value}" }
}

/**
 * Google Drive as a [ReplicationTransport]: the same immutable bundles as the LAN, laid out per farm
 * and per device ([DriveJournalLayout]), carried as versioned blobs. Drive is replication and
 * backup; it is never the live database and never an authority — conflicts resolve by the
 * deterministic journal rules in [RoomReplicaEndpoint], exactly as on the LAN.
 */
internal class DriveReplicationTransport(
    private val store: DriveObjectStore,
    private val farmId: String,
    private val online: () -> Boolean = { true },
    private val onPublished: (OperationBundle) -> Unit = {},
) : ReplicationTransport {
    override val kind = TransportKind.GOOGLE_DRIVE
    override fun isAvailable(): Boolean = online()

    override fun remoteVector(farmId: String): SyncVector = runBlocking {
        requireFarm(farmId)
        vectorFor(validatedBundles())
    }

    override fun fetch(farmId: String, ranges: List<SequenceRange>): List<OperationBundle> = runBlocking {
        requireFarm(farmId)
        val bundles = validatedBundles()
        val vector = vectorFor(bundles)
        require(ranges.all { vector.watermark(it.deviceId) >= it.to }) { "Drive no longer holds the requested operations" }
        bundles.filter { bundle ->
            ranges.any { it.deviceId == bundle.deviceId && bundle.toSequence >= it.from && bundle.fromSequence <= it.to }
        }
    }

    override fun publish(farmId: String, bundles: List<OperationBundle>) = runBlocking {
        requireFarm(farmId)
        bundles.forEach { bundle ->
            require(bundle.verify(farmId) == BundleVerdict.Valid) { "Cannot publish an invalid or foreign-farm bundle" }
            val path = DriveJournalLayout.bundlePath(farmId, bundle)
            val bytes = DriveBundleCodec.encode(bundle)
            if (!store.putIfAbsent(path, bytes, sha256Hex(bytes))) {
                throw IOException("Drive refused a conflicting bundle at $path")
            }
            onPublished(bundle)
        }
    }

    private fun requireFarm(candidate: String) {
        require(candidate == farmId) { "Drive transport serves another farm" }
    }

    /** Object names are hints only. Only authenticated, decoded, matching bytes advance a vector. */
    private suspend fun validatedBundles(): List<OperationBundle> {
        val prefix = "GOAT/farms/$farmId/sync/"
        return store.list(prefix).map { readDriveJournalBundle(store, farmId, it) }
    }

    private fun vectorFor(bundles: List<OperationBundle>): SyncVector {
        val devices = mutableMapOf<String, MutableMap<Long, String>>()
        bundles.forEach { bundle ->
            val positions = devices.getOrPut(bundle.deviceId) { mutableMapOf() }
            bundle.operations.forEach { operation ->
                val previous = positions.put(operation.deviceSequence, operation.checksum)
                require(previous == null || previous == operation.checksum) { "Drive contains conflicting device positions" }
            }
        }
        return SyncVector(devices.mapValues { (_, positions) ->
            var mark = 0L
            while (positions.containsKey(mark + 1)) mark++
            mark
        })
    }
}

/** Shared by the strict vector reader and dependency-aware key bootstrap; names never prove content. */
internal suspend fun readDriveJournalBundle(
    store: DriveObjectStore,
    farmId: String,
    obj: DriveObjectStore.DriveObject,
): OperationBundle {
    val prefix = "GOAT/farms/$farmId/sync/"
    require(obj.path.startsWith(prefix) && BUNDLE_PATH.matches(obj.path)) { "Invalid Drive journal path" }
    require(obj.sizeBytes in 0..MAX_DRIVE_OBJECT_BYTES.toLong()) { "Drive bundle exceeds the permitted size" }
    val bytes = store.read(obj.path) ?: throw IOException("A listed Drive bundle is missing")
    val bundle = DriveBundleCodec.decode(bytes)
    require(bundle.verify(farmId) == BundleVerdict.Valid) { "Drive bundle is invalid or belongs to another farm" }
    require(DriveJournalLayout.bundlePath(farmId, bundle) == obj.path) { "Drive bundle does not match its journal path" }
    return bundle
}

internal data class DriveOpCounts(
    val localOnly: Long = 0,
    val synced: Long = 0,
    val backedUp: Long = 0,
    val failed: Long = 0,
)

internal data class DriveGatewayState(
    val config: DriveGatewayConfig? = null,
    val deliveryStatus: DriveDeliveryStatus = DriveDeliveryStatus.READY,
    val authNeeded: Boolean = false,
    val syncing: Boolean = false,
    val lastSyncEpochMillis: Long? = null,
    val lastOutcome: SyncOutcome? = null,
    val lastError: String? = null,
    val nextAttemptEpochMillis: Long? = null,
    val counts: DriveOpCounts? = null,
)
