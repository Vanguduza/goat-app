package com.farmos.app

import androidx.room.withTransaction
import com.farmos.core.database.AccessAuditEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.LocalAccountEntity
import com.farmos.core.database.LocalFarmEntity
import com.farmos.core.database.toEnvelope
import com.farmos.core.database.journalLocalOperationBlocking
import com.farmos.core.network.FarmMembership
import com.farmos.domain.access.AccessAuditEvent
import com.farmos.domain.access.AccessDenied
import com.farmos.domain.access.AccountStatus
import com.farmos.domain.access.CredentialHasher
import com.farmos.domain.access.CredentialKind
import com.farmos.domain.access.LocalAccessService
import com.farmos.domain.access.LocalAccessStore
import com.farmos.domain.access.LocalAccount
import com.farmos.domain.access.LocalRole
import com.farmos.domain.replication.DeviceStatus
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

    override fun save(account: LocalAccount, actorAccountId: String) {
        requireWritableDevice(account.farmId)
        require(dao.accountFarmId(account.accountId).let { it == null || it == account.farmId }) { "Account ID belongs to another farm" }
        val previous = dao.account(account.farmId, account.accountId)
        val next = account.toEntity(0).identity()
        val changed = if (previous == null) IDENTITY_FIELDS else IDENTITY_FIELDS.filter { previous.identity()[it] != next[it] }
        if (changed.isEmpty()) {
            // Only this device's sign-in counters changed; they are not replicated.
            dao.upsertAccount(account.toEntity(previous!!.updatedAtEpochMillis))
            return
        }
        journal(account.farmId, ACCOUNT_ENTITY, account.accountId, actorAccountId, clock(), ACCOUNT_SET_COMMAND, account.toJson().put("changed", JSONArray(changed)))
        // This transaction's local receipt is eligible immediately; unrelated received changes
        // must already be APPLIED before they can contribute to the account projection.
        val history = dao.appliedAccessHistory(account.farmId, ACCOUNT_ENTITY, account.accountId).map { it.toEnvelope() }
        val merged = foldAccount(history)
        check(changed.all { merged.identity()[it] == next[it] }) {
            "The device clock precedes the current account identity. Correct the clock and retry."
        }
        dao.upsertAccount(merged.copy(failedAttempts = account.failedAttempts, lockedUntilEpochMillis = account.lockedUntilEpochMillis))
    }

    override fun recoveryHash(farmId: String): String? = dao.recoveryHash(farmId)

    override fun saveRecoveryHash(farmId: String, hash: String, actorAccountId: String) {
        requireWritableDevice(farmId)
        val previous = dao.recovery(farmId)
        val operationId = journal(farmId, RECOVERY_ENTITY, farmId, actorAccountId, clock(), RECOVERY_SET_COMMAND,
            JSONObject().put("farmId", farmId).put("recoveryHash", hash))
        val history = dao.appliedAccessHistory(farmId, RECOVERY_ENTITY, farmId).map { it.toEnvelope() }
        val winner = requireNotNull(latestRecoveryChange(history))
        check(winner.operationId == operationId && (previous == null || winner.businessTimeEpochMillis >= previous.updatedAtEpochMillis)) {
            "The device clock precedes the current owner recovery state. Correct the clock and retry."
        }
        dao.upsertRecovery(winner.toRecoveryEntity())
    }

    override fun record(event: AccessAuditEvent) {
        requireWritableDevice(event.farmId)
        val entity = AccessAuditEntity(event.eventId, event.farmId, event.actorAccountId, event.subjectAccountId, event.action.name, event.detail, event.atEpochMillis)
        require(dao.auditEvent(event.eventId) == null) { "Access audit ID is already in use" }
        dao.insertAudit(entity)
        journal(event.farmId, "access_audit", event.eventId, event.actorAccountId ?: SYSTEM_ACTOR, event.atEpochMillis, AUDIT_COMMAND, entity.toJson())
    }

    private fun requireWritableDevice(farmId: String) {
        check(database.inTransaction()) { "Access changes require a Room transaction" }
        val device = database.replicationBlocking().device(farmId, deviceId)
        if (device == null || !device.isLocal || device.status != DeviceStatus.ACTIVE.name || device.revokedAfterSequence != null) {
            throw AccessDenied("This device is not permitted to change local farm access")
        }
    }

    private fun journal(farmId: String, entityType: String, entityId: String, actorId: String, at: Long, command: String, payload: JSONObject): String {
        val operationId = UUID.randomUUID().toString()
        database.journalLocalOperationBlocking(operationId, farmId, entityType, entityId, actorId, deviceId, at, command, payload.toString())
        return operationId
    }
}

/** Local farms and accounts on this device: creation, sign-in and recovery without any server. */
internal class LocalFarmDirectory(
    private val database: FarmOsDatabase,
    private val deviceId: String,
    hasher: CredentialHasher = CredentialHasher(),
    private val clock: () -> Long = System::currentTimeMillis,
    /** Required for first-farm creation; account-only callers never provision keys. */
    private val initialKeys: FarmKeyVault? = null,
) {
    private val store = RoomLocalAccessStore(database, deviceId, clock)
    val access = LocalAccessService(store, hasher, clock, { UUID.randomUUID().toString() })

    fun farms(): List<LocalFarmEntity> = database.localAccess().farms()

    fun accounts(farmId: String): List<LocalAccount> = store.accounts(farmId)

    /** Approval stays on this device; neither Google identity nor a replicated setting grants it. */
    suspend fun approveDriveGateway(
        context: android.content.Context,
        farmId: String,
        accountId: String,
        config: DriveGatewayConfig,
    ): DriveGatewayConfig = DriveFarmCoordinator.shared.withFarm(farmId) {
        val approved = DriveGatewayAuthority(database, deviceId).approve(farmId, accountId, config, DriveConfigStore(context))
        val state = DriveGatewayState(config = approved, deliveryStatus = DriveDeliveryStatus.READY)
        DriveDeliveryStateStore(context).save(farmId, approved, state)
        DriveFarmCoordinator.shared.state(farmId) { state }.value = state
        approved
    }

    fun account(farmId: String, accountId: String): LocalAccount? = store.account(farmId, accountId)

    /** The farm's Owner with [username], used by recovery; null never reveals which part did not match. */
    fun ownerByUsername(farmId: String, username: String): LocalAccount? {
        val normalised = username.trim().lowercase()
        return store.accounts(farmId).firstOrNull { it.username == normalised && it.role == LocalRole.OWNER }
    }

    /** Runs an access change in one transaction, so the change, its audit entry and its journal entries commit together. */
    suspend fun <T> transact(block: (LocalAccessService) -> T): T = database.withTransaction { block(access) }

    /**
     * Creates the farm, Owner and initial sealed keys at the explicit setup boundary. Key persistence is
     * the last action before Room commits; a failure rolls back every farm/account/journal write.
     * A crash at that boundary can retain only an unreferenced new-farm vault, never replace old keys.
     */
    suspend fun createFarm(name: String, create: (farmId: String) -> Unit): LocalFarmEntity {
        require(name.isNotBlank()) { "A farm name is required" }
        val keys = checkNotNull(initialKeys) { "Creating a farm requires configured local key storage" }
        val farm = LocalFarmEntity(UUID.randomUUID().toString(), name.trim(), clock())
        database.withTransaction {
            database.localAccess().insertFarm(farm)
            database.journalLocalOperationBlocking(
                UUID.randomUUID().toString(), farm.farmId, "local_farm", farm.farmId, SYSTEM_ACTOR, deviceId, farm.createdAtEpochMillis,
                FARM_CREATE_COMMAND, JSONObject().put("farmId", farm.farmId).put("name", farm.name).put("createdAtEpochMillis", farm.createdAtEpochMillis).toString(),
            )
            create(farm.farmId)
            check(store.accounts(farm.farmId).any { it.role == LocalRole.OWNER && it.status == AccountStatus.ACTIVE }) { "A new farm requires an active Owner" }
            keys.provisionNewFarm(farm.farmId)
        }
        return farm
    }
}

internal const val FARM_CREATE_COMMAND = "access.farm_create.v1"
internal const val ACCOUNT_SET_COMMAND = "access.account_set.v1"
internal const val RECOVERY_SET_COMMAND = "access.recovery_set.v1"
internal const val AUDIT_COMMAND = "access.audit.v1"
private const val SYSTEM_ACTOR = "local-access"

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
