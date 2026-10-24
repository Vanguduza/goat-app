package com.farmos.core.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** P05/P09 record presentation; supplied text remains intact and is never parsed into business state. */
@Composable
fun FarmRecordRow(text: String, modifier: Modifier = Modifier) {
    val lines = text.split('\n')
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(lines.first(), style = MaterialTheme.typography.bodyLarge)
        if (lines.size > 1) Text(lines.drop(1).joinToString("\n"), style = MaterialTheme.typography.bodyMedium, color = AnimalFarmTheme.colors.mutedInk)
    }
}
