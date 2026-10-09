package com.farmos.app

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.feature.ops.ProcurementRecords
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Replacing a host callback updates the rendered action without resetting the module's state. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class ModuleCallbackReplacementTest {
    @get:Rule
    val compose = createComposeRule()
    private lateinit var database: FarmOsDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun changingOnBackOnTheSameFarmInvokesTheNewCallback() {
        val ops = RoomOpsRepository(database, "farm-a")
        val replacement = mutableStateOf(false)
        val initialLoads = AtomicInteger()
        var oldCalls = 0
        var newCalls = 0
        val original: () -> Unit = { oldCalls++ }
        val updated: () -> Unit = { newCalls++ }
        val records: suspend () -> ProcurementRecords = { initialLoads.incrementAndGet(); ProcurementRecords() }

        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                ProcurementModuleHost(
                    farmId = "farm-a",
                    ops = ops,
                    newContext = { error("No write is performed") },
                    enqueueSync = {},
                    onBack = if (replacement.value) updated else original,
                    loadRecords = records,
                    loadCurrency = { "USD" },
                )
            }
        }
        compose.waitUntil(10_000) { initialLoads.get() > 0 }
        compose.waitForIdle()
        compose.runOnIdle { replacement.value = true }
        compose.waitForIdle()

        compose.onNodeWithText("Farm home").performScrollTo().performClick()

        assertEquals(0, oldCalls)
        assertEquals(1, newCalls)
        assertEquals(1, initialLoads.get())
    }
}
