package com.farmos.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.farmos.core.design.FarmEntitySelector
import com.farmos.core.design.FarmSelectionAtoms
import com.farmos.core.design.FarmSelectorOption
import com.farmos.domain.ops.CreateAnimalGroup
import com.farmos.domain.ops.AmendAnimalGroup
import com.farmos.domain.ops.FarmSpeciesCodes
import com.farmos.domain.ops.RecordGroupCensus
import com.farmos.feature.ops.GroupRecordNavigator
import com.farmos.feature.ops.SimpleCaptureScreen
import java.time.LocalDate
import java.util.UUID

@Composable
internal fun GroupsModuleContent(state: GroupsModuleState) {
    with(state) {
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
}
