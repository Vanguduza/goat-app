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
import com.farmos.core.design.FarmEntitySelector
import com.farmos.core.design.FarmSelectionAtoms
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.CreateAnimalGroup
import com.farmos.domain.ops.FarmSpeciesCodes
import com.farmos.domain.ops.RecordGroupCensus
import com.farmos.feature.ops.GroupRecordNavigator
import com.farmos.feature.ops.GroupRecords
import com.farmos.feature.ops.GroupView
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
    loadGroups: suspend () -> List<GroupView> = { emptyList() },
    loadGroup: suspend (String) -> GroupRecords = { GroupRecords() },
) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf(emptyList<String>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var groupViews by remember(farmId) { mutableStateOf(emptyList<GroupView>()) }
    var groupOptions by remember(farmId) { mutableStateOf(emptyList<FarmSelectorOption>()) }

    suspend fun refresh() {
        val groups = ops.groups()
        rows = groups.map { "${it.id} ${it.name} · ${it.speciesCode} · ${it.headCount}" }
        groupOptions = groups.map { FarmSelectorOption(it.id, it.name, "${it.speciesCode} · ${it.headCount} head recorded") }
        groupViews = loadGroups()
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

    GroupRecordNavigator(groupViews, loadGroup) { recordActions -> SimpleCaptureScreen(
        screenId = "FOS-GROUP-001",
        title = "Groups",
        help = "Groups hold shared animal membership and census records for farm operations.",
        empty = "No groups on this device.",
        rows = rows,
        busy = busy,
        error = error,
        fields = listOf("Name" to name, "Head count" to heads),
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
            FarmEntitySelector(
                FarmSelectionAtoms.SPECIES_SELECTOR,
                "Species",
                FarmSpeciesCodes.ALL.map { code -> FarmSelectorOption(code, code.replaceFirstChar { it.uppercase() }) },
                species.value,
                { species.value = it },
                "No species available.",
                enabled = !busy,
            )
            recordActions()
            FarmEntitySelector(FarmSelectionAtoms.GROUP_SELECTOR, "Census group", groupOptions, groupId.value.ifBlank { null }, { groupId.value = it }, "Create a group first.", enabled = !busy)
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
    ) }
}
