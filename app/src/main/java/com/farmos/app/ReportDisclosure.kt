package com.farmos.app

import com.farmos.domain.access.AccessDenied
import com.farmos.domain.access.Permission
import java.io.Closeable
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * CSV/PDF destination opening and ACTION_SEND admission use the authenticated local session, even
 * when a document chooser returns before the Room observer has delivered a role/device change.
 */
internal suspend fun <T> withReportDisclosure(
    farmId: String,
    authority: LocalSessionAuthority?,
    action: suspend () -> T,
): T = (authority ?: throw AccessDenied("Sign in with an active farm account before exporting records."))
    .withPermission(farmId, Permission.EXPORT_FARM_DATA, action)

/**
 * Retain stream ownership before the cancellable Room/context handoff returns. Cancellation after a
 * provider opens its descriptor still closes it; no success/log is emitted by this boundary.
 */
internal suspend fun writeAuthorizedReport(
    farmId: String,
    authority: LocalSessionAuthority?,
    open: () -> OutputStream,
    write: (OutputStream) -> Unit,
) = withContext(Dispatchers.IO) {
    val caller = currentCoroutineContext()
    var output: OutputStream? = null
    Closeable { output?.close() }.use {
        // Room's cancellable transaction handoff can finish cancellation before a provider returns
        // its descriptor. Finish only this acquisition handoff so finally always owns that resource.
        // Revalidate the same Room permission and caller immediately before opening; never continue
        // to writing or a UI/success callback after cancellation.
        withContext(NonCancellable) {
            withReportDisclosure(farmId, authority) {
                caller.ensureActive()
                output = open()
            }
        }
        caller.ensureActive()
        write(requireNotNull(output))
    }
}
