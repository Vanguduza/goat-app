package com.farmos.app

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * First-run Drive setup logic that runs without the Android framework.
 *
 * UNEXECUTED: this runtime has no JVM/Gradle/Android toolchain, so these tests were written but
 * not executed here. The Android-dependent parts are not covered at all: the SharedPreferences
 * plumbing of [DriveConfigStore] and [DriveSetupFlags], and the Compose states of the first-run
 * Drive setup screen (idle / connecting / sign-in-unavailable / failed).
 */
class DriveSetupAttemptTest {
    private val farmId = "farm-1"

    private fun config() = DriveGatewayConfig(
        accountEmail = "owner@example.com",
        folderId = "folder-abc",
        folderName = "Farm OS",
        connectedAtEpochMillis = 1_700_000_000_000L,
    )

    // --- Config codec round-trip (pure; the SharedPreferences layer around it is Android-only) ---

    @Test
    fun `config codec round-trips every field`() {
        val decoded = DriveGatewayConfigCodec.decode(DriveGatewayConfigCodec.encode(config()))
        assertEquals(config(), decoded)
    }

    @Test
    fun `config codec decode returns null when the account is missing`() {
        val values = DriveGatewayConfigCodec.encode(config()).toMutableMap()
        values.remove("account")
        assertNull(DriveGatewayConfigCodec.decode(values))
    }

    @Test
    fun `config codec decode returns null when the folder is missing`() {
        val values = DriveGatewayConfigCodec.encode(config()).toMutableMap()
        values.remove("folder")
        assertNull(DriveGatewayConfigCodec.decode(values))
    }

    @Test
    fun `config codec decode defaults the folder name and timestamp`() {
        val decoded = DriveGatewayConfigCodec.decode(mapOf("account" to "a@b.c", "folder" to "f"))
        assertEquals(DriveGatewayConfig("a@b.c", "f", "", 0), decoded)
    }

    @Test
    fun `config keys are stable and farm-scoped`() {
        assertEquals("drive_cfg_farm-1_account", DriveConfigKeys.configKey("farm-1", "account"))
        assertEquals("drive_cfg_farm-2_account", DriveConfigKeys.configKey("farm-2", "account"))
        assertEquals("drive_setup_dismissed_farm-1", DriveConfigKeys.dismissedKey("farm-1"))
    }

    // --- Skip flag (in-memory storage; the SharedPreferences adapter is Android-only) ---

    @Test
    fun `skip flag starts clear and is durable per farm`() {
        val flags = DriveSetupFlags(InMemoryDriveFlagStorage())
        assertFalse(flags.isDismissed(farmId))
        flags.setDismissed(farmId, true)
        assertTrue(flags.isDismissed(farmId))
        assertFalse(flags.isDismissed("other-farm"))
        flags.setDismissed(farmId, false)
        assertFalse(flags.isDismissed(farmId))
    }

    // --- Connect attempts ---

    private class FakeAuthorizer(
        private val token: String?,
        private val throwAuth: Boolean = false,
    ) : DriveAuthorizer {
        override suspend fun accessToken(): String? {
            if (throwAuth) throw DriveAuthNeededException()
            return token
        }
    }

    private class FakeStore(
        private val onList: (String) -> List<DriveObjectStore.DriveObject> = { emptyList() },
    ) : DriveObjectStore {
        var listedPrefix: String? = null

        override suspend fun list(prefix: String): List<DriveObjectStore.DriveObject> {
            listedPrefix = prefix
            return onList(prefix)
        }

        override suspend fun read(path: String): ByteArray? = null

        override suspend fun putIfAbsent(path: String, bytes: ByteArray, sha256: String): Boolean = true
    }

    private fun connect(
        authorizer: DriveAuthorizer,
        store: FakeStore = FakeStore(),
        now: Long = 1_700_000_000_000L,
    ): DriveSetupOutcome = runBlocking {
        DriveSetupAttempt.connect(
            farmId = farmId,
            accountEmail = "owner@example.com",
            folderName = "Farm OS",
            folderId = "folder-abc",
            authorizer = authorizer,
            nowEpochMillis = now,
            openStore = { _, _ -> store },
        )
    }

    @Test
    fun `no authorizer reports sign-in unavailable instead of succeeding`() {
        assertIs<DriveSetupOutcome.AuthUnavailable>(connect(NoDriveAuthorizer))
    }

    @Test
    fun `a null token reports sign-in unavailable`() {
        assertIs<DriveSetupOutcome.AuthUnavailable>(connect(FakeAuthorizer(null)))
    }

    @Test
    fun `an authorizer that raises auth-needed reports sign-in unavailable`() {
        assertIs<DriveSetupOutcome.AuthUnavailable>(connect(FakeAuthorizer(null, throwAuth = true)))
    }

    @Test
    fun `a signed-in attempt verifies the farm journal prefix through the gateway`() {
        val store = FakeStore()
        val outcome = connect(FakeAuthorizer("token"), store)
        assertEquals("GOAT/farms/$farmId/", store.listedPrefix)
        val connected = assertIs<DriveSetupOutcome.Connected>(outcome)
        assertEquals(
            DriveGatewayConfig("owner@example.com", "folder-abc", "Farm OS", 1_700_000_000_000L),
            connected.config,
        )
    }

    @Test
    fun `connected timestamp comes from the injected business clock, not the wall clock`() {
        val outcome = connect(FakeAuthorizer("token"), now = 42L)
        assertEquals(42L, assertIs<DriveSetupOutcome.Connected>(outcome).config.connectedAtEpochMillis)
    }

    @Test
    fun `a revoked token during verification reports sign-in unavailable`() {
        val store = FakeStore(onList = { throw DriveAuthNeededException() })
        assertIs<DriveSetupOutcome.AuthUnavailable>(connect(FakeAuthorizer("token"), store))
    }

    @Test
    fun `a network failure during verification is a retryable failure`() {
        val store = FakeStore(onList = { throw IOException("network down") })
        val outcome = assertIs<DriveSetupOutcome.Failed>(connect(FakeAuthorizer("token"), store))
        assertEquals("network down", outcome.reason)
    }
}
