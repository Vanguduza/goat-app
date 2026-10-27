package com.farmos.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** The reference strip expresses actual due/open proportion, not invented task completion. */
@Composable
fun AnimalFarmDueWorkStrip(due: Int, open: Int, modifier: Modifier = Modifier) {
    if (open <= 0 || due !in 0..open) return
    val fill = due.toFloat() / open * 4f
    Row(modifier.fillMaxWidth().height(5.dp).testTag("home-due-work-strip")
        .semantics { contentDescription = "$due of $open open tasks due now" }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(4) { index ->
            val fraction = (fill - index).coerceIn(0f, 1f)
            Box(Modifier.weight(1f).fillMaxHeight().background(AnimalFarmTheme.colors.onLime.copy(alpha = 0.18f), RoundedCornerShape(4.dp))) {
                if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction).fillMaxHeight()
                    .background(AnimalFarmTheme.colors.onLime.copy(alpha = 0.75f), RoundedCornerShape(4.dp)))
            }
        }
    }
}
