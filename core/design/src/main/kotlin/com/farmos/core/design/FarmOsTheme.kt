package com.farmos.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Legacy visual constants retained only while older surfaces are migrated to semantic tokens.
 * New Animal Farm surfaces must consume [AnimalFarmTheme] / MaterialTheme instead.
 */
@Deprecated("Use AnimalFarmTheme semantic tokens")
object FosColors {
    val Primary = AnimalFarmColors.Light.primary
    val OnPrimary = AnimalFarmColors.Light.onPrimary
    val PrimaryContainer = AnimalFarmColors.Light.lime
    val Canvas = AnimalFarmColors.Light.background
    val Card = AnimalFarmColors.Light.surface
    val Sunken = AnimalFarmColors.Light.softSurface
    val TextPrimary = AnimalFarmColors.Light.ink
    val TextSecondary = AnimalFarmColors.Light.mutedInk
    val Hairline = AnimalFarmColors.Light.divider
    val Critical = AnimalFarmColors.Light.critical
    val Warning = AnimalFarmColors.Light.onWarning
    val Positive = AnimalFarmColors.Light.positive
    val Info = AnimalFarmColors.Light.information
    val Withdrawal = AnimalFarmColors.Light.withdrawal

    // Historical illustrated-surface migration constants. They are not new theme authority.
    val Goat = Color(0xFFA9762B)
    val Sage = Color(0xFF4E7F52)
    val Leaf = Color(0xFF7FB069)
    val Sky = Color(0xFF7ECBF5)
    val Sunlight = Color(0xFFF4E6C7)
    val BarnRed = Color(0xFFC9483D)
    val Soil = Color(0xFF8B6847)
}

private val FarmShapes =
    Shapes(
        extraSmall =
            androidx.compose.foundation.shape
                .RoundedCornerShape(8.dp),
        small =
            androidx.compose.foundation.shape
                .RoundedCornerShape(16.dp),
        medium =
            androidx.compose.foundation.shape
                .RoundedCornerShape(20.dp),
        large =
            androidx.compose.foundation.shape
                .RoundedCornerShape(24.dp),
        extraLarge =
            androidx.compose.foundation.shape
                .RoundedCornerShape(28.dp),
    )

@Composable
@Suppress("ktlint:standard:function-naming")
fun FarmOsTheme(
    mode: AnimalFarmThemeMode = if (isSystemInDarkTheme()) AnimalFarmThemeMode.DARK else AnimalFarmThemeMode.LIGHT,
    onModeChange: (AnimalFarmThemeMode) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val colors = AnimalFarmColors.forMode(mode)
    CompositionLocalProvider(
        LocalAnimalFarmPalette provides colors,
        LocalAnimalFarmThemeMode provides mode,
        LocalAnimalFarmThemeChange provides onModeChange,
    ) {
        MaterialTheme(
            colorScheme = colors.materialScheme(mode),
            shapes = FarmShapes,
            typography = FarmOsTypography,
            content = content,
        )
    }
}
