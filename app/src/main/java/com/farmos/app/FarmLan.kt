package com.farmos.app

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.recordPeerHolds
import com.farmos.domain.access.LocalAccount
import com.farmos.domain.access.Permission
import com.farmos.domain.access.RolePermissions
import com.farmos.domain.replication.DeviceKeys
import com.farmos.domain.replication.DeviceRegistry
import com.farmos.domain.replication.EnrolmentDecision
import com.farmos.domain.replication.EnrolmentOutcome
import com.farmos.domain.replication.EnrolmentRequest
import com.farmos.domain.replication.FarmDiscoveryDescriptor
import com.farmos.domain.replication.LanPeerTransport
import com.farmos.domain.replication.LanSyncServer
import com.farmos.domain.replication.PairingAuthority
import com.farmos.domain.replication.PairingClient
import com.farmos.domain.replication.PairingCode
import com.farmos.domain.replication.PairingServer
import com.farmos.domain.replication.PendingEnrolment
import com.farmos.domain.replication.REPLICATION_PROTOCOL_VERSION
import com.farmos.domain.replication.SyncOutcome
import com.farmos.domain.replication.SyncSession
import com.farmos.domain.replication.SyncSessionStatus
import java.io.Closeable
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking

/** A GOAT farm device found on the local network. */
internal data class DiscoveredFarm(val descriptor: FarmDiscoveryDescriptor, val host: String, val port: Int, val serviceName: String)

/** Finds and announces GOAT farm devices on the local network. Only non-secret descriptor data is broadcast. */
internal interface FarmPeerDiscovery {
    fun advertise(serviceName: String, descriptor: FarmDiscoveryDescriptor, port: Int): Closeable

    fun discover(onChange: (List<DiscoveredFarm>) -> Unit): Closeable
}

/** Android NSD (mDNS/DNS-SD) implementation of [FarmPeerDiscovery] for `_goatfarm._tcp`. */
internal class NsdFarmPeerDiscovery(context: Context) : FarmPeerDiscovery {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    override fun advertise(serviceName: String, descriptor: FarmDiscoveryDescriptor, port: Int): Closeable {
        val info = NsdServiceInfo().apply {
            this.serviceName = serviceName
            serviceType = FarmDiscoveryDescriptor.SERVICE_TYPE
            this.port = port
            descriptor.toTxtRecord().forEach { (key, value) -> setAttribute(key, value) }
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
        }
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        return Closeable { runCatching { nsd.unregisterService(listener) } }
    }

    override fun discover(onChange: (List<DiscoveredFarm>) -> Unit): Closeable {
        val found = ConcurrentHashMap<String, DiscoveredFarm>()
        // Older NSD resolves one service at a time, so resolutions are queued on one thread.
        val resolver = Executors.newSingleThreadExecutor()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                resolver.execute { resolve(serviceInfo)?.let { found[it.serviceName] = it; onChange(found.values.toList()) } }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                if (found.remove(serviceInfo.serviceName) != null) onChange(found.values.toList())
            }
        }
        nsd.discoverServices(FarmDiscoveryDescriptor.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        return Closeable {
            runCatching { nsd.stopServiceDiscovery(listener) }
            resolver.shutdownNow()
        }
    }

    @Suppress("DEPRECATION")
    private fun resolve(service: NsdServiceInfo): DiscoveredFarm? {
        val result = java.util.concurrent.LinkedBlockingQueue<Result<NsdServiceInfo>>(1)
        nsd.resolveService(
            service,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    result.offer(Result.failure(IllegalStateException("Resolve failed: $errorCode")))
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    result.offer(Result.success(serviceInfo))
                }
            },
        )
        val resolved = result.poll(RESOLVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)?.getOrNull() ?: return null
        val host = resolved.host?.hostAddress ?: return null
        val record = resolved.attributes.mapValues { (_, value) -> value?.toString(Charsets.UTF_8).orEmpty() }
        val descriptor = FarmDiscoveryDescriptor.fromTxtRecord(record) ?: return null
        return DiscoveredFarm(descriptor, host, resolved.port, resolved.serviceName)
    }

    private companion object {
        const val RESOLVE_TIMEOUT_SECONDS = 10L
    }
}

/** What the LAN runtime last did, for the farm's storage and device screens. */
internal data class FarmLanState(
    val serving: Boolean = false,
    val peers: List<DiscoveredFarm> = emptyList(),
    val lastSyncEpochMillis: Long? = null,
    val lastOutcomes: List<SyncOutcome> = emptyList(),
    val lastError: String? = null,
)

/**
 * Farm-LAN replication for one farm on this device, while the farm is open: serves this device's journal
 * to authenticated farm peers, announces it on the local network, finds the farm's other devices and
 * synchronises with each of them periodically and on request. It needs no Internet and no server.
 */
internal class FarmLanRuntime(
    private val database: FarmOsDatabase,
    private val vault: FarmKeyVault,
    private val discovery: FarmPeerDiscovery,
    private val farmId: String,
    private val farmName: String,
    private val deviceId: String,
    private val clock: () -> Long = System::currentTimeMillis,
) : Closeable {
    private val endpoint = RoomReplicaEndpoint(database, farmId, deviceId, replicationAppliers)
    private val keys = { vault.secretsForLocalFarm(farmId).keys }
    private val worker = Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "goat-farm-lan").apply { isDaemon = true } }
    private val resources = mutableListOf<Closeable>()
    @Volatile private var syncPort: Int? = null
    private var advertisement: Closeable? = null
    private val mutableState = MutableStateFlow(FarmLanState())
    val state: StateFlow<FarmLanState> = mutableState.asStateFlow()

    /** Starts serving, announcing and discovering; synchronises every [intervalSeconds]. */
    fun start(intervalSeconds: Long = SYNC_INTERVAL_SECONDS): FarmLanRuntime {
        worker.execute {
            runCatching {
                val server = LanSyncServer(endpoint, keys).start(InetSocketAddress(0))
                resources += server
                syncPort = server.port
                advertise(pairingPort = null)
                resources += discovery.discover { farms ->
                    mutableState.value = mutableState.value.copy(peers = farms.filter { it.descriptor.farmId == farmId && it.serviceName != serviceName() })
                }
                mutableState.value = mutableState.value.copy(serving = true, lastError = null)
            }.onFailure { mutableState.value = mutableState.value.copy(lastError = it.message ?: "The farm network could not start") }
        }
        worker.scheduleWithFixedDelay({ syncNow() }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS)
        return this
    }

    /**
     * Opens pairing for [approver]: the farm is announced with a pairing port, and each device that asks to
     * join waits in [PairingSession.pending] until the approver types the code it shows, or declines.
     */
    /** Blocks until the pairing server is listening; call it off the main thread. */
    fun openPairing(approver: LocalAccount): PairingSession = worker.submit<PairingSession> {
        checkNotNull(syncPort) { "The farm network is still starting on this device" }
        val registry = DeviceRegistry(runBlocking { database.farmDevices(farmId) })
        val authority = PairingAuthority(farmId, DeviceKeys.farmPairingFingerprint(farmId), registry, clock)
        val session = PairingSession(approver)
        val server = PairingServer(
            authority,
            keys,
            onGranted = { request, grant ->
                runBlocking { database.enrolPairedDevice(grant, request.devicePublicKey, deviceId) }
                session.joined(grant.deviceName)
            },
            onRefused = { request, reason -> session.refused(request.deviceName, reason) },
            decide = session::await,
        ).start(InetSocketAddress(0))
        advertise(pairingPort = server.port)
        session.onClose = {
            server.close()
            // The runtime may already be closed, in which case its advertisement is gone too.
            runCatching { worker.execute { advertise(pairingPort = null) } }
        }
        session
    }.get()

    /** Announces this device's sync port, and the pairing port while a device may join. Runs on the worker. */
    private fun advertise(pairingPort: Int?) {
        val port = syncPort ?: return
        advertisement?.let { runCatching { it.close() } }
        advertisement = discovery.advertise(serviceName(), descriptor().copy(pairingPort = pairingPort), port)
    }

    /** Synchronises with every farm device currently found on the network. */
    fun requestSync() {
        // A screen may still hold a runtime its session has already closed.
        if (!worker.isShutdown) runCatching { worker.execute { syncNow() } }
    }

    private fun syncNow() {
        val outcomes = mutableState.value.peers.map { peer ->
            LanPeerTransport(peer.host, peer.port, farmId, deviceId, keys, endpoint::maySynchronise).use { transport ->
                val outcome = runCatching { SyncSession.run(endpoint, transport) }
                    .getOrElse { SyncOutcome(transport.kind, SyncSessionStatus.TRANSPORT_UNAVAILABLE, rejectedReasons = listOf(it.message ?: "Sync failed")) }
                val peerId = transport.peerDeviceId
                val held = outcome.peerHoldsOwnThrough
                if (outcome.status == SyncSessionStatus.COMPLETED && peerId != null && held != null) {
                    runBlocking { database.recordPeerHolds(farmId, peerId, held, clock()) }
                }
                outcome
            }
        }
        mutableState.value = mutableState.value.copy(lastSyncEpochMillis = clock(), lastOutcomes = outcomes)
    }

    private fun descriptor() = FarmDiscoveryDescriptor(
        farmId = farmId,
        farmName = farmName,
        protocolVersion = REPLICATION_PROTOCOL_VERSION,
        syncGeneration = 1,
        currentKeyId = keys().currentKeyId,
        pairingFingerprint = DeviceKeys.farmPairingFingerprint(farmId),
    )

    /** DNS-SD names are per device, so each farm device announces itself separately. */
    private fun serviceName() = "goat-${deviceId.take(SERVICE_NAME_ID_CHARS)}"

    override fun close() {
        if (worker.isShutdown) return
        worker.execute {
            advertisement?.let { runCatching { it.close() } }
            advertisement = null
            resources.reversed().forEach { runCatching { it.close() } }
            resources.clear()
            mutableState.value = mutableState.value.copy(serving = false)
        }
        worker.shutdown()
    }

    private companion object {
        const val SYNC_INTERVAL_SECONDS = 60L
        const val SERVICE_NAME_ID_CHARS = 12
    }
}

/** One open pairing window on the approving device. */
internal class PairingSession(private val approver: LocalAccount) : Closeable {
    private val decisions = LinkedBlockingQueue<EnrolmentDecision>()
    private val mutablePending = MutableStateFlow<PendingEnrolment?>(null)
    private val mutableLastResult = MutableStateFlow<String?>(null)

    /** The device currently asking to join, if any. */
    val pending: StateFlow<PendingEnrolment?> = mutablePending.asStateFlow()

    /** What happened to the last request, for the approver. */
    val lastResult: StateFlow<String?> = mutableLastResult.asStateFlow()

    internal var onClose: () -> Unit = {}

    /** Called on the pairing server's thread: shows the request and waits for the approver. */
    internal fun await(request: PendingEnrolment): EnrolmentDecision {
        mutablePending.value = request
        val decision = decisions.poll(DECISION_TIMEOUT_MINUTES, TimeUnit.MINUTES) ?: EnrolmentDecision.Decline
        mutablePending.value = null
        return decision
    }

    internal fun joined(deviceName: String) {
        mutableLastResult.value = "$deviceName joined this farm"
    }

    internal fun refused(deviceName: String, reason: String) {
        mutableLastResult.value = when (reason) {
            "DECLINED" -> "Declined $deviceName"
            "CODE_MISMATCH" -> "The code did not match $deviceName. Ask it to try again."
            "APPROVER_NOT_AUTHORISED" -> "Your role cannot add devices."
            "DEVICE_REVOKED" -> "$deviceName was removed from this farm and cannot join again."
            else -> "$deviceName could not join ($reason)."
        }
    }

    /** The approver typed the code shown on the new device. The pairing rules check it and the approver's role. */
    fun approve(codeShownOnNewDevice: String) {
        decisions.offer(
            EnrolmentDecision.Approve(approver.accountId, RolePermissions.allows(approver.role, Permission.APPROVE_DEVICE_PAIRING), codeShownOnNewDevice.filter(Char::isDigit)),
        )
    }

    fun decline() {
        decisions.offer(EnrolmentDecision.Decline)
    }

    override fun close() {
        decisions.offer(EnrolmentDecision.Decline)
        onClose()
    }

    private companion object {
        const val DECISION_TIMEOUT_MINUTES = 5L
    }
}

/** Outcome of joining a farm from this device. */
internal sealed interface JoinResult {
    data class Joined(val farmId: String, val farmName: String) : JoinResult

    data class Refused(val message: String) : JoinResult
}

/**
 * The new device's side of joining a farm found on the network: it shows [onCode] for the farm owner to
 * type, waits for approval, installs the grant, then copies the farm journal from that device so the
 * farm and its accounts are here before anyone signs in.
 */
internal class FarmJoiner(
    private val database: FarmOsDatabase,
    private val vault: FarmKeyVault,
    private val deviceId: String,
    private val deviceName: String,
) {
    fun join(farm: DiscoveredFarm, onCode: (String) -> Unit): JoinResult {
        val descriptor = farm.descriptor
        val pairingPort = descriptor.pairingPort ?: return JoinResult.Refused("${descriptor.farmName} is not accepting new devices. Ask the owner to choose Add a device.")
        val identity = vault.joiningIdentity(descriptor.farmId)
        val request = EnrolmentRequest(
            requestId = UUID.randomUUID().toString(),
            farmId = descriptor.farmId,
            deviceId = deviceId,
            deviceName = deviceName,
            devicePublicKey = DeviceKeys.encode(identity.public),
            nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes),
            requestedAtEpochMillis = System.currentTimeMillis(),
        )
        onCode(PairingCode.of(DeviceKeys.farmPairingFingerprint(descriptor.farmId), request.devicePublicKey, request.nonce))
        return when (val outcome = PairingClient.requestEnrolment(farm.host, pairingPort, request)) {
            is EnrolmentOutcome.Refused -> JoinResult.Refused(refusalMessage(outcome.reason))
            is EnrolmentOutcome.Granted -> {
                runBlocking { database.installGrant(outcome.grant, identity, deviceId, vault) }
                val endpoint = RoomReplicaEndpoint(database, descriptor.farmId, deviceId, replicationAppliers)
                val keys = { vault.secretsForLocalFarm(descriptor.farmId).keys }
                LanPeerTransport(farm.host, farm.port, descriptor.farmId, deviceId, keys, endpoint::maySynchronise).use { transport ->
                    SyncSession.run(endpoint, transport)
                }
                JoinResult.Joined(descriptor.farmId, descriptor.farmName)
            }
        }
    }

    private fun refusalMessage(reason: String) = when (reason) {
        "DECLINED" -> "The farm owner declined this device."
        "CODE_MISMATCH" -> "The code typed on the farm device did not match. Try again."
        "APPROVER_NOT_AUTHORISED" -> "The person approving is not allowed to add devices."
        "DEVICE_REVOKED" -> "This device was removed from the farm and cannot join again."
        else -> "The farm refused this device ($reason)."
    }

    private companion object {
        const val NONCE_BYTES = 16
    }
}
