package com.farmos.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.farmos.core.database.OutboxEntity
import com.farmos.core.design.AnimalFarmCanvas
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmHomeMetrics
import com.farmos.core.design.AnimalFarmModuleHeader
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.AnimalFarmWarningSurface
import com.farmos.core.design.FarmIllustratedSectionSurface
import com.farmos.core.model.SyncState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Read-only outbox queue views. Each view lists this farm's local mutations in one sync state.
 * Nothing here changes outbox state: retry scheduling stays with the sync engine, and conflict or
 * rejection resolution requires its own governed command.
 */
enum class SyncQueueView(
    val state: SyncState,
    val screenId: String,
    val title: String,
    val subtitle: String,
    val emptyMessage: String,
) {
    IN_FLIGHT(
        SyncState.IN_FLIGHT,
        "FOS-SYNC-004",
        "Sending now",
        "Changes claimed by the current sync run",
        "No changes are being sent right now.",
    ),
    RETRY_WAITING(
        SyncState.RETRY_WAIT,
        "FOS-SYNC-005",
        "Retry waiting",
        "Saved on this device; sync retries automatically",
        "No changes are waiting to retry.",
    ),
    CONFLICTS(
        SyncState.CONFLICT,
        "FOS-SYNC-006",
        "Conflict centre",
        "The farm record changed before these changes arrived",
        "No sync conflicts.",
    ),
    REJECTED(
        SyncState.REJECTED,
        "FOS-SYNC-008",
        "Rejected changes",
        "The server did not accept these changes",
        "No rejected changes.",
    ),
    ;

    val shortLabel: String
        get() = when (this) {
            IN_FLIGHT -> "Sending"
            RETRY_WAITING -> "Retry waiting"
            CONFLICTS -> "Conflicts"
            REJECTED -> "Rejected"
        }
}

internal data class SyncQueueRow(
    val mutationId: String,
    val command: String,
    val subject: String,
    val savedAt: String,
    val attempts: Int,
    val reason: String?,
    val timingLabel: String?,
)

internal sealed interface SyncQueueUiState {
    data object Loading : SyncQueueUiState
    data object Denied : SyncQueueUiState
    data class Failed(val message: String) : SyncQueueUiState
    data class Loaded(val rows: List<SyncQueueRow>) : SyncQueueUiState
}

internal const val SYNC_QUEUE_LIMIT = 100

/** Only farm roles that operate records see sync internals; buyer/read-only fail closed. */
internal fun syncQueuesPermitted(role: String): Boolean = resolveFarmHomePersona(role) != FarmHomePersona.BUYER

internal fun syncQueueCounts(counts: Map<String, Long>): Map<SyncQueueView, Long> =
    SyncQueueView.entries.associateWith { counts[it.state.name] ?: 0L }

private val syncTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

internal fun syncTime(epochMillis: Long, zone: ZoneId): String = syncTimeFormat.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

internal fun syncReasonLabel(code: String?): String? = when (code) {
    null -> null
    "CONFLICT" -> "Farm record changed on the server"
    "VALIDATION_REJECTED" -> "Failed server validation"
    "AUTH_REJECTED" -> "Not authorised for this farm"
    "STALE_CLIENT" -> "App version no longer accepted"
    "TEMPORARY_FAILURE" -> "Server temporarily unavailable"
    "TRANSPORT_FAILURE" -> "Network unavailable"
    "AUTH_SESSION_REQUIRED" -> "Sign-in required"
    else -> code
}

internal fun OutboxEntity.toSyncQueueRow(view: SyncQueueView, zone: ZoneId): SyncQueueRow {
    require(state == view.state.name) { "Row $mutationId is $state, not ${view.state.name}" }
    val timing = nextAttemptAtEpochMillis?.let {
        when (view) {
            SyncQueueView.IN_FLIGHT -> "Claimed until ${syncTime(it, zone)}"
            SyncQueueView.RETRY_WAITING -> "Next attempt after ${syncTime(it, zone)}"
            else -> null
        }
    }
    return SyncQueueRow(
        mutationId = mutationId,
        command = commandName,
        subject = "$aggregateType · $aggregateId",
        savedAt = syncTime(createdAtEpochMillis, zone),
        attempts = attemptCount,
        reason = syncReasonLabel(lastErrorCode),
        timingLabel = timing,
    )
}

@Composable
internal fun SyncQueueHost(
    view: SyncQueueView,
    permitted: Boolean,
    loadRows: suspend (SyncQueueView) -> List<OutboxEntity>,
    onSelectView: (SyncQueueView) -> Unit,
    onBack: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    var state by remember(view, permitted) {
        mutableStateOf<SyncQueueUiState>(if (permitted) SyncQueueUiState.Loading else SyncQueueUiState.Denied)
    }
    LaunchedEffect(view, permitted) {
        if (!permitted) return@LaunchedEffect
        state = runCatching { loadRows(view).map { it.toSyncQueueRow(view, zone) } }
            .fold({ SyncQueueUiState.Loaded(it) }, { SyncQueueUiState.Failed(it.message ?: "Sync queue could not be loaded") })
    }
    SyncQueueScreen(view, state, onSelectView, onBack)
}

@Composable
internal fun SyncQueueScreen(
    view: SyncQueueView,
    state: SyncQueueUiState,
    onSelectView: (SyncQueueView) -> Unit,
    onBack: () -> Unit,
) {
    AnimalFarmCanvas(Modifier.testTag("farm-screen:${view.screenId}")) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(AnimalFarmHomeMetrics.pageInset),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AnimalFarmModuleHeader(view.title, view.subtitle)
            if (state != SyncQueueUiState.Denied) SyncQueueSwitcher(view, onSelectView)
            when (state) {
                SyncQueueUiState.Loading -> Text("Loading sync queue")
                SyncQueueUiState.Denied -> AnimalFarmEmptyState("Sync queues are not available for your farm role.")
                is SyncQueueUiState.Failed -> AnimalFarmWarningSurface { Text(state.message) }
                is SyncQueueUiState.Loaded -> SyncQueueContent(view, state.rows)
            }
            TextButton(
                onClick = onBack,
                modifier = Modifier.heightIn(min = AnimalFarmTheme.minimumTouchDp.dp),
            ) { Text("Farm home") }
        }
    }
}

@Composable
private fun SyncQueueSwitcher(current: SyncQueueView, onSelectView: (SyncQueueView) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SyncQueueView.entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { option ->
                    val selected = option == current
                    TextButton(
                        onClick = { if (!selected) onSelectView(option) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                            .semantics { this.selected = selected },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) {
                        Text(
                            option.shortLabel,
                            maxLines = 1,
                            softWrap = false,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
                repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SyncQueueContent(view: SyncQueueView, rows: List<SyncQueueRow>) {
    if (rows.isEmpty()) {
        AnimalFarmEmptyState(view.emptyMessage)
        return
    }
    if (view == SyncQueueView.CONFLICTS || view == SyncQueueView.REJECTED) {
        AnimalFarmWarningSurface {
            Text("Kept on this device and not applied to the farm record.")
        }
    }
    val countLabel = if (rows.size >= SYNC_QUEUE_LIMIT) "Latest $SYNC_QUEUE_LIMIT changes" else "${rows.size} change(s)"
    Text(countLabel, color = AnimalFarmTheme.colors.mutedInk)
    FarmIllustratedSectionSurface {
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider()
            Column(
                Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("sync-queue-row:${row.mutationId}"),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(row.command, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(row.subject)
                Text("Saved ${row.savedAt} · ${row.attempts} attempt(s)", color = AnimalFarmTheme.colors.mutedInk)
                row.reason?.let { Text(it) }
                row.timingLabel?.let { Text(it, color = AnimalFarmTheme.colors.mutedInk) }
            }
        }
    }
}
