package com.farmos.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Reference footer when Record is supplied; legacy hosts retain their existing routes and labels. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimalFarmHomeBottomBar(onHome: () -> Unit, onAnimals: () -> Unit, onTasks: () -> Unit, onMore: () -> Unit,
    modifier: Modifier = Modifier, onRecord: (() -> Unit)? = null, worker: Boolean = false) {
    val items = if (onRecord == null) listOf(
        Triple("Home", "house", onHome), Triple("Animals", "paw-print", onAnimals),
        Triple("Tasks", "clipboard-list", onTasks), Triple("More", "layout-grid", onMore))
    else listOf(Triple("Home", "house", onHome), Triple("Work", "clipboard-list", onTasks),
        Triple("Record", "circle-plus", onRecord), Triple(if (worker) "Guides" else "Modules", if (worker) "book-open" else "layout-grid", onMore))
    Surface(modifier.fillMaxWidth(), color = AnimalFarmTheme.colors.surface, contentColor = AnimalFarmTheme.colors.ink) {
        Column {
            Box(Modifier.fillMaxWidth().height(1.dp).background(AnimalFarmTheme.colors.divider))
            BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding()) {
                val large = LocalDensity.current.fontScale >= 1.5f || maxWidth < 320.dp
                if (large) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp)) {
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items.forEachIndexed { index, (label, icon, action) ->
                                HomeNavigationItem(label, icon, action, index == 0, Modifier.widthIn(min = AnimalFarmTheme.minimumTouchDp.dp))
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { HomeThemeButton() }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(start = 9.dp, end = 9.dp, top = 11.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        items.forEachIndexed { index, (label, icon, action) -> HomeNavigationItem(label, icon, action, index == 0, Modifier.weight(1f)) }
                        HomeThemeButton()
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeNavigationItem(label: String, icon: String, action: () -> Unit, active: Boolean, modifier: Modifier) {
    Column(modifier.heightIn(min = AnimalFarmTheme.minimumTouchDp.dp).testTag("home-nav:$label")
        .clickable(role = Role.Tab, onClick = action).semantics { selected = active }.padding(horizontal = 3.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.background(if (active) AnimalFarmTheme.colors.lime else AnimalFarmTheme.colors.surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
            FarmHtmlIcon(icon, null, Modifier.size(AnimalFarmHomeMetrics.lineIconSize), if (active) AnimalFarmTheme.colors.primary else AnimalFarmTheme.colors.mutedInk)
        }
        Text(label, style = AnimalFarmHtmlType.navigation, textAlign = TextAlign.Start,
            color = if (active) AnimalFarmTheme.colors.primary else AnimalFarmTheme.colors.mutedInk)
    }
}
