package com.farmos.feature.ops

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.FarmMetricStrip
import com.farmos.core.design.FarmMetricValue
import com.farmos.core.design.FarmModuleTools
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmPrimaryButton
import com.farmos.core.design.FarmToolAction
import com.farmos.core.design.FarmToolGroup
import com.farmos.core.design.FarmVisualClass

/** FOS-HEALTH-001. P04 overview; clinical authority remains in the existing command boundary. */
@Composable
internal fun HealthDashboard(counts: HealthReadModel, packs: List<String>, error: String?, onOpen: (HealthPage) -> Unit, onBack: () -> Unit) {
    fun tool(label: String, page: HealthPage) = FarmToolAction(label, { onOpen(page) }, tag = "health-action:" + page.name)
    FarmOperationalPage("FOS-HEALTH-001", "Health", "", FarmVisualClass.I2, onBack) {
        FarmMetricStrip(listOf(
            FarmMetricValue("Observations", counts.observationCount?.toString() ?: "—", onClick = { onOpen(HealthPage.OBSERVATIONS) }),
            FarmMetricValue("Treatments", counts.treatmentCount?.toString() ?: "—", onClick = { onOpen(HealthPage.TREATMENT_RECORDS) }),
            FarmMetricValue("Active withdrawals", counts.activeWithdrawalCount?.toString() ?: "—", onClick = { onOpen(HealthPage.WITHDRAWALS) }),
        ))
        FarmPrimaryButton(onClick = { onOpen(HealthPage.RECORD_OBSERVATION) }, modifier = Modifier.fillMaxWidth()) { Text("Record observation") }
        FarmModuleTools(listOf(
            FarmToolGroup("Observe and review", listOf(
                tool("Observation list", HealthPage.OBSERVATIONS), tool("Today health actions", HealthPage.TODAY_ACTIONS),
                tool("Reference library", HealthPage.REFERENCES), tool("Emergency / red flag", HealthPage.EMERGENCY),
            )),
            FarmToolGroup("Clinical records", listOf(
                tool("Formulary", HealthPage.FORMULARY), tool("Record treatment", HealthPage.TREATMENT),
                tool("Withdrawal windows", HealthPage.WITHDRAWALS), tool("Record vet visit", HealthPage.VET_VISIT),
                tool("Record lab result", HealthPage.LAB_RESULT),
            )),
            FarmToolGroup("Health records", listOf(
                tool("Treatment records", HealthPage.TREATMENT_RECORDS), tool("Vaccination schedule", HealthPage.VACCINATION_SCHEDULE),
                tool("Vet visits", HealthPage.VET_VISITS), tool("Lab results", HealthPage.LAB_RESULTS),
                tool("Formulary items", HealthPage.FORMULARY_ITEM), tool("Protocol pack detail", HealthPage.PROTOCOL_PACK_DETAIL),
                tool("Health timeline", HealthPage.TIMELINE), tool("Health report", HealthPage.HEALTH_REPORT),
            )),
            FarmToolGroup("Protocol packs", listOf(
                tool("View protocol packs", HealthPage.PROTOCOLS), tool("Accept protocol pack", HealthPage.ACCEPT_PROTOCOL),
                tool("Add protocol slot", HealthPage.EDIT_PROTOCOL), tool("Apply protocol pack", HealthPage.APPLY_PROTOCOL),
            )),
        ), title = "Health tools")
        error?.let { Text(it, color = AnimalFarmTheme.colors.critical) }
    }
}
