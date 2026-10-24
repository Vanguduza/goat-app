package com.farmos.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.*
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.access.LocalRole
import com.farmos.domain.ops.LinkPedigree
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class GeneticsAndAdvisoryVisualTest {
    @get:Rule(order = 0) val nativeReview = NativeModuleReviewRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val farm = UUID.randomUUID().toString()
    private val other = UUID.randomUUID().toString()
    private val child = UUID.randomUUID().toString()
    private val sire = UUID.randomUUID().toString()
    private val dam = UUID.randomUUID().toString()
    private lateinit var db: FarmOsDatabase
    private lateinit var ops: RoomOpsRepository
    private lateinit var scratch: File
    private var syncs = 0
    private fun context() = LocalCommandContext(farm, "manager-1", "A", UUID.randomUUID().toString(), 1_790_000_000_000)

    @Before fun seed(): Unit = runBlocking {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(appContext, FarmOsDatabase::class.java).allowMainThreadQueries().build()
        seedCommandAuthority(db, farm, "manager-1", "A", LocalRole.MANAGER)
        ops = RoomOpsRepository(db, farm)
        scratch = File(appContext.cacheDir, "advisory-review-$farm").also { it.mkdirs() }
        db.animals().insert(AnimalEntity(child, farm, "G-003", "Willow", "goat", "FEMALE", "active", null, updatedAtEpochMillis = 1))
        db.animals().insert(AnimalEntity(sire, farm, "G-001", "Atlas", "goat", "MALE", "active", null, updatedAtEpochMillis = 1))
        db.animals().insert(AnimalEntity(dam, farm, "G-002", "Fern", "goat", "FEMALE", "active", null, updatedAtEpochMillis = 1))
        db.animals().insert(AnimalEntity(UUID.randomUUID().toString(), other, "G-003", "Other farm animal", "goat", "FEMALE", "active", null, updatedAtEpochMillis = 1))
        ops.linkPedigree(LinkPedigree(UUID.randomUUID().toString(), child, sire, "sire"), context())
        ops.linkPedigree(LinkPedigree(UUID.randomUUID().toString(), child, dam, "dam"), context())
        db.inventory().insertItem(InventoryItemEntity("feed", farm, "FEED", "Feed", "kg", 250, 1, 500))
    }

    @After fun close() { db.close(); scratch.deleteRecursively() }

    @Test fun pedigreeDrawsOnlyThisAnimalsRecordedParentsAndKeepsTheFullRecordView() {
        compose.setContent { NativeModuleReviewTheme { GeneticsModuleHost(db, farm, ops, ::context, { syncs++ }, {}) } }
        compose.revealFarmTool("Pedigree explorer").performScrollTo().performClick()
        compose.onNodeWithText("Animal tag").performScrollTo().performTextInput("G-003")
        compose.revealFarmTool("Show pedigree").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("genetics-family:$child").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("G-001 (Atlas)") and hasAnyAncestor(hasTestTag("genetics-family:$child"))).assertExists()
        compose.onNode(hasText("G-002 (Fern)") and hasAnyAncestor(hasTestTag("genetics-family:$child"))).assertExists()
        compose.onNodeWithText("Other farm animal", substring = true).assertDoesNotExist()
        compose.captureModuleReview("FOS-GEN-002")
        compose.onNodeWithTag("farm-section:Records").performScrollTo().performClick()
        compose.onNodeWithText("G-003 (Willow) <- sire G-001 (Atlas), dam G-002 (Fern)").assertExists()
        assertEquals(0, syncs)
        runBlocking { assertEquals(2, db.lifecycle().pedigreeRelationCount(farm)) }
    }

    @Test fun parentageUsesSelectedControlsAndRetainsTheDraftWithoutWriting() {
        compose.setContent { NativeModuleReviewTheme { GeneticsModuleHost(db, farm, ops, ::context, { syncs++ }, {}) } }
        compose.revealFarmTool("Record parentage").performScrollTo().performClick()
        compose.onNodeWithText("Animal tag").performTextInput("Draft animal")
        compose.onNodeWithTag("farm-section:Dam").performScrollTo().performClick().assertIsSelected()
        compose.captureModuleReview("FOS-GEN-003")
        compose.revealFarmTool("Genetics").performScrollTo().performClick()
        compose.revealFarmTool("Record parentage").performScrollTo().performClick()
        compose.onNodeWithText("Animal tag").assertTextContains("Draft animal")
        compose.onNodeWithTag("farm-section:Dam").assertIsSelected()
        assertEquals(0, syncs)
        runBlocking { assertEquals(2, db.lifecycle().pedigreeRelationCount(farm)) }
    }

    @Test fun advisoryReviewShowsRecordedEvidenceButDoesNotApplyOrDismissAnActionByItself() {
        val opened = mutableListOf<FarmModule>()
        compose.setContent { NativeModuleReviewTheme { AiBoundaryHost(db, farm, scratch, { opened += it }, {}) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("1").fetchSemanticsNodes().isNotEmpty() }
        compose.captureModuleReview("FOS-AI-001")
        compose.revealFarmTool("Suggested actions").performScrollTo().performClick()
        compose.revealFarmTool("Review procurement").performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-AI-007").assertExists()
        compose.onNodeWithText("No farm action has been recorded here").assertExists()
        compose.captureModuleReview("FOS-AI-007")
        assertEquals(emptyList<FarmModule>(), opened)
        compose.revealFarmTool("View evidence sources").performScrollTo().performClick()
        compose.onNodeWithText("FarmReportDao.inventoryTotals").assertExists()
        runBlocking { assertEquals(250L, db.inventory().item(farm, "feed")!!.quantityMilli) }
    }
}
