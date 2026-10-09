package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.AnimalEntity
import com.farmos.core.database.AnimalGroupEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.SheepScanEntity
import com.farmos.core.model.LocalCommandContext
import com.farmos.domain.access.LocalRole
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.GestationPeriod
import com.farmos.domain.ops.GestationSpecies
import com.farmos.domain.ops.RecordSheepJoining
import com.farmos.domain.ops.RecordSheepLambing
import com.farmos.domain.ops.RecordSheepScan
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Lambing due is exhaustive over the farm: each mob's latest joining dated with the farm's sheep gestation
 * period, and every active ewe whose latest scan found her in lamb with no lambing since.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class SheepLambingDueQueryTest {
    private val farm = "44444444-4444-4444-8444-444444444444"
    private val otherFarm = "55555555-5555-4555-8555-555555555555"
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
        .allowMainThreadQueries()
        .build()
        .also {
            seedCommandAuthority(it, farm, "worker-1", "device-a", LocalRole.WORKER)
            seedCommandAuthority(it, farm, "manager-1", "device-a", LocalRole.MANAGER)
            seedCommandAuthority(it, farm, "owner-1", "device-a", LocalRole.OWNER)
        }
    private val ops = RoomOpsRepository(database, farm)

    @After
    fun tearDown() = database.close()

    private fun context(manager: Boolean = false) =
        LocalCommandContext(farm, if (manager) "manager-1" else "worker-1", "device-a", UUID.randomUUID().toString(), 1_790_000_000_000)

    private fun id() = UUID.randomUUID().toString()

    private suspend fun ewe(id: String, farmId: String = farm) =
        database.animals().insert(AnimalEntity(id = id, farmId = farmId, tag = "S-$id", name = null, speciesCode = "sheep", sex = "FEMALE", status = "active", dateOfBirthEpochDay = null, updatedAtEpochMillis = 1L))

    private suspend fun join(groupId: String, day: Long) =
        ops.recordJoining(RecordSheepJoining(id(), groupId, day, id(), id(), id(), id()), context(manager = true))

    private suspend fun scan(eweId: String, result: String, day: Long) = ops.recordScan(RecordSheepScan(id(), eweId, result, day), context())

    @Test
    fun mobsUseTheirLatestRamInAndOnlyEwesStillInLambAreListed(): Unit = runBlocking {
        database.groups().insert(AnimalGroupEntity("mob-a", farm, "sheep", "Ewes A", 120))
        database.groups().insert(AnimalGroupEntity("mob-b", farm, "sheep", "Maidens", 40))
        join("mob-a", 19_700)
        join("mob-a", 20_000)
        join("mob-b", 20_010)
        listOf("a", "b", "c", "d").forEach { ewe(it) }
        scan("a", "twin", 20_070)
        scan("b", "dry", 20_070)
        scan("c", "single", 20_070)
        ops.recordLambing(RecordSheepLambing(id(), "c", 1, 1, 0, 20_148), context())
        // Ewe d scanned dry, then in lamb on a later scan.
        scan("d", "dry", 20_060)
        scan("d", "triplet", 20_080)
        // Another farm's in-lamb ewe never appears.
        ewe("x", farmId = otherFarm)
        database.lifecycle().insertScan(SheepScanEntity(id(), otherFarm, "x", "twin", 20_070))

        database.setFarmGestation(farm, GestationSpecies.SHEEP, GestationPeriod(143, 148, 153), "owner-1", "device-a")
        val due = loadSheepLambingDue(database, farm)

        assertEquals(148, due.typicalDays)
        assertEquals(listOf("mob-a", "mob-b"), due.mobs.map { it.groupId })
        val a = due.mobs.first()
        assertEquals(20_000L, a.ramInEpochDay)
        assertEquals(20_143L, a.earliestEpochDay)
        assertEquals(20_148L, a.typicalEpochDay)
        assertEquals(120, a.headCount)
        assertEquals(listOf("a", "d"), due.ewes.map { it.animalId })
        assertEquals("triplet", due.ewes.last().result)
    }
}
