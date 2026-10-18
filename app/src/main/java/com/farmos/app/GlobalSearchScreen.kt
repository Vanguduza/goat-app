package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.ensureActive

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
    val request = state.requestSnapshot()
    val statusRevision = state.statusRefreshRevision.value
    androidx.compose.runtime.LaunchedEffect(state, database, statusRevision) {
        try {
            val options = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                database.animals().globalSearchStatuses(farmId)
            }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (state.statusRefreshRevision.value == statusRevision) {
                state.statusOptions.value = options.distinct()
                state.statusOptionsUnavailable.value = false
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (state.statusRefreshRevision.value == statusRevision) state.statusOptionsUnavailable.value = true
        }
    }
    androidx.compose.runtime.LaunchedEffect(state, database, request) {
        val generation = ++state.requestGeneration
        fun isCurrent() = state.requestGeneration == generation && state.requestSnapshot() == request
        state.results.value = emptyList()
        state.hasMore.value = false
        state.searched.value = false
        state.message.value = ""
        state.busy.value = request.query.isNotBlank()
        state.renderedRequest.value = request
        if (request.query.isBlank()) return@LaunchedEffect
        try {
            // Restart on every effective input change; cancellation begins before the typing delay.
            kotlinx.coroutines.delay(220L)
            val page = searchLocalFarmAnimalPage(database, farmId, request.query, request.species, request.status)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (isCurrent()) {
                state.results.value = page.rows
                state.hasMore.value = page.hasMore
                state.searched.value = true
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (isCurrent()) {
                state.results.value = emptyList()
                state.hasMore.value = false
                state.searched.value = false
                state.message.value = "Search is unavailable. Try again."
            }
        } finally {
            if (isCurrent()) state.busy.value = false
        }
    }
    // Old rows disappear in the same composition as the new input, before its effect starts.
    val current = state.renderedRequest.value == request
    GlobalSearchScreen(
        busy = if (current) state.busy.value else request.query.isNotBlank(),
        searched = current && state.searched.value,
        message = if (current) state.message.value else "",
        results = if (current) state.results.value else emptyList(),
        hasMore = current && state.hasMore.value,
        statusOptions = state.statusOptions.value,
        statusOptionsUnavailable = state.statusOptionsUnavailable.value,
        onRetry = {
            state.requestRevision.value += 1L
            state.statusRefreshRevision.value += 1L
        },
        inputState = state.input,
        onSearch = { raw ->
            state.input.query.value = raw
            state.input.rememberSearch(raw)
            state.requestRevision.value += 1L
        },
        onOpenResult = { result ->
            result.destination()?.let { destination ->
                state.input.rememberSearch(state.input.query.value)
                onOpen(destination)
            }
        },
        rfidValue = state.rfidValue.value,
        onRfidValueChange = { state.rfidValue.value = it },
        onBack = onBack,
    )
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
