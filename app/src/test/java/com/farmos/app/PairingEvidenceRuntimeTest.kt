package com.farmos.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.*
import com.farmos.core.design.FarmBreedingView
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Actual database -> criteria -> ranked candidate -> source offspring -> recentered pedigree. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[36],qualifiers="en-rUS-w411dp-h940dp-mdpi")
class PairingEvidenceRuntimeTest {
    @get:Rule(order=0) val nativeReview=NativeModuleReviewRule()
    @get:Rule(order=1) val compose=createComposeRule()
    private lateinit var db:FarmOsDatabase
    private val farm="pairing-proof-farm"
    private val today=LocalDate.of(2026,10,26)
    @Before fun seed()=runBlocking {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),FarmOsDatabase::class.java).allowMainThreadQueries().build()
        suspend fun animal(id:String,tag:String,name:String,sex:String,age:Long,status:String="active") {
            db.animals().insert(AnimalEntity(id,farm,tag,name,"rabbit",sex,status,today.minusDays(age).toEpochDay(),updatedAtEpochMillis=1))
        }
        animal("dam","D-010","Clover","FEMALE",500)
        animal("sire","B-020","Flint","MALE",550)
        animal("unknown","B-090","Unrecorded ancestry","MALE",500)
        for((id,sex) in listOf("dp" to "MALE","dq" to "FEMALE","sp" to "MALE","sq" to "FEMALE")) animal(id,id,id,sex,1000,"retired")
        for((id,child,parent,relation) in listOf(
            listOf("d-s","dam","dp","sire"),listOf("d-d","dam","dq","dam"),
            listOf("s-s","sire","sp","sire"),listOf("s-d","sire","sq","dam"))) {
            db.lifecycle().insertPedigree(PedigreeRelationEntity(id,farm,child,parent,relation))
        }
        repeat(3) { index ->
            val id="kit$index";animal(id,"KIT-$index","Offspring $index","FEMALE",100)
            db.lifecycle().insertPedigree(PedigreeRelationEntity("kit-s-$index",farm,id,"sire","sire"))
            db.lifecycle().insertPedigree(PedigreeRelationEntity("kit-d-$index",farm,id,"dam","dam"))
            for((age,grams) in listOf(28L to 700L,56L to 1400L)) {
                val at=today.minusDays(100-age).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                db.measurements().insert(MeasurementEntity("$id-$age",farm,id,"weight",grams,"g",at))
            }
        }
    }
    @After fun close()=db.close()
    private fun capture(label:String) {
        val root=System.getenv("GOAT_BREEDING_REVIEW_DIR") ?: return
        val dir=File(root).also{it.mkdirs()};val out=File(dir,"pairing-evidence-$label.png")
        require(!out.exists());compose.onRoot().captureRoboImage(out.absolutePath)
    }

    @Test fun ownerCriteriaRankOnlySupportedCandidatesAndExposeActualOffspring() {
        compose.setContent { NativeModuleReviewTheme {
            BreedingWorkspace(db,farm,FarmDestination.Breeding("dam","rabbit",FarmBreedingView.PAIRINGS),{})
        } }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("pairing-dam:field") and hasText("D-010 · Clover")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("pairing-coi-ceiling").performScrollTo().performTextInput("6.25")
        compose.onNodeWithTag("pairing-objective:field").performScrollTo().performClick()
        compose.onNodeWithTag("pairing-objective:OFFSPRING_GROWTH").performScrollTo().performClick()
        compose.onNodeWithTag("pairing-from-age").performScrollTo().performTextInput("28")
        compose.onNodeWithTag("pairing-to-age").performScrollTo().performTextInput("56")
        compose.onNodeWithTag("pairing-analyse").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("breeding-candidate:sire") and hasText("25.0 g/day",substring=true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("breeding-candidate:sire").performScrollTo().assertTextContains("#1 · B-020 · Flint")
        compose.onNodeWithTag("breeding-candidate:unknown").assertTextContains("Review · B-090 · Unrecorded ancestry")
        compose.captureModuleReview("FOS-GEN-008")
        capture("ranked")
        compose.onNodeWithTag("breeding-candidate:sire").performScrollTo().performClick()
        compose.onNodeWithText("Recorded growth samples").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("offspring-evidence:kit0").performScrollTo().assertExists()
        capture("offspring")
        compose.onNodeWithTag("offspring-evidence:kit0").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("pedigree-connected-tree").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("pedigree-node:R").performScrollTo().assertTextContains("KIT-0 · Offspring 0")
        compose.captureModuleReview("FOS-GEN-002")
        capture("offspring-tree")
        runBlocking {assertEquals(0L,db.replication().count(farm));assertEquals(10,db.lifecycle().pedigreeRelationCount(farm))}
    }
}
