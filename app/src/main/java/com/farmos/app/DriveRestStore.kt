package com.farmos.app

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking

internal const val MAX_DRIVE_OBJECT_BYTES = 32 * 1024 * 1024
private const val MAX_DRIVE_RESPONSE_BYTES = 2 * 1024 * 1024

internal fun requireDriveFolderId(folderId: String): String {
    require(folderId.matches(Regex("[A-Za-z0-9_-]{1,256}"))) { "A valid Google Drive folder ID is required" }
    return folderId
}

internal fun driveParentQuery(folderId: String): String =
    "'${requireDriveFolderId(folderId)}' in parents and trashed = false"

internal fun readDriveBytes(input: InputStream, limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (output.size().toLong() + count > limit) throw IOException("Drive object exceeds the permitted size")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
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
    override suspend fun validateDestination(accountEmail: String) = withContext(Dispatchers.IO) {
        val token = authorizer.accessToken() ?: throw DriveAuthNeededException()
        val id = requireDriveFolderId(folderId())
        val folder = DriveJsonParser.parse(
            driveGet("https://www.googleapis.com/drive/v3/files/$id?fields=id,mimeType,trashed,capabilities(canAddChildren)", token),
        ) as? DriveJson.Obj ?: throw IOException("Drive folder returned an unexpected response")
        require(folder.str("id") == id && folder.str("mimeType") == "application/vnd.google-apps.folder") {
            "Choose an existing Google Drive folder"
        }
        require(folder.map["trashed"] != DriveJson.True) { "The Drive folder is in the bin" }
        require(folder.obj("capabilities")?.map?.get("canAddChildren") == DriveJson.True) {
            "This Google account cannot write to the selected folder"
        }
        validateAccount(token, accountEmail)
    }

    override suspend fun ensureFarmFolder(farmId: String, folderName: String, accountEmail: String): String =
        withContext(Dispatchers.IO) {
            require(farmId.matches(Regex("[A-Za-z0-9-]{1,64}"))) { "Invalid farm identity" }
            require(folderName.isNotBlank() && folderName.length <= 128) { "Choose a Drive folder name of at most 128 characters" }
            val token = authorizer.accessToken() ?: throw DriveAuthNeededException()
            validateAccount(token, accountEmail)
            val query = "mimeType = 'application/vnd.google-apps.folder' and trashed = false and " +
                "appProperties has { key='goat_farm_id' and value='$farmId' }"
            val result = DriveJsonParser.parse(driveGet(
                "https://www.googleapis.com/drive/v3/files?q=" + URLEncoder.encode(query, "UTF-8") + "&fields=files(id)&pageSize=2", token,
            )) as? DriveJson.Obj ?: throw IOException("Drive folder search returned an unexpected response")
            val matches = (result.map["files"] as? DriveJson.Arr)?.items.orEmpty()
            require(matches.size <= 1) { "Multiple backup folders exist for this farm; choose one folder ID in Settings" }
            matches.firstOrNull()?.str("id")?.let { return@withContext requireDriveFolderId(it) }
            val metadata = "{\"name\":\"${jsonEscape(folderName)}\",\"mimeType\":\"application/vnd.google-apps.folder\"," +
                "\"appProperties\":{\"goat_farm_id\":\"$farmId\"}}"
            val connection = (URL("https://www.googleapis.com/drive/v3/files?fields=id").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Authorization", authHeader(token))
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            try {
                connection.outputStream.use { it.write(metadata.toByteArray(Charsets.UTF_8)) }
                if (connection.responseCode == 401) {
                    authorizer.invalidateToken(token)
                    throw DriveAuthNeededException()
                }
                if (connection.responseCode !in 200..299) throw IOException("Drive folder creation failed: HTTP ${connection.responseCode}")
                val created = DriveJsonParser.parse(connection.inputStream.use {
                    readDriveBytes(it, MAX_DRIVE_RESPONSE_BYTES).toString(Charsets.UTF_8)
                }) as? DriveJson.Obj ?: throw IOException("Drive returned an invalid folder")
                requireDriveFolderId(created.str("id") ?: throw IOException("Drive returned no folder ID"))
            } finally { connection.disconnect() }
        }

    private fun validateAccount(token: String, accountEmail: String) {
        val account = DriveJsonParser.parse(
            driveGet("https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)", token),
        ) as? DriveJson.Obj ?: throw IOException("Drive account returned an unexpected response")
        require(account.obj("user")?.str("emailAddress")?.equals(accountEmail.trim(), ignoreCase = true) == true) {
            "The signed-in Google account does not match the selected account"
        }
    }

    override suspend fun list(prefix: String): List<DriveObjectStore.DriveObject> = withContext(Dispatchers.IO) {
        val token = authorizer.accessToken() ?: throw DriveAuthNeededException()
        val found = mutableListOf<DriveObjectStore.DriveObject>()
        var pageToken: String? = null
        do {
            val query = buildString {
                append("https://www.googleapis.com/drive/v3/files?q=")
                append(URLEncoder.encode(driveParentQuery(folderId()), "UTF-8"))
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
        requireDriveSafePayload(path, byteArrayOf())
        val id = findId(path, token) ?: return@withContext null
        val bytes = driveDownload("https://www.googleapis.com/drive/v3/files/$id?alt=media", token)
        requireDriveSafePayload(path, bytes)
        bytes
    }

    override suspend fun putIfAbsent(path: String, bytes: ByteArray, sha256: String): Boolean =
        withContext(Dispatchers.IO) {
            requireDriveSafePayload(path, bytes)
            require(sha256Hex(bytes) == sha256) { "Drive upload checksum does not match its bytes" }
            val token = authorizer.accessToken() ?: throw DriveAuthNeededException()
            val existing = findMeta(path, token)
            if (existing != null) {
                // Idempotent retry: the same content already there counts as success; anything else
                // is refused — a published object is never rewritten.
                val held = driveDownload("https://www.googleapis.com/drive/v3/files/${existing.id}?alt=media", token)
                return@withContext held.contentEquals(bytes)
            }
            driveUploadMultipart(token, path, bytes, sha256)
            // Drive names are not unique. Detect a competing create instead of silently trusting one.
            val created = findMeta(path, token) ?: throw IOException("Drive did not confirm the uploaded object")
            driveDownload("https://www.googleapis.com/drive/v3/files/${created.id}?alt=media", token).contentEquals(bytes)
        }

    private data class FileMeta(val id: String, val sha256: String?)

    private fun findMeta(path: String, token: String): FileMeta? {
        val query = buildString {
            append("https://www.googleapis.com/drive/v3/files?q=")
            append(URLEncoder.encode(driveParentQuery(folderId()) + " and name = '$path'", "UTF-8"))
            append("&fields=files(id,appProperties)")
            append("&pageSize=2")
        }
        val json = DriveJsonParser.parse(driveGet(query.toString(), token)) as? DriveJson.Obj
            ?: throw IOException("Drive lookup returned an unexpected response")
        val files = (json.map["files"] as? DriveJson.Arr)?.items ?: return null
        if (files.isEmpty()) return null
        if (files.size != 1) throw IOException("Drive contains conflicting objects at one journal path")
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
        if (code == 401) {
            connection.disconnect()
            runBlocking { authorizer.invalidateToken(token) }
            throw DriveAuthNeededException()
        }
        if (code !in 200..299) { connection.disconnect(); throw IOException("Drive request failed: HTTP $code") }
        return try {
            connection.inputStream.use { readDriveBytes(it, MAX_DRIVE_RESPONSE_BYTES).toString(Charsets.UTF_8) }
        } finally { connection.disconnect() }
    }

    private fun driveDownload(url: String, token: String): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", authHeader(token))
            connectTimeout = 15_000
            readTimeout = 60_000
        }
        val code = connection.responseCode
        if (code == 401) {
            connection.disconnect()
            runBlocking { authorizer.invalidateToken(token) }
            throw DriveAuthNeededException()
        }
        if (code !in 200..299) { connection.disconnect(); throw IOException("Drive download failed: HTTP $code") }
        return try {
            connection.inputStream.use { readDriveBytes(it, MAX_DRIVE_OBJECT_BYTES) }
        } finally { connection.disconnect() }
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
        if (code == 401) {
            connection.disconnect()
            runBlocking { authorizer.invalidateToken(token) }
            throw DriveAuthNeededException()
        }
        if (code !in 200..299) {
            connection.disconnect()
            throw IOException("Drive upload failed: HTTP $code")
        }
        connection.disconnect()
    }
}
