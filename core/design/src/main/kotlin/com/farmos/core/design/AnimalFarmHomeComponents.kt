@file:Suppress("ktlint:standard:function-naming")
package com.farmos.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun AnimalFarmCanvas(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = AnimalFarmTheme.colors
    CompositionLocalProvider(LocalContentColor provides colors.ink) {
        Box(modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 840.dp).fillMaxSize(), content = content)
        }
    }
}

/** Same compact two-row header as the HTML. Farm name is live context, not the prototype's fixture. */
@Composable
fun AnimalFarmContextHeader(farmName: String?, dateLabel: String, title: String, modifier: Modifier = Modifier,
    eyebrow: String? = null, worker: Boolean = false) {
    val colors = AnimalFarmTheme.colors
    Column(modifier.fillMaxWidth().padding(start = animalFarmHomeHeaderInset(), end = animalFarmHomeHeaderInset(), top = 22.dp, bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                FarmHtmlIcon("sprout", null, Modifier.size(18.dp))
                Text(farmName?.takeIf { it.isNotBlank() } ?: "Animal Farm", style = AnimalFarmHtmlType.brand,
                    modifier = Modifier.semantics { contentDescription = "Animal Farm · ${farmName ?: "This farm"}" })
            }
            Text(dateLabel, style = AnimalFarmHtmlType.caption, color = colors.mutedInk, modifier = Modifier.padding(start = 12.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (!eyebrow.isNullOrBlank()) Text(eyebrow.uppercase(Locale.ROOT), style = AnimalFarmHtmlType.eyebrow, color = colors.mutedInk, modifier = Modifier.padding(bottom = 5.dp))
                Text(title, style = AnimalFarmHtmlType.pageTitle, modifier = Modifier.semantics { heading() })
            }
            Box(Modifier.size(42.dp).background(colors.softSurface, CircleShape), contentAlignment = Alignment.Center) {
                FarmHtmlIcon(if (worker) "sun" else "chart-no-axes-combined", if (worker) "Worker home" else "Management home", Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun AnimalFarmModuleHeader(title: String, subtitle: String, modifier: Modifier = Modifier, family: AnimalFarmFamily? = null) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Animal Farm", style = AnimalFarmHtmlType.caption, color = AnimalFarmTheme.colors.mutedInk)
            Text(title, Modifier.semantics { heading() }, style = AnimalFarmHtmlType.pageTitle)
            if (subtitle.isNotBlank()) Text(subtitle, style = AnimalFarmHtmlType.body, color = AnimalFarmTheme.colors.mutedInk)
        }
        if (family != null) AnimalFarmFamilyPortrait(family, Modifier.size(AnimalFarmHomeMetrics.compactPortrait))
    }
}

@Composable
fun AnimalFarmHeroCard(category: String, title: String, context: String, actionLabel: String, onAction: () -> Unit,
    modifier: Modifier = Modifier, family: AnimalFarmFamily? = null) {
    val colors = AnimalFarmTheme.colors
    Surface(modifier.fillMaxWidth().testTag("home-attention").clip(RoundedCornerShape(AnimalFarmHomeMetrics.heroRadius))
        .clickable(role = Role.Button, onClickLabel = actionLabel, onClick = onAction),
        shape = RoundedCornerShape(AnimalFarmHomeMetrics.heroRadius), color = colors.primary, contentColor = colors.onPrimary) {
        BoxWithConstraints(Modifier.heightIn(min = 171.dp).padding(AnimalFarmHomeMetrics.heroPadding)) {
            val stacked = maxWidth < 250.dp || LocalDensity.current.fontScale >= 1.75f
            val copy: @Composable (Modifier) -> Unit = { m ->
                Column(m.testTag("home-attention-copy")) {
                    Text(category.uppercase(Locale.ROOT), style = AnimalFarmHtmlType.eyebrow)
                    Text(title, Modifier.padding(top = 5.dp, bottom = 7.dp), style = AnimalFarmHtmlType.heroTitle)
                    Text(context, style = AnimalFarmHtmlType.caption)
                    Row(Modifier.padding(top = 15.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(actionLabel, style = AnimalFarmHtmlType.caption)
                        FarmHtmlIcon("arrow-up-right", null, Modifier.size(18.dp))
                    }
                }
            }
            val portrait: @Composable () -> Unit = {
                if (family != null) AnimalFarmFamilyPortrait(family,
                    Modifier.size(AnimalFarmHomeMetrics.heroPortraitWidth, AnimalFarmHomeMetrics.heroPortraitHeight)
                        .testTag("home-attention-portrait").clip(RoundedCornerShape(22.dp)).background(colors.softSurface),
                    contentDescription = "${animalFarmFamilyLabel(family)} family illustration", cornerRadius = 22.dp)
            }
            if (stacked) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { copy(Modifier.fillMaxWidth()); portrait() }
            else Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { copy(Modifier.weight(1f)); portrait() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimalFarmSummaryTile(title: String, value: String, detail: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    lime: Boolean = false, valueSuffix: String? = null) {
    val colors = AnimalFarmTheme.colors
    Surface(modifier.testTag("home-summary:$title").heightIn(min = AnimalFarmHomeMetrics.summaryMinimumHeight)
        .clip(RoundedCornerShape(AnimalFarmHomeMetrics.tileRadius)).clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(AnimalFarmHomeMetrics.tileRadius), color = if (lime) colors.lime else colors.surface,
        contentColor = if (lime) colors.onLime else colors.ink) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = AnimalFarmHtmlType.caption, modifier = Modifier.weight(1f))
                FarmHtmlIcon(if (title == "Resources") "package" else "arrow-up-right", null, Modifier.size(18.dp))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(value, style = AnimalFarmHtmlType.numeric)
                if (!valueSuffix.isNullOrBlank()) Text(valueSuffix, style = AnimalFarmHtmlType.subject, modifier = Modifier.align(Alignment.Bottom).padding(bottom = 4.dp))
            }
            Text(detail, style = AnimalFarmHtmlType.caption)
        }
    }
}

@Composable
fun AnimalFarmFamilyLauncher(family: AnimalFarmFamily, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.heightIn(min = AnimalFarmTheme.minimumTouchDp.dp).clip(RoundedCornerShape(AnimalFarmHomeMetrics.actionRadius))
        .clickable(role = Role.Button, onClick = onClick), color = AnimalFarmTheme.colors.surface, contentColor = AnimalFarmTheme.colors.ink,
        shape = RoundedCornerShape(AnimalFarmHomeMetrics.actionRadius)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            AnimalFarmFamilyPortrait(family, Modifier.size(72.dp))
            Text(animalFarmFamilyLabel(family), style = AnimalFarmHtmlType.subject, textAlign = TextAlign.Center)
            if (subtitle.isNotBlank()) Text(subtitle, style = AnimalFarmHtmlType.caption, color = AnimalFarmTheme.colors.mutedInk, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun AnimalFarmModuleTile(label: String, icon: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.heightIn(min = maxOf(AnimalFarmHomeMetrics.moduleMinimumHeight, AnimalFarmTheme.minimumTouchDp.dp))
        .testTag("home-module:$label").clip(RoundedCornerShape(AnimalFarmHomeMetrics.actionRadius)).clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(AnimalFarmHomeMetrics.actionRadius), color = AnimalFarmTheme.colors.surface, contentColor = AnimalFarmTheme.colors.ink) {
        Column(Modifier.padding(horizontal = 5.dp, vertical = 13.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            FarmHtmlIcon(icon, null, Modifier.size(22.dp))
            Text(label, style = AnimalFarmHtmlType.caption, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun AnimalFarmQuickAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.heightIn(min = AnimalFarmTheme.minimumTouchDp.dp).clip(RoundedCornerShape(AnimalFarmHomeMetrics.resourceRadius))
        .clickable(role = Role.Button, onClick = onClick), shape = RoundedCornerShape(AnimalFarmHomeMetrics.resourceRadius),
        color = AnimalFarmTheme.colors.surface, contentColor = AnimalFarmTheme.colors.ink) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, Modifier.weight(1f), style = AnimalFarmHtmlType.brand)
            FarmHtmlIcon("arrow-up-right", null, Modifier.size(18.dp))
        }
    }
}

@Composable
fun AnimalFarmEmptyState(message: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(AnimalFarmHomeMetrics.tileRadius),
        color = AnimalFarmTheme.colors.softSurface, contentColor = AnimalFarmTheme.colors.ink) {
        Text(message, Modifier.padding(16.dp), style = AnimalFarmHtmlType.body)
    }
}
