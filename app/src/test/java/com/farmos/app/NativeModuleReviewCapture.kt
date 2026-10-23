package com.farmos.app

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
internal class NativeModuleReviewRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val output = System.getenv("GOAT_MODULE_REVIEW_DIR")
            if (output.isNullOrBlank()) { base.evaluate(); return }
            val width = System.getenv("GOAT_MODULE_REVIEW_WIDTH")?.toIntOrNull() ?: 411
            require(width in setOf(360, 411, 780))
            RuntimeEnvironment.setQualifiers("en-rUS-w${width}dp-h${if (width >= 700) 1024 else 900}dp-mdpi")
            System.setProperty("goat.moduleReview.case", description.className.substringAfterLast('.') + "." + description.methodName)
            try { base.evaluate() } finally { System.clearProperty("goat.moduleReview.case") }
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

/** The current native tree is captured after real navigation and assertions, not a synthetic redraw. */
internal fun ComposeContentTestRule.captureModuleReview(screenId: String) {
    val requested = System.getenv("GOAT_MODULE_REVIEW_DIR") ?: return
    require(screenId.matches(Regex("[A-Za-z0-9_.:-]+")))
    val output = File(requested).canonicalFile
    require(output.isAbsolute && !output.path.contains("/references/") && !output.path.contains("/golden"))
    val case = System.getProperty("goat.moduleReview.case") ?: error("NativeModuleReviewRule is required")
    val directory = File(output, case).also { require(it.isDirectory || it.mkdirs()) }
    waitForIdle()
    val scrollables = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)).fetchSemanticsNodes()
    if (scrollables.size == 1) {
        onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -100000f) }
        waitForIdle()
    }
    fun snapshot(suffix: String) {
        waitForIdle()
        val filename = screenId.replace(':', '-') + suffix
        onRoot().captureRoboImage(File(directory, "$filename.png").absolutePath)
        File(directory, "$filename.json").writeText(JSONObject()
            .put("screen_id", screenId).put("test", case).put("source", "actual Compose tree")
            .put("variant", suffix.ifBlank { "asserted destination" })
            .put("mode", System.getenv("GOAT_MODULE_REVIEW_MODE") ?: "LIGHT")
            .put("width_dp", System.getenv("GOAT_MODULE_REVIEW_WIDTH") ?: "411")
            .put("font_scale", System.getenv("GOAT_MODULE_REVIEW_SCALE") ?: "1")
            .put("approval", "UNAPPROVED_REVIEW_CANDIDATE").toString(2))
    }
    snapshot("")
    val timeline = SemanticsMatcher.expectValue(SemanticsProperties.TestTag, "farm-section:Timeline")
    val tabs = onAllNodes(timeline).fetchSemanticsNodes()
    if (tabs.size == 1 && tabs.single().config.getOrNull(SemanticsProperties.Selected) != true) {
        onNode(timeline).performScrollTo().performClick()
        if (onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)).fetchSemanticsNodes().size == 1) {
            onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -100000f) }
        }
        snapshot("-workflow")
        onNodeWithTag("farm-section:Records").performScrollTo().performClick()
    }
}

internal fun ComposeContentTestRule.captureCurrentModuleReview() {
    if (System.getenv("GOAT_MODULE_REVIEW_DIR").isNullOrBlank()) return
    val nodes = onAllNodes(SemanticsMatcher("owning screen") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("farm-screen:FOS-") == true
    }).fetchSemanticsNodes()
    val screenId = nodes.firstOrNull()?.config?.getOrNull(SemanticsProperties.TestTag)?.removePrefix("farm-screen:")
        ?: error("No owning screen found for native review")
    captureModuleReview(screenId)
}
