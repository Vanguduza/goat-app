package com.farmos.app

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableStateOf
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
import com.farmos.core.design.runSuspendCatching
import com.farmos.core.network.AuthenticationRequiredException
import kotlinx.coroutines.launch

internal data class GlobalSearchResultUi(
    val animalId: String,
    val speciesCode: String,
    val tag: String,
    val displayName: String?,
    val status: String,
    val source: String,
)

@Composable
internal fun GlobalSearchHost(
    app: FarmOsApplication,
    farmId: String,
    onOpen: (FarmDestination) -> Unit,
    onBack: () -> Unit,
    onRequireReauth: (String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Search animal tags, names, species or identifier values. Local records work offline.") }
    var results by remember { mutableStateOf<List<GlobalSearchResultUi>>(emptyList()) }
    var rfidValue by remember { mutableStateOf("") }

    GlobalSearchScreen(
        busy = busy,
        searched = searched,
        message = message,
        results = results,
        onSearch = { rawQuery ->
            val query = rawQuery.trim()
            if (query.isNotEmpty()) {
                scope.launch {
                    busy = true
                    searched = true
                val local = runSuspendCatching {
                    // FOS-SEARCH-007 / FOS-SEARCH-008 — identifier-driven local search: the
                    // exhaustive local database is the search authority; no server is consulted.
                    // Animals by tag/name/species, plus animals matched by active identifier
                    // (tag, RFID, EID, QR payload pasted as text).
                    val byTag = app.database.animals().searchAll(farmId, query, 50)
                    val tagIds = byTag.map { it.id }.toSet()
                    val byIdentifier = app.database.lifecycle()
                        .searchActiveIdentifiers(farmId, query, 25)
                        .mapNotNull { identifier ->
                            app.database.animals().get(farmId, identifier.animalId)?.let { animal ->
                                GlobalSearchResultUi(
                                    animalId = animal.id,
                                    speciesCode = animal.speciesCode,
                                    tag = animal.tag,
                                    displayName = animal.name,
                                    status = animal.status,
                                    source = "identifier:${identifier.type}",
                                )
                            }
                        }
                        .filter { it.animalId !in tagIds }
                    (byTag.map {
                        GlobalSearchResultUi(
                            animalId = it.id,
                            speciesCode = it.speciesCode,
                            tag = it.tag,
                            displayName = it.name,
                            status = it.status,
                            source = "local",
                        )
                    } + byIdentifier).take(50)
                }.getOrElse {
                    message = "Local search failed: ${it.message ?: "unknown error"}"
                    emptyList()
                }
                results = local
                message = "${local.size} local result(s)"

                    busy = false
                }
            }
        },
        onOpenResult = { result ->
            speciesDestination(result.speciesCode)?.let(onOpen)
        },
        rfidValue = rfidValue,
        onRfidValueChange = { rfidValue = it },
        onBack = onBack,
    )
}

/** FOS-SEARCH-001, FOS-SEARCH-002, FOS-SEARCH-004, FOS-SEARCH-005 — local-first search home: entry, results list, no-results state and offline local search. The results section keeps its FOS-HOME-007 runtime tag.
 *
 * FOS-SEARCH-003 — filter sheet: species and status filters applied to the local search.
 * FOS-SEARCH-006 — server search unavailable: honest by-design state, not an error.
 * FOS-SEARCH-007 — RFID result: honest unavailable state; no RFID reader adapter is bundled.
 * FOS-SEARCH-008 — QR or barcode result: honest unavailable state; no scanner adapter is bundled.
 * FOS-SEARCH-009 — recent searches: the farm's recent search queries on this device.
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
) {
    FarmBackHandler(onBack)
    var query by remember { mutableStateOf("") }
    var speciesFilter by remember { mutableStateOf<String?>(null) }
    var statusFilter by remember { mutableStateOf<String?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    var recentSearches by remember { mutableStateOf(emptyList<String>()) }

    val filteredResults = results.filter { result ->
        (speciesFilter == null || result.speciesCode.equals(speciesFilter, ignoreCase = true)) &&
            (statusFilter == null || result.status.equals(statusFilter, ignoreCase = true))
    }
    val speciesOptions = results.map { it.speciesCode }.distinct().sorted()
    val statusOptions = results.map { it.status }.distinct().sorted()

    fun runSearch(rawQuery: String) {
        val trimmed = rawQuery.trim()
        if (trimmed.isNotEmpty() && !recentSearches.contains(trimmed)) {
            recentSearches = (listOf(trimmed) + recentSearches).take(10)
        }
        onSearch(rawQuery)
    }

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
                                title = "No matching animal records.",
                                hint = "Check the spelling, or search by tag, name, species, or an identifier value (RFID/EID/QR). Only this farm's records are searched.",
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
                            if (speciesDestination(result.speciesCode) != null) {
                                TextButton(
                                    onClick = { onOpenResult(result) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(label)
                                }
                            } else {
                                Text(label)
                            }
                        }
                    }
                }
            }

            if (recentSearches.isNotEmpty()) {
                FarmIllustratedSectionSurface(Modifier.testTag("farm-screen:FOS-SEARCH-009")) {
                    Text(
                        "Recent searches",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    recentSearches.forEach { recent ->
                        TextButton(
                            onClick = {
                                query = recent
                                runSearch(recent)
                            },
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
                        "The search above covers this farm's complete local database.",
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("farm-screen:FOS-SEARCH-007-input"),
                )
                Button(
                    onClick = {
                        query = rfidValue
                        runSearch(rfidValue)
                    },
                    enabled = rfidValue.isNotBlank(),
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

private fun speciesDestination(speciesCode: String): FarmDestination? =
    when (speciesCode.lowercase()) {
        "goat" -> FarmDestination.Goat()
        "rabbit" -> FarmDestination.Module(FarmModule.RABBIT)
        "sheep" -> FarmDestination.Module(FarmModule.SHEEP)
        "cattle" -> FarmDestination.Module(FarmModule.CATTLE)
        "poultry" -> FarmDestination.Module(FarmModule.POULTRY)
        else -> null
    }
