package com.farmos.review

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.DataInputStream
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

/** Evidence collection is test-only, read-only except scrolling, and never invokes business actions. */
internal fun ComposeContentTestRule.requireNativeReviewOwner(screenId: String, nodeId: Int? = null): SemanticsNode {
    val nodes = onAllNodes(SemanticsMatcher("native owner $screenId") {
        nativeReviewOwnerBinding(it)?.id == screenId && (nodeId == null || it.id == nodeId)
    }, useUnmergedTree = true).fetchSemanticsNodes().filter { it.layoutInfo.isAttached && it.layoutInfo.isPlaced }
    check(nodes.size == 1) { "Native owner $screenId must have exactly one placed owner; observed ${nodes.size}" }
    return nodes.single()
}

internal fun ComposeContentTestRule.captureNativeReviewViewports(
    screenId: String, prefix: String, directory: File, case: String, visit: Int, variant: String, ownerNodeId: Int? = null,
) {
    waitForIdle()
    val owner = requireNativeReviewOwner(screenId, ownerNodeId)
    val root = generateSequence(owner) { it.parent }.last()
    fun belongs(node: SemanticsNode): Boolean = generateSequence(node) { it.parent }.any { it.id == root.id }
    val scrollMatcher = SemanticsMatcher("vertical page in owner window") {
        belongs(it) && it.config.contains(SemanticsActions.ScrollBy) && it.config.contains(SemanticsProperties.VerticalScrollAxisRange)
    }
    val scrolls = onAllNodes(scrollMatcher).fetchSemanticsNodes()
    val scroller = scrolls.singleOrNull()
    val scrollId = scroller?.id
    val exactScroll = SemanticsMatcher("original page scroll container") { it.id == scrollId }
    fun position(): Float = onNode(exactScroll).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
    fun maximum(): Float = onNode(exactScroll).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()
    fun scrollBy(delta: Float) {
        if (abs(delta) <= 0.1f) return
        onNode(exactScroll).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, delta) }
        waitForIdle()
    }
    val reasons = mutableListOf<String>()
    val supported = when {
        scrolls.size > 1 -> { reasons += "MULTIPLE_SCROLL_CONTAINERS_REQUIRE_SEPARATE_INSPECTION"; false }
        scroller?.config?.contains(SemanticsActions.ScrollToIndex) == true -> { reasons += "LAZY_SCROLL_REQUIRES_INDEX_AND_OFFSET_RESTORATION"; false }
        scroller?.config?.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.reverseScrolling == true -> { reasons += "REVERSE_SCROLL_REQUIRES_SEPARATE_INSPECTION"; false }
        scroller != null && !maximum().isFinite() -> { reasons += "UNBOUNDED_SCROLL_RANGE"; false }
        else -> true
    }
    val original = if (scroller != null) position() else 0f
    val initialMax = if (scroller != null) maximum() else 0f
    val pages = JSONArray()
    var complete = scrolls.isEmpty()
    var restored = scrolls.isEmpty() || !supported
    val metaFile = File(directory, "$prefix.json")
    check(!metaFile.exists()) { "Native evidence is immutable within a run: ${metaFile.name}" }
    try {
        if (supported && scroller != null) scrollBy(-original)
        for (index in 0 until 64) {
            waitForIdle()
            val nodeList = onAllNodes(SemanticsMatcher("all visible owner-window semantics") { belongs(it) }, useUnmergedTree = true).fetchSemanticsNodes()
            val viewportRoot = onNode(SemanticsMatcher("owner window root") { it.id == root.id }, useUnmergedTree = true).fetchSemanticsNode()
            val bounds = viewportRoot.boundsInRoot
            val visible = nodeList.filter { it.layoutInfo.isPlaced && it.layoutInfo.isAttached && overlaps(it.boundsInRoot, bounds) }
            val tags = visible.mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }.distinct().sorted()
            val ownerBindings = visible.mapNotNull(::nativeReviewOwnerBinding)
            val owners = ownerBindings.map { it.id }.distinct().sorted()
            val basename = prefix + if (index == 0) "" else "-viewport" + (index + 1).toString().padStart(3, '0')
            val image = File(directory, "$basename.png")
            check(!image.exists()) { "Native evidence image already exists: ${image.name}" }
            onNode(SemanticsMatcher("owner window root") { it.id == root.id }, useUnmergedTree = true).captureRoboImage(image.absolutePath)
            val size = pngSize(image)
            val findings = JSONArray()
            runOnIdle {
                visible.forEach { node ->
                    val textLayouts = mutableListOf<TextLayoutResult>()
                    node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(textLayouts)
                    if (textLayouts.any { layout ->
                        layout.multiParagraph.didExceedMaxLines ||
                            layout.multiParagraph.height > layout.size.height + 0.5f ||
                            layout.multiParagraph.width > layout.size.width + 0.5f ||
                            (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
                    }) findings.put(JSONObject()
                        .put("kind", "TEXT_LAYOUT_OVERFLOW_REQUIRES_PIXEL_REVIEW").put("text", if (node.config.contains(SemanticsProperties.Password)) "REDACTED" else node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" · ") { it.text }.orEmpty()).put("tag", node.config.getOrNull(SemanticsProperties.TestTag) ?: JSONObject.NULL)
                        .put("bounds_px", rectangle(node.boundsInRoot))
                        .put("text_layouts", JSONArray(textLayouts.map { layout -> JSONObject()
                            .put("measured_width_px", layout.size.width).put("measured_height_px", layout.size.height)
                            .put("paragraph_width_px", layout.multiParagraph.width).put("paragraph_height_px", layout.multiParagraph.height)
                            .put("exceeded_max_lines", layout.multiParagraph.didExceedMaxLines)
                            .put("ellipsized", (0 until layout.lineCount).any { layout.isLineEllipsized(it) }) })))
                }
            }
            val merged = onAllNodes(SemanticsMatcher("interactive owner-window semantics") {
                belongs(it) && it.config.contains(SemanticsActions.OnClick)
            }).fetchSemanticsNodes().filter { it.layoutInfo.isPlaced && overlaps(it.boundsInRoot, bounds) }
            val controls = JSONArray()
            merged.forEach { node ->
                val private = generateSequence(node) { it.parent }.any { it.config.contains(SemanticsProperties.Password) }
                val text = if (private) "REDACTED" else node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" · ") { it.text }.orEmpty()
                val description = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" · ").orEmpty()
                val density = node.layoutInfo.density.density
                val width = node.layoutInfo.width / density
                val height = node.layoutInfo.height / density
                val touch = node.touchBoundsInRoot
                val touchWidth = touch.width / density
                val touchHeight = touch.height / density
                val fullyVisible = node.boundsInRoot.width + 0.5f >= node.layoutInfo.width && node.boundsInRoot.height + 0.5f >= node.layoutInfo.height
                val min = if (System.getenv("GOAT_MODULE_REVIEW_MODE") == "OUTDOOR") 64f else 48f
                val named = text.isNotBlank() || description.isNotBlank()
                controls.put(JSONObject().put("tag", node.config.getOrNull(SemanticsProperties.TestTag) ?: JSONObject.NULL)
                    .put("name", if (text.isNotBlank()) text else description).put("width_dp", width).put("height_dp", height)
                    .put("disabled", node.config.contains(SemanticsProperties.Disabled)).put("bounds_px", rectangle(node.boundsInRoot))
                    .put("touch_bounds_px", rectangle(touch)).put("touch_width_dp", touchWidth).put("touch_height_dp", touchHeight)
                    .put("fully_visible", fullyVisible))
                if (!named) findings.put(JSONObject().put("kind", "UNNAMED_CLICK_TARGET").put("bounds_px", rectangle(node.boundsInRoot)))
                if (fullyVisible && (touchWidth + 0.5f < min || touchHeight + 0.5f < min)) findings.put(JSONObject()
                    .put("kind", "TOUCH_TARGET_BELOW_REQUIRED_MINIMUM").put("minimum_dp", min)
                    .put("width_dp", touchWidth).put("height_dp", touchHeight).put("name", if (text.isNotBlank()) text else description))
            }
            pages.put(JSONObject().put("image", image.name).put("image_sha256", reviewSha256(image))
                .put("width_px", size.first).put("height_px", size.second).put("density", viewportRoot.layoutInfo.density.density)
                .put("actual_font_scale", owner.layoutInfo.density.fontScale).put("visible_tags", JSONArray(tags))
                .put("observed_owner_ids", JSONArray(owners))
                .put("canonical_owner_bindings", JSONArray(ownerBindings.map { binding -> JSONObject()
                    .put("id", binding.id).put("tag", binding.tag).put("node_id", binding.nodeId).put("kind", binding.kind) }))
                .put("controls", controls).put("layout_findings", findings)
                .put("scroll_position", if (scroller != null) position() else JSONObject.NULL))
            if (!supported || scroller == null) break
            if (abs(maximum() - initialMax) > 1f) { reasons += "CONTENT_RANGE_CHANGED_DURING_CAPTURE"; break }
            val before = position()
            if (before >= maximum() - 0.5f) { complete = true; break }
            val step = (scroller.boundsInRoot.height * 0.85f).coerceAtLeast(1f)
            scrollBy(step)
            if (position() <= before + 0.1f) { reasons += "SCROLL_STOPPED_BEFORE_RECORDED_END"; break }
            if (index == 63) reasons += "BOUNDED_CAPTURE_LIMIT_REACHED"
        }
    } finally {
        if (supported && scroller != null) {
            scrollBy(original - position())
            restored = abs(position() - original) <= 1f
        }
    }
    val synthetic = System.getProperty("goat.moduleReview.class").orEmpty().endsWith("NativeReviewEvidenceContractTest")
    metaFile.writeText(JSONObject().put("schema_version", 2).put("screen_id", screenId).put("visit", visit)
        .put("test", case).put("test_class", System.getProperty("goat.moduleReview.class"))
        .put("owner_node_id", owner.id).put("owner_instance_tag", owner.config.getOrNull(SemanticsProperties.TestTag))
        .put("evidence_class", if (synthetic) "SYNTHETIC_EVIDENCE_COLLECTOR_TEST_NOT_PRODUCT" else "OBSERVED_NATIVE_PRODUCT_OWNER")
        .put("source", "actual Compose tree").put("variant", variant).put("owner_observed_before_capture", true)
        .put("mode", System.getenv("GOAT_MODULE_REVIEW_MODE") ?: "LIGHT")
        .put("width_dp", (System.getenv("GOAT_MODULE_REVIEW_WIDTH") ?: "411").toInt())
        .put("actual_device_width_dp", org.robolectric.RuntimeEnvironment.getApplication().resources.configuration.screenWidthDp)
        .put("actual_device_height_dp", org.robolectric.RuntimeEnvironment.getApplication().resources.configuration.screenHeightDp)
        .put("font_scale", (System.getenv("GOAT_MODULE_REVIEW_SCALE") ?: "1").toDouble())
        .put("vertical_scroll_containers", scrolls.size).put("vertical_sweep_complete", complete)
        .put("scroll_restored", restored).put("incomplete_reasons", JSONArray(reasons)).put("viewports", pages)
        .put("accessibility_manual_review", "TALKBACK_FOCUS_AND_HUMAN_PIXEL_REVIEW_REMAIN_REQUIRED")
        .put("approval", "UNAPPROVED_REVIEW_CANDIDATE").toString(2))
    check(restored) { "Native review must restore the original page scroll position" }
}

private fun overlaps(a: Rect, b: Rect): Boolean = a.width > 0f && a.height > 0f && a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top
private fun rectangle(a: Rect): JSONArray = JSONArray(listOf(a.left, a.top, a.right, a.bottom))
/** Date picker instances retain their scoped field/calendar tag. Child button labels are not owners. */
internal data class NativeOwnerBinding(val id: String, val tag: String, val nodeId: Int, val kind: String)
internal fun nativeReviewOwnerBinding(node: SemanticsNode): NativeOwnerBinding? {
    val tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: return null
    val exact = when {
        tag.startsWith("farm-screen:") -> tag.removePrefix("farm-screen:")
        tag.startsWith("farm-atom:") -> tag.removePrefix("farm-atom:")
        else -> null
    }
    if (exact != null && NATIVE_REVIEW_ID.matches(exact)) return NativeOwnerBinding(exact, tag, node.id, "EXACT_CANONICAL_TAG")
    val prefix = com.farmos.core.design.FarmDateAtoms.DATE_PICKER + ":"
    if (tag.startsWith(prefix) && tag.length > prefix.length) {
        if (node.config.contains(SemanticsProperties.EditableText)) return NativeOwnerBinding("FOS-ATOM-001", tag, node.id, "SCOPED_DATE_EDITABLE_FIELD")
        if (tag.endsWith(":calendar")) return NativeOwnerBinding("FOS-ATOM-001", tag, node.id, "SCOPED_DATE_CALENDAR")
    }
    return null
}
private fun reviewSha256(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
private fun pngSize(file: File): Pair<Int, Int> = DataInputStream(file.inputStream()).use {
    check(it.readLong() == -8552249625308161526L) { "Native capture is not PNG" }
    check(it.readInt() == 13 && it.readInt() == 0x49484452) { "Missing PNG dimensions" }
    it.readInt() to it.readInt()
}
