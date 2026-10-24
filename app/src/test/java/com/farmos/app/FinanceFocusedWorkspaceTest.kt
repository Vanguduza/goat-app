package com.farmos.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.design.FarmOsTheme
import com.farmos.feature.ops.FinanceTransactionView
import com.farmos.feature.ops.MoneyCaptureScreen
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS")
class FinanceFocusedWorkspaceTest {
    @get:Rule(order = 0) val nativeReview = NativeModuleReviewRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test
    fun changingCaptureJobsRetainsEachDraftWithoutRecordingMoney() {
        val writes = mutableListOf<List<String>>()
        compose.setContent { NativeModuleReviewTheme { MoneyCaptureScreen(emptyList(), false, null,
            { kind, category, amount, day -> writes.add(listOf(kind, category, amount, day)) }, {}) } }
        open("Record income")
        compose.onNodeWithText("Amount").performScrollTo().performTextReplacement("125.50")
        compose.onNodeWithText("Category").performScrollTo().performTextReplacement("produce")
        open("Finance")
        open("Record expense")
        fieldEquals("Amount", "")
        compose.onNodeWithText("Amount").performScrollTo().performTextReplacement("8.25")
        open("Finance")
        open("Record income")
        fieldEquals("Amount", "125.50")
        fieldEquals("Category", "produce")
        compose.runOnIdle { assertEquals(0, writes.size) }
        open("Save income")
        compose.runOnIdle { assertEquals(listOf("income", "produce", "125.50"), writes.single().take(3)) }
        open("Finance")
        open("Record expense")
        fieldEquals("Amount", "8.25")
    }

    @Test
    fun transactionFilterSurvivesAVisitToAnotherJobAndSelectsTheExactRecord() {
        val day = LocalDate.of(2026, 10, 23).toEpochDay()
        var selected: String? = null
        compose.setContent { NativeModuleReviewTheme { MoneyCaptureScreen(
            rows = emptyList(), busy = false, error = null, onRecord = { _, _, _, _ -> }, onBack = {},
            transactions = listOf(FinanceTransactionView("feed-1", "expense", "feed", 825, "USD", day, null),
                FinanceTransactionView("sale-1", "income", "produce", 12550, "USD", day, null)),
            onOpenTransaction = { selected = it },
        ) } }
        open("View transactions")
        compose.onNodeWithText("Filter recent transactions").performScrollTo().performTextReplacement("feed")
        open("Finance")
        open("Record income")
        open("Finance")
        open("View transactions")
        fieldEquals("Filter recent transactions", "feed")
        compose.onNode(hasText("income produce", substring = true) and hasClickAction()).assertDoesNotExist()
        compose.onNode(hasText("expense feed 8.25 USD", substring = true) and hasClickAction()).performScrollTo().performClick()
        compose.runOnIdle { assertEquals("feed-1", selected) }
    }

    private fun fieldEquals(label: String, value: String) {
        compose.onNodeWithText(label).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(value)))
        compose.captureObservedNativeOwners()
    }
    private fun open(label: String) { compose.revealFarmTool(label).performScrollTo().performClick(); compose.captureObservedNativeOwners() }
}
