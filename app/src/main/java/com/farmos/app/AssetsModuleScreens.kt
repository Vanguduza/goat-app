package com.farmos.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.farmos.core.database.FarmAssetEntity
import com.farmos.core.database.AssetMeterReadingEntity
import com.farmos.core.database.MaintenanceEventEntity
import com.farmos.core.design.FarmEntitySelector
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmSelectionAtoms
import com.farmos.core.design.FarmSelectorOption
import com.farmos.domain.ops.RecordAssetMeter
import com.farmos.domain.ops.RecordMaintenance
import java.time.LocalDate
import kotlinx.coroutines.launch

/** Internal pages of the asset module. */
internal enum class AssetsPage {
    HOME,
    SCHEDULE,
    BREAKDOWN,
    METER,
    DOCUMENTS,
    GALLERY,
    REPORT,
}

/**
 * Dedicated asset register and maintenance orchestration boundary.
 *
 * FOS-ASSET-004 — create asset: the home surface creates assets with code, name and kind (root tag FOS-ASSET-001).
 * FOS-ASSET-007 — maintenance job: the home surface records maintenance events per asset (root tag FOS-ASSET-001).
 */



/**
 * FOS-ASSET-006 — Maintenance Schedule: planned (future-dated) maintenance per asset and the
 * service history behind it. Maintenance events carry no due date or schedule entity, so overdue
 * items cannot be derived; only planned and past events are shown.
 */
@Composable
internal fun MaintenanceSchedulePage(
    assets: List<FarmAssetEntity>,
    events: List<MaintenanceEventEntity>,
    today: Long,
    onRecordBreakdown: () -> Unit,
    onBack: () -> Unit,
) {
    val names = assets.associate { it.id to "${it.code} · ${it.name}" }
    val upcoming = events.filter { it.occurredEpochDay >= today }.sortedBy { it.occurredEpochDay }
    val history = events.filter { it.occurredEpochDay < today }.sortedByDescending { it.occurredEpochDay }
    val latestByAsset = events.groupBy { it.assetId }.mapValues { (_, assetEvents) -> assetEvents.maxOf { it.occurredEpochDay } }
    FarmOperationalPage(
        screenId = "FOS-ASSET-006",
        title = "Maintenance schedule",
        subtitle = "Planned maintenance and service history per asset.",
        onBack = onBack,
    ) {
        FarmOperationalSection(
            title = "Upcoming",
            description = "Events recorded with a future date. Overdue cannot be shown: maintenance events carry no due date.",
        ) {
            if (upcoming.isEmpty()) {
                androidx.compose.material3.Text("No planned maintenance.")
            } else {
                upcoming.forEach { event ->
                    androidx.compose.material3.Text("${names[event.assetId] ?: "Asset not on this device"} · ${event.title} · ${LocalDate.ofEpochDay(event.occurredEpochDay)}")
                }
            }
        }
        FarmOperationalSection("Service history") {
            if (history.isEmpty()) {
                androidx.compose.material3.Text("No past maintenance events on this device.")
            } else {
                history.forEach { event ->
                    androidx.compose.material3.Text("${names[event.assetId] ?: "Asset not on this device"} · ${event.title} · ${LocalDate.ofEpochDay(event.occurredEpochDay)}")
                }
            }
        }
        FarmOperationalSection(
            title = "Per asset",
            description = "Latest service per asset from the events on this device.",
        ) {
            if (assets.isEmpty()) {
                androidx.compose.material3.Text("No assets on this device.")
            } else {
                assets.forEach { asset ->
                    val latest = latestByAsset[asset.id]?.let { LocalDate.ofEpochDay(it).toString() } ?: "never serviced"
                    androidx.compose.material3.Text("${asset.code} · ${asset.name} · last serviced $latest")
                }
            }
        }
        androidx.compose.material3.TextButton(
            onClick = onRecordBreakdown,
            modifier = Modifier.fillMaxWidth(),
        ) { androidx.compose.material3.Text("Record breakdown") }
    }
}

/**
 * FOS-ASSET-008 — Breakdown: records an unscheduled failure as a maintenance event through
 * RecordMaintenance (maintenance.record.v1).
 */
@Composable
internal fun BreakdownPage(
    assetOptions: List<FarmSelectorOption>,
    busy: Boolean,
    error: String?,
    onRecord: (assetId: String, title: String, day: Long, note: String?) -> Unit,
    onBack: () -> Unit,
) {
    val assetId = remember { mutableStateOf("") }
    val title = remember { mutableStateOf("") }
    val day = remember { mutableStateOf("") }
    val note = remember { mutableStateOf("") }
    FarmOperationalPage(
        screenId = "FOS-ASSET-008",
        title = "Record breakdown",
        subtitle = "An unscheduled failure, kept as a maintenance event.",
        onBack = onBack,
    ) {
        FarmOperationalSection("Breakdown") {
            FarmEntitySelector(
                FarmSelectionAtoms.ASSET_SELECTOR,
                "Asset",
                assetOptions,
                assetId.value.ifBlank { null },
                { assetId.value = it },
                "Create an asset first.",
                enabled = !busy,
            )
            androidx.compose.material3.OutlinedTextField(
                value = title.value,
                onValueChange = { title.value = it },
                label = { androidx.compose.material3.Text("What failed") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            )
            androidx.compose.material3.OutlinedTextField(
                value = day.value,
                onValueChange = { day.value = it },
                label = { androidx.compose.material3.Text("Date") },
                placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            )
            androidx.compose.material3.OutlinedTextField(
                value = note.value,
                onValueChange = { note.value = it },
                label = { androidx.compose.material3.Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            )
            androidx.compose.material3.Button(
                onClick = {
                    onRecord(assetId.value, title.value, LocalDate.parse(day.value).toEpochDay(), note.value)
                },
                enabled = !busy && assetId.value.isNotBlank() && title.value.isNotBlank() && day.value.isNotBlank(),
            ) {
                androidx.compose.material3.Text("Record breakdown")
            }
            error?.let { androidx.compose.material3.Text(it) }
        }
    }
}

/**
 * FOS-ASSET-005 — Meter or Usage Capture: records a meter reading (hours, kWh, litres) for one
 * asset through RecordAssetMeter (asset.meter_record.v1).
 */
@Composable
internal fun MeterCapturePage(
    assetOptions: List<FarmSelectorOption>,
    busy: Boolean,
    error: String?,
    onRecord: (assetId: String, value: Long, unit: String, day: Long, note: String?) -> Unit,
    onBack: () -> Unit,
) {
    val assetId = remember { mutableStateOf("") }
    val value = remember { mutableStateOf("") }
    val unit = remember { mutableStateOf("") }
    val day = remember { mutableStateOf("") }
    val note = remember { mutableStateOf("") }
    FarmOperationalPage(
        screenId = "FOS-ASSET-005",
        title = "Meter reading",
        subtitle = "A usage or meter reading for one asset.",
        onBack = onBack,
    ) {
        FarmOperationalSection("Reading") {
            FarmEntitySelector(
                FarmSelectionAtoms.ASSET_SELECTOR,
                "Asset",
                assetOptions,
                assetId.value.ifBlank { null },
                { assetId.value = it },
                "Create an asset first.",
                enabled = !busy,
            )
            androidx.compose.material3.OutlinedTextField(
                value = value.value,
                onValueChange = { value.value = it },
                label = { androidx.compose.material3.Text("Reading") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            )
            androidx.compose.material3.OutlinedTextField(
                value = unit.value,
                onValueChange = { unit.value = it },
                label = { androidx.compose.material3.Text("Unit (hours, kWh, litres)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            )
            androidx.compose.material3.OutlinedTextField(
                value = day.value,
                onValueChange = { day.value = it },
                label = { androidx.compose.material3.Text("Date") },
                placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            )
            androidx.compose.material3.OutlinedTextField(
                value = note.value,
                onValueChange = { note.value = it },
                label = { androidx.compose.material3.Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            )
            androidx.compose.material3.Button(
                onClick = {
                    onRecord(assetId.value, value.value.toLongOrNull() ?: -1L, unit.value, LocalDate.parse(day.value).toEpochDay(), note.value)
                },
                enabled = !busy && assetId.value.isNotBlank() && value.value.isNotBlank() && unit.value.isNotBlank() && day.value.isNotBlank(),
            ) {
                androidx.compose.material3.Text("Record reading")
            }
            error?.let { androidx.compose.material3.Text(it) }
        }
    }
}

/** Which attachment slice an asset page shows. */
internal enum class AssetAttachmentKind { DOCUMENTS, PHOTOS }

/**
 * FOS-ASSET-010 — Asset Documents, FOS-ASSET-011 — Asset Photo Gallery: the files kept against
 * one asset, newest first. Read-only: asset records are not in AttachmentRules.OWNER_TYPES
 * (only animal and task records carry attachments), so the add side is deliberately absent.
 */
@Composable
internal fun AssetAttachmentsPage(
    kind: AssetAttachmentKind,
    assetOptions: List<FarmSelectorOption>,
    loadAttachments: suspend (String) -> List<com.farmos.core.database.AttachmentEntity>,
    onBack: () -> Unit,
) {
    val screenId = if (kind == AssetAttachmentKind.DOCUMENTS) "FOS-ASSET-010" else "FOS-ASSET-011"
    val title = if (kind == AssetAttachmentKind.DOCUMENTS) "Asset documents" else "Asset photo gallery"
    val scope = rememberCoroutineScope()
    var assetId by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf<List<com.farmos.core.database.AttachmentEntity>?>(null) }

    fun load(id: String) {
        scope.launch {
            rows = null
            rows = runCatching { loadAttachments(id) }.getOrElse { emptyList() }
        }
    }

    FarmOperationalPage(
        screenId = screenId,
        title = title,
        subtitle = "Files kept against one asset, newest first.",
        onBack = onBack,
    ) {
        FarmEntitySelector(
            FarmSelectionAtoms.ASSET_SELECTOR,
            "Asset",
            assetOptions,
            assetId,
            { assetId = it; load(it) },
            "Create an asset first.",
            enabled = true,
        )
        FarmOperationalSection(
            title = if (kind == AssetAttachmentKind.DOCUMENTS) "Documents" else "Photos",
            description = if (kind == AssetAttachmentKind.DOCUMENTS) "PDF documents." else "Photos.",
        ) {
            val list = rows
            when {
                assetId == null -> androidx.compose.material3.Text("Choose an asset.")
                list == null -> androidx.compose.material3.Text("Reading files")
                else -> {
                    val filtered = list.filter {
                        if (kind == AssetAttachmentKind.DOCUMENTS) it.mediaType == "application/pdf"
                        else it.mediaType.startsWith("image/")
                    }
                    if (filtered.isEmpty()) {
                        androidx.compose.material3.Text("No files kept against this asset.")
                    } else {
                        filtered.forEach { row ->
                            androidx.compose.material3.Text("${row.displayName} · ${row.mediaType} · ${row.byteSize} bytes")
                        }
                    }
                }
            }
        }
        androidx.compose.material3.Text("Adding files to an asset is not supported yet: the attachment command accepts animal records only (D-015).")
    }
}

/**
 * FOS-ASSET-012 — Asset Report: the asset register with maintenance counts and latest meter
 * readings, from the records on this device.
 */
@Composable
internal fun AssetReportPage(
    assets: List<FarmAssetEntity>,
    events: List<MaintenanceEventEntity>,
    loadMeters: suspend (String) -> List<AssetMeterReadingEntity>,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var meters by remember { mutableStateOf<Map<String, AssetMeterReadingEntity>?>(null) }
    androidx.compose.runtime.LaunchedEffect(assets) {
        meters = runCatching {
            assets.associate { asset ->
                asset.id to loadMeters(asset.id).maxByOrNull { it.occurredEpochDay }
            }.mapNotNull { (id, reading) -> reading?.let { id to it } }.toMap()
        }.getOrElse { emptyMap() }
    }
    val eventsByAsset = events.groupBy { it.assetId }
    FarmOperationalPage(
        screenId = "FOS-ASSET-012",
        title = "Asset report",
        subtitle = "The register with service history and latest readings.",
        onBack = onBack,
    ) {
        if (assets.isEmpty()) {
            androidx.compose.material3.Text("No assets on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection(
            title = "Register",
            description = "Maintenance events and latest meter reading per asset.",
        ) {
            assets.forEach { asset ->
                val count = eventsByAsset[asset.id]?.size ?: 0
                val latest = eventsByAsset[asset.id]?.maxOfOrNull { it.occurredEpochDay }
                    ?.let { LocalDate.ofEpochDay(it).toString() } ?: "never serviced"
                val meter = meters?.get(asset.id)
                    ?.let { "${it.readingValue} ${it.unit} on ${LocalDate.ofEpochDay(it.occurredEpochDay)}" }
                    ?: "no readings"
                androidx.compose.material3.Text("${asset.code} · ${asset.name} · $count events · last serviced $latest · meter: $meter")
            }
        }
    }
}
