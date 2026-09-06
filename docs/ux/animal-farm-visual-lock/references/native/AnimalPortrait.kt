package com.farmos.design.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.farmos.design.R
import com.farmos.design.theme.HomeTokens
import com.farmos.model.Species
import kotlin.math.roundToInt

private data class PortraitWindow(val x: Int, val y: Int, val width: Int, val height: Int)

private class FamilyPainter(private val image: ImageBitmap, private val window: PortraitWindow) : Painter() {
    override val intrinsicSize = Size(window.width.toFloat(), window.height.toFloat())
    override fun DrawScope.onDraw() {
        drawImage(image, srcOffset = IntOffset(window.x, window.y), srcSize = IntSize(window.width, window.height),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
    }
}

/** Render-only windows in the approved source sheet, never a personal identification image. */
@Composable
fun AnimalPortrait(species: Species, modifier: Modifier = Modifier, personalPhoto: ImageBitmap? = null,
    description: String? = null) {
    val image = ImageBitmap.imageResource(R.drawable.farm_family_portraits_v1)
    val window = remember(species) { when (species) {
        Species.Goat -> PortraitWindow(265, 195, 260, 370)
        Species.Rabbit -> PortraitWindow(480, 515, 155, 280)
        Species.Poultry -> PortraitWindow(722, 435, 205, 360)
        Species.Sheep -> PortraitWindow(1050, 230, 280, 350)
        Species.Cattle -> PortraitWindow(1320, 45, 390, 480)
    } }
    val target = modifier.clip(RoundedCornerShape(HomeTokens.actionRadius))
    if (personalPhoto != null) Image(personalPhoto, description, target, contentScale = ContentScale.Crop)
    else Image(remember(image, window) { FamilyPainter(image, window) }, description, target, contentScale = ContentScale.Fit)
}

@Composable
fun familyName(species: Species): String = stringResource(when (species) {
    Species.Goat -> R.string.family_goats
    Species.Rabbit -> R.string.family_rabbits
    Species.Sheep -> R.string.family_sheep
    Species.Cattle -> R.string.family_cattle
    Species.Poultry -> R.string.family_poultry
})
