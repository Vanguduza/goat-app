package com.farmos.app

import android.content.Context
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.RabbitNestBoxEntity
import com.farmos.core.database.RabbitWaveEntity
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomHerdRepository
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.access.LocalRole
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The real rabbit owner on an in-memory farm database: palpation (FOS-RABBIT-011) takes its
 * breeding wave from a selector over this farm's waves, the only waves the command accepts, and
 * the nest-box cycle (FOS-RABBIT-006) offers only the next statuses the governed cycle allows.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class RabbitCaptureOwnerTest {
    @get:Rule
    val compose = createComposeRule()

    private val farm = "11111111-1111-4111-8111-111111111111"
    private val otherFarm = "22222222-2222-4222-8222-222222222222"
    private lateinit var database: FarmOsDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java).build()
        seedCommandAuthority(database, farm, "user-1", "device-1", LocalRole.WORKER)
        runBlocking {
            database.rabbitProgramme().insertWave(wave("wave-sept", farm, 20_700))
            database.rabbitProgramme().insertWave(wave("wave-other", otherFarm, 20_690))
            database.rabbitProgramme().insertBox(RabbitNestBoxEntity("box-7", farm, "cage-a", "NB-07", "dirty"))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun palpationTakesItsWaveFromThisFarmsWaves() {
        var syncRequests = 0
        render { syncRequests++ }
        compose.onNode(hasClickAction() and hasText("Open Palpation")).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-RABBIT-011").assertExists()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("rabbit-wave-selector:wave:option:wave-sept").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("rabbit-wave-selector:wave:option:wave-other").assertDoesNotExist()

        compose.onNodeWithTag("rabbit-wave-selector:wave:option:wave-sept").performScrollTo().performClick()
        compose.onNode(hasClickAction() and hasText("Record palpation")).performScrollTo().performClick()

        compose.waitUntil(10_000) { runBlocking { database.lifecycle().rabbitPalpations(farm) }.isNotEmpty() }
        val palpation = runBlocking { database.lifecycle().rabbitPalpations(farm) }.single()
        assertEquals("wave-sept", palpation.waveId)
        assertEquals("pregnant", palpation.result)
        compose.waitUntil(10_000) { syncRequests == 1 }
    }

    @Test
    fun nestBoxCycleOffersOnlyTheGovernedNextStatuses() {
        var syncRequests = 0
        render { syncRequests++ }
        compose.onNode(hasClickAction() and hasText("Open Cages & nest boxes")).performScrollTo().performClick()
        compose.onNodeWithTag("farm-screen:FOS-RABBIT-006").assertExists()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("rabbit-nest-box-selector:option:box-7").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("rabbit-nest-status-selector").assertDoesNotExist()

        compose.onNodeWithTag("rabbit-nest-box-selector:option:box-7").performScrollTo().performClick()
        compose.onNodeWithTag("rabbit-nest-status-selector:option:sanitized").assertExists()
        compose.onNodeWithTag("rabbit-nest-status-selector:option:available").assertExists()
        compose.onNodeWithTag("rabbit-nest-status-selector:option:assigned").assertDoesNotExist()
        compose.onNodeWithTag("rabbit-nest-status-selector:option:in_cage").assertDoesNotExist()

        compose.onNodeWithTag("rabbit-nest-status-selector:option:sanitized").performScrollTo().performClick()
        compose.onNode(hasClickAction() and hasText("Set nest status")).performScrollTo().performClick()

        compose.waitUntil(10_000) { runBlocking { database.rabbitProgramme().boxes(farm) }.single().status == "sanitized" }
        compose.waitUntil(10_000) { syncRequests == 1 }
    }

    private fun render(onSync: () -> Unit) {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                RabbitModuleHost(
                    farmId = farm,
                    ops = RoomOpsRepository(database, farm),
                    rabbitHerd = RoomHerdRepository(database, farm, "rabbit"),
                    newContext = { LocalCommandContext(farm, "user-1", "device-1", UUID.randomUUID().toString(), 1_790_000_000_000L) },
                    enqueueSync = onSync,
                    onBack = {},
                )
            }
        }
    }

    private fun wave(id: String, farmId: String, matingDay: Long) =
        RabbitWaveEntity(id, farmId, "cage-a", "pack-a", 4, matingDay, matingDay + 28, matingDay + 31, matingDay + 45, matingDay + 42, matingDay + 56)
}
