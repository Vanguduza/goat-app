package com.farmos.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.AnimalFarmThemeSelectionPanel
import com.farmos.core.design.FarmOsTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.fontScale
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.github.takahirom.roborazzi.size
import java.time.LocalDate
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class AnimalFarmReferenceScreensTest {
    private val fixedDate = LocalDate.of(2026, 9, 24)

    @Test
    fun loginReferenceMatrix() = captureMatrix(screenId("GLOBAL", "002")) {
        FoundationAuthScreen(
            backendConfigured = true,
            busy = false,
            error = null,
            sessionPresent = false,
            memberships = emptyList(),
            onSignIn = { _, _ -> },
            onSelectFarm = {},
            onCreateFarm = {},
            onSignOut = {},
        )
    }

    @Test
    fun managementControlRoomReferenceMatrix() = captureMatrix(screenId("HOME", "012-A")) {
        ManagementControlRoomScreen(
            farmName = "Premier Farm",
            ownerMode = true,
            summary = referenceSummary(),
            onOpen = {},
            onAnimals = {},
            onMore = {},
            today = fixedDate,
        )
    }

    @Test
    fun workerWorkBoardReferenceMatrix() = captureMatrix(screenId("HOME", "012-D")) {
        WorkerWorkBoardScreen(
            farmName = "Premier Farm",
            summary = referenceSummary(),
            onOpen = {},
            onAnimals = {},
            onMore = {},
            today = fixedDate,
        )
    }

    @Test
    fun speciesNavigatorReferenceMatrix() = captureMatrix(screenId("HOME", "002")) {
        SpeciesNavigatorScreen(
            onOpen = {},
            onBack = {},
            today = fixedDate,
        )
    }

    @Test
    fun themeSelectionReferenceMatrix() = captureMatrix("FOS-ATOM-THEME") { mode ->
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(24.dp)) {
                Text("Theme", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Choose the field display mode.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                AnimalFarmThemeSelectionPanel(
                    selected = mode,
                    onModeChange = {},
                )
            }
        }
    }

    private fun referenceSummary(): FarmHomeSummary {
        val today = fixedDate.toEpochDay()
        return FarmHomeSummary(
            goatCount = 24,
            openTasks = 3,
            overdueTasks = 1,
            upcomingTasks = 1,
            completedTasks = 1,
            activeWithdrawals = 1,
            pendingSync = 2,
            inventoryItemCount = 12,
            inventoryBelowReorder = 2,
            tasks = listOf(
                HomeTaskRow(
                    id = "task-health-nala",
                    title = "Check Nala condition",
                    moduleCode = "goat",
                    taskCode = "HEALTH_CHECK",
                    dueEpochDay = today,
                    status = "open",
                    animalId = "goat-nala",
                ),
                HomeTaskRow(
                    id = "task-feed-kids",
                    title = "Review kid feed",
                    moduleCode = "feed",
                    taskCode = "FEED_REVIEW",
                    dueEpochDay = today + 1,
                    status = "open",
                    animalId = null,
                ),
                HomeTaskRow(
                    id = "task-weight",
                    title = "Record weekly weights",
                    moduleCode = "goat",
                    taskCode = "WEIGHT",
                    dueEpochDay = today - 1,
                    status = "done",
                    animalId = "goat-nala",
                ),
            ),
        )
    }

    private fun screenId(module: String, suffix: String): String = "FOS-" + module + "-" + suffix

    private fun captureMatrix(
        screenId: String,
        content: @Composable (AnimalFarmThemeMode) -> Unit,
    ) {
        val widths = listOf(360, 411, 780)
        val fontScales = listOf(1.0f, 1.3f, 2.0f)
        val modes = listOf(
            AnimalFarmThemeMode.LIGHT,
            AnimalFarmThemeMode.DARK,
            AnimalFarmThemeMode.OUTDOOR,
        )
        for (mode in modes) {
            for (width in widths) {
                for (scale in fontScales) {
                    val height = if (width >= 700) 1024 else 860
                    val scaleToken = (scale * 100).roundToInt()
                    val fileName = "${screenId}_${mode.name.lowercase()}_w${width}_f${scaleToken}.png"
                    captureRoboImage(
                        "${roborazziSystemPropertyOutputDirectory()}/$fileName",
                        roborazziComposeOptions = RoborazziComposeOptions {
                            size(widthDp = width, heightDp = height)
                            fontScale(scale)
                        },
                    ) {
                        FarmOsTheme(mode = mode) {
                            content(mode)
                        }
                    }
                }
            }
        }
    }
}
