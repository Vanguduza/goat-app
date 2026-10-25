package com.farmos.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shared Animal Farm action and selection controls (P05/P07/P08/P13/P14).
 * Color, shape and touch geometry derive from the locked design tokens.
 * These emit intents; they never commit business operations.
 */
@Composable
fun FarmActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val colors = AnimalFarmTheme.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.sizeIn(minWidth = AnimalFarmTheme.minimumTouchDp.dp, minHeight = AnimalFarmTheme.minimumTouchDp.dp),
        enabled = enabled,
        shape = RoundedCornerShape(AnimalFarmHomeMetrics.secondaryActionRadius),
        border = BorderStroke(FosDimens.Hairline, if (enabled) colors.divider else colors.divider.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.surface,
            contentColor = colors.ink,
            disabledContainerColor = colors.softSurface,
            disabledContentColor = colors.mutedInk,
        ),
        contentPadding = contentPadding,
        content = { androidx.compose.material3.ProvideTextStyle(AnimalFarmHtmlType.action) { content() } },
    )
}

@Composable
fun FarmPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val colors = AnimalFarmTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier.sizeIn(minWidth = AnimalFarmTheme.minimumTouchDp.dp, minHeight = AnimalFarmTheme.minimumTouchDp.dp),
        enabled = enabled,
        shape = RoundedCornerShape(AnimalFarmHomeMetrics.actionRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            disabledContainerColor = colors.softSurface,
            disabledContentColor = colors.mutedInk,
        ),
        contentPadding = contentPadding,
        content = { androidx.compose.material3.ProvideTextStyle(AnimalFarmHtmlType.action) { content() } },
    )
}

@Composable
fun FarmFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AnimalFarmTheme.colors
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier.sizeIn(minWidth = AnimalFarmTheme.minimumTouchDp.dp, minHeight = AnimalFarmTheme.minimumTouchDp.dp),
        shape = RoundedCornerShape(AnimalFarmHomeMetrics.actionRadius),
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colors.surface,
            labelColor = colors.ink,
            selectedContainerColor = colors.lime,
            selectedLabelColor = colors.onLime,
            selectedLeadingIconColor = colors.onLime,
        ),
    )
}

@Composable
fun FarmBackButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FarmActionButton(onClick, modifier) {
        FarmHtmlIcon("arrow-left", null, Modifier.size(20.dp))
        Text(label, Modifier.padding(start = FosDimens.Grid))
    }
}
