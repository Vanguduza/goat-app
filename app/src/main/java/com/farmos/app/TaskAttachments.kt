package com.farmos.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.farmos.core.database.FarmOsDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * FOS-TASK-012 — Task Attachment (read side): the files kept against one task, newest first,
 * with the same on-this-device / available-when-connected honesty as animal attachments.
 *
 * The add side is BLOCKED and deliberately absent: AttachmentRules.OWNER_TYPES allows animal
 * owners only, and AttachmentCommands.attach validates the owner as an animal. No task-owner
 * attachment command exists, so this host lists only.
 */
@Composable
internal fun TaskAttachmentsHost(
    database: FarmOsDatabase,
    farmId: String,
    taskId: String,
) {
    val context = LocalContext.current
    val store = remember(context) { FileAttachmentStore(File(context.filesDir, "attachments")) }
    var items by remember(farmId, taskId) { mutableStateOf<List<AttachmentView>?>(null) }
    var message by remember(taskId) { mutableStateOf<String?>(null) }

    LaunchedEffect(farmId, taskId) {
        items = runCatching {
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

    AttachmentsSection(
        items = items,
        canAttach = false,
        busy = false,
        message = message,
        onAddPhoto = {},
        onAddDocument = {},
    )
    Text("Adding files to a task is not supported yet: the attachment command accepts animal records only.")
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
