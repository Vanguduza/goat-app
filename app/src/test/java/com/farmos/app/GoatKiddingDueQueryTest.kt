package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.goat.RoomGoatRepository
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.RecordGoatKidding
import com.farmos.domain.goat.RecordGoatMating
import com.farmos.domain.goat.RecordGoatPregnancy
import com.farmos.domain.goat.RegisterGoat
import com.farmos.domain.ops.GestationPeriod
import com.farmos.domain.ops.GestationSpecies
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The kidding list is an exhaustive query over the farm: each active doe's latest service without a later
 * kidding, minus does checked open since, dated with the farm's own gestation period.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class GoatKiddingDueQueryTest {
    private val farm = "11111111-1111-4111-8111-111111111111"
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val goats = RoomGoatRepository(database, farm)

    @After
    fun tearDown() = database.close()

    private fun context() = LocalCommandContext(farm, "worker-1", "device-a", UUID.randomUUID().toString(), 1_790_000_000_000)

    private suspend fun doe(id: String) = goats.registerGoat(RegisterGoat(id, "T-$id", "Doe $id", GoatSex.FEMALE), context())

    private suspend fun mate(id: String, day: Long) = goats.recordMating(RecordGoatMating(UUID.randomUUID().toString(), id, null, "natural", day, UUID.randomUUID().toString()), context())

    @Test
    fun onlyDoesStillExpectedToKidAreListedWithTheFarmsGestation(): Unit = runBlocking {
        listOf("a", "b", "c", "d", "e").forEach { doe(it) }
        listOf("a", "b", "c", "d").forEach { mate(it, 20_000) }
        goats.recordPregnancy(RecordGoatPregnancy(UUID.randomUUID().toString(), "b", "pregnant", 20_040), context())
        goats.recordPregnancy(RecordGoatPregnancy(UUID.randomUUID().toString(), "c", "open", 20_040), context())
        goats.recordKidding(RecordGoatKidding(UUID.randomUUID().toString(), "d", 2, 2, 0, 20_150), context())
        mate("e", 19_000)
        goats.recordKidding(RecordGoatKidding(UUID.randomUUID().toString(), "e", 1, 1, 0, 19_150), context())
        mate("e", 20_100)

        database.setFarmGestation(farm, GestationSpecies.GOAT, GestationPeriod(146, 151, 156), "owner-1", "device-a")
        val period = database.gestationPeriod(farm, GestationSpecies.GOAT)
        val due = goats.kiddingDue(period.earliestDays, period.typicalDays, period.latestDays)

        assertEquals(listOf("a", "b", "e"), due.map { it.animalId })
        assertTrue(due.single { it.animalId == "b" }.confirmedPregnant)
        assertFalse(due.single { it.animalId == "a" }.confirmedPregnant)
        val e = due.single { it.animalId == "e" }
        assertEquals(20_100L, e.serviceEpochDay)
        assertEquals(20_251L, e.typicalDueEpochDay)
        assertEquals(20_246L, e.earliestDueEpochDay)
        assertEquals(20_256L, e.latestDueEpochDay)
    }
}
