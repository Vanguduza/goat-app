package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.recordPeerHolds
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.goat.RoomGoatRepository
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.RecordGoatWeight
import com.farmos.domain.goat.RegisterGoat
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Without a server a goat is waiting to sync only while it has a change no other farm device has confirmed
 * holding; the server-era outbox, never acknowledged without a server, no longer decides it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class GoatLocalSyncPendingTest {
    private val farm = "66666666-6666-4666-8666-666666666666"
    private val device = "device-a"
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val local = RoomGoatRepository(database, farm, localDeviceId = device)
    private val serverEra = RoomGoatRepository(database, farm)

    @After
    fun tearDown() = database.close()

    private fun context() = LocalCommandContext(farm, "worker-1", device, UUID.randomUUID().toString(), 1_790_000_000_000)

    private suspend fun issued() = database.replication().device(farm, device)!!.lastReportedOwnSequence

    @Test
    fun aGoatStopsWaitingOnceAFarmPeerHoldsItsChanges(): Unit = runBlocking {
        local.registerGoat(RegisterGoat("g1", "T-1", "Daisy", GoatSex.FEMALE), context())
        assertTrue(local.getGoat("g1")!!.syncPending)

        // A peer confirms holding everything this device has issued: the goat no longer waits.
        database.recordPeerHolds(farm, "device-b", holdsOwnThrough = issued(), atEpochMillis = 1)
        assertFalse(local.getGoat("g1")!!.syncPending)
        // The server-era rule would still say waiting, because no server acknowledges the outbox.
        assertTrue(serverEra.getGoat("g1")!!.syncPending)

        // A later change waits again until a peer holds it too.
        local.recordWeight(RecordGoatWeight("g1", UUID.randomUUID().toString(), 32_000, 1_790_000_000_000), context())
        assertTrue(local.getGoat("g1")!!.syncPending)
        database.recordPeerHolds(farm, "device-b", holdsOwnThrough = issued(), atEpochMillis = 2)
        assertFalse(local.getGoat("g1")!!.syncPending)
    }
}
