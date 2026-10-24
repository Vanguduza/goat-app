package com.farmos.core.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.farmos.core.design.FarmActionButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class FarmVisualClass { I1, I2, I3, I4 }

/**
 * Shared content-first Farm OS page shell for I2-I4 operational surfaces.
 * The screen id is deliberately not rendered; it keys the composition and preserves
 * traceability to the canonical screen registry without leaking audit language to users.
 */
@Composable
fun FarmOperationalPage(
    screenId: String,
    title: String,
    subtitle: String,
    visualClass: FarmVisualClass = FarmVisualClass.I3,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    backLabel: String = "Farm home",
    content: @Composable ColumnScope.() -> Unit,
) {
    key(screenId) {
        FarmBackHandler(onBack)
        Surface(modifier = modifier.fillMaxSize().testTag("farm-screen:$screenId"), color = AnimalFarmTheme.colors.background) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(FosDimens.ScreenMargin),
                verticalArrangement = Arrangement.spacedBy(FosDimens.SectionGap),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    onBack?.let { back -> FarmBackButton(backLabel, back, Modifier.weight(1f, fill = false)) }
                    if (LocalDensity.current.fontScale < 1.5f) Text("Animal Farm", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.labelMedium, color = AnimalFarmTheme.colors.mutedInk)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    if (subtitle.isNotBlank()) Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (visualClass == FarmVisualClass.I4) {
                                AnimalFarmTheme.colors.critical
                            } else {
                                AnimalFarmTheme.colors.mutedInk
                            },
                    )
                }
                content()
            }
            }
        }
    }
}

@Composable
fun FarmOperationalSection(
    title: String,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    FarmIllustratedSectionSurface {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        description?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = AnimalFarmTheme.colors.mutedInk)
        }
        Column(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(FosDimens.IntraCardGap),
            content = content,
        )
    }
}

@Composable
fun FarmOperationalRows(
    rows: List<String>,
    emptyTitle: String,
    emptyHint: String? = null,
) {
    if (rows.isEmpty()) {
        FarmOperationalSection(emptyTitle, emptyHint) {}
    } else {
        FarmOperationalSection("Current records") {
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = AnimalFarmTheme.colors.divider)
                FarmRecordRow(row)
            }
        }
    }
}
