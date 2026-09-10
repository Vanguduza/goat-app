package com.farmos.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.CreateAnimalGroup
import com.farmos.domain.ops.RecordGroupCensus
import com.farmos.feature.ops.SimpleCaptureScreen
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

/** Dedicated group/census orchestration boundary. Species-specific production writes stay in species modules. */
@Composable
fun GroupsModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf(emptyList<String>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        rows = ops.groups().map { "${it.id} ${it.name} · ${it.speciesCode} · ${it.headCount}" }
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

    val name = remember { mutableStateOf("") }
    val species = remember { mutableStateOf("goat") }
    val heads = remember { mutableStateOf("") }
    val day = remember { mutableStateOf("") }
    val groupId = remember { mutableStateOf("") }

    SimpleCaptureScreen(
        screenId = "FOS-GROUP-001",
        title = "Groups",
        help = "Groups hold shared animal membership and census records for farm operations.",
        empty = "No groups on this device.",
        rows = rows,
        busy = busy,
        error = error,
        fields = listOf("Species" to species, "Name" to name, "Head count" to heads),
        actionLabel = "Create group",
        onSubmit = {
            run {
                ops.createGroup(
                    CreateAnimalGroup(UUID.randomUUID().toString(), species.value, name.value, heads.value.toIntOrNull() ?: 0),
                    newContext(),
                )
            }
        },
        onBack = onBack,
        extra = {
            androidx.compose.material3.OutlinedTextField(
                groupId.value,
                { groupId.value = it },
                label = { androidx.compose.material3.Text("Group id") },
                modifier = Modifier.fillMaxWidth(),
            )
            androidx.compose.material3.OutlinedTextField(
                day.value,
                { day.value = it },
                label = { androidx.compose.material3.Text("Census day") },
                placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") },
                modifier = Modifier.fillMaxWidth(),
            )
            androidx.compose.material3.OutlinedTextField(
                heads.value,
                { heads.value = it },
                label = { androidx.compose.material3.Text("Census head count") },
                modifier = Modifier.fillMaxWidth(),
            )
            androidx.compose.material3.Button(
                onClick = {
                    run {
                        ops.recordCensus(
                            RecordGroupCensus(
                                UUID.randomUUID().toString(),
                                groupId.value,
                                heads.value.toIntOrNull() ?: -1,
                                LocalDate.parse(day.value).toEpochDay(),
                            ),
                            newContext(),
                        )
                    }
                },
                enabled = !busy && groupId.value.isNotBlank() && heads.value.isNotBlank() && day.value.isNotBlank(),
            ) { androidx.compose.material3.Text("Record census") }
        },
    )
}
