package com.farmos.feature.goat

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo

/** Follow the real grouped Goat UI, including accordion expansion; never call routes directly. */
internal fun SemanticsNodeInteractionsProvider.revealGoatTool(label: String): SemanticsNodeInteraction {
    val candidate = hasClickAction() and hasText(label) and !hasSetTextAction()
    val toolRow = candidate and SemanticsMatcher("named goat or Farm OS tool row") {
        val tag = it.config.getOrNull(SemanticsProperties.TestTag).orEmpty()
        tag.startsWith("goat-action:") ||
            tag.startsWith("goat-profile-action:") ||
            tag.startsWith("goat-record:") ||
            tag.startsWith("farm-tool:")
    }
    fun revealed(): SemanticsNodeInteraction? =
        if (onAllNodes(toolRow).fetchSemanticsNodes().size == 1) onNode(toolRow) else null
    revealed()?.let { return it }
    val headings = onAllNodes(SemanticsMatcher("goat tool accordion heading") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("farm-tools-group:") == true
    }).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }.distinct()
    headings.forEach { tag ->
        val group = onNodeWithTag(tag)
        if (group.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription) == "Collapsed") {
            group.performScrollTo().performClick()
        }
        revealed()?.let { return it }
    }
    // Some read-only detail pages still offer direct actions outside the grouped toolkit.
    return onNode(candidate).assertExists("Action '$label' must remain reachable via a rendered control")
}

/** A negative record-link assertion must inspect the expanded group, not an unopened accordion. */
internal fun SemanticsNodeInteractionsProvider.openGoatToolGroup(label: String) {
    val group = onNodeWithTag("farm-tools-group:$label")
    if (group.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription) == "Collapsed") {
        group.performScrollTo().performClick()
    }
}
