package com.farmos.app

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.*
import com.farmos.feature.rabbit.*
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Actual production components; fixture screenshots are not an independent visual certificate. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[36],qualifiers="en-rUS-w411dp-h940dp-mdpi")
class BreedingRabbitryVisualTest {
    @get:Rule(order=0) val nativeReview=NativeModuleReviewRule()
    @get:Rule(order=1) val testName=TestName()
    @get:Rule(order=2) val compose=createComposeRule()
    private val day=LocalDate.of(2026,10,26)
    private val at=day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun records():RabbitRecords {
        val wave=RabbitWaveView("w","A1",2,day.minusDays(32).toEpochDay(),day.minusDays(4).toEpochDay(),day.minusDays(1).toEpochDay(),
            day.plusDays(13).toEpochDay(),day.plusDays(10).toEpochDay(),day.plusDays(34).toEpochDay(),
            listOf(RabbitWaveEventView("birth",day.minusDays(1).toEpochDay(),"Kindling","7 live · 1 dead")),true)
        val unit=RabbitUnitView("a","A1",4,
            listOf(RabbitResidentView("doe","R-010 · Clover","FEMALE","active","doe",1,at),
                RabbitResidentView("buck","R-020 · Flint","MALE","active","buck",1,at)),
            listOf(RabbitUnitNestView("n1","A1-N1","in_cage","doe","R-010 · Clover","w",1,at),
                RabbitUnitNestView("n2","A1-N2","dirty",null,null,null,0,null)),
            listOf(RabbitServiceView("service","buck","R-020 · Flint","w",at,null,1,"Recorded service")),
            listOf(RabbitPairMatingView("mating","doe","R-010 · Clover","buck","R-020 · Flint","w",at,"Observed once")),listOf("w"))
        return RabbitRecords(cages=listOf(RabbitCageView("a","A1",4,emptyList(),listOf("w"))),waves=listOf(wave),units=listOf(unit))
    }
    private fun renderRecords(records:RabbitRecords=records()) {
        compose.setContent { NativeModuleReviewTheme {
            FarmOperationalPage("FOS-RABBIT-008","Rabbitry units","",onBack={}) {RabbitCageRotationBoard(records,day)}
        } }
    }
    private fun capture(label:String) {
        val root=System.getenv("GOAT_BREEDING_REVIEW_DIR") ?: return
        val dir=File(root).also{it.mkdirs()};val file=File(dir,testName.methodName+"-"+label+".png")
        check(!file.exists())
        compose.onRoot().captureRoboImage(file.absolutePath)
    }

    @Test fun cageTileShowsActualBuckResidentsAndNestStates() {
        renderRecords()
        compose.onNodeWithTag("rabbit-cage-tile:a").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Buck in service").assertExists()
        compose.onNodeWithText("In service: R-020 · Flint").assertExists()
        compose.onNodeWithText("A1-N1").assertExists()
        compose.onNodeWithText("To clean").assertExists()
        capture("board")
        compose.onNodeWithTag("rabbit-cage-tile:a").performScrollTo().performClick()
        compose.onNodeWithTag("rabbit-unit-occupant:doe").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("rabbit-unit-occupant:buck").performScrollTo().assertIsDisplayed()
        capture("residents")
        compose.onNodeWithTag("rabbit-unit-view:Nest boxes").performScrollTo().performClick()
        compose.onNodeWithTag("rabbit-unit-nest:n1").performScrollTo().performClick()
        compose.onNodeWithText("Assigned doe: R-010 · Clover").assertExists()
        compose.onNodeWithText("Cage A1 · In cage").assertExists()
        capture("nest")
    }

    @Test fun serviceAndMatingFactsRemainDistinctFromPlannedPregnancy() {
        renderRecords()
        compose.onNodeWithTag("rabbit-cage-tile:a").performScrollTo().performClick()
        compose.onNodeWithTag("rabbit-unit-view:Services").performScrollTo().performClick()
        compose.onNodeWithTag("rabbit-unit-service:service").performScrollTo().assertExists()
        compose.onNodeWithTag("rabbit-unit-mating:mating").performScrollTo().assertExists()
        compose.onNodeWithText("An observed mating is not confirmation of pregnancy or offspring parentage.").assertExists()
        capture("service")
        compose.onNodeWithTag("rabbit-unit-manage").assertDoesNotExist()
    }

    @Test fun missingHousingIsNotMislabelledAsAnEmptyOrPregnantUnit() {
        val unit=RabbitUnitView("legacy","B2",8,emptyList(),emptyList(),emptyList(),emptyList(),emptyList())
        renderRecords(RabbitRecords(units=listOf(unit)))
        compose.onNodeWithTag("rabbit-cage-tile:legacy").performScrollTo().performClick()
        compose.onNodeWithText("No occupants have been recorded. This is not a confirmed empty cage.").assertExists()
        compose.onNodeWithText("Pregnant").assertDoesNotExist()
        capture("unknown")
    }

    @Test fun connectedPedigreeNodesAreTappableAndMissingParentsStayUnknown() {
        var selected:String?=null
        val nodes=mapOf(
            "kit" to FarmLineageNode("kit","R-030 · Juniper","Female · 2026-08-01","sire","dam"),
            "sire" to FarmLineageNode("sire","R-020 · Flint","Buck","grand-sire","grand-dam"),
            "dam" to FarmLineageNode("dam","R-010 · Clover","Doe",null,null),
            "grand-sire" to FarmLineageNode("grand-sire","R-001 · Cedar","Buck",null,null),
            "grand-dam" to FarmLineageNode("grand-dam","R-002 · Hazel","Doe",null,null),
        )
        compose.setContent { NativeModuleReviewTheme {
            FarmOperationalPage("FOS-GEN-002","Pedigree tree","",onBack={}) {FarmLineageTree("kit",nodes,2,{selected=it})}
        } }
        compose.onNodeWithTag("pedigree-connected-tree").assertExists()
        compose.onNodeWithTag("pedigree-node:R").performScrollTo().assertIsDisplayed()
        capture("tree")
        compose.onNodeWithTag("pedigree-node:RS").performScrollTo().performClick()
        compose.runOnIdle{assertEquals("sire",selected)}
        compose.onNodeWithTag("pedigree-node:RDS").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("pedigree-node:RDS").assertTextContains("Not recorded")
    }
}
