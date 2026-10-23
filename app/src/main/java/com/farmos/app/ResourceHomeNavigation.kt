package com.farmos.app

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.farmos.core.design.FarmMetricStrip
import com.farmos.core.design.FarmMetricValue
import com.farmos.core.design.FarmModuleTools
import com.farmos.core.design.FarmSectionTabs
import com.farmos.core.design.FarmToolAction
import com.farmos.core.design.FarmToolGroup
import com.farmos.feature.ops.FeedRecordedFlow
import com.farmos.feature.ops.FeedRecords
import com.farmos.feature.ops.WaterRecordedFlow
import com.farmos.feature.ops.WaterRecords
import java.math.BigDecimal

/** FOS-FEED-001. Inventory issues and ration plans are different jobs. */
@Composable
internal fun FeedStockOverview(records: FeedRecords) {
    FarmMetricStrip(listOf(
        FarmMetricValue("Feed issues", records.issueCount?.toString() ?: "—"),
        FarmMetricValue("Items issued", if (records.issueCount == null) "—" else records.items.size.toString()),
    ))
}

@Composable
internal fun FeedHomeNavigation(view: String, onView: (String) -> Unit, onOpen: (FeedModulePage) -> Unit, recordActions: @Composable () -> Unit, records: FeedRecords) {
    FarmSectionTabs(listOf("Overview", "Issue feed"), view, onView)
    if (view == "Overview") {
        FarmModuleTools(listOf(
            FarmToolGroup("Planning", listOf(
                FarmToolAction("Feed plans", { onOpen(FeedModulePage.PLANS) }),
                FarmToolAction("Ration builder", { onOpen(FeedModulePage.RATION_BUILDER) }),
                FarmToolAction("Ration analysis", { onOpen(FeedModulePage.RATION_ANALYSIS) }),
                FarmToolAction("Compare rations", { onOpen(FeedModulePage.RATION_COMPARE) }),
            )),
            FarmToolGroup("Insights", listOf(
                FarmToolAction("Feed cost", { onOpen(FeedModulePage.COST) }),
                FarmToolAction("Feed alerts", { onOpen(FeedModulePage.ALERTS) }),
                FarmToolAction("Feed report", { onOpen(FeedModulePage.REPORT) }),
            )),
        ), title = "Feed tools")
        recordActions()
        if (records.issues.isNotEmpty()) FeedRecordedFlow(records)
    } else Text("Issuing feed deducts the selected item's stock.")
}

/** FOS-WATER-001. Totals are recorded litres, not measured animal consumption. */
@Composable
internal fun WaterStockOverview(records: WaterRecords) {
    val litres = records.sources.fold(BigDecimal.ZERO) { sum, source -> sum + BigDecimal.valueOf(source.litresMilli, 3) }
    FarmMetricStrip(listOf(
        FarmMetricValue("Records", records.recordCount?.toString() ?: "—"),
        FarmMetricValue("Recorded litres", if (records.recordCount == null) "—" else litres.stripTrailingZeros().toPlainString() + " L"),
    ))
}

@Composable
internal fun WaterHomeNavigation(view: String, onView: (String) -> Unit, onOpen: (WaterModulePage) -> Unit, recordActions: @Composable () -> Unit, records: WaterRecords) {
    FarmSectionTabs(listOf("Overview", "Record water"), view, onView)
    if (view == "Overview") {
        FarmModuleTools(listOf(FarmToolGroup("Points and reports", listOf(
            FarmToolAction("Water points", { onOpen(WaterModulePage.POINTS) }),
            FarmToolAction("Water report", { onOpen(WaterModulePage.REPORT) }),
        ))), title = "Water tools")
        recordActions()
        if (records.records.isNotEmpty()) WaterRecordedFlow(records)
    }
}
