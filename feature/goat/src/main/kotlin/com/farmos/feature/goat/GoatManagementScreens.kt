package com.farmos.feature.goat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.farmos.core.design.FarmDateField
import com.farmos.core.design.FarmIllustratedSectionSurface
import com.farmos.core.design.FarmSearchSelector
import com.farmos.core.design.FarmSelectorSearch
import com.farmos.domain.goat.GoatSnapshot
import java.time.LocalDate

/** FOS-GOAT-005 — Edit Goat Identity: amend tag, name and official identifier of the selected goat. */
@Composable
internal fun GoatIdentityEditScreen(
    goat: GoatSnapshot,
    identifiers: List<GoatIdentifierView>,
    busy: Boolean,
    error: String?,
    onSave: (tag: String, name: String, officialId: String) -> Unit,
    onBack: () -> Unit,
) {
    var tag by remember(goat.animalId) { mutableStateOf(goat.tag) }
    var name by remember(goat.animalId) { mutableStateOf(goat.name.orEmpty()) }
    val currentOfficial = identifiers.firstOrNull { it.type == "official_id" && it.isActive }?.value.orEmpty()
    var officialId by remember(goat.animalId, currentOfficial) { mutableStateOf(currentOfficial) }
    IllustratedGoatPage("Edit goat identity", "FOS-GOAT-005 · I3", onBack) {
        FarmIllustratedSectionSurface {
            Text("Identity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(tag, { tag = it }, label = { Text("Tag") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy)
            OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy)
            OutlinedTextField(officialId, { officialId = it }, label = { Text("Official identifier (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy)
            Text(
                "The official identifier is stored in the shared animal identifier registry; " +
                    "assigning a new one retires the previous official identifier.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            Button({ onSave(tag, name, officialId) }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                Text(if (busy) "Saving…" else "Save identity")
            }
        }
    }
}

/** FOS-GOAT-042 — Weaning: record weaning for the selected kid, with its weaning history. */
@Composable
internal fun GoatWeaningScreen(
    goat: GoatSnapshot,
    weanings: List<GoatWeaningView>,
    busy: Boolean,
    error: String?,
    onRecordWeaning: (weightKgText: String, dayText: String) -> Unit,
    onBack: () -> Unit,
) {
    var weightKg by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    IllustratedGoatPage("Weaning", "FOS-GOAT-042 · I3", onBack) {
        FarmIllustratedSectionSurface {
            Text("Record weaning — ${goatDisplayName(goat)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            FarmDateField(label = "Weaning date", value = day, onValueChange = { day = it }, key = "weaning", enabled = !busy)
            OutlinedTextField(weightKg, { weightKg = it }, label = { Text("Weaning weight kg (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy)
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            Button({ onRecordWeaning(weightKg, day) }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                Text(if (busy) "Saving…" else "Record weaning")
            }
        }
        FarmIllustratedSectionSurface {
            Text("Weaning history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (weanings.isEmpty()) {
                Text("No weaning recorded for this goat.", style = MaterialTheme.typography.bodySmall)
            } else {
                weanings.forEach { w ->
                    Text(
                        "${LocalDate.ofEpochDay(w.occurredEpochDay)}" +
                            (w.weightGrams?.let { " · ${formatKg(it)} kg" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/** FOS-GOAT-049 — Goat Movement: record official on/off/transfer movements, with movement history. */
@Composable
internal fun GoatMovementScreen(
    goat: GoatSnapshot,
    movements: List<GoatMovementView>,
    busy: Boolean,
    error: String?,
    onRecordMovement: (direction: String, fromPlace: String, toPlace: String, dayText: String) -> Unit,
    onBack: () -> Unit,
) {
    var direction by remember { mutableStateOf("transfer") }
    var fromPlace by remember { mutableStateOf("") }
    var toPlace by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    IllustratedGoatPage("Goat movement", "FOS-GOAT-049 · I3", onBack) {
        FarmIllustratedSectionSurface {
            Text("Record movement — ${goatDisplayName(goat)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("on" to "On", "transfer" to "Transfer", "off" to "Off").forEach { (value, label) ->
                    TextButton(onClick = { direction = value }, enabled = !busy) {
                        Text(if (direction == value) "$label · selected" else label)
                    }
                }
            }
            OutlinedTextField(fromPlace, { fromPlace = it }, label = { Text("From place (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy)
            OutlinedTextField(toPlace, { toPlace = it }, label = { Text("To place (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy)
            FarmDateField(label = "Movement date", value = day, onValueChange = { day = it }, key = "movement", enabled = !busy)
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            Button({ onRecordMovement(direction, fromPlace, toPlace, day) }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                Text(if (busy) "Saving…" else "Record movement")
            }
        }
        FarmIllustratedSectionSurface {
            Text("Movement history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (movements.isEmpty()) {
                Text("No movements recorded for this goat.", style = MaterialTheme.typography.bodySmall)
            } else {
                movements.forEach { m ->
                    Text(
                        "${LocalDate.ofEpochDay(m.occurredEpochDay)} · ${m.direction}" +
                            (m.fromPlace?.let { " from $it" } ?: "") +
                            (m.toPlace?.let { " to $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/** FOS-GOAT-050 — Official Identifier: the goat's identifier registry and assignment. */
@Composable
internal fun GoatIdentifiersScreen(
    goat: GoatSnapshot,
    identifiers: List<GoatIdentifierView>,
    busy: Boolean,
    error: String?,
    onAssignIdentifier: (type: String, value: String) -> Unit,
    onBack: () -> Unit,
) {
    var type by remember { mutableStateOf("official_id") }
    var value by remember { mutableStateOf("") }
    IllustratedGoatPage("Official identifier", "FOS-GOAT-050 · I3", onBack) {
        FarmIllustratedSectionSurface {
            Text("Identifiers — ${goatDisplayName(goat)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (identifiers.isEmpty()) {
                Text("No identifiers assigned.", style = MaterialTheme.typography.bodySmall)
            } else {
                identifiers.forEach { id ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${id.type}: ${id.value}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text(if (id.isActive) "active" else "retired", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        FarmIllustratedSectionSurface {
            Text("Assign identifier", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("official_id" to "Official ID", "rfid" to "RFID", "eid" to "EID", "ear_tag" to "Ear tag").forEach { (value, label) ->
                    TextButton(onClick = { type = value }, enabled = !busy) {
                        Text(if (type == value) "$label · selected" else label)
                    }
                }
            }
            OutlinedTextField(value, { value = it }, label = { Text("Identifier value") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy)
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            Button({ onAssignIdentifier(type, value) }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                Text(if (busy) "Saving…" else "Assign identifier")
            }
        }
    }
}

/** FOS-GOAT-023 — FAMACHA Reference: the static 1–5 anaemia scoring chart. No animal data. */
@Composable
internal fun FamachaReferenceScreen(onBack: () -> Unit) {
    IllustratedGoatPage("FAMACHA reference", "FOS-GOAT-023 · I3", onBack) {
        FarmIllustratedSectionSurface {
            Text("FAMACHA chart", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Score the colour of the lower eyelid membrane against this chart. " +
                    "This screen never diagnoses; it only shows the reference.",
                style = MaterialTheme.typography.bodySmall,
            )
            listOf(
                "1 — Red: healthy, no treatment needed",
                "2 — Red-pink: healthy, monitor",
                "3 — Pink: borderline, consider treatment",
                "4 — Pink-white: anaemic, treat",
                "5 — White: severely anaemic, treat urgently",
            ).forEach { row ->
                Text(row, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** FOS-GOAT-045 — Parentage Editor: link dam and sire for the selected goat via the governed pedigree command. */
@Composable
internal fun GoatParentageEditScreen(
    goat: GoatSnapshot,
    pedigreeParents: List<String>,
    searchDams: FarmSelectorSearch,
    searchSires: FarmSelectorSearch,
    busy: Boolean,
    error: String?,
    onLinkParentage: (parentId: String, relationType: String) -> Unit,
    onBack: () -> Unit,
) {
    IllustratedGoatPage("Parentage editor", "FOS-GOAT-045 · I3", onBack) {
        FarmIllustratedSectionSurface {
            Text("Parents — ${goatDisplayName(goat)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (pedigreeParents.isEmpty()) {
                Text("No parents linked yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                pedigreeParents.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
        FarmIllustratedSectionSurface {
            Text("Link dam", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            FarmSearchSelector(
                atomTag = "farm-atom:goat-dam-selector",
                title = "Dam",
                search = searchDams,
                selected = null,
                onSelect = { onLinkParentage(it.id, "dam") },
                emptyText = "No does match on this device",
                enabled = !busy,
            )
        }
        FarmIllustratedSectionSurface {
            Text("Link sire", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            FarmSearchSelector(
                atomTag = "farm-atom:goat-sire-selector",
                title = "Sire",
                search = searchSires,
                selected = null,
                onSelect = { onLinkParentage(it.id, "sire") },
                emptyText = "No bucks match on this device",
                enabled = !busy,
            )
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** FOS-GOAT-047 — Goat Groups: the farm's goat groups. */
@Composable
internal fun GoatGroupsScreen(
    groups: List<GoatGroupView>,
    onOpenGroup: (groupId: String) -> Unit,
    onBack: () -> Unit,
) {
    IllustratedGoatPage("Goat groups", "FOS-GOAT-047 · I3", onBack) {
        FarmIllustratedSectionSurface {
            if (groups.isEmpty()) {
                Text("No goat groups on this farm yet. Create one from the Groups module.", style = MaterialTheme.typography.bodySmall)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { group ->
                        TextButton({ onOpenGroup(group.groupId) }, modifier = Modifier.fillMaxWidth()) {
                            Text("${group.name} · ${group.headCount} head", modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

/** FOS-GOAT-048 — Group Membership: census detail for one goat group. */
@Composable
internal fun GoatGroupMembershipScreen(
    group: GoatGroupView?,
    onBack: () -> Unit,
) {
    IllustratedGoatPage("Group membership", "FOS-GOAT-048 · I3", onBack) {
        FarmIllustratedSectionSurface {
            if (group == null) {
                Text("Group not found.", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Recorded head count: ${group.headCount}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Membership on this farm is tracked as a recorded head count per group; " +
                        "per-animal membership lists are not kept.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** FOS-GOAT-055 — Goat Report: read-only herd aggregates from local records. */
@Composable
internal fun GoatReportScreen(
    counts: com.farmos.domain.goat.GoatHerdCounts?,
    lactation: GoatLactationState,
    kiddingDue: GoatKiddingDueState,
    onBack: () -> Unit,
) {
    IllustratedGoatPage("Goat report", "FOS-GOAT-055 · I3", onBack) {
        FarmIllustratedSectionSurface {
            Text("Herd", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Active: ${counts?.active ?: "—"} · Does: ${counts?.does ?: "—"} · Bucks: ${counts?.bucks ?: "—"} · Kids: ${counts?.kids ?: "—"}", style = MaterialTheme.typography.bodySmall)
        }
        FarmIllustratedSectionSurface {
            Text("Milk", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            when (lactation) {
                is GoatLactationState.Loading -> Text("Loading…", style = MaterialTheme.typography.bodySmall)
                is GoatLactationState.Failed -> Text("Milk records could not be loaded.", style = MaterialTheme.typography.bodySmall)
                is GoatLactationState.Loaded -> {
                    val rows = lactation.rows
                    if (rows.isEmpty()) Text("No milk recorded.", style = MaterialTheme.typography.bodySmall)
                    else {
                        val totalMilli = rows.sumOf { it.totalMilli }
                        Text("${rows.size} does · ${"%.1f".format(totalMilli / 1_000.0)} L recorded", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        FarmIllustratedSectionSurface {
            Text("Kidding due", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            when (kiddingDue) {
                is GoatKiddingDueState.Loading -> Text("Loading…", style = MaterialTheme.typography.bodySmall)
                is GoatKiddingDueState.Failed -> Text("Kidding records could not be loaded.", style = MaterialTheme.typography.bodySmall)
                is GoatKiddingDueState.Loaded -> {
                    if (kiddingDue.rows.isEmpty()) Text("No does expected to kid.", style = MaterialTheme.typography.bodySmall)
                    else Text("${kiddingDue.rows.size} does expected to kid.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * FOS-GOAT-012 — BLE Scale Pairing.
 *
 * GENUINE GAP — not implemented: there is no BLE hardware adapter in this build (no scale
 * discovery, pairing, or weight-ingest path), so a pairing UI would be a fake.
 * Required piece: a Farm OS-owned BLE scale adapter behind the hardware boundary, with a
 * governed weight-ingest command. This screen fails closed.
 */
@Composable
internal fun GoatScalePairingScreen(onBack: () -> Unit) {
    IllustratedGoatPage("Scale pairing", "FOS-GOAT-012 · I3", onBack) {
        FarmIllustratedSectionSurface {
            Text("Unavailable", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "BLE scale pairing is not available in this build: there is no Bluetooth scale adapter. " +
                    "No scale is shown rather than a fabricated one.",
            )
            Text("Required: a Farm OS-owned BLE scale adapter with a governed weight-ingest command.")
        }
    }
}
