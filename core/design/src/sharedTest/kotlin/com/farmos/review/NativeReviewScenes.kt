package com.farmos.review

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

/** State deduplication for observational hooks. Explicit capture visits remain independently retained. */
internal object NativeReviewScenes {
    private val seen = mutableSetOf<String>()
    fun reset() = seen.clear()
    fun admit(signature: String): Boolean = seen.add(signature)
}

/** Called only after existing positive UI assertions. Never opens groups or invokes routes/commands. */
internal fun ComposeContentTestRule.captureObservedNativeOwners() {
    val output = System.getenv("GOAT_MODULE_REVIEW_DIR") ?: return
    val case = requireNotNull(System.getProperty("goat.moduleReview.case"))
    waitForIdle()
    val nodes = onAllNodes(SemanticsMatcher("all current native semantics") { true }, useUnmergedTree = true)
        .fetchSemanticsNodes().filter { it.layoutInfo.isPlaced && it.layoutInfo.isAttached }
    fun owner(node: SemanticsNode): String? = nativeReviewOwnerBinding(node)?.id
    val owners = nodes.filter { owner(it) != null }
    val roots = owners.filter { candidate ->
        generateSequence(candidate.parent) { it.parent }.none { parent -> owners.any { it.id == parent.id } }
    }
    val signature = nodes.joinToString("\n") { node ->
        val cfg = node.config
        val private = generateSequence(node) { it.parent }.any { it.config.contains(SemanticsProperties.Password) }
        listOf(cfg.getOrNull(SemanticsProperties.TestTag).orEmpty(),
            if (private) "REDACTED" else cfg.getOrNull(SemanticsProperties.Text)?.joinToString("|") { it.text }.orEmpty(),
            if (private) "REDACTED" else cfg.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty(),
            cfg.getOrNull(SemanticsProperties.Selected)?.toString().orEmpty(),
            cfg.getOrNull(SemanticsProperties.StateDescription).orEmpty()).joinToString("~")
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
    if (!NativeReviewScenes.admit(digest)) return
    if (roots.isEmpty()) {
        val directory = File(output, case).also { require(it.isDirectory || it.mkdirs()) }
        File(directory, "unowned-state-$digest.json").writeText(JSONObject().put("schema_version", 2)
            .put("evidence_class", "UNOWNED_RENDERED_STATE_NOT_SCREEN_COVERAGE").put("test", case)
            .put("reason", "Positive UI assertion has no exact placed canonical owner tag")
            .put("observed_tag_count", nodes.count { it.config.contains(SemanticsProperties.TestTag) }).toString(2))
        return
    }
    // Root owners are captured separately. A caller string never creates a missing owner.
    roots.forEach { node ->
        val id = requireNotNull(owner(node))
        val visit = NativeReviewVisits.next(id)
        val directory = File(output, case).also { require(it.isDirectory || it.mkdirs()) }
        val prefix = id + if (visit == 1) "" else "-visit" + visit.toString().padStart(3, '0')
        captureNativeReviewViewports(id, prefix, directory, case, visit, "observed state after successful assertion", ownerNodeId = node.id)
    }
}
