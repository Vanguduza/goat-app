package com.farmos.core.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One page of selector results. [hasMore] is true only when the database holds further matches. */
data class FarmSearchPage(val options: List<FarmSelectorOption>, val hasMore: Boolean)

/**
 * A query over the complete local farm database: farm-scoped, deterministically ordered and paged.
 * Implementations must never read a capped presentation list, so no matching record is ever omitted.
 */
fun interface FarmSelectorSearch {
    suspend fun page(query: String, offset: Int, limit: Int): FarmSearchPage
}

/** A search with no source; used only where no database is wired, and shows the empty state. */
val NoFarmSelectorSearch = FarmSelectorSearch { _, _, _ -> FarmSearchPage(emptyList(), hasMore = false) }

/**
 * Search-as-you-type selector over the complete local database. Typing narrows the results; "Show more"
 * pages through further matches in the same deterministic order. [pinned] options (for example an
 * explicit "none") always appear first. The selected option's exact id is returned through [onSelect].
 */
@Composable
fun FarmSearchSelector(
    atomTag: String,
    title: String,
    search: FarmSelectorSearch,
    selected: FarmSelectorOption?,
    onSelect: (FarmSelectorOption) -> Unit,
    emptyText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pinned: List<FarmSelectorOption> = emptyList(),
    pageSize: Int = 25,
    searchLabel: String = "Search by tag or name",
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<FarmSelectorOption>()) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(search, query) {
        loading = true
        if (query.isNotEmpty()) delay(SEARCH_DEBOUNCE_MILLIS)
        runCatching { search.page(query.trim(), 0, pageSize) }
            .onSuccess {
                results = it.options
                hasMore = it.hasMore
                failure = null
            }
            .onFailure { failure = it.message ?: "Search failed on this device" }
        loading = false
    }

    Column(modifier.fillMaxWidth().testTag(atomTag), verticalArrangement = Arrangement.spacedBy(FosDimens.Grid)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(searchLabel) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag("$atomTag:query"),
        )
        selected?.let {
            Text("Selected: ${it.label}", fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("$atomTag:selected"))
        }
        (pinned + results.filterNot { result -> pinned.any { it.id == result.id } }).forEach { option ->
            FarmSelectorOptionRow(atomTag, option, option.id == selected?.id, enabled) { onSelect(option) }
        }
        when {
            failure != null -> Text(failure.orEmpty(), color = MaterialTheme.colorScheme.error)
            loading && results.isEmpty() -> Text("Searching this device", color = AnimalFarmTheme.colors.mutedInk)
            !loading && results.isEmpty() -> Text(emptyText, color = AnimalFarmTheme.colors.mutedInk)
        }
        if (hasMore) {
            TextButton(
                onClick = {
                    scope.launch {
                        loading = true
                        runCatching { search.page(query.trim(), results.size, pageSize) }
                            .onSuccess {
                                results = results + it.options
                                hasMore = it.hasMore
                            }
                            .onFailure { failure = it.message ?: "Search failed on this device" }
                        loading = false
                    }
                },
                enabled = enabled && !loading,
                modifier = Modifier.heightIn(min = AnimalFarmTheme.minimumTouchDp.dp).testTag("$atomTag:more"),
            ) { Text("Show more") }
        }
    }
}

private const val SEARCH_DEBOUNCE_MILLIS = 250L
