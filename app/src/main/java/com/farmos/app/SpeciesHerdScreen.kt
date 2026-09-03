package com.farmos.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.farmos.core.design.FosDimens

data class SpeciesAnimalRow(
    val animalId: String,
    val label: String,
    val active: Boolean,
)

@Composable
fun SpeciesHerdScreen(
    title: String,
    femaleLabel: String,
    maleLabel: String,
    kindRequired: Boolean,
    rows: List<SpeciesAnimalRow>,
    busy: Boolean,
    error: String?,
    onRegister: (tag: String, name: String?, sex: String, kind: String?) -> Unit,
    onRecordWeight: (animalId: String, weightKgText: String) -> Unit,
    onSetStatus: (animalId: String, status: String) -> Unit,
    onBack: () -> Unit,
    extra: @Composable (SpeciesAnimalRow?) -> Unit = {},
) {
    var tag by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("FEMALE") }
    var kind by remember { mutableStateOf("chicken") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var weight by remember { mutableStateOf("") }
    val selected = rows.firstOrNull { it.animalId == selectedId }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FosDimens.ScreenMargin),
        verticalArrangement = Arrangement.spacedBy(FosDimens.IntraCardGap),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (rows.isEmpty()) {
            Text("No animals on this device. Register one to start the record.")
        } else {
            rows.forEach { row ->
                TextButton(
                    onClick = { selectedId = row.animalId },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (row.animalId == selectedId) "${row.label} · open" else row.label)
                }
            }
        }
        HorizontalDivider()
        OutlinedTextField(tag, { tag = it }, label = { Text("Tag") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        if (kindRequired) {
            Text("Kind must be chicken, duck, muscovy, guinea_fowl, turkey, goose, quail, pigeon, or farm_defined.")
            OutlinedTextField(kind, { kind = it }, label = { Text("Poultry kind") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        }
        Row {
            TextButton(onClick = { sex = "FEMALE" }, enabled = !busy) {
                Text(if (sex == "FEMALE") "$femaleLabel selected" else femaleLabel)
            }
            TextButton(onClick = { sex = "MALE" }, enabled = !busy) {
                Text(if (sex == "MALE") "$maleLabel selected" else maleLabel)
            }
        }
        Button(
            onClick = { onRegister(tag, name.trim().ifBlank { null }, sex, kind.takeIf { kindRequired }) },
            enabled = !busy && tag.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Register") }

        HorizontalDivider()
        Text("Weight and lifecycle", style = MaterialTheme.typography.labelLarge)
        if (selected == null) {
            Text("Select a record before recording weight or a lifecycle change.")
        } else {
            Text(selected.label)
            OutlinedTextField(
                weight,
                { weight = it },
                label = { Text("Weight") },
                suffix = { Text("kg") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && selected.active,
                singleLine = true,
            )
            Button(
                onClick = { onRecordWeight(selected.animalId, weight) },
                enabled = !busy && selected.active && weight.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Record weight") }
            if (selected.active) {
                Row(horizontalArrangement = Arrangement.spacedBy(FosDimens.Grid)) {
                    TextButton(onClick = { onSetStatus(selected.animalId, "sold") }, enabled = !busy) { Text("Mark sold") }
                    TextButton(onClick = { onSetStatus(selected.animalId, "dead") }, enabled = !busy) { Text("Mark deceased") }
                    TextButton(onClick = { onSetStatus(selected.animalId, "culled") }, enabled = !busy) { Text("Mark culled") }
                }
            }
        }
        extra(selected)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = onBack, enabled = !busy) { Text("Back to farm home") }
    }
}
