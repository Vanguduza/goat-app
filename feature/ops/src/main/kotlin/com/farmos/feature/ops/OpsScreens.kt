package com.farmos.feature.ops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

@Composable
fun HealthObservationScreen(
    rows: List<String>,
    catalog: List<String>,
    treatments: List<String>,
    busy: Boolean,
    error: String?,
    onRecord: (species: String, signs: String, firstAid: String, redFlag: Boolean) -> Unit,
    onCreateFormulary: (product: String, species: String, vetClass: String, meatDays: Int?) -> Unit,
    onRecordTreatment: (species: String, formularyItemId: String, reason: String) -> Unit,
    packs: List<String> = emptyList(),
    withdrawals: List<String> = emptyList(),
    onAcceptPack: (species: String, name: String, vet: String) -> Unit = { _, _, _ -> },
    onRecordVetVisit: (species: String, reason: String, vet: String, day: String) -> Unit = { _, _, _, _ -> },
    onRecordLab: (animalId: String, testName: String, resultText: String, cells: String, day: String) -> Unit = { _, _, _, _, _ -> },
    onAddPackSlot: (packId: String, slotCode: String, title: String, offset: String, fromEvent: String) -> Unit = { _, _, _, _, _ -> },
    onApplyPack: (packId: String, animalId: String, day: String) -> Unit = { _, _, _ -> },
    onBack: () -> Unit,
) {
    var species by remember { mutableStateOf("goat") }
    var signs by remember { mutableStateOf("") }
    var firstAid by remember { mutableStateOf("") }
    var product by remember { mutableStateOf("") }
    var vetClass by remember { mutableStateOf("vaccine") }
    var withdrawal by remember { mutableStateOf("") }
    var formularyId by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var packName by remember { mutableStateOf("") }
    var vetName by remember { mutableStateOf("") }
    var visitReason by remember { mutableStateOf("") }
    var visitDay by remember { mutableStateOf("") }
    var labAnimalId by remember { mutableStateOf("") }
    var labTest by remember { mutableStateOf("") }
    var labResult by remember { mutableStateOf("") }
    var labCells by remember { mutableStateOf("") }
    var labDay by remember { mutableStateOf("") }
    var packId by remember { mutableStateOf("") }
    var slotCode by remember { mutableStateOf("cdt_prepartum") }
    var slotTitle by remember { mutableStateOf("CDT prepartum") }
    var slotOffset by remember { mutableStateOf("-28") }
    var slotFrom by remember { mutableStateOf("expected_birth") }
    var applyAnimal by remember { mutableStateOf("") }
    var applyDay by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FosDimens.ScreenMargin),
        verticalArrangement = Arrangement.spacedBy(FosDimens.IntraCardGap),
    ) {
        Text("Health observations", style = MaterialTheme.typography.titleLarge)
        Text("Record signs and first aid first. Treatments must use a vet-approved formulary item. Dose is not captured.")
        if (rows.isEmpty()) Text("No observations on this device.")
        rows.forEach { Text(it) }
        HorizontalDivider()
        OutlinedTextField(species, { species = it }, label = { Text("Species") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(signs, { signs = it }, label = { Text("Signs") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
        OutlinedTextField(firstAid, { firstAid = it }, label = { Text("First aid applied") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
        Button(onClick = { onRecord(species, signs, firstAid, false) }, enabled = !busy && signs.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Record observation")
        }
        HorizontalDivider()
        Text("Formulary and treatments", style = MaterialTheme.typography.labelLarge)
        Text("Treatments use a vet-approved formulary item. Dose is not captured here.")
        if (catalog.isEmpty()) Text("No disease catalog rows on this device.")
        catalog.take(8).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (treatments.isEmpty()) Text("No treatments on this device.")
        treatments.forEach { Text(it) }
        OutlinedTextField(product, { product = it }, label = { Text("Formulary product") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(vetClass, { vetClass = it }, label = { Text("Vet class") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(withdrawal, { withdrawal = it }, label = { Text("Meat withdrawal days") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        Button(onClick = { onCreateFormulary(product, species, vetClass, withdrawal.toIntOrNull()) }, enabled = !busy && product.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Add vet-approved formulary item")
        }
        OutlinedTextField(formularyId, { formularyId = it }, label = { Text("Formulary item id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(reason, { reason = it }, label = { Text("Treatment reason") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
        Button(onClick = { onRecordTreatment(species, formularyId, reason) }, enabled = !busy && formularyId.isNotBlank() && reason.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Record treatment")
        }
        HorizontalDivider()
        Text("Protocol packs and withdrawals", style = MaterialTheme.typography.labelLarge)
        Text("A pack is accepted only with the attending vet named. Withdrawals come from the formulary label, not a typed dose.")
        if (packs.isEmpty()) Text("No accepted protocol packs on this device.")
        packs.forEach { Text(it) }
        if (withdrawals.isEmpty()) Text("No open withdrawal windows on this device.")
        withdrawals.forEach { Text(it) }
        OutlinedTextField(packName, { packName = it }, label = { Text("Pack name") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(vetName, { vetName = it }, label = { Text("Attending vet") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        Button(
            onClick = { onAcceptPack(species, packName, vetName) },
            enabled = !busy && packName.isNotBlank() && vetName.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Accept protocol pack") }
        HorizontalDivider()
        Text("Vet visit and lab result", style = MaterialTheme.typography.labelLarge)
        Text("A lab result is a recorded figure, not a diagnosis.")
        OutlinedTextField(visitReason, { visitReason = it }, label = { Text("Visit reason") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
        OutlinedTextField(visitDay, { visitDay = it }, label = { Text("Visit date") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        Button(
            onClick = { onRecordVetVisit(species, visitReason, vetName, visitDay) },
            enabled = !busy && visitReason.isNotBlank() && vetName.isNotBlank() && visitDay.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Record vet visit") }
        OutlinedTextField(labAnimalId, { labAnimalId = it }, label = { Text("Animal id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(labTest, { labTest = it }, label = { Text("Lab test") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(labResult, { labResult = it }, label = { Text("Result") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
        OutlinedTextField(labCells, { labCells = it }, label = { Text("Cells/ml") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(labDay, { labDay = it }, label = { Text("Lab date") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        Button(
            onClick = { onRecordLab(labAnimalId, labTest, labResult, labCells, labDay) },
            enabled = !busy && labAnimalId.isNotBlank() && labTest.isNotBlank() && labResult.isNotBlank() && labDay.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Record lab result") }
        HorizontalDivider()
        Text("Protocol pack slots become due tasks when the pack is applied. This is not a dose.")
        OutlinedTextField(packId, { packId = it }, label = { Text("Pack id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(slotCode, { slotCode = it }, label = { Text("Slot code") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(slotTitle, { slotTitle = it }, label = { Text("Slot title") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(slotOffset, { slotOffset = it }, label = { Text("Offset days") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(slotFrom, { slotFrom = it }, label = { Text("Anchor") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        Button(
            onClick = { onAddPackSlot(packId, slotCode, slotTitle, slotOffset, slotFrom) },
            enabled = !busy && packId.isNotBlank() && slotCode.isNotBlank() && slotTitle.isNotBlank() && slotOffset.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add pack slot") }
        OutlinedTextField(applyAnimal, { applyAnimal = it }, label = { Text("Apply animal id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(applyDay, { applyDay = it }, label = { Text("Anchor date") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        Button(
            onClick = { onApplyPack(packId, applyAnimal, applyDay) },
            enabled = !busy && packId.isNotBlank() && applyAnimal.isNotBlank() && applyDay.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Apply pack") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = onBack, enabled = !busy) { Text("Back to farm home") }
    }
}

@Composable
fun MoneyCaptureScreen(
    rows: List<String>,
    busy: Boolean,
    error: String?,
    onRecord: (kind: String, category: String, amount: String, day: String) -> Unit,
    onBack: () -> Unit,
) {
    var kind by remember { mutableStateOf("expense") }
    var category by remember { mutableStateOf("feed") }
    var amount by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FosDimens.ScreenMargin),
        verticalArrangement = Arrangement.spacedBy(FosDimens.IntraCardGap),
    ) {
        Text("Money records", style = MaterialTheme.typography.titleLarge)
        Text("Amounts are stored in minor units. Enter dollars and cents as a figure, for example 12.50.")
        if (rows.isEmpty()) Text("No money records on this device.")
        rows.forEach { Text(it) }
        HorizontalDivider()
        OutlinedTextField(kind, { kind = it }, label = { Text("Kind") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(category, { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(day, { day = it }, label = { Text("Date") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        Button(onClick = { onRecord(kind, category, amount, day) }, enabled = !busy && amount.isNotBlank() && day.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Record money")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = onBack, enabled = !busy) { Text("Back to farm home") }
    }
}

@Composable
fun InventoryScreen(
    rows: List<String>,
    busy: Boolean,
    error: String?,
    onCreate: (sku: String, name: String, unit: String) -> Unit,
    onMove: (itemId: String, direction: String, quantity: String) -> Unit,
    onReceiveLot: (itemId: String, lotCode: String, expiry: String, quantity: String) -> Unit = { _, _, _, _ -> },
    onIssueLot: (itemId: String, quantity: String) -> Unit = { _, _ -> },
    onSetReorder: (itemId: String, quantity: String) -> Unit = { _, _ -> },
    onRecordReorder: (itemId: String, day: String) -> Unit = { _, _ -> },
    onBack: () -> Unit,
) {
    var sku by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("kg") }
    var itemId by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf("receive") }
    var quantity by remember { mutableStateOf("") }
    var lotCode by remember { mutableStateOf("") }
    var lotExpiry by remember { mutableStateOf("") }
    var reorderQty by remember { mutableStateOf("") }
    var reorderDay by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FosDimens.ScreenMargin),
        verticalArrangement = Arrangement.spacedBy(FosDimens.IntraCardGap),
    ) {
        Text("Inventory", style = MaterialTheme.typography.titleLarge)
        if (rows.isEmpty()) Text("No stock items on this device. Create an item, then receive stock.")
        rows.forEach { Text(it) }
        HorizontalDivider()
        OutlinedTextField(sku, { sku = it }, label = { Text("Sku") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(unit, { unit = it }, label = { Text("Unit") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        Button(onClick = { onCreate(sku, name, unit) }, enabled = !busy && sku.isNotBlank() && name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Create item")
        }
        OutlinedTextField(itemId, { itemId = it }, label = { Text("Item id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(direction, { direction = it }, label = { Text("Direction") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantity") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        Button(onClick = { onMove(itemId, direction, quantity) }, enabled = !busy && itemId.isNotBlank() && quantity.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Move stock")
        }
        HorizontalDivider()
        Text("Dated lots. Issue uses oldest expiry first. Non-lot stock is not used.")
        OutlinedTextField(lotCode, { lotCode = it }, label = { Text("Lot code") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(lotExpiry, { lotExpiry = it }, label = { Text("Expiry") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        Button(
            onClick = { onReceiveLot(itemId, lotCode, lotExpiry, quantity) },
            enabled = !busy && itemId.isNotBlank() && lotCode.isNotBlank() && lotExpiry.isNotBlank() && quantity.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Receive dated lot") }
        Button(
            onClick = { onIssueLot(itemId, quantity) },
            enabled = !busy && itemId.isNotBlank() && quantity.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Issue oldest lot") }
        HorizontalDivider()
        Text("Reorder alerts are records when on-hand is at or below the point. They are not a forecast engine.")
        OutlinedTextField(reorderQty, { reorderQty = it }, label = { Text("Reorder quantity") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        Button(
            onClick = { onSetReorder(itemId, reorderQty) },
            enabled = !busy && itemId.isNotBlank() && reorderQty.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Set reorder point") }
        OutlinedTextField(reorderDay, { reorderDay = it }, label = { Text("Alert date") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        Button(
            onClick = { onRecordReorder(itemId, reorderDay) },
            enabled = !busy && itemId.isNotBlank() && reorderDay.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Record reorder alert") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = onBack, enabled = !busy) { Text("Back to farm home") }
    }
}

@Composable
fun SimpleCaptureScreen(
    title: String,
    help: String,
    empty: String,
    rows: List<String>,
    busy: Boolean,
    error: String?,
    fields: List<Pair<String, androidx.compose.runtime.MutableState<String>>>,
    actionLabel: String,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FosDimens.ScreenMargin),
        verticalArrangement = Arrangement.spacedBy(FosDimens.IntraCardGap),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(help)
        if (rows.isEmpty()) Text(empty) else rows.forEach { Text(it) }
        HorizontalDivider()
        extra()
        fields.forEach { (label, state) ->
            OutlinedTextField(state.value, { state.value = it }, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        }
        Button(onClick = onSubmit, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = onBack, enabled = !busy) { Text("Back to farm home") }
    }
}
