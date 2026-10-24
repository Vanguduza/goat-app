package com.farmos.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.farmos.core.design.FarmOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmCanvas
import com.farmos.core.design.AnimalFarmHomeMetrics
import com.farmos.core.design.AnimalFarmModuleHeader
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.FarmActionButton
import com.farmos.core.design.FarmBackButton
import com.farmos.core.design.FarmBackHandler
import com.farmos.core.design.FarmFilterChip
import com.farmos.core.design.FarmIllustratedSectionSurface
import com.farmos.core.design.FarmSearchEmptyState
import com.farmos.core.design.FosDimens

/** FOS-SEARCH-001..009 / FOS-HOME-006..007. Optional controls share one farm-local query owner. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GlobalSearchScreen(
    busy: Boolean,
    searched: Boolean,
    message: String,
    results: List<GlobalSearchResultUi>,
    onSearch: (String) -> Unit,
    onOpenResult: (GlobalSearchResultUi) -> Unit,
    rfidValue: String = "",
    onRfidValueChange: (String) -> Unit = {},
    onBack: () -> Unit,
    inputState: GlobalSearchInputState = remember { GlobalSearchInputState() },
    hasMore: Boolean = false,
    statusOptions: List<String> = emptyList(),
    statusOptionsUnavailable: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    FarmBackHandler(onBack)
    var query by inputState.query
    var speciesFilter by inputState.speciesFilter
    var statusFilter by inputState.statusFilter
    var showFilters by inputState.showFilters
    var recentSearches by inputState.recentSearches
    var showIdentifiers by inputState.showIdentifiers
    val colors = AnimalFarmTheme.colors
    val filtered = speciesFilter != null || statusFilter != null

    fun rememberQuery(value: String) {
        val trimmed = value.trim()
        if (trimmed.isNotBlank()) recentSearches = (listOf(trimmed) + recentSearches.filterNot { it == trimmed }).take(10)
    }
    fun runSearch(value: String) {
        query = value
        rememberQuery(value)
        onSearch(value)
    }

    Box(Modifier.testTag("farm-screen:FOS-SEARCH-005")) {
        AnimalFarmCanvas(Modifier.testTag("farm-screen:FOS-HOME-006")) {
            Column(
                Modifier.weight(1f).imePadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                    .padding(AnimalFarmHomeMetrics.pageInset),
                verticalArrangement = Arrangement.spacedBy(FosDimens.IntraCardGap),
            ) {
                FarmBackButton("Farm home", onBack)
                AnimalFarmModuleHeader(title = "Search farm", subtitle = "")
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Tag, name or ID") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }, modifier = Modifier.heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)) {
                                Icon(Icons.Rounded.Close, "Clear search")
                            }
                        }
                    } else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runSearch(query) }),
                    shape = RoundedCornerShape(AnimalFarmHomeMetrics.actionRadius),
                    modifier = Modifier.fillMaxWidth().testTag("farm-screen:FOS-SEARCH-001"),
                    singleLine = true,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(FosDimens.Grid)) {
                    FarmActionButton(
                        onClick = { showFilters = !showFilters },
                        modifier = Modifier.testTag("farm-screen:FOS-SEARCH-003"),
                    ) { Text(if (showFilters) "Hide filters" else "Filter results") }
                    FarmActionButton(onClick = { showIdentifiers = !showIdentifiers }) {
                        Text(if (showIdentifiers) "Hide identifier lookup" else "Identifier lookup")
                    }
                    if (filtered) FarmActionButton(onClick = {
                        speciesFilter = null
                        statusFilter = null
                    }) { Text("Clear filters") }
                }
                if (showFilters) {
                    FarmIllustratedSectionSurface {
                        if (statusOptionsUnavailable) {
                            Text("Status filters could not be loaded.", color = colors.mutedInk)
                            FarmActionButton(onClick = { onRetry?.invoke() ?: runSearch(query) }) { Text("Retry filters") }
                        }
                        Text("Species", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(FosDimens.Grid)) {
                            listOf("goat", "cattle", "sheep", "poultry", "rabbit").forEach { species ->
                                FarmFilterChip(
                                    label = species.replaceFirstChar { it.uppercase() },
                                    selected = speciesFilter == species,
                                    onClick = { speciesFilter = if (speciesFilter == species) null else species },
                                    modifier = Modifier.testTag("search-species:$species"),
                                )
                            }
                        }
                        val statuses = (statusOptions + listOfNotNull(statusFilter)).distinct().sorted()
                        if (statuses.isNotEmpty()) {
                            Text("Status", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(FosDimens.Grid)) {
                                statuses.forEach { status ->
                                    FarmFilterChip(
                                        label = status.replaceFirstChar { it.uppercase() },
                                        selected = statusFilter == status,
                                        onClick = { statusFilter = if (statusFilter == status) null else status },
                                        modifier = Modifier.testTag("search-status:$status"),
                                    )
                                }
                            }
                        }
                    }
                }
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().testTag("search-progress"), color = colors.primary)
                    Text("Searching…", style = MaterialTheme.typography.labelMedium, color = colors.mutedInk)
                } else if (!searched && message.isNotBlank() && query.isNotBlank()) {
                    Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("search-error"))
                    FarmActionButton(onClick = { onRetry?.invoke() ?: runSearch(query) }) { Text("Retry") }
                }
                if (searched) {
                    Column(
                        Modifier.fillMaxWidth().testTag("farm-screen:FOS-SEARCH-002"),
                        verticalArrangement = Arrangement.spacedBy(FosDimens.IntraCardGap),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().testTag("farm-screen:FOS-HOME-007"),
                            verticalArrangement = Arrangement.spacedBy(FosDimens.Grid),
                        ) {
                            Text(
                                if (results.isEmpty()) "Search results" else "${results.size}${if (hasMore) "+" else ""} matches",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (hasMore) Text("Refine your search to see other matches.", color = colors.mutedInk)
                            if (results.isEmpty()) {
                                Box(Modifier.testTag("farm-screen:FOS-SEARCH-004")) {
                                    FarmSearchEmptyState(
                                        title = "No matching animal records.",
                                        hint = if (filtered) "Try another search or clear the filters." else "Try a tag, name or identifier.",
                                    )
                                }
                            } else {
                                results.forEach { result ->
                                    GlobalSearchResultRow(result) {
                                        rememberQuery(query)
                                        onOpenResult(result)
                                    }
                                }
                            }
                        }
                    }
                }
                if (query.isBlank() && recentSearches.isNotEmpty()) {
                    Column(Modifier.testTag("farm-screen:FOS-SEARCH-009")) {
                        Text("Recent searches", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(FosDimens.Grid)) {
                            recentSearches.take(5).forEach { recent ->
                                FarmActionButton(onClick = { runSearch(recent) }) { Text(recent) }
                            }
                        }
                    }
                }
                if (showIdentifiers) {
                    FarmIllustratedSectionSurface(Modifier.testTag("farm-screen:FOS-SEARCH-007")) {
                        Text("Identifier lookup", style = MaterialTheme.typography.titleSmall)
                        Box(Modifier.testTag("farm-screen:FOS-SEARCH-008")) {
                            Text("Paste or type RFID, EID, QR or barcode text.", color = colors.mutedInk)
                        }
                        OutlinedTextField(
                            value = rfidValue,
                            onValueChange = onRfidValueChange,
                            label = { Text("RFID/EID tag value") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { runSearch(rfidValue) }),
                            modifier = Modifier.fillMaxWidth().testTag("farm-screen:FOS-SEARCH-007-input"),
                        )
                        FarmActionButton(onClick = { runSearch(rfidValue) }, enabled = rfidValue.isNotBlank()) {
                            Text("Search by tag value")
                        }
                        Text(
                            "Search uses this farm’s saved records. A scanner is not connected.",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.mutedInk,
                            modifier = Modifier.testTag("farm-screen:FOS-SEARCH-006"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalSearchResultRow(result: GlobalSearchResultUi, onOpen: () -> Unit) {
    val colors = AnimalFarmTheme.colors
    val opens = result.destination() != null
    Surface(
        shape = RoundedCornerShape(AnimalFarmHomeMetrics.actionRadius),
        color = colors.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                .testTag("search-result:${result.animalId}")
                .then(if (opens) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier)
                .padding(FosDimens.IntraCardGap),
            horizontalArrangement = Arrangement.spacedBy(FosDimens.Grid),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FosDimens.Grid)) {
                Text(
                    listOfNotNull(result.tag, result.displayName?.takeIf { it.isNotBlank() }).joinToString(" · "),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.ink,
                )
                Text(
                    "${result.speciesCode.replaceFirstChar { it.uppercase() }} · ${result.status}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.mutedInk,
                )
                if (!opens) Text(
                    if (result.speciesCode.equals("poultry", true)) "Individual bird profiles are not available here."
                    else "Rabbit profile unavailable: recorded sex has no Doe or Buck profile.",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.mutedInk,
                )
            }
            if (opens) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, modifier = Modifier.size(20.dp), tint = colors.mutedInk)
        }
    }
}
