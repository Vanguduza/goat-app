package com.farmos.core.design

import androidx.compose.foundation.Image
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

enum class AnimalFarmFamily {
    GOAT,
    RABBIT,
    SHEEP,
    CATTLE,
    POULTRY,
}

data class AnimalFarmPortraitWindow(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

/** Exact crop windows from the locked family sheet. Do not invent new coordinates. */
object AnimalFarmFamilyWindows {
    val Goat = AnimalFarmPortraitWindow(265, 195, 260, 370)
    val Rabbit = AnimalFarmPortraitWindow(480, 515, 155, 280)
    val Poultry = AnimalFarmPortraitWindow(722, 435, 205, 360)
    val Sheep = AnimalFarmPortraitWindow(1050, 230, 280, 350)
    val Cattle = AnimalFarmPortraitWindow(1320, 45, 390, 480)

    fun forFamily(family: AnimalFarmFamily): AnimalFarmPortraitWindow =
        when (family) {
            AnimalFarmFamily.GOAT -> Goat
            AnimalFarmFamily.RABBIT -> Rabbit
            AnimalFarmFamily.POULTRY -> Poultry
            AnimalFarmFamily.SHEEP -> Sheep
            AnimalFarmFamily.CATTLE -> Cattle
        }
}

fun animalFarmFamilyFromKey(key: String?): AnimalFarmFamily? =
    when (key?.lowercase()) {
        "goat", "goats" -> AnimalFarmFamily.GOAT
        "rabbit", "rabbits" -> AnimalFarmFamily.RABBIT
        "sheep" -> AnimalFarmFamily.SHEEP
        "cattle", "cow", "cows" -> AnimalFarmFamily.CATTLE
        "poultry", "chicken", "hen" -> AnimalFarmFamily.POULTRY
        else -> null
    }

fun animalFarmFamilyLabel(family: AnimalFarmFamily): String =
    when (family) {
        AnimalFarmFamily.GOAT -> "Goats"
        AnimalFarmFamily.RABBIT -> "Rabbits"
        AnimalFarmFamily.SHEEP -> "Sheep"
        AnimalFarmFamily.CATTLE -> "Cattle"
        AnimalFarmFamily.POULTRY -> "Poultry"
    }

private class FamilySheetPainter(
    private val image: ImageBitmap,
    private val window: AnimalFarmPortraitWindow,
) : Painter() {
    override val intrinsicSize = Size(window.width.toFloat(), window.height.toFloat())

    override fun DrawScope.onDraw() {
        drawImage(
            image = image,
            srcOffset = IntOffset(window.x, window.y),
            srcSize = IntSize(window.width, window.height),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        )
    }
}

/** Approved family portrait. Never a personal animal photograph. */
@Composable
@Suppress("ktlint:standard:function-naming")
fun AnimalFarmFamilyPortrait(
    family: AnimalFarmFamily,
    modifier: Modifier = Modifier,
    contentDescription: String = animalFarmFamilyLabel(family),
) {
    val image = ImageBitmap.imageResource(R.drawable.farm_family_portraits_v1)
    val window = AnimalFarmFamilyWindows.forFamily(family)
    val painter = remember(image, window) { FamilySheetPainter(image, window) }
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier.clip(RoundedCornerShape(AnimalFarmHomeMetrics.actionRadius)),
        contentScale = ContentScale.Fit,
    )
}
