@file:Suppress("ktlint:standard:function-naming")

package com.farmos.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

object AnimalFarmHomeMetrics {
    val pageInset = 18.dp
    val headerInset = 22.dp
    val heroPadding = 20.dp
    val focusPadding = 21.dp
    val tileGap = 10.dp
    val moduleGap = 8.dp
    val heroRadius = 24.dp
    val focusRadius = 28.dp
    val tileRadius = 20.dp
    val actionRadius = 16.dp
    val compactPortrait = 88.dp
    val taskPortrait = 140.dp
}

@Composable
fun AnimalFarmCanvas(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AnimalFarmTheme.colors
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.background),
        content = content,
    )
}

@Composable
fun AnimalFarmContextHeader(
    farmName: String?,
    dateLabel: String,
    title: String,
    modifier: Modifier = Modifier,
) {
    val colors = AnimalFarmTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = AnimalFarmHomeMetrics.headerInset),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Animal Farm", color = colors.mutedInk, style = MaterialThemeLocal.label())
        Text(farmName ?: "This farm", color = colors.ink, style = MaterialThemeLocal.smallTitle())
        Text(dateLabel, color = colors.mutedInk, style = MaterialThemeLocal.label())
        Text(
            title,
            modifier = Modifier.semantics { heading() },
            color = colors.ink,
            style = MaterialThemeLocal.largeTitle(),
        )
    }
}

@Composable
fun AnimalFarmHeroCard(
    category: String,
    title: String,
    context: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    family: AnimalFarmFamily? = null,
) {
    val colors = AnimalFarmTheme.colors
    val minTouch = AnimalFarmTheme.minimumTouchDp.dp
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AnimalFarmHomeMetrics.heroRadius),
        color = colors.primary,
        contentColor = colors.onPrimary,
    ) {
        BoxWithConstraints(Modifier.padding(AnimalFarmHomeMetrics.heroPadding)) {
            val stacked = maxWidth < 360.dp
            val body: @Composable ColumnScope.() -> Unit = {
                Text(category, style = MaterialThemeLocal.label())
                Text(title, style = MaterialThemeLocal.largeTitle(), fontWeight = FontWeight.SemiBold)
                Text(context, style = MaterialThemeLocal.body())
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.heightIn(min = minTouch),
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.onPrimary),
                ) {
                    Text(actionLabel, fontWeight = FontWeight.SemiBold)
                }
            }
            if (stacked || family == null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = body)
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), content = body)
                    AnimalFarmFamilyPortrait(family, Modifier.size(AnimalFarmHomeMetrics.compactPortrait))
                }
            }
        }
    }
}

@Composable
fun AnimalFarmSummaryTile(
    title: String,
    value: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    lime: Boolean = false,
) {
    val colors = AnimalFarmTheme.colors
    val minTouch = AnimalFarmTheme.minimumTouchDp.dp
    Surface(
        modifier =
            modifier
                .heightIn(min = minTouch)
                .clip(RoundedCornerShape(AnimalFarmHomeMetrics.tileRadius))
                .clickable(role = Role.Button, onClick = onClick),
        color = if (lime) colors.lime else colors.surface,
        contentColor = if (lime) colors.onLime else colors.ink,
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialThemeLocal.label())
            Text(value, style = MaterialThemeLocal.numeric())
            Text(detail, style = MaterialThemeLocal.body())
        }
    }
}

@Composable
fun AnimalFarmFamilyLauncher(
    family: AnimalFarmFamily,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AnimalFarmTheme.colors
    val minTouch = AnimalFarmTheme.minimumTouchDp.dp
    Surface(
        modifier =
            modifier
                .heightIn(min = minTouch)
                .clip(RoundedCornerShape(AnimalFarmHomeMetrics.actionRadius))
                .clickable(role = Role.Button, onClick = onClick),
        color = colors.surface,
        contentColor = colors.ink,
    ) {
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimalFarmFamilyPortrait(family, Modifier.size(72.dp))
            Text(animalFarmFamilyLabel(family), style = MaterialThemeLocal.smallTitle(), textAlign = TextAlign.Center)
            Text(subtitle, style = MaterialThemeLocal.label(), color = colors.mutedInk, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun AnimalFarmStageSelector(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AnimalFarmTheme.colors
    val minTouch = AnimalFarmTheme.minimumTouchDp.dp
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Surface(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = minTouch)
                        .clip(RoundedCornerShape(AnimalFarmHomeMetrics.actionRadius))
                        .clickable(role = Role.Tab) { onSelect(index) },
                color = if (selected) colors.primary else colors.surface,
                contentColor = if (selected) colors.onPrimary else colors.ink,
            ) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialThemeLocal.label(), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun AnimalFarmReviewTaskCard(
    category: String,
    dueLabel: String,
    subjectName: String,
    familyLabel: String?,
    title: String,
    fact: String,
    actionLabel: String,
    onAction: () -> Unit,
    family: AnimalFarmFamily?,
    modifier: Modifier = Modifier,
) {
    val colors = AnimalFarmTheme.colors
    val minTouch = AnimalFarmTheme.minimumTouchDp.dp
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AnimalFarmHomeMetrics.focusRadius),
        color = colors.lime,
        contentColor = colors.onLime,
    ) {
        Column(
            Modifier.padding(AnimalFarmHomeMetrics.focusPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(category, style = MaterialThemeLocal.label())
                Text(dueLabel, style = MaterialThemeLocal.label())
            }
            if (family != null) {
                AnimalFarmFamilyPortrait(family, Modifier.size(AnimalFarmHomeMetrics.taskPortrait))
            }
            Text(subjectName, style = MaterialThemeLocal.smallTitle(), textAlign = TextAlign.Center)
            familyLabel?.let {
                Text(it, style = MaterialThemeLocal.label(), textAlign = TextAlign.Center)
            }
            Text(title, style = MaterialThemeLocal.largeTitle(), textAlign = TextAlign.Center)
            Text(fact, style = MaterialThemeLocal.body(), textAlign = TextAlign.Center)
            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth().heightIn(min = minTouch),
                shape = RoundedCornerShape(AnimalFarmHomeMetrics.actionRadius),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
            ) {
                Text(actionLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun AnimalFarmCarouselControls(
    positionLabel: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val minTouch = AnimalFarmTheme.minimumTouchDp.dp
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPrevious, enabled = previousEnabled, modifier = Modifier.heightIn(min = minTouch)) {
            Text("Previous")
        }
        Text(positionLabel, style = MaterialThemeLocal.label())
        TextButton(onClick = onNext, enabled = nextEnabled, modifier = Modifier.heightIn(min = minTouch)) {
            Text("Next")
        }
    }
}

@Composable
fun AnimalFarmQuickAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AnimalFarmTheme.colors
    val minTouch = AnimalFarmTheme.minimumTouchDp.dp
    Surface(
        modifier =
            modifier
                .heightIn(min = minTouch)
                .clip(RoundedCornerShape(AnimalFarmHomeMetrics.actionRadius))
                .clickable(role = Role.Button, onClick = onClick),
        color = colors.surface,
        contentColor = colors.ink,
    ) {
        Box(Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.CenterStart) {
            Text(label, style = MaterialThemeLocal.smallTitle())
        }
    }
}

@Composable
fun AnimalFarmHomeBottomBar(
    onHome: () -> Unit,
    onAnimals: () -> Unit,
    onTasks: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AnimalFarmTheme.colors
    Surface(modifier = modifier.fillMaxWidth(), color = colors.surface) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeNavItem("Home", onHome, Modifier.weight(1f))
            HomeNavItem("Animals", onAnimals, Modifier.weight(1f))
            HomeNavItem("Tasks", onTasks, Modifier.weight(1f))
            HomeNavItem("More", onMore, Modifier.weight(1f))
            HomeThemeButton()
        }
    }
}

@Composable
private fun HomeNavItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val minTouch = AnimalFarmTheme.minimumTouchDp.dp
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = minTouch)) {
        Text(label)
    }
}

@Composable
fun AnimalFarmEmptyState(
    message: String,
    modifier: Modifier = Modifier,
) {
    val colors = AnimalFarmTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AnimalFarmHomeMetrics.tileRadius),
        color = colors.softSurface,
        contentColor = colors.ink,
    ) {
        Text(message, Modifier.padding(16.dp), style = MaterialThemeLocal.body())
    }
}

private object MaterialThemeLocal

@Composable
private fun MaterialThemeLocal.label() = androidx.compose.material3.MaterialTheme.typography.labelMedium

@Composable
private fun MaterialThemeLocal.body() = androidx.compose.material3.MaterialTheme.typography.bodyMedium

@Composable
private fun MaterialThemeLocal.smallTitle() = androidx.compose.material3.MaterialTheme.typography.titleSmall

@Composable
private fun MaterialThemeLocal.largeTitle() = androidx.compose.material3.MaterialTheme.typography.titleLarge

@Composable
private fun MaterialThemeLocal.numeric() = androidx.compose.material3.MaterialTheme.typography.headlineSmall
