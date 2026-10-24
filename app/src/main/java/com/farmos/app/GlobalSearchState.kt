package com.farmos.app

import androidx.compose.runtime.mutableStateOf
import androidx.room.withTransaction
import com.farmos.core.database.FarmOsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val GLOBAL_SEARCH_LIMIT = 50

internal class GlobalSearchInputState {
    val query = mutableStateOf("")
    val speciesFilter = mutableStateOf<String?>(null)
    val statusFilter = mutableStateOf<String?>(null)
    val showFilters = mutableStateOf(false)
    val showIdentifiers = mutableStateOf(false)
    val recentSearches = mutableStateOf<List<String>>(emptyList())
}

/** State and query identity belong to one farm, including a detail/return round trip. */
internal class GlobalSearchSessionState(val farmId: String) {
    val input = GlobalSearchInputState()
    val busy = mutableStateOf(false)
    val searched = mutableStateOf(false)
    val message = mutableStateOf("")
    val results = mutableStateOf<List<GlobalSearchResultUi>>(emptyList())
    val hasMore = mutableStateOf(false)
    val rfidValue = mutableStateOf("")
    val statusOptions = mutableStateOf<List<String>>(emptyList())
    val statusOptionsUnavailable = mutableStateOf(false)
    val requestRevision = mutableStateOf(0L)
    val statusRefreshRevision = mutableStateOf(0L)
    val renderedRequest = mutableStateOf<GlobalSearchRequest?>(null)
    var requestGeneration = 0L
}

internal data class GlobalSearchPage(val rows: List<GlobalSearchResultUi>, val hasMore: Boolean)

internal data class GlobalSearchRequest(
    val farmId: String,
    val query: String,
    val species: String?,
    val status: String?,
    val revision: Long,
)

internal fun GlobalSearchSessionState.requestSnapshot(): GlobalSearchRequest = GlobalSearchRequest(
    farmId = farmId,
    query = input.query.value.trim(),
    species = input.speciesFilter.value?.takeUnless { it.isBlank() },
    status = input.statusFilter.value?.takeUnless { it.isBlank() },
    revision = requestRevision.value,
)

internal fun GlobalSearchInputState.rememberSearch(query: String) {
    val normalized = query.trim()
    if (normalized.isNotEmpty()) {
        recentSearches.value = (listOf(normalized) + recentSearches.value.filterNot {
            it.equals(normalized, ignoreCase = true)
        }).take(6)
    }
}

/** Compatibility API; every filter is applied to canonical animals before the DAO limit. */
internal suspend fun searchLocalFarmAnimals(
    database: FarmOsDatabase,
    farmId: String,
    query: String,
    species: String? = null,
    status: String? = null,
): List<GlobalSearchResultUi> = searchLocalFarmAnimalPage(database, farmId, query, species, status).rows

internal suspend fun searchLocalFarmAnimalPage(
    database: FarmOsDatabase,
    farmId: String,
    query: String,
    species: String? = null,
    status: String? = null,
): GlobalSearchPage {
    val normalized = query.trim()
    if (normalized.isEmpty()) return GlobalSearchPage(emptyList(), false)
    return withContext(Dispatchers.IO) {
        database.withTransaction {
            val candidates = database.animals().searchGlobal(
                farmId, normalized, species?.takeUnless { it.isBlank() }, status?.takeUnless { it.isBlank() },
                GLOBAL_SEARCH_LIMIT + 1,
            )
            val rows = candidates.take(GLOBAL_SEARCH_LIMIT).map { animal ->
                val identifiers = database.lifecycle().identifiersForAnimal(farmId, animal.id)
                    .filter { it.isActive && it.value.contains(normalized, ignoreCase = true) }
                    .sortedWith(compareByDescending<com.farmos.core.database.AnimalIdentifierEntity> {
                        it.value.equals(normalized, ignoreCase = true)
                    }.thenBy { it.type }.thenBy { it.id })
                val exactIdentifier = identifiers.firstOrNull { it.value.equals(normalized, ignoreCase = true) }
                val matchesText = animal.tag.contains(normalized, true) ||
                    animal.name?.contains(normalized, true) == true || animal.speciesCode.contains(normalized, true)
                val source = when {
                    animal.tag.equals(normalized, true) -> "local"
                    exactIdentifier != null -> "identifier:${exactIdentifier.type}"
                    matchesText -> "local"
                    else -> identifiers.firstOrNull()?.let { "identifier:${it.type}" } ?: "local"
                }
                GlobalSearchResultUi(animal.id, animal.speciesCode, animal.tag, animal.name, animal.status, source, animal.sex)
            }
            GlobalSearchPage(rows, candidates.size > GLOBAL_SEARCH_LIMIT)
        }
    }
}
