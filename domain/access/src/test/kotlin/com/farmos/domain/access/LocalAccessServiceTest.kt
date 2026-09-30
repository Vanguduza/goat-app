package com.farmos.domain.access

import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalAccessServiceTest {
    private class MemoryStore : LocalAccessStore {
        val accounts = LinkedHashMap<String, LocalAccount>()
        val recovery = HashMap<String, String>()
        val events = mutableListOf<AccessAuditEvent>()
        override fun accounts(farmId: String) = accounts.values.filter { it.farmId == farmId }
        override fun account(farmId: String, accountId: String) = accounts[accountId]?.takeIf { it.farmId == farmId }
        override fun save(account: LocalAccount) { accounts[account.accountId] = account }
        override fun recoveryHash(farmId: String) = recovery[farmId]
        override fun saveRecoveryHash(farmId: String, hash: String) { recovery[farmId] = hash }
        override fun record(event: AccessAuditEvent) { events += event }
    }

    private val farm = "farm-premier"
    private val store = MemoryStore()
    private var now = 1_000_000L
    private var ids = 0
    private val service = LocalAccessService(store, CredentialHasher(iterations = 1_000), { now }, { "id-${++ids}" }, SecureRandom())

    private fun pin(value: String) = Credential(CredentialKind.PIN, value)

    private fun ownerSetup() = service.setUpFarm(farm, "Tendai", "Tendai Moyo", pin("482913"))

    @Test
    fun farmSetupCreatesTheOwnerOfflineAndRefusesASecondSetup() {
        val setup = ownerSetup()
        assertEquals(LocalRole.OWNER, setup.owner.role)
        assertEquals("tendai", setup.owner.username)
        assertTrue(Regex("[0-9A-Z]{4}(-[0-9A-Z]{4}){4}").matches(setup.recoveryCode))
        assertFalse(store.recovery.getValue(farm).contains(setup.recoveryCode))
        assertFalse(setup.owner.credentialHash.contains("482913"))
        assertFailsWith<IllegalStateException> { service.setUpFarm(farm, "other", "Other", pin("739104")) }
    }

    @Test
    fun signInSucceedsFailsAndLocksOutThenRecoversAfterTheLock() {
        ownerSetup()
        assertIs<SignInResult.SignedIn>(service.signIn(farm, "TENDAI ", "482913"))
        repeat(4) { assertEquals(SignInResult.InvalidCredentials, service.signIn(farm, "tendai", "000111")) }
        val locked = service.signIn(farm, "tendai", "000111")
        assertIs<SignInResult.Locked>(locked)
        assertIs<SignInResult.Locked>(service.signIn(farm, "tendai", "482913"))
        now = locked.untilEpochMillis + 1
        assertIs<SignInResult.SignedIn>(service.signIn(farm, "tendai", "482913"))
        assertEquals(SignInResult.InvalidCredentials, service.signIn(farm, "nobody", "482913"))
        assertTrue(store.events.any { it.action == AccessAction.SIGN_IN_LOCKED })
        assertTrue(store.events.none { it.detail.contains("482913") || it.detail.contains("000111") })
    }

    @Test
    fun managementCreatesAccountsButOnlyOwnersManageOwners() {
        val owner = ownerSetup().owner
        val manager = service.createAccount(owner, "rudo", "Rudo Manager", LocalRole.MANAGER, pin("573920"))
        val worker = service.createAccount(manager, "farai", "Farai", LocalRole.WORKER, pin("681204"), workerId = "worker-7")

        assertEquals("worker-7", worker.workerId)
        assertFailsWith<AccessDenied> { service.createAccount(worker, "x", "X", LocalRole.WORKER, pin("905172")) }
        assertFailsWith<AccessDenied> { service.createAccount(manager, "o2", "Owner Two", LocalRole.OWNER, pin("905172")) }
        assertFailsWith<AccessDenied> { service.resetCredential(manager, owner.accountId, pin("905172")) }
        assertFailsWith<AccessDenied> { service.setStatus(manager, owner.accountId, AccountStatus.DISABLED) }
        assertFailsWith<IllegalArgumentException> { service.createAccount(owner, "RUDO", "Dup", LocalRole.WORKER, pin("905172")) }

        service.resetCredential(manager, worker.accountId, pin("710396"))
        assertIs<SignInResult.SignedIn>(service.signIn(farm, "farai", "710396"))
        service.setStatus(manager, worker.accountId, AccountStatus.DISABLED)
        assertEquals(SignInResult.Disabled, service.signIn(farm, "farai", "710396"))
        // A disabled manager can no longer administer anything.
        service.setStatus(owner, manager.accountId, AccountStatus.DISABLED)
        assertFailsWith<AccessDenied> { service.setStatus(manager, worker.accountId, AccountStatus.ACTIVE) }
    }

    @Test
    fun theFarmAlwaysKeepsAnActiveOwner() {
        val owner = ownerSetup().owner
        assertFailsWith<AccessDenied> { service.setStatus(owner, owner.accountId, AccountStatus.DISABLED) }
        assertFailsWith<AccessDenied> { service.changeRole(owner, owner.accountId, LocalRole.MANAGER) }
        val second = service.createAccount(owner, "chipo", "Chipo", LocalRole.OWNER, pin("836152"))
        service.changeRole(second, owner.accountId, LocalRole.MANAGER)
        assertEquals(LocalRole.MANAGER, store.account(farm, owner.accountId)!!.role)
    }

    @Test
    fun ownerRecoveryUsesTheOneTimeCodeAndRotatesIt() {
        val setup = ownerSetup()
        repeat(5) { service.signIn(farm, "tendai", "000111") }
        assertEquals(RecoveryResult.InvalidCode, service.recoverOwner(farm, setup.owner.accountId, "AAAA-AAAA-AAAA-AAAA-AAAA", pin("591047")))

        val typed = setup.recoveryCode.lowercase().replace("-", " ")
        val recovered = service.recoverOwner(farm, setup.owner.accountId, typed, pin("591047"))
        assertIs<RecoveryResult.Recovered>(recovered)
        assertNotEquals(setup.recoveryCode, recovered.newRecoveryCode)
        assertIs<SignInResult.SignedIn>(service.signIn(farm, "tendai", "591047"))
        assertEquals(RecoveryResult.InvalidCode, service.recoverOwner(farm, setup.owner.accountId, setup.recoveryCode, pin("264819")))
        assertIs<RecoveryResult.Recovered>(service.recoverOwner(farm, setup.owner.accountId, recovered.newRecoveryCode, pin("264819")))
    }

    @Test
    fun accountsAndRecoveryAreFarmScoped() {
        val setup = ownerSetup()
        service.setUpFarm("farm-other", "tendai", "Other Owner", pin("319574"))
        assertIs<SignInResult.SignedIn>(service.signIn("farm-other", "tendai", "319574"))
        assertEquals(SignInResult.InvalidCredentials, service.signIn("farm-other", "tendai", "482913"))
        assertEquals(RecoveryResult.InvalidCode, service.recoverOwner("farm-other", setup.owner.accountId, setup.recoveryCode, pin("591047")))
    }

    @Test
    fun weakCredentialsAreRefused() {
        assertEquals("A PIN must be 6 to 12 digits", CredentialPolicy.problem(pin("1234")))
        assertEquals("A PIN cannot repeat one digit", CredentialPolicy.problem(pin("777777")))
        assertEquals("A PIN cannot be a simple sequence", CredentialPolicy.problem(pin("123456")))
        assertEquals("A PIN cannot be a simple sequence", CredentialPolicy.problem(pin("9876543")))
        assertNull(CredentialPolicy.problem(pin("482913")))
        assertEquals("A password must be at least 10 characters", CredentialPolicy.problem(Credential(CredentialKind.PASSWORD, "short")))
        assertFailsWith<IllegalArgumentException> { service.setUpFarm(farm, "tendai", "Tendai", pin("111111")) }
        assertTrue(store.accounts(farm).isEmpty())
    }

    @Test
    fun hashesAreSaltedVersionedAndVerifiedInConstantForm() {
        val hasher = CredentialHasher(iterations = 1_000)
        val first = hasher.hash("482913")
        val second = hasher.hash("482913")
        assertNotEquals(first, second)
        assertTrue(first.startsWith("pbkdf2-sha256$1000$"))
        assertTrue(hasher.verify("482913", first))
        assertFalse(hasher.verify("482914", first))
        assertTrue(CredentialHasher(iterations = 5_000).verify("482913", first))
        assertFalse(hasher.verify("482913", "not-a-hash"))
        assertEquals("01AB-1", RecoveryCode.normalise("oiab- l").let { it.substring(0, 4) + "-" + it.substring(4) })
    }

    @Test
    fun rolePermissionsGiveManagementAdministrationAndWorkersFieldWork() {
        assertTrue(RolePermissions.allows(LocalRole.WORKER, Permission.RECORD_FARM_WORK))
        assertTrue(RolePermissions.allows(LocalRole.WORKER, Permission.CAPTURE_STOCK_COUNT))
        assertFalse(RolePermissions.allows(LocalRole.WORKER, Permission.POST_STOCK_ADJUSTMENT))
        assertFalse(RolePermissions.allows(LocalRole.VIEWER, Permission.RECORD_FARM_WORK))
        assertTrue(RolePermissions.allows(LocalRole.MANAGER, Permission.APPROVE_DEVICE_PAIRING))
        assertFalse(RolePermissions.allows(LocalRole.MANAGER, Permission.MANAGE_OWNERS))
        assertEquals(Permission.entries.toSet(), RolePermissions.of(LocalRole.OWNER))
    }
}
