package com.farmos.feature.rabbit

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
import com.farmos.domain.rabbit.KudbatSemiIntensiveExcel

@Composable
fun RabbitProgrammeScreen(
    cages: List<String>,
    waves: List<String>,
    availableBoxes: Long,
    busy: Boolean,
    error: String?,
    does: List<String>,
    onRegisterDoe: (tag: String, name: String?, sex: String) -> Unit,
    onCreateCage: (code: String) -> Unit,
    onCreateNestBox: (cageCode: String, boxCode: String) -> Unit,
    onCreateWave: (cageCode: String, doeCount: Int, matingDay: String) -> Unit,
    onPalpate: (waveId: String, result: String, day: String) -> Unit,
    onKindle: (waveId: String, live: String, dead: String, day: String) -> Unit,
    onFoster: (fromWaveId: String, toWaveId: String, kits: String, day: String, ackOutside: Boolean) -> Unit,
    nestBoxes: List<String> = emptyList(),
    onSetNestStatus: (boxId: String, status: String) -> Unit = { _, _ -> },
    onWean: (waveId: String, count: String, day: String) -> Unit = { _, _, _ -> },
    onRecordOutcome: (waveId: String, outcome: String, day: String) -> Unit = { _, _, _ -> },
    onRecordGiStasis: (animalId: String, signs: String, day: String) -> Unit = { _, _, _ -> },
    onBack: () -> Unit,
) {
    var doeTag by remember { mutableStateOf("") }
    var doeName by remember { mutableStateOf("") }
    var doeSex by remember { mutableStateOf("FEMALE") }
    var cageCode by remember { mutableStateOf("") }
    var boxCode by remember { mutableStateOf("") }
    var doeCount by remember { mutableStateOf("11") }
    var matingDay by remember { mutableStateOf("") }
    var waveId by remember { mutableStateOf("") }
    var palpationResult by remember { mutableStateOf("pregnant") }
    var kindlingLive by remember { mutableStateOf("") }
    var kindlingDead by remember { mutableStateOf("0") }
    var kindlingDay by remember { mutableStateOf("") }
    var fromWaveId by remember { mutableStateOf("") }
    var toWaveId by remember { mutableStateOf("") }
    var kitCount by remember { mutableStateOf("") }
    var fosterDay by remember { mutableStateOf("") }
    var ackFoster by remember { mutableStateOf(false) }
    var nestBoxId by remember { mutableStateOf("") }
    var nestStatus by remember { mutableStateOf("in_cage") }
    var weanCount by remember { mutableStateOf("") }
    var matingOutcome by remember { mutableStateOf("false_pregnancy") }
    var outcomeDay by remember { mutableStateOf("") }
    var giAnimalId by remember { mutableStateOf("") }
    var giSigns by remember { mutableStateOf("") }
    var giDay by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(FosDimens.ScreenMargin),
        verticalArrangement = Arrangement.spacedBy(FosDimens.IntraCardGap),
    ) {
        Text("Rabbit programme", style = MaterialTheme.typography.titleLarge)
        Text(
            "KudBat semi-intensive pack: nest in mating+${KudbatSemiIntensiveExcel.NEST_IN_DAYS_AFTER_MATING}, kindling +${KudbatSemiIntensiveExcel.KINDLING_DAYS_AFTER_MATING}, rebreed +${KudbatSemiIntensiveExcel.REBREED_DAYS_AFTER_MATING}. A wave of 11 does needs 11 nest boxes.",
        )
        Text("Available nest boxes on the selected cage: $availableBoxes")
        HorizontalDivider()
        Text("Does and bucks", style = MaterialTheme.typography.labelLarge)
        if (does.isEmpty()) Text("No rabbits on this device. Register a doe or buck before starting a wave.")
        does.forEach { Text(it) }
        OutlinedTextField(doeTag, { doeTag = it }, label = { Text("Tag") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(doeName, { doeName = it }, label = { Text("Name (optional)") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        Row {
            TextButton(onClick = { doeSex = "FEMALE" }, enabled = !busy) {
                Text(if (doeSex == "FEMALE") "Doe selected" else "Doe")
            }
            TextButton(onClick = { doeSex = "MALE" }, enabled = !busy) {
                Text(if (doeSex == "MALE") "Buck selected" else "Buck")
            }
        }
        Button(
            onClick = { onRegisterDoe(doeTag, doeName.trim().ifBlank { null }, doeSex) },
            enabled = !busy && doeTag.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Register rabbit") }
        HorizontalDivider()
        OutlinedTextField(cageCode, { cageCode = it }, label = { Text("Cage code") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        Button(onClick = { onCreateCage(cageCode) }, enabled = !busy && cageCode.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Create cage")
        }
        OutlinedTextField(boxCode, { boxCode = it }, label = { Text("Nest box code") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        Button(onClick = { onCreateNestBox(cageCode, boxCode) }, enabled = !busy && cageCode.isNotBlank() && boxCode.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Add nest box")
        }
        OutlinedTextField(doeCount, { doeCount = it }, label = { Text("Doe count") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(matingDay, { matingDay = it }, label = { Text("Mating date") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        Button(
            onClick = { onCreateWave(cageCode, doeCount.toIntOrNull() ?: 0, matingDay) },
            enabled = !busy && cageCode.isNotBlank() && matingDay.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Create wave")
        }
        HorizontalDivider()
        Text("Cages", style = MaterialTheme.typography.labelLarge)
        if (cages.isEmpty()) Text("No cages on this device. Create cage A, B, or C to start.")
        cages.forEach { Text(it) }
        Text("Waves", style = MaterialTheme.typography.labelLarge)
        if (waves.isEmpty()) Text("No breeding waves yet.")
        waves.forEach { Text(it) }
        HorizontalDivider()
        Text("Palpation, kindling, foster", style = MaterialTheme.typography.labelLarge)
        Text("Palpation is pregnant or open. Kindling uses live and dead kit counts. Foster after 3 days from kindling needs an acknowledgement.")
        OutlinedTextField(waveId, { waveId = it }, label = { Text("Wave id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(palpationResult, { palpationResult = it }, label = { Text("Palpation result") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(kindlingDay, { kindlingDay = it }, label = { Text("Event date") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        Button(
            onClick = { onPalpate(waveId, palpationResult, kindlingDay) },
            enabled = !busy && waveId.isNotBlank() && kindlingDay.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Record palpation") }
        OutlinedTextField(kindlingLive, { kindlingLive = it }, label = { Text("Live kits") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(kindlingDead, { kindlingDead = it }, label = { Text("Dead kits") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        Button(
            onClick = { onKindle(waveId, kindlingLive, kindlingDead, kindlingDay) },
            enabled = !busy && waveId.isNotBlank() && kindlingLive.isNotBlank() && kindlingDay.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Record kindling") }
        OutlinedTextField(fromWaveId, { fromWaveId = it }, label = { Text("From wave id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(toWaveId, { toWaveId = it }, label = { Text("To wave id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(kitCount, { kitCount = it }, label = { Text("Kit count") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(fosterDay, { fosterDay = it }, label = { Text("Foster date") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        TextButton(onClick = { ackFoster = !ackFoster }, enabled = !busy) {
            Text(if (ackFoster) "Outside-window acknowledgement on" else "Acknowledge foster outside 3-day window")
        }
        Button(
            onClick = { onFoster(fromWaveId, toWaveId, kitCount, fosterDay, ackFoster) },
            enabled = !busy && fromWaveId.isNotBlank() && toWaveId.isNotBlank() && kitCount.isNotBlank() && fosterDay.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Record foster") }
        OutlinedTextField(weanCount, { weanCount = it }, label = { Text("Weaned kits") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        Button(
            onClick = { onWean(waveId, weanCount, kindlingDay) },
            enabled = !busy && waveId.isNotBlank() && weanCount.isNotBlank() && kindlingDay.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Record wean") }
        OutlinedTextField(matingOutcome, { matingOutcome = it }, label = { Text("Mating outcome") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(outcomeDay, { outcomeDay = it }, label = { Text("Outcome date") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        Button(
            onClick = { onRecordOutcome(waveId, matingOutcome, outcomeDay) },
            enabled = !busy && waveId.isNotBlank() && outcomeDay.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Record mating outcome") }
        HorizontalDivider()
        Text("GI stasis is a red-flag observation, not a diagnosis. A vet-call task is created.")
        OutlinedTextField(giAnimalId, { giAnimalId = it }, label = { Text("Rabbit id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(giSigns, { giSigns = it }, label = { Text("Signs") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
        OutlinedTextField(giDay, { giDay = it }, label = { Text("Flag date") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        Button(
            onClick = { onRecordGiStasis(giAnimalId, giSigns, giDay) },
            enabled = !busy && giAnimalId.isNotBlank() && giSigns.isNotBlank() && giDay.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Flag GI stasis") }
        HorizontalDivider()
        Text("Nest boxes", style = MaterialTheme.typography.labelLarge)
        Text("Cycle is available or sanitized, assigned, in cage, dirty, then sanitize.")
        if (nestBoxes.isEmpty()) Text("No nest boxes on this device.")
        nestBoxes.forEach { Text(it) }
        OutlinedTextField(nestBoxId, { nestBoxId = it }, label = { Text("Nest box id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(nestStatus, { nestStatus = it }, label = { Text("Next status") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        Button(
            onClick = { onSetNestStatus(nestBoxId, nestStatus) },
            enabled = !busy && nestBoxId.isNotBlank() && nestStatus.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Set nest box status") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = onBack, enabled = !busy) { Text("Back to farm home") }
    }
}
