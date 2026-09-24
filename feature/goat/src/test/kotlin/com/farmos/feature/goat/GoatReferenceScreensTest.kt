package com.farmos.feature.goat

import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.GoatSnapshot
import com.farmos.domain.goat.GoatStatus
import com.farmos.domain.goat.WeightSample
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
class GoatReferenceScreensTest {
    private val fixedDate = LocalDate.of(2026, 9, 24)
    private val state = referenceState()
    private val actions = noOpActions()

    @Test
    fun goatDashboardReferenceMatrix() = capturePage(screenId("GOAT", "001"), GoatPage.DASHBOARD)

    @Test
    fun herdListFilterReferenceMatrix() = capturePage(screenId("GOAT", "002"), GoatPage.HERD)

    @Test
    fun goatProfileAndTabletAdaptiveReferenceMatrix() = capturePage(screenId("GOAT", "003"), GoatPage.PROFILE)

    @Test
    fun weightCaptureReferenceMatrix() = capturePage(screenId("GOAT", "011"), GoatPage.WEIGHT)

    @Test
    fun lifecycleSafetyReferenceMatrix() = capturePage(screenId("GOAT", "051"), GoatPage.STATUS_CHANGE)

    private fun capturePage(screenId: String, page: GoatPage) {
        captureMatrix(screenId) {
            GoatExperienceScreen(
                state = state,
                actions = actions,
                onBackToFarm = {},
                onSignOut = {},
                initialPage = page,
                today = fixedDate,
            )
        }
    }

    private fun referenceState(): GoatSliceUiState {
        val nala = GoatSnapshot(
            animalId = "goat-nala",
            farmId = "farm-reference",
            tag = "GT-024",
            name = "Nala",
            sex = GoatSex.FEMALE,
            status = GoatStatus.ACTIVE,
            dateOfBirthEpochDay = LocalDate.of(2024, 4, 14).toEpochDay(),
            latestWeightGrams = 54_250,
            averageDailyGainGrams = 118,
            weightHistory = listOf(
                WeightSample("w1", 48_100, LocalDate.of(2026, 7, 1).toEpochDay() * 86_400_000L),
                WeightSample("w2", 51_700, LocalDate.of(2026, 8, 1).toEpochDay() * 86_400_000L),
                WeightSample("w3", 54_250, LocalDate.of(2026, 9, 20).toEpochDay() * 86_400_000L),
            ),
            syncPending = true,
        )
        val buck = GoatSnapshot(
            animalId = "goat-kito",
            farmId = "farm-reference",
            tag = "GT-011",
            name = "Kito",
            sex = GoatSex.MALE,
            status = GoatStatus.ACTIVE,
            dateOfBirthEpochDay = LocalDate.of(2023, 9, 2).toEpochDay(),
            latestWeightGrams = 72_400,
            syncPending = false,
        )
        val kid = GoatSnapshot(
            animalId = "goat-lulu",
            farmId = "farm-reference",
            tag = "GT-031",
            name = "Lulu",
            sex = GoatSex.FEMALE,
            status = GoatStatus.ACTIVE,
            dateOfBirthEpochDay = LocalDate.of(2026, 3, 12).toEpochDay(),
            latestWeightGrams = 21_800,
            syncPending = false,
        )
        return GoatSliceUiState(
            farmName = "Premier Farm",
            herd = listOf(nala, buck, kid),
            herdState = LoadableSurfaceState.IDLE,
            selected = nala,
            animalId = nala.animalId,
            pendingSyncCount = 2,
            goatSummary = "24 active goats",
            syncMessage = "2 local changes waiting to sync",
            searchMessage = "Search this herd",
            busy = false,
            error = null,
        )
    }

    private fun noOpActions() = GoatExperienceActions(
        onRegister = { _, _, _, _ -> },
        onRecordWeight = {},
        onRecordKidding = { _, _, _, _ -> },
        onRegisterKid = { _, _, _ -> },
        onRecordFamacha = { _, _ -> },
        onRecordMilk = { _, _ -> },
        onRecordBcs = { _, _ -> },
        onRecordScc = { _, _, _ -> },
        onRecordHeat = {},
        onRecordMating = { _, _, _ -> },
        onRecordPregnancy = { _, _ -> },
        onPlanLactation = {},
        onSetStatus = {},
        onSelectGoat = {},
        onSyncNow = {},
        onSearch = {},
    )

    private fun screenId(module: String, suffix: String): String = "FOS-" + module + "-" + suffix

    private fun captureMatrix(
        screenId: String,
        content: @Composable () -> Unit,
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
                            content()
                        }
                    }
                }
            }
        }
    }
}
