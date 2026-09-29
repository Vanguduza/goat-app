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

/** Formulary, protocol slot and application reads are farm-scoped and deterministic. */
@RunWith(AndroidJUnit4::class)
class HealthReferenceQueryTest {
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
    fun referenceReadsAreFarmScoped() = runBlocking {
        val formulary = database.formulary()
        formulary.insert(FormularyItemEntity("f2", farmA, "Zinc spray", "goat", "topical", null, null, null, false))
        formulary.insert(FormularyItemEntity("f1", farmA, "Oxytet LA", "goat", "antibiotic", 28, 7, null, true))
        formulary.insert(FormularyItemEntity("f9", farmB, "Other", "goat", "antibiotic", 1, 1, null, true))
        val treatments = database.treatments()
        repeat(3) { treatments.insert(HealthTreatmentEntity("t$it", farmA, null, "goat", "f1", "r", 28, 7, null, it.toLong())) }
        treatments.insert(HealthTreatmentEntity("t-b", farmB, null, "goat", "f1", "r", 28, 7, null, 1))
        val lifecycle = database.lifecycle()
        lifecycle.insertPackSlot(HealthPackSlotEntity("s2", farmA, "pk1", "CDT2", "Second CDT", 56, "birth", true))
        lifecycle.insertPackSlot(HealthPackSlotEntity("s1", farmA, "pk1", "CDT1", "First CDT", 28, "birth", true))
        lifecycle.insertPackSlot(HealthPackSlotEntity("s-b", farmB, "pk1", "X", "Other", 1, "birth", false))
        lifecycle.insertPackApply(HealthPackApplyEntity("a1", farmA, "pk1", null, "g1", 10))
        lifecycle.insertPackApply(HealthPackApplyEntity("a2", farmA, "pk1", "k1", null, 11))
        lifecycle.insertPackApply(HealthPackApplyEntity("a-b", farmB, "pk1", null, null, 11))

        assertEquals(listOf("f1", "f2"), formulary.forFarm(farmA).map { it.id })
        assertEquals(listOf("f1"), formulary.approved(farmA).map { it.id })
        assertEquals(listOf(RecordKeyCount("f1", 3)), treatments.countsByFormularyItem(farmA))
        assertEquals(listOf(RecordKeyCount("f1", 1)), treatments.countsByFormularyItem(farmB))
        assertEquals(listOf("s1", "s2"), lifecycle.packSlots(farmA).map { it.id })
        assertEquals(listOf(RecordKeyCount("pk1", 2)), lifecycle.packApplicationCounts(farmA))
        assertEquals(listOf(RecordKeyCount("pk1", 1)), lifecycle.packApplicationCounts(farmB))
    }
}
