package com.farmos.feature.goat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmCanvas
import com.farmos.core.design.AnimalFarmFamily
import com.farmos.core.design.AnimalFarmModuleHeader
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.AnimalFarmWarningSurface
import com.farmos.core.design.FarmIllustratedSectionSurface
import com.farmos.core.design.FarmStorySurface
import com.farmos.core.design.FosDimens
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.GoatSnapshot
import com.farmos.domain.goat.GoatStatus
import java.time.LocalDate

@Composable
internal fun GoatExperienceScreen(
    state: GoatSliceUiState,
    actions: GoatExperienceActions,
    onBackToFarm: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: GoatPage = GoatPage.DASHBOARD,
    today: LocalDate = LocalDate.now(),
) {
    var page by remember { mutableStateOf(initialPage) }
    val selected = state.selected

    when (page) {
        GoatPage.DASHBOARD -> GoatDashboardScreen(
            state = state,
            onOpen = { page = it },
            onBackToFarm = onBackToFarm,
            onSignOut = onSignOut,
            modifier = modifier,
            today = today,
        )
        GoatPage.HERD -> GoatHerdScreen(
            state = state,
            onSelect = {
                actions.onSelectGoat(it)
                page = GoatPage.PROFILE
            },
            onRegister = { page = GoatPage.REGISTER },
            onBack = { page = GoatPage.DASHBOARD },
        )
        GoatPage.PROFILE -> GoatProfileScreen(
            state = state,
            onOpen = { page = it },
            onBack = { page = GoatPage.DASHBOARD },
        )
        GoatPage.REGISTER -> GoatRegisterScreen(actions.onRegister) { page = GoatPage.DASHBOARD }
        GoatPage.WEIGHT -> GoatWeightScreen(
            state = state,
            onRecordWeight = actions.onRecordWeight,
            onSelectGoat = actions.onSelectGoat,
            onBack = {
                if (initialPage == GoatPage.WEIGHT) onBackToFarm() else page = GoatPage.PROFILE
            },
        )
        GoatPage.HEALTH -> GoatHealthCaptureScreen(state, actions) { page = GoatPage.PROFILE }
        GoatPage.REPRODUCTION -> GoatReproductionScreen(
            state,
            actions,
            onSelectGoat = actions.onSelectGoat,
        ) {
            if (initialPage == GoatPage.REPRODUCTION) onBackToFarm() else page = GoatPage.PROFILE
        }
        GoatPage.KIDDING -> GoatKiddingScreen(
            state,
            actions,
            onSelectGoat = actions.onSelectGoat,
        ) {
            if (initialPage == GoatPage.KIDDING) onBackToFarm() else page = GoatPage.PROFILE
        }
        GoatPage.SEARCH -> GoatSearchScreen(
            state = state,
            onSearch = actions.onSearch,
            onSelect = {
                actions.onSelectGoat(it)
                page = GoatPage.PROFILE
            },
            onBack = {
                if (initialPage == GoatPage.SEARCH) onBackToFarm() else page = GoatPage.DASHBOARD
            },
        )
        GoatPage.SYNC -> GoatSyncScreen(state, actions.onSyncNow) {
            if (initialPage == GoatPage.SYNC) onBackToFarm() else page = GoatPage.DASHBOARD
        }
        GoatPage.STATUS_CHANGE -> GoatStatusChangeScreen(state, actions.onSetStatus) { page = GoatPage.PROFILE }
    }
}

/** FOS-GOAT-001 — illustrated goat operating dashboard derived from FOS-VREF-GOAT-001. */
@Composable
private fun GoatDashboardScreen(
    state: GoatSliceUiState,
    onOpen: (GoatPage) -> Unit,
    onBackToFarm: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier,
    today: LocalDate,
) {
    val active = state.herd.filter { it.status == GoatStatus.ACTIVE }
    val does = active.count { it.sex == GoatSex.FEMALE }
    val bucks = active.count { it.sex == GoatSex.MALE }
    val todayEpochDay = today.toEpochDay()
    val kids = active.count { goat -> goat.dateOfBirthEpochDay?.let { todayEpochDay - it < 365 } == true }
    val selectedDoe = state.selected?.takeIf { it.status == GoatStatus.ACTIVE && it.sex == GoatSex.FEMALE }

    AnimalFarmCanvas(modifier) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimalFarmModuleHeader(
                title = "Goats",
                subtitle = state.farmName ?: "Goat records on this device",
                family = AnimalFarmFamily.GOAT,
            )

            val metrics = listOf(
                "Total" to active.size.toString(),
                "Does" to does.toString(),
                "Bucks" to bucks.toString(),
                "Kids" to kids.toString(),
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val twoColumns = maxWidth < 420.dp && LocalDensity.current.fontScale >= 1.5f
                if (twoColumns) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        metrics.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                pair.forEach { (label, value) ->
                                    GoatMetric(label, value, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        metrics.forEach { (label, value) ->
                            GoatMetric(label, value, Modifier.weight(1f))
                        }
                    }
                }
            }

            GoatDashboardAction("Herd", "Herd on this device") { onOpen(GoatPage.HERD) }
            GoatDashboardAction("Register goat", "Add a goat record") { onOpen(GoatPage.REGISTER) }
            GoatDashboardAction("Breeding", "Heat, mating and pregnancy") {
                onOpen(if (selectedDoe != null) GoatPage.REPRODUCTION else GoatPage.HERD)
            }
            GoatDashboardAction("Kidding", "Due dates and birth records") {
                onOpen(if (selectedDoe != null) GoatPage.KIDDING else GoatPage.HERD)
            }
            GoatDashboardAction("Health", "FAMACHA, BCS and SCC on this device") {
                onOpen(if (state.selected != null) GoatPage.HEALTH else GoatPage.HERD)
            }
            GoatDashboardAction("Milk", "Production and SCC") {
                onOpen(if (selectedDoe != null) GoatPage.REPRODUCTION else GoatPage.HERD)
            }
            GoatDashboardAction("Growth", "Weights and growth rates") {
                onOpen(if (state.selected != null) GoatPage.WEIGHT else GoatPage.HERD)
            }
            GoatDashboardAction("Search", state.searchMessage) { onOpen(GoatPage.SEARCH) }

            state.selected?.let { goat ->
                FarmIllustratedSectionSurface(Modifier.clickable { onOpen(GoatPage.PROFILE) }) {
                    Text("Current animal", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(goatDisplayName(goat), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(goat.tag + " · " + goatStatusLabel(goat.status))
                    goat.latestWeightGrams?.let { Text("${formatKg(it)} kg latest weight") }
                }
            }

            FarmIllustratedSectionSurface {
                Text(state.syncMessage, style = MaterialTheme.typography.bodyMedium)
                Text("${state.pendingSyncCount} local change(s) waiting", style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { onOpen(GoatPage.SYNC) }, modifier = Modifier.heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)) { Text("Open sync status") }
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBackToFarm, modifier = Modifier.heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)) { Text("Farm home") }
                TextButton(onClick = onSignOut, modifier = Modifier.heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)) { Text("Sign out") }
            }
        }
    }
}

@Composable
private fun GoatDashboardAction(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    FarmIllustratedSectionSurface(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GoatMetric(label: String, value: String, modifier: Modifier = Modifier) {
    FarmIllustratedSectionSurface(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private enum class GoatHerdFilter { ALL, ACTIVE, SOLD, DEAD, CULLED }

@Composable
private fun GoatHerdScreen(
    state: GoatSliceUiState,
    onSelect: (String) -> Unit,
    onRegister: () -> Unit,
    onBack: () -> Unit,
) {
    var filter by remember { mutableStateOf(GoatHerdFilter.ACTIVE) }
    val rows = state.herd.filter {
        when (filter) {
            GoatHerdFilter.ALL -> true
            GoatHerdFilter.ACTIVE -> it.status == GoatStatus.ACTIVE
            GoatHerdFilter.SOLD -> it.status == GoatStatus.SOLD
            GoatHerdFilter.DEAD -> it.status == GoatStatus.DEAD
            GoatHerdFilter.CULLED -> it.status == GoatStatus.CULLED
        }
    }
    IllustratedGoatPage("Herd", "FOS-GOAT-002", onBack) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wrapFilters = maxWidth < 520.dp || LocalDensity.current.fontScale >= 1.5f
            val rows = if (wrapFilters) GoatHerdFilter.entries.chunked(3) else listOf(GoatHerdFilter.entries)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                rows.forEach { options ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        options.forEach { option ->
                            GoatHerdFilterButton(
                                option = option,
                                selected = filter == option,
                                onClick = { filter = option },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - options.size) {
                            if (wrapFilters) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        when (state.herdState) {
            LoadableSurfaceState.LOADING -> Text("Loading herd")
            LoadableSurfaceState.EMPTY -> Text("No goats on this device yet.")
            LoadableSurfaceState.ERROR -> Text(state.error ?: "Herd could not be loaded", color = MaterialTheme.colorScheme.error)
            LoadableSurfaceState.DISABLED -> Text("Herd actions are temporarily paused")
            LoadableSurfaceState.IDLE -> if (rows.isEmpty()) {
                Text("No ${filter.name.lowercase()} goats on this device.")
            } else rows.forEach { goat -> GoatHerdRow(goat) { onSelect(goat.animalId) } }
        }
        Button(onClick = onRegister, modifier = Modifier.fillMaxWidth().heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)) { Text("Register goat") }
    }
}

@Composable
private fun GoatHerdFilterButton(
    option: GoatHerdFilter,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = AnimalFarmTheme.minimumTouchDp.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
    ) {
        val label = option.name.lowercase()
        Text(
            if (selected) "$label ✓" else label,
            maxLines = 1,
            softWrap = false,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun GoatHerdRow(goat: GoatSnapshot, onClick: () -> Unit) {
    FarmIllustratedSectionSurface(Modifier.clickable(onClick = onClick)) {
        Text(goatDisplayName(goat), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(goat.tag + " · " + goat.sex.name.lowercase() + " · " + goatStatusLabel(goat.status))
        goat.latestWeightGrams?.let { Text("${formatKg(it)} kg") }
        if (goat.syncPending) Text("Waiting to sync", color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun GoatProfileScreen(
    state: GoatSliceUiState,
    onOpen: (GoatPage) -> Unit,
    onBack: () -> Unit,
) {
    val goat = state.selected
    IllustratedGoatPage("Goat profile", "FOS-GOAT-003", onBack) {
        if (goat == null) {
            Text("Select a goat from the herd before opening its profile.")
            return@IllustratedGoatPage
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 720.dp
            if (wide) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        GoatIdentityCard(goat)
                        GoatProfileActions(goat, onOpen)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        GoatHistoryCard(goat)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GoatIdentityCard(goat)
                    GoatProfileActions(goat, onOpen)
                    GoatHistoryCard(goat)
                }
            }
        }
    }
}

@Composable
private fun GoatIdentityCard(goat: GoatSnapshot) {
    FarmStorySurface(Modifier.fillMaxWidth()) {
        Text(goatDisplayName(goat), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(goat.tag + " · " + goat.sex.name.lowercase() + " · " + goatStatusLabel(goat.status))
        goat.dateOfBirthEpochDay?.let { Text("Born ${LocalDate.ofEpochDay(it)}") }
        goat.latestWeightGrams?.let { Text("${formatKg(it)} kg latest weight") }
        goat.averageDailyGainGrams?.let { Text("$it g/day average daily gain") }
        if (goat.syncPending) Text("Saved locally · waiting to sync", color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun GoatProfileActions(goat: GoatSnapshot, onOpen: (GoatPage) -> Unit) {
    FarmIllustratedSectionSurface {
        Text("Record", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        val enabled = goat.status == GoatStatus.ACTIVE
        val actions = buildList {
            add("Weight" to GoatPage.WEIGHT)
            add("Health" to GoatPage.HEALTH)
            if (goat.sex == GoatSex.FEMALE) add("Reproduction" to GoatPage.REPRODUCTION)
            if (goat.sex == GoatSex.FEMALE) add("Kidding" to GoatPage.KIDDING)
            add("Lifecycle" to GoatPage.STATUS_CHANGE)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked = maxWidth < 480.dp
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    actions.forEach { (label, page) ->
                        TextButton(
                            onClick = { onOpen(page) },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth().heightIn(min = AnimalFarmTheme.minimumTouchDp.dp),
                        ) {
                            Text(label, maxLines = 1, softWrap = false)
                        }
                    }
                }
            } else {
                actions.chunked(3).forEach { rowActions ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        rowActions.forEach { (label, page) ->
                            TextButton(
                                onClick = { onOpen(page) },
                                enabled = enabled,
                                modifier = Modifier.weight(1f).heightIn(min = AnimalFarmTheme.minimumTouchDp.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            ) {
                                Text(label, maxLines = 1, softWrap = false)
                            }
                        }
                        repeat(3 - rowActions.size) {
                            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoatHistoryCard(goat: GoatSnapshot) {
    FarmIllustratedSectionSurface {
        Text("History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (goat.weightHistory.isEmpty()) Text("No weights recorded yet.")
        goat.weightHistory.asReversed().take(6).forEach { Text("${weightDate(it.measuredAtEpochMillis)} · ${formatKg(it.weightGrams)} kg") }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        if (goat.famachaHistory.isNotEmpty()) Text("Latest FAMACHA ${goat.famachaHistory.first().score}")
        if (goat.bcsHistory.isNotEmpty()) {
            val bcs = goat.bcsHistory.first().scoreTenths
            Text("Latest BCS ${bcs / 10}.${bcs % 10}")
        }
        if (goat.kiddingHistory.isNotEmpty()) Text("${goat.kiddingHistory.size} kidding record(s)")
        if (goat.milkHistory.isNotEmpty()) Text("${goat.milkHistory.size} milk record(s)")
    }
}

@Composable
private fun GoatStatusChangeScreen(
    state: GoatSliceUiState,
    onSetStatus: (GoatStatus) -> Unit,
    onBack: () -> Unit,
) {
    var pending by remember { mutableStateOf<GoatStatus?>(null) }
    IllustratedGoatPage("Lifecycle change", "FOS-GOAT-051 · I4", onBack, safety = true) {
        val goat = state.selected
        if (goat == null) {
            Text("No goat selected.")
            return@IllustratedGoatPage
        }
        AnimalFarmWarningSurface {
            Text(goatDisplayName(goat), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("A lifecycle change removes this goat from the active herd. Existing history remains on the record.")
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked = maxWidth < 480.dp
            val changes = listOf(
                "Mark sold" to GoatStatus.SOLD,
                "Mark deceased" to GoatStatus.DEAD,
                "Mark culled" to GoatStatus.CULLED,
            )
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    changes.forEach { (label, status) ->
                        TextButton(
                            onClick = { pending = status },
                            modifier = Modifier.fillMaxWidth().heightIn(min = AnimalFarmTheme.minimumTouchDp.dp),
                        ) {
                            Text(label, maxLines = 1, softWrap = false)
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    changes.forEach { (label, status) ->
                        TextButton(
                            onClick = { pending = status },
                            modifier = Modifier.weight(1f).heightIn(min = AnimalFarmTheme.minimumTouchDp.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        ) {
                            Text(label, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
        pending?.let { next ->
            AnimalFarmWarningSurface {
                Text("Confirm ${goatStatusLabel(next).lowercase()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("This is an authoritative farm record after sync. Check the animal and status before continuing.")
                Button(
                    onClick = {
                        onSetStatus(next)
                        pending = null
                    },
                    enabled = !state.busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                        .testTag("goat-lifecycle-confirm"),
                ) { Text("Confirm status change") }
                TextButton(onClick = { pending = null }, enabled = !state.busy, modifier = Modifier.heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)) { Text("Cancel") }
            }
        }
    }
}

@Composable
internal fun IllustratedGoatPage(
    title: String,
    screenId: String,
    onBack: () -> Unit,
    safety: Boolean = false,
    content: @Composable () -> Unit,
) {
    AnimalFarmCanvas {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimalFarmModuleHeader(
                title = title,
                subtitle = if (safety) "Safety-priority surface" else "Goat records on this device",
                family = if (safety) null else AnimalFarmFamily.GOAT,
            )
            content()
            TextButton(
                onClick = onBack,
                modifier = Modifier.sizeIn(
                    minWidth = AnimalFarmTheme.minimumTouchDp.dp,
                    minHeight = AnimalFarmTheme.minimumTouchDp.dp,
                ),
            ) { Text("Back") }
        }
    }
}
