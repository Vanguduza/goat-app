package com.farmos.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Underlined stage views and count badges from .af-lanes; these do not create domain transitions. */
@Composable
fun AnimalFarmStageSelector(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier,
    counts: List<Int>? = null) {
    require(counts == null || counts.size == labels.size)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { i, label ->
            val selected = selectedIndex == i
            Column(Modifier.weight(1f).heightIn(min = maxOf(62.dp, AnimalFarmTheme.minimumTouchDp.dp))
                .testTag("home-stage:$i").clickable(role = Role.Tab, onClick = { onSelect(i) }).semantics { this.selected = selected },
                horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.weight(1f).padding(top = 9.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(label, style = AnimalFarmHtmlType.caption, textAlign = TextAlign.Center,
                        color = if (selected) AnimalFarmTheme.colors.primary else AnimalFarmTheme.colors.mutedInk)
                    counts?.get(i)?.let { count ->
                        Box(Modifier.widthIn(min = 23.dp).background(AnimalFarmTheme.colors.softSurface, RoundedCornerShape(7.dp)).padding(horizontal = 5.dp, vertical = 1.dp), contentAlignment = Alignment.Center) {
                            Text(count.toString(), style = AnimalFarmHtmlType.navigation)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(if (selected) 3.dp else 1.dp).background(if (selected) AnimalFarmTheme.colors.primary else AnimalFarmTheme.colors.divider))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimalFarmReviewTaskCard(category: String, dueLabel: String, subjectName: String, familyLabel: String?, title: String,
    fact: String, actionLabel: String, onAction: () -> Unit, family: AnimalFarmFamily?, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth().testTag("home-review-card"), shape = RoundedCornerShape(AnimalFarmHomeMetrics.focusRadius),
        color = AnimalFarmTheme.colors.lime, contentColor = AnimalFarmTheme.colors.onLime) {
        Column(Modifier.heightIn(min = 352.dp).padding(AnimalFarmHomeMetrics.focusPadding)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(category.uppercase(Locale.ROOT), style = AnimalFarmHtmlType.eyebrow)
                Surface(shape = RoundedCornerShape(20.dp), color = AnimalFarmTheme.colors.softSurface, contentColor = AnimalFarmTheme.colors.ink) {
                    Text(dueLabel, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = AnimalFarmHtmlType.navigation)
                }
            }
            if (family != null) AnimalFarmFamilyPortrait(family,
                Modifier.padding(top = 10.dp, bottom = 13.dp).align(Alignment.CenterHorizontally)
                    .size(AnimalFarmHomeMetrics.focusPortraitWidth, AnimalFarmHomeMetrics.focusPortraitHeight)
                    .clip(RoundedCornerShape(24.dp)).background(AnimalFarmTheme.colors.softSurface),
                contentDescription = "${animalFarmFamilyLabel(family)} family illustration", cornerRadius = 24.dp)
            val identity = listOfNotNull(subjectName.takeIf { it.isNotBlank() && it != title }, familyLabel).joinToString(" · ")
            if (identity.isNotBlank()) Text(identity, style = AnimalFarmHtmlType.subject, modifier = Modifier.padding(vertical = 8.dp))
            Text(title, Modifier.testTag("home-task-title").then(if (LocalDensity.current.fontScale < 1.5f) Modifier.widthIn(max = 250.dp) else Modifier.fillMaxWidth()).padding(top = 4.dp), style = AnimalFarmHtmlType.focusTitle)
            if (fact.isNotBlank()) Text(fact, Modifier.padding(top = 7.dp), style = AnimalFarmHtmlType.caption)
            FarmPrimaryButton(onAction, Modifier.fillMaxWidth().padding(top = 19.dp).testTag("home-task-action")) {
                Text(actionLabel, style = AnimalFarmHtmlType.action)
                Spacer(Modifier.width(9.dp))
                FarmHtmlIcon("arrow-right", null, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun AnimalFarmCarouselControls(positionLabel: String, onPrevious: () -> Unit, onNext: () -> Unit,
    previousEnabled: Boolean, nextEnabled: Boolean, modifier: Modifier = Modifier, position: Int = 0, count: Int = 0) {
    Row(modifier.fillMaxWidth().padding(top = 11.dp, bottom = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        AnimalFarmRoundIcon("arrow-left", "Previous task", onPrevious, enabled = previousEnabled)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(horizontal = 6.dp)) {
            val range = if (count <= 7) 0 until count else (position - 3).coerceIn(0, count - 7).let { it until it + 7 }
            range.forEach { index ->
                Box(Modifier.size(if (index == position) 27.dp else 8.dp, 8.dp).background(
                    if (index == position) AnimalFarmTheme.colors.primary else AnimalFarmTheme.colors.divider, CircleShape))
            }
        }
        Text(positionLabel, style = AnimalFarmHtmlType.caption, color = AnimalFarmTheme.colors.mutedInk,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        AnimalFarmRoundIcon("arrow-right", "Next task", onNext, enabled = nextEnabled)
    }
}

@Composable
fun AnimalFarmRoundIcon(icon: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, lime: Boolean = false) {
    IconButton(onClick, modifier.sizeIn(minWidth = AnimalFarmTheme.minimumTouchDp.dp, minHeight = AnimalFarmTheme.minimumTouchDp.dp), enabled = enabled) {
        Box(Modifier.size(44.dp).background(if (lime) AnimalFarmTheme.colors.lime else AnimalFarmTheme.colors.surface, CircleShape), contentAlignment = Alignment.Center) {
            FarmHtmlIcon(icon, description, Modifier.size(20.dp),
                tint = if (enabled) if (lime) AnimalFarmTheme.colors.onLime else AnimalFarmTheme.colors.ink else AnimalFarmTheme.colors.mutedInk.copy(alpha = 0.4f))
        }
    }
}

@Composable
fun AnimalFarmWorkDayBanner(day: String, weekday: String, context: String, onRecord: () -> Unit) {
    Surface(Modifier.fillMaxWidth().testTag("home-workday-banner"), shape = RoundedCornerShape(18.dp), color = AnimalFarmTheme.colors.primary, contentColor = AnimalFarmTheme.colors.onPrimary) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(day, style = AnimalFarmHtmlType.calendarDay)
            Column(Modifier.weight(1f)) { Text(weekday, style = AnimalFarmHtmlType.action); Text(context, style = AnimalFarmHtmlType.navigation) }
            AnimalFarmRoundIcon("plus", "Add a work update", onRecord, lime = true)
        }
    }
}

@Composable
fun AnimalFarmQuickCaptureTile(label: String, icon: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.heightIn(min = maxOf(80.dp, AnimalFarmTheme.minimumTouchDp.dp)).testTag("home-quick:$label")
        .clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(16.dp), color = AnimalFarmTheme.colors.surface, contentColor = AnimalFarmTheme.colors.ink) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            FarmHtmlIcon(icon, null, Modifier.size(22.dp))
            Text(label, style = AnimalFarmHtmlType.caption, textAlign = TextAlign.Center)
        }
    }
}
