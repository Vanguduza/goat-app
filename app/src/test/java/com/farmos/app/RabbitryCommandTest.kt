package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.*
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.*
import com.farmos.domain.access.LocalRole
import com.farmos.domain.rabbit.*
import com.farmos.domain.ops.LinkPedigree
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@Config(sdk=[36])
class RabbitryCommandTest {
    private lateinit var db:FarmOsDatabase
    private val farm="rabbitry-farm"
    private val at=1792990000000L
    private fun ctx(actor:String="manager",device:String="device")=LocalCommandContext(farm,actor,device,UUID.randomUUID().toString(),at)
    @Before fun setup() {
        runBlocking {
            db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),FarmOsDatabase::class.java).allowMainThreadQueries().build()
            seedCommandAuthority(db,farm,"manager","device",LocalRole.MANAGER)
            seedCommandAuthority(db,farm,"worker","worker-device",LocalRole.WORKER)
            for((id,sex) in listOf("doe" to "FEMALE","doe2" to "FEMALE","buck" to "MALE","buck2" to "MALE")) {
                db.animals().insert(AnimalEntity(id,farm,id,id,"rabbit",sex,"active",1,updatedAtEpochMillis=1))
            }
            val ops=RoomOpsRepository(db,farm)
            ops.createCage(CreateRabbitCage("a","A",doeCapacity=1),ctx())
            ops.createCage(CreateRabbitCage("b","B",doeCapacity=2),ctx())
            ops.createNestBox(CreateRabbitNestBox("box-a","a","A1"),ctx())
        }
    }
    @After fun close()=db.close()

    @Test fun housingIsExplicitFarmScopedAndIdempotent()=runBlocking {
        val commands=RabbitryCommands(db,farm)
        assertTrue(db.rabbitry().housing(farm).isEmpty())
        val context=ctx();val command=SetRabbitHousing("doe","a","doe",0,at)
        commands.setHousing(command,context);commands.setHousing(command,context)
        assertEquals(1,db.rabbitry().occupants(farm,"a").size)
        assertEquals(1L,db.rabbitry().housingFor(farm,"doe")!!.version)
        assertTrue(runCatching{commands.setHousing(command.copy(cageId="b"),context)}.isFailure)
        assertEquals("a",db.rabbitry().housingFor(farm,"doe")!!.cageId)
    }

    @Test fun staleCapacityAndCrossFarmMovesFailWithoutOverwriting()=runBlocking {
        val c=RabbitryCommands(db,farm)
        c.setHousing(SetRabbitHousing("doe","a","doe",0,at),ctx())
        assertTrue(runCatching{c.setHousing(SetRabbitHousing("doe2","a","doe",0,at),ctx())}.isFailure)
        assertTrue(runCatching{c.setHousing(SetRabbitHousing("doe","b","doe",0,at),ctx())}.isFailure)
        db.animals().insert(AnimalEntity("foreign","other-farm","F",null,"rabbit","FEMALE","active",1,updatedAtEpochMillis=1))
        assertTrue(runCatching{c.setHousing(SetRabbitHousing("foreign","b","doe",0,at),ctx())}.isFailure)
        assertNull(db.rabbitry().housingFor(farm,"foreign"))
        assertEquals("a",db.rabbitry().housingFor(farm,"doe")!!.cageId)
    }

    @Test fun buckServiceRequiresRealPlacementAndAnExplicitEndBeforeMove()=runBlocking {
        val c=RabbitryCommands(db,farm)
        val start=StartRabbitBuckService("service","a","buck",startedAtEpochMillis=at)
        assertTrue(runCatching{c.startService(start,ctx())}.isFailure)
        c.setHousing(SetRabbitHousing("buck","a","buck",0,at),ctx())
        c.startService(start,ctx())
        assertEquals("buck",db.rabbitry().activeServices(farm,"buck").single().buckId)
        assertTrue(db.rabbitry().matings(farm).isEmpty())
        assertTrue(runCatching{c.setHousing(SetRabbitHousing("buck","b","buck",1,at),ctx())}.isFailure)
        c.endService(EndRabbitBuckService("service",1,at),ctx())
        c.setHousing(SetRabbitHousing("buck","b","buck",1,at),ctx())
        assertEquals("b",db.rabbitry().housingFor(farm,"buck")!!.cageId)
        assertEquals(at,db.rabbitry().service(farm,"service")!!.endedAtEpochMillis)
    }

    @Test fun workerCannotStartAServiceAndMatingDoesNotInventPregnancyOrParentage()=runBlocking {
        val c=RabbitryCommands(db,farm)
        c.setHousing(SetRabbitHousing("buck","a","buck",0,at),ctx())
        assertTrue(runCatching{c.startService(StartRabbitBuckService("forbidden","a","buck",startedAtEpochMillis=at),ctx("worker","worker-device"))}.isFailure)
        assertNull(db.rabbitry().service(farm,"forbidden"))
        c.recordMating(RecordRabbitPairMating("mating","a","doe","buck",matedAtEpochMillis=at),ctx())
        assertEquals("doe",db.rabbitry().matings(farm).single().doeId)
        assertTrue(db.lifecycle().pedigreeParents(farm,"doe").isEmpty())
        assertTrue(db.rabbitProgramme().waves(farm).isEmpty())
        assertNull(db.rabbitry().housingFor(farm,"doe"))
    }

    @Test fun nestAssignmentAndPhysicalConditionRemainSeparate()=runBlocking {
        val c=RabbitryCommands(db,farm);val ops=RoomOpsRepository(db,farm)
        c.setHousing(SetRabbitHousing("doe","a","doe",0,at),ctx())
        c.assignNest(SetRabbitNestAssignment("box-a","doe",expectedVersion=0,occurredAtEpochMillis=at),ctx())
        assertEquals("assigned",db.rabbitry().box(farm,"box-a")!!.status)
        assertEquals("doe",db.rabbitry().assignment(farm,"box-a")!!.doeId)
        ops.setNestBoxStatus(SetRabbitNestBoxStatus("box-a","in_cage"),ctx())
        assertTrue(runCatching{c.setHousing(SetRabbitHousing("doe","b","doe",1,at),ctx())}.isFailure)
        assertTrue(runCatching{c.assignNest(SetRabbitNestAssignment("box-a",null,expectedVersion=1,occurredAtEpochMillis=at),ctx())}.isFailure)
        ops.setNestBoxStatus(SetRabbitNestBoxStatus("box-a","dirty"),ctx())
        c.assignNest(SetRabbitNestAssignment("box-a",null,expectedVersion=1,occurredAtEpochMillis=at),ctx())
        assertNull(db.rabbitry().assignment(farm,"box-a")!!.doeId)
        assertEquals("dirty",db.rabbitry().box(farm,"box-a")!!.status)
    }

    @Test fun invalidNewParentageCannotCreateCyclesOrWrongSpecies()=runBlocking {
        val ops=RoomOpsRepository(db,farm)
        db.animals().insert(AnimalEntity("kit",farm,"KIT",null,"rabbit","MALE","active",100,updatedAtEpochMillis=1))
        ops.linkPedigree(LinkPedigree("link1","kit","buck","sire"),ctx())
        assertTrue(runCatching{ops.linkPedigree(LinkPedigree("cycle","buck","kit","sire"),ctx())}.isFailure)
        assertTrue(runCatching{ops.linkPedigree(LinkPedigree("wrong-sex","kit","doe","sire"),ctx())}.isFailure)
        assertEquals(listOf("buck"),db.lifecycle().pedigreeParents(farm,"kit").map{it.parentId})
    }

    @Test fun replayUsesTheSameHandlersWithoutCreatingAnotherOutboxOperation()=runBlocking {
        val c=RabbitryCommands(db,farm)
        c.setHousing(SetRabbitHousing("doe","a","doe",0,at),ctx())
        c.setHousing(SetRabbitHousing("buck","a","buck",0,at),ctx())
        c.startService(StartRabbitBuckService("transport-service","a","buck",startedAtEpochMillis=at),ctx())
        c.recordMating(RecordRabbitPairMating("transport-mating","a","doe","buck",matedAtEpochMillis=at),ctx())
        c.assignNest(SetRabbitNestAssignment("box-a","doe",expectedVersion=0,occurredAtEpochMillis=at),ctx())
        c.endService(EndRabbitBuckService("transport-service",1,at),ctx())
        val operations=db.replication().operationsInRange(farm,"device",1,1000).map{it.toEnvelope()}
        assertTrue(operations.map{it.operationType}.containsAll(RabbitryReplication.all.keys))
        val target=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),FarmOsDatabase::class.java).allowMainThreadQueries().build()
        try {
            // These fixture animals existed before the journalled rabbitry changes. Copy exactly the fixture base.
            db.animals().search(farm,"rabbit",null,null,-1,0).forEach {target.animals().insert(it)}
            val receiver=RoomReplicaEndpoint(target,farm,"replica",OpsReplicationAppliers.all+HerdReplicationAppliers.all)
                .apply{registerPairedDevice("device","Original tablet")}
            val bundle=com.farmos.domain.replication.OperationBundle.seal(farm,"device",operations)
            assertEquals(operations.size,receiver.ingest(bundle).applied)
            assertEquals(operations.size,receiver.ingest(bundle).duplicates)
            assertEquals("a",target.rabbitry().housingFor(farm,"doe")!!.cageId)
            assertEquals(1L,target.rabbitry().housingFor(farm,"doe")!!.version)
            assertEquals(at,target.rabbitry().service(farm,"transport-service")!!.endedAtEpochMillis)
            assertEquals("doe",target.rabbitry().assignment(farm,"box-a")!!.doeId)
            assertEquals(1,target.rabbitry().matings(farm).size)
            assertEquals(0L,target.outbox().countUnacknowledgedForFarm(farm))
            for(op in operations) assertEquals(ApplicationState.APPLIED.name,target.replicationApplications().get(farm,op.operationId)!!.state)
        } finally {target.close()}
    }

}
