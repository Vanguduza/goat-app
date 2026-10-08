package com.farmos.app

import android.content.Context
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.design.runSuspendCatching
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
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

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
 *   and a damaged file reads as missing rather than as the wrong photo.
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
            .apply()
    }

    fun clear(farmId: String) {
        prefs.edit()
            .remove(DriveConfigKeys.configKey(farmId, "account"))
            .remove(DriveConfigKeys.configKey(farmId, "folder"))
            .remove(DriveConfigKeys.configKey(farmId, "folder_name"))
            .remove(DriveConfigKeys.configKey(farmId, "connected_at"))
            .apply()
    }

    private companion object {
        const val DRIVE_PREFS = "farm_drive"
    }
}

/**
 * Supplies a Google OAuth2 bearer token for Drive. The consent/refresh flow is a bounded platform
 * adapter outside this boundary; until one is wired, [NoDriveAuthorizer] reports sign-in as needed
 * and the gateway stays honestly disconnected.
 *
 * Owner lock: tokens are privileged credentials and MUST live in Keystore-backed storage, never in
 * SharedPreferences, never in a file beside the config, and never broadcast. This interface only
 * ever receives a short-lived bearer token in memory; persistence of refresh tokens is the
 * adapter's responsibility under the same lock.
 */
internal interface DriveAuthorizer {
    /** A valid bearer token, or null when the owner has not signed in. Never throws for auth state. */
    suspend fun accessToken(): String?
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

/** Minimal JSON reader for the Drive v3 shapes this gateway uses: objects, arrays, strings, numbers. */
internal sealed interface DriveJson {
    data class Obj(val map: Map<String, DriveJson>) : DriveJson
    data class Arr(val items: List<DriveJson>) : DriveJson
    data class Str(val value: String) : DriveJson
    data class Num(val value: String) : DriveJson
    data object True : DriveJson
    data object False : DriveJson
    data object Null : DriveJson

    fun obj(key: String): Obj? = (this as? Obj)?.map?.get(key) as? Obj
    fun str(key: String): String? = ((this as? Obj)?.map?.get(key) as? Str)?.value
}

internal object DriveJsonParser {
    fun parse(text: String): DriveJson {
        val parser = Parser(text)
        val value = parser.value()
        parser.skipWs()
        require(parser.atEnd()) { "Trailing JSON content" }
        return value
    }

    private class Parser(val text: String) {
        var pos = 0

        fun atEnd(): Boolean = pos >= text.length

        fun skipWs() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        fun value(): DriveJson {
            skipWs()
            require(pos < text.length) { "Unexpected end of JSON" }
            return when (text[pos]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> DriveJson.Str(string())
                't' -> {
                    expect("true")
                    DriveJson.True
                }
                'f' -> {
                    expect("false")
                    DriveJson.False
                }
                'n' -> {
                    expect("null")
                    DriveJson.Null
                }
                else -> DriveJson.Num(number())
            }
        }

        private fun obj(): DriveJson.Obj {
            pos++ // {
            val map = LinkedHashMap<String, DriveJson>()
            skipWs()
            if (peek() == '}') {
                pos++
                return DriveJson.Obj(map)
            }
            while (true) {
                skipWs()
                require(peek() == '"') { "Object key must be a string" }
                val key = string()
                skipWs()
                require(peek() == ':') { "Expected ':'" }
                pos++
                map[key] = value()
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return DriveJson.Obj(map)
                    }
                    else -> throw IllegalArgumentException("Expected ',' or '}'")
                }
            }
        }

        private fun arr(): DriveJson.Arr {
            pos++ // [
            val items = mutableListOf<DriveJson>()
            skipWs()
            if (peek() == ']') {
                pos++
                return DriveJson.Arr(items)
            }
            while (true) {
                items.add(value())
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return DriveJson.Arr(items)
                    }
                    else -> throw IllegalArgumentException("Expected ',' or ']'")
                }
            }
        }

        private fun string(): String {
            require(text[pos] == '"')
            pos++
            val out = StringBuilder()
            while (true) {
                require(pos < text.length) { "Unterminated string" }
                val c = text[pos++]
                if (c == '"') return out.toString()
                if (c == '\\') {
                    require(pos < text.length) { "Unterminated escape" }
                    when (val e = text[pos++]) {
                        '"', '\\', '/' -> out.append(e)
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            require(pos + 4 <= text.length) { "Bad unicode escape" }
                            out.append(text.substring(pos, pos + 4).toInt(16).toChar())
                            pos += 4
                        }
                        else -> throw IllegalArgumentException("Bad escape \\$e")
                    }
                } else {
                    out.append(c)
                }
            }
        }

        private fun number(): String {
            val start = pos
            while (pos < text.length && text[pos] in "-+0123456789.eE") pos++
            require(pos > start) { "Bad number" }
            return text.substring(start, pos)
        }

        private fun expect(word: String) {
            require(text.startsWith(word, pos)) { "Expected $word" }
            pos += word.length
        }

        private fun peek(): Char {
            require(pos < text.length) { "Unexpected end of JSON" }
            return text[pos]
        }
    }
}

private fun jsonEscape(value: String): String = buildString {
    value.forEach { c ->
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
    }
}

/**
 * Google Drive v3 as a [DriveObjectStore], over plain HTTPS with no extra dependencies. File names
 * are the deterministic journal paths; content identity rides in `appProperties.sha256` and is
 * re-verified against the bytes on read. Drive never sees a database file: [requireDriveSafePayload]
 * guards every write, and reads verify the SHA-256 before the bytes are trusted.
 */
internal class DriveRestStore(
    private val authorizer: DriveAuthorizer,
    private val folderId: () -> String,
) : DriveObjectStore {
    override suspend fun list(prefix: String): List<DriveObjectStore.DriveObject> = withContext(Dispatchers.IO) {
        val token = authorizer.accessToken() ?: throw DriveAuthNeededException()
        val found = mutableListOf<DriveObjectStore.DriveObject>()
        var pageToken: String? = null
        do {
            val query = buildString {
                append("https://www.googleapis.com/drive/v3/files?q=")
                append(URLEncoder.encode("'$folderId' in parents and trashed = false", "UTF-8"))
                append("&fields=nextPageToken,files(id,name,size,appProperties)")
                append("&pageSize=1000")
                if (pageToken != null) append("&pageToken=").append(URLEncoder.encode(pageToken, "UTF-8"))
            }
            val json = DriveJsonParser.parse(driveGet(query.toString(), token)) as? DriveJson.Obj
                ?: throw IOException("Drive list returned an unexpected response")
            val files = (json.map["files"] as? DriveJson.Arr)?.items ?: emptyList()
            files.forEach { file ->
                val name = file.str("name") ?: return@forEach
                if (!name.startsWith(prefix)) return@forEach
                found += DriveObjectStore.DriveObject(
                    path = name,
                    sizeBytes = file.str("size")?.toLongOrNull() ?: 0,
                    sha256 = file.obj("appProperties")?.str("sha256"),
                )
            }
            pageToken = json.str("nextPageToken")
        } while (pageToken != null)
        found.sortedBy { it.path }
    }

    override suspend fun read(path: String): ByteArray? = withContext(Dispatchers.IO) {
        val token = authorizer.accessToken() ?: throw DriveAuthNeededException()
        val id = findId(path, token) ?: return@withContext null
        val bytes = driveDownload("https://www.googleapis.com/drive/v3/files/$id?alt=media", token)
        requireDriveSafePayload(path, bytes)
        val hex = sha256Hex(bytes)
        // The bytes must hash to a 64-hex name for attachments; bundles carry their own checksum.
        if (ATTACHMENT_PATH.matches(path)) {
            val expected = path.substringAfterLast("/")
            require(hex == expected) { "Drive attachment failed its content-hash check" }
        }
        bytes
    }

    override suspend fun putIfAbsent(path: String, bytes: ByteArray, sha256: String): Boolean =
        withContext(Dispatchers.IO) {
            requireDriveSafePayload(path, bytes)
            val token = authorizer.accessToken() ?: throw DriveAuthNeededException()
            val existing = findMeta(path, token)
            if (existing != null) {
                // Idempotent retry: the same content already there counts as success; anything else
                // is refused — a published object is never rewritten.
                return@withContext existing.sha256?.equals(sha256, ignoreCase = true) == true
            }
            driveUploadMultipart(token, path, bytes, sha256)
            true
        }

    private data class FileMeta(val id: String, val sha256: String?)

    private fun findMeta(path: String, token: String): FileMeta? {
        val query = buildString {
            append("https://www.googleapis.com/drive/v3/files?q=")
            append(URLEncoder.encode("'$folderId' in parents and name = '$path' and trashed = false", "UTF-8"))
            append("&fields=files(id,appProperties)")
            append("&pageSize=2")
        }
        val json = DriveJsonParser.parse(driveGet(query.toString(), token)) as? DriveJson.Obj
            ?: throw IOException("Drive lookup returned an unexpected response")
        val files = (json.map["files"] as? DriveJson.Arr)?.items ?: return null
        if (files.isEmpty()) return null
        val first = files.first()
        return FileMeta(
            id = first.str("id") ?: throw IOException("Drive lookup returned a file without an id"),
            sha256 = first.obj("appProperties")?.str("sha256"),
        )
    }

    private fun findId(path: String, token: String): String? = findMeta(path, token)?.id

    private fun authHeader(token: String) = "Bearer $token"

    private fun driveGet(url: String, token: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", authHeader(token))
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        val code = connection.responseCode
        if (code == 401 || code == 403) throw DriveAuthNeededException()
        if (code !in 200..299) throw IOException("Drive request failed: HTTP $code")
        return connection.inputStream.bufferedReader().readText()
    }

    private fun driveDownload(url: String, token: String): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", authHeader(token))
            connectTimeout = 15_000
            readTimeout = 60_000
        }
        val code = connection.responseCode
        if (code == 401 || code == 403) throw DriveAuthNeededException()
        if (code !in 200..299) throw IOException("Drive download failed: HTTP $code")
        return connection.inputStream.readBytes()
    }

    private fun driveUploadMultipart(token: String, path: String, bytes: ByteArray, sha256: String) {
        val boundary = "farm_os_${System.currentTimeMillis()}"
        val metadata =
            "{\"name\":\"${jsonEscape(path)}\"," +
                "\"parents\":[\"${jsonEscape(folderId())}\"]," +
                "\"mimeType\":\"application/octet-stream\"," +
                "\"appProperties\":{\"sha256\":\"${jsonEscape(sha256)}\",\"kind\":\"farm_journal\"}}"
        val body = ByteArrayOutputStream().also { out ->
            out.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray(Charsets.UTF_8))
            out.write(metadata.toByteArray(Charsets.UTF_8))
            out.write("\r\n--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray(Charsets.UTF_8))
            out.write(bytes)
            out.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
        }.toByteArray()
        val connection = (URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", authHeader(token))
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            connectTimeout = 15_000
            readTimeout = 120_000
            setFixedLengthStreamingMode(body.size)
        }
        connection.outputStream.use { it.write(body) }
        val code = connection.responseCode
        if (code == 401 || code == 403) throw DriveAuthNeededException()
        if (code !in 200..299) {
            val detail = runCatching { connection.errorStream?.bufferedReader()?.readText() }.getOrNull()
            throw IOException("Drive upload failed: HTTP $code${detail?.let { " $it" } ?: ""}")
        }
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

internal class DriveCursorStore(context: Context) {
    private val prefs = context.getSharedPreferences(DRIVE_PREFS, Context.MODE_PRIVATE)

    fun load(farmId: String): DriveCursor = DriveCursor(
        uploadedThrough = readMap(farmId, "up"),
        verifiedThrough = readMap(farmId, "ver"),
        failedThrough = readMap(farmId, "fail"),
        consecutiveFailures = prefs.getInt(key(farmId, "n"), 0),
        nextAttemptAtEpochMillis = prefs.getLong(key(farmId, "next"), 0),
        lastError = prefs.getString(key(farmId, "err"), null),
    )

    fun save(farmId: String, cursor: DriveCursor) {
        prefs.edit()
            .putString(key(farmId, "up"), writeMap(cursor.uploadedThrough))
            .putString(key(farmId, "ver"), writeMap(cursor.verifiedThrough))
            .putString(key(farmId, "fail"), writeMap(cursor.failedThrough))
            .putInt(key(farmId, "n"), cursor.consecutiveFailures)
            .putLong(key(farmId, "next"), cursor.nextAttemptAtEpochMillis)
            .putString(key(farmId, "err"), cursor.lastError)
            .apply()
    }

    fun clear(farmId: String) {
        prefs.edit()
            .remove(key(farmId, "up"))
            .remove(key(farmId, "ver"))
            .remove(key(farmId, "fail"))
            .remove(key(farmId, "n"))
            .remove(key(farmId, "next"))
            .remove(key(farmId, "err"))
            .apply()
    }

    private fun key(farmId: String, name: String) = "drive_cur_${farmId}_$name"

    private fun readMap(farmId: String, name: String): Map<String, Long> =
        prefs.getString(key(farmId, name), null)
            ?.split(";")
            ?.mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) parts[0] to (parts[1].toLongOrNull() ?: return@mapNotNull null) else null
            }
            ?.toMap() ?: emptyMap()

    private fun writeMap(map: Map<String, Long>): String =
        map.entries.joinToString(";") { "${it.key}:${it.value}" }

    private companion object {
        const val DRIVE_PREFS = "farm_drive"
    }
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
    /** Called for every bundle the transport hands to Drive, so the gateway can advance its cursor. */
    private val onPublished: (OperationBundle) -> Unit = {},
) : ReplicationTransport {
    override val kind = TransportKind.GOOGLE_DRIVE

    override fun isAvailable(): Boolean = online()

    override fun remoteVector(farmId: String): SyncVector = runBlocking {
        check(farmId == this@DriveReplicationTransport.farmId) { "Drive transport serves another farm" }
        val byDevice = listedBundles()
            .groupBy { it.deviceId }
        SyncVector(
            byDevice.mapValues { (_, bundles) ->
                var mark = 0L
                bundles.sortedBy { it.fromSequence }.forEach { bundle ->
                    if (bundle.fromSequence <= mark + 1) mark = maxOf(mark, bundle.toSequence)
                }
                mark
            },
        )
    }

    override fun fetch(farmId: String, ranges: List<SequenceRange>): List<OperationBundle> = runBlocking {
        check(farmId == this@DriveReplicationTransport.farmId) { "Drive transport serves another farm" }
        val wanted = listedBundles()
        val paths = wanted.filter { bundle ->
            ranges.any { it.deviceId == bundle.deviceId && bundle.toSequence >= it.from && bundle.fromSequence <= it.to }
        }.map { DriveJournalLayout.bundlePath(farmId, it) }
        paths.mapNotNull { path ->
            runSuspendCatching {
                val bytes = store.read(path) ?: return@runSuspendCatching null
                DriveBundleCodec.decode(bytes)
            }.getOrNull()
        }
    }

    override fun publish(farmId: String, bundles: List<OperationBundle>) = runBlocking {
        check(farmId == this@DriveReplicationTransport.farmId) { "Drive transport serves another farm" }
        bundles.forEach { bundle ->
            val path = DriveJournalLayout.bundlePath(farmId, bundle)
            val bytes = DriveBundleCodec.encode(bundle)
            val ok = runSuspendCatching { store.putIfAbsent(path, bytes, sha256Hex(bytes)) }.getOrElse { throw it }
            if (!ok) throw IOException("Drive refused bundle $path: a different object occupies the path")
            onPublished(bundle)
        }
    }

    /**
     * Bundle headers parsed from object names only — no downloads — so the vector listing stays
     * cheap. A name that does not parse is ignored here; [fetch] verifies every downloaded bundle.
     */
    private suspend fun listedBundles(): List<ParsedBundle> =
        store.list("GOAT/farms/$farmId/sync/").mapNotNull { obj ->
            val rest = obj.path.removePrefix("GOAT/farms/$farmId/sync/")
            val device = rest.substringBefore("/", "")
            val range = rest.substringAfter("/", "")
            val match = BUNDLE_NAME.matchEntire(range) ?: return@mapNotNull null
            if (device.isEmpty()) return@mapNotNull null
            ParsedBundle(device, match.groupValues[1].toLong(), match.groupValues[2].toLong())
        }

    private data class ParsedBundle(val deviceId: String, val fromSequence: Long, val toSequence: Long)

    private companion object {
        val BUNDLE_NAME = Regex("^([0-9]{12})-([0-9]{12})\\.bundle$")
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
    private val authorizer: DriveAuthorizer = NoDriveAuthorizer,
    private val clock: () -> Long = System::currentTimeMillis,
) : Closeable {
    private val configStore = DriveConfigStore(context)
    private val cursorStore = DriveCursorStore(context)
    private val worker = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "goat-farm-drive").apply { isDaemon = true }
    }
    private val mutableState = MutableStateFlow(DriveGatewayState(config = configStore.get(farmId)))
    val state: StateFlow<DriveGatewayState> = mutableState.asStateFlow()

    /** Starts the periodic Drive sync loop; synchronises every [intervalSeconds] when connected. */
    fun start(intervalSeconds: Long = DRIVE_SYNC_INTERVAL_SECONDS): FarmDriveRuntime {
        worker.scheduleWithFixedDelay({ syncGuarded() }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS)
        return this
    }

    /**
     * Connects the owner's Drive: records the account and folder, keeps any existing cursor so a
     * reconnect resumes from the last acknowledged operation, then synchronises at once. A
     * successful connect also clears the first-run "Drive setup dismissed" flag, so the choice to
     * connect later is the durable one.
     */
    fun connect(accountEmail: String, folderId: String, folderName: String) {
        worker.execute {
            require(accountEmail.isNotBlank() && folderId.isNotBlank()) { "A Drive account and folder are required" }
            val config = DriveGatewayConfig(accountEmail.trim(), folderId.trim(), folderName.trim(), clock())
            configStore.save(farmId, config)
            DriveSetupFlags(context).setDismissed(farmId, false)
            mutableState.value = mutableState.value.copy(config = config, authNeeded = false, lastError = null)
            syncGuarded()
        }
    }

    /**
     * Disconnects Drive for this farm. The configuration is removed; the local journal, the cursor
     * and every farm record stay exactly as they are — disconnecting never deletes farm data.
     */
    fun disconnect() {
        worker.execute {
            configStore.clear(farmId)
            mutableState.value = DriveGatewayState()
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
            syncNow()
        } catch (failure: Exception) {
            mutableState.value = mutableState.value.copy(
                syncing = false,
                lastError = failure.message ?: "Drive synchronisation failed",
            )
        }
    }

    private fun syncNow() {
        val config = configStore.get(farmId)
        if (config == null) return
        val cursor = cursorStore.load(farmId)
        if (clock() < cursor.nextAttemptAtEpochMillis) {
            mutableState.value = mutableState.value.copy(nextAttemptEpochMillis = cursor.nextAttemptAtEpochMillis)
            return
        }
        mutableState.value = mutableState.value.copy(syncing = true, authNeeded = false, lastError = null)
        val store = DriveRestStore(authorizer) { config.folderId }
        val transport = DriveReplicationTransport(store, farmId)
        val endpoint = RoomReplicaEndpoint(database, farmId, deviceId, farmAppliers(vault, deviceId))
        try {
            // Phase 1 — verify: an independent listing confirms what earlier uploads actually hold.
            val before = transport.remoteVector(farmId)
            // Phase 2 — converge: pull what Drive holds that we lack, push what we hold that it lacks.
            val offer = before.missingFrom(endpoint.vector())
            val outcome = SyncSession.run(endpoint, transport)
            // Phase 3 — acknowledge: the post-publish listing advances the upload cursor.
            val after = transport.remoteVector(farmId)
            advanceCursor(cursor, offer, before, after)
            syncAttachments(store)
            val counts = runBlocking { stateCounts(config) }
            mutableState.value = mutableState.value.copy(
                syncing = false,
                lastSyncEpochMillis = clock(),
                lastOutcome = outcome,
                lastError = outcome.rejectedReasons.firstOrNull(),
                nextAttemptEpochMillis = null,
                counts = counts,
            )
        } catch (auth: DriveAuthNeededException) {
            // Sign-in state, not a failure: surface it without consuming the retry budget.
            mutableState.value = mutableState.value.copy(syncing = false, authNeeded = true, lastError = null)
        } catch (failure: Exception) {
            recordFailure(cursor, offer, failure)
            throw failure
        }
    }

    /**
     * Advances the durable cursor. Uploaded marks come from this sync's post-publish listing;
     * verified marks come from the pre-sync listing, so SYNCED only becomes BACKED_UP after an
     * independent read confirms the object. A successful sync clears the failure state.
     */
    private fun advanceCursor(
        cursor: DriveCursor,
        offer: List<SequenceRange>,
        before: SyncVector,
        after: SyncVector,
    ) {
        val uploaded = cursor.uploadedThrough.toMutableMap()
        val verified = cursor.verifiedThrough.toMutableMap()
        for ((device, mark) in after.entries) {
            uploaded[device] = maxOf(uploaded[device] ?: 0, mark)
            val wasUploaded = cursor.uploadedThrough[device] ?: 0
            verified[device] = maxOf(verified[device] ?: 0, minOf(wasUploaded, before.watermark(device)))
        }
        // Ranges we offered but Drive still lacks after this sync stay below the cursor: they will
        // be offered again next time, so the cursor never claims an unacknowledged upload.
        for (range in offer) {
            val mark = after.watermark(range.deviceId)
            if (mark < range.to) uploaded[range.deviceId] = minOf(uploaded[range.deviceId] ?: 0, mark)
        }
        cursorStore.save(
            farmId,
            cursor.copy(
                uploadedThrough = uploaded,
                verifiedThrough = verified,
                failedThrough = emptyMap(),
                consecutiveFailures = 0,
                nextAttemptAtEpochMillis = 0,
                lastError = null,
            ),
        )
    }

    /** Records a failed sync with exponential backoff; the failed ranges surface as FAILED operations. */
    private fun recordFailure(cursor: DriveCursor, offer: List<SequenceRange>, failure: Exception) {
        val failures = cursor.consecutiveFailures + 1
        val delay = minOf(DRIVE_MAX_BACKOFF_MILLIS, DRIVE_BASE_BACKOFF_MILLIS * (1L shl minOf(failures, 10)))
        val failed = cursor.failedThrough.toMutableMap()
        for (range in offer) {
            failed[range.deviceId] = maxOf(failed[range.deviceId] ?: 0, range.to)
        }
        cursorStore.save(
            farmId,
            cursor.copy(
                failedThrough = failed,
                consecutiveFailures = failures,
                nextAttemptAtEpochMillis = clock() + delay,
                lastError = failure.message ?: failure.javaClass.simpleName,
            ),
        )
        mutableState.value = mutableState.value.copy(
            syncing = false,
            lastError = failure.message ?: "Drive synchronisation failed",
            nextAttemptEpochMillis = clock() + delay,
        )
    }

    /**
     * Attachment bytes by content hash: uploads local bytes Drive lacks, then pulls the bytes this
     * device lacks. Every byte is verified against its SHA-256 on the way in and on the way out.
     */
    private fun syncAttachments(store: DriveObjectStore) {
        val prefix = "GOAT/farms/$farmId/attachments/"
        val remote = runBlocking { store.list(prefix) }.map { it.path.removePrefix(prefix) }.toSet()
        val local = runBlocking { database.attachments().contents(farmId) }
        var uploaded = 0
        for (row in local) {
            if (uploaded >= DRIVE_ATTACHMENTS_PER_SESSION) break
            if (remote.contains(row.contentSha256)) continue
            val bytes = attachments.read(farmId, row.contentSha256) ?: continue
            val ok = runBlocking {
                runSuspendCatching { store.putIfAbsent(prefix + row.contentSha256, bytes, row.contentSha256) }
                    .getOrDefault(false)
            }
            if (ok) uploaded++
        }
        runBlocking {
            runSuspendCatching {
                pullMissingAttachments(
                    database,
                    farmId,
                    attachments,
                    limit = DRIVE_ATTACHMENTS_PER_SESSION,
                    fetch = { sha, max -> store.read(prefix + sha)?.takeIf { it.size <= max } },
                )
            }
        }
    }

    /** Per-operation Drive states for every journalled operation of this farm, for settings UI. */
    suspend fun stateCounts(): DriveOpCounts {
        val config = configStore.get(farmId) ?: return DriveOpCounts()
        return stateCounts(config)
    }

    private suspend fun stateCounts(config: DriveGatewayConfig): DriveOpCounts {
        val cursor = cursorStore.load(farmId)
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
