package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOsTheme
import com.farmos.domain.rabbit.KudbatSemiIntensiveExcel
import com.farmos.feature.ops.TaskUiRow
import com.farmos.feature.ops.TasksBoardScreen
import com.farmos.feature.rabbit.RabbitCageRotationBoard
import com.farmos.feature.rabbit.RabbitCageView
import com.farmos.feature.rabbit.RabbitNestBoxView
import com.farmos.feature.rabbit.RabbitNestCycle
import com.farmos.feature.rabbit.RabbitProgrammeScreen
import com.farmos.feature.rabbit.RabbitRecords
import com.farmos.feature.rabbit.RabbitWaveEventView
import com.farmos.feature.rabbit.RabbitWaveTimeline
import com.farmos.feature.rabbit.RabbitWaveView
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.fontScale
import com.github.takahirom.roborazzi.size
import java.io.File
import java.time.LocalDate
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** New review candidates only; this test never writes the protected reference set. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class FarmWorkflowUxCaptureTest {
    private val today = LocalDate.of(2026, 10, 18)

    @Test
    fun captureFocusedSearchAndRabbitWorkflows() {
        val requested = System.getenv("GOAT_UX_CAPTURE_DIR") ?: System.getProperty("goat.ux.captureDir")
        assumeTrue(!requested.isNullOrBlank())
        val root = File(requireNotNull(requested))
        require(root.isAbsolute && (root.isDirectory || root.mkdirs()))
        val records = records()
        for (mode in listOf(AnimalFarmThemeMode.LIGHT, AnimalFarmThemeMode.DARK, AnimalFarmThemeMode.OUTDOOR)) {
            val scale = if (mode == AnimalFarmThemeMode.OUTDOOR) 2f else 1f
            fun capture(name: String, content: @Composable () -> Unit) {
                captureRoboImage(
                    File(root, "$name-${mode.name.lowercase()}.png").absolutePath,
                    roborazziComposeOptions = RoborazziComposeOptions { size(widthDp = 360, heightDp = 860); fontScale(scale) },
                ) { FarmOsTheme(mode = mode) { content() } }
            }
            capture("search") {
                val input = GlobalSearchInputState().apply { query.value = "GT" }
                GlobalSearchScreen(
                    busy = false, searched = true, message = "",
                    results = listOf(
                        GlobalSearchResultUi("fixture-goat-a", "goat", "GT-024", "Nala", "active", "local"),
                        GlobalSearchResultUi("fixture-goat-b", "goat", "GT-031", "Mara", "active", "local"),
                    ),
                    onSearch = {}, onOpenResult = {}, onBack = {}, inputState = input,
                )
            }
            capture("rabbitry") { Rabbitry(records) }
            capture("cage-rotation") {
                FarmOperationalPage("FOS-RABBIT-008", "Cage occupancy", "", onBack = {}) { RabbitCageRotationBoard(records, today) }
            }
            capture("nest-cycle") {
                FarmOperationalPage("FOS-RABBIT-013", "Nest-box schedule", "", onBack = {}) { RabbitNestCycle(records.cages.flatMap { it.nestBoxes }) }
            }
            capture("breeding-timeline") {
                FarmOperationalPage("FOS-RABBIT-018", "Litter profile", "", onBack = {}) { RabbitWaveTimeline(records.waves.first { it.id == "wave-current" }, today) }
            }
        }
        captureRoboImage(
            File(root, "rabbitry-empty-light.png").absolutePath,
            roborazziComposeOptions = RoborazziComposeOptions { size(widthDp = 360, heightDp = 860) },
        ) { FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) { Rabbitry(RabbitRecords()) } }
        captureRoboImage(
            File(root, "search-empty-light.png").absolutePath,
            roborazziComposeOptions = RoborazziComposeOptions { size(widthDp = 360, heightDp = 860) },
        ) {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                GlobalSearchScreen(false, false, "", emptyList(), {}, {}, onBack = {})
            }
        }
    }

    @Composable
    private fun Rabbitry(records: RabbitRecords) {
        RabbitProgrammeScreen(
            cages = records.cages.map { it.code }, waves = records.waves.map { it.id },
            availableBoxes = 2, busy = false, error = null, does = emptyList(),
            onRegisterDoe = { _, _, _ -> }, onCreateCage = {}, onCreateNestBox = { _, _ -> },
            onCreateWave = { _, _, _ -> }, onPalpate = { _, _, _ -> }, onKindle = { _, _, _, _ -> },
            onFoster = { _, _, _, _, _ -> }, onBack = {}, records = records, today = today,
            rabbitCount = if (records.cages.isEmpty()) 0 else 9,
        )
    }

    @Test
    fun captureHomeMenuAndTasks() {
        val requestedDir = System.getProperty("goat.ux.captureDir") ?: System.getenv("GOAT_UX_CAPTURE_DIR")
        assumeTrue(!requestedDir.isNullOrBlank())
        val outputDir = File(requireNotNull(requestedDir))
        require(outputDir.isAbsolute && (outputDir.isDirectory || outputDir.mkdirs()))
        val modes = listOf(AnimalFarmThemeMode.LIGHT, AnimalFarmThemeMode.DARK, AnimalFarmThemeMode.OUTDOOR)
        val summary = FarmHomeSummary(
            goatCount = 12, openTasks = 3, overdueTasks = 1, upcomingTasks = 2, completedTasks = 4,
            activeWithdrawals = 1, pendingSync = 8, inventoryItemCount = 7, inventoryBelowReorder = 1,
        )
        val day = LocalDate.now().toEpochDay()
        val tasks = listOf(
            TaskUiRow("due-today", "Today checks", "goat", "CHECK", day, "open", assigneeAccountId = "account-a"),
            TaskUiRow("due-future", "Future feed", "goat", "FEED", day + 2, "open"),
            TaskUiRow("due-overdue", "Overdue inspection", "goat", "CHECK", day - 2, "open"),
        )
        for (mode in modes) {
            val options = RoborazziComposeOptions {
                size(widthDp = 360, heightDp = 860)
                fontScale(if (mode == AnimalFarmThemeMode.OUTDOOR) 2f else 1f)
            }
            captureRoboImage(
                File(outputDir, "home-" + mode.name.lowercase() + ".png").absolutePath,
                roborazziComposeOptions = options,
            ) {
                FarmOsTheme(mode = mode) {
                    ManagementControlRoomScreen(
                        farmName = "Premier Farm", ownerMode = false, summary = summary,
                        onOpen = {}, onAnimals = {}, onMore = {}, today = today,
                    )
                }
            }
            captureRoboImage(
                File(outputDir, "menu-" + mode.name.lowercase() + ".png").absolutePath,
                roborazziComposeOptions = options,
            ) {
                FarmOsTheme(mode = mode) {
                    FarmMoreScreen(
                        onOpen = {}, onBack = {}, onSignOut = {}, onNavigate = {},
                        persona = FarmHomePersona.MANAGER, summary = summary, farmName = "Premier Farm",
                    )
                }
            }
            captureRoboImage(
                File(outputDir, "tasks-" + mode.name.lowercase() + ".png").absolutePath,
                roborazziComposeOptions = options,
            ) {
                FarmOsTheme(mode = mode) {
                    TasksBoardScreen(
                        rows = tasks, busy = false, error = null, onCreate = { _, _, _, _ -> },
                        onComplete = {}, onBack = {}, canPlanWork = true, currentAccountId = "account-a",
                    )
                }
            }
        }
    }

    private fun records(): RabbitRecords {
        val prior = wave("wave-prior", "A1", 100, true)
        val current = wave("wave-current", "A1", 40, true)
        val next = wave("wave-next", "B1", 10, false)
        return RabbitRecords(
            waves = listOf(prior, current, next),
            cages = listOf(
                RabbitCageView("cage-a", "A1", 4, listOf(
                    RabbitNestBoxView("box-1", "A1-01", "in_cage"),
                    RabbitNestBoxView("box-2", "A1-02", "in_cage"),
                    RabbitNestBoxView("box-3", "A1-03", "dirty"),
                ), listOf(prior.id, current.id)),
                RabbitCageView("cage-b", "B1", 4, listOf(
                    RabbitNestBoxView("box-4", "B1-01", "available"),
                    RabbitNestBoxView("box-5", "B1-02", "sanitized"),
                    RabbitNestBoxView("box-6", "B1-03", "assigned"),
                ), listOf(next.id)),
            ),
        )
    }

    private fun wave(id: String, cage: String, daysAgo: Long, kindled: Boolean): RabbitWaveView {
        val dates = KudbatSemiIntensiveExcel.schedule(today.toEpochDay() - daysAgo)
        return RabbitWaveView(
            id, cage, 3, dates.matingEpochDay, dates.nestInEpochDay, dates.kindlingEpochDay,
            dates.nestOutEpochDay, dates.rebreedEpochDay, dates.weanEpochDay,
            if (kindled) listOf(RabbitWaveEventView("$id-kindling", dates.kindlingEpochDay + 1, "Kindling", "8 live · 1 dead")) else emptyList(),
            kindled,
        )
    }
}
