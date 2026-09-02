package com.farmos.app

import android.app.Application
import androidx.room.Room
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.model.CommandAcknowledgement
import com.farmos.core.model.CommandResultCode
import com.farmos.core.network.AuthenticationRequiredException
import com.farmos.core.network.CommandTransport
import com.farmos.core.network.FarmMembership
import com.farmos.core.network.FarmSearchClient
import com.farmos.core.network.MutableSessionStore
import com.farmos.core.network.RefreshingAccessTokenProvider
import com.farmos.core.network.SupabaseIdentityClient
import com.farmos.core.network.SupabasePullClient
import com.farmos.core.network.SupabaseRpcCommandTransport
import com.farmos.core.network.WireCommand
import com.farmos.core.sync.AuthoritativePullOutcome
import com.farmos.core.sync.SyncEngine
import com.farmos.core.sync.SyncEngineOwner
import com.farmos.core.sync.SyncWorker
import com.farmos.data.goat.GoatPullReconciler
import com.farmos.data.goat.RoomGoatRepository
import com.farmos.data.goat.UnsupportedServerEvent
import com.farmos.domain.goat.GoatRepository
import java.util.UUID
import java.util.concurrent.TimeUnit

class FarmOsApplication : Application(), SyncEngineOwner {
    lateinit var database: FarmOsDatabase
        private set

    lateinit var sessionStore: MutableSessionStore
        private set

    var identityClient: SupabaseIdentityClient? = null
        private set
    var pullClient: SupabasePullClient? = null
        private set
    var farmSearchClient: FarmSearchClient? = null
        private set

    override lateinit var syncEngine: SyncEngine
        private set

    lateinit var deviceId: String
        private set

    val backendConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()

    override fun onCreate() {
        super.onCreate()

        sessionStore = if (backendConfigured) {
            val secureSessions = SecureSessionPersistence(this, BuildConfig.SUPABASE_URL)
            MutableSessionStore(
                onChanged = { session, expiresAt ->
                    runCatching {
                        if (session == null) {
                            secureSessions.clear()
                        } else {
                            secureSessions.save(session, expiresAt)
                        }
                    }
                },
            ).also { store ->
                secureSessions.load()?.let { restored ->
                    store.restore(restored.session, restored.expiresAtEpochMillis)
                }
            }
        } else {
            MutableSessionStore()
        }

        database = Room.databaseBuilder(
            this,
            FarmOsDatabase::class.java,
            "farm-os.db",
        )
            .addMigrations(FarmOsDatabase.MIGRATION_1_2)
            .build()

        deviceId = getSharedPreferences("farm_os_device", MODE_PRIVATE)
            .getString("device_id", null)
            ?: UUID.randomUUID().toString().also { generated ->
                getSharedPreferences("farm_os_device", MODE_PRIVATE)
                    .edit()
                    .putString("device_id", generated)
                    .apply()
            }

        val transport: CommandTransport
        if (backendConfigured) {
            identityClient = SupabaseIdentityClient(
                supabaseUrl = BuildConfig.SUPABASE_URL,
                publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
                sessionStore = sessionStore,
            )
            val refreshingTokenProvider = RefreshingAccessTokenProvider(
                sessionStore = sessionStore,
                refreshSession = {
                    requireNotNull(identityClient) { "Supabase identity client unavailable" }.refresh()
                },
            )
            pullClient = SupabasePullClient(
                supabaseUrl = BuildConfig.SUPABASE_URL,
                publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
                tokenProvider = refreshingTokenProvider,
            )
            if (BuildConfig.MEILI_HOST.isNotBlank()) {
                farmSearchClient = FarmSearchClient(
                    supabaseUrl = BuildConfig.SUPABASE_URL,
                    supabasePublishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
                    meiliHost = BuildConfig.MEILI_HOST,
                    tokenProvider = refreshingTokenProvider,
                )
            }
            transport = SupabaseRpcCommandTransport(
                supabaseUrl = BuildConfig.SUPABASE_URL,
                publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
                tokenProvider = refreshingTokenProvider,
            )
        } else {
            transport = object : CommandTransport {
                override suspend fun send(command: WireCommand): CommandAcknowledgement =
                    CommandAcknowledgement(
                        code = CommandResultCode.TEMPORARY_FAILURE,
                        safeMessage = "Supabase project configuration is not connected",
                    )
            }
        }
        syncEngine = SyncEngine(
            outbox = database.outbox(),
            aggregateVersions = database.aggregateVersions(),
            transport = transport,
        )

        if (lastMembershipForCurrentSession() != null) {
            scheduleBackgroundSync()
        }
    }

    override suspend fun pullAuthoritativeChanges(): AuthoritativePullOutcome {
        if (!backendConfigured) return AuthoritativePullOutcome.SKIPPED
        val farmId = lastMembershipForCurrentSession()?.farmId
            ?: return AuthoritativePullOutcome.SKIPPED
        val reconciler = goatPullReconciler()
            ?: return AuthoritativePullOutcome.SKIPPED

        return try {
            reconciler.reconcile(farmId)
            AuthoritativePullOutcome.APPLIED_OR_CURRENT
        } catch (_: AuthenticationRequiredException) {
            AuthoritativePullOutcome.RETRY
        } catch (_: UnsupportedServerEvent) {
            AuthoritativePullOutcome.FAILURE
        } catch (_: Exception) {
            AuthoritativePullOutcome.RETRY
        }
    }

    fun goatRepository(farmId: String): GoatRepository = RoomGoatRepository(database, farmId)

    fun goatPullReconciler(): GoatPullReconciler? = pullClient?.let { client ->
        GoatPullReconciler(database, client)
    }

    fun rememberMembership(membership: FarmMembership) {
        val userId = sessionStore.current()?.user?.id ?: return
        getSharedPreferences(FARM_CONTEXT_PREFERENCES, MODE_PRIVATE)
            .edit()
            .putString(LAST_USER_ID, userId)
            .putString(LAST_FARM_ID, membership.farmId)
            .putString(LAST_FARM_ROLE, membership.role)
            .commit()
        scheduleBackgroundSync()
    }

    fun lastMembershipForCurrentSession(): FarmMembership? {
        val userId = sessionStore.current()?.user?.id ?: return null
        val preferences = getSharedPreferences(FARM_CONTEXT_PREFERENCES, MODE_PRIVATE)
        if (preferences.getString(LAST_USER_ID, null) != userId) return null
        val farmId = preferences.getString(LAST_FARM_ID, null) ?: return null
        val role = preferences.getString(LAST_FARM_ROLE, null) ?: return null
        return FarmMembership(farmId = farmId, role = role)
    }

    fun clearRememberedMembership() {
        getSharedPreferences(FARM_CONTEXT_PREFERENCES, MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        WorkManager.getInstance(this).cancelUniqueWork(PERIODIC_SYNC_WORK_NAME)
    }

    private fun scheduleBackgroundSync() {
        if (!backendConfigured) return
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        private const val FARM_CONTEXT_PREFERENCES = "farm_os_last_context"
        private const val LAST_USER_ID = "user_id"
        private const val LAST_FARM_ID = "farm_id"
        private const val LAST_FARM_ROLE = "role"
        private const val PERIODIC_SYNC_WORK_NAME = "farm-os-authoritative-sync"
    }
}
