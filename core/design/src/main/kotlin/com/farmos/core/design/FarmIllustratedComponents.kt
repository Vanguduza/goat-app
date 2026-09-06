@file:Suppress("ktlint:standard:function-naming")

package com.farmos.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Species key used by older call sites. Maps to approved family portraits;
 * it is not permission to draw a geometric animal.
 */
enum class FarmSpeciesVisual { GOAT, RABBIT, SHEEP, CATTLE, POULTRY }

fun FarmSpeciesVisual.toAnimalFarmFamily(): AnimalFarmFamily =
    when (this) {
        FarmSpeciesVisual.GOAT -> AnimalFarmFamily.GOAT
        FarmSpeciesVisual.RABBIT -> AnimalFarmFamily.RABBIT
        FarmSpeciesVisual.SHEEP -> AnimalFarmFamily.SHEEP
        FarmSpeciesVisual.CATTLE -> AnimalFarmFamily.CATTLE
        FarmSpeciesVisual.POULTRY -> AnimalFarmFamily.POULTRY
    }

@Composable
fun FarmOsWordmark(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = AnimalFarmTheme.colors
    Text(
        text = "Animal Farm",
        modifier = modifier,
        style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold,
        color = colors.ink,
    )
}

@Composable
fun FarmPastoralBackdrop(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") heroSpecies: FarmSpeciesVisual? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(AnimalFarmTheme.colors.background),
        content = content,
    )
}

@Composable
fun FarmStorySurface(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AnimalFarmTheme.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AnimalFarmHomeMetrics.heroRadius),
        color = colors.surface,
        contentColor = colors.ink,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun FarmIllustratedSectionSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AnimalFarmTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AnimalFarmHomeMetrics.tileRadius),
        color = colors.surface,
        contentColor = colors.ink,
        border = BorderStroke(1.dp, colors.divider),
    ) {
        Column(Modifier.padding(FosDimens.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}
