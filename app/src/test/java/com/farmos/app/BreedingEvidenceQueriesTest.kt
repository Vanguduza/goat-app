package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.*
import com.farmos.data.herd.BreedingEvidenceQueries
import com.farmos.domain.ops.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk=[36])
class BreedingEvidenceQueriesTest {
    private lateinit var db:FarmOsDatabase
    @Before fun open(){db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),FarmOsDatabase::class.java).allowMainThreadQueries().build()}
    @After fun close()=db.close()
    private suspend fun animal(id:String,sex:String,farm:String="a") {
        db.animals().insert(AnimalEntity(id,farm,id,null,"rabbit",sex,"active",0,updatedAtEpochMillis=1))
    }
    @Test fun offspringUseGeneticNotFosterOrGestationalParentage()=runBlocking {
        animal("genetic","FEMALE");animal("carried","FEMALE");animal("foster","FEMALE");animal("sire","MALE");animal("child","FEMALE")
        for((id,parent,kind) in listOf(Triple("1","genetic","genetic_dam"),Triple("2","carried","dam"),Triple("3","foster","foster_dam"),Triple("4","sire","sire"))) {
            db.lifecycle().insertPedigree(PedigreeRelationEntity(id,"a","child",parent,kind))
        }
        val q=BreedingEvidenceQueries(db,"a")
        assertEquals(listOf("child"),q.lineage("genetic").offspring.map{it.id})
        assertTrue(q.lineage("carried").offspring.isEmpty())
        assertTrue(q.lineage("foster").offspring.isEmpty())
        assertEquals("genetic",q.lineage("child").parents["child"]!!.damId)
    }
    @Test fun allEligibleSiresAreAssessedRatherThanOnlyTheFirstPage()=runBlocking {
        animal("dam","FEMALE")
        repeat(60){animal("sire-$it","MALE")}
        animal("foreign","MALE","b")
        val result=BreedingEvidenceQueries(db,"a").compare("dam",PairingCriteria(minimumCompleteGenerations=2),100)
        assertEquals(60,result.assessed)
        assertEquals(60,result.rows.size)
        assertFalse(result.rows.any{it.sire.id=="foreign"})
        assertTrue(result.rows.all{it.rank==null})
    }
    @Test fun foreignParentNamesAreNotExposedAndDanglingLinksAreNotTrusted()=runBlocking {
        animal("child","FEMALE");animal("other-farm-sire","MALE","b")
        db.lifecycle().insertPedigree(PedigreeRelationEntity("bad","a","child","other-farm-sire","sire"))
        val graph=BreedingEvidenceQueries(db,"a").lineage("child")
        assertNull(graph.parents["child"]!!.sireId)
        assertTrue("child" in graph.conflicting)
        assertFalse("other-farm-sire" in graph.animals)
    }
}
