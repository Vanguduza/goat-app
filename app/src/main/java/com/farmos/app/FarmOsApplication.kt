package com.farmos.app

import android.app.Application
import androidx.room.Room
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.model.CommandAcknowledgement
import com.farmos.core.model.CommandResultCode
import com.farmos.core.network.AccessTokenProvider
import com.farmos.core.network.CommandTransport
import com.farmos.core.network.FarmSearchClient
import com.farmos.core.network.MutableSessionStore
import com.farmos.core.network.SupabaseIdentityClient
import com.farmos.core.network.SupabasePullClient
import com.farmos.core.network.SupabaseRpcCommandTransport
import com.farmos.core.network.WireCommand
import com.farmos.core.sync.SyncEngine
import com.farmos.core.sync.SyncEngineOwner
import com.farmos.data.goat.GoatPullReconciler
import com.farmos.data.goat.RoomGoatRepository
import com.farmos.domain.goat.GoatRepository
import java.util.UUID

class FarmOsApplication : Application(), SyncEngineOwner {
    lateinit var database: FarmOsDatabase
        private set

    val sessionStore = MutableSessionStore()

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
            val refreshingTokenProvider = object : AccessTokenProvider {
                override suspend fun accessToken(): String? {
                    if (sessionStore.needsRefresh()) {
                        identityClient?.refresh()
                    }
                    return sessionStore.accessToken()
                }
            }
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
    }

    fun goatRepository(farmId: String): GoatRepository = RoomGoatRepository(database, farmId)

    fun goatPullReconciler(): GoatPullReconciler? = pullClient?.let { client ->
        GoatPullReconciler(database, client)
    }
}
