package com.farmos.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Per-animal health reads stay inside one farm and one animal. */
@RunWith(AndroidJUnit4::class)
class AnimalHealthQueryTest {
    private lateinit var database: FarmOsDatabase
    private val farmA = "11111111-1111-4111-8111-111111111111"
    private val farmB = "22222222-2222-4222-8222-222222222222"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FarmOsDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun treatmentsAndObservationsAreAnimalAndFarmScoped() = runBlocking {
        database.treatments().insert(treatment("t1", farmA, "nala", at = 1_000L))
        database.treatments().insert(treatment("t2", farmA, "nala", at = 2_000L))
        database.treatments().insert(treatment("t3", farmA, "kito", at = 3_000L))
        database.treatments().insert(treatment("t4", farmB, "nala", at = 4_000L))
        database.healthObservations().insert(observation("o1", farmA, "nala"))
        database.healthObservations().insert(observation("o2", farmB, "nala"))

        assertEquals(listOf("t2", "t1"), database.treatments().forAnimal(farmA, "nala").map { it.id })
        assertEquals(listOf("t4"), database.treatments().forAnimal(farmB, "nala").map { it.id })
        assertEquals(listOf("o1"), database.healthObservations().forAnimal(farmA, "nala").map { it.id })
    }

    @Test
    fun withdrawalsReachAnAnimalOnlyThroughASameFarmTreatment() = runBlocking {
        database.treatments().insert(treatment("t1", farmA, "nala", at = 1_000L))
        database.treatments().insert(treatment("t2", farmA, "kito", at = 2_000L))
        database.lifecycle().insertWithdrawal(WithdrawalWindowEntity("t1:milk", farmA, "t1", "P", "milk", endsEpochDay = 20))
        database.lifecycle().insertWithdrawal(WithdrawalWindowEntity("t1:meat", farmA, "t1", "P", "meat", endsEpochDay = 40))
        database.lifecycle().insertWithdrawal(WithdrawalWindowEntity("t2:milk", farmA, "t2", "P", "milk", endsEpochDay = 30))
        // A window in another farm that references farm A's treatment must never be joined in.
        database.lifecycle().insertWithdrawal(WithdrawalWindowEntity("x:milk", farmB, "t1", "P", "milk", endsEpochDay = 99))

        assertEquals(listOf("t1:meat", "t1:milk"), database.lifecycle().withdrawalsForAnimal(farmA, "nala").map { it.id })
        assertEquals(emptyList<String>(), database.lifecycle().withdrawalsForAnimal(farmB, "nala").map { it.id })
    }

    private fun treatment(id: String, farmId: String, animalId: String, at: Long) = HealthTreatmentEntity(
        id = id,
        farmId = farmId,
        animalId = animalId,
        speciesCode = "goat",
        formularyItemId = "f1",
        reason = "reason",
        meatWithdrawalDays = 28,
        milkWithdrawalDays = 7,
        eggWithdrawalDays = null,
        occurredAtEpochMillis = at,
    )

    private fun observation(id: String, farmId: String, animalId: String) = HealthObservationEntity(
        id = id,
        farmId = farmId,
        animalId = animalId,
        speciesCode = "goat",
        signs = "signs",
        firstAidApplied = null,
        redFlag = false,
        occurredAtEpochMillis = 1L,
    )
}
