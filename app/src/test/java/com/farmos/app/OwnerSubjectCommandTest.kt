package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.*
import com.farmos.core.design.*
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.goat.RoomGoatRepository
import com.farmos.data.herd.*
import com.farmos.domain.access.LocalRole
import com.farmos.domain.ops.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@Config(sdk=[36])
class OwnerSubjectCommandTest {
    private lateinit var db:FarmOsDatabase
    private val farm="field-farm"
    private val today=LocalDate.of(2026,10,26)
    @Before fun setup()=runBlocking {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),FarmOsDatabase::class.java).allowMainThreadQueries().build()
        seedCommandAuthority(db,farm,"manager","device",LocalRole.MANAGER)
    }
    @After fun close()=db.close()
    private fun context()=LocalCommandContext(farm,"manager","device",UUID.randomUUID().toString(),1792987200000)
    private suspend fun animal(id:String,species:String,ownFarm:String=farm) {
        db.animals().insert(AnimalEntity(id,ownFarm,id,"Name $id",species,"FEMALE","active",null,updatedAtEpochMillis=1))
    }

    @Test fun allFiveSpeciesUseTheirNativeWeightOwnerAndExactAnimal()=runBlocking {
        val ops=RoomOpsRepository(db,farm);val goats=RoomGoatRepository(db,farm)
        val amounts=mapOf("goat" to "25.125","rabbit" to "2.275","sheep" to "45.125","cattle" to "425.125","poultry" to "1.125")
        for((species,kg) in amounts) {
            val id="weigh-$species";animal(id,species)
            recordSpeciesWeight(db,farm,ops,species,id,kg,today,context(),goats::recordWeight)
            if(species=="rabbit") {
                assertEquals(2.275,db.lifecycle().rabbitWeightHistory(farm,id).single().weightKg,0.000001)
                assertTrue(db.measurements().history(farm,id,"weight").isEmpty())
            } else assertEquals(kg.toBigDecimal().movePointRight(3).longValueExact(),db.measurements().history(farm,id,"weight").single().valueLong)
        }
    }

    @Test fun sameDayRabbitWeightUsesTheOriginalCommandTimestamp()=runBlocking {
        animal("clock-rabbit","rabbit")
        val ctx=context()
        recordSpeciesWeight(db,farm,RoomOpsRepository(db,farm),"rabbit","clock-rabbit","2.125",today,ctx,RoomGoatRepository(db,farm)::recordWeight)
        assertEquals(ctx.occurredAtEpochMillis,db.lifecycle().rabbitWeightHistory(farm,"clock-rabbit").single().weighedAtEpochMillis)
    }

    @Test fun oneOffAndRepeatingTasksKeepTheAnimalId()=runBlocking {
        animal("rabbit-a","rabbit")
        val ops=RoomOpsRepository(db,farm)
        ops.createTask(CreateFarmTask("one","rabbit","CHECK","Review rabbit",today.toEpochDay(),animalId="rabbit-a"),context())
        assertEquals("rabbit-a",db.tasks().get(farm,"one")!!.animalId)
        val planning=TaskPlanning(db,farm,true,"manager")
        planning.create(com.farmos.feature.ops.TaskSeriesDraft("Daily rabbit check","farm","CHECK",today,
            com.farmos.feature.ops.TaskRepeat.DAILY,1,null,null,animalId="rabbit-a"),context())
        val series=db.taskSeries().active(farm).single()
        assertEquals("rabbit-a",series.animalId)
        assertEquals("rabbit",series.moduleCode)
        assertTrue(planning.openOccurrences(today.toEpochDay()).all{it.animalId=="rabbit-a"})
    }

    @Test fun taskAndHealthCommandsRejectForeignOrWrongSpeciesSubjectsAtomically()=runBlocking {
        animal("foreign","goat","other-farm");animal("local-rabbit","rabbit")
        val ops=RoomOpsRepository(db,farm)
        assertTrue(runCatching { ops.createTask(CreateFarmTask("bad-task","goat","CHECK","Wrong animal",today.toEpochDay(),animalId="foreign"),context()) }.isFailure)
        assertNull(db.tasks().get(farm,"bad-task"))
        assertTrue(runCatching { ops.recordObservation(RecordHealthObservation("bad-health","goat","foreign","Cough",occurredAtEpochMillis=1),context()) }.isFailure)
        assertTrue(runCatching { ops.recordObservation(RecordHealthObservation("wrong-species","goat","local-rabbit","Check",occurredAtEpochMillis=1),context()) }.isFailure)
        assertTrue(db.healthObservations().forAnimal(farm,"foreign").isEmpty())
        assertTrue(db.healthObservations().forAnimal(farm,"local-rabbit").isEmpty())
        assertTrue(runCatching { TaskSeriesCommands(db,farm).create(CreateTaskSeries("bad-series","goat","CHECK","Bad", "DAILY",1,today.toEpochDay(),animalId="foreign"),context()) }.isFailure)
        assertNull(db.taskSeries().get(farm,"bad-series"))
    }

    @Test fun profileHealthUsesExactSubjectQueriesRatherThanCappedRecentRecords()=runBlocking {
        animal("target","goat");animal("other","goat")
        db.healthObservations().insert(HealthObservationEntity("target-obs",farm,"target","goat","Old target observation",null,true,1000))
        repeat(220) { index -> db.healthObservations().insert(HealthObservationEntity("other-$index",farm,"other","goat","Other animal",null,false,2000L+index)) }
        val review=loadProfileHealth(db,farm,FarmHealthSubject("target","goat"),now=5000)
        assertEquals(1,review.observationCount)
        assertEquals(listOf("target-obs"),review.signals.single{it.code=="recorded_red_flag"}.evidenceIds)
        assertFalse(review.history.any{it.detail=="Other animal"})
        assertTrue(runCatching { loadProfileHealth(db,"other-farm",FarmHealthSubject("target","goat"),5000) }.isFailure)
    }
}
