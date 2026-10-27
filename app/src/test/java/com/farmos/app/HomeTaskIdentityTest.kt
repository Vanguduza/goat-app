package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class HomeTaskIdentityTest {
    private lateinit var db: FarmOsDatabase
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() = db.close()
    @Test fun namesAndTagsComeFromTheExactFarmSubject() = runBlocking {
        db.animals().insert(AnimalEntity("nala", "farm-a", "G-001", "Nala", "goat", "FEMALE", "active", null, updatedAtEpochMillis = 1))
        db.animals().insert(AnimalEntity("other", "farm-b", "SECRET", "Other farm", "cattle", "FEMALE", "active", null, updatedAtEpochMillis = 1))
        val valid = TaskEntity("task-a", "farm-a", "health", "OBSERVATION", "Health check", 1, "open", "nala", null, null, 1)
        val missing = valid.copy(id = "missing", animalId = "other")
        val result = loadHomeTaskIdentities(db, "farm-a", listOf(valid, missing))
        assertEquals(HomeTaskIdentity("Nala · G-001", "goat"), result["task-a"])
        assertFalse(result.containsKey("missing"))
    }
    @Test fun foreignFarmTaskIsRejectedBeforeReadingAnyIdentity() = runBlocking {
        val foreign = TaskEntity("task-b", "farm-b", "goat", "WEIGHT", "Weight", 1, "open", null, null, null, 1)
        val failure = runCatching { loadHomeTaskIdentities(db, "farm-a", listOf(foreign)) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }
}
