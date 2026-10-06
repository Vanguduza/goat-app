package com.farmos.core.design

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnimalFarmThemeTokensTest {
    @Test
    fun lightPaletteMatchesLockedWebTokensExactly() {
        val palette = AnimalFarmColors.forMode(AnimalFarmThemeMode.LIGHT)
        assertEquals(Color(0xFFF3F4ED), palette.background)
        assertEquals(Color(0xFFFFFFFF), palette.surface)
        assertEquals(Color(0xFFE8ECE3), palette.softSurface)
        assertEquals(Color(0xFF183328), palette.ink)
        assertEquals(Color(0xFF5B6A5E), palette.mutedInk)
        assertEquals(Color(0xFFDCE3D7), palette.divider)
        assertEquals(Color(0xFF285640), palette.primary)
        assertEquals(Color(0xFFFFFFFF), palette.onPrimary)
        assertEquals(Color(0xFFDCEBBA), palette.lime)
        assertEquals(Color(0xFF294128), palette.onLime)
        assertEquals(Color(0xFFF8E4B9), palette.warningSurface)
        assertEquals(Color(0xFF67420D), palette.onWarning)
    }

    @Test
    fun darkPaletteMatchesLockedWebTokensExactly() {
        val palette = AnimalFarmColors.forMode(AnimalFarmThemeMode.DARK)
        assertEquals(Color(0xFF141B17), palette.background)
        assertEquals(Color(0xFF222C25), palette.surface)
        assertEquals(Color(0xFF303C32), palette.softSurface)
        assertEquals(Color(0xFFEFF4E8), palette.ink)
        assertEquals(Color(0xFFB5C2B4), palette.mutedInk)
        assertEquals(Color(0xFF3C4A3D), palette.divider)
        assertEquals(Color(0xFFC7E9A3), palette.primary)
        assertEquals(Color(0xFF1B3627), palette.onPrimary)
        assertEquals(Color(0xFF3A5130), palette.lime)
        assertEquals(Color(0xFFE0EFCB), palette.onLime)
        assertEquals(Color(0xFF5A4422), palette.warningSurface)
        assertEquals(Color(0xFFFFE1A2), palette.onWarning)
    }

    @Test
    fun outdoorModeRetainsCandidateStatusAndFieldTouchTarget() {
        val palette = AnimalFarmColors.forMode(AnimalFarmThemeMode.OUTDOOR)
        assertEquals(Color.White, palette.background)
        assertEquals(Color.Black, palette.ink)
        assertEquals(Color(0xFF23432D), palette.primary)
        assertTrue(AnimalFarmThemeMode.OUTDOOR.requiresNativeAcceptance)
        assertFalse(AnimalFarmThemeMode.LIGHT.requiresNativeAcceptance)
        assertEquals(64, AnimalFarmTouchTarget.minimumDp(AnimalFarmThemeMode.OUTDOOR))
        assertEquals(48, AnimalFarmTouchTarget.minimumDp(AnimalFarmThemeMode.LIGHT))
    }
}
