package com.farmos.app

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.AnimalEntity
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.farmos.domain.access.LocalRole
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercises the real Host and Room while a transaction holds a lookup pending. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = FarmOsApplication::class, sdk = [36], qualifiers = "en-rUS")
class GlobalSearchTypeaheadTest {
    private val compose = createComposeRule()
    private val worker = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val app get() = ApplicationProvider.getApplicationContext<FarmOsApplication>()
    private val farmA = UUID.randomUUID().toString()
    private val farmB = UUID.randomUUID().toString()
    private val oldId = "$farmA-old"
    private val newId = "$farmA-new"
    private val cowId = "$farmA-cow"
    private val soldId = "$farmA-sold"
    private val foreignId = "$farmB-new"
    private val state = GlobalSearchSessionState(farmA)

    @get:Rule
    val resources: TestRule = RuleChain.outerRule(
        object : ExternalResource() {
            override fun after() {
                worker.cancel()
                app.database.close()
            }
        },
    ).around(compose)

    @Before
    fun seedSearchSubjects() {
        assertFalse(app.backendConfigured)
        seedCommandAuthority(app.database, farmA, "owner-a", app.deviceId, LocalRole.OWNER)
        seedCommandAuthority(app.database, farmB, "owner-b", app.deviceId, LocalRole.OWNER)
        io {
            app.database.withTransaction {
                animal(oldId, farmA, "OLD-ONLY", "Old doe")
                animal(newId, farmA, "NEEDLE-G", "Current doe")
                animal(cowId, farmA, "NEEDLE-C", "Current cow", species = "cattle")
                animal(soldId, farmA, "NEEDLE-S", "Sold doe", status = "sold")
                animal(foreignId, farmB, "NEEDLE-F", "Other farm doe")
            }
        }
    }

    @Test
    fun typingSuggestsMatchesWithoutSubmissionOrSavingEveryPrefix() {
        host()
        replaceQuery("NEEDLE-G")
        awaitCompleted()
        compose.onNodeWithTag("search-result:$newId").assertExists()
        compose.onNodeWithTag("search-result:$foreignId").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(newId), state.results.value.map { it.animalId })
            assertTrue(state.input.recentSearches.value.isEmpty())
            assertEquals("NEEDLE-G", state.input.query.value)
        }
        assertNoOperations()
    }

    @Test
    fun queryAndFilterChangesDuringAWaitingReadPublishOnlyTheLatestMatches() {
        host()
        compose.onNodeWithText("Filter results").performClick()
        withBlockedSearch { release ->
            // Room holds the lookup pending; UI frames must keep scrolling and layout responsive.
            compose.mainClock.autoAdvance = true
            replaceQuery("OLD-ONLY")
            compose.mainClock.advanceTimeBy(300L)
            compose.runOnIdle { assertTrue(state.busy.value) }
            compose.onNodeWithTag("farm-screen:FOS-SEARCH-001").assertIsEnabled()
            replaceQuery("NEEDLE")
            compose.onNodeWithTag("search-species:goat").performScrollTo().performClick()
            compose.onNodeWithTag("search-status:active").performScrollTo().performClick()
            compose.mainClock.advanceTimeBy(300L)
            compose.onNodeWithTag("search-result:$oldId").assertDoesNotExist()
            compose.onNodeWithTag("farm-screen:FOS-SEARCH-001").assertIsEnabled()
            compose.runOnIdle { assertTrue(state.busy.value) }
            release()
            compose.mainClock.autoAdvance = true
            awaitCompleted()
        }
        compose.onNodeWithTag("search-result:$newId").assertExists()
        compose.onNodeWithTag("search-result:$oldId").assertDoesNotExist()
        compose.onNodeWithTag("search-result:$cowId").assertDoesNotExist()
        compose.onNodeWithTag("search-result:$soldId").assertDoesNotExist()
        compose.onNodeWithTag("search-result:$foreignId").assertDoesNotExist()
        compose.onNodeWithTag("search-species:goat").assertIsSelected()
        compose.onNodeWithTag("search-status:active").assertIsSelected()
        compose.runOnIdle {
            assertEquals(listOf(newId), state.results.value.map { it.animalId })
            assertEquals("NEEDLE", state.input.query.value)
            assertEquals(state.requestSnapshot(), state.renderedRequest.value)
            assertTrue(state.message.value.isEmpty())
        }
        assertNoOperations()
    }

    @Test
    fun clearingAWaitingSearchReturnsToTheInitialStateWithoutLateRows() {
        host()
        withBlockedSearch { release ->
            // Room holds the lookup pending; UI frames must keep scrolling and layout responsive.
            compose.mainClock.autoAdvance = true
            replaceQuery("OLD-ONLY")
            compose.mainClock.advanceTimeBy(300L)
            compose.runOnIdle { assertTrue(state.busy.value) }
            compose.onNodeWithContentDescription("Clear search").performClick()
            compose.mainClock.advanceTimeBy(300L)
            release()
            compose.mainClock.autoAdvance = true
            compose.waitUntil(10_000) { state.input.query.value.isEmpty() && !state.busy.value }
            compose.waitForIdle()
        }
        compose.onNodeWithTag("search-result:$oldId").assertDoesNotExist()
        compose.onNodeWithTag("farm-screen:FOS-HOME-007").assertDoesNotExist()
        compose.onNodeWithTag("farm-screen:FOS-SEARCH-004").assertDoesNotExist()
        compose.onNodeWithTag("search-error").assertDoesNotExist()
        compose.runOnIdle {
            assertFalse(state.searched.value)
            assertTrue(state.results.value.isEmpty())
            assertTrue(state.message.value.isEmpty())
        }
        assertNoOperations()
    }

    private fun host() {
        compose.setContent {
            FarmOsTheme(mode = AnimalFarmThemeMode.LIGHT) {
                GlobalSearchHost(
                    database = app.database, farmId = farmA,
                    onOpen = {}, onBack = {}, state = state,
                )
            }
        }
        compose.waitUntil(10_000) {
            state.statusOptions.value.contains("active") && state.statusOptions.value.contains("sold")
        }
    }

    private fun replaceQuery(query: String) {
        compose.onNode(hasSetTextAction() and hasText("Tag, name or ID"))
            .performScrollTo().performTextReplacement(query)
    }

    private fun awaitCompleted() {
        compose.waitUntil(10_000) {
            state.searched.value && !state.busy.value && state.renderedRequest.value == state.requestSnapshot()
        }
        compose.waitForIdle()
    }

    /** Hold an actual Room transaction to keep the production lookup pending. */
    private fun withBlockedSearch(block: (release: () -> Unit) -> Unit) {
        val entered = CompletableDeferred<Unit>()
        val released = CompletableDeferred<Unit>()
        val holder = worker.launch {
            app.database.withTransaction {
                entered.complete(Unit)
                released.await()
            }
        }
        try {
            io { withTimeout(10_000L) { entered.await() } }
            block { released.complete(Unit) }
        } finally {
            released.complete(Unit)
            compose.mainClock.autoAdvance = true
            io { withTimeout(10_000L) { holder.join() } }
        }
    }

    private suspend fun animal(
        id: String, farmId: String, tag: String, name: String,
        species: String = "goat", status: String = "active",
    ) {
        app.database.animals().insert(
            AnimalEntity(
                id = id, farmId = farmId, tag = tag, name = name, speciesCode = species,
                sex = "FEMALE", status = status, dateOfBirthEpochDay = null, updatedAtEpochMillis = 1,
            ),
        )
    }

    private fun assertNoOperations() {
        io {
            assertTrue(app.database.replication().operationsInRange(farmA, app.deviceId, 1, Long.MAX_VALUE).isEmpty())
            assertTrue(app.database.replication().operationsInRange(farmB, app.deviceId, 1, Long.MAX_VALUE).isEmpty())
        }
    }

    private fun <T> io(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }
}
