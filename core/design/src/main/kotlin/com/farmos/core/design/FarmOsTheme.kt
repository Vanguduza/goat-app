package com.farmos.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object FosColors {
    val Primary = Color(0xFF2C5539)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFFDDE8DD)
    val Canvas = Color(0xFFFAFAF6)
    val Card = Color(0xFFFFFFFF)
    val Sunken = Color(0xFFF1F1EA)
    val TextPrimary = Color(0xFF1B1D1A)
    val TextSecondary = Color(0xFF5A5D57)
    val Hairline = Color(0xFFE3E4DC)
    val Critical = Color(0xFFB3261E)
    val Warning = Color(0xFF8A5A00)
    val Positive = Color(0xFF2E6B34)
    val Info = Color(0xFF3A5A78)
    val Withdrawal = Color(0xFF8E3B62)
    val Goat = Color(0xFFA9762B)
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
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
)

/**
 * Typography intentionally remains platform-backed until the repository receives the approved Inter font asset.
 * UI certification must remain blocked until Inter is bundled and this Typography is replaced by the governed scale.
 */
private val BootstrapTypography = Typography()

@Composable
fun FarmOsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = FarmShapes,
        typography = BootstrapTypography,
        content = content,
    )
}
