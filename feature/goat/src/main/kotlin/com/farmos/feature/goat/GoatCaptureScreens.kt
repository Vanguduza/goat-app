package com.farmos.feature.goat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import com.farmos.core.design.FarmIllustratedSectionSurface
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.GoatStatus

@Composable
internal fun GoatRegisterScreen(
    onRegister: (String, String?, GoatSex, String) -> Unit,
    onBack: () -> Unit,
) {
    var tag by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf(GoatSex.FEMALE) }
    IllustratedGoatPage("Register goat", "FOS-GOAT-004 · I3", onBack) {
        FarmIllustratedSectionSurface {
            Text("Identity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(tag, { tag = it }, label = { Text("Tag") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                dob,
                { dob = it },
                label = { Text("Date of birth") },
                placeholder = { Text("YYYY-MM-DD") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(com.farmos.core.design.FosDimens.Grid)) {
                TextButton(onClick = { sex = GoatSex.FEMALE }) { Text(if (sex == GoatSex.FEMALE) "Female · selected" else "Female") }
                TextButton(onClick = { sex = GoatSex.MALE }) { Text(if (sex == GoatSex.MALE) "Male · selected" else "Male") }
            }
            Text("The record is saved locally first, then synchronized through the authoritative command path.")
            Button(
                onClick = { onRegister(tag, name.trim().ifBlank { null }, sex, dob) },
                enabled = tag.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Register goat") }
        }
    }
}

@Composable
internal fun GoatWeightScreen(
    state: GoatSliceUiState,
    onRecordWeight: (String) -> Unit,
    onSelectGoat: (String) -> Unit,
    onBack: () -> Unit,
) {
    var weight by remember { mutableStateOf("") }
    IllustratedGoatPage("Record weight", "FOS-GOAT-011 · I3 reference", onBack) {
        val goat = state.selected
        if (goat == null) {
            Text("Select the goat to weigh.")
            when (state.herdState) {
                LoadableSurfaceState.LOADING -> Text("Loading herd")
                LoadableSurfaceState.EMPTY -> Text("No goats on this device yet.")
                LoadableSurfaceState.ERROR -> Text(state.error ?: "Herd could not be loaded", color = MaterialTheme.colorScheme.error)
                LoadableSurfaceState.DISABLED -> Text("Herd actions are temporarily paused")
                LoadableSurfaceState.IDLE -> {
                    val active = state.herd.filter { it.status == GoatStatus.ACTIVE }
                    if (active.isEmpty()) {
                        Text("No active goats on this device.")
                    } else {
                        active.forEach { row ->
                            TextButton(onClick = { onSelectGoat(row.animalId) }, modifier = Modifier.fillMaxWidth()) {
                                Text(goatDisplayName(row) + " · " + row.tag)
                            }
                        }
                    }
                }
            }
            return@IllustratedGoatPage
        }
        FarmIllustratedSectionSurface {
            Text(goatDisplayName(goat), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(goat.tag + " · " + goatStatusLabel(goat.status))
            goat.latestWeightGrams?.let { Text("Latest ${formatKg(it)} kg") }
            OutlinedTextField(
                weight,
                { weight = it },
                label = { Text("Weight") },
                suffix = { Text("kg") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                enabled = !state.busy && goat.status == GoatStatus.ACTIVE,
            )
            Text("Saving succeeds when the measurement is durable on this device. Sync can happen later.")
            Button(
                onClick = { onRecordWeight(weight) },
                enabled = !state.busy && goat.status == GoatStatus.ACTIVE && weight.toDoubleOrNull()?.let { it > 0.0 } == true,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Record weight") }
            if (goat.syncPending || state.pendingSyncCount > 0) {
                Text("Local entries waiting to sync: ${state.pendingSyncCount}", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
internal fun GoatHealthCaptureScreen(
    state: GoatSliceUiState,
    actions: GoatExperienceActions,
    onBack: () -> Unit,
) {
    var famacha by remember { mutableStateOf("") }
    var famachaDay by remember { mutableStateOf("") }
    var bcs by remember { mutableStateOf("") }
    var bcsDay by remember { mutableStateOf("") }
    var scc by remember { mutableStateOf("") }
    var dim by remember { mutableStateOf("") }
    var sccDay by remember { mutableStateOf("") }
    IllustratedGoatPage("Health observations", "FOS-GOAT-022/016/020 · I3/I4", onBack) {
        val goat = state.selected
        if (goat == null) {
            Text("Select a goat first.")
            return@IllustratedGoatPage
        }
        FarmIllustratedSectionSurface {
            Text("FAMACHA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Record the eyelid score from 1 to 5. This observation does not start a treatment.")
            OutlinedTextField(famacha, { famacha = it }, label = { Text("Score 1–5") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(famachaDay, { famachaDay = it }, label = { Text("Date") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(
                onClick = { actions.onRecordFamacha(famacha, famachaDay) },
                enabled = !state.busy && famacha.toIntOrNull() in 1..5 && famachaDay.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Record FAMACHA") }
        }
        FarmIllustratedSectionSurface {
            Text("Body condition", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Record a BCS from 1.0 to 5.0. It remains an observation, not a diagnosis.")
            OutlinedTextField(bcs, { bcs = it }, label = { Text("BCS") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(bcsDay, { bcsDay = it }, label = { Text("Date") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(
                onClick = { actions.onRecordBcs(((bcs.toDoubleOrNull() ?: 0.0) * 10).toInt().toString(), bcsDay) },
                enabled = !state.busy && bcs.toDoubleOrNull()?.let { it in 1.0..5.0 } == true && bcsDay.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Record BCS") }
        }
        FarmIllustratedSectionSurface {
            Text("Somatic cell count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("SCC is recorded as a laboratory/production measure, not as a diagnosis.")
            OutlinedTextField(scc, { scc = it }, label = { Text("Cells/ml") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(dim, { dim = it }, label = { Text("Days in milk (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(sccDay, { sccDay = it }, label = { Text("Date") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(
                onClick = { actions.onRecordScc(scc, dim, sccDay) },
                enabled = !state.busy && scc.toIntOrNull()?.let { it > 0 } == true && sccDay.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Record SCC") }
        }
    }
}

@Composable
internal fun GoatReproductionScreen(
    state: GoatSliceUiState,
    actions: GoatExperienceActions,
    onBack: () -> Unit,
) {
    var milk by remember { mutableStateOf("") }
    var milkDay by remember { mutableStateOf("") }
    var heatDay by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("natural") }
    var sire by remember { mutableStateOf("") }
    var matingDay by remember { mutableStateOf("") }
    var pregResult by remember { mutableStateOf("pregnant") }
    var pregDay by remember { mutableStateOf("") }
    var lactationDay by remember { mutableStateOf("") }
    IllustratedGoatPage("Reproduction & milk", "FOS-GOAT-017/031/032/034/043 · I3", onBack) {
        val goat = state.selected
        if (goat == null || goat.sex != GoatSex.FEMALE) {
            Text("Select an active doe before recording reproduction or milk events.")
            return@IllustratedGoatPage
        }
        FarmIllustratedSectionSurface {
            Text("Milk", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(milk, { milk = it }, label = { Text("Litres") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(milkDay, { milkDay = it }, label = { Text("Date") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = { actions.onRecordMilk(milk, milkDay) }, enabled = !state.busy && milk.toDoubleOrNull()?.let { it > 0.0 } == true && milkDay.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Record milk") }
        }
        FarmIllustratedSectionSurface {
            Text("Heat and mating", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(heatDay, { heatDay = it }, label = { Text("Heat date") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = { actions.onRecordHeat(heatDay) }, enabled = !state.busy && heatDay.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Record heat") }
            OutlinedTextField(method, { method = it }, label = { Text("Method: natural, ai, hand_mating") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(sire, { sire = it }, label = { Text("Sire ID (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(matingDay, { matingDay = it }, label = { Text("Mating date") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("A mating record creates the governed +45 day pregnancy-check task.")
            Button(onClick = { actions.onRecordMating(method, sire, matingDay) }, enabled = !state.busy && method in setOf("natural", "ai", "hand_mating") && matingDay.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Record mating") }
        }
        FarmIllustratedSectionSurface {
            Text("Pregnancy & lactation follow-up", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(com.farmos.core.design.FosDimens.Grid)) {
                TextButton(onClick = { pregResult = "pregnant" }) { Text(if (pregResult == "pregnant") "Pregnant · selected" else "Pregnant") }
                TextButton(onClick = { pregResult = "open" }) { Text(if (pregResult == "open") "Open · selected" else "Open") }
            }
            OutlinedTextField(pregDay, { pregDay = it }, label = { Text("Check date") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("The pregnancy result is a recorded check; Farm OS does not diagnose autonomously.")
            Button(onClick = { actions.onRecordPregnancy(pregResult, pregDay) }, enabled = !state.busy && pregDay.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Record pregnancy check") }
            OutlinedTextField(lactationDay, { lactationDay = it }, label = { Text("Kidding date for +7 day follow-up") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = { actions.onPlanLactation(lactationDay) }, enabled = !state.busy && lactationDay.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Plan lactation follow-up") }
        }
    }
}

@Composable
internal fun GoatKiddingScreen(
    state: GoatSliceUiState,
    actions: GoatExperienceActions,
    onBack: () -> Unit,
) {
    var born by remember { mutableStateOf("") }
    var live by remember { mutableStateOf("") }
    var dead by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    var kidTag by remember { mutableStateOf("") }
    var kidSex by remember { mutableStateOf(GoatSex.FEMALE) }
    IllustratedGoatPage("Kidding", "FOS-GOAT-037/039 · I3/I4", onBack) {
        val goat = state.selected
        if (goat == null || goat.sex != GoatSex.FEMALE) {
            Text("Select a doe before recording kidding.")
            return@IllustratedGoatPage
        }
        FarmIllustratedSectionSurface {
            Text("Record kidding", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Born must equal live + dead. Mortality remains explicit and is never hidden by optimistic copy.")
            OutlinedTextField(born, { born = it }, label = { Text("Born") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(live, { live = it }, label = { Text("Live") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(dead, { dead = it }, label = { Text("Dead") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(day, { day = it }, label = { Text("Kidding date") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            val countsValid = born.toIntOrNull()?.let { b -> live.toIntOrNull()?.let { l -> dead.toIntOrNull()?.let { d -> b > 0 && l >= 0 && d >= 0 && l + d == b } } } == true
            Button(onClick = { actions.onRecordKidding(born, live, dead, day) }, enabled = !state.busy && countsValid && day.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Record kidding") }
        }
        FarmIllustratedSectionSurface {
            Text("Register live kid", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val latest = goat.kiddingHistory.firstOrNull()
            if (latest == null) Text("Record a kidding event first.")
            OutlinedTextField(kidTag, { kidTag = it }, label = { Text("Kid tag") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = latest != null)
            Row(horizontalArrangement = Arrangement.spacedBy(com.farmos.core.design.FosDimens.Grid)) {
                TextButton(onClick = { kidSex = GoatSex.FEMALE }, enabled = latest != null) { Text(if (kidSex == GoatSex.FEMALE) "Doe kid · selected" else "Doe kid") }
                TextButton(onClick = { kidSex = GoatSex.MALE }, enabled = latest != null) { Text(if (kidSex == GoatSex.MALE) "Buck kid · selected" else "Buck kid") }
            }
            Button(
                onClick = { latest?.let { actions.onRegisterKid(it.kiddingId, kidTag, kidSex) } },
                enabled = !state.busy && latest != null && kidTag.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Register kid") }
        }
    }
}

@Composable
internal fun GoatSearchScreen(
    state: GoatSliceUiState,
    onSearch: (String) -> Unit,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    IllustratedGoatPage("Search goats", "FOS-GOAT-006 · I3", onBack) {
        FarmIllustratedSectionSurface {
            OutlinedTextField(query, { query = it }, label = { Text("Tag or name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = { onSearch(query) }, enabled = !state.busy && query.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Search records") }
            Text(state.searchMessage)
            state.searchResults.take(12).forEach { result ->
                TextButton(onClick = { onSelect(result.animalId) }, modifier = Modifier.fillMaxWidth()) { Text(herdSearchLabel(result)) }
            }
        }
    }
}

@Composable
internal fun GoatSyncScreen(
    state: GoatSliceUiState,
    onSync: () -> Unit,
    onBack: () -> Unit,
) {
    IllustratedGoatPage("Sync status", "FOS-SYNC-002/003 · I3", onBack) {
        FarmIllustratedSectionSurface {
            Text(state.syncMessage, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Pending local changes: ${state.pendingSyncCount}")
            Text("Saved field entries stay on this device when the network is unavailable.")
            Button(onClick = onSync, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(if (state.busy) "Syncing…" else "Sync now") }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
