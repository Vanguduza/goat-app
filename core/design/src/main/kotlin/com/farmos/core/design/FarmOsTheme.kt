package com.farmos.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object FosColors {
    val Primary = Color(0xFF1E4D2B)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFFE2EFE3)
    val Canvas = Color(0xFFF8F5EC)
    val Card = Color(0xFFFFFCF3)
    val Sunken = Color(0xFFF1F1EA)
    val TextPrimary = Color(0xFF1F2937)
    val TextSecondary = Color(0xFF526157)
    val Hairline = Color(0xFFD8DED6)
    val Critical = Color(0xFFB3261E)
    val Warning = Color(0xFF8A5A00)
    val Positive = Color(0xFF2E6B34)
    val Info = Color(0xFF3A5A78)
    val Withdrawal = Color(0xFF8E3B62)
    val Goat = Color(0xFFA9762B)
    val Sage = Color(0xFF4E7F52)
    val Leaf = Color(0xFF7FB069)
    val Sky = Color(0xFF7ECBF5)
    val Sunlight = Color(0xFFF4E6C7)
    val BarnRed = Color(0xFFC9483D)
    val Soil = Color(0xFF8B6847)
}

private val LightColors = lightColorScheme(
    primary = FosColors.Primary,
    onPrimary = FosColors.OnPrimary,
    primaryContainer = FosColors.PrimaryContainer,
    background = FosColors.Canvas,
    surface = FosColors.Card,
    onBackground = FosColors.TextPrimary,
    onSurface = FosColors.TextPrimary,
    outline = FosColors.Hairline,
    error = FosColors.Critical,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BC49F),
    background = Color(0xFF121411),
    surface = Color(0xFF1C1F1B),
    onBackground = Color(0xFFF3F4EE),
    onSurface = Color(0xFFF3F4EE),
    outline = Color(0xFF454940),
    error = Color(0xFFFFB4AB),
)

private val FarmShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
)

@Composable
fun FarmOsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = FarmShapes,
        typography = FarmOsTypography,
        content = content,
    )
}
