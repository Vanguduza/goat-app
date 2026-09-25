package com.farmos.feature.goat

import com.farmos.domain.goat.GoatSearchResult
import com.farmos.domain.goat.GoatSnapshot

data class GoatSliceUiState(
    val farmName: String? = null,
    val herd: List<GoatSnapshot> = emptyList(),
    val herdState: LoadableSurfaceState = LoadableSurfaceState.IDLE,
    val selected: GoatSnapshot? = null,
    val animalId: String? = null,
    val pendingSyncCount: Long = 0,
    val goatSummary: String? = null,
    val syncMessage: String = "No local changes yet",
    val searchMessage: String = "Local search is always available",
    val searchResults: List<GoatSearchResult> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
)

enum class LoadableSurfaceState { IDLE, LOADING, EMPTY, ERROR, DISABLED }

/** Deep-entry contract for canonical goat operating pages. */
enum class GoatEntryPage {
    DASHBOARD,
    WEIGHT,
    SEARCH,
    SCAN,
    SYNC,
    KIDDING,
    REPRODUCTION,
}
