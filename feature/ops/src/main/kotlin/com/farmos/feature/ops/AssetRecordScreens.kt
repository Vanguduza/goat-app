package com.farmos.feature.ops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmVisualClass
import java.time.LocalDate

/** One recorded maintenance event; [assetLabel] is the asset's code and name as recorded. */
data class AssetServiceView(val id: String, val assetId: String, val assetLabel: String, val title: String, val note: String?, val epochDay: Long)

/** A registered asset with its exhaustive maintenance summary. */
data class AssetView(val id: String, val code: String, val name: String, val kind: String, val serviceCount: Int, val latestServiceEpochDay: Long?)

/**
 * Farm-scoped asset records. [services] may be only the latest rows; [serviceCount] and each
 * asset's summary are exhaustive aggregates. Nothing here writes; no depreciation is derived.
 */
data class AssetRecords(
    val assets: List<AssetView> = emptyList(),
    val services: List<AssetServiceView> = emptyList(),
    val serviceCount: Int? = null,
)

enum class AssetRecordPage(val label: String) {
    REGISTER("Asset register"),
    DETAIL("Asset detail"),
    SERVICE_HISTORY("Service history"),
}

private fun AssetView.serviceSummary(): String =
    if (serviceCount == 0) "No maintenance recorded"
    else "$serviceCount maintenance " + (if (serviceCount == 1) "record" else "records") +
        (latestServiceEpochDay?.let { " · last ${LocalDate.ofEpochDay(it)}" } ?: "")

@Composable
private fun AssetRow(primary: String, secondary: String, tag: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(tag), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(primary, color = AnimalFarmTheme.colors.mutedInk)
        Text(secondary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Asset record navigation. [home] renders the assets home (FOS-ASSET-001) and places the supplied
 * record actions; each action opens a read-only record page whose back returns home.
 */
@Composable
fun AssetRecordNavigator(records: AssetRecords, home: @Composable (recordActions: @Composable () -> Unit) -> Unit) {
    var page by rememberSaveable { mutableStateOf<AssetRecordPage?>(null) }
    val back = { page = null }
    when (page) {
        null -> home {
            FarmOperationalSection("Records") {
                AssetRecordPage.entries.forEach { target ->
                    TextButton(onClick = { page = target }, modifier = Modifier.fillMaxWidth()) { Text("Open ${target.label}") }
                }
            }
        }
        AssetRecordPage.REGISTER -> AssetRegisterScreen(records, back)
        AssetRecordPage.DETAIL -> AssetDetailScreen(records, back)
        AssetRecordPage.SERVICE_HISTORY -> AssetServiceHistoryScreen(records, back)
    }
}

/** FOS-ASSET-002 — every registered asset with its recorded maintenance summary. */
@Composable
internal fun AssetRegisterScreen(records: AssetRecords, onBack: () -> Unit) {
    FarmOperationalPage("FOS-ASSET-002", "Asset register", "Registered assets and their recorded maintenance. Not a depreciation ledger.", FarmVisualClass.I3, onBack) {
        if (records.assets.isEmpty()) {
            AnimalFarmEmptyState("No assets on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection("Assets · ${records.assets.size}") {
            records.assets.forEachIndexed { index, asset ->
                if (index > 0) HorizontalDivider()
                AssetRow("${asset.code} · ${asset.name} · ${asset.kind}", asset.serviceSummary(), "asset:${asset.id}")
            }
        }
    }
}

/** FOS-ASSET-003 — one asset as registered, with its maintenance records. */
@Composable
internal fun AssetDetailScreen(records: AssetRecords, onBack: () -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf(records.assets.firstOrNull()?.id) }
    FarmOperationalPage("FOS-ASSET-003", "Asset detail", "One registered asset and its recorded maintenance.", FarmVisualClass.I3, onBack) {
        if (records.assets.isEmpty()) {
            AnimalFarmEmptyState("No assets on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection("Assets") {
            records.assets.forEach { asset ->
                val selected = asset.id == selectedId
                TextButton(
                    onClick = { selectedId = asset.id },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                        .semantics { this.selected = selected }
                        .testTag("asset-option:${asset.id}"),
                ) { Text("${asset.code} · ${asset.name}", fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
            }
        }
        val asset = records.assets.firstOrNull { it.id == selectedId } ?: return@FarmOperationalPage
        FarmOperationalSection(asset.name) {
            AssetRow("Code", asset.code, "asset-code")
            AssetRow("Kind", asset.kind, "asset-kind")
            AssetRow("Maintenance", asset.serviceSummary(), "asset-service-summary")
        }
        val services = records.services.filter { it.assetId == asset.id }
        FarmOperationalSection(recordListTitle("Maintenance records", services.size, asset.serviceCount)) {
            if (asset.serviceCount == 0) Text("No maintenance recorded for this asset.", color = AnimalFarmTheme.colors.mutedInk)
            services.forEach { AssetRow(LocalDate.ofEpochDay(it.epochDay).toString(), it.title + (it.note?.let { n -> " · $n" } ?: ""), "asset-detail-service:${it.id}") }
        }
    }
}

/** FOS-ASSET-009 — recorded maintenance across assets, newest first. */
@Composable
internal fun AssetServiceHistoryScreen(records: AssetRecords, onBack: () -> Unit) {
    FarmOperationalPage("FOS-ASSET-009", "Service history", "Recorded maintenance on this device, newest first.", FarmVisualClass.I3, onBack) {
        if (records.services.isEmpty()) {
            AnimalFarmEmptyState("No maintenance recorded on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection(recordListTitle("Maintenance records", records.services.size, records.serviceCount)) {
            records.services.forEachIndexed { index, service ->
                if (index > 0) HorizontalDivider()
                AssetRow(
                    "${LocalDate.ofEpochDay(service.epochDay)} · ${service.assetLabel}",
                    service.title + (service.note?.let { " · $it" } ?: ""),
                    "asset-service:${service.id}",
                )
            }
        }
    }
}
