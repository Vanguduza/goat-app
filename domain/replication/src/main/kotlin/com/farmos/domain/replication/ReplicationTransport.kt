package com.farmos.domain.replication

/**
 * One way operations can travel: the farm LAN, Google Drive, or any future carrier. Every transport
 * moves the same immutable bundles; the replica never knows or cares which transport delivered one.
 */
interface ReplicationTransport {
    val kind: TransportKind

    /** False when the carrier cannot be reached right now. Local work continues regardless. */
    fun isAvailable(): Boolean

    /** The remote side's incorporated watermarks for [farmId]. */
    fun remoteVector(farmId: String): SyncVector

    /** Bundles the remote side holds that cover [ranges]. */
    fun fetch(farmId: String, ranges: List<SequenceRange>): List<OperationBundle>

    /** Hands immutable bundles to the remote side. */
    fun publish(farmId: String, bundles: List<OperationBundle>)
}

enum class TransportKind { FARM_LAN_PEER, GOOGLE_DRIVE }

data class SyncOutcome(
    val transport: TransportKind,
    val status: SyncSessionStatus,
    val pulledOperations: Int = 0,
    val duplicateOperations: Int = 0,
    val pushedOperations: Int = 0,
    val rejectedReasons: List<String> = emptyList(),
    /**
     * After a completed session: the highest of this device's own sequences the peer now holds, so this
     * device knows which of its changes have reached another farm device.
     */
    val peerHoldsOwnThrough: Long? = null,
)

enum class SyncSessionStatus { COMPLETED, TRANSPORT_UNAVAILABLE, PEER_NOT_AUTHORISED }

/**
 * One synchronisation exchange: compare vectors, pull exactly the missing ranges, push exactly what the
 * remote lacks. A rejected bundle is reported and skipped; it never damages local data.
 */
object SyncSession {
    fun run(
        replica: ReplicaEndpoint,
        transport: ReplicationTransport,
        remoteDeviceId: String? = null,
        maxOperationsPerBundle: Int = 100,
    ): SyncOutcome {
        if (!transport.isAvailable()) return SyncOutcome(transport.kind, SyncSessionStatus.TRANSPORT_UNAVAILABLE)
        if (remoteDeviceId != null && !replica.maySynchronise(remoteDeviceId)) {
            return SyncOutcome(transport.kind, SyncSessionStatus.PEER_NOT_AUTHORISED)
        }
        if (!replica.maySynchronise(replica.deviceId)) {
            return SyncOutcome(transport.kind, SyncSessionStatus.PEER_NOT_AUTHORISED)
        }
        val local = replica.vector()
        val remote = transport.remoteVector(replica.farmId)

        var pulled = 0
        var duplicates = 0
        val rejected = mutableListOf<String>()
        val missing = local.missingFrom(remote)
        if (missing.isNotEmpty()) {
            transport.fetch(replica.farmId, missing).sortedWith(compareBy({ it.deviceId }, { it.fromSequence })).forEach { bundle ->
                val result = replica.ingest(bundle)
                if (result.rejected) rejected += result.rejectedReason!! else {
                    pulled += result.applied
                    duplicates += result.duplicates
                }
            }
        }

        val offer = remote.missingFrom(replica.vector())
        val outgoing = replica.bundlesFor(offer, maxOperationsPerBundle)
        if (outgoing.isNotEmpty()) transport.publish(replica.farmId, outgoing)
        // The peer's own answer, not an assumption that everything pushed was accepted.
        val peerHoldsOwn = if (outgoing.isEmpty()) remote.watermark(replica.deviceId) else transport.remoteVector(replica.farmId).watermark(replica.deviceId)

        return SyncOutcome(
            transport = transport.kind,
            status = SyncSessionStatus.COMPLETED,
            pulledOperations = pulled,
            duplicateOperations = duplicates,
            pushedOperations = outgoing.sumOf { it.operations.size },
            rejectedReasons = rejected,
            peerHoldsOwnThrough = peerHoldsOwn,
        )
    }
}

/**
 * Farm-LAN peer transport over an in-process peer replica. The production LAN transport carries the
 * same calls over an authenticated socket; this implementation is the executable contract used by tests.
 */
class LocalPeerTransport(
    private val peer: ReplicaEndpoint,
    private val reachable: () -> Boolean = { true },
) : ReplicationTransport {
    override val kind = TransportKind.FARM_LAN_PEER

    override fun isAvailable(): Boolean = reachable()

    override fun remoteVector(farmId: String): SyncVector {
        check(farmId == peer.farmId) { "Peer serves another farm" }
        return peer.vector()
    }

    override fun fetch(farmId: String, ranges: List<SequenceRange>): List<OperationBundle> {
        check(farmId == peer.farmId) { "Peer serves another farm" }
        return peer.bundlesFor(ranges)
    }

    override fun publish(farmId: String, bundles: List<OperationBundle>) {
        check(farmId == peer.farmId) { "Peer serves another farm" }
        bundles.forEach { peer.ingest(it) }
    }
}

/**
 * Deterministic, farm-scoped immutable bundle layout used by the Drive transport:
 * `GOAT/farms/<farm>/sync/<device>/<from>-<to>.bundle` with zero-padded sequences.
 */
object DriveJournalLayout {
    fun bundlePath(farmId: String, bundle: OperationBundle): String =
        "GOAT/farms/$farmId/sync/${bundle.deviceId}/${pad(bundle.fromSequence)}-${pad(bundle.toSequence)}.bundle"

    private fun pad(sequence: Long): String = sequence.toString().padStart(12, '0')
}

/**
 * Stand-in for the owner's Drive folder: a write-once object store. Publishing a different bundle at
 * an existing path is refused, so no published batch is ever rewritten.
 */
class ImmutableBundleStore {
    private val objects = LinkedHashMap<String, OperationBundle>()

    fun put(path: String, bundle: OperationBundle) {
        val existing = objects[path]
        check(existing == null || existing.checksum == bundle.checksum) { "Published bundle $path cannot be rewritten" }
        objects[path] = bundle
    }

    fun paths(): List<String> = objects.keys.sorted()

    fun get(path: String): OperationBundle? = objects[path]

    /** Replaces an object's bytes to simulate storage corruption in tests. */
    fun corrupt(path: String, replacement: OperationBundle) {
        objects[path] = replacement
    }
}

/**
 * Google Drive journal transport. It publishes and reads the same immutable bundles as the LAN, laid out
 * per farm and per device; Drive is replication and backup, never the live database.
 */
class GoogleDriveTransport(
    private val store: ImmutableBundleStore,
    private val online: () -> Boolean = { true },
) : ReplicationTransport {
    override val kind = TransportKind.GOOGLE_DRIVE

    override fun isAvailable(): Boolean = online()

    override fun remoteVector(farmId: String): SyncVector {
        val byDevice = farmBundles(farmId).groupBy { it.deviceId }
        return SyncVector(
            byDevice.mapValues { (_, bundles) ->
                var mark = 0L
                bundles.sortedBy { it.fromSequence }.forEach { if (it.fromSequence <= mark + 1) mark = maxOf(mark, it.toSequence) }
                mark
            },
        )
    }

    override fun fetch(farmId: String, ranges: List<SequenceRange>): List<OperationBundle> =
        farmBundles(farmId).filter { bundle ->
            ranges.any { it.deviceId == bundle.deviceId && bundle.toSequence >= it.from && bundle.fromSequence <= it.to }
        }

    override fun publish(farmId: String, bundles: List<OperationBundle>) {
        bundles.forEach { store.put(DriveJournalLayout.bundlePath(farmId, it), it) }
    }

    private fun farmBundles(farmId: String): List<OperationBundle> =
        store.paths().filter { it.startsWith("GOAT/farms/$farmId/sync/") }.mapNotNull(store::get)
}
