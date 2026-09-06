package com.farmos.core.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnimalFarmFamilyArtTest {
    @Test
    fun familyWindowsMatchLockedSheetCoordinates() {
        assertEquals(AnimalFarmPortraitWindow(265, 195, 260, 370), AnimalFarmFamilyWindows.Goat)
        assertEquals(AnimalFarmPortraitWindow(480, 515, 155, 280), AnimalFarmFamilyWindows.Rabbit)
        assertEquals(AnimalFarmPortraitWindow(722, 435, 205, 360), AnimalFarmFamilyWindows.Poultry)
        assertEquals(AnimalFarmPortraitWindow(1050, 230, 280, 350), AnimalFarmFamilyWindows.Sheep)
        assertEquals(AnimalFarmPortraitWindow(1320, 45, 390, 480), AnimalFarmFamilyWindows.Cattle)
    }

    @Test
    fun familyKeyDoesNotInventAPortraitForSharedModules() {
        assertEquals(AnimalFarmFamily.GOAT, animalFarmFamilyFromKey("goat"))
        assertNull(animalFarmFamilyFromKey("tasks"))
        assertNull(animalFarmFamilyFromKey(null))
    }
}
