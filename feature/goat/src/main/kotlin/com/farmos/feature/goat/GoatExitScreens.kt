package com.farmos.feature.goat

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.AnimalFarmWarningSurface
import com.farmos.core.design.FarmIrreversibleConfirmation
import java.time.LocalDate

/** How a goat leaves the herd (D-022). [code] matches the exit contract. */
enum class GoatExitKind(val code: String, val title: String, val screenId: String) {
    SALE("SALE", "Sale exit", "FOS-GOAT-052"),
    DEATH("DEATH", "Mortality record", "FOS-GOAT-053"),
    CULL("CULL", "Cull record", "FOS-GOAT-054"),
}

/** Death cause categories offered; they record what was observed, not a diagnosis. */
internal val goatDeathCauses = listOf(
    "ILLNESS" to "Illness",
    "INJURY" to "Injury or accident",
    "PREDATION" to "Predation",
    "BIRTH_RELATED" to "Birth-related",
    "UNKNOWN" to "Unknown",
    "OTHER" to "Other",
)

/** An exit to record. Text fields are validated and converted by the host. */
data class GoatExitDraft(
    val kind: GoatExitKind,
    val day: LocalDate,
    val deathCause: String?,
    val reason: String?,
    val buyer: String?,
    val price: String?,
)

/** The goat's standing exit, which a manager may reverse with a reason. */
data class GoatExitView(val exitId: String, val summary: String)

/** FOS-GOAT-052/053/054 — record how the selected goat left the herd, confirmed before it is written. */
@Composable
internal fun GoatExitCaptureScreen(
    kind: GoatExitKind,
    state: GoatSliceUiState,
    currency: String?,
    onRecord: (GoatExitDraft) -> Unit,
    onBack: () -> Unit,
) {
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    var cause by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }
    var buyer by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    IllustratedGoatPage(kind.title, "${kind.screenId} · I4", onBack, safety = true) {
        val goat = state.selected
        if (goat == null) {
            Text("No goat selected.")
            return@IllustratedGoatPage
        }
        AnimalFarmWarningSurface {
            Text(goatDisplayName(goat), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("The goat leaves the active herd. Its history stays on the record, and a mistake can be reversed.")
        }
        OutlinedTextField(day, { day = it }, label = { Text("Date") }, placeholder = { Text("YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("goat-exit-date"))
        when (kind) {
            GoatExitKind.DEATH -> {
                Text("What did the goat die of?", fontWeight = FontWeight.SemiBold)
                goatDeathCauses.forEach { (code, label) ->
                    val chosen = cause == code
                    TextButton(
                        onClick = { cause = code },
                        modifier = Modifier.fillMaxWidth().heightIn(min = AnimalFarmTheme.minimumTouchDp.dp).semantics { selected = chosen }.testTag("goat-exit-cause:$code"),
                    ) { Text(if (chosen) "$label · selected" else label) }
                }
                OutlinedTextField(reason, { reason = it }, label = { Text("What was seen (optional)") }, modifier = Modifier.fillMaxWidth().testTag("goat-exit-reason"))
            }
            GoatExitKind.CULL -> OutlinedTextField(reason, { reason = it }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth().testTag("goat-exit-reason"))
            GoatExitKind.SALE -> {
                OutlinedTextField(buyer, { buyer = it }, label = { Text("Buyer") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("goat-exit-buyer"))
                OutlinedTextField(price, { price = it }, label = { Text("Price${currency?.let { " ($it)" } ?: ""} (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("goat-exit-price"))
                Text("Recording the sale here does not add income; record the sale money in Finance.", color = AnimalFarmTheme.colors.mutedInk)
            }
        }
        val date = runCatching { LocalDate.parse(day) }.getOrNull()
        val ready = date != null && when (kind) {
            GoatExitKind.DEATH -> cause != null
            GoatExitKind.CULL -> reason.isNotBlank()
            GoatExitKind.SALE -> buyer.isNotBlank()
        }
        if (!confirming) {
            TextButton(onClick = { confirming = true }, enabled = ready && !state.busy, modifier = Modifier.fillMaxWidth().testTag("goat-exit-continue")) { Text("Continue") }
        } else {
            FarmIrreversibleConfirmation(
                title = "Confirm ${kind.title.lowercase()}",
                consequence = "${goatDisplayName(goat)} leaves the active herd on $day.",
                confirmLabel = "Record ${kind.title.lowercase()}",
                busy = state.busy,
                onConfirm = {
                    onRecord(GoatExitDraft(kind, date!!, cause, reason.trim().ifBlank { null }, buyer.trim().ifBlank { null }, price.trim().ifBlank { null }))
                    confirming = false
                },
                onCancel = { confirming = false },
                confirmTag = "goat-exit-confirm",
            )
        }
        state.error?.let { Text(it, color = AnimalFarmTheme.colors.critical) }
    }
}

/** The standing exit of a goat that has left, and the reversal that returns it to the herd. */
@Composable
internal fun GoatExitReversal(exit: GoatExitView, busy: Boolean, onReverse: (exitId: String, reason: String) -> Unit) {
    var reason by remember(exit.exitId) { mutableStateOf("") }
    AnimalFarmWarningSurface(Modifier.testTag("goat-standing-exit")) {
        Text(exit.summary, fontWeight = FontWeight.SemiBold)
        Text("If this was recorded in error, reverse it. Both records are kept.")
    }
    OutlinedTextField(reason, { reason = it }, label = { Text("Why is it reversed?") }, modifier = Modifier.fillMaxWidth().testTag("goat-exit-reverse-reason"))
    TextButton(onClick = { onReverse(exit.exitId, reason.trim()) }, enabled = !busy && reason.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("goat-exit-reverse")) {
        Text("Reverse and return to the herd")
    }
}
