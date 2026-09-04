package com.farmos.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

object FarmIllustratedPalette {
    val SkyTop = Color(0xFFDCE9ED)
    val SkyHorizon = Color(0xFFF4EFD9)
    val HillFar = Color(0xFF91A58A)
    val HillNear = Color(0xFF6E8B63)
    val Pasture = Color(0xFF5F7D52)
    val PastureLight = Color(0xFF88A66E)
    val Barn = Color(0xFF9A5743)
    val BarnDark = Color(0xFF6F3D31)
    val Silo = Color(0xFFB8B5A8)
    val Bark = Color(0xFF604736)
    val Leaf = Color(0xFF456642)
    val LeafLight = Color(0xFF68865B)
    val Cream = Color(0xFFF9F4E8)
    val CreamStrong = Color(0xFFFFFCF3)
    val Soil = Color(0xFF8A6B4E)
    val Ink = Color(0xFF233126)
}

/**
 * Canonical Farm OS illustrated scene primitive.
 * This is intentionally rendered from versioned vectors so the product has a portable
 * repository-owned visual baseline even when original chat/session assets are unavailable.
 * Rich approved raster/vector art may replace individual layers without changing this contract.
 */
@Composable
enum class FarmSpeciesVisual { GOAT, RABBIT, SHEEP, CATTLE, POULTRY }

@Composable
fun FarmPastoralBackdrop(
    modifier: Modifier = Modifier,
    heroSpecies: FarmSpeciesVisual? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.background(FarmIllustratedPalette.SkyTop)) {
        Canvas(Modifier.fillMaxSize()) { drawPastoralScene(heroSpecies) }
        content()
    }
}

private fun DrawScope.drawPastoralScene(heroSpecies: FarmSpeciesVisual?) {
    drawRect(
        brush = Brush.verticalGradient(
            listOf(FarmIllustratedPalette.SkyTop, FarmIllustratedPalette.SkyHorizon),
        ),
    )

    val w = size.width
    val h = size.height
    val horizon = h * 0.56f

    drawCircle(
        color = Color(0xFFFFE2A6).copy(alpha = 0.72f),
        radius = w * 0.09f,
        center = Offset(w * 0.78f, h * 0.16f),
    )

    fun hill(y: Float, color: Color, amplitude: Float, phase: Float) {
        val p = Path().apply {
            moveTo(0f, h)
            lineTo(0f, y)
            cubicTo(w * .18f, y - amplitude, w * .33f, y + amplitude * .35f, w * .52f, y - amplitude * .45f)
            cubicTo(w * .72f, y - amplitude, w * .88f, y + amplitude * phase, w, y - amplitude * .15f)
            lineTo(w, h)
            close()
        }
        drawPath(p, color)
    }

    hill(horizon - h * .08f, FarmIllustratedPalette.HillFar.copy(alpha = .78f), h * .08f, .45f)
    hill(horizon + h * .015f, FarmIllustratedPalette.HillNear, h * .065f, .35f)
    drawRect(FarmIllustratedPalette.Pasture, topLeft = Offset(0f, horizon), size = Size(w, h - horizon))

    val lightPasture = Path().apply {
        moveTo(0f, h * .74f)
        cubicTo(w * .2f, h * .68f, w * .42f, h * .79f, w * .64f, h * .70f)
        cubicTo(w * .80f, h * .64f, w * .92f, h * .72f, w, h * .67f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(lightPasture, FarmIllustratedPalette.PastureLight.copy(alpha = .55f))

    // Barn + silo, kept in the mid-ground so functional UI remains the focal plane.
    val barnX = w * .68f
    val barnY = horizon - h * .01f
    drawRect(FarmIllustratedPalette.Barn, Offset(barnX, barnY), Size(w * .17f, h * .10f))
    val roof = Path().apply {
        moveTo(barnX - w * .015f, barnY)
        lineTo(barnX + w * .085f, barnY - h * .055f)
        lineTo(barnX + w * .185f, barnY)
        close()
    }
    drawPath(roof, FarmIllustratedPalette.BarnDark)
    drawRect(FarmIllustratedPalette.BarnDark, Offset(barnX + w * .065f, barnY + h * .035f), Size(w * .04f, h * .065f))
    drawRect(FarmIllustratedPalette.Silo, Offset(w * .86f, barnY - h * .035f), Size(w * .055f, h * .135f))
    drawOval(FarmIllustratedPalette.Silo, Offset(w * .86f, barnY - h * .052f), Size(w * .055f, h * .035f))

    // Framing tree: a strong signature from the locked countryside composition.
    drawRect(FarmIllustratedPalette.Bark, Offset(w * .055f, h * .10f), Size(w * .055f, h * .58f))
    drawCircle(FarmIllustratedPalette.Leaf, w * .16f, Offset(w * .10f, h * .13f))
    drawCircle(FarmIllustratedPalette.LeafLight, w * .13f, Offset(w * .22f, h * .10f))
    drawCircle(FarmIllustratedPalette.Leaf, w * .12f, Offset(w * .02f, h * .05f))

    // Fence establishes agricultural scale and scene depth.
    val fenceY = h * .72f
    for (i in 0..5) {
        val x = w * (.45f + i * .10f)
        drawLine(Color(0xFFD9C6A4), Offset(x, fenceY), Offset(x, fenceY + h * .12f), strokeWidth = 4f)
    }
    drawLine(Color(0xFFD9C6A4), Offset(w * .42f, fenceY + h * .025f), Offset(w, fenceY + h * .06f), strokeWidth = 3f)
    drawLine(Color(0xFFD9C6A4), Offset(w * .42f, fenceY + h * .075f), Offset(w, fenceY + h * .105f), strokeWidth = 3f)

    // Soft livestock silhouettes communicate the mixed-farm world without becoming UI icons.
    drawGoat(Offset(w * .12f, h * .76f), w * .15f, Color(0xFFF1E6D3))
    drawRabbit(Offset(w * .30f, h * .87f), w * .075f, Color(0xFFD9C4AE))
    drawSheep(Offset(w * .82f, h * .82f), w * .12f)
    drawChicken(Offset(w * .93f, h * .91f), w * .055f)

    when (heroSpecies) {
        FarmSpeciesVisual.GOAT -> {
            drawGoat(Offset(w * .62f, h * .73f), w * .22f, Color(0xFFE7D7BF))
            drawGoat(Offset(w * .78f, h * .82f), w * .12f, Color(0xFFF2E7D5))
        }
        FarmSpeciesVisual.RABBIT -> {
            drawRabbit(Offset(w * .62f, h * .82f), w * .14f, Color(0xFFD8C1A8))
            drawRabbit(Offset(w * .78f, h * .86f), w * .10f, Color(0xFFE8DDD0))
        }
        FarmSpeciesVisual.SHEEP -> drawSheep(Offset(w * .66f, h * .77f), w * .20f)
        FarmSpeciesVisual.CATTLE -> drawCattle(Offset(w * .63f, h * .72f), w * .24f)
        FarmSpeciesVisual.POULTRY -> {
            drawChicken(Offset(w * .67f, h * .82f), w * .11f)
            drawChicken(Offset(w * .79f, h * .87f), w * .08f)
        }
        null -> Unit
    }
}

private fun DrawScope.drawGoat(origin: Offset, scale: Float, coat: Color) {
    drawOval(coat, origin, Size(scale, scale * .48f))
    val head = Offset(origin.x + scale * .84f, origin.y - scale * .03f)
    drawOval(coat, head, Size(scale * .34f, scale * .31f))
    drawLine(FarmIllustratedPalette.Ink, Offset(head.x + scale * .13f, head.y), Offset(head.x + scale * .06f, head.y - scale * .10f), 3f)
    drawLine(FarmIllustratedPalette.Ink, Offset(head.x + scale * .22f, head.y), Offset(head.x + scale * .27f, head.y - scale * .10f), 3f)
    drawLine(FarmIllustratedPalette.Ink, Offset(origin.x + scale * .22f, origin.y + scale * .40f), Offset(origin.x + scale * .20f, origin.y + scale * .72f), 4f)
    drawLine(FarmIllustratedPalette.Ink, Offset(origin.x + scale * .72f, origin.y + scale * .40f), Offset(origin.x + scale * .76f, origin.y + scale * .72f), 4f)
}

private fun DrawScope.drawRabbit(origin: Offset, scale: Float, coat: Color) {
    drawOval(coat, origin, Size(scale, scale * .58f))
    drawCircle(coat, scale * .24f, Offset(origin.x + scale * .78f, origin.y + scale * .15f))
    drawOval(coat, Offset(origin.x + scale * .70f, origin.y - scale * .35f), Size(scale * .14f, scale * .42f))
    drawOval(coat, Offset(origin.x + scale * .86f, origin.y - scale * .33f), Size(scale * .13f, scale * .40f))
}

private fun DrawScope.drawSheep(origin: Offset, scale: Float) {
    val wool = Color(0xFFF4F0E6)
    drawCircle(wool, scale * .30f, Offset(origin.x + scale * .28f, origin.y + scale * .18f))
    drawCircle(wool, scale * .29f, Offset(origin.x + scale * .52f, origin.y + scale * .17f))
    drawCircle(wool, scale * .27f, Offset(origin.x + scale * .72f, origin.y + scale * .20f))
    drawOval(Color(0xFF5B5147), Offset(origin.x + scale * .78f, origin.y + scale * .10f), Size(scale * .28f, scale * .23f))
    drawLine(Color(0xFF5B5147), Offset(origin.x + scale * .30f, origin.y + scale * .42f), Offset(origin.x + scale * .28f, origin.y + scale * .67f), 4f)
    drawLine(Color(0xFF5B5147), Offset(origin.x + scale * .68f, origin.y + scale * .42f), Offset(origin.x + scale * .70f, origin.y + scale * .67f), 4f)
}

private fun DrawScope.drawChicken(origin: Offset, scale: Float) {
    val body = Color(0xFFC77A42)
    drawOval(body, origin, Size(scale, scale * .62f))
    drawCircle(body, scale * .20f, Offset(origin.x + scale * .82f, origin.y + scale * .02f))
    val beak = Path().apply {
        moveTo(origin.x + scale, origin.y + scale * .02f)
        lineTo(origin.x + scale * 1.18f, origin.y + scale * .10f)
        lineTo(origin.x + scale, origin.y + scale * .15f)
        close()
    }
    drawPath(beak, Color(0xFFE0B05B))
    drawCircle(Color(0xFF9A3F32), scale * .075f, Offset(origin.x + scale * .82f, origin.y - scale * .17f))
}

private fun DrawScope.drawCattle(origin: Offset, scale: Float) {
    val coat = Color(0xFFEDE5D9)
    val dark = Color(0xFF3E3833)
    drawOval(coat, origin, Size(scale, scale * .50f))
    drawOval(dark, Offset(origin.x + scale * .72f, origin.y + scale * .03f), Size(scale * .33f, scale * .28f))
    drawCircle(dark, scale * .08f, Offset(origin.x + scale * .32f, origin.y + scale * .12f))
    drawCircle(dark, scale * .07f, Offset(origin.x + scale * .58f, origin.y + scale * .30f))
    drawLine(dark, Offset(origin.x + scale * .18f, origin.y + scale * .42f), Offset(origin.x + scale * .16f, origin.y + scale * .76f), 5f)
    drawLine(dark, Offset(origin.x + scale * .68f, origin.y + scale * .42f), Offset(origin.x + scale * .72f, origin.y + scale * .76f), 5f)
}

@Composable
fun FarmStorySurface(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = FarmIllustratedPalette.CreamStrong.copy(alpha = .96f),
        tonalElevation = 1.dp,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun FarmIllustratedSectionSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = FarmIllustratedPalette.Cream.copy(alpha = .94f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f)),
    ) {
        Column(Modifier.padding(FosDimens.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}
