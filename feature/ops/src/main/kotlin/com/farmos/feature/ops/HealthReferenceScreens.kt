package com.farmos.feature.ops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmVisualClass

/** A formulary item as recorded; withdrawal days are null when not recorded. */
data class FormularyItemView(
    val id: String,
    val productName: String,
    val speciesCode: String,
    val vetClass: String,
    val meatWithdrawalDays: Int?,
    val milkWithdrawalDays: Int?,
    val eggWithdrawalDays: Int?,
    val vetApproved: Boolean,
    /** Exhaustive count of treatments recorded against this item. */
    val treatmentCount: Int,
)

data class ProtocolSlotView(val id: String, val slotCode: String, val title: String, val offsetDays: Int, val fromEvent: String, val isCore: Boolean)

/** A protocol pack as recorded, its schedule slots and the exhaustive count of recorded applications. */
data class ProtocolPackView(
    val id: String,
    val name: String,
    val speciesCode: String,
    val status: String,
    val acceptedByVet: String?,
    val slots: List<ProtocolSlotView>,
    val applicationCount: Int,
)

@Composable
private fun ReferenceRow(primary: String, secondary: String, tag: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(tag), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(primary, color = AnimalFarmTheme.colors.mutedInk)
        Text(secondary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ReferenceSwitcher(title: String, options: List<Pair<String, String>>, selectedId: String?, onSelect: (String) -> Unit) {
    FarmOperationalSection(title) {
        options.forEach { (id, label) ->
            val selected = id == selectedId
            TextButton(
                onClick = { onSelect(id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                    .semantics { this.selected = selected }
                    .testTag("health-reference-option:$id"),
            ) { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
}

private fun withdrawal(days: Int?) = days?.let { "$it day(s)" } ?: "Not recorded"

/** FOS-HEALTH-014 — one formulary item exactly as recorded. Farm OS does not prescribe a dose. */
@Composable
internal fun FormularyItemScreen(items: List<FormularyItemView>, onBack: () -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf(items.firstOrNull()?.id) }
    FarmOperationalPage("FOS-HEALTH-014", "Formulary item", "A formulary item as recorded on this device. No dose is prescribed.", FarmVisualClass.I4, onBack) {
        if (items.isEmpty()) {
            AnimalFarmEmptyState("No formulary items on this device.")
            return@FarmOperationalPage
        }
        ReferenceSwitcher("Formulary", items.map { it.id to "${it.productName} · ${it.speciesCode}" }, selectedId) { selectedId = it }
        val item = items.firstOrNull { it.id == selectedId } ?: return@FarmOperationalPage
        FarmOperationalSection(item.productName) {
            ReferenceRow("Species", item.speciesCode, "formulary-species")
            ReferenceRow("Veterinary class", item.vetClass, "formulary-class")
            ReferenceRow("Vet approval", if (item.vetApproved) "Vet approved" else "Not vet approved", "formulary-approval")
            ReferenceRow("Meat withdrawal", withdrawal(item.meatWithdrawalDays), "formulary-meat")
            ReferenceRow("Milk withdrawal", withdrawal(item.milkWithdrawalDays), "formulary-milk")
            ReferenceRow("Egg withdrawal", withdrawal(item.eggWithdrawalDays), "formulary-egg")
            ReferenceRow("Treatments recorded", item.treatmentCount.toString(), "formulary-treatments")
        }
    }
}

/** FOS-HEALTH-016 — one protocol pack with its recorded schedule slots. Records only; no dosing. */
@Composable
internal fun ProtocolPackDetailScreen(packs: List<ProtocolPackView>, onBack: () -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf(packs.firstOrNull()?.id) }
    FarmOperationalPage("FOS-HEALTH-016", "Protocol pack", "A protocol pack and its schedule slots as recorded. Farm OS does not prescribe.", FarmVisualClass.I3, onBack) {
        if (packs.isEmpty()) {
            AnimalFarmEmptyState("No protocol packs on this device.")
            return@FarmOperationalPage
        }
        ReferenceSwitcher("Protocol packs", packs.map { it.id to "${it.name} · ${it.speciesCode}" }, selectedId) { selectedId = it }
        val pack = packs.firstOrNull { it.id == selectedId } ?: return@FarmOperationalPage
        FarmOperationalSection(pack.name) {
            ReferenceRow("Species", pack.speciesCode, "pack-species")
            ReferenceRow("Status", pack.status, "pack-status")
            ReferenceRow("Accepted by vet", pack.acceptedByVet ?: "Not recorded", "pack-vet")
            ReferenceRow("Times applied", pack.applicationCount.toString(), "pack-applications")
        }
        FarmOperationalSection("Schedule slots · ${pack.slots.size}") {
            if (pack.slots.isEmpty()) Text("No schedule slots recorded for this pack.", color = AnimalFarmTheme.colors.mutedInk)
            pack.slots.forEach {
                ReferenceRow(
                    "${it.slotCode} · day ${it.offsetDays} from ${it.fromEvent}" + if (it.isCore) " · core" else "",
                    it.title,
                    "pack-slot:${it.id}",
                )
            }
        }
    }
}
