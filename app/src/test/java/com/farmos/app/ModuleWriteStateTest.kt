package com.farmos.app

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.FarmOsDatabase
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.feature.ops.ProcurementRecords
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Real module-state writes reject duplicate taps and preserve the receipt after a committed command. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class ModuleWriteStateTest {
    private lateinit var database: FarmOsDatabase
    private lateinit var scope: CoroutineScope
    private var syncRequests = 0
    private var refreshes = 0

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
    }

    @Test
    fun twoImmediateSubmitsStartOnlyOneCommandAndOneSyncRequest() = runBlocking {
        val state = state()
        val release = CompletableDeferred<Unit>()
        var commands = 0

        state.run { commands++; release.await() }
        state.run { commands++ }

        assertTrue(state.busy.value)
        assertEquals(1, commands)
        release.complete(Unit)
        awaitWrites()
        assertEquals(1, commands)
        assertEquals(1, syncRequests)
        assertEquals(1, refreshes)
        assertTrue(state.saved.value)
        assertFalse(state.busy.value)
    }

    @Test
    fun aCommittedCommandKeepsItsReceiptAndRequestsSyncWhenRefreshFails() = runBlocking {
        val state = state(failRefresh = true)
        var commands = 0

        state.run { commands++ }
        awaitWrites()

        assertEquals(1, commands)
        assertEquals(1, syncRequests)
        assertTrue(state.saved.value)
        assertTrue(state.error.value.orEmpty().startsWith("Saved on this device."))
        assertTrue(state.error.value.orEmpty().contains("could not refresh"))
        assertFalse(state.busy.value)
    }

    @Test
    fun aRejectedCommandDoesNotClaimSavedRequestSyncOrRefresh() = runBlocking {
        val state = state()

        state.run { error("Insufficient inventory") }
        awaitWrites()

        assertFalse(state.saved.value)
        assertFalse(state.busy.value)
        assertEquals("Insufficient inventory", state.error.value)
        assertEquals(0, syncRequests)
        assertEquals(0, refreshes)
    }

    @Test
    fun cancellationReleasesTheBusyStateWithoutClaimingAnUncommittedCommand() = runBlocking {
        val state = state()
        val release = CompletableDeferred<Unit>()
        state.run { release.await() }
        assertTrue(state.busy.value)

        scope.cancel()
        awaitWrites()

        assertFalse(state.busy.value)
        assertFalse(state.saved.value)
        assertEquals(0, syncRequests)
    }

    private fun state(failRefresh: Boolean = false) =
        ProcurementModuleState(
            farmId = "farm-a",
            ops = RoomOpsRepository(database, "farm-a"),
            newContext = { error("This test supplies the command directly") },
            enqueueSync = { syncRequests++ },
            onBack = {},
            loadRecords = {
                refreshes++
                check(!failRefresh) { "Refresh failed after commit" }
                ProcurementRecords()
            },
            loadCurrency = { "USD" },
            scope = scope,
            currencyState = mutableStateOf<String?>("USD"),
        )

    private suspend fun awaitWrites() {
        scope.coroutineContext[Job]!!.children.toList().joinAll()
    }
}
