package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.network.AuthorizationLoss
import com.farmos.core.network.FarmAccessDecision
import com.farmos.core.network.FarmAccessGuard
import com.farmos.core.network.FarmMembership
import com.farmos.core.network.MutableSessionStore
import com.farmos.core.network.RefreshingAccessTokenProvider
import com.farmos.core.network.SupabaseIdentityClient
import com.farmos.core.network.SupabasePullClient
import com.farmos.core.network.SupabaseRpcCommandTransport
import com.farmos.core.sync.SyncEngine
import com.farmos.data.goat.RoomGoatRepository
import com.farmos.domain.goat.GoatSex
import com.farmos.core.model.LocalCommandContext
import com.farmos.domain.goat.RegisterGoat
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RevokedMembershipClientTest {
    private lateinit var context: Context
    private val databaseName = "farm-os-e2e-revoked.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun revokedMembershipStopsSyncAndClearsRememberedFarmAuthority() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val email = requireArgument(arguments.getString("farmosE2eEmail"), "farmosE2eEmail")
        val password = requireArgument(arguments.getString("farmosE2ePassword"), "farmosE2ePassword")
        val farmId = requireArgument(arguments.getString("farmosE2eFarmId"), "farmosE2eFarmId")
        val supabaseUrl = requireArgument(BuildConfig.SUPABASE_URL, "FARM_OS_SUPABASE_URL")
        val publishableKey = requireArgument(
            BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            "FARM_OS_SUPABASE_PUBLISHABLE_KEY",
        )

        val sessionStore = MutableSessionStore()
        val identity = SupabaseIdentityClient(
            supabaseUrl = supabaseUrl,
            publishableKey = publishableKey,
            sessionStore = sessionStore,
        )
        val session = identity.signIn(email, password)
        val memberships = identity.memberships()
        assertTrue(memberships.none { it.farmId == farmId })
        assertEquals(
            FarmAccessDecision.NO_FARM_MEMBERSHIP,
            FarmAccessGuard.decide(
                sessionPresent = true,
                rememberedFarmId = farmId,
                memberships = memberships,
            ),
        )

        val database = Room.databaseBuilder(context, FarmOsDatabase::class.java, databaseName)
            .addMigrations(*FarmOsDatabase.ALL_MIGRATIONS)
            .build()
        val app = context.applicationContext as FarmOsApplication
        try {
            val repository = RoomGoatRepository(database, farmId)
            val animalId = UUID.randomUUID().toString()
            repository.registerGoat(
                RegisterGoat(
                    animalId = animalId,
                    tag = "REV-E2E-01",
                    name = "Revoked Nala",
                    sex = GoatSex.FEMALE,
                    dateOfBirthEpochDay = 20_000,
                ),
                LocalCommandContext(
                    farmId = farmId,
                    actorId = session.user.id,
                    deviceId = "android-e2e-revoked",
                    mutationId = UUID.randomUUID().toString(),
                    occurredAtEpochMillis = 1_700_000_000_000L,
                ),
            )

            val tokenProvider = RefreshingAccessTokenProvider(sessionStore) {
                identity.refresh()
                Unit
            }
            val pullClient = SupabasePullClient(
                supabaseUrl = supabaseUrl,
                publishableKey = publishableKey,
                tokenProvider = tokenProvider,
            )
            assertTrue(pullClient.pull(farmId, afterCursor = 0L).isEmpty())

            val push = SyncEngine(
                outbox = database.outbox(),
                aggregateVersions = database.aggregateVersions(),
                transport = SupabaseRpcCommandTransport(
                    supabaseUrl = supabaseUrl,
                    publishableKey = publishableKey,
                    tokenProvider = tokenProvider,
                ),
            ).drain(workName = "revoked-membership-e2e")

            assertEquals(1, push.authRejected)
            assertEquals(0, push.acknowledged)
            assertEquals(0, push.retrying)
            assertEquals(AuthorizationLoss.FARM_ACCESS_REVOKED, push.authorizationLoss)
            assertTrue(database.outbox().pending(now = Long.MAX_VALUE, limit = 10).isEmpty())
            assertEquals(
                1L,
                database.outbox().countUnacknowledgedForAggregate(farmId, "animal", animalId),
            )

            app.sessionStore.set(session)
            app.rememberMembership(FarmMembership(farmId = farmId, role = "owner"))
            assertEquals(farmId, app.lastMembershipForCurrentSession()?.farmId)
            app.applyAuthorizationLoss(AuthorizationLoss.FARM_ACCESS_REVOKED)
            assertNull(app.lastMembershipForCurrentSession())
            assertNotNull(app.sessionStore.current())
            assertEquals(session.user.id, app.sessionStore.current()?.user?.id)
        } finally {
            database.close()
            app.sessionStore.set(null)
        }
    }

    private fun requireArgument(value: String?, name: String): String =
        value?.takeIf { it.isNotBlank() }
            ?: error("Required Farm OS E2E argument $name is not configured")
}
