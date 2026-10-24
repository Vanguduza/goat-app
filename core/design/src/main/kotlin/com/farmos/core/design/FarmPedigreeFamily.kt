package com.farmos.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/** Only recorded parent edges are drawn. Unknown or conflicting parentage stays explicitly unknown. */
data class FarmPedigreeView(val childId: String, val childLabel: String, val sireLabel: String?, val damLabel: String?, val conflicting: Boolean = false)

/** P08: retained native family view using the same Animal Farm surfaces and typography. */
@Composable
fun FarmPedigreeFamily(family: FarmPedigreeView, modifier: Modifier = Modifier) {
    val colors = AnimalFarmTheme.colors
    val sire = family.sireLabel.takeUnless { family.conflicting }
    val dam = family.damLabel.takeUnless { family.conflicting }
    Column(modifier.semantics { stateDescription = if (family.conflicting) "Conflicting parentage; parents unknown" else "Recorded family" }) {
    FarmOperationalSection(family.childLabel) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 340.dp || LocalDensity.current.fontScale >= 1.5f
            Column(verticalArrangement = Arrangement.spacedBy(FosDimens.Grid)) {
                if (compact) {
                    PedigreeParent("Sire", sire, Modifier.fillMaxWidth())
                    PedigreeParent("Dam", dam, Modifier.fillMaxWidth())
                    Text(if (sire != null || dam != null) "Parents of" else "Parentage not recorded for", style = MaterialTheme.typography.labelMedium, color = colors.mutedInk)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(FosDimens.IntraCardGap)) {
                        PedigreeParent("Sire", sire, Modifier.weight(1f))
                        PedigreeParent("Dam", dam, Modifier.weight(1f))
                    }
                    if (sire != null || dam != null) Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                        val center = Offset(size.width / 2, size.height)
                        if (sire != null) drawLine(colors.primary, Offset(size.width / 4, 0f), center, 2.dp.toPx())
                        if (dam != null) drawLine(colors.primary, Offset(size.width * 3 / 4, 0f), center, 2.dp.toPx())
                    }
                }
                Surface(Modifier.fillMaxWidth(), color = colors.primary, contentColor = colors.onPrimary, shape = RoundedCornerShape(AnimalFarmHomeMetrics.tileRadius)) {
                    Column(Modifier.padding(FosDimens.CardPadding), verticalArrangement = Arrangement.spacedBy(FosDimens.Grid)) {
                        Text("Animal", style = MaterialTheme.typography.labelLarge)
                        Text(family.childLabel, style = MaterialTheme.typography.titleMedium)
                    }
                }
                if (family.conflicting) Text("Conflicting parentage is left unknown. Review the recorded links.", color = colors.critical)
            }
        }
    }
    }
}

@Composable
private fun PedigreeParent(relation: String, label: String?, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(AnimalFarmHomeMetrics.tileRadius), color = AnimalFarmTheme.colors.softSurface, contentColor = AnimalFarmTheme.colors.ink, border = BorderStroke(FosDimens.Hairline, AnimalFarmTheme.colors.divider)) {
        Column(Modifier.padding(FosDimens.CardPadding), verticalArrangement = Arrangement.spacedBy(FosDimens.Grid)) {
            Text(relation, style = MaterialTheme.typography.labelLarge)
            Text(label ?: "Not recorded", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
