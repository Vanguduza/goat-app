package com.farmos.app

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
    when (page) {
        AssetsPage.HOME -> AssetRecordNavigator(records) { recordActions -> SimpleCaptureScreen(
            screenId = "FOS-ASSET-001",
            title = "Assets",
            help = "Record equipment and maintenance. This is not a depreciation ledger.",
            empty = "No assets on this device.",
            rows = rows,
            busy = busy,
            error = error,
            fields = listOf("Code" to code, "Name" to name, "Kind" to kind),
            actionLabel = "Create asset",
            onSubmit = {
                run { ops.createAsset(CreateFarmAsset(UUID.randomUUID().toString(), code.value, name.value, kind.value), newContext()) }
            },
            onBack = onBack,
            extra = {
                androidx.compose.material3.TextButton(
                    onClick = { page = AssetsPage.SCHEDULE },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { androidx.compose.material3.Text("Maintenance schedule") }
                androidx.compose.material3.TextButton(
                    onClick = { page = AssetsPage.BREAKDOWN },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { androidx.compose.material3.Text("Record breakdown") }
                androidx.compose.material3.TextButton(
                    onClick = { page = AssetsPage.METER },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { androidx.compose.material3.Text("Meter reading") }
                androidx.compose.material3.TextButton(
                    onClick = { page = AssetsPage.DOCUMENTS },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { androidx.compose.material3.Text("Documents") }
                androidx.compose.material3.TextButton(
                    onClick = { page = AssetsPage.GALLERY },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { androidx.compose.material3.Text("Photo gallery") }
                androidx.compose.material3.TextButton(
                    onClick = { page = AssetsPage.REPORT },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { androidx.compose.material3.Text("Asset report") }
                recordActions()
                FarmEntitySelector(FarmSelectionAtoms.ASSET_SELECTOR, "Asset", assetOptions, assetId.value.ifBlank { null }, { assetId.value = it }, "Create an asset first.", enabled = !busy)
                androidx.compose.material3.OutlinedTextField(title.value, { title.value = it }, label = { androidx.compose.material3.Text("Maintenance title") }, modifier = Modifier.fillMaxWidth())
                androidx.compose.material3.OutlinedTextField(day.value, { day.value = it }, label = { androidx.compose.material3.Text("Date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
                androidx.compose.material3.Button(
                    onClick = {
                        run {
                            ops.recordMaintenance(
                                RecordMaintenance(UUID.randomUUID().toString(), assetId.value, title.value, LocalDate.parse(day.value).toEpochDay()),
                                newContext(),
                            )
                        }
                    },
                    enabled = !busy && assetId.value.isNotBlank() && title.value.isNotBlank() && day.value.isNotBlank(),
                ) { androidx.compose.material3.Text("Record maintenance") }
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
