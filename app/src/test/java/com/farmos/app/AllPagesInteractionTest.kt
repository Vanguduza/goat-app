package com.farmos.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.FarmEntitySelector
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOsTheme
import com.farmos.core.design.FarmSelectorOption
import com.farmos.feature.rabbit.RabbitCommerceRecords
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class AllPagesInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun settingsToolSearchOpensTheExistingDestination() {
        var destination: String? = null
        compose.setContent {
            FarmOsTheme {
                FarmOperationalPage("FOS-ADMIN-001", "Farm settings", "", onBack = {}) {
                    FarmSettingsMenu("Farm owner", "Owner") { destination = it }
                }
            }
        }
        compose.onNodeWithText("Find tool").performScrollTo().performClick()
        compose.onNodeWithTag("farm-tools-search").performTextInput("offline")
        compose.onNodeWithTag("settings-action:SYNC_SETTINGS").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("SYNC_SETTINGS", destination) }
    }

    @Test
    fun anEntityBeyondTheInitialRowsCanBeFoundAndSelectedByItsActualId() {
        val selected = mutableStateOf<String?>(null)
        val options = (1..80).map { FarmSelectorOption("animal-$it", if (it == 73) "Rare doe" else "Doe $it") }
        compose.setContent {
            FarmOsTheme {
                FarmOperationalPage("FOS-ATOM-004", "Select doe", "", onBack = {}) {
                    FarmEntitySelector("test-animal-selector", "Doe", options, selected.value, { selected.value = it }, "No does")
                }
            }
        }
        compose.onNodeWithTag("test-animal-selector:search").performTextInput("Rare")
        compose.onNodeWithTag("test-animal-selector:option:animal-73").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("animal-73", selected.value) }
        compose.onNodeWithTag("test-animal-selector:option:animal-73").assertIsSelected()
    }

    @Test
    fun disabledEntityChoicesStayDisabled() {
        compose.setContent {
            FarmOsTheme {
                FarmOperationalPage("FOS-ATOM-004", "Select doe", "", onBack = {}) {
                    FarmEntitySelector("disabled-selector", "Doe", listOf(FarmSelectorOption("a", "Doe A")), null, { error("Disabled selector emitted an intent") }, "No does", enabled = false)
                }
            }
        }
        compose.onNodeWithTag("disabled-selector:option:a").assertIsNotEnabled()
    }

    @Test
    fun switchingRabbitCommerceJobsKeepsTheBuyerDraftAndDoesNotSaveIt() {
        var writes = 0
        val actions = RabbitCommerceActions(
            enqueueWaitlist = { writes++ }, registerKit = { writes++ }, decideRetention = { writes++ },
            promoteKit = { writes++ }, fulfillWaitlist = { writes++ }, agreeContract = { writes++ },
            recordPlan = { writes++ }, bindBedding = { writes++ },
        )
        compose.setContent {
            FarmOsTheme {
                RabbitCommerceContent("USD", false, null, emptyList(), emptyList(), "No bedding item bound.", RabbitCommerceRecords(), actions, { block -> runBlocking { block() } }, {})
            }
        }
        fun open(label: String) = compose.onNodeWithText(label).performScrollTo().performClick()
        open("Buyers and sales")
        open("Add buyer to waitlist")
        compose.onNodeWithText("Buyer").performScrollTo().performTextInput("Draft buyer")
        open("Waitlist overview")
        open("Buyers and sales")
        open("Agree contract")
        compose.runOnIdle { assertEquals(0, writes) }
        open("Waitlist overview")
        open("Buyers and sales")
        open("Add buyer to waitlist")
        compose.onNodeWithText("Buyer").assertTextContains("Draft buyer")
        compose.onNodeWithText("Enqueue waitlist").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, writes) }
    }
}
