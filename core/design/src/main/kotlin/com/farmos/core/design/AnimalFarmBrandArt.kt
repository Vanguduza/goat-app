@file:Suppress("ktlint:standard:function-naming")

package com.farmos.core.design

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

/** Exact versioned Animal Farm login lineup. Never regenerate or geometrically redraw. */
@Composable
fun FarmAnimalLineup(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.farm_animal_lineup),
        contentDescription = "Goat, rabbit, hen, sheep and cow",
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
