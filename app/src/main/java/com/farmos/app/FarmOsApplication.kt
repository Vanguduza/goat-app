package com.farmos.app

import android.app.Application
import android.util.Log
import androidx.room.Room
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.model.CommandAcknowledgement
import com.farmos.core.model.CommandResultCode
import com.farmos.core.network.AuthenticationRequiredException
import com.farmos.core.network.AuthorizationLoss
import com.farmos.core.network.CommandTransport
import com.farmos.core.network.FarmAccessGuard
import com.farmos.core.network.FarmMembership
import com.farmos.core.network.FarmSearchClient
import com.farmos.core.network.MutableSessionStore
import com.farmos.core.network.RefreshingAccessTokenProvider
import com.farmos.core.network.SupabaseIdentityClient
import com.farmos.core.network.SupabasePullClient
import com.farmos.core.network.SupabaseRpcCommandTransport
import com.farmos.core.network.UnsupportedServerEvent
import com.farmos.core.network.WireCommand
import com.farmos.core.sync.AuthoritativePullOutcome
import com.farmos.core.sync.SyncEngine
import com.farmos.core.sync.SyncEngineOwner
import com.farmos.core.sync.SyncObserver
import com.farmos.core.sync.SyncWorker
import com.farmos.data.goat.RoomGoatRepository
import com.farmos.data.herd.FarmOsPullReconciler
import com.farmos.data.herd.RoomOpsRepository
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

    @Volatile
    var authorizationListener: ((AuthorizationLoss) -> Unit)? = null

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
            .addMigrations(*FarmOsDatabase.ALL_MIGRATIONS)
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
            observer = SyncObserver { trace ->
                Log.i(SYNC_LOG_TAG, trace.toStructuredLine())
            },
        )

        if (lastMembershipForCurrentSession() != null) {
            scheduleBackgroundSync()
        }
    }

    override suspend fun pullAuthoritativeChanges(): AuthoritativePullOutcome {
        if (!backendConfigured) return AuthoritativePullOutcome.SKIPPED
        val remembered = lastMembershipForCurrentSession()
            ?: return AuthoritativePullOutcome.SKIPPED
        val identity = identityClient ?: return AuthoritativePullOutcome.SKIPPED
        val reconciler = farmPullReconciler()
            ?: return AuthoritativePullOutcome.SKIPPED

        return try {
            val memberships = identity.memberships()
            val decision = FarmAccessGuard.decide(
                sessionPresent = sessionStore.current() != null,
                rememberedFarmId = remembered.farmId,
                memberships = memberships,
            )
            val loss = FarmAccessGuard.authorizationLoss(decision)
            if (loss != null) {
                onTerminalAuthorizationLoss(loss)
                AuthoritativePullOutcome.AUTHORIZATION_LOST
            } else {
                reconciler.reconcile(remembered.farmId)
                AuthoritativePullOutcome.APPLIED_OR_CURRENT
            }
        } catch (_: AuthenticationRequiredException) {
            onTerminalAuthorizationLoss(AuthorizationLoss.SESSION_EXPIRED)
            AuthoritativePullOutcome.AUTHORIZATION_LOST
        } catch (_: UnsupportedServerEvent) {
            AuthoritativePullOutcome.FAILURE
        } catch (_: Exception) {
            AuthoritativePullOutcome.RETRY
        }
    }

    override fun onTerminalAuthorizationLoss(reason: AuthorizationLoss) {
        applyAuthorizationLoss(reason)
        authorizationListener?.invoke(reason)
    }

    fun applyAuthorizationLoss(reason: AuthorizationLoss) {
        if (reason == AuthorizationLoss.SESSION_EXPIRED) {
            identityClient?.signOut() ?: sessionStore.set(null)
        }
        clearRememberedMembership()
    }

    fun savedThemeMode(): AnimalFarmThemeMode? {
        val stored = getSharedPreferences(THEME_PREFERENCES, MODE_PRIVATE)
            .getString(THEME_MODE, null)
            ?: return null
        return AnimalFarmThemeMode.entries.firstOrNull { it.name == stored }
    }

    fun saveThemeMode(mode: AnimalFarmThemeMode) {
        getSharedPreferences(THEME_PREFERENCES, MODE_PRIVATE)
            .edit()
            .putString(THEME_MODE, mode.name)
            .apply()
    }

    fun goatRepository(farmId: String): GoatRepository = RoomGoatRepository(database, farmId)

    fun opsRepository(farmId: String): RoomOpsRepository = RoomOpsRepository(database, farmId)

    fun farmPullReconciler(): FarmOsPullReconciler? = pullClient?.let { client ->
        FarmOsPullReconciler(database, client)
    }

    fun goatPullReconciler(): FarmOsPullReconciler? = farmPullReconciler()

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
        val workManager = WorkManager.getInstance(this)
        workManager.cancelUniqueWork(PERIODIC_SYNC_WORK_NAME)
        workManager.cancelAllWorkByTag(SYNC_WORK_TAG)
    }

    private fun scheduleBackgroundSync() {
        if (!backendConfigured) return
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(SYNC_WORK_TAG)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        private const val FARM_CONTEXT_PREFERENCES = "farm_os_last_context"
        private const val THEME_PREFERENCES = "animal_farm_theme"
        private const val THEME_MODE = "mode"
        private const val LAST_USER_ID = "user_id"
        private const val LAST_FARM_ID = "farm_id"
        private const val LAST_FARM_ROLE = "role"
        private const val PERIODIC_SYNC_WORK_NAME = "farm-os-authoritative-sync"
        const val SYNC_WORK_TAG = "farm-os-sync"
        private const val SYNC_LOG_TAG = "FarmOsSync"
    }
}
