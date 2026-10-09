package com.farmos.app

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class DriveDeliveryStatus {
    NOT_CONFIGURED, READY, RUNNING, COMPLETED, OFFLINE, NEEDS_CONSENT,
    APPROVAL_REQUIRED, KEYS_UNAVAILABLE, KEY_ROTATION_PENDING, RETRY_WAIT, FAILED,
}

/** One app-process coordinator: WorkManager and the foreground use the same farm lock and state. */
internal class DriveFarmCoordinator {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val states = ConcurrentHashMap<String, MutableStateFlow<DriveGatewayState>>()

    suspend fun <T> withFarm(farmId: String, block: suspend () -> T): T =
        locks.computeIfAbsent(farmId) { Mutex() }.withLock { block() }

    fun state(farmId: String, initial: () -> DriveGatewayState): MutableStateFlow<DriveGatewayState> =
        states.computeIfAbsent(farmId) { MutableStateFlow(initial()) }

    companion object {
        val shared = DriveFarmCoordinator()
    }
}

/** Non-secret delivery evidence survives process death, scoped to this exact approved connection. */
internal class DriveDeliveryStateStore(context: Context) {
    private val prefs = context.getSharedPreferences("farm_drive_delivery", Context.MODE_PRIVATE)

    fun load(farmId: String, config: DriveGatewayConfig?): DriveGatewayState {
        if (config == null) return DriveGatewayState(deliveryStatus = DriveDeliveryStatus.NOT_CONFIGURED)
        val scope = scope(farmId, config)
        val recorded = prefs.getString("${scope}_status", null)
            ?.let { runCatching { DriveDeliveryStatus.valueOf(it) }.getOrNull() }
            ?: DriveDeliveryStatus.READY
        val interrupted = recorded == DriveDeliveryStatus.RUNNING
        val status = if (interrupted) DriveDeliveryStatus.RETRY_WAIT else recorded
        return DriveGatewayState(
            config = config,
            deliveryStatus = status,
            authNeeded = status == DriveDeliveryStatus.NEEDS_CONSENT,
            lastSyncEpochMillis = prefs.getLong("${scope}_success", 0).takeIf { it > 0 },
            lastError = if (interrupted) "The previous Drive attempt did not finish. Waiting to retry."
                else prefs.getString("${scope}_error", null),
            nextAttemptEpochMillis = prefs.getLong("${scope}_retry", 0).takeIf { it > 0 },
        )
    }

    fun save(farmId: String, config: DriveGatewayConfig, state: DriveGatewayState) {
        val scope = scope(farmId, config)
        check(prefs.edit()
            .putString("${scope}_status", state.deliveryStatus.name)
            .putLong("${scope}_success", state.lastSyncEpochMillis ?: 0)
            .putString("${scope}_error", state.lastError)
            .putLong("${scope}_retry", state.nextAttemptEpochMillis ?: 0)
            .commit()) { "Drive delivery state could not be saved on this device" }
    }

    private fun scope(farmId: String, config: DriveGatewayConfig): String =
        driveCursorScope(farmId, config) + "_" + sha256Hex(
            "${config.connectedAtEpochMillis}\u0000${config.approvedByAccountId}\u0000${config.approvedDeviceId}"
                .toByteArray(Charsets.UTF_8),
        )
}
