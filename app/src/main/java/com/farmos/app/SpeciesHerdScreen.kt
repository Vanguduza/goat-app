package com.farmos.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmCanvas
import com.farmos.core.design.AnimalFarmModuleHeader
import com.farmos.core.design.FarmIllustratedSectionSurface
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalRows
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmSpeciesVisual
import com.farmos.core.design.FarmVisualClass
import com.farmos.core.design.FosDimens
import com.farmos.core.design.toAnimalFarmFamily

data class SpeciesAnimalRow(
    val animalId: String,
    val label: String,
    val active: Boolean,
)

private enum class SpeciesPage { DASHBOARD, HERD, PROFILE, REGISTER, WEIGHT, OPERATIONS, STATUS }

private data class SpeciesUiConfig(
    val name: String,
    val plural: String,
    val female: String,
    val male: String,
    val prefix: String,
    val visual: FarmSpeciesVisual,
    val operationsTitle: String,
)

@Composable
fun SpeciesHerdScreen(
    module: FarmModule,
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
    extra: @Composable (SpeciesAnimalRow?, onBack: () -> Unit) -> Unit = { _, _ -> },
) {
    require(module == FarmModule.SHEEP || module == FarmModule.CATTLE) {
        "SpeciesHerdScreen is reserved for individual-animal sheep/cattle UX"
    }
    val config =
        if (module == FarmModule.SHEEP) {
            SpeciesUiConfig(
                "Sheep",
                "sheep",
                femaleLabel,
                maleLabel,
                "FOS-SHEEP",
                FarmSpeciesVisual.SHEEP,
                "Breeding & wool operations",
            )
        } else {
            SpeciesUiConfig(
                "Cattle",
                "cattle",
                femaleLabel,
                maleLabel,
                "FOS-CATTLE",
                FarmSpeciesVisual.CATTLE,
                "Breeding, dairy & beef operations",
            )
        }
    var page by remember { mutableStateOf(SpeciesPage.DASHBOARD) }
    var selectedId by remember { mutableStateOf<String?>(rows.firstOrNull()?.animalId) }
    val selected = rows.firstOrNull { it.animalId == selectedId }
    val home = { page = SpeciesPage.DASHBOARD }
    when (page) {
        SpeciesPage.DASHBOARD -> {
            SpeciesDashboard(config, rows, selected, error, { page = it }, onBack)
        }

        SpeciesPage.HERD -> {
            SpeciesHerdList(config, rows, busy, error, {
                selectedId = it
                page = SpeciesPage.PROFILE
            }, home)
        }

        SpeciesPage.PROFILE -> {
            SpeciesProfile(config, selected, { page = it }, home)
        }

        SpeciesPage.REGISTER -> {
            SpeciesRegister(config, busy, error, onRegister, home)
        }

        SpeciesPage.WEIGHT -> {
            SpeciesWeight(config, selected, busy, error, onRecordWeight, home)
        }

        SpeciesPage.OPERATIONS -> {
            SpeciesOperations(selected, home, extra)
        }

        SpeciesPage.STATUS -> {
            SpeciesStatus(config, selected, busy, error, onSetStatus, home)
        }
    }
}

@Composable
private fun SpeciesDashboard(
    config: SpeciesUiConfig,
    rows: List<SpeciesAnimalRow>,
    selected: SpeciesAnimalRow?,
    error: String?,
    onOpen: (SpeciesPage) -> Unit,
    onBack: () -> Unit,
) {
    AnimalFarmCanvas {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(FosDimens.SectionGap),
        ) {
            AnimalFarmModuleHeader(
                title = config.name,
                subtitle = "Individual ${config.plural} records on this device",
                family = config.visual.toAnimalFarmFamily(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpeciesMetric("Total", rows.size, Modifier.weight(1f))
                SpeciesMetric("Active", rows.count { it.active }, Modifier.weight(1f))
            }
            SpeciesAction("${config.name} records", "Browse the current ${config.plural} list") { onOpen(SpeciesPage.HERD) }
            SpeciesAction("Register ${config.name.lowercase()}", "Create an individual animal identity") { onOpen(SpeciesPage.REGISTER) }
            if (selected != null) SpeciesAction("Open profile", selected.label) { onOpen(SpeciesPage.PROFILE) }
            SpeciesAction(
                config.operationsTitle,
                "Open this species module",
            ) { onOpen(SpeciesPage.OPERATIONS) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onBack) { Text("Farm home") }
        }
    }
}

@Composable
private fun SpeciesMetric(
    label: String,
    value: Int,
    modifier: Modifier,
) {
    FarmIllustratedSectionSurface(modifier) {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SpeciesAction(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    FarmIllustratedSectionSurface(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onClick) { Text("Open") }
    }
}

@Composable
private fun SpeciesHerdList(
    config: SpeciesUiConfig,
    rows: List<SpeciesAnimalRow>,
    busy: Boolean,
    error: String?,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
) {
    val id = "${config.prefix}-002"
    FarmOperationalPage(
        id,
        "${config.name} ${if (config.prefix.endsWith("SHEEP")) "mob / flock" else "herd"}",
        "Individual ${config.plural} records on this device.",
        onBack = onBack,
    ) {
        if (rows.isEmpty()) {
            FarmOperationalRows(emptyList(), "No ${config.plural} registered", "Register the first animal from the dashboard.")
        } else {
            FarmOperationalSection("Animals") {
                rows.forEach { row ->
                    TextButton(
                        onClick = { onSelect(row.animalId) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(row.label) }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun SpeciesProfile(
    config: SpeciesUiConfig,
    selected: SpeciesAnimalRow?,
    onOpen: (SpeciesPage) -> Unit,
    onBack: () -> Unit,
) {
    FarmOperationalPage(
        "${config.prefix}-003",
        "${config.name} profile",
        "Identity, current state and species-native actions.",
        FarmVisualClass.I2,
        onBack,
    ) {
        if (selected == null) {
            FarmOperationalRows(emptyList(), "No animal selected", "Choose an animal from the herd or flock first.")
        } else {
            FarmOperationalSection("Identity") {
                Text(selected.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (selected.active) "Active" else "Closed lifecycle record")
            }
            FarmOperationalSection("Actions") {
                Button(
                    onClick = { onOpen(SpeciesPage.WEIGHT) },
                    enabled = selected.active,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Record weight") }
                TextButton(onClick = { onOpen(SpeciesPage.OPERATIONS) }, enabled = selected.active) { Text(config.operationsTitle) }
                TextButton(onClick = { onOpen(SpeciesPage.STATUS) }, enabled = selected.active) { Text("Lifecycle status") }
            }
        }
    }
}

@Composable
private fun SpeciesRegister(
    config: SpeciesUiConfig,
    busy: Boolean,
    error: String?,
    onRegister: (String, String?, String, String?) -> Unit,
    onBack: () -> Unit,
) {
    var tag by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("FEMALE") }
    FarmOperationalPage(
        "${config.prefix}-004",
        "Register ${config.name.lowercase()}",
        "Create an individual animal record.",
        onBack = onBack,
    ) {
        FarmOperationalSection("Identity") {
            OutlinedTextField(
                tag,
                { tag = it },
                label = { Text("Tag") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
            )
            OutlinedTextField(name, {
                name = it
            }, label = { Text("Name (optional)") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { sex = "FEMALE" }, enabled = !busy) {
                    Text(
                        if (sex ==
                            "FEMALE"
                        ) {
                            "${config.female} · selected"
                        } else {
                            config.female
                        },
                    )
                }
                TextButton(onClick = { sex = "MALE" }, enabled = !busy) {
                    Text(
                        if (sex ==
                            "MALE"
                        ) {
                            "${config.male} · selected"
                        } else {
                            config.male
                        },
                    )
                }
            }
            Button(onClick = {
                onRegister(
                    tag,
                    name.trim().ifBlank {
                        null
                    },
                    sex,
                    null,
                )
            }, enabled = !busy && tag.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Register ${config.name.lowercase()}") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun SpeciesWeight(
    config: SpeciesUiConfig,
    selected: SpeciesAnimalRow?,
    busy: Boolean,
    error: String?,
    onRecord: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    var weight by remember { mutableStateOf("") }
    val id = "${config.prefix}-006"
    FarmOperationalPage(id, "Record weight", "Capture live weight in kilograms.", onBack = onBack) {
        if (selected == null) {
            FarmOperationalRows(emptyList(), "No animal selected", null)
        } else {
            FarmOperationalSection(selected.label) {
                OutlinedTextField(
                    weight,
                    { weight = it },
                    label = { Text("Weight") },
                    suffix = { Text("kg") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled =
                        !busy && selected.active,
                    singleLine = true,
                )
                Button(onClick = {
                    onRecord(selected.animalId, weight)
                }, enabled = !busy && selected.active && weight.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Record weight") }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun SpeciesOperations(
    selected: SpeciesAnimalRow?,
    onBack: () -> Unit,
    extra: @Composable (SpeciesAnimalRow?, onBack: () -> Unit) -> Unit,
) {
    extra(selected, onBack)
}

@Composable
private fun SpeciesStatus(
    config: SpeciesUiConfig,
    selected: SpeciesAnimalRow?,
    busy: Boolean,
    error: String?,
    onSetStatus: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    var pending by remember { mutableStateOf<String?>(null) }
    val id = if (config.prefix.endsWith("SHEEP")) "FOS-SHEEP-030" else "FOS-CATTLE-034"
    FarmOperationalPage(
        id,
        "Lifecycle status",
        "This removes the animal from the active herd or flock but preserves history.",
        FarmVisualClass.I4,
        onBack,
    ) {
        if (selected == null) {
            FarmOperationalRows(emptyList(), "No animal selected", null)
        } else {
            FarmOperationalSection(selected.label) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { pending = "sold" }, enabled = !busy) { Text("Sold") }
                    TextButton(onClick = { pending = "dead" }, enabled = !busy) { Text("Deceased") }
                    TextButton(onClick = { pending = "culled" }, enabled = !busy) { Text("Culled") }
                }
                pending?.let { status ->
                    Text(
                        "Confirm ${status.lowercase()}: the existing history remains attached to this record.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = {
                        onSetStatus(selected.animalId, status)
                        pending = null
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Confirm status change") }
                    TextButton(onClick = { pending = null }, enabled = !busy) { Text("Cancel") }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
