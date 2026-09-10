package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.network.MutableSessionStore
import com.farmos.core.network.RefreshingAccessTokenProvider
import com.farmos.core.network.SupabaseIdentityClient
import com.farmos.core.network.SupabasePullClient
import com.farmos.core.network.SupabaseRpcCommandTransport
import com.farmos.core.sync.SyncEngine
import com.farmos.data.goat.RoomGoatRepository
import com.farmos.data.herd.FarmOsPullReconciler
import com.farmos.domain.goat.GoatSex
import com.farmos.core.model.LocalCommandContext
import com.farmos.domain.goat.RecordGoatWeight
import com.farmos.domain.goat.RegisterGoat
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TwoDeviceAuthoritativeSyncTest {
    private lateinit var context: Context
    private val deviceADatabaseName = "farm-os-e2e-device-a.db"
    private val deviceBDatabaseName = "farm-os-e2e-device-b.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(deviceADatabaseName)
        context.deleteDatabase(deviceBDatabaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(deviceADatabaseName)
        context.deleteDatabase(deviceBDatabaseName)
    }

    @Test
    fun secondIndependentDeviceReceivesAuthoritativeGoatAndWeightChanges() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val email = requireArgument(arguments.getString("farmosE2eEmail"), "farmosE2eEmail")
        val password = requireArgument(arguments.getString("farmosE2ePassword"), "farmosE2ePassword")
        val farmId = requireArgument(arguments.getString("farmosE2eFarmId"), "farmosE2eFarmId")
        val supabaseUrl = requireArgument(BuildConfig.SUPABASE_URL, "FARM_OS_SUPABASE_URL")
        val publishableKey = requireArgument(
            BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            "FARM_OS_SUPABASE_PUBLISHABLE_KEY",
        )

        val databaseA = openDatabase(deviceADatabaseName)
        val databaseB = openDatabase(deviceBDatabaseName)
        try {
            val sessionStoreA = MutableSessionStore()
            val identityA = SupabaseIdentityClient(
                supabaseUrl = supabaseUrl,
                publishableKey = publishableKey,
                sessionStore = sessionStoreA,
            )
            val sessionA = identityA.signIn(email, password)
            assertTrue(identityA.memberships().any { it.farmId == farmId })

            val sessionStoreB = MutableSessionStore()
            val identityB = SupabaseIdentityClient(
                supabaseUrl = supabaseUrl,
                publishableKey = publishableKey,
                sessionStore = sessionStoreB,
            )
            identityB.signIn(email, password)
            assertTrue(identityB.memberships().any { it.farmId == farmId })

            val tokenProviderA = RefreshingAccessTokenProvider(sessionStoreA) {
                identityA.refresh()
                Unit
            }
            val tokenProviderB = RefreshingAccessTokenProvider(sessionStoreB) {
                identityB.refresh()
                Unit
            }

            val repositoryA = RoomGoatRepository(databaseA, farmId)
            val repositoryB = RoomGoatRepository(databaseB, farmId)
            val syncA = SyncEngine(
                outbox = databaseA.outbox(),
                aggregateVersions = databaseA.aggregateVersions(),
                transport = SupabaseRpcCommandTransport(
                    supabaseUrl = supabaseUrl,
                    publishableKey = publishableKey,
                    tokenProvider = tokenProviderA,
                ),
            )
            val reconcilerB = FarmOsPullReconciler(
                database = databaseB,
                pullClient = SupabasePullClient(
                    supabaseUrl = supabaseUrl,
                    publishableKey = publishableKey,
                    tokenProvider = tokenProviderB,
                ),
            )

            val animalId = UUID.randomUUID().toString()
            val firstWeightId = UUID.randomUUID().toString()

            repositoryA.registerGoat(
                RegisterGoat(
                    animalId = animalId,
                    tag = "E2E-001",
                    name = "Nala E2E",
                    sex = GoatSex.FEMALE,
                    dateOfBirthEpochDay = 20_000,
                ),
                context(
                    farmId = farmId,
                    actorId = sessionA.user.id,
                    deviceId = "android-e2e-device-a",
                    occurredAt = 1_700_000_000_000L,
                ),
            )
            repositoryA.recordWeight(
                RecordGoatWeight(
                    animalId = animalId,
                    measurementId = firstWeightId,
                    weightGrams = 32_450L,
                    measuredAtEpochMillis = 1_700_000_001_000L,
                ),
                context(
                    farmId = farmId,
                    actorId = sessionA.user.id,
                    deviceId = "android-e2e-device-a",
                    occurredAt = 1_700_000_001_000L,
                ),
            )

            assertTrue(repositoryA.getGoat(animalId)?.syncPending == true)
            assertNull(repositoryB.getGoat(animalId))

            val firstPush = syncA.drain()
            assertEquals(2, firstPush.acknowledged)
            assertEquals(0, firstPush.conflicts)
            assertEquals(0, firstPush.rejected)
            assertEquals(0, firstPush.retrying)
            assertFalse(repositoryA.getGoat(animalId)?.syncPending ?: true)

            val firstPull = reconcilerB.reconcile(farmId)
            assertEquals(2, firstPull.appliedEvents)
            assertTrue(firstPull.finalCursor > 0L)

            val deviceBFirstSnapshot = repositoryB.getGoat(animalId)
            assertNotNull(deviceBFirstSnapshot)
            assertEquals("E2E-001", deviceBFirstSnapshot?.tag)
            assertEquals("Nala E2E", deviceBFirstSnapshot?.name)
            assertEquals(32_450L, deviceBFirstSnapshot?.latestWeightGrams)
            assertFalse(deviceBFirstSnapshot?.syncPending ?: true)
            assertEquals(
                0L,
                databaseB.outbox().countUnacknowledgedForAggregate(
                    farmId = farmId,
                    aggregateType = "animal",
                    aggregateId = animalId,
                ),
            )

            val secondWeightId = UUID.randomUUID().toString()
            repositoryA.recordWeight(
                RecordGoatWeight(
                    animalId = animalId,
                    measurementId = secondWeightId,
                    weightGrams = 33_125L,
                    measuredAtEpochMillis = 1_700_000_002_000L,
                ),
                context(
                    farmId = farmId,
                    actorId = sessionA.user.id,
                    deviceId = "android-e2e-device-a",
                    occurredAt = 1_700_000_002_000L,
                ),
            )

            val secondPush = syncA.drain()
            assertEquals(1, secondPush.acknowledged)
            assertEquals(0, secondPush.conflicts)
            assertEquals(0, secondPush.rejected)
            assertEquals(0, secondPush.retrying)

            val secondPull = reconcilerB.reconcile(farmId)
            assertEquals(1, secondPull.appliedEvents)
            assertTrue(secondPull.finalCursor > firstPull.finalCursor)
            assertEquals(33_125L, repositoryB.getGoat(animalId)?.latestWeightGrams)
            assertEquals(secondPull.finalCursor, databaseB.syncCursors().get(farmId))
        } finally {
            databaseA.close()
            databaseB.close()
        }
    }

    private fun openDatabase(name: String): FarmOsDatabase = Room.databaseBuilder(
        context,
        FarmOsDatabase::class.java,
        name,
    )
        .addMigrations(*FarmOsDatabase.ALL_MIGRATIONS)
        .build()

    private fun context(
        farmId: String,
        actorId: String,
        deviceId: String,
        occurredAt: Long,
    ) = LocalCommandContext(
        farmId = farmId,
        actorId = actorId,
        deviceId = deviceId,
        mutationId = UUID.randomUUID().toString(),
        occurredAtEpochMillis = occurredAt,
    )

    private fun requireArgument(value: String?, name: String): String =
        value?.takeIf { it.isNotBlank() }
            ?: error("Required Farm OS E2E argument $name is not configured")
}
