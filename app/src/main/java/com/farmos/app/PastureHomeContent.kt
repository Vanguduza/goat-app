package com.farmos.app

import androidx.compose.foundation.layout.fillMaxWidth
import com.farmos.core.design.FarmOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.farmos.core.design.FarmEntitySelector
import com.farmos.core.design.FarmModuleTools
import com.farmos.core.design.FarmPrimaryButton
import com.farmos.core.design.FarmSectionTabs
import com.farmos.core.design.FarmSelectionAtoms
import com.farmos.core.design.FarmToolAction
import com.farmos.core.design.FarmToolGroup
import com.farmos.domain.ops.CreatePaddock
import com.farmos.domain.ops.EndGrazing
import com.farmos.domain.ops.StartGrazing
import com.farmos.feature.ops.PastureOccupancyBoard
import com.farmos.feature.ops.SimpleCaptureScreen
import java.time.LocalDate
import java.util.UUID

/** FOS-PASTURE-001/004/005. Different farm operations get distinct focused workspaces. */
@Composable
internal fun PastureHomeContent(state: PastureModuleState, recordActions: @Composable () -> Unit) {
    var view by rememberSaveable { mutableStateOf("Overview") }
    with(state) {
        SimpleCaptureScreen(
            screenId = "FOS-PASTURE-001", title = "Pasture", help = "",
            empty = "No paddocks on this device.", rows = paddockRows + grazingRows,
            busy = busy, error = error,
            fields = if (view == "New paddock") listOf("Code" to code, "Name" to display, "Water source" to water) else emptyList(),
            actionLabel = "Create paddock",
            onSubmit = { run { ops.createPaddock(CreatePaddock(UUID.randomUUID().toString(), code.value, display.value.ifBlank { code.value }, waterSource = water.value, shade = true), newContext()) } },
            onBack = { if (view == "Overview") onBack() else view = "Overview" },
            captureVisible = view != "Overview", recordsVisible = false,
            showPrimaryAction = view == "New paddock", recordTitle = when (view) { "Start" -> "Start grazing"; "End" -> "End grazing"; else -> "Create paddock" },
            navigation = {
                FarmSectionTabs(listOf("Overview", "Start", "End", "New paddock"), view, { view = it })
                if (view == "Overview") {
                    FarmModuleTools(listOf(FarmToolGroup("Planning and reports", listOf(
                        FarmToolAction("Rest period", { page = PastureModulePage.REST }),
                        FarmToolAction("Carrying capacity", { page = PastureModulePage.CAPACITY }),
                        FarmToolAction("Pasture condition", { page = PastureModulePage.CONDITION }),
                        FarmToolAction("Pasture report", { page = PastureModulePage.REPORT }),
                        FarmToolAction("Pasture map", { page = PastureModulePage.MAP }),
                    ))), title = "Pasture tools")
                    recordActions()
                    PastureOccupancyBoard(records)
                }
            },
            extra = {
                when (view) {
                    "Start" -> {
                        FarmEntitySelector(FarmSelectionAtoms.LOCATION_SELECTOR, "Paddock", paddockOptions, paddockId.value.ifBlank { null }, { paddockId.value = it }, "Create a paddock first.", enabled = !busy)
                        FarmEntitySelector(FarmSelectionAtoms.GROUP_SELECTOR, "Group", groupOptions, groupId.value.ifBlank { null }, { groupId.value = it }, "Create a group in Groups first.", enabled = !busy)
                        OutlinedTextField(heads.value, { heads.value = it }, label = { Text("Head count") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                        OutlinedTextField(entered.value, { entered.value = it }, label = { Text("Enter date") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                        FarmPrimaryButton(
                            onClick = { run { ops.startGrazing(StartGrazing(UUID.randomUUID().toString(), paddockId.value, groupId.value, heads.value.toIntOrNull() ?: 0, LocalDate.parse(entered.value).toEpochDay()), newContext()) } },
                            enabled = !busy && paddockId.value.isNotBlank() && groupId.value.isNotBlank() && entered.value.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().testTag("pasture-start-submit"),
                        ) { Text("Start grazing") }
                    }
                    "End" -> {
                        FarmEntitySelector("pasture-open-session-selector", "Grazing session", openSessionOptions, sessionId.value.ifBlank { null }, { sessionId.value = it }, "No open grazing sessions.", enabled = !busy)
                        OutlinedTextField(exited.value, { exited.value = it }, label = { Text("Exit date") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                        FarmPrimaryButton(
                            onClick = { run { ops.endGrazing(EndGrazing(sessionId.value, LocalDate.parse(exited.value).toEpochDay()), newContext()) } },
                            enabled = !busy && sessionId.value.isNotBlank() && exited.value.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().testTag("pasture-end-submit"),
                        ) { Text("End grazing") }
                    }
                }
            },
        )
    }
}
