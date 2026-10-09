package com.farmos.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import java.io.Closeable
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking

/** Two independent device databases, sealed vaults and preference stores using the real Drive runtime. */
internal class DriveRotationTestDevice(
    val farm: String,
    val device: String,
    val carrier: MemoryDriveCarrier,
    provision: Boolean = true,
) : Closeable {
    val local = KeyRotationTestFixture(farm, device, provision)
    val database get() = local.database
    val vault get() = local.vault
    private val preferencePrefix = "drive-rotation-" + UUID.randomUUID()
    val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            super.getSharedPreferences(preferencePrefix + "-" + name, mode)
    }
    var now = 1_800_000_000_000L
    val runtime = FarmDriveRuntime(
        context, database, vault, farm, device, FileAttachmentStore(File(local.directory, "attachments")),
        authorizer = CountingDriveAuthorizer(), clock = { now }, coordinator = DriveFarmCoordinator(),
        online = { true }, openStore = { _, _ -> carrier },
    )

    init {
        runBlocking {
            DriveGatewayAuthority(database, device).approve(
                farm, "owner", DriveGatewayConfig("owner@example.com", "test-folder", "Test", now), DriveConfigStore(context),
            )
        }
    }

    fun secrets(): FarmSecrets = requireNotNull(vault.secrets(farm))

    suspend fun pairWith(other: DriveRotationTestDevice) {
        require(other.farm == farm)
        val shared = secrets()
        other.vault.save(farm, FarmSecrets(shared.keys, other.secrets().device, shared.currentSinceEpochMillis))
        local.knows(other.local)
        other.local.knows(local)
    }

    override fun close() {
        runtime.close()
        local.close()
    }
}
