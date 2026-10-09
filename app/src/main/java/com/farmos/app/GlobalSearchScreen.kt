package com.farmos.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmCanvas
import com.farmos.core.design.AnimalFarmModuleHeader
import com.farmos.core.design.FarmBackHandler
import com.farmos.core.design.FarmIllustratedSectionSurface
import com.farmos.core.design.FarmSearchEmptyState
import kotlinx.coroutines.launch

internal data class GlobalSearchResultUi(
    val animalId: String,
    val speciesCode: String,
    val tag: String,
    val displayName: String?,
    val status: String,
    val source: String,
    val sex: String? = null,
)

@Composable
internal fun GlobalSearchHost(
    database: com.farmos.core.database.FarmOsDatabase,
    farmId: String,
    onOpen: (FarmDestination) -> Unit,
    onBack: () -> Unit,
    state: GlobalSearchSessionState = remember(farmId) { GlobalSearchSessionState(farmId) },
) {
    require(state.farmId == farmId) { "Search state belongs to another farm" }
    val scope = rememberCoroutineScope()

    GlobalSearchScreen(
        busy = state.busy,
        searched = state.searched,
        message = state.message,
        results = state.results,
        inputState = state.input,
        onSearch = { rawQuery ->
            val query = rawQuery.trim()
            if (query.isNotEmpty() && !state.busy) {
                state.busy = true
                state.searched = false
                state.results = emptyList()
                state.message = "Searching local records"
                scope.launch {
                    try {
                        val local = searchLocalFarmAnimals(database, farmId, query)
                        state.results = local
                        state.searched = true
                        state.message = "${local.size} local result(s). Up to $GLOBAL_SEARCH_LIMIT are shown; narrow the query if needed."
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        state.message = "Local search was interrupted. Search again."
                        throw cancelled
                    } catch (failure: Exception) {
                        // A failed database read is not a successful search with zero matches.
                        state.message = "Local search failed: ${failure.message ?: "records could not be read"}"
                    } finally {
                        state.busy = false
                    }
                }
            }
        },
        onOpenResult = { result -> result.destination()?.let(onOpen) },
        rfidValue = state.rfidValue,
        onRfidValueChange = { state.rfidValue = it },
        onBack = onBack,
    )
}

/** FOS-SEARCH-001, FOS-SEARCH-002, FOS-SEARCH-004, FOS-SEARCH-005 — local-first search home: entry, results list, no-results state and offline local search. The results section keeps its FOS-HOME-007 runtime tag.
 *
 * FOS-SEARCH-003 — species and status filters applied to the displayed matches.
 * FOS-SEARCH-006 — server search unavailable: honest by-design state, not an error.
 * FOS-SEARCH-007 — RFID result: honest unavailable state; no RFID reader adapter is bundled.
 * FOS-SEARCH-008 — QR or barcode result: honest unavailable state; no scanner adapter is bundled.
 * FOS-SEARCH-009 — recent queries retained for this farm session and detail/return journeys.
 */
@Composable
internal fun GlobalSearchScreen(
    busy: Boolean,
    searched: Boolean,
    message: String,
    results: List<GlobalSearchResultUi>,
    onSearch: (String) -> Unit,
    onOpenResult: (GlobalSearchResultUi) -> Unit,
    /** FOS-SEARCH-007 — the RFID/EID lookup field value, fed into the same identifier search. */
    rfidValue: String = "",
    onRfidValueChange: (String) -> Unit = {},
    onBack: () -> Unit,
    inputState: GlobalSearchInputState = remember { GlobalSearchInputState() },
) {
    FarmBackHandler(onBack)
    var query by inputState.query
    var speciesFilter by inputState.speciesFilter
    var statusFilter by inputState.statusFilter
    var showFilters by inputState.showFilters
    var recentSearches by inputState.recentSearches

    val filteredResults = results.filter { result ->
        (speciesFilter == null || result.speciesCode.equals(speciesFilter, ignoreCase = true)) &&
            (statusFilter == null || result.status.equals(statusFilter, ignoreCase = true))
    }
    val speciesOptions = results.map { it.speciesCode }.distinct().sorted()
    val statusOptions = results.map { it.status }.distinct().sorted()

    fun runSearch(rawQuery: String) {
        if (busy) return
        val trimmed = rawQuery.trim()
        if (trimmed.isNotEmpty() && !recentSearches.contains(trimmed)) {
            recentSearches = (listOf(trimmed) + recentSearches).take(10)
        }
        onSearch(rawQuery)
    }

    // The home-search and offline-search identities share this local database query owner.
    Box(Modifier.testTag("farm-screen:FOS-SEARCH-005")) {
        AnimalFarmCanvas(Modifier.testTag("farm-screen:FOS-HOME-006")) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnimalFarmModuleHeader(
                    title = "Search farm",
                    subtitle = "Animals on this farm · offline first",
                )
                FarmIllustratedSectionSurface(Modifier.testTag("farm-screen:FOS-SEARCH-001")) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Tag, name, species or identifier") },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Button(
                        onClick = { runSearch(query) },
                        enabled = !busy && query.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (busy) "Searching…" else "Search farm")
                    }
                    TextButton(
                        onClick = { showFilters = !showFilters },
                        modifier = Modifier.fillMaxWidth().testTag("farm-screen:FOS-SEARCH-003"),
                    ) {
                        Text(if (showFilters) "Hide filters" else "Filter results")
                    }
                    if (speciesFilter != null || statusFilter != null) {
                        TextButton(
                            onClick = {
                                speciesFilter = null
                                statusFilter = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Clear filters")
                        }
                    }
                    if (showFilters) {
                        if (speciesOptions.isNotEmpty()) {
                            Text("Species", style = MaterialTheme.typography.labelLarge)
                            speciesOptions.forEach { species ->
                                TextButton(
                                    onClick = { speciesFilter = if (speciesFilter == species) null else species },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        species.replaceFirstChar { it.uppercase() } +
                                            if (speciesFilter == species) " (active)" else "",
                                    )
                                }
                            }
                        }
                        if (statusOptions.isNotEmpty()) {
                            Text("Status", style = MaterialTheme.typography.labelLarge)
                            statusOptions.forEach { status ->
                                TextButton(
                                    onClick = { statusFilter = if (statusFilter == status) null else status },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(status + if (statusFilter == status) " (active)" else "")
                                }
                            }
                        }
                        if (speciesFilter == null && statusFilter == null) {
                            Text(
                                "No filters active. Run a search first; filters appear for the species and statuses found.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (searched) {
                    Box(Modifier.testTag("farm-screen:FOS-SEARCH-002")) {
                        FarmIllustratedSectionSurface(
                            Modifier.testTag("farm-screen:FOS-HOME-007"),
                        ) {
                            Text(
                                "Search results",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            val shown = if (speciesFilter != null || statusFilter != null) filteredResults else results
                            if (shown.isEmpty()) {
                                Column(Modifier.testTag("farm-screen:FOS-SEARCH-004")) {
                                    FarmSearchEmptyState(
                                        title = if (results.isNotEmpty()) "No animal records match the active filters." else "No matching animal records.",
                                        hint = if (results.isNotEmpty()) {
                                            "Clear filters to show the ${results.size} local result(s) from this search."
                                        } else {
                                            "Check the spelling, or search by tag, name, species, or an identifier value (RFID/EID/QR). Only this farm's records are searched."
                                        },
                                    )
                                }
                            } else {
                                shown.forEach { result ->
                                    val label = buildString {
                                        append(result.speciesCode.replaceFirstChar { it.uppercase() })
                                        append(" · ").append(result.tag)
                                        result.displayName?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                                        append(" · ").append(result.status)
                                        append(" · ").append(result.source)
                                    }
                                    if (result.destination() != null) {
                                        TextButton(
                                            onClick = { onOpenResult(result) },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(label)
                                        }
                                    } else {
                                        Text(label)
                                        when (result.speciesCode.lowercase()) {
                                            "poultry" -> Text("Individual bird profiles are not available here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            "rabbit" -> Text("Rabbit profile unavailable: recorded sex has no Doe or Buck profile.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (recentSearches.isNotEmpty()) {
                    FarmIllustratedSectionSurface(Modifier.testTag("farm-screen:FOS-SEARCH-009")) {
                        Text(
                            "Recent searches in this farm session",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        recentSearches.forEach { recent ->
                            TextButton(
                                onClick = {
                                    query = recent
                                    runSearch(recent)
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(recent)
                            }
                        }
                    }
                }

                FarmIllustratedSectionSurface(Modifier.testTag("farm-screen:FOS-SEARCH-006")) {
                    Text(
                        "Server search",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Server search is unavailable by design. Farm OS keeps every record on this device " +
                            "and on the farm's own network; there is no cloud search index to query. " +
                            "The search above reads this farm's local database and shows up to 50 matches. " +
                            "Narrow the query to find a specific animal; filters apply to the displayed matches.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                FarmIllustratedSectionSurface(Modifier.testTag("farm-screen:FOS-SEARCH-007")) {
                    Text(
                        "RFID lookup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "No RFID reader is connected in this build. Paste or type the RFID/EID tag value " +
                            "below and it searches this farm's local database — the same identifier search " +
                            "as the main field. Any scanner adapter, if ever fitted, would be advisory " +
                            "transport into this field: the local database is the search authority and " +
                            "there is no server search index. See Settings → Hardware → RFID readers.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = rfidValue,
                        onValueChange = onRfidValueChange,
                        label = { Text("RFID/EID tag value") },
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("farm-screen:FOS-SEARCH-007-input"),
                    )
                    Button(
                        onClick = {
                            query = rfidValue
                            runSearch(rfidValue)
                        },
                        enabled = !busy && rfidValue.isNotBlank(),
                    ) {
                        Text("Search by tag value")
                    }
                }

                FarmIllustratedSectionSurface(Modifier.testTag("farm-screen:FOS-SEARCH-008")) {
                    Text(
                        "QR or barcode lookup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "No camera/scanner adapter is bundled, so paste the QR or barcode payload into the " +
                            "search field above: it is decoded as plain text and searched against this farm's " +
                            "local database. Any future scanner would be advisory transport only — " +
                            "manual text search remains fully available.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                TextButton(onClick = onBack) { Text("Farm home") }
            }
        }
    }
}

internal fun GlobalSearchResultUi.destination(): FarmDestination? =
    when (speciesCode.lowercase()) {
        "goat" -> FarmDestination.Goat(com.farmos.feature.goat.GoatEntryPage.PROFILE, animalId)
        "rabbit" -> when (sex?.uppercase()) {
            "FEMALE" -> FarmDestination.AnimalProfile(AnimalProfileKind.RABBIT_DOE, animalId)
            "MALE" -> FarmDestination.AnimalProfile(AnimalProfileKind.RABBIT_BUCK, animalId)
            else -> null
        }
        "sheep" -> FarmDestination.AnimalProfile(AnimalProfileKind.SHEEP, animalId)
        "cattle" -> FarmDestination.AnimalProfile(AnimalProfileKind.CATTLE, animalId)
        "poultry" -> null
        else -> null
    }
