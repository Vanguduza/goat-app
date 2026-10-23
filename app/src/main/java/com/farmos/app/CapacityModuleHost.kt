package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.design.runSuspendCatching
import java.time.LocalDate

internal enum class CapacityPage(val screenId: String, val title: String) {
    DASHBOARD("FOS-CAP-001", "Capacity"),
    HOUSING("FOS-CAP-002", "Housing capacity"),
    STOCKING("FOS-CAP-003", "Stocking pressure"),
    CAGES("FOS-CAP-004", "Cage capacity"),
    POULTRY_HOUSES("FOS-CAP-005", "Poultry house capacity"),
    PADDOCKS("FOS-CAP-006", "Paddock capacity"),
    FEED("FOS-CAP-007", "Feed capacity"),
    WATER("FOS-CAP-008", "Water capacity"),
    FORECAST("FOS-CAP-009", "Capacity forecast"),
    ALERTS("FOS-CAP-010", "Capacity alerts"),
}

/** FOS-CAP-001..010: P04/P10, Animal Farm A. Room reads and explicit scenarios, never writes. */
@Composable
fun CapacityModuleHost(database: FarmOsDatabase, farmId: String, onBack: () -> Unit) {
    var page by rememberSaveable(farmId) { mutableStateOf(CapacityPage.DASHBOARD) }
    var model by remember(farmId) { mutableStateOf<CapacityModel?>(null) }
    var error by remember(farmId) { mutableStateOf<String?>(null) }
    LaunchedEffect(farmId) {
        runSuspendCatching { loadCapacityModel(database, farmId, LocalDate.now()) }
            .onSuccess { model = it }.onFailure { error = it.message ?: "Could not read capacity records" }
    }
    CapacityWorkspace(page, model, error, { page = it }, onBack)
}
