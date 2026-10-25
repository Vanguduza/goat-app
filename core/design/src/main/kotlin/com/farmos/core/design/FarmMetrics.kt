package com.farmos.core.design

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip

import androidx.compose.ui.platform.testTag

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Metric formatting and meaning are supplied by the module; no display value becomes a domain fact. */
data class FarmMetricValue(val label: String, val value: String, val detail: String? = null,
    val onClick: (() -> Unit)? = null, val emphasized: Boolean = false)

/** P04/P10: compact HTML-derived tiles. Missing values remain unknown, exact quantities retain their units. */
@Composable
fun FarmMetricStrip(metrics: List<FarmMetricValue>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = when {
            LocalDensity.current.fontScale >= 1.5f -> 1
            maxWidth >= 600.dp -> 3
            else -> 2
        }
        Column(verticalArrangement = Arrangement.spacedBy(AnimalFarmHomeMetrics.tileGap)) {
            metrics.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(AnimalFarmHomeMetrics.tileGap)) {
                    row.forEach { metric ->
                        val click = metric.onClick
                        val colors = AnimalFarmTheme.colors
                        val shape = RoundedCornerShape(AnimalFarmHomeMetrics.tileRadius)
                        Surface(
                            modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = AnimalFarmHomeMetrics.metricMinimumHeight)
                                .clip(shape).testTag("farm-metric:" + metric.label)
                                .then(if (click == null) Modifier else Modifier.clickable(role = Role.Button, onClick = click)),
                            shape = shape,
                            color = if (metric.emphasized) colors.lime else colors.surface,
                            contentColor = if (metric.emphasized) colors.onLime else colors.ink,
                            border = if (AnimalFarmTheme.mode == AnimalFarmThemeMode.OUTDOOR) BorderStroke(FosDimens.Hairline, colors.divider) else null,
                        ) {
                            Column(Modifier.padding(FosDimens.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(metric.label, Modifier.weight(1f), style = AnimalFarmHtmlType.caption)
                                    if (click != null) FarmHtmlIcon("arrow-up-right", null, Modifier.size(AnimalFarmHomeMetrics.lineIconSize))
                                }
                                Text(metric.value, style = AnimalFarmHtmlType.metricValue)
                                metric.detail?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = AnimalFarmHtmlType.caption, color = if (metric.emphasized) colors.onLime else colors.mutedInk)
                                }
                            }
                        }
                    }
                    repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** displayValue retains the exact formatted value. value is only an optional plotting coordinate. */
data class FarmBarValue(val label: String, val value: Double?, val displayValue: String, val detail: String? = null, val tag: String? = null)

@Composable
fun FarmMetricBars(
    title: String,
    values: List<FarmBarValue>,
    unit: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    var table by rememberSaveable(title) { mutableStateOf(false) }
    val largest = values.mapNotNull { it.value?.takeIf(Double::isFinite)?.let(::abs) }.maxOrNull() ?: 0.0
    Column(modifier.fillMaxWidth()) {
        FarmOperationalSection(title) {
            FarmSectionTabs(listOf("Chart", "Table"), if (table) "Table" else "Chart", { table = it == "Table" })
            if (unit.isNotBlank()) Text(unit, style = MaterialTheme.typography.labelLarge, color = AnimalFarmTheme.colors.mutedInk)
            if (values.isEmpty()) Text("No values recorded", color = AnimalFarmTheme.colors.mutedInk)
            values.forEachIndexed { index, item ->
                if (table && index > 0) HorizontalDivider(color = AnimalFarmTheme.colors.divider)
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).then(item.tag?.let { Modifier.testTag(it) } ?: Modifier), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.label, style = MaterialTheme.typography.titleSmall)
                    Text(item.displayValue, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    val value = item.value?.takeIf(Double::isFinite)
                    if (!table && value != null) {
                        val fraction = if (largest > 0) (abs(value) / largest).toFloat().coerceIn(0f, 1f) else 0f
                        val shape = RoundedCornerShape(AnimalFarmHomeMetrics.actionRadius)
                        Box(Modifier.fillMaxWidth().height(8.dp).background(AnimalFarmTheme.colors.softSurface, shape).clearAndSetSemantics {}) {
                            Box(
                                Modifier.fillMaxWidth(fraction).height(8.dp)
                                    .background(if (value < 0) AnimalFarmTheme.colors.critical else AnimalFarmTheme.colors.primary, shape),
                            )
                        }
                    }
                    item.detail?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AnimalFarmTheme.colors.mutedInk) }
                }
            }
            caption?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AnimalFarmTheme.colors.mutedInk) }
        }
    }
}
