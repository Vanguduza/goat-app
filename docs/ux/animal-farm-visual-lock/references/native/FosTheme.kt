package com.farmos.design.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farmos.design.R
import com.farmos.model.Species

enum class ThemeMode { Light, Dark, Outdoor }

data class FosPalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val canvas: Color,
    val card: Color,
    val sunken: Color,
    val text: Color,
    val secondary: Color,
    val hairline: Color,
    val critical: Color,
    val warning: Color,
    val positive: Color,
    val info: Color,
    val withdrawal: Color,
    val goat: Color,
    val rabbit: Color,
) {
    fun species(species: Species): Color = when (species) {
        Species.Goat -> goat
        Species.Rabbit -> rabbit
        else -> secondary
    }
}

/** Hand-authored semantic tokens implementing design spec sections 2–4. */
object FosColors {
    val Light = FosPalette(
        primary = Color(0xFF2C5539), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDDE8DD), canvas = Color(0xFFFAFAF6),
        card = Color(0xFFFFFFFF), sunken = Color(0xFFF1F1EA),
        text = Color(0xFF1B1D1A), secondary = Color(0xFF5A5D57),
        hairline = Color(0xFFE3E4DC), critical = Color(0xFFB3261E),
        warning = Color(0xFF8A5A00), positive = Color(0xFF2E6B34),
        info = Color(0xFF3A5A78), withdrawal = Color(0xFF8E3B62),
        goat = Color(0xFFA9762B), rabbit = Color(0xFF5B7C99),
    )
    val Dark = FosPalette(
        primary = Color(0xFF9BC49F), onPrimary = Color(0xFF16351E),
        primaryContainer = Color(0xFF2B4430), canvas = Color(0xFF121411),
        card = Color(0xFF1C1F1B), sunken = Color(0xFF252A23),
        text = Color(0xFFE5E8DF), secondary = Color(0xFFB5BAAF),
        hairline = Color(0xFF3E443B), critical = Color(0xFFFFB4AB),
        warning = Color(0xFFE4BC78), positive = Color(0xFFA0D49F),
        info = Color(0xFFAFCCE8), withdrawal = Color(0xFFE8A5C4),
        goat = Color(0xFFDDB573), rabbit = Color(0xFFA8C3DB),
    )
    val Outdoor = Light.copy(
        primary = Color(0xFF23432D), canvas = Color(0xFFFFFFFF),
        text = Color(0xFF000000), secondary = Color(0xFF33372F),
        critical = Color(0xFF8C1D18), warning = Color(0xFF644000),
        positive = Color(0xFF24532A), info = Color(0xFF28435D),
        withdrawal = Color(0xFF6C2448),
    )
    fun forMode(mode: ThemeMode) = when (mode) {
        ThemeMode.Light -> Light
        ThemeMode.Dark -> Dark
        ThemeMode.Outdoor -> Outdoor
    }
}

object FosSpace {
    val hairline = 1.dp
    val xs = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val page = 16.dp
    val section = 24.dp
    val touch = 48.dp
    val row = 56.dp
    val outdoorTouch = 64.dp
    val icon = 24.dp
    val statusDot = 6.dp
    val speciesDot = 8.dp
    val contentMax = 780.dp
    val formMax = 480.dp
    const val animalPortraitRatio = 1.5f
    val skeletonTitle = 160.dp
    val skeletonBody = 240.dp
    val skeletonLine = 16.dp
}

@OptIn(ExperimentalTextApi::class)
private val Inter = FontFamily(
    Font(R.font.inter_variable, FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter_variable, FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter_variable, FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

object FosText {
    val display = TextStyle(fontFamily = Inter, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold)
    val titleLg = TextStyle(fontFamily = Inter, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
    val titleSm = TextStyle(fontFamily = Inter, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontFamily = Inter, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)
    val bodyStrong = body.copy(fontWeight = FontWeight.SemiBold)
    val label = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    val numeric = body.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
    val numericLg = TextStyle(fontFamily = Inter, fontSize = 24.sp, lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
}

private val LocalPalette = staticCompositionLocalOf { FosColors.Light }
private val LocalMode = staticCompositionLocalOf { ThemeMode.Light }
object FosTheme {
    val colors: FosPalette @Composable get() = LocalPalette.current
    val mode: ThemeMode @Composable get() = LocalMode.current
    val minTouch @Composable get() = if (mode == ThemeMode.Outdoor) FosSpace.outdoorTouch else FosSpace.touch
}

/** Map component defaults as well as explicit screen colours; menus must not inherit purple. */
internal fun FosPalette.materialScheme(mode: ThemeMode): ColorScheme {
    val base = if (mode == ThemeMode.Dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = primaryContainer, onPrimaryContainer = text,
        secondary = primary, onSecondary = onPrimary,
        secondaryContainer = primaryContainer, onSecondaryContainer = text,
        tertiary = primary, onTertiary = onPrimary,
        tertiaryContainer = primaryContainer, onTertiaryContainer = text,
        background = canvas, onBackground = text,
        surface = card, onSurface = text,
        surfaceVariant = sunken, onSurfaceVariant = secondary,
        surfaceTint = primary,
        surfaceBright = card, surfaceDim = sunken,
        surfaceContainerLowest = canvas, surfaceContainerLow = card,
        surfaceContainer = card, surfaceContainerHigh = sunken, surfaceContainerHighest = sunken,
        inverseSurface = text, inverseOnSurface = canvas,
        inversePrimary = if (mode == ThemeMode.Dark) FosColors.Light.primary else FosColors.Dark.primary,
        outline = secondary, outlineVariant = hairline,
        error = critical, onError = canvas,
        errorContainer = card, onErrorContainer = critical,
        scrim = Color.Black,
    )
}

@Composable
fun FarmOsTheme(
    mode: ThemeMode = if (isSystemInDarkTheme()) ThemeMode.Dark else ThemeMode.Light,
    content: @Composable () -> Unit,
) {
    val colors = FosColors.forMode(mode)
    CompositionLocalProvider(LocalPalette provides colors, LocalMode provides mode) {
        MaterialTheme(
            colorScheme = colors.materialScheme(mode),
            typography = Typography(
                displayLarge = FosText.display, displayMedium = FosText.display, displaySmall = FosText.display,
                headlineLarge = FosText.titleLg, headlineMedium = FosText.titleLg, headlineSmall = FosText.titleLg,
                titleLarge = FosText.titleLg, titleMedium = FosText.titleSm, titleSmall = FosText.titleSm,
                bodyLarge = FosText.body, bodyMedium = FosText.body, bodySmall = FosText.label,
                labelLarge = FosText.label, labelMedium = FosText.label, labelSmall = FosText.label,
            ),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(FosSpace.small), small = RoundedCornerShape(FosSpace.small),
                medium = RoundedCornerShape(FosSpace.medium), large = RoundedCornerShape(FosSpace.medium),
                extraLarge = RoundedCornerShape(FosSpace.page),
            ),
            content = content,
        )
    }
}
