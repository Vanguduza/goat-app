package com.farmos.domain.access

import java.security.SecureRandom

enum class AccountStatus { ACTIVE, DISABLED }

/**
 * A local GOAT account on one farm. [workerId] optionally links the account to an existing worker record,
 * so historical work stays attributed when a worker later gets a login.
 */
data class LocalAccount(
    val accountId: String,
    val farmId: String,
    val username: String,
    val displayName: String,
    val role: LocalRole,
    val status: AccountStatus,
    val credentialKind: CredentialKind,
    val credentialHash: String,
    val failedAttempts: Int = 0,
    val lockedUntilEpochMillis: Long? = null,
    val workerId: String? = null,
    val createdAtEpochMillis: Long,
)

/** One access-history entry. Secrets are never recorded. */
data class AccessAuditEvent(
    val eventId: String,
    val farmId: String,
    val actorAccountId: String?,
    val subjectAccountId: String?,
    val action: AccessAction,
    val detail: String,
    val atEpochMillis: Long,
)

enum class AccessAction {
    FARM_SET_UP,
    SIGNED_IN,
    SIGN_IN_FAILED,
    SIGN_IN_LOCKED,
    ACCOUNT_CREATED,
    ROLE_CHANGED,
    ACCOUNT_ENABLED,
    ACCOUNT_DISABLED,
    CREDENTIAL_RESET,
    WORKER_LINKED,
    OWNER_RECOVERED,
    RECOVERY_FAILED,
}

/** Persistence port for local access data; the Room adapter implements it. */
interface LocalAccessStore {
    fun accounts(farmId: String): List<LocalAccount>
    fun account(farmId: String, accountId: String): LocalAccount?
    fun save(account: LocalAccount)
    fun recoveryHash(farmId: String): String?
    fun saveRecoveryHash(farmId: String, hash: String)
    fun record(event: AccessAuditEvent)
}

class AccessDenied(message: String) : IllegalStateException(message)

data class FarmSetupResult(val owner: LocalAccount, val recoveryCode: String)

sealed interface SignInResult {
    data class SignedIn(val account: LocalAccount) : SignInResult
    data object InvalidCredentials : SignInResult
    data class Locked(val untilEpochMillis: Long) : SignInResult
    data object Disabled : SignInResult
}

sealed interface RecoveryResult {
    /** The owner's credential was replaced; [newRecoveryCode] replaces the used code and is shown once. */
    data class Recovered(val owner: LocalAccount, val newRecoveryCode: String) : RecoveryResult
    data object InvalidCode : RecoveryResult
}

/**
 * Local accounts, roles and permissions for one device's farms. There is no central authentication
 * service: accounts, credential hashes and the owner recovery code hash live in local storage, and every
 * administrative action is authorised against the acting account's role and audited.
 */
class LocalAccessService(
    private val store: LocalAccessStore,
    private val hasher: CredentialHasher,
    private val clock: () -> Long,
    private val newId: () -> String,
    private val random: SecureRandom = SecureRandom(),
) {
    /**
     * First farm setup: creates the Owner account and the owner recovery code. Works offline and is
     * refused once the farm has any account.
     */
    fun setUpFarm(farmId: String, username: String, displayName: String, credential: Credential): FarmSetupResult {
        check(store.accounts(farmId).isEmpty()) { "This farm already has accounts" }
        val owner = newAccount(farmId, username, displayName, LocalRole.OWNER, credential, workerId = null)
        store.save(owner)
        val code = RecoveryCode.generate(random)
        store.saveRecoveryHash(farmId, hasher.hash(RecoveryCode.normalise(code)))
        audit(farmId, owner.accountId, owner.accountId, AccessAction.FARM_SET_UP, "Owner account created")
        return FarmSetupResult(owner, code)
    }

    fun signIn(farmId: String, username: String, secret: String): SignInResult {
        val account = findByUsername(farmId, username)
        if (account == null) {
            audit(farmId, null, null, AccessAction.SIGN_IN_FAILED, "Unknown username")
            return SignInResult.InvalidCredentials
        }
        if (account.status == AccountStatus.DISABLED) {
            audit(farmId, account.accountId, account.accountId, AccessAction.SIGN_IN_FAILED, "Account disabled")
            return SignInResult.Disabled
        }
        val now = clock()
        account.lockedUntilEpochMillis?.takeIf { it > now }?.let {
            audit(farmId, account.accountId, account.accountId, AccessAction.SIGN_IN_LOCKED, "Locked")
            return SignInResult.Locked(it)
        }
        if (!hasher.verify(secret, account.credentialHash)) {
            val failures = account.failedAttempts + 1
            val lockedUntil = if (failures >= LOCKOUT_THRESHOLD) now + lockoutMillis(failures) else null
            store.save(account.copy(failedAttempts = failures, lockedUntilEpochMillis = lockedUntil))
            audit(farmId, account.accountId, account.accountId, AccessAction.SIGN_IN_FAILED, "Wrong credential ($failures)")
            return lockedUntil?.let { SignInResult.Locked(it) } ?: SignInResult.InvalidCredentials
        }
        val cleared = account.copy(failedAttempts = 0, lockedUntilEpochMillis = null)
        store.save(cleared)
        audit(farmId, account.accountId, account.accountId, AccessAction.SIGNED_IN, "Signed in")
        return SignInResult.SignedIn(cleared)
    }

    fun createAccount(
        actor: LocalAccount,
        username: String,
        displayName: String,
        role: LocalRole,
        credential: Credential,
        workerId: String? = null,
    ): LocalAccount {
        authorise(actor, Permission.MANAGE_ACCOUNTS)
        if (role == LocalRole.OWNER) authorise(actor, Permission.MANAGE_OWNERS)
        val account = newAccount(actor.farmId, username, displayName, role, credential, workerId)
        store.save(account)
        audit(actor.farmId, actor.accountId, account.accountId, AccessAction.ACCOUNT_CREATED, "Created as ${role.name}")
        return account
    }

    fun changeRole(actor: LocalAccount, accountId: String, role: LocalRole): LocalAccount {
        authorise(actor, Permission.MANAGE_ACCOUNTS)
        val subject = subject(actor, accountId)
        if (role == LocalRole.OWNER || subject.role == LocalRole.OWNER) authorise(actor, Permission.MANAGE_OWNERS)
        if (subject.role == LocalRole.OWNER && role != LocalRole.OWNER) requireAnotherActiveOwner(subject)
        val updated = subject.copy(role = role)
        store.save(updated)
        audit(actor.farmId, actor.accountId, accountId, AccessAction.ROLE_CHANGED, "${subject.role.name} to ${role.name}")
        return updated
    }

    fun setStatus(actor: LocalAccount, accountId: String, status: AccountStatus): LocalAccount {
        authorise(actor, Permission.MANAGE_ACCOUNTS)
        val subject = subject(actor, accountId)
        if (subject.role == LocalRole.OWNER) authorise(actor, Permission.MANAGE_OWNERS)
        if (status == AccountStatus.DISABLED && subject.role == LocalRole.OWNER) requireAnotherActiveOwner(subject)
        val updated = subject.copy(status = status, failedAttempts = 0, lockedUntilEpochMillis = null)
        store.save(updated)
        val action = if (status == AccountStatus.ACTIVE) AccessAction.ACCOUNT_ENABLED else AccessAction.ACCOUNT_DISABLED
        audit(actor.farmId, actor.accountId, accountId, action, status.name)
        return updated
    }

    /** Management resets a user's PIN or password; an Owner's credential can only be reset by an Owner. */
    fun resetCredential(actor: LocalAccount, accountId: String, credential: Credential): LocalAccount {
        authorise(actor, Permission.RESET_USER_CREDENTIALS)
        val subject = subject(actor, accountId)
        if (subject.role == LocalRole.OWNER && subject.accountId != actor.accountId) authorise(actor, Permission.MANAGE_OWNERS)
        val updated = subject.copy(
            credentialKind = credential.kind,
            credentialHash = hashChecked(credential),
            failedAttempts = 0,
            lockedUntilEpochMillis = null,
        )
        store.save(updated)
        audit(actor.farmId, actor.accountId, accountId, AccessAction.CREDENTIAL_RESET, credential.kind.name)
        return updated
    }

    fun linkWorker(actor: LocalAccount, accountId: String, workerId: String): LocalAccount {
        authorise(actor, Permission.MANAGE_WORKERS)
        val updated = subject(actor, accountId).copy(workerId = workerId)
        store.save(updated)
        audit(actor.farmId, actor.accountId, accountId, AccessAction.WORKER_LINKED, "Linked to worker $workerId")
        return updated
    }

    /**
     * Owner recovery without any central service: the one-time recovery code replaces the Owner's
     * credential, clears any lockout, and is itself replaced by a new code shown once.
     */
    fun recoverOwner(farmId: String, ownerAccountId: String, recoveryCode: String, credential: Credential): RecoveryResult {
        val stored = store.recoveryHash(farmId)
        val owner = store.account(farmId, ownerAccountId)?.takeIf { it.role == LocalRole.OWNER }
        if (stored == null || owner == null || !hasher.verify(RecoveryCode.normalise(recoveryCode), stored)) {
            audit(farmId, null, ownerAccountId, AccessAction.RECOVERY_FAILED, "Recovery code rejected")
            return RecoveryResult.InvalidCode
        }
        val recovered = owner.copy(
            status = AccountStatus.ACTIVE,
            credentialKind = credential.kind,
            credentialHash = hashChecked(credential),
            failedAttempts = 0,
            lockedUntilEpochMillis = null,
        )
        store.save(recovered)
        val next = RecoveryCode.generate(random)
        store.saveRecoveryHash(farmId, hasher.hash(RecoveryCode.normalise(next)))
        audit(farmId, ownerAccountId, ownerAccountId, AccessAction.OWNER_RECOVERED, "Owner credential replaced; recovery code rotated")
        return RecoveryResult.Recovered(recovered, next)
    }

    private fun newAccount(
        farmId: String,
        username: String,
        displayName: String,
        role: LocalRole,
        credential: Credential,
        workerId: String?,
    ): LocalAccount {
        val normalised = username.trim().lowercase()
        require(normalised.isNotEmpty()) { "A username is required" }
        require(displayName.isNotBlank()) { "A display name is required" }
        require(findByUsername(farmId, normalised) == null) { "That username is already used on this farm" }
        return LocalAccount(
            accountId = newId(),
            farmId = farmId,
            username = normalised,
            displayName = displayName.trim(),
            role = role,
            status = AccountStatus.ACTIVE,
            credentialKind = credential.kind,
            credentialHash = hashChecked(credential),
            workerId = workerId,
            createdAtEpochMillis = clock(),
        )
    }

    private fun hashChecked(credential: Credential): String {
        CredentialPolicy.problem(credential)?.let { throw IllegalArgumentException(it) }
        return hasher.hash(credential.secret)
    }

    private fun findByUsername(farmId: String, username: String): LocalAccount? {
        val normalised = username.trim().lowercase()
        return store.accounts(farmId).firstOrNull { it.username == normalised }
    }

    private fun subject(actor: LocalAccount, accountId: String): LocalAccount =
        store.account(actor.farmId, accountId) ?: throw IllegalArgumentException("Unknown account on this farm")

    private fun authorise(actor: LocalAccount, permission: Permission) {
        val current = store.account(actor.farmId, actor.accountId)
        if (current == null || current.status != AccountStatus.ACTIVE || !RolePermissions.allows(current.role, permission)) {
            throw AccessDenied("This account may not ${permission.name.lowercase().replace('_', ' ')}")
        }
    }

    private fun requireAnotherActiveOwner(subject: LocalAccount) {
        val others = store.accounts(subject.farmId).count {
            it.accountId != subject.accountId && it.role == LocalRole.OWNER && it.status == AccountStatus.ACTIVE
        }
        if (others == 0) throw AccessDenied("A farm must keep at least one active Owner")
    }

    private fun audit(farmId: String, actor: String?, subject: String?, action: AccessAction, detail: String) {
        store.record(AccessAuditEvent(newId(), farmId, actor, subject, action, detail, clock()))
    }

    private fun lockoutMillis(failures: Int): Long {
        val doublings = (failures - LOCKOUT_THRESHOLD).coerceIn(0, 4)
        return BASE_LOCKOUT_MILLIS shl doublings
    }

    companion object {
        const val LOCKOUT_THRESHOLD = 5
        const val BASE_LOCKOUT_MILLIS = 5 * 60_000L
    }
}
