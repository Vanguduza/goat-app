package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.model.SearchSource
import com.farmos.core.network.AuthenticationRequiredException
import com.farmos.core.network.AuthorizationLoss
import com.farmos.core.network.FarmMembership
import com.farmos.domain.goat.GoatSearchResult
import com.farmos.domain.goat.GoatSnapshot
import com.farmos.domain.goat.PlanGoatLactation
import com.farmos.domain.goat.RecordGoatBcs
import com.farmos.domain.goat.RecordGoatFamacha
import com.farmos.domain.goat.RecordGoatHeat
import com.farmos.domain.goat.RecordGoatKidding
import com.farmos.domain.goat.RecordGoatMating
import com.farmos.domain.goat.RecordGoatMilk
import com.farmos.domain.goat.RecordGoatPregnancy
import com.farmos.domain.goat.RecordGoatScc
import com.farmos.domain.goat.RecordGoatWeight
import com.farmos.domain.goat.RegisterGoat
import com.farmos.domain.goat.RegisterGoatKid
import com.farmos.domain.goat.SetGoatStatus
import com.farmos.feature.goat.GoatEntryPage
import com.farmos.feature.goat.GoatSliceUiState
import com.farmos.feature.goat.GoatVerticalSliceScreen
import com.farmos.feature.goat.LoadableSurfaceState
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun GoatModuleHost(
    app: FarmOsApplication,
    membership: FarmMembership,
    farmName: String?,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onRequireReauth: (String?) -> Unit,
    onRequireFarmReselection: (String, List<FarmMembership>) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    entryPage: GoatEntryPage = GoatEntryPage.DASHBOARD,
) {
    val scope = rememberCoroutineScope()
    val repository = remember(membership.farmId) { app.goatRepository(membership.farmId) }
    var herd by remember { mutableStateOf<List<GoatSnapshot>>(emptyList()) }
    var selectedGoatId by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<GoatSnapshot?>(null) }
    var searchResults by remember { mutableStateOf<List<GoatSearchResult>>(emptyList()) }
    var syncMessage by remember { mutableStateOf("No local changes yet") }
    var searchMessage by remember { mutableStateOf("Local search is always available") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var herdState by remember { mutableStateOf(LoadableSurfaceState.LOADING) }
    var pendingSyncCount by remember { mutableStateOf(0L) }

    suspend fun refreshGoatState() {
        herdState = LoadableSurfaceState.LOADING
        runCatching {
            val loaded = repository.listGoats(500)
            val autoSelect = entryPage != GoatEntryPage.WEIGHT && entryPage != GoatEntryPage.SEARCH
            val effectiveId = selectedGoatId ?: loaded.firstOrNull()?.animalId?.takeIf { autoSelect }
            val chosen = effectiveId?.let { repository.getGoat(it) }
            Triple(loaded, effectiveId, chosen)
        }.onSuccess { (loaded, effectiveId, chosen) ->
            herd = loaded
            selectedGoatId = effectiveId
            selected = chosen
            pendingSyncCount = app.database.outbox().countUnacknowledgedForFarm(membership.farmId)
            herdState = if (loaded.isEmpty()) LoadableSurfaceState.EMPTY else LoadableSurfaceState.IDLE
        }.onFailure { failure ->
            error = failure.message
            herdState = LoadableSurfaceState.ERROR
        }
    }

    suspend fun refreshMembershipAfterAuthorizationLoss(message: String) {
        val available = runCatching { app.identityClient?.memberships().orEmpty() }.getOrDefault(emptyList())
        onRequireFarmReselection(message, available)
    }

    fun handleFailure(failure: Throwable) {
        when (failure) {
            is AuthenticationRequiredException -> onRequireReauth(failure.message)
            else -> error = failure.message
        }
    }

    fun runGoatWrite(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            runCatching {
                block()
                refreshGoatState()
            }.onSuccess {
                syncMessage = "Saved on this device · waiting to sync"
                enqueueSync()
            }.onFailure(::handleFailure)
            busy = false
        }
    }

    LaunchedEffect(membership.farmId) { refreshGoatState() }

    GoatVerticalSliceScreen(
        state = GoatSliceUiState(
            animalId = selectedGoatId,
            herd = herd,
            selected = selected,
            farmName = farmName,
            pendingSyncCount = pendingSyncCount,
            busy = busy,
            error = error,
            herdState = herdState,
            syncMessage = syncMessage,
            searchMessage = searchMessage,
            searchResults = searchResults,
        ),
        entryPage = entryPage,
        onRegister = { tag, name, sex, dateText ->
            runGoatWrite {
                val day = dateText.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it).toEpochDay() }
                val animalId = UUID.randomUUID().toString()
                repository.registerGoat(
                    RegisterGoat(animalId, tag, name, sex, day),
                    newContext(),
                )
                selectedGoatId = animalId
            }
        },
        onRecordWeight = { text ->
            val animalId = selectedGoatId
            if (animalId == null) {
                error = "Select a goat first"
            } else {
                runGoatWrite {
                    val grams = (text.toBigDecimal() * 1000.toBigDecimal()).longValueExact()
                    repository.recordWeight(
                        RecordGoatWeight(
                            animalId = animalId,
                            measurementId = UUID.randomUUID().toString(),
                            weightGrams = grams,
                            measuredAtEpochMillis = System.currentTimeMillis(),
                        ),
                        newContext(),
                    )
                }
            }
        },
        onRecordKidding = { born, live, dead, day ->
            val animalId = selectedGoatId
            if (animalId == null) error = "Select a doe first" else runGoatWrite {
                repository.recordKidding(
                    RecordGoatKidding(
                        kiddingId = UUID.randomUUID().toString(),
                        damAnimalId = animalId,
                        bornCount = born.toInt(),
                        liveCount = live.toInt(),
                        deadCount = dead.toInt(),
                        occurredEpochDay = LocalDate.parse(day).toEpochDay(),
                    ),
                    newContext(),
                )
            }
        },
        onRegisterKid = { kiddingId, tag, sex ->
            val damId = selectedGoatId
            if (damId == null) error = "Select the dam first" else runGoatWrite {
                val kidId = UUID.randomUUID().toString()
                repository.registerKid(
                    RegisterGoatKid(
                        animalId = kidId,
                        kiddingId = kiddingId,
                        tag = tag,
                        name = null,
                        sex = sex,
                        dateOfBirthEpochDay = null,
                        pedigreeLinkId = UUID.randomUUID().toString(),
                    ),
                    newContext(),
                )
                selectedGoatId = kidId
            }
        },
        onRecordFamacha = { score, day ->
            val animalId = selectedGoatId
            if (animalId == null) error = "Select a goat first" else runGoatWrite {
                repository.recordFamacha(
                    RecordGoatFamacha(UUID.randomUUID().toString(), animalId, score.toInt(), LocalDate.parse(day).toEpochDay()),
                    newContext(),
                )
            }
        },
        onRecordMilk = { litres, day ->
            val animalId = selectedGoatId
            if (animalId == null) error = "Select a doe first" else runGoatWrite {
                val milliLitres = (litres.toBigDecimal() * 1000.toBigDecimal()).longValueExact()
                repository.recordMilk(
                    RecordGoatMilk(UUID.randomUUID().toString(), animalId, milliLitres, LocalDate.parse(day).toEpochDay()),
                    newContext(),
                )
            }
        },
        onRecordBcs = { scoreTenths, day ->
            val animalId = selectedGoatId
            if (animalId == null) error = "Select a goat first" else runGoatWrite {
                repository.recordBcs(
                    RecordGoatBcs(UUID.randomUUID().toString(), animalId, scoreTenths.toInt(), LocalDate.parse(day).toEpochDay()),
                    newContext(),
                )
            }
        },
        onRecordScc = { cells, dim, day ->
            val animalId = selectedGoatId
            if (animalId == null) error = "Select a doe first" else runGoatWrite {
                repository.recordScc(
                    RecordGoatScc(
                        recordId = UUID.randomUUID().toString(),
                        animalId = animalId,
                        cellsPerMl = cells.toInt(),
                        dimDays = dim.toIntOrNull(),
                        occurredEpochDay = LocalDate.parse(day).toEpochDay(),
                    ),
                    newContext(),
                )
            }
        },
        onRecordHeat = { day ->
            val animalId = selectedGoatId
            if (animalId == null) error = "Select a doe first" else runGoatWrite {
                repository.recordHeat(
                    RecordGoatHeat(UUID.randomUUID().toString(), animalId, LocalDate.parse(day).toEpochDay()),
                    newContext(),
                )
            }
        },
        onRecordMating = { method, sireId, day ->
            val animalId = selectedGoatId
            if (animalId == null) error = "Select a doe first" else runGoatWrite {
                repository.recordMating(
                    RecordGoatMating(
                        matingId = UUID.randomUUID().toString(),
                        damId = animalId,
                        sireId = sireId.ifBlank { null },
                        method = method,
                        occurredEpochDay = LocalDate.parse(day).toEpochDay(),
                        pregCheckTaskId = UUID.randomUUID().toString(),
                    ),
                    newContext(),
                )
            }
        },
        onRecordPregnancy = { result, day ->
            val animalId = selectedGoatId
            if (animalId == null) error = "Select a doe first" else runGoatWrite {
                repository.recordPregnancy(
                    RecordGoatPregnancy(UUID.randomUUID().toString(), animalId, result, LocalDate.parse(day).toEpochDay()),
                    newContext(),
                )
            }
        },
        onPlanLactation = { day ->
            val animalId = selectedGoatId
            if (animalId == null) error = "Select a doe first" else runGoatWrite {
                repository.planLactation(
                    PlanGoatLactation(
                        planId = UUID.randomUUID().toString(),
                        animalId = animalId,
                        kiddingId = null,
                        occurredEpochDay = LocalDate.parse(day).toEpochDay(),
                        checkTaskId = UUID.randomUUID().toString(),
                    ),
                    newContext(),
                )
            }
        },
        onSetStatus = { status ->
            val animalId = selectedGoatId
            if (animalId == null) error = "Select a goat first" else runGoatWrite {
                repository.setStatus(SetGoatStatus(animalId, status), newContext())
            }
        },
        onSelectGoat = { animalId ->
            scope.launch {
                selectedGoatId = animalId
                selected = repository.getGoat(animalId)
            }
        },
        onSyncNow = {
            scope.launch {
                busy = true
                error = null
                syncMessage = "Syncing"
                runCatching {
                    val push = app.syncEngine.drain()
                    push to if (push.authorizationLoss == null) app.farmPullReconciler()?.reconcile(membership.farmId) else null
                }.onSuccess { (push, pull) ->
                    when (push.authorizationLoss) {
                        AuthorizationLoss.SESSION_EXPIRED -> onRequireReauth(null)
                        AuthorizationLoss.FARM_ACCESS_REVOKED -> refreshMembershipAfterAuthorizationLoss(
                            "Farm access was removed. Pending local records stayed on this device and were not sent.",
                        )
                        null -> {
                            syncMessage = when {
                                push.conflicts > 0 -> "Conflict needs review"
                                push.rejected > 0 -> "Server rejected a pending record"
                                push.retrying > 0 -> "Saved locally · server retry pending"
                                push.acknowledged > 0 || (pull?.appliedEvents ?: 0) > 0 ->
                                    "Synced · ${pull?.appliedEvents ?: 0} server changes applied"
                                else -> "Synced · no new server changes"
                            }
                            refreshGoatState()
                        }
                    }
                }.onFailure { failure ->
                    if (failure is AuthenticationRequiredException) {
                        onRequireReauth(failure.message)
                    } else {
                        syncMessage = "Saved locally · sync failed. Entries stay on this device."
                        error = failure.message
                    }
                }
                busy = false
            }
        },
        onSearch = { query ->
            scope.launch {
                error = null
                runCatching {
                    if (query.isBlank()) emptyList() else repository.searchGoats(query, 25)
                }.onSuccess { local ->
                    searchResults = local
                    searchMessage = "${local.size} local result(s)"
                    runCatching {
                        app.farmSearchClient
                            ?.searchAnimals(membership.farmId, query, 25)
                            ?.filter { it.speciesCode == null || it.speciesCode == "goat" }
                            ?.mapNotNull { hit ->
                                val tag = hit.tag ?: return@mapNotNull null
                                GoatSearchResult(
                                    animalId = hit.id,
                                    tag = tag,
                                    name = hit.displayName?.takeUnless { it == tag },
                                    status = hit.status ?: "active",
                                    source = SearchSource.MEILISEARCH,
                                )
                            }
                            .orEmpty()
                    }.onSuccess { remote ->
                        searchResults = (remote + local).distinctBy { it.animalId }
                        searchMessage = "${local.size} local · ${remote.size} online result(s)"
                    }.onFailure { remoteFailure ->
                        if (remoteFailure is AuthenticationRequiredException) {
                            onRequireReauth(remoteFailure.message)
                        } else {
                            searchMessage = "${local.size} local result(s) · online search unavailable"
                        }
                    }
                }.onFailure(::handleFailure)
            }
        },
        onSignOut = onSignOut,
        onBack = onBack,
    )
}
