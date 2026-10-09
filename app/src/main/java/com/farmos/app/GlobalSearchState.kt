package com.farmos.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.room.withTransaction
import com.farmos.core.database.AnimalEntity
import com.farmos.core.database.FarmOsDatabase

/** Input belongs to one farm search session, so a detail/return round trip does not discard it. */
internal class GlobalSearchInputState {
    val query = mutableStateOf("")
    val speciesFilter = mutableStateOf<String?>(null)
    val statusFilter = mutableStateOf<String?>(null)
    val showFilters = mutableStateOf(false)
    val recentSearches = mutableStateOf(emptyList<String>())
}

internal class GlobalSearchSessionState(val farmId: String) {
    val input = GlobalSearchInputState()
    var busy by mutableStateOf(false)
    var searched by mutableStateOf(false)
    var message by mutableStateOf("Search animal tags, names, species or identifier values. Local records work offline.")
    var results by mutableStateOf<List<GlobalSearchResultUi>>(emptyList())
    var rfidValue by mutableStateOf("")
}

internal const val GLOBAL_SEARCH_LIMIT = 50

/**
 * D-027: the farm-local Room database is the only authority. Merge duplicate identifier
 * matches by animal ID and put exact tags/identifiers before partial matches. The rendered
 * result list is bounded; it is not an exhaustive count or a filter over every farm animal.
 */
internal suspend fun searchLocalFarmAnimals(
    database: FarmOsDatabase,
    farmId: String,
    query: String,
): List<GlobalSearchResultUi> = database.withTransaction {
    data class Match(val row: GlobalSearchResultUi, val exact: Boolean)
    val matches = linkedMapOf<String, Match>()
    fun offer(animal: AnimalEntity, source: String, exact: Boolean) {
        val old = matches[animal.id]
        if (old == null || (exact && !old.exact)) {
            matches[animal.id] = Match(animal.toGlobalSearchResult(source), exact)
        }
    }
    database.animals().searchAll(farmId, query, GLOBAL_SEARCH_LIMIT + 1).forEach {
        offer(it, "local", it.tag.equals(query, ignoreCase = true))
    }
    database.lifecycle().searchActiveIdentifiers(farmId, query, GLOBAL_SEARCH_LIMIT + 1).forEach { identifier ->
        database.animals().get(farmId, identifier.animalId)?.let { animal ->
            offer(animal, "identifier:${identifier.type}", identifier.value.equals(query, ignoreCase = true))
        }
    }
    matches.values.sortedWith(
        compareByDescending<Match> { it.exact }
            .thenBy { it.row.speciesCode }
            .thenBy { it.row.tag.lowercase() }
            .thenBy { it.row.animalId },
    ).take(GLOBAL_SEARCH_LIMIT).map { it.row }
}

private fun AnimalEntity.toGlobalSearchResult(source: String) = GlobalSearchResultUi(
    animalId = id,
    speciesCode = speciesCode,
    tag = tag,
    displayName = name,
    status = status,
    source = source,
)
