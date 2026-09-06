package com.farmos.design.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Scoped owner-approved A / D+C amendment; existing module tokens stay unchanged. */
data class HomePalette(val hero: Color, val onHero: Color, val accent: Color, val onAccent: Color)

object HomeTokens {
    val cardRadius = 24.dp
    val heroRadius = 28.dp
    val actionRadius = 16.dp
    val portrait = 152.dp
    val profilePortrait = 184.dp
    val compactPortrait = 96.dp
    val familyPortrait = 88.dp
    val badgePortrait = 32.dp
    val minimumCard = 176.dp
    val emptyStage = 176.dp
    val homeMax = 780.dp

    fun palette(mode: ThemeMode): HomePalette = when (mode) {
        ThemeMode.Light -> HomePalette(Color(0xFF285744), Color.White, Color(0xFFDFEDBA), Color(0xFF193725))
        ThemeMode.Dark -> HomePalette(Color(0xFFCDE6A6), Color(0xFF182916), Color(0xFF34432D), Color(0xFFE3F0CA))
        ThemeMode.Outdoor -> HomePalette(Color(0xFF23432D), Color.White, Color(0xFFE5F1CD), Color.Black)
    }
}
