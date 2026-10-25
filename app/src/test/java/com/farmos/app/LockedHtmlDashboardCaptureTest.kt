package com.farmos.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.time.LocalDate
import org.junit.*
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Unapproved production-composable renders beside the unchanged HTML. All data is synthetic. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w390dp-h940dp-mdpi")
class LockedHtmlDashboardCaptureTest {
    @get:Rule val compose = createComposeRule()
    private val day = LocalDate.of(2026, 8, 30)
    @Before fun enabled() = assumeTrue(!System.getenv("GOAT_HTML_PARITY_DIR").isNullOrBlank())
    private fun summary(worker: Boolean) = FarmHomeSummary(
        goatCount = 24, openTasks = 4, overdueTasks = 3, upcomingTasks = 1,
        inventoryItemCount = 2, inventoryBelowReorder = 0,
        tasks = listOf(
            HomeTaskRow("health", "Nala’s health review", "goat", "HEALTH_REVIEW", day.toEpochDay(), "open", "nala", "Nala · G-001", "goat"),
            HomeTaskRow("nest", "Place nest boxes", "rabbit", "RABBIT_CARE", day.toEpochDay(), "open", null, "Cage B", "rabbit"),
            HomeTaskRow("weight", "Record Nala’s weight", "goat", "GROWTH_CHECK", day.toEpochDay(), "open", "nala", "Nala · G-001", "goat"),
            HomeTaskRow("kindling", "Kindling check", "rabbit", "RABBIT_CARE", day.plusDays(4).toEpochDay(), "open", null, "Cage B", "rabbit"),
        ).let { if (worker) it.drop(1) else it },
    )
    private fun render(worker: Boolean, mode: AnimalFarmThemeMode) {
        compose.setContent { FarmOsTheme(mode = mode) {
            if (worker) WorkerWorkBoardScreen("Animal Farm", summary(true), {}, {}, {}, day)
            else ManagementControlRoomScreen("Animal Farm", true, summary(false), {}, {}, {}, day)
        } }
        compose.waitForIdle()
    }
    private fun capture(name: String) {
        val root = File(requireNotNull(System.getenv("GOAT_HTML_PARITY_DIR"))).also { it.mkdirs() }
        val file = File(root, "$name.png")
        require(!file.exists()) { "Use an empty evidence directory; do not overwrite a prior candidate" }
        compose.onRoot().captureRoboImage(file.absolutePath)
    }
    @Test fun managementLight() { render(false, AnimalFarmThemeMode.LIGHT); capture("native-management-light") }
    @Test fun managementDark() { render(false, AnimalFarmThemeMode.DARK); capture("native-management-dark") }
    @Test fun workerLight() { render(true, AnimalFarmThemeMode.LIGHT); capture("native-worker-light") }
    @Test fun workerDark() { render(true, AnimalFarmThemeMode.DARK); capture("native-worker-dark") }
    @Test fun animalFamiliesSheet() {
        render(false, AnimalFarmThemeMode.LIGHT)
        compose.onNodeWithTag("home-module:Animals").performScrollTo().performClick()
        compose.onNodeWithTag("home-sheet:Animal families").assertIsDisplayed()
        capture("native-animal-families-light")
    }
    @Test fun themeSheet() {
        render(false, AnimalFarmThemeMode.LIGHT)
        compose.onNodeWithContentDescription("Theme").performClick()
        compose.onNodeWithTag("home-sheet:Theme").assertIsDisplayed()
        capture("native-theme-light")
    }
}
