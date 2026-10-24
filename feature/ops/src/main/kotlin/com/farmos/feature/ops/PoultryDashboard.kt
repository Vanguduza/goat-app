package com.farmos.feature.ops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.farmos.core.design.AnimalFarmCanvas
import com.farmos.core.design.AnimalFarmFamily
import com.farmos.core.design.AnimalFarmModuleHeader
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.FarmBackButton
import com.farmos.core.design.FarmBackHandler
import com.farmos.core.design.FarmMetricStrip
import com.farmos.core.design.FarmMetricValue
import com.farmos.core.design.FarmModuleTools
import com.farmos.core.design.FarmPrimaryButton
import com.farmos.core.design.FarmToolAction
import com.farmos.core.design.FarmToolGroup
import com.farmos.core.design.FosDimens

/** FOS-POULTRY-001. P04 derives from Animal Farm A; kinds remain distinct flock contexts. */
@Composable
internal fun PoultryDashboard(
    enabledKinds: List<String>,
    houses: List<String>,
    records: PoultryRecords,
    error: String?,
    onOpen: (PoultryPage) -> Unit,
    onBack: () -> Unit,
) {
    fun tool(label: String, page: PoultryPage) = FarmToolAction(label, { onOpen(page) }, tag = "poultry-action:" + page.name)
    val loaded = records.flockCount != null
    FarmBackHandler(onBack)
    AnimalFarmCanvas {
        Column(
            Modifier.weight(1f).imePadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(FosDimens.ScreenMargin)
                .testTag("farm-screen:FOS-POULTRY-001"),
            verticalArrangement = Arrangement.spacedBy(FosDimens.SectionGap),
        ) {
            FarmBackButton("Farm home", onBack)
            AnimalFarmModuleHeader("Poultry", "Houses, flocks and hatchery", family = AnimalFarmFamily.POULTRY)
            FarmMetricStrip(listOf(
                FarmMetricValue("Kinds", if (loaded) enabledKinds.size.toString() else "—", onClick = { onOpen(PoultryPage.KINDS) }),
                FarmMetricValue("Houses", if (loaded) houses.size.toString() else "—", onClick = { onOpen(PoultryPage.HOUSES) }),
                FarmMetricValue("Flocks", records.flockCount?.toString() ?: "—", onClick = { onOpen(PoultryPage.FLOCKS) }),
                FarmMetricValue("Hatches", if (loaded) records.hatches.size.toString() else "—", onClick = { onOpen(PoultryPage.HATCHERY) }),
            ))
            FarmPrimaryButton(onClick = { onOpen(PoultryPage.DAILY) }, modifier = Modifier.fillMaxWidth()) { Text("Daily flock record") }
            FarmModuleTools(listOf(
                FarmToolGroup("Flocks", listOf(
                    tool("Flocks", PoultryPage.FLOCKS), tool("Place flock", PoultryPage.PLACEMENT),
                    tool("Flock profiles", PoultryPage.FLOCK_PROFILE), tool("Move flock", PoultryPage.FLOCK_MOVE),
                    tool("Close out flock", PoultryPage.FLOCK_CLOSEOUT),
                )),
                FarmToolGroup("Hatchery", listOf(
                    tool("Hatchery", PoultryPage.HATCHERY), tool("Set eggs", PoultryPage.SET_EGGS),
                    tool("Candling", PoultryPage.CANDLING), tool("Hatch result", PoultryPage.HATCH_RESULT),
                    tool("Incubation batches", PoultryPage.INCUBATION_BATCH),
                )),
                FarmToolGroup("Production", listOf(
                    tool("Egg production", PoultryPage.EGG_PRODUCTION), tool("Feed", PoultryPage.FEED),
                    tool("Poultry report", PoultryPage.POULTRY_REPORT),
                )),
                FarmToolGroup("Health", listOf(
                    tool("Vaccination", PoultryPage.VACCINATION), tool("Biosecurity", PoultryPage.BIOSECURITY),
                    tool("Biosecurity records", PoultryPage.BIOSECURITY_RECORDS),
                    tool("Flock health", PoultryPage.FLOCK_HEALTH), tool("Flock timeline", PoultryPage.FLOCK_TIMELINE),
                )),
                FarmToolGroup("Setup", listOf(
                    tool("Enabled kinds", PoultryPage.KINDS), tool("Houses", PoultryPage.HOUSES),
                    tool("House details", PoultryPage.HOUSE_DETAIL),
                )),
            ), title = "Poultry tools")
            error?.let { Text(it, color = AnimalFarmTheme.colors.critical) }
        }
    }
}
