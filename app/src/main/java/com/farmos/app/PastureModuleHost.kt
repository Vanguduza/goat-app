package com.farmos.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.CreatePaddock
import com.farmos.domain.ops.EndGrazing
import com.farmos.domain.ops.StartGrazing
import com.farmos.feature.ops.SimpleCaptureScreen
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

/** Dedicated pasture/grazing orchestration boundary. */
@Composable
fun PastureModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var paddockRows by remember { mutableStateOf(emptyList<String>()) }
    var grazingRows by remember { mutableStateOf(emptyList<String>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        paddockRows = ops.paddocks().map { "${it.id} ${it.code} · ${it.displayName} · ${it.waterSource}" }
        grazingRows = ops.openGrazing().map { "${it.id} paddock ${it.paddockId} · group ${it.groupId}" }
    }

    LaunchedEffect(farmId) { runCatching { refresh() } }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            runCatching {
                block()
                refresh()
            }.onSuccess { enqueueSync() }
                .onFailure { error = it.message }
            busy = false
        }
    }

    val code = remember { mutableStateOf("") }
    val display = remember { mutableStateOf("") }
    val water = remember { mutableStateOf("trough") }
    val paddockId = remember { mutableStateOf("") }
    val groupId = remember { mutableStateOf("") }
    val heads = remember { mutableStateOf("") }
    val entered = remember { mutableStateOf("") }
    val sessionId = remember { mutableStateOf("") }
    val exited = remember { mutableStateOf("") }

    SimpleCaptureScreen(
        screenId = "FOS-PASTURE-001",
        title = "Pasture",
        help = "One group grazes one paddock at a time. Rest starts when the session ends.",
        empty = "No paddocks on this device.",
        rows = paddockRows + grazingRows,
        busy = busy,
        error = error,
        fields = listOf("Code" to code, "Name" to display, "Water source" to water),
        actionLabel = "Create paddock",
        onSubmit = {
            run {
                ops.createPaddock(
                    CreatePaddock(UUID.randomUUID().toString(), code.value, display.value.ifBlank { code.value }, waterSource = water.value, shade = true),
                    newContext(),
                )
            }
        },
        onBack = onBack,
        extra = {
            androidx.compose.material3.OutlinedTextField(paddockId.value, { paddockId.value = it }, label = { androidx.compose.material3.Text("Paddock id") }, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(groupId.value, { groupId.value = it }, label = { androidx.compose.material3.Text("Group id") }, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(heads.value, { heads.value = it }, label = { androidx.compose.material3.Text("Head count") }, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(entered.value, { entered.value = it }, label = { androidx.compose.material3.Text("Enter date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.Button(
                onClick = {
                    run {
                        ops.startGrazing(
                            StartGrazing(UUID.randomUUID().toString(), paddockId.value, groupId.value, heads.value.toIntOrNull() ?: 0, LocalDate.parse(entered.value).toEpochDay()),
                            newContext(),
                        )
                    }
                },
                enabled = !busy && paddockId.value.isNotBlank() && groupId.value.isNotBlank() && entered.value.isNotBlank(),
            ) { androidx.compose.material3.Text("Start grazing") }
            androidx.compose.material3.OutlinedTextField(sessionId.value, { sessionId.value = it }, label = { androidx.compose.material3.Text("Session id") }, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(exited.value, { exited.value = it }, label = { androidx.compose.material3.Text("Exit date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.Button(
                onClick = { run { ops.endGrazing(EndGrazing(sessionId.value, LocalDate.parse(exited.value).toEpochDay()), newContext()) } },
                enabled = !busy && sessionId.value.isNotBlank() && exited.value.isNotBlank(),
            ) { androidx.compose.material3.Text("End grazing") }
        },
    )
}
