package com.farmos.app

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.farmos.core.database.AnimalEntity
import com.farmos.core.design.FarmEntitySelector
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmSelectionAtoms
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.CreateAnimalGroup
import com.farmos.domain.ops.AmendAnimalGroup
import com.farmos.domain.ops.MoveAnimalGroup
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
import com.farmos.core.design.runSuspendCatching

/** Internal pages of the group module, including FOS-GROUP-007 (Group Move) wired to MoveAnimalGroup (group.animal_move.v1). */
private enum class GroupsPage {
    HOME,
    CREATE,
    MEMBERSHIP,
    EDIT,
    HEALTH,
    MOVE,
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

    LaunchedEffect(farmId) { runSuspendCatching { refresh() } }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            runSuspendCatching {
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
                androidx.compose.material3.TextButton(
                    onClick = { page = GroupsPage.EDIT },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { androidx.compose.material3.Text("Edit group") }
                androidx.compose.material3.TextButton(
                    onClick = { page = GroupsPage.HEALTH },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { androidx.compose.material3.Text("Group health") }
                androidx.compose.material3.TextButton(
                    onClick = { page = GroupsPage.MOVE },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { androidx.compose.material3.Text("Move animals") }
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
        GroupsPage.EDIT -> GroupEditPage(
            groupOptions = groupOptions,
            busy = busy,
            error = error,
            loadGroup = { id -> ops.group(id) },
            onSave = { id, groupName, speciesCode ->
                run {
                    ops.amendGroup(
                        AmendAnimalGroup(id, groupName, speciesCode),
                        newContext(),
                    )
                    page = GroupsPage.HOME
                }
            },
            onBack = { page = GroupsPage.HOME },
        )
        GroupsPage.HEALTH -> GroupHealthPage(
            groupOptions = groupOptions,
            loadGroup = { id -> ops.group(id) },
            loadObservations = { ops.recentObservations() },
            loadTreatments = { ops.recentTreatments() },
            onBack = { page = GroupsPage.HOME },
        )
        GroupsPage.MOVE -> GroupMovePage(
            groupOptions = groupOptions,
            busy = busy,
            error = error,
            loadGroup = { id -> ops.group(id) },
            loadMembers = { id -> ops.animalsInGroup(id) },
            loadUnassigned = { speciesCode -> ops.unassignedAnimals(speciesCode) },
            loadTargets = { speciesCode, excludeId ->
                ops.groups().filter { it.speciesCode == speciesCode && it.id != excludeId }
                    .map { FarmSelectorOption(it.id, it.name, "${it.speciesCode} · ${it.headCount} head recorded") }
            },
            onMove = { command ->
                run {
                    ops.moveAnimalGroup(command, newContext())
                }
            },
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
            census = runSuspendCatching { loadCensus(id) }.getOrNull().orEmpty()
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

/**
 * FOS-GROUP-004 — Edit Group: renames a group or moves it to another species, written through
 * AmendAnimalGroup (group.amend.v1). The head count is not edited here; it is set by census.
 */
@Composable
private fun GroupEditPage(
    groupOptions: List<FarmSelectorOption>,
    busy: Boolean,
    error: String?,
    loadGroup: suspend (String) -> com.farmos.core.database.AnimalGroupEntity?,
    onSave: (id: String, name: String, speciesCode: String) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var groupId by remember { mutableStateOf<String?>(null) }
    val name = remember { mutableStateOf("") }
    val species = remember { mutableStateOf("goat") }

    fun load(id: String) {
        scope.launch {
            val group = runSuspendCatching { loadGroup(id) }.getOrNull()
            if (group != null) {
                name.value = group.name
                species.value = group.speciesCode
            }
        }
    }

    FarmOperationalPage(
        screenId = "FOS-GROUP-004",
        title = "Edit group",
        subtitle = "Rename a group or change its species.",
        onBack = onBack,
    ) {
        FarmEntitySelector(
            FarmSelectionAtoms.GROUP_SELECTOR,
            "Group",
            groupOptions,
            groupId,
            { groupId = it; load(it) },
            "Create a group first.",
            enabled = !busy,
        )
        FarmOperationalSection("Details") {
            androidx.compose.material3.OutlinedTextField(
                value = name.value,
                onValueChange = { name.value = it },
                label = { androidx.compose.material3.Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && groupId != null,
            )
            FarmEntitySelector(
                FarmSelectionAtoms.SPECIES_SELECTOR,
                "Species",
                FarmSpeciesCodes.ALL.map { code -> FarmSelectorOption(code, code.replaceFirstChar { it.uppercase() }) },
                species.value,
                { species.value = it },
                "No species available.",
                enabled = !busy && groupId != null,
            )
            androidx.compose.material3.Button(
                onClick = { onSave(groupId!!, name.value.trim(), species.value) },
                enabled = !busy && groupId != null && name.value.isNotBlank(),
            ) {
                androidx.compose.material3.Text("Save changes")
            }
            error?.let { androidx.compose.material3.Text(it) }
        }
    }
}

/**
 * FOS-GROUP-008 — Group Health: health observations and treatments for the group's species,
 * from the records on this device. Groups track head counts, not per-animal membership, so
 * the summary is species-scoped and says so plainly.
 */
@Composable
private fun GroupHealthPage(
    groupOptions: List<FarmSelectorOption>,
    loadGroup: suspend (String) -> com.farmos.core.database.AnimalGroupEntity?,
    loadObservations: suspend () -> List<com.farmos.core.database.HealthObservationEntity>,
    loadTreatments: suspend () -> List<com.farmos.core.database.HealthTreatmentEntity>,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var groupId by remember { mutableStateOf<String?>(null) }
    var species by remember { mutableStateOf<String?>(null) }
    var observations by remember { mutableStateOf<List<com.farmos.core.database.HealthObservationEntity>?>(null) }
    var treatments by remember { mutableStateOf<List<com.farmos.core.database.HealthTreatmentEntity>?>(null) }

    fun load(id: String) {
        scope.launch {
            observations = null
            treatments = null
            val group = runSuspendCatching { loadGroup(id) }.getOrNull()
            species = group?.speciesCode
            val code = group?.speciesCode
            observations = runSuspendCatching { loadObservations() }.getOrElse { emptyList() }.filter { it.speciesCode == code }
            treatments = runSuspendCatching { loadTreatments() }.getOrElse { emptyList() }.filter { it.speciesCode == code }
        }
    }

    FarmOperationalPage(
        screenId = "FOS-GROUP-008",
        title = "Group health",
        subtitle = "Health records for the group's species, from this device.",
        onBack = onBack,
    ) {
        FarmEntitySelector(
            FarmSelectionAtoms.GROUP_SELECTOR,
            "Group",
            groupOptions,
            groupId,
            { groupId = it; load(it) },
            "Create a group first.",
            enabled = true,
        )
        FarmOperationalSection(
            title = "Summary",
            description = "Groups hold head counts, not per-animal links, so this is the species picture.",
        ) {
            when {
                groupId == null -> androidx.compose.material3.Text("Choose a group.")
                observations == null -> androidx.compose.material3.Text("Reading health records")
                else -> {
                    val obs = observations!!
                    val trt = treatments.orEmpty()
                    val redFlags = obs.count { it.redFlag }
                    androidx.compose.material3.Text("Species: ${species ?: "-"}")
                    androidx.compose.material3.Text("${obs.size} observations · $redFlags red flags · ${trt.size} treatments on this device.")
                    val recent = obs.sortedByDescending { it.occurredAtEpochMillis }.take(10)
                    if (recent.isNotEmpty()) {
                        androidx.compose.material3.Text("Latest observations:")
                        recent.forEach { o ->
                            androidx.compose.material3.Text("${o.signs}${if (o.redFlag) " · RED FLAG" else ""}")
                        }
                    }
                }
            }
        }
    }
}

/**
 * FOS-GROUP-007 — Group Move: move one or more animals between groups of the same species, written
 * through MoveAnimalGroup (group.animal_move.v1). The source group's recorded members are listed
 * with checkboxes; animals of the same species with no recorded membership are listed as unassigned
 * and may be claimed by the move as their first group assignment. The move is a two-tap confirm;
 * success is reported only after the host's local Room transaction commits (error stays null).
 */
@Composable
private fun GroupMovePage(
    groupOptions: List<FarmSelectorOption>,
    busy: Boolean,
    error: String?,
    loadGroup: suspend (String) -> com.farmos.core.database.AnimalGroupEntity?,
    loadMembers: suspend (String) -> List<AnimalEntity>,
    loadUnassigned: suspend (String) -> List<AnimalEntity>,
    loadTargets: suspend (String, String) -> List<FarmSelectorOption>,
    onMove: (MoveAnimalGroup) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var fromGroupId by remember { mutableStateOf<String?>(null) }
    var toGroupId by remember { mutableStateOf<String?>(null) }
    var species by remember { mutableStateOf<String?>(null) }
    var members by remember { mutableStateOf<List<AnimalEntity>?>(null) }
    var unassigned by remember { mutableStateOf<List<AnimalEntity>?>(null) }
    var targets by remember { mutableStateOf(emptyList<FarmSelectorOption>()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var armed by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(0) }
    var done by remember { mutableStateOf<String?>(null) }

    fun load(id: String) {
        scope.launch {
            members = null
            unassigned = null
            targets = emptyList()
            selected = emptySet()
            armed = false
            done = null
            val group = runSuspendCatching { loadGroup(id) }.getOrNull()
            species = group?.speciesCode
            members = runSuspendCatching { loadMembers(id) }.getOrElse { emptyList() }
            if (group != null) {
                unassigned = runSuspendCatching { loadUnassigned(group.speciesCode) }.getOrElse { emptyList() }
                targets = runSuspendCatching { loadTargets(group.speciesCode, id) }.getOrElse { emptyList() }
            } else {
                unassigned = emptyList()
            }
        }
    }

    // A submitted move reports its outcome once the host finishes: success only when the host
    // reports no error; otherwise the host's error state carries the rejection reason.
    LaunchedEffect(busy) {
        if (submitted > 0 && !busy) {
            if (error == null) {
                done = "Moved $submitted animal(s). Saved on this device · waiting to sync."
                selected = emptySet()
                armed = false
                fromGroupId?.let { load(it) }
            }
            submitted = 0
        }
    }
    LaunchedEffect(selected, toGroupId) { armed = false }

    val fromId = fromGroupId
    val toId = toGroupId
    val targetName = targets.firstOrNull { it.id == toId }?.label

    FarmOperationalPage(
        screenId = "FOS-GROUP-007",
        title = "Move animals",
        subtitle = "Move animals between groups of the same species.",
        onBack = onBack,
    ) {
        FarmEntitySelector(
            FarmSelectionAtoms.GROUP_SELECTOR,
            "Source group",
            groupOptions,
            fromId,
            { fromGroupId = it; toGroupId = null; load(it) },
            "Create a group first.",
            enabled = !busy,
        )
        when {
            fromId == null -> androidx.compose.material3.Text("Choose a source group.")
            members == null -> androidx.compose.material3.Text("Reading group members")
            else -> {
                FarmOperationalSection(
                    title = "Animals in this group",
                    description = "Recorded members. Tick the animals to move.",
                ) {
                    val memberList = members!!
                    if (memberList.isEmpty()) {
                        androidx.compose.material3.Text("No animals recorded in this group yet.")
                    } else {
                        memberList.forEach { animal ->
                            AnimalCheckRow(animal, selected.contains(animal.id), !busy) { checked ->
                                selected = if (checked) selected + animal.id else selected - animal.id
                            }
                        }
                    }
                }
                val unassignedList = unassigned.orEmpty()
                if (unassignedList.isNotEmpty()) {
                    FarmOperationalSection(
                        title = "Unassigned ${species ?: ""} animals",
                        description = "No recorded group yet. Moving one assigns it to the target group.",
                    ) {
                        unassignedList.forEach { animal ->
                            AnimalCheckRow(animal, selected.contains(animal.id), !busy) { checked ->
                                selected = if (checked) selected + animal.id else selected - animal.id
                            }
                        }
                    }
                }
                FarmOperationalSection(
                    title = "Target group",
                    description = "Same species as the source group.",
                ) {
                    FarmEntitySelector(
                        FarmSelectionAtoms.GROUP_SELECTOR,
                        "Target group",
                        targets,
                        toId,
                        { toGroupId = it },
                        "No other group of this species on this device.",
                        enabled = !busy,
                    )
                }
                FarmOperationalSection(title = "Confirm") {
                    androidx.compose.material3.Button(
                        onClick = {
                            if (armed) {
                                submitted = selected.size
                                done = null
                                onMove(MoveAnimalGroup(UUID.randomUUID().toString(), selected.toList(), fromId, toId!!))
                            } else {
                                armed = true
                            }
                        },
                        enabled = !busy && toId != null && selected.isNotEmpty(),
                    ) {
                        androidx.compose.material3.Text(
                            if (armed) "Confirm move of ${selected.size} animal(s)" + (targetName?.let { " to $it" } ?: "")
                            else "Move ${selected.size} animal(s)",
                        )
                    }
                    if (busy) androidx.compose.material3.Text("Saving move")
                    error?.let { androidx.compose.material3.Text(it) }
                    done?.let { androidx.compose.material3.Text(it) }
                }
            }
        }
    }
}

@Composable
private fun AnimalCheckRow(
    animal: AnimalEntity,
    checked: Boolean,
    enabled: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Checkbox(checked, onChecked, enabled = enabled)
        val name = animal.name?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
        androidx.compose.material3.Text(
            "${animal.tag}$name · ${animal.sex}",
            modifier = Modifier.weight(1f),
        )
    }
}
