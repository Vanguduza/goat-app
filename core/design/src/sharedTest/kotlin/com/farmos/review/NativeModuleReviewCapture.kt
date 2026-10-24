package com.farmos.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import com.farmos.core.design.AnimalFarmThemeMode
import com.farmos.core.design.FarmOsTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.json.JSONObject
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.RuntimeEnvironment

/** Opt-in review candidates only; no reference image or acceptance gate is written. */
class NativeModuleReviewRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val output = System.getenv("GOAT_MODULE_REVIEW_DIR")
            if (output.isNullOrBlank()) { base.evaluate(); return }
            val width = System.getenv("GOAT_MODULE_REVIEW_WIDTH")?.toIntOrNull() ?: 411
            require(width in setOf(360, 411, 780))
            RuntimeEnvironment.setQualifiers("en-rUS-w${width}dp-h${if (width >= 700) 1024 else 900}dp-mdpi")
            System.setProperty("goat.moduleReview.case", description.className.substringAfterLast('.') + "." + description.methodName)
            System.setProperty("goat.moduleReview.class", description.className)
            NativeReviewVisits.reset(); NativeReviewScenes.reset()
            try { base.evaluate() } finally {
                System.clearProperty("goat.moduleReview.case")
                System.clearProperty("goat.moduleReview.class")
                NativeReviewVisits.reset(); NativeReviewScenes.reset()
            }
        }
    }
}

@Composable
internal fun NativeModuleReviewTheme(content: @Composable () -> Unit) {
    val capture = !System.getenv("GOAT_MODULE_REVIEW_DIR").isNullOrBlank()
    val mode = if (capture) AnimalFarmThemeMode.valueOf(System.getenv("GOAT_MODULE_REVIEW_MODE") ?: "LIGHT") else AnimalFarmThemeMode.LIGHT
    val current = LocalDensity.current
    val scale = if (capture) System.getenv("GOAT_MODULE_REVIEW_SCALE")?.toFloatOrNull() ?: 1f else current.fontScale
    require(scale in 1f..2f)
    CompositionLocalProvider(LocalDensity provides Density(current.density, scale)) {
        FarmOsTheme(mode = mode, content = content)
    }
}

/** Captures actual owner semantics and every reachable viewport of a plain vertical page. */
internal fun ComposeContentTestRule.captureModuleReview(screenId: String) {
    val requested = System.getenv("GOAT_MODULE_REVIEW_DIR") ?: return
    require(NATIVE_REVIEW_ID.matches(screenId)) { "Invalid native owner ID: $screenId" }
    val output = File(requested).canonicalFile
    require(output.isAbsolute && !output.path.contains("/references/") && !output.path.contains("/golden"))
    val case = requireNotNull(System.getProperty("goat.moduleReview.case")) { "NativeModuleReviewRule is required" }
    waitForIdle()
    requireNativeReviewOwner(screenId) // No directory or picture may manufacture owner evidence.
    val visit = NativeReviewVisits.next(screenId)
    val directory = File(output, case).also { require(it.isDirectory || it.mkdirs()) }
    val prefix = screenId + if (visit == 1) "" else "-visit" + visit.toString().padStart(3, '0')
    captureNativeReviewViewports(screenId, prefix, directory, case, visit, "asserted destination")
    val timeline = SemanticsMatcher.expectValue(SemanticsProperties.TestTag, "farm-section:Timeline")
    val tabs = onAllNodes(timeline).fetchSemanticsNodes()
    if (tabs.size == 1 && tabs.single().config.getOrNull(SemanticsProperties.Selected) != true) {
        onNode(timeline).performScrollTo().performClick()
        try {
            captureNativeReviewViewports(screenId, prefix + "-workflow", directory, case, visit, "workflow alternative")
        } finally {
            onNodeWithTag("farm-section:Records").performScrollTo().performClick()
        }
    }
}

internal fun ComposeContentTestRule.captureCurrentModuleReview() {
    if (System.getenv("GOAT_MODULE_REVIEW_DIR").isNullOrBlank()) return
    val owners = onAllNodes(SemanticsMatcher("owning screen") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("farm-screen:FOS-") == true
    }, useUnmergedTree = true).fetchSemanticsNodes().filter { it.layoutInfo.isPlaced }
    val owner = owners.minByOrNull { node -> generateSequence(node.parent) { it.parent }.count() }
        ?: error("No rendered owner found for native review")
    captureModuleReview(requireNotNull(owner.config.getOrNull(SemanticsProperties.TestTag)).removePrefix("farm-screen:"))
}

internal val NATIVE_REVIEW_ID = Regex("FOS-[A-Z]+-[0-9]{3}(?:-[A-Z])?")
internal object NativeReviewVisits {
    private val counts = mutableMapOf<String, Int>()
    fun reset() = counts.clear()
    fun next(id: String): Int = (counts.getOrDefault(id, 0) + 1).also { counts[id] = it }
}
