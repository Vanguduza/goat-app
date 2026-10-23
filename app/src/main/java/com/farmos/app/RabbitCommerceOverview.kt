package com.farmos.app

import androidx.compose.runtime.Composable
import com.farmos.core.design.FarmMetricStrip
import com.farmos.core.design.FarmMetricValue
import com.farmos.core.design.FarmModuleTools
import com.farmos.core.design.FarmToolAction
import com.farmos.core.design.FarmToolGroup
import com.farmos.feature.rabbit.RabbitCommerceRecords

/** P04/P07: one commerce job at a time; counts are actual local records, not funnel conversion. */
@Composable
internal fun RabbitCommerceOverview(records: RabbitCommerceRecords, onTool: (String) -> Unit) {
    FarmMetricStrip(listOf(
        FarmMetricValue("Reservations", records.reservations.size.toString(), onClick = { onTool("Records") }),
        FarmMetricValue("Contracts", records.contracts.size.toString(), onClick = { onTool("Records") }),
        FarmMetricValue("Market plans", records.plans.size.toString(), onClick = { onTool("Records") }),
    ))
    fun action(label: String, target: String) = FarmToolAction(label, { onTool(target) }, tag = "rabbit-commerce-tool:$target")
    FarmModuleTools(
        title = "Rabbit commerce",
        groups = listOf(
            FarmToolGroup("Buyers and sales", listOf(
                action("Add buyer to waitlist", "Waitlist"),
                action("Fulfill waitlist", "Fulfill"),
                action("Agree contract", "Contract"),
                action("Browse records", "Records"),
            )),
            FarmToolGroup("Kits and plans", listOf(
                action("Register kit", "Register kit"),
                action("Record retention", "Retention"),
                action("Promote kit", "Promote kit"),
                action("Record market plan", "Market plan"),
            )),
            FarmToolGroup("Bedding", listOf(action("Bind bedding", "Bedding"))),
        ),
    )
}
