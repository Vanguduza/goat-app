package com.farmos.app

import androidx.compose.ui.semantics.SemanticsActions
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
import androidx.compose.ui.test.performTextReplacement

/** Follow the real grouped menu. Never calls a route callback or changes production state directly. */
internal fun SemanticsNodeInteractionsProvider.revealFarmTool(label: String, substring: Boolean = false): SemanticsNodeInteraction {
    val notGroup = SemanticsMatcher("not a tool-group heading") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("farm-tools-group:") != true
    }
    val target = hasClickAction() and hasText(label, substring = substring) and !hasSetTextAction() and notGroup
    val tool = target and SemanticsMatcher("a named tool action") {
        val tag = it.config.getOrNull(SemanticsProperties.TestTag).orEmpty()
        tag.startsWith("farm-tool:") || tag.contains("-action:") || tag.startsWith("rabbit-commerce-record:")
    }
    fun match(): SemanticsNodeInteraction? {
        if (onAllNodes(tool).fetchSemanticsNodes().size == 1) return onNode(tool)
        return if (onAllNodes(target).fetchSemanticsNodes().size == 1) onNode(target) else null
    }
    match()?.let { return it }
    // Some module homes expose tools as an explicit view, separate from overview/capture.
    val tools = onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, "farm-section:Tools")).fetchSemanticsNodes()
    if (tools.size == 1 && tools.single().config.getOrNull(SemanticsProperties.Selected) != true) {
        onNodeWithTag("farm-section:Tools").scrollIfSupported().performClick()
        match()?.let { return it }
    }
    val groups = onAllNodes(SemanticsMatcher("tool group") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("farm-tools-group:") == true
    }).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }.distinct()
    for (tag in groups) {
        val group = onNodeWithTag(tag)
        if (group.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription) == "Collapsed") {
            group.scrollIfSupported().performClick()
        }
        match()?.let { return it }
    }
    // A retained tool-search state can hide the group rows after a prior visit.
    val search = onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, "farm-tools-search")).fetchSemanticsNodes()
    if (search.size == 1) {
        onNodeWithTag("farm-tools-search").performScrollTo().performTextReplacement(label)
        match()?.let { return it }
    }
    return onNode(target).assertExists("Tool '$label' must remain reachable through its rendered menu")
}

/** The complete recorded rows are now an explicit alternative to a visual workflow. */
internal fun SemanticsNodeInteractionsProvider.showRecordedRows() {
    val tab = SemanticsMatcher.expectValue(SemanticsProperties.TestTag, "farm-section:Records") and hasClickAction()
    if (onAllNodes(tab).fetchSemanticsNodes().size == 1) onNode(tab).performScrollTo().performClick()
}

/** Minimal navigator harnesses may have no scroll container; do not invent one or bypass clicks. */
private fun SemanticsNodeInteraction.scrollIfSupported(): SemanticsNodeInteraction {
    val node = fetchSemanticsNode()
    if (generateSequence(node) { it.parent }.any { it.config.contains(SemanticsActions.ScrollBy) }) performScrollTo()
    return this
}

/** Select a submit control, not the identically named mode tab. */
internal fun SemanticsNodeInteractionsProvider.formSubmit(label: String): SemanticsNodeInteraction =
    onNode(hasClickAction() and hasText(label) and !hasSetTextAction() and SemanticsMatcher("form action rather than section selector") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("farm-section:") != true
    })
