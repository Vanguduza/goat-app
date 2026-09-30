package com.farmos.app

import androidx.room.withTransaction
import com.farmos.core.database.AccessAuditEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.FarmRecoveryEntity
import com.farmos.core.database.LocalAccountEntity
import com.farmos.core.database.LocalFarmEntity
import com.farmos.core.network.FarmMembership
import com.farmos.domain.access.AccessAuditEvent
import com.farmos.domain.access.AccountStatus
import com.farmos.domain.access.CredentialHasher
import com.farmos.domain.access.CredentialKind
import com.farmos.domain.access.LocalAccessService
import com.farmos.domain.access.LocalAccessStore
import com.farmos.domain.access.LocalAccount
import com.farmos.domain.access.LocalRole
import java.util.UUID

/** Room adapter for the local access port. Called only from a background dispatcher. */
internal class RoomLocalAccessStore(private val database: FarmOsDatabase) : LocalAccessStore {
    private val dao get() = database.localAccess()

    override fun accounts(farmId: String): List<LocalAccount> = dao.accounts(farmId).map { it.toDomain() }

    override fun account(farmId: String, accountId: String): LocalAccount? = dao.account(farmId, accountId)?.toDomain()

    override fun save(account: LocalAccount) = dao.upsertAccount(account.toEntity())

    override fun recoveryHash(farmId: String): String? = dao.recoveryHash(farmId)

    override fun saveRecoveryHash(farmId: String, hash: String) = dao.upsertRecovery(FarmRecoveryEntity(farmId, hash))

    override fun record(event: AccessAuditEvent) = dao.insertAudit(
        AccessAuditEntity(event.eventId, event.farmId, event.actorAccountId, event.subjectAccountId, event.action.name, event.detail, event.atEpochMillis),
    )
}

/** Local farms and accounts on this device: creation, sign-in and recovery without any server. */
internal class LocalFarmDirectory(
    private val database: FarmOsDatabase,
    hasher: CredentialHasher = CredentialHasher(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val store = RoomLocalAccessStore(database)
    val access = LocalAccessService(store, hasher, clock, { UUID.randomUUID().toString() })

    fun farms(): List<LocalFarmEntity> = database.localAccess().farms()

    fun accounts(farmId: String): List<LocalAccount> = store.accounts(farmId)

    fun account(farmId: String, accountId: String): LocalAccount? = store.account(farmId, accountId)

    /** The farm's Owner with [username], used by recovery; null never reveals which part did not match. */
    fun ownerByUsername(farmId: String, username: String): LocalAccount? {
        val normalised = username.trim().lowercase()
        return store.accounts(farmId).firstOrNull { it.username == normalised && it.role == LocalRole.OWNER }
    }

    /** Creates the farm and its Owner in one transaction, so a failed setup leaves no half-created farm. */
    suspend fun createFarm(name: String, create: (farmId: String) -> Unit): LocalFarmEntity {
        require(name.isNotBlank()) { "A farm name is required" }
        val farm = LocalFarmEntity(UUID.randomUUID().toString(), name.trim(), clock())
        database.withTransaction {
            database.localAccess().insertFarm(farm)
            create(farm.farmId)
        }
        return farm
    }
}

/** The session role string the farm screens understand for a local role. */
internal fun LocalRole.sessionRole(): String = when (this) {
    LocalRole.OWNER -> "owner"
    LocalRole.MANAGER -> "manager"
    LocalRole.SUPERVISOR -> "supervisor"
    LocalRole.WORKER -> "worker"
    LocalRole.VIEWER -> "read_only"
}

internal fun LocalAccount.membership(): FarmMembership = FarmMembership(farmId = farmId, role = role.sessionRole())

private fun LocalAccountEntity.toDomain() = LocalAccount(
    accountId = accountId,
    farmId = farmId,
    username = username,
    displayName = displayName,
    role = LocalRole.valueOf(role),
    status = AccountStatus.valueOf(status),
    credentialKind = CredentialKind.valueOf(credentialKind),
    credentialHash = credentialHash,
    failedAttempts = failedAttempts,
    lockedUntilEpochMillis = lockedUntilEpochMillis,
    workerId = workerId,
    createdAtEpochMillis = createdAtEpochMillis,
)

private fun LocalAccount.toEntity() = LocalAccountEntity(
    accountId = accountId,
    farmId = farmId,
    username = username,
    displayName = displayName,
    role = role.name,
    status = status.name,
    credentialKind = credentialKind.name,
    credentialHash = credentialHash,
    failedAttempts = failedAttempts,
    lockedUntilEpochMillis = lockedUntilEpochMillis,
    workerId = workerId,
    createdAtEpochMillis = createdAtEpochMillis,
)
