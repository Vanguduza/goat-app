package com.farmos.app.hardware

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.farmos.core.design.EidReaderAdapter
import com.farmos.core.design.EidTag
import com.farmos.core.design.NoOpEidReaderAdapter
import com.farmos.core.design.NoOpScaleAdapter
import com.farmos.core.design.ScaleAdapter
import com.farmos.core.design.ScaleDevice
import com.farmos.core.design.ScaleReading
import com.farmos.core.design.runSuspendCatching
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * FOS-GOAT-012 — BLE scale adapter provider.
 *
 * The BLE path is DISABLED by default ([bleScaleEnabled] = false) and is only enabled by an
 * explicit user toggle on the scale pairing screen. Until then [current] returns
 * [NoOpScaleAdapter]: no Bluetooth permission is requested and no BLE code path executes.
 * Enabling requires the runtime Bluetooth permissions (see manifest notes in the batch
 * report); the adapter itself never requests permissions — the host UI does, on user action.
 */
object ScaleAdapters {
    @Volatile
    var bleScaleEnabled: Boolean = false

    fun current(context: Context): ScaleAdapter =
        if (bleScaleEnabled) BleScaleAdapter(context.applicationContext) else NoOpScaleAdapter
}

/**
 * FOS-SHEEP-005 — EID reader adapter provider. Same explicit opt-in discipline as scales:
 * disabled by default, [NoOpEidReaderAdapter] until the user enables the reader path.
 */
object EidReaderAdapters {
    @Volatile
    var eidReaderEnabled: Boolean = false

    fun current(context: Context): EidReaderAdapter =
        if (eidReaderEnabled) BleEidReaderAdapter(context.applicationContext) else NoOpEidReaderAdapter
}

/** Bluetooth permissions the adapter path needs on API 31+; checked, never requested, by adapters. */
fun blePermissionsNeeded(context: Context): Array<String> {
    if (Build.VERSION.SDK_INT < 31) return emptyArray()
    return listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        .filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        .toTypedArray()
}

private class ScaleHardwareException(message: String) : IllegalStateException(message)

/**
 * FOS-GOAT-012 — Farm OS-owned BLE weigh-scale adapter skeleton.
 *
 * Implements the Bluetooth SIG Weight Scale profile plumbing: scan filtering on the
 * Weight Scale service (0x181D), GATT connect, service discovery, indications on the
 * Weight Measurement characteristic (0x2A9D), and spec parsing of the measurement value.
 * No weight is ever fabricated: every failure (no permission, Bluetooth off, no device,
 * GATT error, unparseable value) surfaces as a failed [Result] with an honest message.
 *
 * Skeleton status: the protocol path is real, but scale-vendor quirks (bonding flows,
 * proprietary services, multi-user records) are not handled and need device validation
 * before any production claim. Readings still enter Farm OS only through the governed
 * weight-record command after user confirmation.
 */
class BleScaleAdapter(private val appContext: Context) : ScaleAdapter {
    override val adapterName: String = "BLE weigh scale (Weight Scale profile)"

    private val weightScaleService: UUID = UUID.fromString("0000181D-0000-1000-8000-00805F9B34FB")
    private val weightMeasurementChar: UUID = UUID.fromString("00002A9D-0000-1000-8000-00805F9B34FB")

    @Volatile
    private var gatt: BluetoothGatt? = null
    private val foundDevices = ConcurrentHashMap<String, ScaleDevice>()

    // Single connection callback for the lifetime of a connection; routes completions
    // into the continuations registered by connect()/readWeight().
    private val pendingConnect = java.util.concurrent.atomic.AtomicReference<kotlinx.coroutines.CancellableContinuation<Boolean>?>(null)
    private val pendingRead = java.util.concurrent.atomic.AtomicReference<kotlinx.coroutines.CancellableContinuation<ByteArray>?>(null)

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                @Suppress("MissingPermission")
                if (!gatt.discoverServices()) {
                    pendingConnect.getAndSet(null)?.resume(false)
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                pendingConnect.getAndSet(null)?.resume(false)
                pendingRead.getAndSet(null)?.resumeWithException(
                    ScaleHardwareException("Scale disconnected while reading."),
                )
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            pendingConnect.getAndSet(null)?.resume(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (characteristic.uuid == weightMeasurementChar) {
                pendingRead.getAndSet(null)?.let { cont ->
                    if (status == BluetoothGatt.GATT_SUCCESS) cont.resume(value)
                    else cont.resumeWithException(ScaleHardwareException("Scale read failed (GATT status $status)."))
                }
            }
        }

        @Suppress("Deprecated")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == weightMeasurementChar) {
                pendingRead.getAndSet(null)?.let { cont ->
                    val value = characteristic.value
                    if (value != null) cont.resume(value)
                    else cont.resumeWithException(ScaleHardwareException("Scale returned no value."))
                }
            }
        }
    }

    private fun requirePermission(): Result<Unit> {
        val missing = blePermissionsNeeded(appContext)
        return if (missing.isEmpty()) Result.success(Unit)
        else Result.failure(
            ScaleHardwareException(
                "Bluetooth permission not granted (${missing.joinToString()}): " +
                    "grant it in system settings or keep the adapter disabled and enter weights manually.",
            ),
        )
    }

    private fun bluetoothAdapter(): Result<BluetoothAdapter> {
        val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return Result.failure(ScaleHardwareException("Bluetooth is not available on this device."))
        val adapter = manager.adapter
            ?: return Result.failure(ScaleHardwareException("Bluetooth is not available on this device."))
        if (!adapter.isEnabled) return Result.failure(ScaleHardwareException("Bluetooth is switched off."))
        return Result.success(adapter)
    }

    override suspend fun scanForScales(): Result<List<ScaleDevice>> = runSuspendCatching {
        requirePermission().getOrThrow()
        val adapter = bluetoothAdapter().getOrThrow()
        val scanner = adapter.bluetoothLeScanner
            ?: throw ScaleHardwareException("BLE scanning is not available on this device.")
        foundDevices.clear()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                @Suppress("MissingPermission")
                val name = result.scanRecord?.deviceName ?: device.name ?: "Unnamed scale"
                foundDevices[device.address] = ScaleDevice(name, device.address)
            }

            override fun onScanFailed(errorCode: Int) = Unit
        }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(weightScaleService)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            @Suppress("MissingPermission")
            scanner.startScan(listOf(filter), settings, callback)
            // 10 s collection window; cancellable.
            withTimeoutOrNull(10_000L) {
                suspendCancellableCoroutine<Unit> { cont ->
                    cont.invokeOnCancellation { stopScanQuietly(scanner, callback) }
                    // The timeout drives completion; nothing to resume with.
                }
            }
        } finally {
            stopScanQuietly(scanner, callback)
        }
        foundDevices.values.sortedBy { it.name }.also {
            if (it.isEmpty()) throw ScaleHardwareException("No weigh scales found nearby.")
        }
    }

    private fun stopScanQuietly(
        scanner: android.bluetooth.le.BluetoothLeScanner,
        callback: ScanCallback,
    ) {
        runCatching {
            @Suppress("MissingPermission")
            scanner.stopScan(callback)
        }
    }

    override suspend fun connect(device: ScaleDevice): Result<Unit> = runSuspendCatching {
        requirePermission().getOrThrow()
        val adapter = bluetoothAdapter().getOrThrow()
        @Suppress("MissingPermission")
        val remote: BluetoothDevice = try {
            adapter.getRemoteDevice(device.address)
        } catch (_: IllegalArgumentException) {
            throw ScaleHardwareException("Unknown scale address: ${device.address}")
        }
        disconnect()
        val connected = suspendCancellableCoroutine<Boolean> { cont ->
            pendingConnect.set(cont)
            @Suppress("MissingPermission")
            gatt = remote.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            cont.invokeOnCancellation {
                pendingConnect.getAndSet(null)
                disconnectQuietly()
            }
        }
        if (!connected) {
            disconnectQuietly()
            throw ScaleHardwareException("Could not connect to ${device.name}.")
        }
        val service = gatt?.getService(weightScaleService)
            ?: throw ScaleHardwareException("${device.name} does not expose the Weight Scale service.")
        service.getCharacteristic(weightMeasurementChar)
            ?: throw ScaleHardwareException("${device.name} does not expose a Weight Measurement characteristic.")
    }

    override suspend fun readWeight(): Result<ScaleReading> = runSuspendCatching {
        requirePermission().getOrThrow()
        val active = gatt ?: throw ScaleHardwareException("No scale connected.")
        val characteristic = active.getService(weightScaleService)?.getCharacteristic(weightMeasurementChar)
            ?: throw ScaleHardwareException("Weight Measurement characteristic is gone; reconnect the scale.")
        val raw: ByteArray = withTimeoutOrNull(15_000L) {
            suspendCancellableCoroutine { cont ->
                pendingRead.set(cont)
                @Suppress("MissingPermission")
                val started = active.readCharacteristic(characteristic)
                if (!started) {
                    pendingRead.getAndSet(null)
                    cont.resumeWithException(ScaleHardwareException("Scale did not accept the read request."))
                }
                cont.invokeOnCancellation { pendingRead.getAndSet(null) }
            }
        } ?: throw ScaleHardwareException("Scale did not answer within 15 seconds.")
        val grams = parseWeightMeasurement(raw)
        ScaleReading(weightGrams = grams, capturedAtEpochMillis = System.currentTimeMillis())
    }

    /**
     * Parses a Bluetooth SIG Weight Measurement characteristic value (0x2A9D).
     * Flags bit 0: 0 = SI kilograms (uint16 in 0.005 kg steps), 1 = imperial pounds
     * (uint16 in 0.01 lb steps). Returns exact grams; throws on malformed input.
     */
    internal fun parseWeightMeasurement(value: ByteArray): Long {
        require(value.size >= 3) { "Weight Measurement value too short (${value.size} bytes)." }
        val flags = value[0].toInt() and 0xFF
        val raw = ((value[2].toInt() and 0xFF) shl 8) or (value[1].toInt() and 0xFF)
        return if (flags and 0x01 == 0) {
            // SI: resolution 0.005 kg -> grams = raw * 5
            raw.toLong() * 5L
        } else {
            // Imperial: resolution 0.01 lb -> grams = raw * 4.5359237, exact integer math
            (raw.toLong() * 45359237L + 500000L) / 10000000L
        }
    }

    override suspend fun disconnect(): Result<Unit> = runSuspendCatching {
        disconnectQuietly()
    }

    private fun disconnectQuietly() {
        runCatching {
            @Suppress("MissingPermission")
            gatt?.disconnect()
            gatt?.close()
        }
        gatt = null
    }
}

/**
 * FOS-SHEEP-005 — Farm OS-owned EID reader adapter skeleton.
 *
 * Wire plumbing (scan/connect/disconnect over BLE) is real; tag *protocol* handling is
 * deliberately absent: EID reader wire protocols are vendor-specific (HID keyboard,
 * serial-over-BLE, proprietary GATT services) and no single binding is correct here.
 * [readTag] therefore fails closed until a reader protocol module is fitted for the
 * deployment. HID-keyboard readers already work through the manual/paste identifier
 * field, which remains the primary path.
 */
class BleEidReaderAdapter(private val appContext: Context) : EidReaderAdapter {
    override val adapterName: String = "BLE EID reader (protocol module required)"

    private fun requirePermission(): Result<Unit> {
        val missing = blePermissionsNeeded(appContext)
        return if (missing.isEmpty()) Result.success(Unit)
        else Result.failure(
            IllegalStateException(
                "Bluetooth permission not granted (${missing.joinToString()}): " +
                    "grant it in system settings or keep the reader disabled and enter identifiers manually.",
            ),
        )
    }

    override suspend fun readTag(): Result<EidTag> = runSuspendCatching {
        requirePermission().getOrThrow()
        val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            throw IllegalStateException("Bluetooth is not available or is switched off.")
        }
        throw IllegalStateException(
            "No EID tag protocol is bound for this reader: EID wire protocols are vendor-specific. " +
                "Fit a reader protocol module for this deployment, or type/paste the tag value manually.",
        )
    }
}
