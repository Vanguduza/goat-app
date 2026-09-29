package com.farmos.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmCanvas
import com.farmos.core.design.AnimalFarmHomeMetrics
import com.farmos.core.design.AnimalFarmModuleHeader
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.AnimalFarmWarningSurface
import com.farmos.core.design.FarmIllustratedSectionSurface
import com.farmos.core.network.MutationTraceResponse
import kotlinx.coroutines.launch

/** One search-index job the farm server queued for an applied change. */
internal data class ServerSearchJob(val jobId: Long, val state: String, val attempts: Int, val errorRecorded: Boolean)

/**
 * The farm server's read-only answer for one mutation, from the member-gated `mutation_trace_v1`
 * RPC. Times are shown exactly as the server returned them.
 */
internal sealed interface ServerMutationTrace {
    data class Applied(
        val commandName: String?,
        val appliedAt: String?,
        val eventType: String?,
        val streamVersion: Long?,
        val changeCursor: Long?,
        val recordedAt: String?,
        val searchJobs: List<ServerSearchJob>,
    ) : ServerMutationTrace
    data object NotFound : ServerMutationTrace
    data class Refused(val message: String) : ServerMutationTrace
}

internal fun MutationTraceResponse.toServerMutationTrace(): ServerMutationTrace = when (code) {
    "FOUND" -> ServerMutationTrace.Applied(
        commandName = commandName,
        appliedAt = appliedAt,
        eventType = event?.eventType,
        streamVersion = event?.streamVersion,
        changeCursor = event?.changeCursor,
        recordedAt = event?.recordedAt,
        searchJobs = searchJobs.map { ServerSearchJob(it.jobId, it.state, it.attempts, it.errorPresent) },
    )
    "NOT_FOUND" -> ServerMutationTrace.NotFound
    else -> ServerMutationTrace.Refused(safeMessage ?: code)
}

private sealed interface ServerTraceState {
    data object NotChecked : ServerTraceState
    data object Checking : ServerTraceState
    data class Checked(val trace: ServerMutationTrace) : ServerTraceState
    data class Failed(val message: String) : ServerTraceState
}

/**
 * FOS-SYNC-018 — one saved change exactly as this device stores it, and on request the farm
 * server's record of it. Read-only: nothing here retries, resolves or edits the change.
 */
@Composable
internal fun SyncMutationTraceScreen(
    row: SyncQueueRow,
    queue: SyncQueueView,
    traceOnServer: (suspend (String) -> ServerMutationTrace)?,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var server by remember(row.mutationId) { mutableStateOf<ServerTraceState>(ServerTraceState.NotChecked) }
    AnimalFarmCanvas(Modifier.testTag("farm-screen:FOS-SYNC-018")) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(AnimalFarmHomeMetrics.pageInset),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AnimalFarmModuleHeader("Mutation trace", "${row.command} · ${queue.title}")
            Text("On this device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            TraceLines(row.localTrace, "sync-trace-local")
            Text("Farm server", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (traceOnServer == null) {
                Text("This app is not connected to a farm server, so only the device record is shown.", color = AnimalFarmTheme.colors.mutedInk)
            } else {
                when (val current = server) {
                    ServerTraceState.NotChecked -> Unit
                    ServerTraceState.Checking -> Text("Checking the farm server")
                    is ServerTraceState.Failed -> AnimalFarmWarningSurface { Text(current.message) }
                    is ServerTraceState.Checked -> ServerTraceContent(current.trace)
                }
                TextButton(
                    onClick = {
                        server = ServerTraceState.Checking
                        scope.launch {
                            server = runCatching { traceOnServer(row.mutationId) }
                                .fold({ ServerTraceState.Checked(it) }, { ServerTraceState.Failed(it.message ?: "Farm server could not be reached") })
                        }
                    },
                    enabled = server != ServerTraceState.Checking,
                    modifier = Modifier.heightIn(min = AnimalFarmTheme.minimumTouchDp.dp),
                ) { Text(if (server is ServerTraceState.NotChecked) "Check the farm server" else "Check again") }
            }
            TextButton(
                onClick = onBack,
                modifier = Modifier.heightIn(min = AnimalFarmTheme.minimumTouchDp.dp),
            ) { Text("Back to ${queue.title}") }
        }
    }
}

@Composable
private fun ServerTraceContent(trace: ServerMutationTrace) {
    when (trace) {
        ServerMutationTrace.NotFound -> Text("The farm server has no record of this change for this farm.")
        is ServerMutationTrace.Refused -> AnimalFarmWarningSurface { Text(trace.message) }
        is ServerMutationTrace.Applied -> {
            TraceLines(
                listOf(
                    "Command" to (trace.commandName ?: "Not returned"),
                    "Applied" to (trace.appliedAt ?: "Not returned"),
                    "Event" to (trace.eventType ?: "Not returned"),
                    "Record version" to (trace.streamVersion?.toString() ?: "Not returned"),
                    "Change cursor" to (trace.changeCursor?.toString() ?: "Not returned"),
                    "Recorded" to (trace.recordedAt ?: "Not returned"),
                ),
                "sync-trace-server",
            )
            if (trace.searchJobs.isEmpty()) {
                Text("No search index jobs recorded for this change.", color = AnimalFarmTheme.colors.mutedInk)
            } else {
                TraceLines(
                    trace.searchJobs.map { job ->
                        "Search job ${job.jobId}" to "${job.state} · ${job.attempts} attempt(s)" + if (job.errorRecorded) " · error recorded" else ""
                    },
                    "sync-trace-search",
                )
            }
        }
    }
}

@Composable
private fun TraceLines(lines: List<Pair<String, String>>, tagPrefix: String) {
    FarmIllustratedSectionSurface {
        lines.forEachIndexed { index, (label, value) ->
            if (index > 0) HorizontalDivider()
            Column(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("$tagPrefix:$label"),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(label, color = AnimalFarmTheme.colors.mutedInk)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
