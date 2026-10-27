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
import com.farmos.domain.ops.CreateFarmAsset
import com.farmos.domain.ops.RecordAssetMeter
import com.farmos.domain.ops.RecordMaintenance
import com.farmos.feature.ops.AssetRecordNavigator
import com.farmos.feature.ops.SimpleCaptureScreen
import java.time.LocalDate
import java.util.UUID

@Composable
internal fun AssetsModuleContent(state: AssetsModuleState) {
    with(state) {
    var homeSection by rememberSaveable(farmId) { mutableStateOf("Overview") }
    var captureMode by rememberSaveable(farmId) { mutableStateOf("Create asset") }
    when (page) {
        AssetsPage.HOME -> AssetRecordNavigator(records) { recordActions -> SimpleCaptureScreen(
            screenId = "FOS-ASSET-001",
            title = "Assets",
            help = "Record equipment and maintenance. This is not a depreciation ledger.",
            empty = "No assets on this device.",
            rows = rows,
            busy = busy,
            error = error,
            fields = if (captureMode == "Create asset") listOf("Code" to code, "Name" to name, "Kind" to kind) else emptyList(),
            actionLabel = "Create asset",
            onSubmit = {
                run { ops.createAsset(CreateFarmAsset(UUID.randomUUID().toString(), code.value, name.value, kind.value), newContext()) }
            },
            onBack = onBack,
            overview = {
                FarmSectionTabs(listOf("Overview", "Record", "Tools", "Records"), homeSection, { homeSection = it })
                if (homeSection == "Overview") {
                FarmMetricStrip(listOf(FarmMetricValue("Assets", records.assets.size.toString()), FarmMetricValue("Maintenance records", records.serviceCount?.toString() ?: "—")))
                if (maintenanceEvents.isNotEmpty()) FarmWorkflowTimeline("Recent maintenance records", maintenanceEvents.sortedByDescending { it.occurredEpochDay }.take(5).reversed().map { event ->
                    FarmWorkflowStep(assetEntities.firstOrNull { it.id == event.assetId }?.let { "${it.code} · ${it.name}" } ?: "Asset unavailable", event.title, LocalDate.ofEpochDay(event.occurredEpochDay).toString(), if (event.occurredEpochDay > LocalDate.now().toEpochDay()) FarmWorkflowState.UNKNOWN else FarmWorkflowState.RECORDED)
                }, caption = "Latest ${minOf(5, maintenanceEvents.size)} of ${maintenanceEvents.size} records. A future date does not establish completion.")
                }
            },
            navigation = { if (homeSection == "Tools") {
                FarmModuleTools(listOf(
                    FarmToolGroup("Maintenance", listOf(
                        FarmToolAction("Maintenance schedule", { page = AssetsPage.SCHEDULE }),
                        FarmToolAction("Record maintenance", { captureMode = "Record maintenance"; homeSection = "Record" }),
                        FarmToolAction("Record breakdown", { page = AssetsPage.BREAKDOWN }),
                        FarmToolAction("Meter reading", { page = AssetsPage.METER }),
                    )),
                    FarmToolGroup("Documents", listOf(
                        FarmToolAction("Documents", { page = AssetsPage.DOCUMENTS }),
                        FarmToolAction("Photo gallery", { page = AssetsPage.GALLERY }),
                        FarmToolAction("Asset report", { page = AssetsPage.REPORT }),
                    )),
                ), title = "Asset tools")
                recordActions()
            } },
            captureVisible = homeSection == "Record",
            recordsVisible = homeSection == "Records",
            showPrimaryAction = captureMode == "Create asset",
            recordTitle = captureMode,
            extra = {
                FarmSectionTabs(listOf("Create asset", "Record maintenance"), captureMode, { captureMode = it })
                if (captureMode == "Record maintenance") {
                    FarmEntitySelector(FarmSelectionAtoms.ASSET_SELECTOR, "Asset", assetOptions, assetId.value.ifBlank { null }, { assetId.value = it }, "Create an asset first.", enabled = !busy)
                    com.farmos.core.design.FarmOutlinedTextField(title.value, { title.value = it }, label = { androidx.compose.material3.Text("Maintenance title") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    com.farmos.core.design.FarmOutlinedTextField(day.value, { day.value = it }, label = { androidx.compose.material3.Text("Date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    com.farmos.core.design.FarmPrimaryButton(onClick = {
                        run { ops.recordMaintenance(RecordMaintenance(UUID.randomUUID().toString(), assetId.value, title.value, LocalDate.parse(day.value).toEpochDay()), newContext()) }
                    }, enabled = !busy && assetId.value.isNotBlank() && title.value.isNotBlank() && day.value.isNotBlank(), modifier = Modifier.fillMaxWidth()) { androidx.compose.material3.Text("Record maintenance") }
                }
            },
        ) }
        AssetsPage.SCHEDULE -> MaintenanceSchedulePage(
            assets = assetEntities,
            events = maintenanceEvents,
            today = LocalDate.now().toEpochDay(),
            onRecordBreakdown = { page = AssetsPage.BREAKDOWN },
            onBack = { page = AssetsPage.HOME },
        )
        AssetsPage.BREAKDOWN -> BreakdownPage(
            assetOptions = assetOptions,
            busy = busy,
            error = error,
            onRecord = { breakdownAssetId, breakdownTitle, breakdownDay, note ->
                run {
                    ops.recordMaintenance(
                        RecordMaintenance(
                            UUID.randomUUID().toString(),
                            breakdownAssetId,
                            "Breakdown: ${breakdownTitle.trim()}",
                            breakdownDay,
                            note?.trim()?.takeIf { it.isNotBlank() },
                        ),
                        newContext(),
                    )
                    page = AssetsPage.HOME
                }
            },
            onBack = { page = AssetsPage.HOME },
        )
        AssetsPage.METER -> MeterCapturePage(
            assetOptions = assetOptions,
            busy = busy,
            error = error,
            onRecord = { meterAssetId, value, unit, meterDay, note ->
                run {
                    ops.recordAssetMeter(
                        RecordAssetMeter(
                            UUID.randomUUID().toString(),
                            meterAssetId,
                            value,
                            unit.trim(),
                            meterDay,
                            note?.trim()?.takeIf { it.isNotBlank() },
                        ),
                        newContext(),
                    )
                    page = AssetsPage.HOME
                }
            },
            onBack = { page = AssetsPage.HOME },
        )
        AssetsPage.DOCUMENTS -> AssetAttachmentsPage(
            kind = AssetAttachmentKind.DOCUMENTS,
            assetOptions = assetOptions,
            loadAttachments = { assetId -> ops.attachmentsForOwner("asset", assetId) },
            onBack = { page = AssetsPage.HOME },
        )
        AssetsPage.GALLERY -> AssetAttachmentsPage(
            kind = AssetAttachmentKind.PHOTOS,
            assetOptions = assetOptions,
            loadAttachments = { assetId -> ops.attachmentsForOwner("asset", assetId) },
            onBack = { page = AssetsPage.HOME },
        )
        AssetsPage.REPORT -> AssetReportPage(
            assets = assetEntities,
            events = maintenanceEvents,
            loadMeters = { assetId -> ops.meterReadings(assetId) },
            onBack = { page = AssetsPage.HOME },
        )
    }

    }
}
