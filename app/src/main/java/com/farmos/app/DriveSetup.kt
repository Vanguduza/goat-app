package com.farmos.app

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * First-run Google Drive setup (FOS-ADMIN-013) and the durable "skipped" flag.
 *
 * Drive is a dumb blob store for the operation journal: the gateway copies operation bundles and
 * content-addressed attachment bytes into the owner's Drive folder. The whole database file is
 * never copied or synchronised — see [requireDriveSafePayload].
 */

/** Pure key derivation for every Drive preference, farm-scoped. Tested on the JVM. */
internal object DriveConfigKeys {
    fun configKey(farmId: String, name: String) = "drive_cfg_${farmId}_$name"
    fun dismissedKey(farmId: String) = "drive_setup_dismissed_${farmId}"
}

/**
 * Pure encode/decode of [DriveGatewayConfig] to string prefs values. [DriveConfigStore] delegates to
 * this; the SharedPreferences plumbing around it is Android-only and not covered here.
 */
internal object DriveGatewayConfigCodec {
    fun encode(config: DriveGatewayConfig): Map<String, String> = mapOf(
        "account" to config.accountEmail,
        "folder" to config.folderId,
        "folder_name" to config.folderName,
        "connected_at" to config.connectedAtEpochMillis.toString(),
    )

    /** Null when the account or folder is absent — a missing row means Drive is not connected. */
    fun decode(values: Map<String, String?>): DriveGatewayConfig? {
        val account = values["account"]?.takeIf { it.isNotBlank() } ?: return null
        val folder = values["folder"]?.takeIf { it.isNotBlank() } ?: return null
        return DriveGatewayConfig(
            accountEmail = account,
            folderId = folder,
            folderName = values["folder_name"] ?: "",
            connectedAtEpochMillis = values["connected_at"]?.toLongOrNull() ?: 0,
        )
    }
}

/** Minimal boolean storage so the dismissed flag is unit-testable without Android. */
internal interface DriveFlagStorage {
    fun getBoolean(key: String): Boolean
    fun setBoolean(key: String, value: Boolean)
}

internal class SharedPrefsDriveFlagStorage(prefs: SharedPreferences) : DriveFlagStorage {
    private val prefs = prefs

    override fun getBoolean(key: String): Boolean = prefs.getBoolean(key, false)

    override fun setBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }
}

internal class InMemoryDriveFlagStorage : DriveFlagStorage {
    private val values = mutableMapOf<String, Boolean>()

    override fun getBoolean(key: String): Boolean = values[key] == true

    override fun setBoolean(key: String, value: Boolean) {
        values[key] = value
    }
}

/**
 * The explicit, durable "Drive setup dismissed" flag, per farm. Skipping first-run Drive setup sets
 * it; a later successful connect clears it. It lives in the same "farm_drive" preferences as the
 * gateway config — never beside tokens, and tokens never live here at all.
 */
internal class DriveSetupFlags(private val storage: DriveFlagStorage) {
    constructor(context: Context) : this(
        SharedPrefsDriveFlagStorage(context.getSharedPreferences(DRIVE_SETUP_PREFS, Context.MODE_PRIVATE)),
    )

    fun isDismissed(farmId: String): Boolean = storage.getBoolean(DriveConfigKeys.dismissedKey(farmId))

    fun setDismissed(farmId: String, dismissed: Boolean) =
        storage.setBoolean(DriveConfigKeys.dismissedKey(farmId), dismissed)

    private companion object {
        const val DRIVE_SETUP_PREFS = "farm_drive"
    }
}

/** What one "Connect Google Drive" attempt produced. */
internal sealed interface DriveSetupOutcome {
    /** Authorized; the owner's folder verified through the gateway. Carries the config to persist. */
    data class Connected(val config: DriveGatewayConfig) : DriveSetupOutcome

    /** No Google sign-in is available — honest, not an error to retry blindly. */
    data object AuthUnavailable : DriveSetupOutcome

    /** Authorized (or the attempt itself) failed for another reason; safe to retry. */
    data class Failed(val reason: String) : DriveSetupOutcome
}

/**
 * Shared checked connection boundary for onboarding and Settings. Google identity only grants
 * Drive access; the existing local farm account remains the app's authority.
 */
internal object DriveSetupAttempt {
    suspend fun connect(
        farmId: String,
        accountEmail: String,
        folderName: String,
        folderId: String,
        authorizer: DriveAuthorizer,
        nowEpochMillis: Long,
        openStore: (DriveAuthorizer, () -> String) -> DriveObjectStore = ::DriveRestStore,
    ): DriveSetupOutcome {
        return try {
            if (authorizer.accessToken().isNullOrBlank()) return DriveSetupOutcome.AuthUnavailable
            require(farmId.matches(Regex("[A-Za-z0-9-]{1,64}"))) { "Invalid farm identity" }
            require(accountEmail.isNotBlank()) { "Choose the Google account for this backup" }
            var destination = folderId.trim()
            val store = openStore(authorizer) { destination }
            if (destination.isBlank()) {
                destination = store.ensureFarmFolder(farmId, folderName.trim(), accountEmail.trim())
            }
            requireDriveFolderId(destination)
            store.validateDestination(accountEmail.trim())
            store.list("GOAT/farms/$farmId/")
            DriveSetupOutcome.Connected(
                DriveGatewayConfig(accountEmail.trim(), destination, folderName.trim(), nowEpochMillis),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (auth: DriveAuthNeededException) {
            DriveSetupOutcome.AuthUnavailable
        } catch (failure: Exception) {
            DriveSetupOutcome.Failed(failure.message ?: "Google Drive could not be reached")
        }
    }
}
