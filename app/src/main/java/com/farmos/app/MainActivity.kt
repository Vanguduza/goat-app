package com.farmos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.farmos.core.design.FarmOsTheme
import com.farmos.core.network.AuthenticationRequiredException
import com.farmos.core.network.FarmMembership
import com.farmos.core.sync.SyncWorker
import com.farmos.domain.goat.GoatRepository
import com.farmos.domain.goat.GoatSearchResult
import com.farmos.domain.goat.LocalCommandContext
import com.farmos.domain.goat.RecordGoatWeight
import com.farmos.domain.goat.RegisterGoat
import com.farmos.domain.goat.SearchSource
import com.farmos.feature.goat.GoatSliceUiState
import com.farmos.feature.goat.GoatVerticalSliceScreen
import java.util.UUID
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FarmOsApplication

        setContent {
            FarmOsTheme {
                val scope = rememberCoroutineScope()
                val restoredMembership = remember { app.lastMembershipForCurrentSession() }
                var memberships by remember {
                    mutableStateOf(restoredMembership?.let(::listOf) ?: emptyList())
                }
                var selectedMembership by remember { mutableStateOf(restoredMembership) }
                var authBusy by remember { mutableStateOf(false) }
                var authError by remember { mutableStateOf<String?>(null) }
                val requireReauthentication: (String?) -> Unit = { message ->
                    app.clearRememberedMembership()
                    memberships = emptyList()
                    selectedMembership = null
                    authBusy = false
                    authError = message ?: "Your session expired. Sign in again."
                }

                LaunchedEffect(restoredMembership?.farmId) {
                    val restored = restoredMembership ?: return@LaunchedEffect
                    val identity = app.identityClient ?: return@LaunchedEffect
                    runCatching { identity.memberships() }
                        .onSuccess { available ->
                            memberships = available
                            val revalidated = available.firstOrNull { it.farmId == restored.farmId }
                            if (revalidated == null) {
                                app.clearRememberedMembership()
                                selectedMembership = null
                                authError = "Farm access changed. Choose an available farm or sign in again."
                            } else {
                                app.rememberMembership(revalidated)
                                selectedMembership = revalidated
                            }
                        }
                        .onFailure { error ->
                            if (error is AuthenticationRequiredException) {
                                requireReauthentication(error.message)
                            } else {
                                authError = error.message ?: "Could not revalidate farm access"
                            }
                        }
                }

                val membership = selectedMembership
                if (membership == null) {
                    FoundationAuthScreen(
                        backendConfigured = app.backendConfigured,
                        busy = authBusy,
                        error = authError,
                        memberships = memberships,
                        onSignIn = { email, password ->
                            scope.launch {
                                authBusy = true
                                authError = null
                                runCatching {
                                    val identity = requireNotNull(app.identityClient) { "Supabase is not configured" }
                                    identity.signIn(email, password)
                                    identity.memberships()
                                }.onSuccess { available ->
                                    authBusy = false
                                    memberships = available
                                    when {
                                        available.isEmpty() -> {
                                            app.clearRememberedMembership()
                                            authError = "This account does not have a Farm OS farm membership yet"
                                        }
                                        available.size == 1 -> {
                                            val onlyMembership = available.single()
                                            app.rememberMembership(onlyMembership)
                                            selectedMembership = onlyMembership
                                        }
                                    }
                                }.onFailure { error ->
                                    authBusy = false
                                    authError = error.message ?: "Sign in failed"
                                }
                            }
                        },
                        onSelectFarm = { selected ->
                            app.rememberMembership(selected)
                            selectedMembership = selected
                        },
                    )
                } else {
                    val farmId = membership.farmId
                    val repository: GoatRepository = remember(farmId) { app.goatRepository(farmId) }
                    var state by remember(farmId) { mutableStateOf(GoatSliceUiState()) }

                    GoatVerticalSliceScreen(
                        state = state,
                        onRegister = { tag, name, sex ->
                            scope.launch {
                                state = state.copy(busy = true, error = null)
                                runCatching {
                                    val animalId = UUID.randomUUID().toString()
                                    repository.registerGoat(
                                        RegisterGoat(
                                            animalId = animalId,
                                            tag = tag,
                                            name = name,
                                            sex = sex,
                                        ),
                                        newContext(app, farmId),
                                    )
                                    repository.getGoat(animalId)
                                }.onSuccess { goat ->
                                    state = state.copy(
                                        busy = false,
                                        animalId = goat?.animalId,
                                        goatSummary = goat?.let { "${it.tag} · ${it.sex.name.lowercase()} · saved on this device" },
                                        syncMessage = "Saved on this device · waiting to sync",
                                    )
                                    enqueueSync()
                                }.onFailure { error ->
                                    if (error is AuthenticationRequiredException) {
                                        requireReauthentication(error.message)
                                    } else {
                                        state = state.copy(busy = false, error = error.message ?: "Could not register goat")
                                    }
                                }
                            }
                        },
                        onRecordWeight = { weightText ->
                            scope.launch {
                                val animalId = state.animalId ?: return@launch
                                val kg = weightText.replace(',', '.').toDoubleOrNull()
                                if (kg == null || kg <= 0.0) {
                                    state = state.copy(error = "Enter a valid weight in kg")
                                    return@launch
                                }
                                state = state.copy(busy = true, error = null)
                                runCatching {
                                    repository.recordWeight(
                                        RecordGoatWeight(
                                            animalId = animalId,
                                            measurementId = UUID.randomUUID().toString(),
                                            weightGrams = (kg * 1_000.0).toLong(),
                                            measuredAtEpochMillis = System.currentTimeMillis(),
                                        ),
                                        newContext(app, farmId),
                                    )
                                    repository.getGoat(animalId)
                                }.onSuccess { goat ->
                                    val kgText = goat?.latestWeightGrams?.let { "%.2f".format(it / 1_000.0) }
                                    state = state.copy(
                                        busy = false,
                                        goatSummary = goat?.let { "${it.tag} · ${it.sex.name.lowercase()} · $kgText kg" },
                                        syncMessage = "Weight saved on this device · waiting to sync",
                                    )
                                    enqueueSync()
                                }.onFailure { error ->
                                    if (error is AuthenticationRequiredException) {
                                        requireReauthentication(error.message)
                                    } else {
                                        state = state.copy(busy = false, error = error.message ?: "Could not record weight")
                                    }
                                }
                            }
                        },
                        onSyncNow = {
                            scope.launch {
                                state = state.copy(busy = true, error = null, syncMessage = "Syncing")
                                runCatching {
                                    val push = app.syncEngine.drain()
                                    val pull = app.goatPullReconciler()?.reconcile(farmId)
                                    push to pull
                                }.onSuccess { (push, pull) ->
                                    state = state.copy(
                                        busy = false,
                                        syncMessage = when {
                                            push.conflicts > 0 -> "Conflict needs review"
                                            push.rejected > 0 -> "Server rejected a pending record"
                                            push.retrying > 0 -> "Saved locally · server retry pending"
                                            (push.acknowledged > 0) || ((pull?.appliedEvents ?: 0) > 0) -> "Synced · ${pull?.appliedEvents ?: 0} server changes applied"
                                            else -> "Synced · no new server changes"
                                        },
                                    )
                                }.onFailure { error ->
                                    if (error is AuthenticationRequiredException) {
                                        requireReauthentication(error.message)
                                    } else {
                                        state = state.copy(
                                            busy = false,
                                            syncMessage = "Saved locally · sync failed",
                                            error = error.message,
                                        )
                                    }
                                }
                            }
                        },
                        onSearch = { query ->
                            scope.launch {
                                state = state.copy(busy = true, error = null)
                                val local = repository.searchGoats(query)
                                state = state.copy(
                                    busy = false,
                                    searchResults = local,
                                    searchMessage = "${local.size} local result(s)",
                                )

                                val onlineAttempt = runCatching {
                                    app.farmSearchClient
                                        ?.searchAnimals(farmId, query, 20)
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
                                }
                                val onlineError = onlineAttempt.exceptionOrNull()
                                if (onlineError is AuthenticationRequiredException) {
                                    requireReauthentication(onlineError.message)
                                    return@launch
                                }
                                val online = onlineAttempt.getOrNull()

                                if (online != null) {
                                    val merged = (online + local).distinctBy { it.animalId }
                                    state = state.copy(
                                        searchResults = merged,
                                        searchMessage = "${local.size} local · ${online.size} online result(s)",
                                    )
                                } else {
                                    state = state.copy(
                                        searchMessage = "${local.size} local result(s) · online search unavailable",
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    private fun newContext(app: FarmOsApplication, farmId: String): LocalCommandContext {
        val actorId = app.sessionStore.current()?.user?.id
            ?: throw AuthenticationRequiredException("Your session expired. Sign in again.")
        return LocalCommandContext(
            farmId = farmId,
            actorId = actorId,
            deviceId = app.deviceId,
            mutationId = UUID.randomUUID().toString(),
            occurredAtEpochMillis = System.currentTimeMillis(),
        )
    }

    private fun enqueueSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(this).enqueue(request)
    }
}
