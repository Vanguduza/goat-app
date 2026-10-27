package com.farmos.app

import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.foundation.layout.imePadding

import com.farmos.core.design.FarmSectionTabs

import com.farmos.core.design.FarmMetricValue

import com.farmos.core.design.FarmMetricStrip

import com.farmos.core.design.FarmBackButton

import com.farmos.core.design.FarmActionButton

import androidx.compose.runtime.saveable.rememberSaveableStateHolder

import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.farmos.core.design.FarmPrimaryButton as Button
import androidx.compose.material3.MaterialTheme
import com.farmos.core.design.FarmOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Text
import com.farmos.core.design.FarmActionButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmCanvas
import com.farmos.core.design.AnimalFarmModuleHeader
import com.farmos.core.design.FarmBackHandler
import com.farmos.core.design.FarmIllustratedSectionSurface
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalRows
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmSpeciesVisual
import com.farmos.core.design.FarmVisualClass
import com.farmos.core.design.FosDimens
import com.farmos.core.design.toAnimalFarmFamily

/** Exhaustive herd counts: [total] is every animal the herd list can show, [active] the active ones. */
data class SpeciesHerdCounts(val total: Int, val active: Int)

data class SpeciesAnimalRow(
    val animalId: String,
    val label: String,
    val active: Boolean,
)

private enum class SpeciesPage { DASHBOARD, HERD, PROFILE, REGISTER, WEIGHT, OPERATIONS, STATUS }

private data class SpeciesScreenIds(
    val herd: String,
    val profile: String,
    val register: String,
    val weight: String,
    val status: String,
)

private data class SpeciesUiConfig(
    val name: String,
    val plural: String,
    val female: String,
    val male: String,
    val prefix: String,
    val visual: FarmSpeciesVisual,
    val operationsTitle: String,
    val screenIds: SpeciesScreenIds,
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
    counts: SpeciesHerdCounts? = null,
    exitContent: (@Composable (animal: SpeciesAnimalRow, screenId: String, onBack: () -> Unit) -> Unit)? = null,
    attachmentContent: (@Composable (animal: SpeciesAnimalRow) -> Unit)? = null,
    /** An exact profile entry; the host loads this subject independently of the capped herd list. */
    entryAnimalId: String? = null,
    loading: Boolean = false,
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
                SpeciesScreenIds(
                    herd = "FOS-SHEEP-002",
                    profile = "FOS-SHEEP-003",
                    register = "FOS-SHEEP-004",
                    weight = "FOS-SHEEP-006",
                    status = "FOS-SHEEP-030",
                ),
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
                SpeciesScreenIds(
                    herd = "FOS-CATTLE-002",
                    profile = "FOS-CATTLE-003",
                    register = "FOS-CATTLE-004",
                    weight = "FOS-CATTLE-006",
                    status = "FOS-CATTLE-034",
                ),
            )
        }
    require(entryAnimalId == null || entryAnimalId.isNotBlank()) { "An animal profile needs an id" }
    var page by rememberSaveable(module.name, entryAnimalId) { mutableStateOf(if (entryAnimalId == null) SpeciesPage.DASHBOARD else SpeciesPage.PROFILE) }
    var selectedId by rememberSaveable(module.name, entryAnimalId) { mutableStateOf(entryAnimalId ?: rows.firstOrNull()?.animalId) }
    val selected = rows.firstOrNull { it.animalId == selectedId }
    val home = { page = SpeciesPage.DASHBOARD }
    val profileBack: () -> Unit = if (entryAnimalId != null) onBack else home
    val detailBack = { page = if (entryAnimalId != null) SpeciesPage.PROFILE else SpeciesPage.DASHBOARD }
    val pageStateHolder = rememberSaveableStateHolder()
    pageStateHolder.SaveableStateProvider(page.name) {
    when (page) {
        SpeciesPage.DASHBOARD -> {
            SpeciesDashboard(config, rows, counts, selected, error, { page = it }, onBack)
        }

        SpeciesPage.HERD -> {
            SpeciesHerdList(config, rows, counts?.total, busy, error, {
                selectedId = it
                page = SpeciesPage.PROFILE
            }, home)
        }

        SpeciesPage.PROFILE -> {
            SpeciesProfile(config, selected, { page = it }, profileBack, exitsReversible = exitContent != null, attachmentContent = attachmentContent,
                loading = loading, error = error, exactEntry = entryAnimalId != null)
        }

        SpeciesPage.REGISTER -> {
            SpeciesRegister(config, busy, error, onRegister, home)
        }

        SpeciesPage.WEIGHT -> {
            SpeciesWeight(config, selected, busy, error, onRecordWeight, detailBack, backLabel = if (entryAnimalId != null) "Profile" else "Farm home")
        }

        SpeciesPage.OPERATIONS -> {
            SpeciesOperations(selected, detailBack, extra)
        }

        SpeciesPage.STATUS -> {
            // Owner decision D-022: exits are recorded as reversible exit events when the host provides them.
            val animal = selected
            if (exitContent != null && animal != null) {
                exitContent(animal, config.screenIds.status, detailBack)
            } else {
                SpeciesStatus(config, selected, busy, error, onSetStatus, detailBack)
            }
        }
    }
    }
}

@Composable
private fun SpeciesDashboard(
    config: SpeciesUiConfig,
    rows: List<SpeciesAnimalRow>,
    counts: SpeciesHerdCounts?,
    selected: SpeciesAnimalRow?,
    error: String?,
    onOpen: (SpeciesPage) -> Unit,
    onBack: () -> Unit,
) {
    FarmBackHandler(onBack)
    AnimalFarmCanvas(Modifier.testTag("farm-screen:${config.prefix}-001")) {
        Column(
            Modifier.weight(1f).imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = com.farmos.core.design.animalFarmHomePageInset(), vertical = com.farmos.core.design.AnimalFarmHomeMetrics.headerInset),
            verticalArrangement = Arrangement.spacedBy(com.farmos.core.design.AnimalFarmHomeMetrics.contentGap),
        ) {
            FarmBackButton("Farm home", onBack)
            AnimalFarmModuleHeader(
                title = config.name,
                subtitle = "",
                family = config.visual.toAnimalFarmFamily(),
            )
            FarmMetricStrip(listOf(
                FarmMetricValue("Total", counts?.total?.toString() ?: "—", onClick = { onOpen(SpeciesPage.HERD) }),
                FarmMetricValue("Active", counts?.active?.toString() ?: "—", onClick = { onOpen(SpeciesPage.HERD) }),
            ))
            SpeciesAction("${config.name} records", "Browse the current ${config.plural} list") { onOpen(SpeciesPage.HERD) }
            SpeciesAction("Register ${config.name.lowercase()}", "Create an individual animal identity") { onOpen(SpeciesPage.REGISTER) }
            if (selected != null) SpeciesAction("Open profile", selected.label) { onOpen(SpeciesPage.PROFILE) }
            SpeciesAction(
                config.operationsTitle,
                "Open this species module",
            ) { onOpen(SpeciesPage.OPERATIONS) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
private fun SpeciesAction(title: String, subtitle: String, onClick: () -> Unit) {
    com.farmos.core.design.FarmResourceAction(
        title = title, detail = subtitle.takeIf { title == "Open profile" }, onClick = onClick,
    )
}

@Composable
private fun SpeciesHerdList(
    config: SpeciesUiConfig,
    rows: List<SpeciesAnimalRow>,
    listedTotal: Int?,
    busy: Boolean,
    error: String?,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf("All") }
    val visibleRows = rows.filter { filter == "All" || (filter == "Active") == it.active }
    val id = config.screenIds.herd
    FarmOperationalPage(
        id,
        "${config.name} ${if (config.prefix.endsWith("SHEEP")) "mob / flock" else "herd"}",
        "",
        onBack = onBack,
    ) {
        FarmSectionTabs(listOf("All", "Active", "Closed"), filter, { filter = it })
        listedTotal?.takeIf { it > rows.size }?.let { total ->
            Text("Showing the first ${rows.size} of $total ${config.plural} by tag.", Modifier.testTag("species-herd-bounded"))
        }
        if (visibleRows.isEmpty()) {
            FarmOperationalRows(emptyList(), if (rows.isEmpty()) "No ${config.plural} registered" else "No ${filter.lowercase()} records shown", if (rows.isEmpty()) "Register the first animal from the dashboard." else null)
        } else {
            FarmOperationalSection("Animals") {
                visibleRows.forEach { row ->
                    TextButton(
                        onClick = { onSelect(row.animalId) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FosDimens.Grid)) {
                            Text(row.label, fontWeight = FontWeight.SemiBold)
                            Text(if (row.active) "Active" else "Closed lifecycle record", style = MaterialTheme.typography.labelMedium)
                        }
                    }
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
    exitsReversible: Boolean = false,
    attachmentContent: (@Composable (animal: SpeciesAnimalRow) -> Unit)? = null,
    loading: Boolean = false,
    error: String? = null,
    exactEntry: Boolean = false,
) {
    FarmOperationalPage(
        config.screenIds.profile,
        "${config.name} profile",
        "",
        FarmVisualClass.I2,
        onBack,
        backLabel = if (exactEntry) "Back" else "Farm home",
    ) {
        if (selected == null) {
            FarmOperationalRows(
                emptyList(),
                when {
                    loading -> "Loading the selected animal"
                    error != null -> error
                    exactEntry -> "The selected animal is not available on this farm."
                    else -> "No animal selected"
                },
                if (exactEntry || loading || error != null) null else "Choose an animal from the herd or flock first.",
            )
        } else {
            FarmOperationalSection("Identity") {
                Text(selected.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (selected.active) "Active" else "Closed lifecycle record")
            }
            com.farmos.core.design.FarmProfileHealth(com.farmos.core.design.FarmHealthSubject(selected.animalId,config.plural))
            com.farmos.core.design.FarmProfileBreeding(selected.animalId,config.plural)
            FarmOperationalSection("Actions") {
                TextButton(onClick = { onOpen(SpeciesPage.WEIGHT) }, enabled = selected.active, modifier = Modifier.fillMaxWidth().testTag("species-profile-record-weight")) { Text("Record weight") }
                TextButton(onClick = { onOpen(SpeciesPage.OPERATIONS) }, enabled = selected.active, modifier = Modifier.fillMaxWidth()) { Text(config.operationsTitle) }
                TextButton(onClick = { onOpen(SpeciesPage.STATUS) }, enabled = selected.active || exitsReversible, modifier = Modifier.fillMaxWidth()) { Text("Lifecycle status") }
            }
            // Owner decision D-015: photos and documents of this animal, when the host provides them.
            attachmentContent?.invoke(selected)
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
    var tag by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var sex by rememberSaveable { mutableStateOf("FEMALE") }
    FarmOperationalPage(
        config.screenIds.register,
        "Register ${config.name.lowercase()}",
        "",
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
    backLabel: String = "Farm home",
) {
    var weight by rememberSaveable { mutableStateOf("") }
    val id = config.screenIds.weight
    FarmOperationalPage(id, "Record weight", "", onBack = onBack, backLabel = backLabel) {
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

@OptIn(ExperimentalLayoutApi::class)
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
    val id = config.screenIds.status
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
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
