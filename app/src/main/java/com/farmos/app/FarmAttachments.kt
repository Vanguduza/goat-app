package com.farmos.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.database.AttachmentEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmSelectionAtoms
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.model.LocalCommandResult
import com.farmos.data.herd.AttachmentCommands
import com.farmos.domain.ops.AttachFile
import com.farmos.domain.ops.AttachmentRules
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Attachment bytes on this device (owner decision D-015), one file per farm named by the SHA-256 of its
 * content. Only bytes that hash to their name are kept or returned, so a damaged file reads as missing
 * rather than as the wrong photo. Removing a farm's metadata never happens here; bytes are never synced as
 * a database file.
 */
internal class FileAttachmentStore(private val root: File) {
    fun has(farmId: String, contentSha256: String): Boolean = file(farmId, contentSha256).isFile

    /** Keeps [bytes] under their hash and returns it; identical content already held is left as it is. */
    fun put(farmId: String, bytes: ByteArray): String {
        val sha = sha256Hex(bytes)
        val target = file(farmId, sha)
        if (target.isFile && sha256Hex(target.readBytes()) == sha) return sha
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.writeBytes(bytes)
        check(temporary.renameTo(target)) { "The file could not be kept on this device" }
        return sha
    }

    /** The verified bytes, or null when they are not on this device or no longer match their hash. */
    fun read(farmId: String, contentSha256: String): ByteArray? =
        file(farmId, contentSha256).takeIf { it.isFile }?.readBytes()?.takeIf { sha256Hex(it) == contentSha256 }

    private fun file(farmId: String, contentSha256: String): File {
        require(farmId.matches(SAFE_ID)) { "Invalid farm id" }
        require(contentSha256.matches(SHA)) { "Invalid content hash" }
        return File(File(root, farmId), contentSha256)
    }

    private companion object {
        val SAFE_ID = Regex("^[A-Za-z0-9-]{1,64}$")
        val SHA = Regex("^[0-9a-f]{64}$")
    }
}

internal fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/**
 * Keeps the file on this device, then records its metadata through the governed command. The rules and the
 * animal are checked before anything is written, so a refused file leaves nothing behind.
 */
internal suspend fun attachToAnimal(
    database: FarmOsDatabase,
    farmId: String,
    store: FileAttachmentStore,
    animalId: String,
    bytes: ByteArray,
    mediaType: String,
    displayName: String,
    context: LocalCommandContext,
): LocalCommandResult {
    val command = AttachFile(UUID.randomUUID().toString(), "animal", animalId, sha256Hex(bytes), bytes.size.toLong(), mediaType, displayName.trim())
    AttachmentRules.attach(command)?.let { error(it) }
    requireNotNull(database.animals().get(farmId, animalId)) { "Animal not found on this farm" }
    withContext(Dispatchers.IO) { store.put(farmId, bytes) }
    return AttachmentCommands(database, farmId).attach(command, context)
}

/** One attachment as shown: whether its bytes are here, and a small preview of a photo that is. */
internal data class AttachmentView(val row: AttachmentEntity, val local: Boolean, val preview: Bitmap?)

internal fun attachmentSizeText(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes bytes"
}

/**
 * FOS-ATOM-016 — Attachment picker and list for one animal: photos from the device's photo picker and PDF
 * documents from its document picker. An attachment whose bytes are on another device shows "Available when
 * connected"; nothing is shown as present that is not.
 */
@Composable
internal fun AttachmentsSection(
    items: List<AttachmentView>?,
    canAttach: Boolean,
    busy: Boolean,
    message: String?,
    onAddPhoto: () -> Unit,
    onAddDocument: () -> Unit,
) {
    FarmOperationalSection("Photos and documents", "Kept on this device and shared with the farm's other devices when connected.") {
        when {
            items == null -> Text("Reading attachments")
            items.isEmpty() -> Text("No photos or documents yet.")
            else -> items.forEach { item ->
                val kind = if (item.row.mediaType == "application/pdf") "PDF document" else "Photo"
                Text(item.row.displayName, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("attachment:${item.row.id}"))
                Text(
                    "$kind · ${attachmentSizeText(item.row.byteSize)} · ${if (item.local) "On this device" else "Available when connected"}",
                    color = AnimalFarmTheme.colors.mutedInk,
                )
                item.preview?.let { Image(it.asImageBitmap(), contentDescription = item.row.displayName, modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)) }
            }
        }
        if (canAttach) {
            TextButton(
                onClick = onAddPhoto,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = AnimalFarmTheme.minimumTouchDp.dp).testTag("${FarmSelectionAtoms.ATTACHMENT_PICKER}:photo"),
            ) { Text("Add photo") }
            TextButton(
                onClick = onAddDocument,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = AnimalFarmTheme.minimumTouchDp.dp).testTag("${FarmSelectionAtoms.ATTACHMENT_PICKER}:document"),
            ) { Text("Add PDF document") }
        }
        message?.let { Text(it, modifier = Modifier.testTag("attachment-message")) }
    }
}

/** Attachments of one animal over the local database and this device's attachment files. */
@Composable
internal fun AnimalAttachmentsHost(database: FarmOsDatabase, farmId: String, animalId: String, canAttach: Boolean, newContext: () -> LocalCommandContext) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { FileAttachmentStore(File(context.filesDir, "attachments")) }
    var items by remember(farmId, animalId) { mutableStateOf<List<AttachmentView>?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember(animalId) { mutableStateOf<String?>(null) }
    LaunchedEffect(farmId, animalId, version) {
        items = runCatching {
            withContext(Dispatchers.IO) {
                database.attachments().forOwner(farmId, "animal", animalId).map { row ->
                    val bytes = if (row.mediaType.startsWith("image/")) store.read(farmId, row.contentSha256) else null
                    AttachmentView(row, bytes != null || store.has(farmId, row.contentSha256), bytes?.let(::attachmentPreview))
                }
            }
        }.getOrElse {
            message = "Attachments could not be read"
            emptyList()
        }
    }
    val onPicked: (Uri?) -> Unit = { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                message = runCatching {
                    val (bytes, type, name) = withContext(Dispatchers.IO) { readPicked(context, uri) }
                    attachToAnimal(database, farmId, store, animalId, bytes, type, name, newContext())
                    version++
                    "Saved on this device: $name"
                }.getOrElse { it.message ?: "The file could not be attached" }
                busy = false
            }
        }
    }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), onPicked)
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), onPicked)
    AttachmentsSection(
        items = items,
        canAttach = canAttach,
        busy = busy,
        message = message,
        onAddPhoto = { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onAddDocument = { documents.launch(arrayOf("application/pdf")) },
    )
}

/** Reads a picked file up to the size limit, with its media type and display name. */
private fun readPicked(context: Context, uri: Uri): Triple<ByteArray, String, String> {
    val resolver = context.contentResolver
    val type = resolver.getType(uri) ?: error("The file type could not be read")
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }?.takeIf { it.isNotBlank() } ?: if (type == "application/pdf") "Document" else "Photo"
    val bytes = requireNotNull(resolver.openInputStream(uri)) { "The file could not be opened" }.use { input ->
        val limited = input.readNBytesCompat(AttachmentRules.MAX_BYTES + 1)
        require(limited.size <= AttachmentRules.MAX_BYTES) { "A file is at most 20 MB" }
        limited
    }
    return Triple(bytes, type, name.take(AttachmentRules.MAX_NAME))
}

private fun java.io.InputStream.readNBytesCompat(limit: Long): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (total < limit) {
        val read = read(buffer, 0, minOf(buffer.size.toLong(), limit - total).toInt())
        if (read < 0) break
        out.write(buffer, 0, read)
        total += read
    }
    return out.toByteArray()
}

/** A small preview so a list of photos never decodes full-size images. */
private fun attachmentPreview(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= PREVIEW_EDGE && bounds.outHeight / (sample * 2) >= PREVIEW_EDGE) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

private const val PREVIEW_EDGE = 360
