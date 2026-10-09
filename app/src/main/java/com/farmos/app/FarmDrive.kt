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
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
)

/**
 * Owner-configured Drive account and folder, farm-scoped. A missing row means Drive is not connected.
 *
 * This stores configuration only — account email and folder identity. OAuth tokens are privileged
 * credentials and live in Keystore-backed storage via the [DriveAuthorizer] adapter, never here.
 */
internal class DriveConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences(DRIVE_PREFS, Context.MODE_PRIVATE)

    fun get(farmId: String): DriveGatewayConfig? = DriveGatewayConfigCodec.decode(
        mapOf(
            "account" to prefs.getString(DriveConfigKeys.configKey(farmId, "account"), null),
            "folder" to prefs.getString(DriveConfigKeys.configKey(farmId, "folder"), null),
            "folder_name" to prefs.getString(DriveConfigKeys.configKey(farmId, "folder_name"), null),
            "connected_at" to prefs.getLong(DriveConfigKeys.configKey(farmId, "connected_at"), 0).toString(),
        ),
    )

    fun save(farmId: String, config: DriveGatewayConfig) {
        val encoded = DriveGatewayConfigCodec.encode(config)
        prefs.edit()
            .putString(DriveConfigKeys.configKey(farmId, "account"), encoded.getValue("account"))
            .putString(DriveConfigKeys.configKey(farmId, "folder"), encoded.getValue("folder"))
            .putString(DriveConfigKeys.configKey(farmId, "folder_name"), encoded.getValue("folder_name"))
            .putLong(DriveConfigKeys.configKey(farmId, "connected_at"), encoded.getValue("connected_at").toLongOrNull() ?: 0)
            .commit().also { check(it) { "Drive configuration could not be saved" } }
    }

    fun clear(farmId: String) {
        prefs.edit()
            .remove(DriveConfigKeys.configKey(farmId, "account"))
            .remove(DriveConfigKeys.configKey(farmId, "folder"))
            .remove(DriveConfigKeys.configKey(farmId, "folder_name"))
            .remove(DriveConfigKeys.configKey(farmId, "connected_at"))
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
 * only. Any path outside the journal layout, or any payload with SQLite magic bytes, is refused —
 * the gateway can never be used to copy or synchronise whole database files.
 */
internal fun requireDriveSafePayload(path: String, bytes: ByteArray) {
    require(BUNDLE_PATH.matches(path) || ATTACHMENT_PATH.matches(path)) {
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
        return store.list(prefix).map { obj ->
            require(obj.path.startsWith(prefix) && BUNDLE_PATH.matches(obj.path)) { "Invalid Drive journal path" }
            require(obj.sizeBytes in 0..MAX_DRIVE_OBJECT_BYTES.toLong()) { "Drive bundle exceeds the permitted size" }
            val bytes = store.read(obj.path) ?: throw IOException("A listed Drive bundle is missing")
            val bundle = DriveBundleCodec.decode(bytes)
            require(bundle.verify(farmId) == BundleVerdict.Valid) { "Drive bundle is invalid or belongs to another farm" }
            require(DriveJournalLayout.bundlePath(farmId, bundle) == obj.path) { "Drive bundle does not match its journal path" }
            bundle
        }
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

internal data class DriveOpCounts(
    val localOnly: Long = 0,
    val synced: Long = 0,
    val backedUp: Long = 0,
    val failed: Long = 0,
)

internal data class DriveGatewayState(
    val config: DriveGatewayConfig? = null,
    val authNeeded: Boolean = false,
    val syncing: Boolean = false,
    val lastSyncEpochMillis: Long? = null,
    val lastOutcome: SyncOutcome? = null,
    val lastError: String? = null,
    val nextAttemptEpochMillis: Long? = null,
    val counts: DriveOpCounts? = null,
)

/**
 * The Drive gateway runtime for one farm on this device: owns the owner-configured connection,
 * runs the journal sync loop with durable retry, keeps the per-operation replication cursor, and
 * moves attachment bytes by content hash. It needs no Internet to keep the farm running — without
 * a connection, without sign-in, or while backing off, local work continues untouched.
 */
internal class FarmDriveRuntime(
    private val context: Context,
    private val database: FarmOsDatabase,
    private val vault: FarmKeyVault,
    private val farmId: String,
    private val deviceId: String,
    private val attachments: FileAttachmentStore,
    private val authorizer: DriveAuthorizer = GoogleDriveAuthorizer(context) { DriveConfigStore(context).get(farmId)?.accountEmail },
    private val clock: () -> Long = System::currentTimeMillis,
) : Closeable {
    private val connectionMutex = Mutex()
    private val configStore = DriveConfigStore(context)
    private val cursorStore = DriveCursorStore(context)
    private val worker = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "goat-farm-drive").apply { isDaemon = true }
    }
    private val mutableState = MutableStateFlow(DriveGatewayState(config = configStore.get(farmId)))
    val state: StateFlow<DriveGatewayState> = mutableState.asStateFlow()
    val googleAuthorizer: GoogleDriveAuthorizer? get() = authorizer as? GoogleDriveAuthorizer

    /** Starts the periodic Drive sync loop; synchronises every [intervalSeconds] when connected. */
    fun start(intervalSeconds: Long = DRIVE_SYNC_INTERVAL_SECONDS): FarmDriveRuntime {
        worker.scheduleWithFixedDelay({ syncGuarded() }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS)
        return this
    }

    /** Persist a connection only after consent and destination validation have completed. */
    suspend fun connect(accountEmail: String, folderId: String, folderName: String): DriveSetupOutcome =
        withContext(Dispatchers.IO) {
            val outcome = connectionMutex.withLock {
                val result = DriveSetupAttempt.connect(
                    farmId, accountEmail, folderName, folderId, googleAuthorizer?.forAccount(accountEmail) ?: authorizer, clock(),
                )
                if (result is DriveSetupOutcome.Connected) {
                    configStore.save(farmId, result.config)
                    DriveSetupFlags(context).setDismissed(farmId, false)
                    mutableState.value = DriveGatewayState(config = result.config)
                }
                result
            }
            if (outcome is DriveSetupOutcome.Connected) requestSync()
            outcome
        }

    /** Disconnecting removes only the transport configuration, never the farm's local data. */
    fun disconnect() {
        if (!worker.isShutdown) worker.execute {
            runBlocking {
                connectionMutex.withLock {
                    configStore.clear(farmId)
                    mutableState.value = DriveGatewayState()
                }
            }
        }
    }

    /** Synchronises with Drive now when connected and not backing off. */
    fun requestSync() {
        if (!worker.isShutdown) {
            runCatching { worker.execute { syncGuarded() } }
        }
    }

    override fun close() {
        worker.shutdownNow()
    }

    private fun syncGuarded() {
        if (worker.isShutdown) return
        try {
            runBlocking { connectionMutex.withLock { syncNow() } }
        } catch (failure: Exception) {
            mutableState.value = mutableState.value.copy(
                syncing = false,
                lastError = failure.message ?: "Drive synchronisation failed",
            )
        }
    }

    private fun syncNow() {
        val config = configStore.get(farmId) ?: return
        var cursor = cursorStore.load(farmId, config)
        if (clock() < cursor.nextAttemptAtEpochMillis) {
            mutableState.value = mutableState.value.copy(nextAttemptEpochMillis = cursor.nextAttemptAtEpochMillis)
            return
        }
        mutableState.value = mutableState.value.copy(syncing = true, authNeeded = false, lastError = null)
        val store = EncryptedDriveStore(DriveRestStore(authorizer) { config.folderId }, farmId) {
            vault.secretsForLocalFarm(farmId).keys
        }
        val transport = DriveReplicationTransport(store, farmId, onPublished = { bundle ->
            cursor = cursor.copy(uploadedThrough = cursor.uploadedThrough +
                (bundle.deviceId to maxOf(cursor.uploadedThrough[bundle.deviceId] ?: 0, bundle.toSequence)))
            cursorStore.save(farmId, config, cursor)
        })
        val endpoint = RoomReplicaEndpoint(database, farmId, deviceId, farmAppliers(vault, deviceId))
        var offer = emptyList<SequenceRange>()
        try {
            offer = SyncVector().missingFrom(endpoint.vector())
            val before = transport.remoteVector(farmId)
            // This is an authenticated observation. Deleted objects cannot retain old backup marks.
            cursor = confirmedDriveCursor(before)
            cursorStore.save(farmId, config, cursor)
            offer = before.missingFrom(endpoint.vector())
            val outcome = SyncSession.run(endpoint, transport)
            if (outcome.status != com.farmos.domain.replication.SyncSessionStatus.COMPLETED || outcome.rejectedReasons.isNotEmpty()) {
                throw IOException(outcome.rejectedReasons.firstOrNull() ?: "This device is not authorised to synchronise")
            }
            // A second download verifies the bytes after publishing, independently of the upload ACK.
            cursor = confirmedDriveCursor(transport.remoteVector(farmId))
            cursorStore.save(farmId, config, cursor)
            syncAttachments(store)
            mutableState.value = mutableState.value.copy(
                syncing = false, lastSyncEpochMillis = clock(), lastOutcome = outcome,
                lastError = null, nextAttemptEpochMillis = null,
                counts = runBlocking { stateCounts(config) },
            )
        } catch (auth: DriveAuthNeededException) {
            mutableState.value = mutableState.value.copy(syncing = false, authNeeded = true, lastError = null, counts = null)
        } catch (failure: Exception) {
            recordFailure(config, cursor, offer, failure)
            throw failure
        }
    }

    private fun recordFailure(
        config: DriveGatewayConfig,
        cursor: DriveCursor,
        offer: List<SequenceRange>,
        failure: Exception,
    ) {
        val failures = (cursor.consecutiveFailures + 1).coerceAtMost(30)
        val delay = minOf(DRIVE_MAX_BACKOFF_MILLIS, DRIVE_BASE_BACKOFF_MILLIS * (1L shl minOf(failures, 10)))
        val failed = cursor.failedThrough.toMutableMap()
        for (range in offer) failed[range.deviceId] = maxOf(failed[range.deviceId] ?: 0, range.to)
        val retryAt = clock() + delay
        cursorStore.save(farmId, config, cursor.copy(
            // A failed integrity observation cannot certify current Drive availability.
            verifiedThrough = emptyMap(), failedThrough = failed, consecutiveFailures = failures,
            nextAttemptAtEpochMillis = retryAt, lastError = failure.message ?: failure.javaClass.simpleName,
        ))
        mutableState.value = mutableState.value.copy(
            syncing = false, lastError = failure.message ?: "Drive synchronisation failed",
            nextAttemptEpochMillis = retryAt, counts = null,
        )
    }

    /**
     * Attachment bytes by content hash: uploads local bytes Drive lacks, then pulls the bytes this
     * device lacks. Every byte is verified against its SHA-256 on the way in and on the way out.
     */
    private fun syncAttachments(store: DriveObjectStore) = runBlocking {
        val prefix = "GOAT/farms/$farmId/attachments/"
        val remote = store.list(prefix).map { it.path.removePrefix(prefix) }.toSet()
        val local = database.attachments().contents(farmId)
        var uploaded = 0
        for (row in local) {
            if (uploaded >= DRIVE_ATTACHMENTS_PER_SESSION) break
            if (remote.contains(row.contentSha256)) continue
            val bytes = attachments.read(farmId, row.contentSha256) ?: continue
            require(bytes.size.toLong() == row.byteSize) { "Local attachment size does not match its metadata" }
            if (!store.putIfAbsent(prefix + row.contentSha256, bytes, row.contentSha256)) {
                throw IOException("Drive refused a conflicting attachment")
            }
            uploaded++
        }
        for (row in local.filterNot { attachments.has(farmId, it.contentSha256) }.take(DRIVE_ATTACHMENTS_PER_SESSION)) {
            // No copy on Drive yet is normal; a listed but corrupt or missing copy is an explicit failure.
            if (row.contentSha256 !in remote) continue
            val bytes = store.read(prefix + row.contentSha256) ?: throw IOException("A listed Drive attachment is missing")
            require(bytes.size.toLong() == row.byteSize && sha256Hex(bytes) == row.contentSha256) {
                "Drive attachment does not match its metadata"
            }
            attachments.put(farmId, bytes)
        }
    }

    /** Per-operation Drive states for every journalled operation of this farm, for settings UI. */
    suspend fun stateCounts(): DriveOpCounts {
        val config = configStore.get(farmId) ?: return DriveOpCounts()
        return stateCounts(config)
    }

    private suspend fun stateCounts(config: DriveGatewayConfig): DriveOpCounts {
        val cursor = cursorStore.load(farmId, config)
        val journal = database.replication()
        var localOnly = 0L
        var synced = 0L
        var backedUp = 0L
        var failed = 0L
        for (span in journal.sequenceSpans(farmId)) {
            val ops = journal.operationsInRange(farmId, span.deviceId, 1, span.maxSequence)
            for (op in ops) {
                when (driveOpStateFor(op.deviceId, op.deviceSequence, cursor)) {
                    DriveOpState.LOCAL_ONLY -> localOnly++
                    DriveOpState.SYNCED -> synced++
                    DriveOpState.BACKED_UP -> backedUp++
                    DriveOpState.FAILED -> failed++
                }
            }
        }
        return DriveOpCounts(localOnly, synced, backedUp, failed)
    }

    private companion object {
        const val DRIVE_SYNC_INTERVAL_SECONDS = 15 * 60L
        const val DRIVE_BASE_BACKOFF_MILLIS = 30_000L
        const val DRIVE_MAX_BACKOFF_MILLIS = 4 * 60 * 60 * 1000L
        const val DRIVE_ATTACHMENTS_PER_SESSION = 25
    }
}
