package com.farmos.core.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Theme choices authorized by the Animal Farm visual-lock package.
 * Outdoor remains a native candidate and must not be certified from light/dark evidence.
 */
enum class AnimalFarmThemeMode(
    val requiresNativeAcceptance: Boolean,
) {
    LIGHT(false),
    DARK(false),
    OUTDOOR(true),
}

@Immutable
data class AnimalFarmPalette(
    val background: Color,
    val surface: Color,
    val softSurface: Color,
    val ink: Color,
    val mutedInk: Color,
    val divider: Color,
    val primary: Color,
    val onPrimary: Color,
    val lime: Color,
    val onLime: Color,
    val warningSurface: Color,
    val onWarning: Color,
    val critical: Color,
    val positive: Color,
    val information: Color,
    val withdrawal: Color,
)

/** Exact locked web pairs plus the retained native outdoor candidate. */
object AnimalFarmColors {
    val Light =
        AnimalFarmPalette(
            background = Color(0xFFF3F4ED),
            surface = Color(0xFFFFFFFF),
            softSurface = Color(0xFFE8ECE3),
            ink = Color(0xFF183328),
            mutedInk = Color(0xFF5B6A5E),
            divider = Color(0xFFDCE3D7),
            primary = Color(0xFF285640),
            onPrimary = Color(0xFFFFFFFF),
            lime = Color(0xFFDCEBBA),
            onLime = Color(0xFF294128),
            warningSurface = Color(0xFFF8E4B9),
            onWarning = Color(0xFF67420D),
            critical = Color(0xFFB3261E),
            positive = Color(0xFF2E6B34),
            information = Color(0xFF3A5A78),
            withdrawal = Color(0xFF8E3B62),
        )

    val Dark =
        AnimalFarmPalette(
            background = Color(0xFF141B17),
            surface = Color(0xFF222C25),
            softSurface = Color(0xFF303C32),
            ink = Color(0xFFEFF4E8),
            mutedInk = Color(0xFFB5C2B4),
            divider = Color(0xFF3C4A3D),
            primary = Color(0xFFC7E9A3),
            onPrimary = Color(0xFF1B3627),
            lime = Color(0xFF3A5130),
            onLime = Color(0xFFE0EFCB),
            warningSurface = Color(0xFF5A4422),
            onWarning = Color(0xFFFFE1A2),
            critical = Color(0xFFFFB4AB),
            positive = Color(0xFFA0D49F),
            information = Color(0xFFAFCCE8),
            withdrawal = Color(0xFFE8A5C4),
        )

    /**
     * Candidate inherited from the retained native baseline where the new lock is silent.
     * White canvas, black ink and #23432D primary are explicitly governed; remaining
     * semantic pairs retain the native baseline until independent outdoor acceptance.
     */
    val OutdoorCandidate =
        AnimalFarmPalette(
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFFFFFFF),
            softSurface = Color(0xFFF1F1EA),
            ink = Color(0xFF000000),
            mutedInk = Color(0xFF33372F),
            divider = Color(0xFFD8DED6),
            primary = Color(0xFF23432D),
            onPrimary = Color(0xFFFFFFFF),
            lime = Color(0xFFDCEBBA),
            onLime = Color(0xFF294128),
            warningSurface = Color(0xFFF8E4B9),
            onWarning = Color(0xFF67420D),
            critical = Color(0xFF8C1D18),
            positive = Color(0xFF24532A),
            information = Color(0xFF28435D),
            withdrawal = Color(0xFF6C2448),
        )

    fun forMode(mode: AnimalFarmThemeMode): AnimalFarmPalette =
        when (mode) {
            AnimalFarmThemeMode.LIGHT -> Light
            AnimalFarmThemeMode.DARK -> Dark
            AnimalFarmThemeMode.OUTDOOR -> OutdoorCandidate
        }
}

object AnimalFarmTouchTarget {
    fun minimumDp(mode: AnimalFarmThemeMode): Int = if (mode == AnimalFarmThemeMode.OUTDOOR) 64 else 48
}

internal val LocalAnimalFarmPalette = staticCompositionLocalOf { AnimalFarmColors.Light }
internal val LocalAnimalFarmThemeMode = staticCompositionLocalOf { AnimalFarmThemeMode.LIGHT }

object AnimalFarmTheme {
    val colors: AnimalFarmPalette
        @Composable get() = LocalAnimalFarmPalette.current

    val mode: AnimalFarmThemeMode
        @Composable get() = LocalAnimalFarmThemeMode.current

    val minimumTouchDp: Int
        @Composable get() = AnimalFarmTouchTarget.minimumDp(mode)
}

internal fun AnimalFarmPalette.materialScheme(mode: AnimalFarmThemeMode): ColorScheme {
    val base = if (mode == AnimalFarmThemeMode.DARK) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = lime,
        onPrimaryContainer = onLime,
        secondary = primary,
        onSecondary = onPrimary,
        secondaryContainer = softSurface,
        onSecondaryContainer = ink,
        tertiary = primary,
        onTertiary = onPrimary,
        tertiaryContainer = warningSurface,
        onTertiaryContainer = onWarning,
        background = background,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = softSurface,
        onSurfaceVariant = mutedInk,
        surfaceTint = primary,
        surfaceBright = surface,
        surfaceDim = softSurface,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = softSurface,
        surfaceContainerHighest = softSurface,
        inverseSurface = ink,
        inverseOnSurface = background,
        inversePrimary = if (mode == AnimalFarmThemeMode.DARK) AnimalFarmColors.Light.primary else AnimalFarmColors.Dark.primary,
        outline = mutedInk,
        outlineVariant = divider,
        error = critical,
        onError = background,
        errorContainer = warningSurface,
        onErrorContainer = onWarning,
        scrim = Color.Black,
    )
}
