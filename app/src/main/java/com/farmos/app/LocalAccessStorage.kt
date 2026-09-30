package com.farmos.app

import androidx.room.withTransaction
import com.farmos.core.database.AccessAuditEntity
import com.farmos.core.database.COMMAND_PAYLOAD_KEY
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.FarmRecoveryEntity
import com.farmos.core.database.LocalAccountEntity
import com.farmos.core.database.LocalFarmEntity
import com.farmos.core.database.OperationApplier
import com.farmos.core.database.toEnvelope
import com.farmos.core.database.journalLocalOperationBlocking
import com.farmos.core.network.FarmMembership
import com.farmos.domain.access.AccessAuditEvent
import com.farmos.domain.access.AccountStatus
import com.farmos.domain.access.CredentialHasher
import com.farmos.domain.access.CredentialKind
import com.farmos.domain.access.LocalAccessService
import com.farmos.domain.access.LocalAccessStore
import com.farmos.domain.access.LocalAccount
import com.farmos.domain.access.LocalRole
import com.farmos.domain.replication.CanonicalOperationOrder
import com.farmos.domain.replication.OperationEnvelope
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * Room adapter for the local access port. Called only from a background thread, inside the transaction
 * [LocalFarmDirectory.transact] opens, so each change and its journal entry commit together.
 *
 * Identity changes (name, role, status, credential hash, worker link), recovery-hash rotation and audit
 * events are journalled so every paired device knows the farm's accounts. Sign-in failure counters and
 * lockouts stay on the device where they happened.
 */
internal class RoomLocalAccessStore(
    private val database: FarmOsDatabase,
    private val deviceId: String,
    private val clock: () -> Long,
) : LocalAccessStore {
    private val dao get() = database.localAccess()

    override fun accounts(farmId: String): List<LocalAccount> = dao.accounts(farmId).map { it.toDomain() }

    override fun account(farmId: String, accountId: String): LocalAccount? = dao.account(farmId, accountId)?.toDomain()

    override fun save(account: LocalAccount) {
        val previous = dao.account(account.farmId, account.accountId)
        val next = account.toEntity(0).identity()
        val changed = if (previous == null) IDENTITY_FIELDS else IDENTITY_FIELDS.filter { previous.identity()[it] != next[it] }
        if (changed.isEmpty()) {
            // Only this device's sign-in counters changed; they are not replicated.
            dao.upsertAccount(account.toEntity(previous!!.updatedAtEpochMillis))
            return
        }
        journal(account.farmId, ACCOUNT_ENTITY, account.accountId, account.accountId, clock(), ACCOUNT_SET_COMMAND, account.toJson().put("changed", JSONArray(changed)))
        // The row is the fold of every change to the account, exactly as on devices that receive it.
        val history = database.replicationBlocking().operationsForEntity(account.farmId, ACCOUNT_ENTITY, account.accountId).map { it.toEnvelope() }
        dao.upsertAccount(foldAccount(history).copy(failedAttempts = account.failedAttempts, lockedUntilEpochMillis = account.lockedUntilEpochMillis))
    }

    override fun recoveryHash(farmId: String): String? = dao.recoveryHash(farmId)

    override fun saveRecoveryHash(farmId: String, hash: String) {
        val now = clock()
        dao.upsertRecovery(FarmRecoveryEntity(farmId, hash, now))
        journal(farmId, "farm_recovery", farmId, SYSTEM_ACTOR, now, RECOVERY_SET_COMMAND, JSONObject().put("farmId", farmId).put("recoveryHash", hash))
    }

    override fun record(event: AccessAuditEvent) {
        val entity = AccessAuditEntity(event.eventId, event.farmId, event.actorAccountId, event.subjectAccountId, event.action.name, event.detail, event.atEpochMillis)
        dao.insertAudit(entity)
        journal(event.farmId, "access_audit", event.eventId, event.actorAccountId ?: SYSTEM_ACTOR, event.atEpochMillis, AUDIT_COMMAND, entity.toJson())
    }

    private fun journal(farmId: String, entityType: String, entityId: String, actorId: String, at: Long, command: String, payload: JSONObject) =
        database.journalLocalOperationBlocking(UUID.randomUUID().toString(), farmId, entityType, entityId, actorId, deviceId, at, command, payload.toString())
}

/** Local farms and accounts on this device: creation, sign-in and recovery without any server. */
internal class LocalFarmDirectory(
    private val database: FarmOsDatabase,
    private val deviceId: String,
    hasher: CredentialHasher = CredentialHasher(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val store = RoomLocalAccessStore(database, deviceId, clock)
    val access = LocalAccessService(store, hasher, clock, { UUID.randomUUID().toString() })

    fun farms(): List<LocalFarmEntity> = database.localAccess().farms()

    fun accounts(farmId: String): List<LocalAccount> = store.accounts(farmId)

    fun account(farmId: String, accountId: String): LocalAccount? = store.account(farmId, accountId)

    /** The farm's Owner with [username], used by recovery; null never reveals which part did not match. */
    fun ownerByUsername(farmId: String, username: String): LocalAccount? {
        val normalised = username.trim().lowercase()
        return store.accounts(farmId).firstOrNull { it.username == normalised && it.role == LocalRole.OWNER }
    }

    /** Runs an access change in one transaction, so the change, its audit entry and its journal entries commit together. */
    suspend fun <T> transact(block: (LocalAccessService) -> T): T = database.withTransaction { block(access) }

    /** Creates the farm and its Owner in one transaction, so a failed setup leaves no half-created farm. */
    suspend fun createFarm(name: String, create: (farmId: String) -> Unit): LocalFarmEntity {
        require(name.isNotBlank()) { "A farm name is required" }
        val farm = LocalFarmEntity(UUID.randomUUID().toString(), name.trim(), clock())
        database.withTransaction {
            database.localAccess().insertFarm(farm)
            database.journalLocalOperationBlocking(
                UUID.randomUUID().toString(), farm.farmId, "local_farm", farm.farmId, SYSTEM_ACTOR, deviceId, farm.createdAtEpochMillis,
                FARM_CREATE_COMMAND, JSONObject().put("farmId", farm.farmId).put("name", farm.name).put("createdAtEpochMillis", farm.createdAtEpochMillis).toString(),
            )
            create(farm.farmId)
        }
        return farm
    }
}

internal const val FARM_CREATE_COMMAND = "access.farm_create.v1"
internal const val ACCOUNT_SET_COMMAND = "access.account_set.v1"
internal const val RECOVERY_SET_COMMAND = "access.recovery_set.v1"
internal const val AUDIT_COMMAND = "access.audit.v1"
private const val SYSTEM_ACTOR = "local-access"

/**
 * Appliers for replicated access operations. Account changes merge field by field through [foldAccount];
 * the recovery hash merges by business time, so a late older rotation never overwrites a newer one. This
 * device's sign-in counters and lockout are kept.
 * Two devices that created the same username concurrently fail the unique-username rule and stay failed
 * for review instead of silently merging two people.
 */
internal val accessReplicationAppliers: Map<String, OperationApplier> = mapOf(
    FARM_CREATE_COMMAND to OperationApplier { database, op ->
        val farm = op.json()
        database.localAccess().insertFarmIfAbsent(LocalFarmEntity(farm.getString("farmId"), farm.getString("name"), farm.getLong("createdAtEpochMillis")))
    },
    ACCOUNT_SET_COMMAND to OperationApplier { database, op ->
        require(op.json().getString("farmId") == op.farmId) { "Account belongs to another farm" }
        val merged = foldAccount(database.replication().operationsForEntity(op.farmId, ACCOUNT_ENTITY, op.entityId).map { it.toEnvelope() })
        val existing = database.localAccess().account(op.farmId, op.entityId)
        database.localAccess().upsertAccount(
            merged.copy(failedAttempts = existing?.failedAttempts ?: 0, lockedUntilEpochMillis = existing?.lockedUntilEpochMillis),
        )
    },
    RECOVERY_SET_COMMAND to OperationApplier { database, op ->
        val recovery = op.json()
        require(recovery.getString("farmId") == op.farmId) { "Recovery hash belongs to another farm" }
        val existing = database.localAccess().recovery(op.farmId)
        if (existing == null || existing.updatedAtEpochMillis <= op.businessTimeEpochMillis) {
            database.localAccess().upsertRecovery(FarmRecoveryEntity(op.farmId, recovery.getString("recoveryHash"), op.businessTimeEpochMillis))
        }
    },
    AUDIT_COMMAND to OperationApplier { database, op ->
        val event = op.json()
        require(event.getString("farmId") == op.farmId) { "Audit event belongs to another farm" }
        database.localAccess().insertAuditIfAbsent(
            AccessAuditEntity(
                event.getString("eventId"), op.farmId, event.optStringOrNull("actorAccountId"), event.optStringOrNull("subjectAccountId"),
                event.getString("action"), event.getString("detail"), event.getLong("atEpochMillis"),
            ),
        )
    },
)

private fun OperationEnvelope.json() = JSONObject(payload.getValue(COMMAND_PAYLOAD_KEY))

/**
 * Field-update merge for an account: every change to it, in canonical order, each setting only the fields
 * it changed. Concurrent changes to different fields both survive, and every device that holds the same
 * changes computes the same account.
 */
private fun foldAccount(operations: List<OperationEnvelope>): LocalAccountEntity {
    val history = operations.filter { it.operationType == ACCOUNT_SET_COMMAND }.sortedWith(CanonicalOperationOrder)
    require(history.isNotEmpty()) { "No account changes to fold" }
    val folded = JSONObject(history.first().json().toString())
    history.forEach { change ->
        val fields = change.json()
        val changed = fields.getJSONArray("changed")
        for (i in 0 until changed.length()) folded.put(changed.getString(i), fields.get(changed.getString(i)))
    }
    return folded.toAccountEntity(history.last().businessTimeEpochMillis)
}

private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else getString(key)

private const val ACCOUNT_ENTITY = "local_account"

/** Fields that identify a person and their access: the replicated part of an account. */
private val IDENTITY_FIELDS = listOf("username", "displayName", "role", "status", "credentialKind", "credentialHash", "workerId")

private fun LocalAccountEntity.identity(): Map<String, String?> = mapOf(
    "username" to username,
    "displayName" to displayName,
    "role" to role,
    "status" to status,
    "credentialKind" to credentialKind,
    "credentialHash" to credentialHash,
    "workerId" to workerId,
)

private fun LocalAccount.toJson(): JSONObject = JSONObject()
    .put("accountId", accountId)
    .put("farmId", farmId)
    .put("username", username)
    .put("displayName", displayName)
    .put("role", role.name)
    .put("status", status.name)
    .put("credentialKind", credentialKind.name)
    .put("credentialHash", credentialHash)
    .put("workerId", workerId ?: JSONObject.NULL)
    .put("createdAtEpochMillis", createdAtEpochMillis)

private fun JSONObject.toAccountEntity(updatedAt: Long) = LocalAccountEntity(
    accountId = getString("accountId"),
    farmId = getString("farmId"),
    username = getString("username"),
    displayName = getString("displayName"),
    role = LocalRole.valueOf(getString("role")).name,
    status = AccountStatus.valueOf(getString("status")).name,
    credentialKind = CredentialKind.valueOf(getString("credentialKind")).name,
    credentialHash = getString("credentialHash"),
    failedAttempts = 0,
    lockedUntilEpochMillis = null,
    workerId = optStringOrNull("workerId"),
    createdAtEpochMillis = getLong("createdAtEpochMillis"),
    updatedAtEpochMillis = updatedAt,
)

private fun AccessAuditEntity.toJson(): JSONObject = JSONObject()
    .put("eventId", eventId)
    .put("farmId", farmId)
    .put("actorAccountId", actorAccountId ?: JSONObject.NULL)
    .put("subjectAccountId", subjectAccountId ?: JSONObject.NULL)
    .put("action", action)
    .put("detail", detail)
    .put("atEpochMillis", atEpochMillis)

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

private fun LocalAccount.toEntity(updatedAt: Long) = LocalAccountEntity(
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
    updatedAtEpochMillis = updatedAt,
)
