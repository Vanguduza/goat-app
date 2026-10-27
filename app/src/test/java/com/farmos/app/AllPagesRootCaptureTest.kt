package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmEntitySelector
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOsTheme
import com.farmos.core.design.FarmSelectorOption
import com.farmos.feature.rabbit.RabbitAnimalView
import com.farmos.feature.rabbit.RabbitCommerceRecords
import com.farmos.feature.rabbit.RabbitContractView
import com.farmos.feature.rabbit.RabbitMarketPlanView
import com.farmos.feature.rabbit.RabbitProfileEntry
import com.farmos.feature.rabbit.RabbitProgrammeScreen
import com.farmos.feature.rabbit.RabbitReservationView
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

/** Unapproved app-wide review candidates. Never writes protected reference images. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class AllPagesRootCaptureTest {
    @Test
    fun captureRootAndSharedSurfaces() {
        val requested = System.getenv("GOAT_ALL_PAGES_CAPTURE_DIR") ?: System.getProperty("goat.allPages.captureDir")
        assumeTrue(!requested.isNullOrBlank())
        val output = File(requireNotNull(requested))
        require(output.isAbsolute && (output.isDirectory || output.mkdirs()))
        val day = LocalDate.of(2026, 10, 18).toEpochDay()
        val records = RabbitCommerceRecords(
            reservations = listOf(RabbitReservationView("reservation-1", "Test buyer", "female", 2, "open", null)),
            contracts = listOf(RabbitContractView("contract-1", "Test buyer", "Reservation 1", "R-014", 12500, "USD", "agreed", day - 3)),
            plans = listOf(RabbitMarketPlanView("plan-1", "Wave A", 2200, day + 10, "pet_sale", "planned")),
        )
        for (mode in listOf(AnimalFarmThemeMode.LIGHT, AnimalFarmThemeMode.DARK, AnimalFarmThemeMode.OUTDOOR)) {
            for (width in listOf(360, 411, 780)) for (scale in listOf(1f, 1.3f, 2f)) {
                fun capture(name: String, content: @Composable () -> Unit) {
                    captureRoboImage(
                        File(output, name + "-" + mode.name.lowercase() + "-w" + width + "-f" + (scale * 100).toInt() + ".png").absolutePath,
                        roborazziComposeOptions = RoborazziComposeOptions { size(widthDp = width, heightDp = if (width >= 700) 1024 else 860); fontScale(scale) },
                    ) { FarmOsTheme(mode = mode) { content() } }
                }
                capture("settings") {
                    FarmOperationalPage("FOS-ADMIN-001", "Farm settings", "", onBack = {}) { FarmSettingsMenu("Farm owner", "Owner", {}) }
                }
                capture("rabbit-commerce") {
                    FarmOperationalPage("FOS-RABBIT-027", "Rabbit waitlist", "", onBack = {}) { RabbitCommerceOverview(records, {}) }
                }
                capture("sync-empty") { SyncQueueScreen(SyncQueueView.IN_FLIGHT, SyncQueueUiState.Loaded(emptyList(), 0), {}, {}) }
                capture("entity-selector") {
                    FarmOperationalPage("FOS-ATOM-005", "Select group", "", onBack = {}) {
                        FarmEntitySelector("farm-atom:FOS-ATOM-005", "Group", listOf(FarmSelectorOption("g1", "North paddock", "18 sheep"), FarmSelectorOption("g2", "Young stock", "7 cattle")), "g1", {}, "No groups")
                    }
                }
                capture("rabbit-profile") {
                    RabbitProgrammeScreen(
                        cages = emptyList(), waves = emptyList(), availableBoxes = 0, busy = false, error = null, does = emptyList(),
                        onRegisterDoe = { _, _, _ -> }, onCreateCage = {}, onCreateNestBox = { _, _ -> }, onCreateWave = { _, _, _ -> },
                        onPalpate = { _, _, _ -> }, onKindle = { _, _, _, _ -> }, onFoster = { _, _, _, _, _ -> }, onBack = {},
                        rabbits = listOf(RabbitAnimalView("rabbit-1", "R-014", "Willow", true, "active")), entryProfile = RabbitProfileEntry("rabbit-1", true),
                    )
                }
            }
        }
    }
}
