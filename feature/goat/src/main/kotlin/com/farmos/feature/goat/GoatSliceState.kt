package com.farmos.feature.goat

import com.farmos.domain.goat.GoatSearchResult
import com.farmos.domain.goat.GoatSnapshot

data class GoatSliceUiState(
    val farmName: String? = null,
    val herd: List<GoatSnapshot> = emptyList(),
    /** Exhaustive herd counts; null until loaded, when the bounded [herd] list is the only source. */
    val herdCounts: com.farmos.domain.goat.GoatHerdCounts? = null,
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
    /** Exhaustive per-goat milk aggregates for the lactation dashboard. */
    val lactation: GoatLactationState = GoatLactationState.Loading,
    /** Does expected to kid, for Kidding due (FOS-GOAT-036). */
    val kiddingDue: GoatKiddingDueState = GoatKiddingDueState.Loading,
)

/** Every doe expected to kid, from an exhaustive query. */
sealed interface GoatKiddingDueState {
    data object Loading : GoatKiddingDueState
    data class Failed(val message: String) : GoatKiddingDueState
    data class Loaded(val rows: List<com.farmos.domain.goat.GoatKiddingDue>, val typicalDays: Int) : GoatKiddingDueState
}

sealed interface GoatLactationState {
    data object Loading : GoatLactationState
    data class Failed(val message: String) : GoatLactationState
    data class Loaded(val rows: List<com.farmos.domain.goat.GoatLactationSummary>) : GoatLactationState
}

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
