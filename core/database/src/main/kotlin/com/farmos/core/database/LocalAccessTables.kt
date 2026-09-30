package com.farmos.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/** A farm created on this device; its identity replicates to paired devices. */
@Entity(tableName = "local_farms")
data class LocalFarmEntity(
    @PrimaryKey val farmId: String,
    val name: String,
    val createdAtEpochMillis: Long,
)

/** A local GOAT account. Only the salted credential hash is stored, never the PIN or password. */
@Entity(
    tableName = "local_accounts",
    indices = [Index(value = ["farmId", "username"], unique = true)],
)
data class LocalAccountEntity(
    @PrimaryKey val accountId: String,
    val farmId: String,
    val username: String,
    val displayName: String,
    val role: String,
    val status: String,
    val credentialKind: String,
    val credentialHash: String,
    val failedAttempts: Int,
    val lockedUntilEpochMillis: Long?,
    val workerId: String?,
    val createdAtEpochMillis: Long,
    /** Business time of the last replicated identity change; later changes from any device win. */
    @ColumnInfo(defaultValue = "0") val updatedAtEpochMillis: Long = 0,
)

/** The hash of the farm's current one-time owner recovery code. */
@Entity(tableName = "farm_recovery")
data class FarmRecoveryEntity(
    @PrimaryKey val farmId: String,
    val recoveryHash: String,
    @ColumnInfo(defaultValue = "0") val updatedAtEpochMillis: Long = 0,
)

/** Access history: sign-ins, failures and every administrative action. Never contains secrets. */
@Entity(
    tableName = "access_audit",
    indices = [Index(value = ["farmId", "atEpochMillis"])],
)
data class AccessAuditEntity(
    @PrimaryKey val eventId: String,
    val farmId: String,
    val actorAccountId: String?,
    val subjectAccountId: String?,
    val action: String,
    val detail: String,
    val atEpochMillis: Long,
)

/** Blocking DAO: the access service runs on a background dispatcher and needs synchronous reads. */
@Dao
interface LocalAccessDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertFarm(farm: LocalFarmEntity)

    @Query("SELECT * FROM local_farms ORDER BY name, farmId")
    fun farms(): List<LocalFarmEntity>

    @Query("SELECT * FROM local_accounts WHERE farmId = :farmId ORDER BY username")
    fun accounts(farmId: String): List<LocalAccountEntity>

    @Query("SELECT * FROM local_accounts WHERE farmId = :farmId AND accountId = :accountId LIMIT 1")
    fun account(farmId: String, accountId: String): LocalAccountEntity?

    @Upsert
    fun upsertAccount(account: LocalAccountEntity)

    @Query("SELECT recoveryHash FROM farm_recovery WHERE farmId = :farmId LIMIT 1")
    fun recoveryHash(farmId: String): String?

    @Upsert
    fun upsertRecovery(recovery: FarmRecoveryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertAudit(event: AccessAuditEntity)

    /** Replicated farms and audit events arrive at most once per id; a repeat changes nothing. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertFarmIfAbsent(farm: LocalFarmEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAuditIfAbsent(event: AccessAuditEntity)

    @Query("SELECT * FROM farm_recovery WHERE farmId = :farmId LIMIT 1")
    fun recovery(farmId: String): FarmRecoveryEntity?

    @Query("SELECT * FROM access_audit WHERE farmId = :farmId ORDER BY atEpochMillis DESC, eventId LIMIT :limit")
    fun audit(farmId: String, limit: Int): List<AccessAuditEntity>

    @Query("SELECT COUNT(*) FROM access_audit WHERE farmId = :farmId")
    fun auditCount(farmId: String): Long
}
