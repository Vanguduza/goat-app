package com.farmos.app

import android.content.Context
import com.farmos.core.database.FarmOsDatabase
import com.farmos.domain.replication.SequenceRange
import com.farmos.domain.replication.SyncSession
import com.farmos.domain.replication.SyncSessionStatus
import com.farmos.domain.replication.SyncVector
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

/** The same checked, resumable Drive pass is used by an open farm and by WorkManager. */
internal class FarmDriveRuntime(
    private val context: Context,
    private val database: FarmOsDatabase,
    private val vault: FarmKeyVault,
    private val farmId: String,
    private val deviceId: String,
    private val attachments: FileAttachmentStore,
    private val authorizer: DriveAuthorizer = GoogleDriveAuthorizer(context) { DriveConfigStore(context).get(farmId)?.accountEmail },
    private val clock: () -> Long = System::currentTimeMillis,
    private val coordinator: DriveFarmCoordinator = DriveFarmCoordinator.shared,
    private val online: () -> Boolean = { driveNetworkAvailable(context) },
    private val openStore: (DriveAuthorizer, () -> String) -> DriveObjectStore = ::DriveRestStore,
) : Closeable {
    private val configStore = DriveConfigStore(context)
    private val cursorStore = DriveCursorStore(context)
    private val deliveryStore = DriveDeliveryStateStore(context)
    private val authority = DriveGatewayAuthority(database, deviceId)
    private val worker = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "goat-farm-drive").apply { isDaemon = true }
    }
    private val mutableState = coordinator.state(farmId) { deliveryStore.load(farmId, configStore.get(farmId)) }
    val state: StateFlow<DriveGatewayState> = mutableState.asStateFlow()
    val googleAuthorizer: GoogleDriveAuthorizer? get() = authorizer as? GoogleDriveAuthorizer

    fun start(intervalSeconds: Long = DRIVE_SYNC_INTERVAL_SECONDS): FarmDriveRuntime {
        worker.scheduleWithFixedDelay({ syncGuarded() }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS)
        return this
    }

    /** Persist local approval only after current storage permission and destination validation. */
    suspend fun connect(actorId: String, accountEmail: String, folderId: String, folderName: String): DriveSetupOutcome =
        withContext(Dispatchers.IO) {
            val outcome = coordinator.withFarm(farmId) {
                authority.requireApprover(farmId, actorId)
                val result = DriveSetupAttempt.connect(
                    farmId, accountEmail, folderName, folderId,
                    googleAuthorizer?.forAccount(accountEmail) ?: authorizer, clock(), openStore,
                )
                if (result is DriveSetupOutcome.Connected) {
                    val approved = authority.approve(farmId, actorId, result.config, configStore)
                    DriveSetupFlags(context).setDismissed(farmId, false)
                    mutableState.value = DriveGatewayState(config = approved, deliveryStatus = DriveDeliveryStatus.READY)
                    deliveryStore.save(farmId, approved, mutableState.value)
                    DriveSetupOutcome.Connected(approved)
                } else result
            }
            if (outcome is DriveSetupOutcome.Connected) {
                DriveBackgroundWork.request(context, farmId)
                requestSync()
            }
            outcome
        }

    /** Disconnect cancels durable delivery and removes transport configuration; farm records remain. */
    suspend fun disconnect(actorId: String) = withContext(Dispatchers.IO) {
        coordinator.withFarm(farmId) {
            authority.requireApprover(farmId, actorId)
            configStore.clear(farmId)
            mutableState.value = DriveGatewayState(deliveryStatus = DriveDeliveryStatus.NOT_CONFIGURED)
            DriveBackgroundWork.cancel(context, farmId)
        }
    }

    fun requestSync() {
        if (!worker.isShutdown) runCatching { worker.execute { syncGuarded() } }
    }

    override fun close() {
        worker.shutdownNow()
    }

    /** No consent UI and no key provisioning are reachable from this entry point. */
    suspend fun synchroniseOnce(): DriveDeliveryStatus = coordinator.withFarm(farmId) {
        runInterruptible(Dispatchers.IO) { syncNow() }
    }

    private fun syncGuarded() {
        if (worker.isShutdown) return
        try {
            runBlocking { synchroniseOnce() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (failure: Exception) {
            mutableState.value = mutableState.value.copy(syncing = false, lastError = failure.message ?: "Drive synchronisation failed")
        }
    }

    private fun syncNow(): DriveDeliveryStatus {
        val config = configStore.get(farmId) ?: run {
            mutableState.value = DriveGatewayState(deliveryStatus = DriveDeliveryStatus.NOT_CONFIGURED)
            return DriveDeliveryStatus.NOT_CONFIGURED
        }
        var cursor = cursorStore.load(farmId, config)
        var offer = emptyList<SequenceRange>()
        try {
            runBlocking { authority.requireCurrent(farmId, config, configStore) }
            val previous = deliveryStore.load(farmId, config)
            if (previous.deliveryStatus == DriveDeliveryStatus.APPROVAL_REQUIRED) {
                return publish(config, DriveDeliveryStatus.APPROVAL_REQUIRED, previous.lastError)
            }
            if (previous.deliveryStatus == DriveDeliveryStatus.NEEDS_CONSENT) {
                return publish(config, DriveDeliveryStatus.NEEDS_CONSENT)
            }
            requireKeys()
            if (!online()) return publish(config, DriveDeliveryStatus.OFFLINE, "Offline. Farm records remain saved on this device.")
            if (clock() < cursor.nextAttemptAtEpochMillis) {
                return publish(config, DriveDeliveryStatus.RETRY_WAIT, cursor.lastError, cursor.nextAttemptAtEpochMillis)
            }
            val attemptAuthorizer = googleAuthorizer?.forAccount(config.accountEmail) ?: authorizer
            // Google Play services owns renewal. A required resolution is reported, never launched.
            if (runBlocking { attemptAuthorizer.accessToken() }.isNullOrBlank()) {
                return publish(config, DriveDeliveryStatus.NEEDS_CONSENT)
            }
            publish(config, DriveDeliveryStatus.RUNNING)
            val carrier = ApprovedDriveStore(openStore(attemptAuthorizer) { config.folderId }) {
                authority.requireCurrent(farmId, config, configStore)
            }
            val store = EncryptedDriveStore(carrier, farmId) { requireKeys().keys }
            val bootstrap = DriveKeyBootstrap(database, carrier, farmId, deviceId, ::requireKeys)
            val endpoint = RoomReplicaEndpoint(database, farmId, deviceId, farmAppliers(vault, deviceId), admission = bootstrap::admitKeyPrerequisites)
            val imported = runBlocking {
                bootstrap.publishAvailable()
                bootstrap.importReadable(endpoint, store)
            }
            val transport = DriveReplicationTransport(store, farmId, onPublished = { bundle ->
                cursor = cursor.copy(uploadedThrough = cursor.uploadedThrough +
                    (bundle.deviceId to maxOf(cursor.uploadedThrough[bundle.deviceId] ?: 0, bundle.toSequence)))
                cursorStore.save(farmId, config, cursor)
            })
            offer = SyncVector().missingFrom(endpoint.vector())
            val before = transport.remoteVector(farmId)
            cursor = confirmedDriveCursor(before)
            cursorStore.save(farmId, config, cursor)
            offer = before.missingFrom(endpoint.vector())
            val exchanged = SyncSession.run(endpoint, transport)
            val outcome = exchanged.copy(pulledOperations = exchanged.pulledOperations + imported.applied,
                duplicateOperations = exchanged.duplicateOperations + imported.duplicates)
            runBlocking { authority.requireCurrent(farmId, config, configStore) }
            if (outcome.status != SyncSessionStatus.COMPLETED || outcome.rejectedReasons.isNotEmpty()) {
                throw IOException(outcome.rejectedReasons.firstOrNull() ?: "This device is not authorised to synchronise")
            }
            runBlocking { bootstrap.publishAvailable() }
            // Re-read authenticated bytes after publishing; upload acknowledgement alone is insufficient.
            cursor = confirmedDriveCursor(transport.remoteVector(farmId))
            cursorStore.save(farmId, config, cursor)
            syncAttachments(store)
            mutableState.value = mutableState.value.copy(
                lastSyncEpochMillis = clock(), lastOutcome = outcome, counts = runBlocking { stateCounts(config) },
            )
            return publish(config, DriveDeliveryStatus.COMPLETED, keepCounts = true)
        } catch (cancelled: CancellationException) {
            recordInterruption(config)
            throw cancelled
        } catch (interrupted: InterruptedException) {
            recordInterruption(config)
            throw interrupted
        } catch (denied: DriveGatewayApprovalException) {
            return publish(config, DriveDeliveryStatus.APPROVAL_REQUIRED, denied.message)
        } catch (pending: FarmKeyRotationPendingException) {
            return publish(config, DriveDeliveryStatus.KEY_ROTATION_PENDING, pending.message)
        } catch (missing: DriveKeysUnavailableException) {
            return publish(config, DriveDeliveryStatus.KEYS_UNAVAILABLE, missing.message)
        } catch (auth: DriveAuthNeededException) {
            return publish(config, DriveDeliveryStatus.NEEDS_CONSENT)
        } catch (failure: Exception) {
            if (!online()) return publish(config, DriveDeliveryStatus.OFFLINE, "Offline. Farm records remain saved on this device.")
            return recordFailure(config, cursor, offer, failure)
        }
    }

    private fun requireKeys(): FarmSecrets = try {
        try {
            vault.secrets(farmId) ?: throw DriveKeysUnavailableException()
        } catch (_: FarmKeyRotationPendingException) {
            runBlocking { database.reconcileFarmKeyRotations(farmId, deviceId, vault) }
            vault.secrets(farmId) ?: throw DriveKeysUnavailableException()
        }
    } catch (pending: FarmKeyRotationPendingException) {
        throw pending
    } catch (missing: DriveKeysUnavailableException) {
        throw missing
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        throw DriveKeysUnavailableException()
    }

    /** The farm lock is still held: only this attempt's current connection may leave RUNNING. */
    private fun recordInterruption(config: DriveGatewayConfig) {
        // SharedPreferences.commit waits for disk. Clear interruption only during this small cleanup,
        // then restore it and propagate the original cancellation even if persistence itself fails.
        val interrupted = Thread.interrupted()
        try {
            if (configStore.get(farmId) == config && mutableState.value.config == config &&
                mutableState.value.deliveryStatus == DriveDeliveryStatus.RUNNING
            ) {
                publish(config, DriveDeliveryStatus.RETRY_WAIT, "Drive delivery was interrupted. Waiting to retry.")
            }
        } catch (_: Exception) {
            // publish updates the live state first; a later load also treats persisted RUNNING as interrupted.
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    private fun publish(
        config: DriveGatewayConfig,
        status: DriveDeliveryStatus,
        error: String? = null,
        retryAt: Long? = null,
        keepCounts: Boolean = false,
    ): DriveDeliveryStatus {
        mutableState.value = mutableState.value.copy(
            config = config, deliveryStatus = status, syncing = status == DriveDeliveryStatus.RUNNING,
            authNeeded = status == DriveDeliveryStatus.NEEDS_CONSENT, lastError = error,
            nextAttemptEpochMillis = retryAt, counts = if (keepCounts) mutableState.value.counts else null,
        )
        deliveryStore.save(farmId, config, mutableState.value)
        return status
    }

    private fun recordFailure(
        config: DriveGatewayConfig,
        cursor: DriveCursor,
        offer: List<SequenceRange>,
        failure: Exception,
    ): DriveDeliveryStatus {
        val failures = (cursor.consecutiveFailures + 1).coerceAtMost(30)
        val delay = minOf(DRIVE_MAX_BACKOFF_MILLIS, DRIVE_BASE_BACKOFF_MILLIS * (1L shl minOf(failures, 10)))
        val failed = cursor.failedThrough.toMutableMap()
        for (range in offer) failed[range.deviceId] = maxOf(failed[range.deviceId] ?: 0, range.to)
        val retryAt = clock() + delay
        val message = failure.message ?: "Drive synchronisation failed"
        cursorStore.save(farmId, config, cursor.copy(
            verifiedThrough = emptyMap(), failedThrough = failed, consecutiveFailures = failures,
            nextAttemptAtEpochMillis = retryAt, lastError = message,
        ))
        return publish(config, DriveDeliveryStatus.FAILED, message, retryAt)
    }

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
            if (row.contentSha256 !in remote) continue
            val bytes = store.read(prefix + row.contentSha256) ?: throw IOException("A listed Drive attachment is missing")
            require(bytes.size.toLong() == row.byteSize && sha256Hex(bytes) == row.contentSha256) { "Drive attachment does not match its metadata" }
            attachments.put(farmId, bytes)
        }
    }

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
            for (op in journal.operationsInRange(farmId, span.deviceId, 1, span.maxSequence)) {
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
