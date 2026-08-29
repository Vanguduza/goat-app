package com.farmos.feature.goat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.farmos.core.design.FosDimens
import com.farmos.domain.goat.GoatSex

@Composable
fun GoatVerticalSliceScreen(
    state: GoatSliceUiState,
    onRegister: (tag: String, name: String?, sex: GoatSex) -> Unit,
    onRecordWeight: (weightKgText: String) -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tag by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf(GoatSex.FEMALE) }
    var weight by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(FosDimens.ScreenMargin),
        verticalArrangement = Arrangement.spacedBy(FosDimens.IntraCardGap),
    ) {
        Text("Goat field record", style = MaterialTheme.typography.titleLarge)
        Text(
            "Register a goat, record a weight offline, then synchronize the durable outbox.",
            style = MaterialTheme.typography.bodyMedium,
        )

        HorizontalDivider()
        Text("Identity", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = tag,
            onValueChange = { tag = it },
            label = { Text("Tag") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(FosDimens.Grid)) {
            TextButton(onClick = { sex = GoatSex.FEMALE }) {
                Text(if (sex == GoatSex.FEMALE) "Female selected" else "Female")
            }
            TextButton(onClick = { sex = GoatSex.MALE }) {
                Text(if (sex == GoatSex.MALE) "Male selected" else "Male")
            }
        }
        Button(
            enabled = !state.busy && tag.isNotBlank(),
            onClick = { onRegister(tag, name.trim().ifBlank { null }, sex) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Register goat")
        }

        Spacer(Modifier.height(FosDimens.Grid))
        HorizontalDivider()
        Text("Weight", style = MaterialTheme.typography.labelLarge)
        Text(state.goatSummary ?: "Register a goat before recording weight")
        OutlinedTextField(
            value = weight,
            onValueChange = { weight = it },
            label = { Text("Weight") },
            suffix = { Text("kg") },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.animalId != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Button(
            enabled = !state.busy && state.animalId != null && weight.isNotBlank(),
            onClick = { onRecordWeight(weight) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Record weight")
        }

        HorizontalDivider()
        Text("Sync", style = MaterialTheme.typography.labelLarge)
        Text(state.syncMessage)
        Button(
            enabled = !state.busy,
            onClick = onSyncNow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sync now")
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

data class GoatSliceUiState(
    val animalId: String? = null,
    val goatSummary: String? = null,
    val syncMessage: String = "No local changes yet",
    val busy: Boolean = false,
    val error: String? = null,
)
