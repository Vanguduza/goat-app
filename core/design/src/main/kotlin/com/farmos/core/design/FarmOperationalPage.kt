package com.farmos.core.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
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
    content: @Composable ColumnScope.() -> Unit,
) {
    key(screenId) {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(FosDimens.ScreenMargin),
                verticalArrangement = Arrangement.spacedBy(FosDimens.SectionGap),
            ) {
                FarmStorySurface(Modifier.fillMaxWidth()) {
                    FarmOsWordmark(compact = true)
                    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        subtitle,
                        style = if (visualClass == FarmVisualClass.I4) MaterialTheme.typography.bodyMedium else FarmOsAccentMedium,
                        color =
                            if (visualClass ==
                                FarmVisualClass.I4
                            ) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                    )
                }
                content()
                onBack?.let { back ->
                    TextButton(onClick = back) { Text("Back to farm home") }
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
        description?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            rows.forEach { row -> Text(row, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
