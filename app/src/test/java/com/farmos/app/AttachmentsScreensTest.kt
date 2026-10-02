package com.farmos.app

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.AttachmentEntity
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Rendered attachment picker and list (FOS-ATOM-016), owner decision D-015. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class AttachmentsScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private fun row(id: String, name: String, type: String, size: Long) =
        AttachmentEntity(id, "farm", "animal", "s1", "a".repeat(64), size, type, name, 1L, "worker-1")

    @Test
    fun attachmentsSayWhetherTheirBytesAreOnThisDevice() {
        var photos = 0
        var documents = 0
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                AttachmentsSection(
                    items = listOf(
                        AttachmentView(row("p1", "Ear tag.jpg", "image/jpeg", 2_500_000), local = true, preview = null),
                        AttachmentView(row("d1", "Vet report.pdf", "application/pdf", 40_960), local = false, preview = null),
                    ),
                    canAttach = true,
                    busy = false,
                    message = "Saved on this device: Ear tag.jpg",
                    onAddPhoto = { photos++ },
                    onAddDocument = { documents++ },
                )
            }
        }
        compose.onNodeWithTag("attachment:p1").assertExists()
        compose.onNodeWithText("Photo · 2.4 MB · On this device").assertExists()
        compose.onNodeWithText("PDF document · 40 KB · Available when connected").assertExists()
        compose.onNodeWithText("Saved on this device: Ear tag.jpg").assertExists()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-016:photo").performClick()
        compose.onNodeWithTag("farm-atom:FOS-ATOM-016:document").performClick()
        compose.runOnIdle {
            assertEquals(1, photos)
            assertEquals(1, documents)
        }
    }

    @Test
    fun aClosedRecordShowsItsAttachmentsWithoutAddingMore() {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                AttachmentsSection(items = emptyList(), canAttach = false, busy = false, message = null, onAddPhoto = {}, onAddDocument = {})
            }
        }
        compose.onNodeWithText("No photos or documents yet.").assertExists()
        assertEquals(0, compose.onAllNodesWithTag("farm-atom:FOS-ATOM-016:photo").fetchSemanticsNodes().size)
    }
}
