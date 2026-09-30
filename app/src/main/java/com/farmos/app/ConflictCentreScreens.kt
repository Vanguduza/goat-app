package com.farmos.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.database.ApplicationState
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.AnimalFarmWarningSurface
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmPermissionExplanation
import com.farmos.core.design.FarmVisualClass
import com.farmos.core.model.LocalCommandContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val conflictTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun conflictWhen(epochMillis: Long): String = conflictTime.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

private fun conflictWhy(item: ConflictItem): String = when (item.state) {
    ApplicationState.AWAITING_APPLIER.name -> "Needs a newer version of this app"
    ApplicationState.SET_ASIDE.name -> "Set aside: ${item.reason.orEmpty()}"
    else -> item.reason ?: "Not applied yet"
}

/** The Conflict Centre for one farm, reading and resolving through this device's journal (D-013). */
@Composable
internal fun ConflictCentreHost(
    database: FarmOsDatabase,
    farmId: String,
    canResolve: Boolean,
    newContext: () -> LocalCommandContext,
    onRetryAll: suspend () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var review by remember(farmId) { mutableStateOf<ConflictReview?>(null) }
    var open by remember { mutableStateOf<ConflictDetail?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    suspend fun reload() {
        review = withContext(Dispatchers.IO) { database.loadConflictReview(farmId) }
    }
    fun act(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            runCatching { withContext(Dispatchers.IO) { block() }; reload(); open = null }.onFailure { error = it.message ?: "The change could not be resolved on this device" }
            busy = false
        }
    }
    LaunchedEffect(farmId) { runCatching { reload() }.onFailure { error = it.message } }
    val detail = open
    if (detail != null) {
        ConflictDetailScreen(
            detail = detail,
            canResolve = canResolve,
            busy = busy,
            error = error,
            onRetry = { act { onRetryAll() } },
            onSetAside = { reason -> act { database.setAsideReceivedOperation(farmId, detail.item.operationId, reason, newContext()) } },
            onBack = { open = null; error = null },
        )
        return
    }
    ConflictCentreScreen(
        review = review,
        busy = busy,
        error = error,
        onOpen = { item -> scope.launch { open = withContext(Dispatchers.IO) { database.conflictDetail(farmId, item) } } },
        onBack = onBack,
    )
}

/** FOS-SYNC-006 — received changes that have not taken effect here, with who, where and why, and those set aside. */
@Composable
internal fun ConflictCentreScreen(review: ConflictReview?, busy: Boolean, error: String?, onOpen: (ConflictItem) -> Unit, onBack: () -> Unit) {
    FarmOperationalPage("FOS-SYNC-006", "Conflict Centre", "Changes from other farm devices that have not taken effect on this device.", FarmVisualClass.I3, onBack, backLabel = "Storage and backup") {
        when {
            review == null -> Text("Reading this device's journal")
            else -> {
                if (review.waiting.isEmpty()) {
                    AnimalFarmEmptyState("Every change received from other farm devices has taken effect here.")
                } else {
                    val shown = if (review.waitingTotal > review.waiting.size) "Latest ${review.waiting.size} of ${review.waitingTotal}" else "${review.waitingTotal}"
                    FarmOperationalSection("$shown waiting for review") {
                        review.waiting.forEachIndexed { index, item ->
                            if (index > 0) HorizontalDivider()
                            ConflictRow(item, !busy, onOpen)
                        }
                    }
                }
                if (review.setAside.isNotEmpty()) {
                    FarmOperationalSection("Set aside") {
                        review.setAside.forEachIndexed { index, item ->
                            if (index > 0) HorizontalDivider()
                            ConflictRow(item, !busy, onOpen)
                        }
                    }
                }
            }
        }
        error?.let { Text(it, color = AnimalFarmTheme.colors.critical) }
    }
}

@Composable
private fun ConflictRow(item: ConflictItem, enabled: Boolean, onOpen: (ConflictItem) -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
            .clickable(enabled = enabled, role = Role.Button) { onOpen(item) }
            .padding(vertical = 6.dp).testTag("conflict:${item.operationId}"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(item.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("${conflictWhen(item.businessTimeEpochMillis)} · ${item.deviceName} · ${item.actorName}", color = AnimalFarmTheme.colors.mutedInk)
        Text(conflictWhy(item))
    }
}

/**
 * FOS-SYNC-007 — one received change with its full provenance and recorded content. Management retries it or
 * sets it aside with a reason (the FOS-ATOM-027 resolution sheet); setting aside keeps it as history.
 */
@Composable
internal fun ConflictDetailScreen(
    detail: ConflictDetail,
    canResolve: Boolean,
    busy: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onSetAside: (reason: String) -> Unit,
    onBack: () -> Unit,
) {
    val item = detail.item
    var reason by remember(item.operationId) { mutableStateOf("") }
    FarmOperationalPage("FOS-SYNC-007", item.label, conflictWhy(item), FarmVisualClass.I4, onBack, backLabel = "Conflict Centre") {
        FarmOperationalSection("Where it came from") {
            Text("Recorded ${conflictWhen(item.businessTimeEpochMillis)} by ${item.actorName} on ${item.deviceName}", modifier = Modifier.testTag("conflict-provenance"))
            Text("Tried ${item.attempts} time(s) on this device.", color = AnimalFarmTheme.colors.mutedInk)
        }
        FarmOperationalSection("What it records") {
            if (detail.fields.isEmpty()) Text("The recorded content could not be read on this device.")
            detail.fields.forEach { (name, value) -> Text("$name: $value", modifier = Modifier.testTag("conflict-field:$name")) }
        }
        if (item.state == ApplicationState.SET_ASIDE.name) {
            Text("This change was set aside. It stays in this device's journal as history and is not applied.")
        } else if (!canResolve) {
            FarmPermissionExplanation("Conflicts are resolved by farm management", "Only Owner and Manager accounts can retry or set aside received changes.")
        } else {
            Column(Modifier.fillMaxWidth().testTag("farm-atom:FOS-ATOM-027"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FarmOperationalSection("Resolve") {
                    Text("Try again after recording what it depends on, or set it aside so it no longer waits. Nothing is deleted.")
                    TextButton(onClick = onRetry, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("conflict-retry")) { Text("Try again") }
                    detail.correction?.let { AnimalFarmWarningSurface { Text(it) } }
                    OutlinedTextField(reason, { reason = it }, label = { Text("Why is it set aside?") }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("conflict-reason"))
                    Button(onClick = { onSetAside(reason.trim()) }, enabled = !busy && reason.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("conflict-set-aside")) {
                        Text("Set aside")
                    }
                }
            }
        }
        error?.let { Text(it, color = AnimalFarmTheme.colors.critical) }
    }
}
