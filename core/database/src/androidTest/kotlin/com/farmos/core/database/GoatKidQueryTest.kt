package com.farmos.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Kid records are read per kidding and per kid inside one farm. */
@RunWith(AndroidJUnit4::class)
class GoatKidQueryTest {
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
    fun kidsForKiddingAndKidRecordStayInsideTheFarm() = runBlocking {
        val lifecycle = database.lifecycle()
        lifecycle.insertKid(GoatKidEntity("kid-b", farmA, "kid-b", "k1", "nala"))
        lifecycle.insertKid(GoatKidEntity("kid-a", farmA, "kid-a", "k1", "nala"))
        lifecycle.insertKid(GoatKidEntity("kid-c", farmA, "kid-c", "k2", "zuri"))
        lifecycle.insertKid(GoatKidEntity("kid-x", farmB, "kid-x", "k1", "nala"))

        assertEquals(listOf("kid-a", "kid-b"), lifecycle.kidsForKidding(farmA, "k1").map { it.animalId })
        assertEquals(listOf("kid-x"), lifecycle.kidsForKidding(farmB, "k1").map { it.animalId })
        assertEquals("k1", lifecycle.kidRecordFor(farmA, "kid-a")?.kiddingId)
        assertNull(lifecycle.kidRecordFor(farmB, "kid-a"))
    }
}
