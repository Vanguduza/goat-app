package com.farmos.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.design.runSuspendCatching
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.model.LocalCommandResult
import com.farmos.data.herd.AttachmentCommands
import com.farmos.domain.ops.AttachTaskAttachment
import com.farmos.domain.ops.AttachmentRules
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps the file on this device, then records its metadata through the governed command. Mirrors
 * attachToAnimal: the task rules and the task itself are checked before anything is written, so a
 * refused file leaves nothing behind. The metadata commits as the shared attachment.attach.v1
 * operation with ownerType "task", so the Drive gateway uploads the bytes under the same
 * content-addressed layout as animal attachments and paired devices replay the metadata as-is.
 */
internal suspend fun attachToTask(
    database: FarmOsDatabase,
    farmId: String,
    store: FileAttachmentStore,
    taskId: String,
    bytes: ByteArray,
    mediaType: String,
    displayName: String,
    context: LocalCommandContext,
): LocalCommandResult {
    val command = AttachTaskAttachment(
        UUID.randomUUID().toString(), taskId, sha256Hex(bytes), bytes.size.toLong(), mediaType, displayName.trim(),
    )
    AttachmentRules.attachTask(command, sourceReadable = true)?.let { error(it) }
    requireNotNull(database.tasks().get(farmId, taskId)) { "Task not found on this farm" }
    withContext(Dispatchers.IO) { store.put(farmId, bytes) }
    return AttachmentCommands(database, farmId).attach(command.asAttachFile(), context)
}

/**
 * FOS-TASK-012 — Task Attachment: the files kept against one task, newest first, with the same
 * on-this-device / available-when-connected honesty as animal attachments. Adding a photo or a PDF
 * goes through the governed AttachTaskAttachment command; only open tasks accept new files, so a
 * completed task keeps the files it was finished with.
 */
@Composable
internal fun TaskAttachmentsHost(
    database: FarmOsDatabase,
    farmId: String,
    taskId: String,
    canAttach: Boolean,
    newContext: () -> LocalCommandContext,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { FileAttachmentStore(File(context.filesDir, "attachments")) }
    var items by remember(farmId, taskId) { mutableStateOf<List<AttachmentView>?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember(taskId) { mutableStateOf<String?>(null) }

    LaunchedEffect(farmId, taskId, version) {
        items = runSuspendCatching {
            withContext(Dispatchers.IO) {
                database.attachments().forOwner(farmId, "task", taskId).map { row ->
                    val bytes = if (row.mediaType.startsWith("image/")) store.read(farmId, row.contentSha256) else null
                    AttachmentView(row, bytes != null || store.has(farmId, row.contentSha256), bytes?.let(::taskAttachmentPreview))
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
                message = runSuspendCatching {
                    // readPicked opens and reads the file, so the source was readable when it returns.
                    val (bytes, type, name) = withContext(Dispatchers.IO) { readPicked(context, uri) }
                    attachToTask(database, farmId, store, taskId, bytes, type, name, newContext())
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

/** Small preview so a list of photos never decodes full-size images. Mirrors FarmAttachments.kt. */
private fun taskAttachmentPreview(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= TASK_ATTACHMENT_PREVIEW_EDGE && bounds.outHeight / (sample * 2) >= TASK_ATTACHMENT_PREVIEW_EDGE) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

private const val TASK_ATTACHMENT_PREVIEW_EDGE = 360
