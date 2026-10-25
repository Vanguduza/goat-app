package com.farmos.core.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Geometry and hierarchy from the immutable approved HTML (blob 6baaea08).
 * Inter is the explicit native adaptation; Android touch/large-text rules remain mandatory.
 */
object AnimalFarmHomeMetrics {
    val lineIconSize = 16.dp
    val familyChoiceHeight = 90.dp
    val familyChoiceRadius = 17.dp
    val pageInset = 18.dp
    val headerInset = 22.dp
    val heroPadding = 20.dp
    val focusPadding = 21.dp
    val contentGap = 16.dp
    val rowPadding = 15.dp
    val identityThumbnail = 56.dp
    val metricMinimumHeight = 112.dp
    val tileGap = 10.dp
    val moduleGap = 8.dp
    val heroRadius = 24.dp
    val focusRadius = 28.dp
    val tileRadius = 20.dp
    val actionRadius = 16.dp
    val secondaryActionRadius = 14.dp
    val fieldRadius = 12.dp
    val sheetRadius = 26.dp
    val resourceRadius = 18.dp
    val compactPortrait = 88.dp
    val taskPortrait = 154.dp
    val heroPortraitWidth = 100.dp
    val heroPortraitHeight = 120.dp
    val focusPortraitWidth = 150.dp
    val focusPortraitHeight = 154.dp
    val moduleMinimumHeight = 78.dp
    val summaryMinimumHeight = 128.dp
}

object AnimalFarmHtmlType {
    private fun face(size: Int, line: Number, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f) =
        TextStyle(fontFamily = FarmOsInter, fontWeight = weight, fontSize = size.sp, lineHeight = line.toFloat().sp, letterSpacing = tracking.sp)
    val pageTitle = face(28, 35, FontWeight.Medium, -0.9f)
    val heroTitle = face(26, 32.5f, FontWeight.Medium, -0.4f)
    val focusTitle = face(30, 37.5f, FontWeight.Medium, -0.8f)
    val sheetTitle = face(21, 26.25f, FontWeight.Medium, -0.4f)
    val reviewTitle = face(23, 28.75f, FontWeight.Medium, -0.5f)
    val sectionTitle = face(16, 20, FontWeight.Medium)
    val body = face(15, 21)
    val action = face(15, 21, FontWeight.Medium)
    val field = face(15, 21)
    val brand = face(14, 19.6f, FontWeight.Medium)
    val subject = face(13, 18.2f, FontWeight.Medium)
    val familyChoice = face(13, 18.2f)
    val caption = face(12, 16.8f)
    val navigation = face(11, 15.4f)
    val eyebrow = face(11, 15.4f, FontWeight.Medium, 1.25f)
    val calendarDay = face(30, 30, tracking = -1f)
    val metricValue = face(24, 30, FontWeight.Medium, -0.5f).copy(fontFeatureSettings = "tnum")
    val numeric = face(32, 32, FontWeight.Medium, -1f).copy(fontFeatureSettings = "tnum")
}

@Composable
fun animalFarmHomePageInset() = if (LocalConfiguration.current.screenWidthDp <= 380) 13.dp else AnimalFarmHomeMetrics.pageInset
@Composable
fun animalFarmHomeHeaderInset() = if (LocalConfiguration.current.screenWidthDp <= 380) 17.dp else AnimalFarmHomeMetrics.headerInset

/** Apply the same type hierarchy to Material-backed feature pages, not only custom home widgets. */
val AnimalFarmHtmlTypography = androidx.compose.material3.Typography(
    displayLarge = AnimalFarmHtmlType.numeric,
    displayMedium = AnimalFarmHtmlType.focusTitle,
    displaySmall = AnimalFarmHtmlType.pageTitle,
    headlineLarge = AnimalFarmHtmlType.focusTitle,
    headlineMedium = AnimalFarmHtmlType.pageTitle,
    headlineSmall = AnimalFarmHtmlType.reviewTitle,
    titleLarge = AnimalFarmHtmlType.sheetTitle,
    titleMedium = AnimalFarmHtmlType.sectionTitle,
    titleSmall = AnimalFarmHtmlType.brand,
    bodyLarge = AnimalFarmHtmlType.body,
    bodyMedium = AnimalFarmHtmlType.brand.copy(fontWeight = FontWeight.Normal),
    bodySmall = AnimalFarmHtmlType.caption,
    labelLarge = AnimalFarmHtmlType.action,
    labelMedium = AnimalFarmHtmlType.caption.copy(fontWeight = FontWeight.Medium),
    labelSmall = AnimalFarmHtmlType.navigation,
)
