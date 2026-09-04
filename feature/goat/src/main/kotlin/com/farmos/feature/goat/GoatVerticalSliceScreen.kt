package com.farmos.feature.goat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.farmos.core.design.FarmOsTheme
import com.farmos.core.design.FosDimens
import com.farmos.domain.goat.GoatSearchResult
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.GoatSnapshot
import com.farmos.domain.goat.GoatStatus
import com.farmos.domain.goat.WeightSample
import java.time.Instant
import java.time.LocalDate

/**
 * Architecture proving surface only. Visual authority maps this legacy mega-screen to
 * FOS-GOAT-001/002/003/004/011/022/031/032/034/037/039.
 * Preserve command/state contracts while decomposing into atomic illustrated pages.
 */
@Composable
fun GoatVerticalSliceScreen(
    state: GoatSliceUiState,
    onRegister: (tag: String, name: String?, sex: GoatSex, dateOfBirthText: String) -> Unit,
    onRecordWeight: (weightKgText: String) -> Unit,
    onRecordKidding: (bornText: String, liveText: String, deadText: String, dayText: String) -> Unit,
    onRegisterKid: (kiddingId: String, tag: String, sex: GoatSex) -> Unit,
    onRecordFamacha: (scoreText: String, dayText: String) -> Unit,
    onRecordMilk: (litresText: String, dayText: String) -> Unit,
    onRecordBcs: (scoreTenthsText: String, dayText: String) -> Unit,
    onRecordScc: (cellsText: String, dimText: String, dayText: String) -> Unit,
    onRecordHeat: (dayText: String) -> Unit,
    onRecordMating: (method: String, sireId: String, dayText: String) -> Unit,
    onRecordPregnancy: (result: String, dayText: String) -> Unit,
    onPlanLactation: (dayText: String) -> Unit,
    onSetStatus: (GoatStatus) -> Unit,
    onSelectGoat: (animalId: String) -> Unit,
    onSyncNow: () -> Unit,
    onSearch: (query: String) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit = onSignOut,
    modifier: Modifier = Modifier,
) {
    var tag by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var dateOfBirth by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf(GoatSex.FEMALE) }
    var weight by remember { mutableStateOf("") }
    var bornCount by remember { mutableStateOf("") }
    var liveCount by remember { mutableStateOf("") }
    var deadCount by remember { mutableStateOf("") }
    var kiddingDay by remember { mutableStateOf("") }
    var kidTag by remember { mutableStateOf("") }
    var kidSex by remember { mutableStateOf(GoatSex.FEMALE) }
    var famachaScore by remember { mutableStateOf("") }
    var famachaDay by remember { mutableStateOf("") }
    var milkLitres by remember { mutableStateOf("") }
    var milkDay by remember { mutableStateOf("") }
    var bcsTenths by remember { mutableStateOf("") }
    var bcsDay by remember { mutableStateOf("") }
    var sccCells by remember { mutableStateOf("") }
    var sccDim by remember { mutableStateOf("") }
    var sccDay by remember { mutableStateOf("") }
    var heatDay by remember { mutableStateOf("") }
    var matingMethod by remember { mutableStateOf("natural") }
    var sireId by remember { mutableStateOf("") }
    var matingDay by remember { mutableStateOf("") }
    var pregResult by remember { mutableStateOf("pregnant") }
    var pregDay by remember { mutableStateOf("") }
    var lactationDay by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var herdFilter by remember { mutableStateOf(GoatHerdFilter.ACTIVE) }
    var pendingStatus by remember { mutableStateOf<GoatStatus?>(null) }

    val selected = state.selected
    val filteredHerd = state.herd.filter { herdFilter.matches(it.status) }
    val activeCount = state.herd.count { it.status == GoatStatus.ACTIVE }
    val femaleCount = state.herd.count { it.sex == GoatSex.FEMALE }
    val captureEnabled = !state.busy

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(FosDimens.ScreenMargin),
        verticalArrangement = Arrangement.spacedBy(FosDimens.IntraCardGap),
    ) {
        Text(state.farmName ?: "Goat herd", style = MaterialTheme.typography.titleLarge)
        Text(
            "Capture stays on this device until sync. Historical weights remain after a lifecycle change.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(FosDimens.SectionGap)) {
            KpiTile(label = "Active", value = activeCount.toString())
            KpiTile(label = "Female", value = femaleCount.toString())
            KpiTile(label = "Pending", value = state.pendingSyncCount.toString())
        }

        HorizontalDivider()
        Text("Herd", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(FosDimens.Grid)) {
            listOf(GoatHerdFilter.ALL, GoatHerdFilter.ACTIVE).forEach { filter ->
                TextButton(
                    onClick = { herdFilter = filter },
                    enabled = !state.busy,
                ) {
                    Text(if (herdFilter == filter) "${filter.label} selected" else filter.label)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FosDimens.Grid)) {
            listOf(GoatHerdFilter.SOLD, GoatHerdFilter.DEAD, GoatHerdFilter.CULLED).forEach { filter ->
                TextButton(
                    onClick = { herdFilter = filter },
                    enabled = !state.busy,
                ) {
                    Text(if (herdFilter == filter) "${filter.label} selected" else filter.label)
                }
            }
        }
        when (state.herdState) {
            LoadableSurfaceState.LOADING -> Text("Loading herd")
            LoadableSurfaceState.DISABLED -> Text("Herd actions are paused while this device works")
            LoadableSurfaceState.ERROR -> Text(
                state.error ?: "Herd could not be loaded. Retry sync, then open the list again.",
                color = MaterialTheme.colorScheme.error,
            )
            LoadableSurfaceState.EMPTY -> Text("No goats on this device. Register a goat to start the herd record.")
            LoadableSurfaceState.IDLE -> {
                if (filteredHerd.isEmpty()) {
                    Text("No ${herdFilter.label.lowercase()} goats on this device.")
                } else {
                    filteredHerd.forEach { goat ->
                        TextButton(
                            onClick = { onSelectGoat(goat.animalId) },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(herdRowLabel(goat, selected = goat.animalId == state.animalId))
                        }
                    }
                }
            }
        }

        HorizontalDivider()
        Text("Profile", style = MaterialTheme.typography.labelLarge)
        if (selected == null) {
            Text("Select a goat from the herd or search before recording weight.")
        } else {
            Text(profileSummary(selected))
            selected.dateOfBirthEpochDay?.let { day ->
                Text("Born ${LocalDate.ofEpochDay(day)}")
            }
            selected.averageDailyGainGrams?.let { adg ->
                Text("Average daily gain $adg g/day")
            } ?: Text("Average daily gain needs 2 chronological weights.")
            if (selected.weightHistory.isEmpty()) {
                Text("No weights on this goat yet.")
            } else {
                selected.weightHistory.asReversed().forEach { sample ->
                    Text(weightRowLabel(sample), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (selected.bcsHistory.isEmpty()) {
                Text("No BCS scores on this goat yet.")
            } else {
                selected.bcsHistory.forEach { score ->
                    Text("${LocalDate.ofEpochDay(score.occurredEpochDay)}  BCS ${score.scoreTenths / 10}.${score.scoreTenths % 10}")
                }
            }
            if (selected.sccHistory.isEmpty()) {
                Text("No SCC counts on this goat yet.")
            } else {
                selected.sccHistory.forEach { row ->
                    Text("${LocalDate.ofEpochDay(row.occurredEpochDay)}  ${row.cellsPerMl} cells/ml")
                }
            }
            if (selected.famachaHistory.isEmpty()) {
                Text("No FAMACHA scores on this goat yet.")
            } else {
                selected.famachaHistory.forEach { score ->
                    Text("${LocalDate.ofEpochDay(score.occurredEpochDay)}  FAMACHA ${score.score}")
                }
            }
            if (selected.sex == GoatSex.FEMALE) {
                if (selected.milkHistory.isEmpty()) {
                    Text("No milk records on this doe yet.")
                } else {
                    selected.milkHistory.forEach { row ->
                        Text("${LocalDate.ofEpochDay(row.occurredEpochDay)}  ${row.litresMilli} ml")
                    }
                }
                if (selected.kiddingHistory.isEmpty()) {
                    Text("No kidding events on this doe yet.")
                } else {
                    selected.kiddingHistory.forEach { event ->
                        Text(
                            "${LocalDate.ofEpochDay(event.occurredEpochDay)}  ${event.liveCount} live · ${event.deadCount} dead · ${event.bornCount} born",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (selected.status == GoatStatus.ACTIVE) {
                Text(
                    "Sold, deceased, or culled goats leave the active herd. Weights already saved stay on the record.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(FosDimens.Grid)) {
                    TextButton(
                        enabled = captureEnabled,
                        onClick = { pendingStatus = GoatStatus.SOLD },
                    ) { Text("Mark sold") }
                    TextButton(
                        enabled = captureEnabled,
                        onClick = { pendingStatus = GoatStatus.DEAD },
                    ) { Text("Mark deceased") }
                    TextButton(
                        enabled = captureEnabled,
                        onClick = { pendingStatus = GoatStatus.CULLED },
                    ) { Text("Mark culled") }
                }
                pendingStatus?.let { nextStatus ->
                    Text("Confirm ${statusLabel(nextStatus).lowercase()}: this goat leaves the active herd.")
                    Button(
                        enabled = captureEnabled,
                        onClick = {
                            onSetStatus(nextStatus)
                            pendingStatus = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Confirm status change")
                    }
                    TextButton(onClick = { pendingStatus = null }, enabled = captureEnabled) {
                        Text("Cancel status change")
                    }
                }
            }
        }

        HorizontalDivider()
        Text("Register goat", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = tag,
            onValueChange = { tag = it },
            label = { Text("Tag") },
            modifier = Modifier.fillMaxWidth(),
            enabled = captureEnabled,
            singleLine = true,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name (optional)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = captureEnabled,
            singleLine = true,
        )
        OutlinedTextField(
            value = dateOfBirth,
            onValueChange = { dateOfBirth = it },
            label = { Text("Date of birth") },
            placeholder = { Text("YYYY-MM-DD") },
            modifier = Modifier.fillMaxWidth(),
            enabled = captureEnabled,
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(FosDimens.Grid)) {
            TextButton(enabled = captureEnabled, onClick = { sex = GoatSex.FEMALE }) {
                Text(if (sex == GoatSex.FEMALE) "Female selected" else "Female")
            }
            TextButton(enabled = captureEnabled, onClick = { sex = GoatSex.MALE }) {
                Text(if (sex == GoatSex.MALE) "Male selected" else "Male")
            }
        }
        Button(
            enabled = captureEnabled && tag.isNotBlank(),
            onClick = { onRegister(tag, name.trim().ifBlank { null }, sex, dateOfBirth) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Register goat")
        }

        HorizontalDivider()
        Text("Weight", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = weight,
            onValueChange = { weight = it },
            label = { Text("Weight") },
            suffix = { Text("kg") },
            modifier = Modifier.fillMaxWidth(),
            enabled = captureEnabled && selected?.status == GoatStatus.ACTIVE,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Button(
            enabled = captureEnabled && selected?.status == GoatStatus.ACTIVE && weight.isNotBlank(),
            onClick = { onRecordWeight(weight) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Record weight")
        }

        if (selected?.sex == GoatSex.FEMALE && selected.status == GoatStatus.ACTIVE) {
            HorizontalDivider()
            Text("Kidding", style = MaterialTheme.typography.labelLarge)
            Text("Record born, live, and dead counts. Live plus dead must equal born.")
            OutlinedTextField(
                value = bornCount,
                onValueChange = { bornCount = it },
                label = { Text("Born") },
                modifier = Modifier.fillMaxWidth(),
                enabled = captureEnabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = liveCount,
                onValueChange = { liveCount = it },
                label = { Text("Live") },
                modifier = Modifier.fillMaxWidth(),
                enabled = captureEnabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = deadCount,
                onValueChange = { deadCount = it },
                label = { Text("Dead") },
                modifier = Modifier.fillMaxWidth(),
                enabled = captureEnabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = kiddingDay,
                onValueChange = { kiddingDay = it },
                label = { Text("Kidding date") },
                placeholder = { Text("YYYY-MM-DD") },
                modifier = Modifier.fillMaxWidth(),
                enabled = captureEnabled,
                singleLine = true,
            )
            Button(
                enabled = captureEnabled && bornCount.isNotBlank() && liveCount.isNotBlank() && kiddingDay.isNotBlank(),
                onClick = { onRecordKidding(bornCount, liveCount, deadCount, kiddingDay) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Record kidding")
            }
            val latestKidding = selected.kiddingHistory.firstOrNull()
            Text("Kid records cannot exceed live kids from that kidding. Dam pedigree is recorded only.")
            if (latestKidding == null) {
                Text("Record kidding first, then add each live kid.")
            }
            OutlinedTextField(
                value = kidTag,
                onValueChange = { kidTag = it },
                label = { Text("Kid tag") },
                modifier = Modifier.fillMaxWidth(),
                enabled = captureEnabled && latestKidding != null,
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(FosDimens.IntraCardGap)) {
                Button(
                    enabled = captureEnabled && latestKidding != null,
                    onClick = { kidSex = GoatSex.FEMALE },
                ) { Text("Doe kid") }
                Button(
                    enabled = captureEnabled && latestKidding != null,
                    onClick = { kidSex = GoatSex.MALE },
                ) { Text("Buck kid") }
            }
            Button(
                enabled = captureEnabled && latestKidding != null && kidTag.isNotBlank(),
                onClick = { onRegisterKid(latestKidding!!.kiddingId, kidTag, kidSex) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Register kid") }
        }

        if (selected?.status == GoatStatus.ACTIVE) {
            HorizontalDivider()
            Text("FAMACHA", style = MaterialTheme.typography.labelLarge)
            Text("Score 1 to 5 from the eyelid card. This does not start a drench.")
            OutlinedTextField(
                value = famachaScore,
                onValueChange = { famachaScore = it },
                label = { Text("Score") },
                modifier = Modifier.fillMaxWidth(),
                enabled = captureEnabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = famachaDay,
                onValueChange = { famachaDay = it },
                label = { Text("Score date") },
                placeholder = { Text("YYYY-MM-DD") },
                modifier = Modifier.fillMaxWidth(),
                enabled = captureEnabled,
                singleLine = true,
            )
            Button(
                enabled = captureEnabled && famachaScore.isNotBlank() && famachaDay.isNotBlank(),
                onClick = { onRecordFamacha(famachaScore, famachaDay) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Record FAMACHA") }
            Text("BCS 1 to 5, stored as tenths. Palpate the loin. This is not a diagnosis.")
            OutlinedTextField(
                value = bcsTenths,
                onValueChange = { bcsTenths = it },
                label = { Text("BCS tenths") },
                modifier = Modifier.fillMaxWidth(),
                enabled = captureEnabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = bcsDay,
                onValueChange = { bcsDay = it },
                label = { Text("BCS date") },
                placeholder = { Text("YYYY-MM-DD") },
                modifier = Modifier.fillMaxWidth(),
                enabled = captureEnabled,
                singleLine = true,
            )
            Button(
                enabled = captureEnabled && bcsTenths.isNotBlank() && bcsDay.isNotBlank(),
                onClick = { onRecordBcs(bcsTenths, bcsDay) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Record BCS") }
            Text("SCC is a cell count, not a diagnosis.")
            OutlinedTextField(
                value = sccCells,
                onValueChange = { sccCells = it },
                label = { Text("SCC cells/ml") },
                modifier = Modifier.fillMaxWidth(),
                enabled = captureEnabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = sccDim,
                onValueChange = { sccDim = it },
                label = { Text("DIM days") },
                modifier = Modifier.fillMaxWidth(),
                enabled = captureEnabled,
                singleLine = true,
            )
            OutlinedTextField(
                value = sccDay,
                onValueChange = { sccDay = it },
                label = { Text("SCC date") },
                placeholder = { Text("YYYY-MM-DD") },
                modifier = Modifier.fillMaxWidth(),
                enabled = captureEnabled,
                singleLine = true,
            )
            Button(
                enabled = captureEnabled && sccCells.isNotBlank() && sccDay.isNotBlank(),
                onClick = { onRecordScc(sccCells, sccDim, sccDay) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Record SCC") }
        }

        if (selected?.status == GoatStatus.ACTIVE && selected.sex == GoatSex.FEMALE) {
            HorizontalDivider()
            Text("Milk", style = MaterialTheme.typography.labelLarge)
            Text("Enter litres as a figure. The device stores milli-litres.")
            OutlinedTextField(
                value = milkLitres,
                onValueChange = { milkLitres = it },
                label = { Text("Litres") },
                modifier = Modifier.fillMaxWidth(),
                enabled = captureEnabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(
                value = milkDay,
                onValueChange = { milkDay = it },
                label = { Text("Milk date") },
                placeholder = { Text("YYYY-MM-DD") },
                modifier = Modifier.fillMaxWidth(),
                enabled = captureEnabled,
                singleLine = true,
            )
            Button(
                enabled = captureEnabled && milkLitres.isNotBlank() && milkDay.isNotBlank(),
                onClick = { onRecordMilk(milkLitres, milkDay) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Record milk") }
            Text("Heat, mating, and pregnancy check. Mating creates a +45 day pregnancy-check task. This is not a diagnosis.")
            OutlinedTextField(
                value = heatDay,
                onValueChange = { heatDay = it },
                label = { Text("Heat date") },
                placeholder = { Text("YYYY-MM-DD") },
                modifier = Modifier.fillMaxWidth(),
                enabled = captureEnabled,
                singleLine = true,
            )
            Button(
                enabled = captureEnabled && heatDay.isNotBlank(),
                onClick = { onRecordHeat(heatDay) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Record heat") }
            OutlinedTextField(
                value = matingMethod,
                onValueChange = { matingMethod = it },
                label = { Text("Mating method") },
                modifier = Modifier.fillMaxWidth(),
                enabled = captureEnabled,
                singleLine = true,
            )
            OutlinedTextField(
                value = sireId,
                onValueChange = { sireId = it },
                label = { Text("Sire id") },
                modifier = Modifier.fillMaxWidth(),
                enabled = captureEnabled,
                singleLine = true,
            )
            OutlinedTextField(
                value = matingDay,
                onValueChange = { matingDay = it },
                label = { Text("Mating date") },
                placeholder = { Text("YYYY-MM-DD") },
                modifier = Modifier.fillMaxWidth(),
                enabled = captureEnabled,
                singleLine = true,
            )
            Button(
                enabled = captureEnabled && matingDay.isNotBlank(),
                onClick = { onRecordMating(matingMethod, sireId, matingDay) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Record mating") }
            OutlinedTextField(
                value = pregResult,
                onValueChange = { pregResult = it },
                label = { Text("Pregnancy result") },
                modifier = Modifier.fillMaxWidth(),
                enabled = captureEnabled,
                singleLine = true,
            )
            OutlinedTextField(
                value = pregDay,
                onValueChange = { pregDay = it },
                label = { Text("Check date") },
                placeholder = { Text("YYYY-MM-DD") },
                modifier = Modifier.fillMaxWidth(),
                enabled = captureEnabled,
                singleLine = true,
            )
            Button(
                enabled = captureEnabled && pregDay.isNotBlank(),
                onClick = { onRecordPregnancy(pregResult, pregDay) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Record pregnancy check") }
            Text("Lactation follow-up creates a +7 day LACTATION_CHECK task after kidding.")
            OutlinedTextField(
                value = lactationDay,
                onValueChange = { lactationDay = it },
                label = { Text("Kidding date") },
                placeholder = { Text("YYYY-MM-DD") },
                modifier = Modifier.fillMaxWidth(),
                enabled = captureEnabled,
                singleLine = true,
            )
            Button(
                enabled = captureEnabled && lactationDay.isNotBlank(),
                onClick = { onPlanLactation(lactationDay) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Plan lactation follow-up") }
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

        HorizontalDivider()
        Text("Search", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Tag or name") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
            singleLine = true,
        )
        Button(
            enabled = !state.busy,
            onClick = { onSearch(searchQuery) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Search records")
        }
        Text(state.searchMessage, style = MaterialTheme.typography.bodySmall)
        state.searchResults.take(8).forEach { result ->
            TextButton(
                onClick = { onSelectGoat(result.animalId) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    buildString {
                        append(result.tag)
                        result.name?.let { append(" · ").append(it) }
                        append(" · ").append(statusLabel(result.status))
                        append(" · ").append(result.source.name.lowercase())
                    },
                )
            }
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        TextButton(onClick = onBack, enabled = !state.busy) {
            Text("Back to farm home")
        }
        TextButton(onClick = onSignOut, enabled = !state.busy) {
            Text("Sign out")
        }
        Spacer(Modifier.height(FosDimens.SectionGap))
    }
}

@Composable
private fun KpiTile(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

private fun herdRowLabel(goat: GoatSnapshot, selected: Boolean): String = buildString {
    append(goat.tag)
    goat.name?.let { append(" · ").append(it) }
    append(" · ").append(statusLabel(goat.status))
    goat.latestWeightGrams?.let { append(" · ").append(formatKg(it)).append(" kg") }
    if (goat.syncPending) append(" · waiting to sync")
    if (selected) append(" · open")
}

private fun profileSummary(goat: GoatSnapshot): String = buildString {
    append(goat.tag)
    goat.name?.let { append(" · ").append(it) }
    append(" · ").append(goat.sex.name.lowercase())
    append(" · ").append(statusLabel(goat.status))
    goat.latestWeightGrams?.let { append(" · ").append(formatKg(it)).append(" kg") }
    if (goat.syncPending) append(" · waiting to sync")
}

private fun weightRowLabel(sample: WeightSample): String =
    "${Instant.ofEpochMilli(sample.measuredAtEpochMillis).toString().take(10)}  ${formatKg(sample.weightGrams)} kg"

private fun formatKg(grams: Long): String = "%.2f".format(grams / 1_000.0)

private fun statusLabel(status: GoatStatus): String = when (status) {
    GoatStatus.ACTIVE -> "active"
    GoatStatus.SOLD -> "sold"
    GoatStatus.DEAD -> "deceased"
    GoatStatus.CULLED -> "culled"
    GoatStatus.CLOSED -> "closed"
}

private fun statusLabel(raw: String): String =
    runCatching { statusLabel(GoatStatus.fromWire(raw)) }.getOrDefault(raw)

data class GoatSliceUiState(
    val farmName: String? = null,
    val herd: List<GoatSnapshot> = emptyList(),
    val herdState: LoadableSurfaceState = LoadableSurfaceState.IDLE,
    val selected: GoatSnapshot? = null,
    val animalId: String? = null,
    val pendingSyncCount: Long = 0,
    val goatSummary: String? = null,
    val syncMessage: String = "No local changes yet",
    val searchMessage: String = "Local search is always available",
    val searchResults: List<GoatSearchResult> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
)

enum class LoadableSurfaceState { IDLE, LOADING, EMPTY, ERROR, DISABLED }

enum class GoatHerdFilter {
    ALL,
    ACTIVE,
    SOLD,
    DEAD,
    CULLED,
    ;

    val label: String
        get() = when (this) {
            ALL -> "All"
            ACTIVE -> "Active"
            SOLD -> "Sold"
            DEAD -> "Deceased"
            CULLED -> "Culled"
        }

    fun matches(status: GoatStatus): Boolean = when (this) {
        ALL -> true
        ACTIVE -> status == GoatStatus.ACTIVE
        SOLD -> status == GoatStatus.SOLD
        DEAD -> status == GoatStatus.DEAD
        CULLED -> status == GoatStatus.CULLED
    }
}

@Preview(showBackground = true)
@Composable
private fun GoatHerdPreview() {
    val nala = GoatSnapshot(
        animalId = "nala-001",
        farmId = "farm-nala",
        tag = "NALA-01",
        name = "Nala",
        sex = GoatSex.FEMALE,
        status = GoatStatus.ACTIVE,
        dateOfBirthEpochDay = 19_723,
        latestWeightGrams = 32_450,
        averageDailyGainGrams = 120,
        weightHistory = listOf(
            WeightSample("w1", 30_000, 1_700_000_000_000L),
            WeightSample("w2", 32_450, 1_700_864_000_000L),
        ),
        syncPending = true,
    )
    FarmOsTheme {
        GoatVerticalSliceScreen(
            state = GoatSliceUiState(
                farmName = "Nala herd",
                herd = listOf(nala),
                herdState = LoadableSurfaceState.IDLE,
                selected = nala,
                animalId = nala.animalId,
                pendingSyncCount = 1,
                goatSummary = "NALA-01 · female · 32.45 kg",
                syncMessage = "Saved on this device · waiting to sync",
            ),
            onRegister = { _, _, _, _ -> },
            onRecordWeight = {},
            onRecordKidding = { _, _, _, _ -> },
            onRegisterKid = { _, _, _ -> },
            onRecordFamacha = { _, _ -> },
            onRecordMilk = { _, _ -> },
            onRecordBcs = { _, _ -> },
            onRecordScc = { _, _, _ -> },
            onRecordHeat = {},
            onRecordMating = { _, _, _ -> },
            onRecordPregnancy = { _, _ -> },
            onPlanLactation = {},
            onSetStatus = {},
            onSelectGoat = {},
            onSyncNow = {},
            onSearch = {},
            onSignOut = {},
        )
    }
}
