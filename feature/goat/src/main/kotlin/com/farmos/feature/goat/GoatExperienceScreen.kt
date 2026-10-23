package com.farmos.feature.goat

import com.farmos.core.design.FarmFilterChip

import com.farmos.core.design.FarmToolGroup

import com.farmos.core.design.FarmToolAction

import com.farmos.core.design.FarmModuleTools

import com.farmos.core.design.FarmMetricValue

import com.farmos.core.design.FarmMetricStrip

import com.farmos.core.design.FarmBackButton

import com.farmos.core.design.FarmActionButton

import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.foundation.layout.imePadding

import androidx.compose.runtime.saveable.rememberSaveableStateHolder

import androidx.compose.runtime.saveable.rememberSaveable

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
import com.farmos.core.design.FarmPrimaryButton as Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.farmos.core.design.FarmActionButton as TextButton
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
import com.farmos.core.design.FarmBackHandler
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
    weanings: List<GoatWeaningView> = emptyList(),
    movements: List<GoatMovementView> = emptyList(),
    identifiers: List<GoatIdentifierView> = emptyList(),
    goatGroups: List<GoatGroupView> = emptyList(),
    pedigreeParentLabels: List<String> = emptyList(),
) {
    var page by rememberSaveable(initialPage) { mutableStateOf(initialPage) }
    var selectedKiddingId by remember { mutableStateOf<String?>(null) }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    val selected = state.selected
    var reproductionSection by rememberSaveable { mutableStateOf("Mating") }

    val pageStateHolder = rememberSaveableStateHolder()
    pageStateHolder.SaveableStateProvider(page.name) {
    when (page) {
        GoatPage.DASHBOARD -> GoatDashboardScreen(
            state = state,
            onOpen = { if (it == GoatPage.REPRODUCTION) reproductionSection = "Mating"; page = it },
            onBackToFarm = onBackToFarm,
            onSignOut = onSignOut,
            modifier = modifier,
            today = today,
            onOpenMilk = { reproductionSection = "Milk"; page = if (selected?.sex == GoatSex.FEMALE && selected?.status == GoatStatus.ACTIVE) GoatPage.REPRODUCTION else GoatPage.HERD },
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
            onOpen = { if (it == GoatPage.REPRODUCTION) reproductionSection = "Mating"; page = it },
            onBack = { if (initialPage == GoatPage.PROFILE) onBackToFarm() else page = GoatPage.DASHBOARD },
            attachments = actions.profileAttachments,
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
            onCompareMates = { page = GoatPage.MATE_COMPARE },
            initialSection = reproductionSection,
            onSectionChange = { reproductionSection = it },
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
        GoatPage.SCAN -> GoatScanScreen(
            state = state,
            onScan = actions.onScanIdentifier,
            onSelect = {
                actions.onSelectGoat(it)
                page = GoatPage.PROFILE
            },
            onBack = {
                if (initialPage == GoatPage.SCAN) onBackToFarm() else page = GoatPage.DASHBOARD
            },
        )
        GoatPage.SYNC -> GoatSyncScreen(state, actions.onSyncNow) {
            if (initialPage == GoatPage.SYNC) onBackToFarm() else page = GoatPage.DASHBOARD
        }
        GoatPage.MATE_COMPARE -> GoatMateCompareScreen(state, actions) { page = GoatPage.REPRODUCTION }
        GoatPage.STATUS_CHANGE -> GoatStatusChangeScreen(state, { page = it }, actions.onReverseExit) { page = GoatPage.PROFILE }
        GoatPage.SALE_EXIT -> GoatExitCaptureScreen(GoatExitKind.SALE, state, state.currency, actions.onRecordExit) { page = GoatPage.STATUS_CHANGE }
        GoatPage.MORTALITY -> GoatExitCaptureScreen(GoatExitKind.DEATH, state, state.currency, actions.onRecordExit) { page = GoatPage.STATUS_CHANGE }
        GoatPage.CULL -> GoatExitCaptureScreen(GoatExitKind.CULL, state, state.currency, actions.onRecordExit) { page = GoatPage.STATUS_CHANGE }
        GoatPage.PREGNANCY_DASHBOARD -> GoatPregnancyDashboardScreen(
            herd = state.herd,
            herdTotal = state.herdCounts?.notClosed,
            onSelectDoe = {
                actions.onSelectGoat(it)
                page = GoatPage.DOE_REPRODUCTION
            },
            onBack = { page = GoatPage.DASHBOARD },
        )
        GoatPage.KIDDING_DUE -> GoatKiddingDueScreen(
            due = state.kiddingDue,
            todayEpochDay = today.toEpochDay(),
            onSelectDoe = {
                actions.onSelectGoat(it)
                page = GoatPage.DOE_REPRODUCTION
            },
            onBack = { page = GoatPage.DASHBOARD },
        )
        GoatPage.LACTATION_DASHBOARD -> GoatLactationDashboardScreen(
            lactation = state.lactation,
            onSelectDoe = {
                actions.onSelectGoat(it)
                page = GoatPage.LACTATION_HISTORY
            },
            onBack = { page = GoatPage.DASHBOARD },
        )
        GoatPage.KID_COHORT -> GoatKidCohortScreen(
            herd = state.herd,
            herdTotal = state.herdCounts?.notClosed,
            onSelectKid = {
                actions.onSelectGoat(it)
                page = GoatPage.KID_PROFILE
            },
            onBack = { page = GoatPage.DASHBOARD },
        )
        GoatPage.IDENTITY_EDIT -> {
            val goat = state.selected
            if (goat == null) page = GoatPage.HERD else GoatIdentityEditScreen(
                goat = goat,
                identifiers = identifiers,
                busy = state.busy,
                error = state.error,
                onSave = actions.onAmendIdentity,
                onBack = { page = GoatPage.PROFILE },
            )
        }
        GoatPage.WEANING -> {
            val goat = state.selected
            if (goat == null) page = GoatPage.HERD else GoatWeaningScreen(
                goat = goat,
                weanings = weanings,
                busy = state.busy,
                error = state.error,
                onRecordWeaning = actions.onRecordWeaning,
                onBack = { page = GoatPage.PROFILE },
            )
        }
        GoatPage.MOVEMENT -> {
            val goat = state.selected
            if (goat == null) page = GoatPage.HERD else GoatMovementScreen(
                goat = goat,
                movements = movements,
                busy = state.busy,
                error = state.error,
                onRecordMovement = actions.onRecordMovement,
                onBack = { page = GoatPage.PROFILE },
            )
        }
        GoatPage.IDENTIFIERS -> {
            val goat = state.selected
            if (goat == null) page = GoatPage.HERD else GoatIdentifiersScreen(
                goat = goat,
                identifiers = identifiers,
                busy = state.busy,
                error = state.error,
                onAssignIdentifier = actions.onAssignIdentifier,
                onBack = { page = GoatPage.PROFILE },
            )
        }
        GoatPage.FAMACHA_REF -> FamachaReferenceScreen { page = GoatPage.DASHBOARD }
        GoatPage.PARENTAGE_EDIT -> {
            val goat = state.selected
            if (goat == null) page = GoatPage.HERD else GoatParentageEditScreen(
                goat = goat,
                pedigreeParents = pedigreeParentLabels,
                searchDams = actions.searchDams,
                searchSires = actions.searchSires,
                busy = state.busy,
                error = state.error,
                onLinkParentage = actions.onLinkParentage,
                onBack = { page = GoatPage.PROFILE },
            )
        }
        GoatPage.GOAT_GROUPS -> GoatGroupsScreen(
            groups = goatGroups,
            onOpenGroup = {
                selectedGroupId = it
                page = GoatPage.GROUP_MEMBERSHIP
            },
            onBack = { page = GoatPage.DASHBOARD },
        )
        GoatPage.GROUP_MEMBERSHIP -> GoatGroupMembershipScreen(
            group = goatGroups.firstOrNull { it.groupId == selectedGroupId },
            onBack = { page = GoatPage.GOAT_GROUPS },
        )
        GoatPage.GOAT_REPORT -> GoatReportScreen(
            counts = state.herdCounts,
            lactation = state.lactation,
            kiddingDue = state.kiddingDue,
            onBack = { page = GoatPage.DASHBOARD },
        )
        GoatPage.SCALE_PAIRING -> GoatScalePairingScreen(
            adapter = actions.scaleAdapter,
            adapterEnabled = actions.isScaleAdapterEnabled,
            onToggleAdapter = actions.onToggleScaleAdapter,
            selectedGoatLabel = selected?.let { "${it.tag}${it.name?.let { n -> " · $n" } ?: ""}" },
            onRecordWeight = actions.onRecordWeight,
            onBack = { page = GoatPage.DASHBOARD },
        )
        in goatHistoryPages -> GoatHistoryPage(
            page = page,
            goat = state.selected,
            today = today,
            onOpen = { if (it == GoatPage.REPRODUCTION) reproductionSection = "Mating"; page = it },
            onBack = { page = if (page == GoatPage.KIDDING_DETAIL) GoatPage.DOE_REPRODUCTION else GoatPage.PROFILE },
            selectedKiddingId = selectedKiddingId,
            onOpenKidding = {
                selectedKiddingId = it
                page = GoatPage.KIDDING_DETAIL
            },
        )
        else -> error("Unhandled goat page $page")
    }
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
    onOpenMilk: () -> Unit,
) {
    FarmBackHandler(onBackToFarm)
    val selectedDoe = state.selected?.takeIf { it.status == GoatStatus.ACTIVE && it.sex == GoatSex.FEMALE }
    fun tool(label: String, page: GoatPage, detail: String? = null) =
        FarmToolAction(label, { onOpen(page) }, detail = detail, tag = "goat-action:${page.name}")
    AnimalFarmCanvas(modifier.testTag("farm-screen:FOS-GOAT-001")) {
        Column(
            Modifier.weight(1f).imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(FosDimens.ScreenMargin),
            verticalArrangement = Arrangement.spacedBy(FosDimens.SectionGap),
        ) {
            FarmBackButton("Farm home", onBackToFarm)
            AnimalFarmModuleHeader("Goats", state.farmName.orEmpty(), family = AnimalFarmFamily.GOAT)
            val counts = state.herdCounts
            FarmMetricStrip(listOf(
                FarmMetricValue("Total", counts?.active?.toString() ?: "—", onClick = { onOpen(GoatPage.HERD) }),
                FarmMetricValue("Does", counts?.does?.toString() ?: "—", onClick = { onOpen(GoatPage.HERD) }),
                FarmMetricValue("Bucks", counts?.bucks?.toString() ?: "—", onClick = { onOpen(GoatPage.HERD) }),
                FarmMetricValue("Kids", counts?.kids?.toString() ?: "—", onClick = { onOpen(GoatPage.HERD) }),
            ))
            state.selected?.let { goat ->
                FarmActionButton({ onOpen(GoatPage.PROFILE) }, Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FosDimens.Grid)) {
                        Text(goatDisplayName(goat), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(goat.tag + " · " + goatStatusLabel(goat.status))
                        goat.latestWeightGrams?.let { Text("${formatKg(it)} kg latest weight", style = MaterialTheme.typography.labelMedium) }
                        Text("Open profile", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            FarmModuleTools(
                groups = listOf(
                    FarmToolGroup("Herd", listOf(tool("Herd", GoatPage.HERD), tool("Register goat", GoatPage.REGISTER), tool("Goat groups", GoatPage.GOAT_GROUPS), tool("Kids", GoatPage.KID_COHORT))),
                    FarmToolGroup("Breeding", listOf(
                        tool("Breeding", if (selectedDoe != null) GoatPage.REPRODUCTION else GoatPage.HERD),
                        tool("Kidding", if (selectedDoe != null) GoatPage.KIDDING else GoatPage.HERD),
                        tool("Pregnancy", GoatPage.PREGNANCY_DASHBOARD), tool("Kidding due", GoatPage.KIDDING_DUE),
                    )),
                    FarmToolGroup("Production", listOf(
                        FarmToolAction("Milk", onOpenMilk, tag = "goat-action:MILK"),
                        tool("Lactation", GoatPage.LACTATION_DASHBOARD),
                        tool("Growth", if (state.selected != null) GoatPage.WEIGHT else GoatPage.HERD),
                    )),
                    FarmToolGroup("Health", listOf(tool("Health", if (state.selected != null) GoatPage.HEALTH else GoatPage.HERD), tool("FAMACHA chart", GoatPage.FAMACHA_REF))),
                    FarmToolGroup("Records & tools", listOf(tool("Goat report", GoatPage.GOAT_REPORT), tool("Search", GoatPage.SEARCH), tool("Scan tag", GoatPage.SCAN), tool("Scale pairing", GoatPage.SCALE_PAIRING))),
                    FarmToolGroup("Device & account", listOf(tool("Sync status", GoatPage.SYNC, state.syncMessage), FarmToolAction("Sign out", onSignOut))),
                ),
                title = "Goat tools",
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun GoatDashboardAction(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    com.farmos.core.design.AnimalFarmQuickAction(
        label = if (subtitle.contains("not available")) "$title · unavailable" else title,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
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
    var filter by rememberSaveable { mutableStateOf(GoatHerdFilter.ACTIVE) }
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
        state.herdCounts?.notClosed?.takeIf { it > state.herd.size }?.let { total ->
            Text("Showing the first ${state.herd.size} of $total goats by tag.", modifier = Modifier.testTag("goat-herd-bounded"))
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
private fun GoatHerdFilterButton(option: GoatHerdFilter, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FarmFilterChip(option.name.lowercase(), selected, onClick, modifier)
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
    attachments: @Composable (animalId: String, active: Boolean) -> Unit = { _, _ -> },
) {
    val goat = state.selected
    IllustratedGoatPage("Goat profile", "FOS-GOAT-003", onBack) {
        if (goat == null) {
            when (state.herdState) {
                LoadableSurfaceState.LOADING -> Text("Loading the selected goat")
                LoadableSurfaceState.ERROR -> Text(state.error ?: "The selected goat could not be read.", color = MaterialTheme.colorScheme.error)
                else -> Text("Select a goat from the herd before opening its profile.")
            }
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
                        GoatRecordLinks(goat, onOpen)
                        attachments(goat.animalId, goat.status == GoatStatus.ACTIVE)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GoatIdentityCard(goat)
                    GoatProfileActions(goat, onOpen)
                    GoatHistoryCard(goat)
                    GoatRecordLinks(goat, onOpen)
                    attachments(goat.animalId, goat.status == GoatStatus.ACTIVE)
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
    val enabled = goat.status == GoatStatus.ACTIVE
    fun action(label: String, page: GoatPage) = FarmToolAction(label, { onOpen(page) }, enabled = enabled, tag = "goat-profile-action:${page.name}")
    FarmModuleTools(
        title = "Record",
        groups = listOf(
            FarmToolGroup("Capture", buildList {
                add(action("Weight", GoatPage.WEIGHT))
                add(action("Health", GoatPage.HEALTH))
                if (goat.sex == GoatSex.FEMALE) add(action("Reproduction", GoatPage.REPRODUCTION))
                if (goat.sex == GoatSex.FEMALE) add(action("Kidding", GoatPage.KIDDING))
                add(action("Weaning", GoatPage.WEANING))
            }),
            FarmToolGroup("Identity", listOf(action("Edit identity", GoatPage.IDENTITY_EDIT), action("Identifiers", GoatPage.IDENTIFIERS), action("Parentage", GoatPage.PARENTAGE_EDIT))),
            FarmToolGroup("Movement & lifecycle", listOf(action("Movement", GoatPage.MOVEMENT), action("Lifecycle", GoatPage.STATUS_CHANGE))),
        ),
    )
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

/**
 * FOS-GOAT-051 — lifecycle: an active goat leaves the herd through a sale (FOS-GOAT-052), mortality
 * (FOS-GOAT-053) or cull (FOS-GOAT-054) record; a goat that has left shows its exit, which can be reversed.
 */
@Composable
private fun GoatStatusChangeScreen(
    state: GoatSliceUiState,
    onOpen: (GoatPage) -> Unit,
    onReverseExit: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    IllustratedGoatPage("Lifecycle change", "FOS-GOAT-051 · I4", onBack, safety = true) {
        val goat = state.selected
        if (goat == null) {
            Text("No goat selected.")
            return@IllustratedGoatPage
        }
        Text(goatDisplayName(goat), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        val exit = state.standingExit
        if (goat.status != GoatStatus.ACTIVE && exit != null) {
            GoatExitReversal(exit, state.busy, onReverseExit)
        } else if (goat.status != GoatStatus.ACTIVE) {
            Text("${goatStatusLabel(goat.status)} before exits were recorded; no exit to reverse.")
        } else {
            Text("How is this goat leaving the herd?")
            listOf("Sale" to GoatPage.SALE_EXIT, "Death" to GoatPage.MORTALITY, "Cull" to GoatPage.CULL).forEach { (label, target) ->
                TextButton(
                    onClick = { onOpen(target) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = AnimalFarmTheme.minimumTouchDp.dp).testTag("goat-exit-open:${target.name}"),
                ) { Text(label) }
            }
        }
        state.error?.let { Text(it, color = AnimalFarmTheme.colors.critical) }
    }
}

/** Shared P05/P06/P07/P09 goat shell; owning Screen IDs and commands remain unchanged. */
@Composable
internal fun IllustratedGoatPage(
    title: String,
    screenId: String,
    onBack: () -> Unit,
    safety: Boolean = false,
    content: @Composable () -> Unit,
) {
    FarmBackHandler(onBack)
    val canonicalScreenId = screenId.substringBefore(" ").takeIf { it.startsWith("FOS-") && "/" !in it }
    AnimalFarmCanvas(canonicalScreenId?.let { Modifier.testTag("farm-screen:$it") } ?: Modifier) {
        Column(
            Modifier.weight(1f).imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(FosDimens.ScreenMargin),
            verticalArrangement = Arrangement.spacedBy(FosDimens.SectionGap),
        ) {
            FarmBackButton("Back", onBack)
            AnimalFarmModuleHeader(title, "", family = if (safety) null else AnimalFarmFamily.GOAT)
            content()
        }
    }
}
