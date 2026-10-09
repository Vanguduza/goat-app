package com.farmos.core.design

/**
 * FOS-GOAT-012 — ScaleAdapter: optional bounded hardware boundary for BLE weigh scales.
 *
 * The adapter is OPTIONAL: the app is fully functional without any scale hardware and
 * deterministic manual weight entry remains the primary path. An adapter implementation
 * must never write to Room directly — scale readings flow into the existing governed
 * weight-record commands (e.g. `RecordGoatWeight`), which validate and journal them
 * like any manual entry. A reading is advisory until the user confirms it.
 *
 * No Bluetooth permission is requested and no BLE code path executes unless the host
 * explicitly enables the adapter path (see `NoOpScaleAdapter`, the default).
 */
interface ScaleAdapter {
    /** Human-readable adapter name for settings/diagnostics surfaces. */
    val adapterName: String

    /** Lists nearby scales. Fails when no adapter is fitted or Bluetooth is unavailable. */
    suspend fun scanForScales(): Result<List<ScaleDevice>>

    /** Connects to a previously scanned scale. */
    suspend fun connect(device: ScaleDevice): Result<Unit>

    /** Reads one stable weight from the connected scale. Never fabricated. */
    suspend fun readWeight(): Result<ScaleReading>

    /** Releases the connection; safe to call when not connected. */
    suspend fun disconnect()
}

/** A scale discovered by [ScaleAdapter.scanForScales]. */
data class ScaleDevice(
    val name: String,
    val address: String,
)

/** One stable weight reading. Grams are exact integers; conversion happens at the UI boundary. */
data class ScaleReading(
    val weightGrams: Long,
    val capturedAtEpochMillis: Long,
)

/**
 * FOS-GOAT-012 — default adapter: no hardware. Every operation fails closed with an
 * honest message instead of simulating a scale.
 */
object NoOpScaleAdapter : ScaleAdapter {
    override val adapterName: String = "No scale adapter"

    private fun unavailable(): Result<Nothing> =
        Result.failure(IllegalStateException("No scale adapter is fitted: enable the BLE scale path or enter the weight manually."))

    override suspend fun scanForScales(): Result<List<ScaleDevice>> = unavailable()
    override suspend fun connect(device: ScaleDevice): Result<Unit> = unavailable()
    override suspend fun readWeight(): Result<ScaleReading> = unavailable()
    override suspend fun disconnect() = Unit
}

/**
 * FOS-SHEEP-005 — EidReaderAdapter: optional bounded hardware boundary for electronic
 * identification readers (RFID/EID).
 *
 * The adapter is OPTIONAL and advisory: a scanned tag value flows through the exact
 * same domain validation and governed `AssignAnimalIdentifier` command as manual entry.
 * Manual identifier entry remains available and primary. Hardware never has authority
 * over domain truth — farm-scoped uniqueness is enforced by the validator, not the reader.
 *
 * Identifier types "eid" and "rfid" are already listed domain identifier types.
 */
interface EidReaderAdapter {
    /** Human-readable adapter name for settings/diagnostics surfaces. */
    val adapterName: String

    /**
     * Reads one tag. Returns the raw tag value with its kind ("eid" or "rfid").
     * The caller must route the value through identifier validation and the governed
     * assign-identifier command; the adapter performs no persistence or validation.
     */
    suspend fun readTag(): Result<EidTag>
}

/** One tag read. The raw value is untrusted input until validated. */
data class EidTag(
    val rawValue: String,
    val kind: String,
    val readAtEpochMillis: Long,
)

/**
 * FOS-SHEEP-005 — default adapter: no hardware. Fails closed instead of simulating a scan.
 */
object NoOpEidReaderAdapter : EidReaderAdapter {
    override val adapterName: String = "No EID reader adapter"

    override suspend fun readTag(): Result<EidTag> =
        Result.failure(IllegalStateException("No EID reader is fitted: enable the reader path or enter the identifier manually."))
}
