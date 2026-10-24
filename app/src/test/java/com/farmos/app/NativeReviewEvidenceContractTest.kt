package com.farmos.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.FarmPrimaryButton
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Synthetic negative controls for evidence collection; these fixtures do NOT certify a product page. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class NativeReviewEvidenceContractTest {
    @get:Rule(order = 0) val nativeReview = NativeModuleReviewRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    @Before fun enabled() = assumeTrue(!System.getenv("GOAT_MODULE_REVIEW_DIR").isNullOrBlank())

    @Test fun rejectsARequestedOwnerThatIsOnlyTextNotARenderedOwner() {
        compose.setContent { NativeModuleReviewTheme {
            Column(Modifier.testTag("farm-screen:FOS-TASK-003")) {
                Text("FOS-CATTLE-007")
                Text("Synthetic invariant fixture")
            }
        } }
        val failure = runCatching { compose.captureModuleReview("FOS-CATTLE-007") }.exceptionOrNull()
        assertNotNull("A filename must not manufacture native owner coverage", failure)
        assertTrue(failure!!.message.orEmpty().contains("owner", ignoreCase = true))
        assertTrue("No misleading evidence is written", output().walkTopDown().none { it.name.startsWith("FOS-CATTLE-007") })
    }

    @Test fun retainsLowerContentAndRestoresPositionWithoutExecutingControls() {
        var writes = 0
        compose.setContent { NativeModuleReviewTheme {
            Column(Modifier.fillMaxWidth().testTag("farm-screen:FOS-TASK-003").verticalScroll(rememberScrollState())) {
                repeat(28) { i -> Text("Synthetic row $i", Modifier.padding(24.dp).testTag("review-fixture-row:$i")) }
                FarmPrimaryButton(onClick = { writes++ }, modifier = Modifier.testTag("review-fixture-save")) { Text("Do not execute") }
            }
        } }
        compose.onNodeWithTag("review-fixture-row:12").performScrollTo().assertIsDisplayed()
        compose.captureModuleReview("FOS-TASK-003")
        compose.onNodeWithTag("review-fixture-row:12").assertIsDisplayed()
        assertEquals(0, writes)
        val proof = records().first { it.optString("screen_id") == "FOS-TASK-003" && it.optInt("schema_version") == 2 }
        assertTrue("Evidence must include the final viewport", proof.getBoolean("vertical_sweep_complete"))
        val pages = proof.getJSONArray("viewports")
        assertTrue("Long content requires multiple native captures", pages.length() > 1)
        assertTrue((0 until pages.length()).any { pages.getJSONObject(it).getJSONArray("visible_tags").toString().contains("review-fixture-save") })
        assertTrue(proof.getBoolean("scroll_restored"))
    }

    @Test fun repeatedVisitsDoNotOverwriteDifferentStates() {
        compose.setContent { NativeModuleReviewTheme { Text("Fixture", Modifier.testTag("farm-screen:FOS-TASK-003")) } }
        compose.captureModuleReview("FOS-TASK-003")
        compose.captureModuleReview("FOS-TASK-003")
        val proofs = records().filter { it.optInt("schema_version") == 2 && it.optString("screen_id") == "FOS-TASK-003" }
        assertEquals("Each visit retains its own evidence", 2, proofs.size)
        assertEquals(setOf(1, 2), proofs.map { it.getInt("visit") }.toSet())
    }

    private fun output(): File = File(requireNotNull(System.getenv("GOAT_MODULE_REVIEW_DIR")), requireNotNull(System.getProperty("goat.moduleReview.case")))
    private fun records(): List<JSONObject> = output().walkTopDown().filter { it.isFile && it.extension == "json" }.map { JSONObject(it.readText()) }.toList()
}
