package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.AnimalEntity
import com.farmos.core.database.CattleServiceEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.GestationPeriod
import com.farmos.domain.ops.GestationSpecies
import com.farmos.domain.ops.RecordCattleCalving
import com.farmos.domain.ops.RecordCattleDryOff
import com.farmos.domain.ops.RecordCattlePd
import com.farmos.domain.ops.RecordCattleService
import com.farmos.feature.ops.CattleDueSource
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The calving list is an exhaustive query over the farm: each active cow's latest service without a later
 * calving, minus cows diagnosed open since; a dry-off expected date wins, else the farm's gestation period.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class CattleCalvingDueQueryTest {
    private val farm = "22222222-2222-4222-8222-222222222222"
    private val otherFarm = "33333333-3333-4333-8333-333333333333"
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val ops = RoomOpsRepository(database, farm)

    @After
    fun tearDown() = database.close()

    private fun context() = LocalCommandContext(farm, "worker-1", "device-a", UUID.randomUUID().toString(), 1_790_000_000_000)

    private suspend fun cow(id: String, farmId: String = farm, status: String = "active") =
        database.animals().insert(AnimalEntity(id = id, farmId = farmId, tag = "C-$id", name = null, speciesCode = "cattle", sex = "FEMALE", status = status, dateOfBirthEpochDay = null, updatedAtEpochMillis = 1L))

    private suspend fun serve(id: String, day: Long) = ops.recordCattleService(
        RecordCattleService(UUID.randomUUID().toString(), id, "ai", day, UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString()),
        context(),
    )

    private fun id() = UUID.randomUUID().toString()

    @Test
    fun onlyCowsStillExpectedToCalveAreListedAndAStoredDateWins(): Unit = runBlocking {
        listOf("a", "b", "c", "d", "e", "f").forEach { cow(it) }
        // Another farm's served cow never appears.
        cow("x", farmId = otherFarm)
        database.lifecycle().insertService(CattleServiceEntity(id(), otherFarm, "x", "ai", 20_000))
        listOf("a", "b", "c", "d", "f").forEach { serve(it, 20_000) }
        ops.recordCattlePd(RecordCattlePd(id(), "b", "pregnant", 20_035), context())
        ops.recordCattlePd(RecordCattlePd(id(), "c", "open", 20_035), context())
        ops.recordCalving(RecordCattleCalving(id(), "d", 1, 1, 0, 20_280), context())
        // Cow e calved from an earlier service and was served again.
        serve("e", 19_000)
        ops.recordCalving(RecordCattleCalving(id(), "e", 1, 1, 0, 19_283), context())
        serve("e", 19_400)
        // Cow f was dried off with an expected calving date recorded.
        ops.recordDryOff(RecordCattleDryOff(id(), "f", 20_220, expectedCalvingEpochDay = 20_279), context())

        database.setFarmGestation(farm, GestationSpecies.CATTLE, GestationPeriod(275, 285, 292), "owner-1", "device-a")
        val due = loadCattleCalvingDue(database, farm)

        assertEquals(285, due.typicalDays)
        val e = due.rows.single { it.animalId == "e" }
        assertEquals(19_400L, e.serviceEpochDay)
        assertEquals(CattleDueSource.PREDICTED, e.source)
        assertEquals(19_685L, e.typicalEpochDay)
        assertEquals(19_675L, e.earliestEpochDay)
        assertEquals(19_692L, e.latestEpochDay)
        assertEquals("pregnant", due.rows.single { it.animalId == "b" }.pdResult)
        assertNull(due.rows.single { it.animalId == "a" }.pdResult)
        val f = due.rows.single { it.animalId == "f" }
        assertEquals(CattleDueSource.STORED, f.source)
        assertEquals(20_279L, f.typicalEpochDay)
        // Ordered by expected day.
        assertEquals(listOf("e", "f", "a", "b"), due.rows.map { it.animalId })
    }
}
