package com.farmos.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

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
        LocalMinimumInteractiveComponentSize provides AnimalFarmTouchTarget.minimumDp(mode).dp,
    ) {
        MaterialTheme(
            colorScheme = colors.materialScheme(mode),
            shapes = FarmShapes,
            typography = FarmOsTypography,
            content = content,
        )
    }
}
