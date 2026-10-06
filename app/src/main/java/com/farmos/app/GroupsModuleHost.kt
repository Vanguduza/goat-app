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
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmSelectionAtoms
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.CreateAnimalGroup
import com.farmos.domain.ops.FarmSpeciesCodes
import com.farmos.domain.ops.RecordGroupCensus
import com.farmos.feature.ops.GroupCensusView
import com.farmos.feature.ops.GroupRecordNavigator
import com.farmos.feature.ops.GroupRecords
import com.farmos.feature.ops.GroupView
import com.farmos.feature.ops.SimpleCaptureScreen
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

/** Internal pages of the group module. FOS-GROUP-007 (Group Move) has no domain command and is not a page. */
private enum class GroupsPage {
    HOME,
    CREATE,
    MEMBERSHIP,
}

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
    var page by remember(farmId) { mutableStateOf(GroupsPage.HOME) }
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

    when (page) {
        GroupsPage.HOME -> GroupRecordNavigator(groupViews, loadGroup) { recordActions -> SimpleCaptureScreen(
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
                androidx.compose.material3.TextButton(
                    onClick = { page = GroupsPage.CREATE },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { androidx.compose.material3.Text("New group") }
                androidx.compose.material3.TextButton(
                    onClick = { page = GroupsPage.MEMBERSHIP },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { androidx.compose.material3.Text("Membership") }
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
        GroupsPage.CREATE -> GroupCreatePage(
            busy = busy,
            error = error,
            onCreate = { groupName, speciesCode, headCount ->
                run {
                    ops.createGroup(
                        CreateAnimalGroup(UUID.randomUUID().toString(), speciesCode, groupName, headCount),
                        newContext(),
                    )
                    page = GroupsPage.HOME
                }
            },
            onBack = { page = GroupsPage.HOME },
        )
        GroupsPage.MEMBERSHIP -> GroupMembershipPage(
            groupOptions = groupOptions,
            busy = busy,
            loadCensus = { id -> loadGroup(id).census },
            onBack = { page = GroupsPage.HOME },
        )
    }
}

/**
 * FOS-GROUP-003 — Create Group: name, species and opening head count, written through
 * CreateAnimalGroup (group.create.v1).
 */
@Composable
private fun GroupCreatePage(
    busy: Boolean,
    error: String?,
    onCreate: (name: String, speciesCode: String, headCount: Int) -> Unit,
    onBack: () -> Unit,
) {
    val name = remember { mutableStateOf("") }
    val species = remember { mutableStateOf("goat") }
    val heads = remember { mutableStateOf("") }
    FarmOperationalPage(
        screenId = "FOS-GROUP-003",
        title = "Create group",
        subtitle = "A named set of animals counted together.",
        onBack = onBack,
    ) {
        FarmOperationalSection(
            title = "New group",
            description = "The head count becomes the group's first census entry.",
        ) {
            androidx.compose.material3.OutlinedTextField(
                value = name.value,
                onValueChange = { name.value = it },
                label = { androidx.compose.material3.Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            )
            FarmEntitySelector(
                FarmSelectionAtoms.SPECIES_SELECTOR,
                "Species",
                FarmSpeciesCodes.ALL.map { code -> FarmSelectorOption(code, code.replaceFirstChar { it.uppercase() }) },
                species.value,
                { species.value = it },
                "No species available.",
                enabled = !busy,
            )
            androidx.compose.material3.OutlinedTextField(
                value = heads.value,
                onValueChange = { heads.value = it },
                label = { androidx.compose.material3.Text("Head count") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            )
            androidx.compose.material3.Button(
                onClick = { onCreate(name.value.trim(), species.value, heads.value.toIntOrNull() ?: 0) },
                enabled = !busy && name.value.isNotBlank(),
            ) {
                androidx.compose.material3.Text("Create group")
            }
            error?.let { androidx.compose.material3.Text(it) }
        }
    }
}

/**
 * FOS-GROUP-005 — Membership: census history per group. Group membership is tracked through
 * census head counts; no per-animal membership link table exists in the schema.
 */
@Composable
private fun GroupMembershipPage(
    groupOptions: List<FarmSelectorOption>,
    busy: Boolean,
    loadCensus: suspend (String) -> List<GroupCensusView>,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var census by remember { mutableStateOf<List<GroupCensusView>?>(null) }

    fun load(id: String) {
        scope.launch {
            census = null
            census = runCatching { loadCensus(id) }.getOrNull().orEmpty()
        }
    }

    FarmOperationalPage(
        screenId = "FOS-GROUP-005",
        title = "Membership",
        subtitle = "Who belongs to a group is its census history: head counts over time.",
        onBack = onBack,
    ) {
        FarmEntitySelector(
            FarmSelectionAtoms.GROUP_SELECTOR,
            "Group",
            groupOptions,
            selectedId,
            { selectedId = it; load(it) },
            "Create a group first.",
            enabled = !busy,
        )
        FarmOperationalSection(
            title = "Census history",
            description = "Newest first. Each census sets the group's recorded head count.",
        ) {
            when {
                selectedId == null -> androidx.compose.material3.Text("Choose a group.")
                census == null -> androidx.compose.material3.Text("Reading census history")
                census!!.isEmpty() -> androidx.compose.material3.Text("No census records for this group yet.")
                else -> census!!.forEach { row ->
                    androidx.compose.material3.Text("${LocalDate.ofEpochDay(row.epochDay)} · ${row.headCount} head")
                }
            }
        }
    }
}
