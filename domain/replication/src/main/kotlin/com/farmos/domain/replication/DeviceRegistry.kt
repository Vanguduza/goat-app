package com.farmos.domain.replication

enum class DeviceStatus { ACTIVE, TEMPORARILY_OFFLINE, RETIRED, LOST_REVOKED }

/**
 * A farm device as the farm knows it. [lastReportedOwnSequence] is the highest sequence the device
 * itself last reported having issued; comparing it with what the farm has incorporated reveals
 * unpublished work. [revokedAfterSequence] is set when the device is revoked or lost: operations it
 * originated above that sequence are refused.
 */
data class FarmDevice(
    val deviceId: String,
    val name: String,
    val status: DeviceStatus,
    val lastReportedOwnSequence: Long = 0,
    val revokedAfterSequence: Long? = null,
    /** Base64 X.509 identity key, when known; used to authenticate the device and wrap farm keys to it. */
    val publicKey: String? = null,
)

/** Whether the farm's known state can be claimed complete. Unknown is never reported as complete. */
enum class FarmCompleteness {
    /** Every non-retired device's reported work has been incorporated. */
    ALL_KNOWN_DEVICES_SYNCHRONISED,

    /** Some known device holds reported work that has not reached this replica yet. */
    KNOWN_STATE_WITH_PENDING_DEVICES,

    /** A lost device may have taken unsynchronised work with it; completeness cannot be proven. */
    UNKNOWN_LOST_DEVICE_DATA,
}

/** Outcome of asking to retire a device normally. */
data class RetirementCheck(val deviceId: String, val unpublishedSequences: LongRange?) {
    val safeToRetire: Boolean get() = unpublishedSequences == null
}

class DeviceRegistry(devices: Collection<FarmDevice> = emptyList()) {
    private val devices = devices.associateBy { it.deviceId }.toMutableMap()

    fun register(device: FarmDevice) {
        devices[device.deviceId] = device
    }

    fun device(deviceId: String): FarmDevice? = devices[deviceId]

    fun all(): List<FarmDevice> = devices.values.sortedBy { it.deviceId }

    fun reportOwnSequence(deviceId: String, sequence: Long) {
        val current = devices[deviceId] ?: return
        devices[deviceId] = current.copy(lastReportedOwnSequence = maxOf(current.lastReportedOwnSequence, sequence))
    }

    /** Operations originated by [deviceId] at [sequence] may enter the farm journal. */
    fun accepts(deviceId: String, sequence: Long): Boolean {
        val device = devices[deviceId] ?: return false
        val cutoff = device.revokedAfterSequence ?: return true
        return sequence <= cutoff
    }

    /** A revoked or lost device may not take part in synchronisation sessions at all. */
    fun maySynchronise(deviceId: String): Boolean {
        val device = devices[deviceId] ?: return false
        return device.status == DeviceStatus.ACTIVE || device.status == DeviceStatus.TEMPORARILY_OFFLINE
    }

    /** Warns before normal retirement when the device reported work the farm has not incorporated. */
    fun checkRetirement(deviceId: String, farmVector: SyncVector): RetirementCheck {
        val device = requireNotNull(devices[deviceId]) { "Unknown device" }
        val incorporated = farmVector.watermark(deviceId)
        val pending = if (device.lastReportedOwnSequence > incorporated) incorporated + 1..device.lastReportedOwnSequence else null
        return RetirementCheck(deviceId, pending)
    }

    fun retire(deviceId: String, farmVector: SyncVector) {
        val device = requireNotNull(devices[deviceId]) { "Unknown device" }
        devices[deviceId] = device.copy(status = DeviceStatus.RETIRED, revokedAfterSequence = farmVector.watermark(deviceId))
    }

    /** Records that a device is lost or revoked; operations above what the farm already holds are refused. */
    fun markLostOrRevoked(deviceId: String, farmVector: SyncVector) {
        val device = requireNotNull(devices[deviceId]) { "Unknown device" }
        devices[deviceId] = device.copy(status = DeviceStatus.LOST_REVOKED, revokedAfterSequence = farmVector.watermark(deviceId))
    }

    fun completeness(farmVector: SyncVector): FarmCompleteness {
        // A lost device may have issued work after its last report; that can never be ruled out.
        if (devices.values.any { it.status == DeviceStatus.LOST_REVOKED }) return FarmCompleteness.UNKNOWN_LOST_DEVICE_DATA
        val pending = devices.values.any {
            it.status != DeviceStatus.RETIRED && it.lastReportedOwnSequence > farmVector.watermark(it.deviceId)
        }
        return if (pending) FarmCompleteness.KNOWN_STATE_WITH_PENDING_DEVICES else FarmCompleteness.ALL_KNOWN_DEVICES_SYNCHRONISED
    }
}
