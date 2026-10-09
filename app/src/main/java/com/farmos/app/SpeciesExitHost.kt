package com.farmos.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.AnimalFarmWarningSurface
import com.farmos.core.design.FarmIrreversibleConfirmation
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmVisualClass
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.AnimalExitCommands
import com.farmos.domain.ops.AnimalExitKind
import com.farmos.domain.ops.DeathCause
import com.farmos.domain.ops.FarmCurrency
import com.farmos.domain.ops.RecordAnimalExit
import com.farmos.domain.ops.ReverseAnimalExit
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch
import com.farmos.core.design.runSuspendCatching

/** An exit being recorded for a sheep or cattle animal; text fields are converted on save. */
internal data class SpeciesExitDraft(val kind: AnimalExitKind, val day: LocalDate, val deathCause: String?, val reason: String?, val buyer: String?, val price: String?)

/** The standing exit shown for an animal that has left, which may be reversed. */
internal data class SpeciesStandingExit(val exitId: String, val summary: String)

/**
 * Lifecycle status for sheep and cattle (FOS-SHEEP-030 / FOS-CATTLE-034), owner decision D-022: an active
 * animal leaves through a death, cull or sale record; an animal that has left shows its exit, which can be
 * reversed with a reason. Every record is kept.
 */
@Composable
internal fun SpeciesExitHost(
    database: FarmOsDatabase,
    farmId: String,
    animal: SpeciesAnimalRow,
    /** The page's Screen ID; null embeds the exit content in the caller's own page (a rabbit profile). */
    screenId: String?,
    newContext: () -> LocalCommandContext,
    onRecorded: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val exits = remember(farmId) { AnimalExitCommands(database, farmId) }
    val currency by rememberFarmCurrency(farmId) { database.farmCurrency(farmId) }
    var standing by remember(animal.animalId) { mutableStateOf<SpeciesStandingExit?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        standing = standingAnimalExit(database, farmId, animal.animalId)?.let { SpeciesStandingExit(it.id, exitSummary(it)) }
    }
    LaunchedEffect(animal.animalId, animal.active) { runSuspendCatching { reload() } }

    fun write(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            runSuspendCatching { block(); reload() }.onSuccess { onRecorded() }.onFailure { error = it.message }
            busy = false
        }
    }

    val onRecord: (SpeciesExitDraft) -> Unit = { draft ->
        write {
            val priceMinor = draft.price?.let { price ->
                val code = requireNotNull(currency) { "The farm currency is still loading" }
                price.toScaledLongExact(FarmCurrency.minorDigits(code), "Price")
            }
            exits.record(
                RecordAnimalExit(
                    UUID.randomUUID().toString(), animal.animalId, draft.kind.name, draft.day.toEpochDay(), draft.deathCause,
                    draft.reason, draft.buyer, priceMinor, currency.takeIf { priceMinor != null },
                ),
                newContext(),
            )
        }
    }
    val onReverse: (String, String) -> Unit = { exitId, reason ->
        write { exits.reverse(ReverseAnimalExit(UUID.randomUUID().toString(), animal.animalId, exitId, reason, LocalDate.now().toEpochDay()), newContext()) }
    }
    if (screenId == null) {
        SpeciesExitContent(animal, standing, currency, busy, error, onRecord, onReverse)
        return
    }
    SpeciesExitScreen(
        screenId = screenId,
        animal = animal,
        standing = standing,
        currency = currency,
        busy = busy,
        error = error,
        onRecord = onRecord,
        onReverse = onReverse,
        onBack = onBack,
    )
}

@Composable
internal fun SpeciesExitScreen(
    screenId: String,
    animal: SpeciesAnimalRow,
    standing: SpeciesStandingExit?,
    currency: String?,
    busy: Boolean,
    error: String?,
    onRecord: (SpeciesExitDraft) -> Unit,
    onReverse: (exitId: String, reason: String) -> Unit,
    onBack: () -> Unit,
) {
    FarmOperationalPage(screenId, "Lifecycle status", animal.label, FarmVisualClass.I4, onBack) {
        SpeciesExitContent(animal, standing, currency, busy, error, onRecord, onReverse)
    }
}

/** The exit record and reversal for one animal (D-022), inside a lifecycle page or a species profile. */
@Composable
internal fun SpeciesExitContent(
    animal: SpeciesAnimalRow,
    standing: SpeciesStandingExit?,
    currency: String?,
    busy: Boolean,
    error: String?,
    onRecord: (SpeciesExitDraft) -> Unit,
    onReverse: (exitId: String, reason: String) -> Unit,
) {
    var kind by remember(animal.animalId) { mutableStateOf<AnimalExitKind?>(null) }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    var cause by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }
    var buyer by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var reversal by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!animal.active) {
            if (standing == null) {
                Text("This animal left the herd before exits were recorded; there is no exit to reverse.")
            } else {
                AnimalFarmWarningSurface(Modifier.testTag("species-standing-exit")) { Text(standing.summary) }
                OutlinedTextField(reversal, { reversal = it }, label = { Text("Why is it reversed?") }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("species-exit-reverse-reason"))
                TextButton(onClick = { onReverse(standing.exitId, reversal.trim()) }, enabled = !busy && reversal.isNotBlank(), modifier = Modifier.testTag("species-exit-reverse")) {
                    Text("Reverse and return to the herd")
                }
            }
        } else {
            FarmOperationalSection("How is it leaving?") {
                listOf(AnimalExitKind.SALE to "Sale", AnimalExitKind.DEATH to "Death", AnimalExitKind.CULL to "Cull").forEach { (option, label) ->
                    val chosen = kind == option
                    TextButton(onClick = { kind = option; confirming = false }, enabled = !busy, modifier = Modifier.fillMaxWidth().semantics { selected = chosen }.testTag("species-exit-kind:${option.name}")) {
                        Text(if (chosen) "$label · selected" else label)
                    }
                }
            }
            kind?.let { chosenKind ->
                FarmOperationalSection("Details") {
                    OutlinedTextField(day, { day = it }, label = { Text("Date") }, placeholder = { Text("YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("species-exit-date"))
                    when (chosenKind) {
                        AnimalExitKind.DEATH -> {
                            DeathCause.entries.forEach { option ->
                                val chosen = cause == option.name
                                TextButton(onClick = { cause = option.name }, modifier = Modifier.fillMaxWidth().semantics { selected = chosen }.testTag("species-exit-cause:${option.name}")) {
                                    Text(if (chosen) "${option.label} · selected" else option.label)
                                }
                            }
                            OutlinedTextField(reason, { reason = it }, label = { Text("What was seen (optional)") }, modifier = Modifier.fillMaxWidth())
                        }
                        AnimalExitKind.CULL -> OutlinedTextField(reason, { reason = it }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth().testTag("species-exit-reason"))
                        AnimalExitKind.SALE -> {
                            OutlinedTextField(buyer, { buyer = it }, label = { Text("Buyer") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("species-exit-buyer"))
                            OutlinedTextField(price, { price = it }, label = { Text("Price${currency?.let { " ($it)" } ?: ""} (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Text("Recording the sale here does not add income; record the sale money in Finance.", color = AnimalFarmTheme.colors.mutedInk)
                        }
                    }
                    val date = runCatching { LocalDate.parse(day) }.getOrNull()
                    val ready = date != null && when (chosenKind) {
                        AnimalExitKind.DEATH -> cause != null
                        AnimalExitKind.CULL -> reason.isNotBlank()
                        AnimalExitKind.SALE -> buyer.isNotBlank()
                    }
                    if (!confirming) {
                        Button(onClick = { confirming = true }, enabled = ready && !busy, modifier = Modifier.fillMaxWidth().testTag("species-exit-continue")) { Text("Continue") }
                    } else {
                        FarmIrreversibleConfirmation(
                            title = "Confirm ${chosenKind.name.lowercase()}",
                            consequence = "${animal.label} leaves the active herd on $day. A mistake can be reversed; both records are kept.",
                            confirmLabel = "Record ${chosenKind.name.lowercase()}",
                            busy = busy,
                            onConfirm = {
                                onRecord(SpeciesExitDraft(chosenKind, date!!, cause.takeIf { chosenKind == AnimalExitKind.DEATH }, reason.trim().ifBlank { null }, buyer.trim().ifBlank { null }, price.trim().ifBlank { null }))
                                confirming = false
                            },
                            onCancel = { confirming = false },
                            confirmTag = "species-exit-confirm",
                        )
                    }
                }
            }
        }
        error?.let { Text(it, color = AnimalFarmTheme.colors.critical) }
    }
}
