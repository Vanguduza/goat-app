package com.farmos.app

import com.farmos.core.database.AccessAuditEntity
import com.farmos.core.database.ApplicationState
import com.farmos.core.database.COMMAND_PAYLOAD_KEY
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.FarmRecoveryEntity
import com.farmos.core.database.LocalAccountEntity
import com.farmos.core.database.LocalFarmEntity
import com.farmos.core.database.OperationApplier
import com.farmos.core.database.toEnvelope
import com.farmos.domain.access.AccountStatus
import com.farmos.domain.access.CredentialKind
import com.farmos.domain.access.LocalRole
import com.farmos.domain.replication.CanonicalOperationOrder
import com.farmos.domain.replication.OperationEnvelope
import org.json.JSONObject

internal const val ACCOUNT_ENTITY = "local_account"
internal const val RECOVERY_ENTITY = "farm_recovery"
internal val IDENTITY_FIELDS = listOf("username", "displayName", "role", "status", "credentialKind", "credentialHash", "workerId")

/**
 * Replays exact admitted access receipts, never a new call borrowing the receiver's current role.
 * Invalid identity/tenant claims remain failed receipts and cannot influence another valid fold.
 */
internal val accessReplicationAppliers: Map<String, OperationApplier> = mapOf(
    FARM_CREATE_COMMAND to OperationApplier { database, op ->
        database.requireAccessReceipt(op, FARM_CREATE_COMMAND)
        val farm = op.boundPayload("local_farm")
        require(op.entityId == op.farmId && farm.getString("farmId") == op.farmId) { "Farm identity does not match its operation" }
        require(farm.getString("name").isNotBlank()) { "A farm name is required" }
        require(farm.getLong("createdAtEpochMillis") == op.businessTimeEpochMillis) { "Farm creation time does not match its operation" }
        database.localAccess().insertFarmIfAbsent(LocalFarmEntity(op.farmId, farm.getString("name"), farm.getLong("createdAtEpochMillis")))
    },
    ACCOUNT_SET_COMMAND to OperationApplier { database, op ->
        database.requireAccessReceipt(op, ACCOUNT_SET_COMMAND)
        op.accountChange()
        require(database.localAccess().accountFarmId(op.entityId).let { it == null || it == op.farmId }) { "Account ID belongs to another farm" }
        val merged = foldAccount(database.accessHistoryIncludingCurrent(op))
        val existing = database.localAccess().account(op.farmId, op.entityId)
        database.localAccess().upsertAccount(
            merged.copy(failedAttempts = existing?.failedAttempts ?: 0, lockedUntilEpochMillis = existing?.lockedUntilEpochMillis),
        )
    },
    RECOVERY_SET_COMMAND to OperationApplier { database, op ->
        database.requireAccessReceipt(op, RECOVERY_SET_COMMAND)
        op.toRecoveryEntity()
        val history = database.accessHistoryIncludingCurrent(op)
        val winner = requireNotNull(latestRecoveryChange(history)).toRecoveryEntity()
        val existing = database.localAccess().recovery(op.farmId)
        // Preserve a newer retained legacy projection; equal-time journal history uses the full
        // canonical order, so opposite delivery order cannot choose different recovery codes.
        if (existing == null || winner.updatedAtEpochMillis >= existing.updatedAtEpochMillis) {
            database.localAccess().upsertRecovery(winner)
        }
    },
    AUDIT_COMMAND to OperationApplier { database, op ->
        database.requireAccessReceipt(op, AUDIT_COMMAND)
        val event = op.boundPayload("access_audit")
        require(event.getString("farmId") == op.farmId && event.getString("eventId") == op.entityId) { "Audit identity does not match its operation" }
        require(event.getLong("atEpochMillis") == op.businessTimeEpochMillis) { "Audit time does not match its operation" }
        val actor = event.nullableString("actorAccountId")
        require((actor ?: "local-access") == op.actorId) { "Audit actor does not match its operation" }
        val entity = AccessAuditEntity(op.entityId, op.farmId, actor, event.nullableString("subjectAccountId"),
            event.getString("action"), event.getString("detail"), op.businessTimeEpochMillis)
        val existing = database.localAccess().auditEvent(op.entityId)
        require(existing == null || existing == entity) { "Access audit ID already identifies a different event" }
        database.localAccess().insertAuditIfAbsent(entity)
    },
)

private suspend fun FarmOsDatabase.requireAccessReceipt(op: OperationEnvelope, command: String) {
    check(inTransaction()) { "Access replay requires a Room transaction" }
    require(op.operationType == command && op.checksumValid()) { "Invalid access operation" }
    val recorded = replication().operation(op.farmId, op.operationId)
    require(recorded?.toEnvelope() == op) { "Access replay requires the exact admitted journal receipt" }
    val application = replicationApplications().get(op.farmId, op.operationId)
    require(application?.state != ApplicationState.SET_ASIDE.name) { "A set-aside access receipt cannot be applied" }
    val admitted = when (application?.state) {
        ApplicationState.APPLIED.name, ApplicationState.FAILED.name, ApplicationState.AWAITING_APPLIER.name -> true
        null -> replication().device(op.farmId, op.deviceId)?.isLocal == true
        else -> false
    }
    require(admitted) { "Access replay requires a local receipt or a received application record" }
}

/** The scheduler is applying this exact admitted receipt now; no other pending effect is borrowed. */
private fun FarmOsDatabase.accessHistoryIncludingCurrent(op: OperationEnvelope): List<OperationEnvelope> =
    (localAccess().appliedAccessHistory(op.farmId, op.entityType, op.entityId).map { it.toEnvelope() } + op)
        .distinctBy { it.operationId }

private fun OperationEnvelope.boundPayload(entity: String): JSONObject {
    require(schemaVersion == 1 && entityType == entity && payload.keys == setOf(COMMAND_PAYLOAD_KEY)) { "Unsupported access operation shape" }
    return JSONObject(payload.getValue(COMMAND_PAYLOAD_KEY))
}

private fun OperationEnvelope.accountChange(): JSONObject {
    require(operationType == ACCOUNT_SET_COMMAND) { "Not an account change" }
    val fields = boundPayload(ACCOUNT_ENTITY)
    require(fields.getString("farmId") == farmId && fields.getString("accountId") == entityId) { "Account identity does not match its operation" }
    val changed = fields.getJSONArray("changed")
    val names = List(changed.length()) { changed.getString(it) }
    require(names.isNotEmpty() && names.distinct().size == names.size && names.all { it in IDENTITY_FIELDS }) { "Invalid account identity fields" }
    // Validate the complete historical record before it can become the initial fold or contribute
    // selected fields. An invalid pending receipt never poisons otherwise valid account history.
    fields.toAccountEntity(businessTimeEpochMillis)
    return fields
}

internal fun foldAccount(operations: List<OperationEnvelope>): LocalAccountEntity {
    val history = operations.mapNotNull { op -> runCatching { op to op.accountChange() }.getOrNull() }
        .sortedWith { a, b -> CanonicalOperationOrder.compare(a.first, b.first) }
    require(history.isNotEmpty()) { "No valid account changes to fold" }
    val folded = JSONObject(history.first().second.toString())
    history.forEach { (_, fields) ->
        val changed = fields.getJSONArray("changed")
        for (i in 0 until changed.length()) folded.put(changed.getString(i), fields.get(changed.getString(i)))
    }
    return folded.toAccountEntity(history.last().first.businessTimeEpochMillis)
}

internal fun latestRecoveryChange(operations: List<OperationEnvelope>): OperationEnvelope? =
    operations.filter { runCatching { it.toRecoveryEntity() }.isSuccess }.maxWithOrNull(CanonicalOperationOrder)

internal fun OperationEnvelope.toRecoveryEntity(): FarmRecoveryEntity {
    require(operationType == RECOVERY_SET_COMMAND) { "Not an owner recovery change" }
    val fields = boundPayload(RECOVERY_ENTITY)
    require(entityId == farmId && fields.getString("farmId") == farmId) { "Recovery identity does not match its operation" }
    val hash = fields.getString("recoveryHash")
    require(hash.isNotBlank()) { "A recovery hash is required" }
    return FarmRecoveryEntity(farmId, hash, businessTimeEpochMillis)
}

private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else getString(key)

private fun JSONObject.toAccountEntity(updatedAt: Long): LocalAccountEntity {
    require(getString("username").isNotBlank() && getString("displayName").isNotBlank() && getString("credentialHash").isNotBlank()) { "Incomplete account identity" }
    return LocalAccountEntity(
        accountId = getString("accountId"), farmId = getString("farmId"), username = getString("username"),
        displayName = getString("displayName"), role = LocalRole.valueOf(getString("role")).name,
        status = AccountStatus.valueOf(getString("status")).name,
        credentialKind = CredentialKind.valueOf(getString("credentialKind")).name,
        credentialHash = getString("credentialHash"), failedAttempts = 0, lockedUntilEpochMillis = null,
        workerId = nullableString("workerId"), createdAtEpochMillis = getLong("createdAtEpochMillis"),
        updatedAtEpochMillis = updatedAt,
    )
}
