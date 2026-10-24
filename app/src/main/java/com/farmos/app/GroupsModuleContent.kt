package com.farmos.app

import com.farmos.core.design.FarmMetricStrip
import com.farmos.core.design.FarmMetricValue
import com.farmos.core.design.FarmMetricBars
import com.farmos.core.design.FarmBarValue
import com.farmos.core.design.FarmModuleTools
import com.farmos.core.design.FarmToolAction
import com.farmos.core.design.FarmToolGroup
import com.farmos.core.design.FarmSectionTabs
import com.farmos.core.design.FarmWorkflowTimeline
import com.farmos.core.design.FarmWorkflowStep
import com.farmos.core.design.FarmWorkflowState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
    var homeSection by rememberSaveable(farmId) { mutableStateOf("Overview") }
    var captureMode by rememberSaveable(farmId) { mutableStateOf("Create group") }
    when (page) {
        GroupsPage.HOME -> GroupRecordNavigator(groupViews, loadGroup) { recordActions -> SimpleCaptureScreen(
            screenId = "FOS-GROUP-001",
            title = "Groups",
            help = "Groups hold shared animal membership and census records for farm operations.",
            empty = "No groups on this device.",
            rows = rows,
            busy = busy,
            error = error,
            fields = if (captureMode == "Create group") listOf("Name" to name, "Head count" to heads) else emptyList(),
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
            overview = {
                FarmSectionTabs(listOf("Overview", "Record", "Tools", "Records"), homeSection, { homeSection = it })
                if (homeSection == "Overview") {
                FarmMetricStrip(listOf(FarmMetricValue("Groups", groupViews.size.toString()), FarmMetricValue("Species", groupViews.map { it.speciesCode }.distinct().size.toString())))
                if (groupViews.isNotEmpty()) FarmMetricBars("Registered group sizes", groupViews.map { FarmBarValue(it.name, it.recordedHeadCount.toDouble(), "${it.recordedHeadCount} head", it.speciesCode) }, "head", caption = "Registered sizes. Census records remain separate.")
                }
            },
            navigation = { if (homeSection == "Tools") {
                FarmModuleTools(listOf(
                    FarmToolGroup("Manage", listOf(
                        FarmToolAction("New group", { page = GroupsPage.CREATE }),
                        FarmToolAction("Membership", { page = GroupsPage.MEMBERSHIP }),
                        FarmToolAction("Edit group", { page = GroupsPage.EDIT }),
                    )),
                    FarmToolGroup("Operations", listOf(
                        FarmToolAction("Group health", { page = GroupsPage.HEALTH }),
                        FarmToolAction("Move animals", { page = GroupsPage.MOVE }),
                        FarmToolAction("Record census", { captureMode = "Record census"; homeSection = "Record" }),
                    )),
                ), title = "Group tools")
                recordActions()
            } },
            captureVisible = homeSection == "Record",
            recordsVisible = homeSection == "Records",
            showPrimaryAction = captureMode == "Create group",
            recordTitle = captureMode,
            extra = {
                FarmSectionTabs(listOf("Create group", "Record census"), captureMode, { captureMode = it })
                if (captureMode == "Create group") {
                    FarmEntitySelector(FarmSelectionAtoms.SPECIES_SELECTOR, "Species", FarmSpeciesCodes.ALL.map { FarmSelectorOption(it, it.replaceFirstChar { c -> c.uppercase() }) }, species.value, { species.value = it }, "No species available.", enabled = !busy)
                } else {
                    FarmEntitySelector(FarmSelectionAtoms.GROUP_SELECTOR, "Census group", groupOptions, groupId.value.ifBlank { null }, { groupId.value = it }, "Create a group first.", enabled = !busy)
                    com.farmos.core.design.FarmOutlinedTextField(day.value, { day.value = it }, label = { androidx.compose.material3.Text("Census day") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    com.farmos.core.design.FarmOutlinedTextField(heads.value, { heads.value = it }, label = { androidx.compose.material3.Text("Census head count") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    com.farmos.core.design.FarmPrimaryButton(onClick = {
                        run { ops.recordCensus(RecordGroupCensus(UUID.randomUUID().toString(), groupId.value, heads.value.toIntOrNull() ?: -1, LocalDate.parse(day.value).toEpochDay()), newContext()) }
                    }, enabled = !busy && groupId.value.isNotBlank() && heads.value.isNotBlank() && day.value.isNotBlank(), modifier = Modifier.fillMaxWidth()) { androidx.compose.material3.Text("Record census") }
                }
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
