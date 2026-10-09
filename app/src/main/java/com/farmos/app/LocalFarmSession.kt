package com.farmos.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.design.AnimalFarmWarningSurface
import com.farmos.domain.access.LocalAccount
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.CancellationException

/** Local entry/session wiring. No farm content or foreground carrier starts from a sign-in snapshot alone. */
@Composable
internal fun LocalFarmSession(app: FarmOsApplication) {
    val directory = remember(app) { LocalFarmDirectory(app.database, app.deviceId, initialKeys = app.keyVault) }
    val joiner = remember(app) {
        FarmJoiner(app.database, app.keyVault, app.deviceId, android.os.Build.MODEL ?: "Farm device")
    }
    LocalFarmSessionHost(
        database = app.database,
        directory = directory,
        deviceId = app.deviceId,
        entry = { message, onSignedIn ->
            LocalFarmEntry(directory, onSignedIn, discovery = app.peerDiscovery, joiner = joiner, initialMessage = message)
        },
        startCarriers = { current -> startLocalFarmCarriers(app, current) },
    ) { current, authority, endSession ->
        val account = current.account
        val membership = account.membership()
        FarmSessionContent(
            app = app, membership = membership, farmName = current.farmName,
            memberships = listOf(membership), farmNames = mapOf(membership.farmId to current.farmName),
            onSwitchFarm = {}, onRequireReauth = endSession,
            onRequireFarmReselection = { message, _ -> endSession(message) },
            onSignOut = { endSession(null) }, actorId = account.accountId, localAuthority = authority,
        )
    }
}

/** Owns sign-in admission, observation and disposal; entry and farm content use their existing visual owners. */
@Composable
internal fun LocalFarmSessionHost(
    database: FarmOsDatabase,
    directory: LocalFarmDirectory,
    deviceId: String,
    entry: @Composable (String?, (LocalAccount, String) -> Unit) -> Unit,
    startCarriers: (CurrentLocalSession) -> Closeable,
    content: @Composable (CurrentLocalSession, LocalSessionAuthority, (String?) -> Unit) -> Unit,
) {
    var signedIn by remember(directory, deviceId) { mutableStateOf<LocalAccount?>(null) }
    var message by remember(directory, deviceId) { mutableStateOf<String?>(null) }
    val account = signedIn
    if (account == null) {
        entry(message) { accepted, _ ->
            message = null
            signedIn = accepted
        }
        return
    }
    // A new successful sign-in replaces this complete owner, including any delayed activity result.
    key(account) {
        val authority = remember(database, directory, deviceId) {
            LocalSessionAuthority(database, directory, account, deviceId)
        }
        val observations = remember(authority) { authority.observe() }
        val status by observations.collectAsState(LocalSessionStatus.Checking)
        val beginCarriers by rememberUpdatedState(startCarriers)
        var carrierFailure by remember(authority) { mutableStateOf<String?>(null) }
        DisposableEffect(authority) {
            onDispose { authority.end() }
        }
        when (val current = status) {
            LocalSessionStatus.Checking -> LocalSessionCheckingScreen()
            is LocalSessionStatus.ReauthenticationRequired -> {
                LocalSessionCheckingScreen()
                LaunchedEffect(authority, current.message) {
                    if (signedIn == account) {
                        message = current.message
                        signedIn = null
                    }
                }
            }
            is LocalSessionStatus.Accepted -> {
                DisposableEffect(authority) {
                    val carriers = try {
                        beginCarriers(current.current)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        carrierFailure = "Synchronisation could not start on this device. Your local farm records are still available. Review Storage and backup."
                        null
                    }
                    onDispose { carriers?.close() }
                }
                Column(Modifier.fillMaxSize()) {
                    carrierFailure?.let { AnimalFarmWarningSurface { Text(it) } }
                    Box(Modifier.weight(1f)) {
                        // Clear nested routes/forms and delayed chooser owners on any role change.
                        // Valid carriers remain outside this authority-sensitive content key.
                        key(current.current.account.role) {
                            content(current.current, authority) { reason ->
                                authority.end(reason)
                                message = reason
                                signedIn = null
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun startLocalFarmCarriers(app: FarmOsApplication, current: CurrentLocalSession): Closeable = startOwnedCarriers { own ->
    val farmId = current.account.farmId
    val attachments = FileAttachmentStore(File(app.filesDir, "attachments"))
    val lan = FarmLanRuntime(
        app.database, app.keyVault, app.peerDiscovery, farmId, current.farmName, app.deviceId,
        attachments = attachments,
    )
    own(Closeable {
        if (app.farmLan === lan) app.farmLan = null
        lan.close()
    })
    lan.start()
    app.farmLan = lan
    val drive = FarmDriveRuntime(app, app.database, app.keyVault, farmId, app.deviceId, attachments)
    own(Closeable {
        if (app.farmDrive === drive) app.farmDrive = null
        drive.close()
    })
    drive.start()
    app.farmDrive = drive
    DriveBackgroundWork.request(app, farmId)
}

/** Own resources before starting them, including when a later carrier cannot open this farm's keys. */
internal fun startOwnedCarriers(start: ((Closeable) -> Unit) -> Unit): Closeable {
    val owned = mutableListOf<Closeable>()
    val resources = Closeable {
        var failure: Exception? = null
        owned.asReversed().forEach { resource ->
            try {
                resource.close()
            } catch (error: Exception) {
                if (failure == null) failure = error else failure?.addSuppressed(error)
            }
        }
        owned.clear()
        failure?.let { throw it }
    }
    try {
        start { owned += it }
    } catch (failure: Exception) {
        try { resources.close() } catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
        throw failure
    }
    return resources
}
