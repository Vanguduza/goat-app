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
import com.farmos.core.database.MaintenanceEventEntity
import com.farmos.core.design.FarmEntitySelector
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmSelectionAtoms
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.CreateFarmAsset
import com.farmos.domain.ops.RecordMaintenance
import com.farmos.feature.ops.AssetRecordNavigator
import com.farmos.feature.ops.AssetRecords
import com.farmos.feature.ops.SimpleCaptureScreen
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

/** Internal pages of the asset module. */
private enum class AssetsPage {
    HOME,
    SCHEDULE,
    BREAKDOWN,
}

/**
 * Dedicated asset register and maintenance orchestration boundary.
 *
 * FOS-ASSET-004 — create asset: the home surface creates assets with code, name and kind (root tag FOS-ASSET-001).
 * FOS-ASSET-007 — maintenance job: the home surface records maintenance events per asset (root tag FOS-ASSET-001).
 */
@Composable
fun AssetsModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadRecords: suspend () -> AssetRecords = { AssetRecords() },
) {
    val scope = rememberCoroutineScope()
    var page by remember(farmId) { mutableStateOf(AssetsPage.HOME) }
    var rows by remember { mutableStateOf(emptyList<String>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var records by remember(farmId) { mutableStateOf(AssetRecords()) }
    var assetOptions by remember(farmId) { mutableStateOf(emptyList<FarmSelectorOption>()) }
    var assetEntities by remember(farmId) { mutableStateOf(emptyList<FarmAssetEntity>()) }
    var maintenanceEvents by remember(farmId) { mutableStateOf(emptyList<MaintenanceEventEntity>()) }

    suspend fun refresh() {
        val assets = ops.assets()
        rows = assets.map { "${it.id} ${it.code} · ${it.name}" }
        assetOptions = assets.map { FarmSelectorOption(it.id, "${it.code} · ${it.name}", it.kind) }
        assetEntities = assets
        maintenanceEvents = ops.recentMaintenance()
        records = loadRecords()
    }

    LaunchedEffect(farmId) { runCatching { refresh() } }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            runCatching {
                block()
                refresh()
            }.onSuccess { enqueueSync() }
                .onFailure { error = it.message }
            busy = false
        }
    }

    val code = remember { mutableStateOf("") }
    val name = remember { mutableStateOf("") }
    val kind = remember { mutableStateOf("equipment") }
    val assetId = remember { mutableStateOf("") }
    val title = remember { mutableStateOf("") }
    val day = remember { mutableStateOf("") }

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
    }
}

/**
 * FOS-ASSET-006 — Maintenance Schedule: planned (future-dated) maintenance per asset and the
 * service history behind it. Maintenance events carry no due date or schedule entity, so overdue
 * items cannot be derived; only planned and past events are shown.
 */
@Composable
private fun MaintenanceSchedulePage(
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
private fun BreakdownPage(
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
