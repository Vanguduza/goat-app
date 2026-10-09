package com.farmos.app

import androidx.room.withTransaction
import com.farmos.core.database.FarmOsDatabase
import com.farmos.domain.access.AccessDenied
import com.farmos.domain.access.AccountStatus
import com.farmos.domain.access.LocalAccount
import com.farmos.domain.access.Permission
import com.farmos.domain.replication.DeviceStatus
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

internal data class CurrentLocalSession(val account: LocalAccount, val farmName: String)

internal sealed interface LocalSessionStatus {
    data object Checking : LocalSessionStatus
    data class Accepted(val current: CurrentLocalSession) : LocalSessionStatus
    data class ReauthenticationRequired(val message: String) : LocalSessionStatus
}

/**
 * Authority for one completed local sign-in. The credential hash already returned by sign-in stays
 * only in memory; neither the PIN nor a new durable session credential is created here.
 *
 * Room remains authoritative for the current role and device. A terminal account/credential/device
 * change ends this sign-in permanently; re-enabling a row cannot resurrect a retained UI callback.
 */
internal class LocalSessionAuthority(
    private val database: FarmOsDatabase,
    private val directory: LocalFarmDirectory,
    private val authenticated: LocalAccount,
    private val deviceId: String,
) {
    val farmId: String = authenticated.farmId
    private val ended = AtomicReference<String?>(null)

    fun observe(): Flow<LocalSessionStatus> = database.invalidationTracker
        .createFlow("local_accounts", "local_farms", "replication_devices")
        .map {
            val status: LocalSessionStatus = database.withTransaction {
                LocalSessionStatus.Accepted(requireCurrent())
            }
            status
        }
        .catch { failure ->
            if (failure is CancellationException) throw failure
            val message = if (failure is AccessDenied) failure.message else null
            end(message ?: "This device could not verify your farm access. Sign in again.")
            emit(LocalSessionStatus.ReauthenticationRequired(requireNotNull(ended.get())))
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    fun end(message: String? = null) {
        ended.compareAndSet(null, message ?: "Sign in again to continue with this farm.")
    }

    /**
     * Revalidate immediately at a disclosure boundary. The Room transaction orders that admission
     * against account/device writes; it does not make the external action rollbackable.
     */
    suspend fun <T> withPermission(farmId: String, permission: Permission, action: suspend () -> T): T {
        if (farmId != this.farmId) throw AccessDenied("Sign in to the farm whose records you want to export.")
        return database.withTransaction {
            requireCurrent()
            database.requireLocalAppPermission(farmId, authenticated.accountId, deviceId, permission)
            action()
        }
    }

    private fun requireCurrent(): CurrentLocalSession {
        check(database.inTransaction()) { "Session authority needs one current Room snapshot" }
        ended.get()?.let { throw AccessDenied(it) }
        val account = directory.account(farmId, authenticated.accountId)
            ?: refuse("Your farm account is no longer available. Sign in again.")
        if (account.status != AccountStatus.ACTIVE) {
            refuse("Your farm account is disabled. Ask farm management to restore access, then sign in again.")
        }
        if (account.credentialKind != authenticated.credentialKind || account.credentialHash != authenticated.credentialHash) {
            refuse("Your sign-in credential has changed. Sign in again with your current PIN or password.")
        }
        val device = database.replicationBlocking().device(farmId, deviceId)
        if (device?.isLocal != true || device.status != DeviceStatus.ACTIVE.name || device.revokedAfterSequence != null) {
            refuse("This device is no longer active for this farm. Ask farm management to review its access.")
        }
        val farm = directory.farms().firstOrNull { it.farmId == farmId }
            ?: refuse("This farm is no longer available on the device. Sign in again.")
        return CurrentLocalSession(account, farm.name)
    }

    private fun refuse(message: String): Nothing {
        end(message)
        throw AccessDenied(requireNotNull(ended.get()))
    }
}
