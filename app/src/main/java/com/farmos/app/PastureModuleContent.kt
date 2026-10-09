package com.farmos.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.farmos.core.design.FarmEntitySelector
import com.farmos.core.design.FarmSelectionAtoms
import com.farmos.domain.ops.CreatePaddock
import com.farmos.domain.ops.EndGrazing
import com.farmos.domain.ops.StartGrazing
import com.farmos.feature.ops.PastureRecordNavigator
import com.farmos.feature.ops.SimpleCaptureScreen
import java.time.LocalDate
import java.util.UUID

@Composable
internal fun PastureModuleContent(state: PastureModuleState) {
    with(state) {
    when (page) {
        PastureModulePage.HOME -> PastureRecordNavigator(records) { recordActions -> SimpleCaptureScreen(
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
            recordActions()
            FarmEntitySelector(FarmSelectionAtoms.LOCATION_SELECTOR, "Paddock", paddockOptions, paddockId.value.ifBlank { null }, { paddockId.value = it }, "Create a paddock first.", enabled = !busy)
            FarmEntitySelector(FarmSelectionAtoms.GROUP_SELECTOR, "Group", groupOptions, groupId.value.ifBlank { null }, { groupId.value = it }, "Create a group in Groups first.", enabled = !busy)
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
            androidx.compose.material3.TextButton(onClick = { page = PastureModulePage.REST }) { androidx.compose.material3.Text("Rest period") }
            androidx.compose.material3.TextButton(onClick = { page = PastureModulePage.CAPACITY }) { androidx.compose.material3.Text("Carrying capacity") }
            androidx.compose.material3.TextButton(onClick = { page = PastureModulePage.CONDITION }) { androidx.compose.material3.Text("Pasture condition") }
            androidx.compose.material3.TextButton(onClick = { page = PastureModulePage.REPORT }) { androidx.compose.material3.Text("Pasture report") }
            androidx.compose.material3.TextButton(onClick = { page = PastureModulePage.MAP }) { androidx.compose.material3.Text("Pasture map") }
        },
    ) }
        PastureModulePage.REST -> PastureRestPeriodScreen(ops = ops, onBack = { page = PastureModulePage.HOME })
        PastureModulePage.CAPACITY -> PastureCarryingCapacityScreen(ops = ops, onBack = { page = PastureModulePage.HOME })
        PastureModulePage.CONDITION -> PastureConditionScreen(ops = ops, onBack = { page = PastureModulePage.HOME })
        PastureModulePage.REPORT -> PastureReportScreen(ops = ops, onBack = { page = PastureModulePage.HOME })
        PastureModulePage.MAP -> PastureMapScreen(ops = ops, farmId = farmId, onBack = { page = PastureModulePage.HOME })
    }

    }
}
