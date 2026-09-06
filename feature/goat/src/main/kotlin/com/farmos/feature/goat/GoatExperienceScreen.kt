package com.farmos.feature.goat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.FarmIllustratedSectionSurface
import com.farmos.core.design.FarmOsAccentMedium
import com.farmos.core.design.FarmPastoralBackdrop
import com.farmos.core.design.FarmSpeciesVisual
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
        GoatPage.REPRODUCTION -> GoatReproductionScreen(state, actions) { page = GoatPage.PROFILE }
        GoatPage.KIDDING -> GoatKiddingScreen(state, actions) { page = GoatPage.PROFILE }
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
        GoatPage.SYNC -> GoatSyncScreen(state, actions.onSyncNow) { page = GoatPage.DASHBOARD }
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
) {
    val active = state.herd.filter { it.status == GoatStatus.ACTIVE }
    val does = active.count { it.sex == GoatSex.FEMALE }
    val bucks = active.count { it.sex == GoatSex.MALE }
    val today = LocalDate.now().toEpochDay()
    val kids = active.count { goat -> goat.dateOfBirthEpochDay?.let { today - it < 365 } == true }
    val selectedDoe = state.selected?.takeIf { it.status == GoatStatus.ACTIVE && it.sex == GoatSex.FEMALE }

    FarmPastoralBackdrop(modifier.fillMaxSize(), heroSpecies = FarmSpeciesVisual.GOAT) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FarmStorySurface(Modifier.fillMaxWidth()) {
                Text("Goats", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(state.farmName ?: "Your goat herd", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Healthy animals. Thriving farms.", style = FarmOsAccentMedium, color = MaterialTheme.colorScheme.primary)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GoatMetric("Total", active.size.toString(), Modifier.weight(1f))
                GoatMetric("Does", does.toString(), Modifier.weight(1f))
                GoatMetric("Bucks", bucks.toString(), Modifier.weight(1f))
                GoatMetric("Kids", kids.toString(), Modifier.weight(1f))
            }

            GoatDashboardAction("Herd", "View and manage your goats") { onOpen(GoatPage.HERD) }
            GoatDashboardAction("Register Goat", "Add a new goat") { onOpen(GoatPage.REGISTER) }
            GoatDashboardAction("Breeding", "Heat, mating and pregnancy") {
                onOpen(if (selectedDoe != null) GoatPage.REPRODUCTION else GoatPage.HERD)
            }
            GoatDashboardAction("Kidding", "Due dates and birth records") {
                onOpen(if (selectedDoe != null) GoatPage.KIDDING else GoatPage.HERD)
            }
            GoatDashboardAction("Health", "Treatments, FAMACHA, BCS and SCC observations") {
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
                TextButton(onClick = { onOpen(GoatPage.SYNC) }) { Text("Open sync status") }
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBackToFarm) { Text("Farm home") }
                TextButton(onClick = onSignOut) { Text("Sign out") }
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            GoatHerdFilter.entries.forEach { option ->
                TextButton(onClick = { filter = option }) {
                    Text(if (filter == option) "${option.name.lowercase()} · selected" else option.name.lowercase())
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
        Button(onClick = onRegister, modifier = Modifier.fillMaxWidth()) { Text("Register goat") }
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { onOpen(GoatPage.WEIGHT) }, enabled = goat.status == GoatStatus.ACTIVE) { Text("Weight") }
            TextButton(onClick = { onOpen(GoatPage.HEALTH) }, enabled = goat.status == GoatStatus.ACTIVE) { Text("Health") }
            if (goat.sex == GoatSex.FEMALE) {
                TextButton(onClick = { onOpen(GoatPage.REPRODUCTION) }, enabled = goat.status == GoatStatus.ACTIVE) { Text("Reproduction") }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (goat.sex == GoatSex.FEMALE) {
                TextButton(onClick = { onOpen(GoatPage.KIDDING) }, enabled = goat.status == GoatStatus.ACTIVE) { Text("Kidding") }
            }
            TextButton(onClick = { onOpen(GoatPage.STATUS_CHANGE) }, enabled = goat.status == GoatStatus.ACTIVE) { Text("Lifecycle") }
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
        Text(goatDisplayName(goat), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("A lifecycle change removes this goat from the active herd. Existing history remains on the record.")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { pending = GoatStatus.SOLD }) { Text("Mark sold") }
            TextButton(onClick = { pending = GoatStatus.DEAD }) { Text("Mark deceased") }
            TextButton(onClick = { pending = GoatStatus.CULLED }) { Text("Mark culled") }
        }
        pending?.let { next ->
            FarmIllustratedSectionSurface {
                Text("Confirm ${goatStatusLabel(next).lowercase()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("This is an authoritative farm record after sync. Check the animal and status before continuing.")
                Button(
                    onClick = {
                        onSetStatus(next)
                        pending = null
                    },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Confirm status change") }
                TextButton(onClick = { pending = null }, enabled = !state.busy) { Text("Cancel") }
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
    FarmPastoralBackdrop(Modifier.fillMaxSize(), heroSpecies = if (safety) null else FarmSpeciesVisual.GOAT) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FarmStorySurface(Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (safety) Text("Safety-priority surface", color = MaterialTheme.colorScheme.error)
            }
            content()
            TextButton(onClick = onBack) { Text("Back") }
        }
    }
}
