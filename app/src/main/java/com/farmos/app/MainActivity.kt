package com.farmos.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
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
import com.farmos.core.network.AuthorizationLoss
import com.farmos.core.network.FarmAccessDecision
import com.farmos.core.network.FarmAccessGuard
import com.farmos.core.network.FarmMembership
import com.farmos.core.sync.SyncWorker
import com.farmos.domain.goat.GoatRepository
import com.farmos.domain.goat.GoatSearchResult
import com.farmos.domain.goat.GoatSnapshot
import com.farmos.domain.goat.GoatStatus
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.model.SearchSource
import com.farmos.domain.goat.RecordGoatBcs
import com.farmos.domain.goat.RecordGoatFamacha
import com.farmos.domain.goat.RecordGoatHeat
import com.farmos.domain.goat.RecordGoatMating
import com.farmos.domain.goat.PlanGoatLactation
import com.farmos.domain.goat.RecordGoatPregnancy
import com.farmos.domain.goat.PlanGoatLactation
import com.farmos.domain.goat.RecordGoatScc
import com.farmos.domain.goat.RecordGoatKidding
import com.farmos.domain.goat.RecordGoatMilk
import com.farmos.domain.goat.RecordGoatWeight
import com.farmos.domain.goat.RegisterGoat
import com.farmos.domain.goat.RegisterGoatKid
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.SetGoatStatus
import com.farmos.feature.goat.GoatSliceUiState
import com.farmos.feature.goat.GoatVerticalSliceScreen
import com.farmos.feature.goat.LoadableSurfaceState
import java.time.LocalDate
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
                var sessionPresent by remember { mutableStateOf(app.sessionStore.current() != null) }
                var farmNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
                val requireReauthentication: (String?) -> Unit = { message ->
                    app.applyAuthorizationLoss(AuthorizationLoss.SESSION_EXPIRED)
                    sessionPresent = false
                    memberships = emptyList()
                    selectedMembership = null
                    authBusy = false
                    authError = message ?: "Your session expired. Sign in again."
                }
                val requireFarmReselection: (String, List<FarmMembership>) -> Unit = { message, available ->
                    app.applyAuthorizationLoss(AuthorizationLoss.FARM_ACCESS_REVOKED)
                    sessionPresent = app.sessionStore.current() != null
                    memberships = available
                    selectedMembership = null
                    authBusy = false
                    authError = message
                }

                DisposableEffect(Unit) {
                    app.authorizationListener = { loss ->
                        Handler(Looper.getMainLooper()).post {
                            if (loss == AuthorizationLoss.SESSION_EXPIRED) {
                                requireReauthentication(null)
                            } else {
                                scope.launch {
                                    val available = runCatching {
                                        app.identityClient?.memberships().orEmpty()
                                    }.getOrDefault(emptyList())
                                    requireFarmReselection(
                                        "Farm access was removed. Choose another farm or sign out.",
                                        available,
                                    )
                                }
                            }
                        }
                    }
                    onDispose { app.authorizationListener = null }
                }

                LaunchedEffect(restoredMembership?.farmId) {
                    val restored = restoredMembership ?: return@LaunchedEffect
                    val identity = app.identityClient ?: return@LaunchedEffect
                    runCatching { identity.memberships() }
                        .onSuccess { available ->
                            memberships = available
                            sessionPresent = true
                            farmNames = runCatching {
                                identity.farms(available.map { it.farmId }).associate { it.id to it.name }
                            }.getOrDefault(farmNames)
                            val decision = FarmAccessGuard.decide(
                                sessionPresent = true,
                                rememberedFarmId = restored.farmId,
                                memberships = available,
                            )
                            when (decision) {
                                FarmAccessDecision.GRANTED -> {
                                    val revalidated = available.first { it.farmId == restored.farmId }
                                    app.rememberMembership(revalidated)
                                    selectedMembership = revalidated
                                }
                                else -> {
                                    val loss = FarmAccessGuard.authorizationLoss(decision)
                                    if (loss == AuthorizationLoss.FARM_ACCESS_REVOKED) {
                                        requireFarmReselection(
                                            "Farm access changed. Choose an available farm or sign out.",
                                            available,
                                        )
                                    } else {
                                        selectedMembership = null
                                        authError = "Choose an available farm to continue"
                                    }
                                }
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
                        sessionPresent = sessionPresent,
                        memberships = memberships,
                        farmNames = farmNames,
                        onSignIn = { email, password ->
                            scope.launch {
                                authBusy = true
                                authError = null
                                runCatching {
                                    val identity = requireNotNull(app.identityClient) { "Supabase is not configured" }
                                    identity.signIn(email, password)
                                    val available = identity.memberships()
                                    val names = runCatching {
                                        identity.farms(available.map { it.farmId }).associate { it.id to it.name }
                                    }.getOrDefault(emptyMap())
                                    available to names
                                }.onSuccess { (available, names) ->
                                    authBusy = false
                                    sessionPresent = true
                                    memberships = available
                                    farmNames = names
                                    when {
                                        available.isEmpty() -> {
                                            app.clearRememberedMembership()
                                            authError = "Create a farm on a connection, or wait for an owner to grant access."
                                        }
                                        available.size == 1 -> {
                                            val onlyMembership = available.single()
                                            app.rememberMembership(onlyMembership)
                                            selectedMembership = onlyMembership
                                        }
                                    }
                                }.onFailure { error ->
                                    authBusy = false
                                    sessionPresent = app.sessionStore.current() != null
                                    authError = error.message ?: "Sign in failed"
                                }
                            }
                        },
                        onSelectFarm = { selected ->
                            app.rememberMembership(selected)
                            selectedMembership = selected
                        },
                        onCreateFarm = { name ->
                            scope.launch {
                                authBusy = true
                                authError = null
                                runCatching {
                                    val identity = requireNotNull(app.identityClient) { "Supabase is not configured" }
                                    identity.createFarm(name)
                                }.onSuccess { created ->
                                    authBusy = false
                                    app.rememberMembership(created)
                                    farmNames = farmNames + (created.farmId to name.trim())
                                    memberships = listOf(created)
                                    selectedMembership = created
                                }.onFailure { error ->
                                    authBusy = false
                                    if (error is AuthenticationRequiredException) {
                                        requireReauthentication(error.message)
                                    } else {
                                        authError = error.message ?: "Could not create farm. Check the connection and try again."
                                    }
                                }
                            }
                        },
                        onSignOut = {
                            app.applyAuthorizationLoss(AuthorizationLoss.SESSION_EXPIRED)
                            sessionPresent = false
                            memberships = emptyList()
                            selectedMembership = null
                            authError = null
                        },
                    )
                } else {
                    val farmId = membership.farmId
                    var module by remember(farmId) { mutableStateOf(FarmModule.HOME) }
                    val repository: GoatRepository = remember(farmId) { app.goatRepository(farmId) }
                    var state by remember(farmId) {
                        mutableStateOf(
                            GoatSliceUiState(
                                farmName = farmNames[farmId],
                                herdState = LoadableSurfaceState.LOADING,
                                busy = true,
                            ),
                        )
                    }
                    suspend fun refreshSurface(
                        selectedId: String?,
                        syncMessage: String = state.syncMessage,
                        searchMessage: String = state.searchMessage,
                        searchResults: List<GoatSearchResult> = state.searchResults,
                        error: String? = null,
                    ): GoatSliceUiState {
                        val herd = repository.listGoats()
                        val selected = selectedId?.let { repository.getGoat(it) }
                        return GoatSliceUiState(
                            farmName = farmNames[farmId],
                            herd = herd,
                            herdState = if (herd.isEmpty()) {
                                LoadableSurfaceState.EMPTY
                            } else {
                                LoadableSurfaceState.IDLE
                            },
                            selected = selected,
                            animalId = selected?.animalId,
                            pendingSyncCount = repository.pendingSyncCount(),
                            goatSummary = selected?.let(::summarizeGoat),
                            syncMessage = syncMessage,
                            searchMessage = searchMessage,
                            searchResults = searchResults,
                            busy = false,
                            error = error,
                        )
                    }

                    LaunchedEffect(farmId) {
                        runCatching {
                            val name = app.identityClient?.farm(farmId)?.name
                            if (name != null) {
                                farmNames = farmNames + (farmId to name)
                            }
                            refreshSurface(state.animalId)
                        }.onSuccess { loaded ->
                            state = loaded
                        }.onFailure { error ->
                            if (error is AuthenticationRequiredException) {
                                requireReauthentication(error.message)
                            } else {
                                state = state.copy(
                                    busy = false,
                                    herdState = LoadableSurfaceState.ERROR,
                                    error = error.message ?: "Herd could not be loaded. Retry sync, then open the list again.",
                                )
                            }
                        }
                    }

                    when (module) {
                        FarmModule.HOME -> FarmHomeScreen(
                            farmName = farmNames[farmId],
                            onOpen = { module = it },
                            onSignOut = {
                                app.applyAuthorizationLoss(AuthorizationLoss.SESSION_EXPIRED)
                                sessionPresent = false
                                memberships = emptyList()
                                selectedMembership = null
                                authError = null
                            },
                        )
                        FarmModule.GOAT -> GoatVerticalSliceScreen(
                        state = state,
                        onRegister = { tag, name, sex, dateOfBirthText ->
                            scope.launch {
                                val dateOfBirthEpochDay = dateOfBirthText.trim().takeIf { it.isNotEmpty() }?.let { raw ->
                                    runCatching { LocalDate.parse(raw).toEpochDay() }.getOrElse {
                                        state = state.copy(error = "Enter date of birth as YYYY-MM-DD")
                                        return@launch
                                    }
                                }
                                state = state.copy(busy = true, error = null)
                                runCatching {
                                    val animalId = UUID.randomUUID().toString()
                                    repository.registerGoat(
                                        RegisterGoat(
                                            animalId = animalId,
                                            tag = tag,
                                            name = name,
                                            sex = sex,
                                            dateOfBirthEpochDay = dateOfBirthEpochDay,
                                        ),
                                        newContext(app, farmId),
                                    )
                                    refreshSurface(
                                        selectedId = animalId,
                                        syncMessage = "Saved on this device · waiting to sync",
                                    )
                                }.onSuccess { loaded ->
                                    state = loaded
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
                                    refreshSurface(
                                        selectedId = animalId,
                                        syncMessage = "Weight saved on this device · waiting to sync",
                                    )
                                }.onSuccess { loaded ->
                                    state = loaded
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
                        onRecordKidding = { bornText, liveText, deadText, dayText ->
                            scope.launch {
                                val animalId = state.animalId ?: return@launch
                                val born = bornText.toIntOrNull()
                                val live = liveText.toIntOrNull()
                                val dead = deadText.toIntOrNull() ?: 0
                                val day = runCatching { LocalDate.parse(dayText.trim()).toEpochDay() }.getOrNull()
                                if (born == null || live == null || day == null) {
                                    state = state.copy(error = "Enter born, live, dead counts and a YYYY-MM-DD kidding date")
                                    return@launch
                                }
                                state = state.copy(busy = true, error = null)
                                runCatching {
                                    repository.recordKidding(
                                        RecordGoatKidding(
                                            kiddingId = UUID.randomUUID().toString(),
                                            damAnimalId = animalId,
                                            bornCount = born,
                                            liveCount = live,
                                            deadCount = dead,
                                            occurredEpochDay = day,
                                        ),
                                        newContext(app, farmId),
                                    )
                                    refreshSurface(
                                        selectedId = animalId,
                                        syncMessage = "Kidding saved on this device · waiting to sync",
                                    )
                                }.onSuccess { loaded ->
                                    state = loaded
                                    enqueueSync()
                                }.onFailure { error ->
                                    if (error is AuthenticationRequiredException) {
                                        requireReauthentication(error.message)
                                    } else {
                                        state = state.copy(
                                            busy = false,
                                            error = error.message ?: "Could not record kidding",
                                        )
                                    }
                                }
                            }
                        },
                        onRegisterKid = { kiddingId, tag, sex ->
                            scope.launch {
                                val animalId = state.animalId ?: return@launch
                                if (tag.isBlank()) {
                                    state = state.copy(error = "Enter a kid tag")
                                    return@launch
                                }
                                state = state.copy(busy = true, error = null)
                                runCatching {
                                    repository.registerKid(
                                        RegisterGoatKid(
                                            animalId = UUID.randomUUID().toString(),
                                            kiddingId = kiddingId,
                                            tag = tag,
                                            sex = sex,
                                            pedigreeLinkId = UUID.randomUUID().toString(),
                                        ),
                                        newContext(app, farmId),
                                    )
                                    refreshSurface(
                                        selectedId = animalId,
                                        syncMessage = "Kid saved on this device · waiting to sync",
                                    )
                                }.onSuccess { loaded ->
                                    state = loaded
                                    enqueueSync()
                                }.onFailure { error ->
                                    if (error is AuthenticationRequiredException) {
                                        requireReauthentication(error.message)
                                    } else {
                                        state = state.copy(
                                            busy = false,
                                            error = error.message ?: "Could not register kid",
                                        )
                                    }
                                }
                            }
                        },
                        onRecordMilk = { litresText, dayText ->
                            scope.launch {
                                val animalId = state.animalId ?: return@launch
                                val litres = litresText.replace(',', '.').toDoubleOrNull()
                                val day = runCatching { LocalDate.parse(dayText.trim()).toEpochDay() }.getOrNull()
                                if (litres == null || litres <= 0.0 || day == null) {
                                    state = state.copy(error = "Enter litres and a YYYY-MM-DD milk date")
                                    return@launch
                                }
                                state = state.copy(busy = true, error = null)
                                runCatching {
                                    repository.recordMilk(
                                        RecordGoatMilk(
                                            milkId = UUID.randomUUID().toString(),
                                            animalId = animalId,
                                            litresMilli = (litres * 1000.0).toLong(),
                                            occurredEpochDay = day,
                                        ),
                                        newContext(app, farmId),
                                    )
                                    refreshSurface(
                                        selectedId = animalId,
                                        syncMessage = "Milk saved on this device · waiting to sync",
                                    )
                                }.onSuccess { loaded ->
                                    state = loaded
                                    enqueueSync()
                                }.onFailure { error ->
                                    if (error is AuthenticationRequiredException) {
                                        requireReauthentication(error.message)
                                    } else {
                                        state = state.copy(
                                            busy = false,
                                            error = error.message ?: "Could not record milk",
                                        )
                                    }
                                }
                            }
                        },
                        onRecordBcs = { tenthsText, dayText ->
                            scope.launch {
                                val animalId = state.animalId ?: return@launch
                                val tenths = tenthsText.toIntOrNull()
                                val day = runCatching { LocalDate.parse(dayText.trim()).toEpochDay() }.getOrNull()
                                if (tenths == null || day == null) {
                                    state = state.copy(error = "Enter BCS tenths from 10 to 50 and a YYYY-MM-DD date")
                                    return@launch
                                }
                                state = state.copy(busy = true, error = null)
                                runCatching {
                                    repository.recordBcs(
                                        RecordGoatBcs(
                                            scoreId = UUID.randomUUID().toString(),
                                            animalId = animalId,
                                            scoreTenths = tenths,
                                            occurredEpochDay = day,
                                        ),
                                        newContext(app, farmId),
                                    )
                                    refreshSurface(
                                        selectedId = animalId,
                                        syncMessage = "BCS saved on this device · waiting to sync",
                                    )
                                }.onSuccess { loaded ->
                                    state = loaded
                                    enqueueSync()
                                }.onFailure { error ->
                                    if (error is AuthenticationRequiredException) {
                                        requireReauthentication(error.message)
                                    } else {
                                        state = state.copy(
                                            busy = false,
                                            error = error.message ?: "Could not record BCS",
                                        )
                                    }
                                }
                            }
                        },
                        onRecordScc = { cellsText, dimText, dayText ->
                            scope.launch {
                                val animalId = state.animalId ?: return@launch
                                val cells = cellsText.toIntOrNull()
                                val day = runCatching { LocalDate.parse(dayText.trim()).toEpochDay() }.getOrNull()
                                if (cells == null || day == null) {
                                    state = state.copy(error = "Enter SCC cells per millilitre and a YYYY-MM-DD date")
                                    return@launch
                                }
                                state = state.copy(busy = true, error = null)
                                runCatching {
                                    repository.recordScc(
                                        RecordGoatScc(
                                            recordId = UUID.randomUUID().toString(),
                                            animalId = animalId,
                                            cellsPerMl = cells,
                                            dimDays = dimText.toIntOrNull(),
                                            occurredEpochDay = day,
                                        ),
                                        newContext(app, farmId),
                                    )
                                    refreshSurface(
                                        selectedId = animalId,
                                        syncMessage = "SCC saved on this device · waiting to sync",
                                    )
                                }.onSuccess { loaded ->
                                    state = loaded
                                    enqueueSync()
                                }.onFailure { error ->
                                    if (error is AuthenticationRequiredException) {
                                        requireReauthentication(error.message)
                                    } else {
                                        state = state.copy(
                                            busy = false,
                                            error = error.message ?: "Could not record SCC",
                                        )
                                    }
                                }
                            }
                        },
                        onRecordHeat = { dayText ->
                            scope.launch {
                                val animalId = state.animalId ?: return@launch
                                val day = runCatching { LocalDate.parse(dayText.trim()).toEpochDay() }.getOrNull()
                                if (day == null) {
                                    state = state.copy(error = "Enter a YYYY-MM-DD heat date")
                                    return@launch
                                }
                                state = state.copy(busy = true, error = null)
                                runCatching {
                                    repository.recordHeat(
                                        RecordGoatHeat(
                                            heatId = UUID.randomUUID().toString(),
                                            animalId = animalId,
                                            occurredEpochDay = day,
                                        ),
                                        newContext(app, farmId),
                                    )
                                    refreshSurface(
                                        selectedId = animalId,
                                        syncMessage = "Heat saved on this device · waiting to sync",
                                    )
                                }.onSuccess { loaded ->
                                    state = loaded
                                    enqueueSync()
                                }.onFailure { error ->
                                    if (error is AuthenticationRequiredException) {
                                        requireReauthentication(error.message)
                                    } else {
                                        state = state.copy(
                                            busy = false,
                                            error = error.message ?: "Could not record heat",
                                        )
                                    }
                                }
                            }
                        },
                        onRecordMating = { method, sireId, dayText ->
                            scope.launch {
                                val animalId = state.animalId ?: return@launch
                                val day = runCatching { LocalDate.parse(dayText.trim()).toEpochDay() }.getOrNull()
                                if (day == null) {
                                    state = state.copy(error = "Enter a YYYY-MM-DD mating date")
                                    return@launch
                                }
                                state = state.copy(busy = true, error = null)
                                runCatching {
                                    repository.recordMating(
                                        RecordGoatMating(
                                            matingId = UUID.randomUUID().toString(),
                                            damId = animalId,
                                            sireId = sireId.trim().ifBlank { null },
                                            method = method.trim(),
                                            occurredEpochDay = day,
                                            pregCheckTaskId = UUID.randomUUID().toString(),
                                        ),
                                        newContext(app, farmId),
                                    )
                                    refreshSurface(
                                        selectedId = animalId,
                                        syncMessage = "Mating saved on this device · waiting to sync",
                                    )
                                }.onSuccess { loaded ->
                                    state = loaded
                                    enqueueSync()
                                }.onFailure { error ->
                                    if (error is AuthenticationRequiredException) {
                                        requireReauthentication(error.message)
                                    } else {
                                        state = state.copy(
                                            busy = false,
                                            error = error.message ?: "Could not record mating",
                                        )
                                    }
                                }
                            }
                        },
                        onRecordPregnancy = { result, dayText ->
                            scope.launch {
                                val animalId = state.animalId ?: return@launch
                                val day = runCatching { LocalDate.parse(dayText.trim()).toEpochDay() }.getOrNull()
                                if (day == null) {
                                    state = state.copy(error = "Enter a YYYY-MM-DD pregnancy-check date")
                                    return@launch
                                }
                                state = state.copy(busy = true, error = null)
                                runCatching {
                                    repository.recordPregnancy(
                                        RecordGoatPregnancy(
                                            checkId = UUID.randomUUID().toString(),
                                            animalId = animalId,
                                            result = result.trim(),
                                            occurredEpochDay = day,
                                        ),
                                        newContext(app, farmId),
                                    )
                                    refreshSurface(
                                        selectedId = animalId,
                                        syncMessage = "Pregnancy check saved on this device · waiting to sync",
                                    )
                                }.onSuccess { loaded ->
                                    state = loaded
                                    enqueueSync()
                                }.onFailure { error ->
                                    if (error is AuthenticationRequiredException) {
                                        requireReauthentication(error.message)
                                    } else {
                                        state = state.copy(
                                            busy = false,
                                            error = error.message ?: "Could not record pregnancy check",
                                        )
                                    }
                                }
                            }
                        },
                        onPlanLactation = { dayText ->
                            scope.launch {
                                val animalId = state.animalId ?: return@launch
                                val day = runCatching { LocalDate.parse(dayText.trim()).toEpochDay() }.getOrNull()
                                if (day == null) {
                                    state = state.copy(error = "Enter a YYYY-MM-DD kidding date")
                                    return@launch
                                }
                                state = state.copy(busy = true, error = null)
                                runCatching {
                                    repository.planLactation(
                                        PlanGoatLactation(
                                            planId = UUID.randomUUID().toString(),
                                            animalId = animalId,
                                            occurredEpochDay = day,
                                            checkTaskId = UUID.randomUUID().toString(),
                                        ),
                                        newContext(app, farmId),
                                    )
                                    refreshSurface(
                                        selectedId = animalId,
                                        syncMessage = "Lactation plan saved on this device · waiting to sync",
                                    )
                                }.onSuccess { loaded ->
                                    state = loaded
                                    enqueueSync()
                                }.onFailure { error ->
                                    if (error is AuthenticationRequiredException) {
                                        requireReauthentication(error.message)
                                    } else {
                                        state = state.copy(
                                            busy = false,
                                            error = error.message ?: "Could not plan lactation follow-up",
                                        )
                                    }
                                }
                            }
                        },
                        onRecordFamacha = { scoreText, dayText ->
                            scope.launch {
                                val animalId = state.animalId ?: return@launch
                                val score = scoreText.toIntOrNull()
                                val day = runCatching { LocalDate.parse(dayText.trim()).toEpochDay() }.getOrNull()
                                if (score == null || day == null) {
                                    state = state.copy(error = "Enter a FAMACHA score from 1 to 5 and a YYYY-MM-DD date")
                                    return@launch
                                }
                                state = state.copy(busy = true, error = null)
                                runCatching {
                                    repository.recordFamacha(
                                        RecordGoatFamacha(
                                            scoreId = UUID.randomUUID().toString(),
                                            animalId = animalId,
                                            score = score,
                                            occurredEpochDay = day,
                                        ),
                                        newContext(app, farmId),
                                    )
                                    refreshSurface(
                                        selectedId = animalId,
                                        syncMessage = "FAMACHA saved on this device · waiting to sync",
                                    )
                                }.onSuccess { loaded ->
                                    state = loaded
                                    enqueueSync()
                                }.onFailure { error ->
                                    if (error is AuthenticationRequiredException) {
                                        requireReauthentication(error.message)
                                    } else {
                                        state = state.copy(
                                            busy = false,
                                            error = error.message ?: "Could not record FAMACHA",
                                        )
                                    }
                                }
                            }
                        },
                        onSetStatus = { nextStatus ->
                            scope.launch {
                                val animalId = state.animalId ?: return@launch
                                state = state.copy(busy = true, error = null)
                                runCatching {
                                    repository.setStatus(
                                        SetGoatStatus(animalId = animalId, status = nextStatus),
                                        newContext(app, farmId),
                                    )
                                    refreshSurface(
                                        selectedId = animalId,
                                        syncMessage = "Status saved on this device · waiting to sync",
                                    )
                                }.onSuccess { loaded ->
                                    state = loaded
                                    enqueueSync()
                                }.onFailure { error ->
                                    if (error is AuthenticationRequiredException) {
                                        requireReauthentication(error.message)
                                    } else {
                                        state = state.copy(
                                            busy = false,
                                            error = error.message ?: "Could not change goat status",
                                        )
                                    }
                                }
                            }
                        },
                        onSelectGoat = { animalId ->
                            scope.launch {
                                state = state.copy(busy = true, error = null)
                                runCatching { refreshSurface(animalId) }
                                    .onSuccess { state = it }
                                    .onFailure { error ->
                                        state = state.copy(
                                            busy = false,
                                            error = error.message ?: "Goat record could not be opened",
                                        )
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
                                    val loss = push.authorizationLoss
                                    if (loss == AuthorizationLoss.SESSION_EXPIRED) {
                                        requireReauthentication(null)
                                        return@launch
                                    }
                                    if (loss == AuthorizationLoss.FARM_ACCESS_REVOKED) {
                                        val available = runCatching {
                                            app.identityClient?.memberships().orEmpty()
                                        }.getOrDefault(emptyList())
                                        requireFarmReselection(
                                            "Farm access was removed. Pending local records stayed on this device and were not sent.",
                                            available,
                                        )
                                        return@launch
                                    }
                                    state = refreshSurface(
                                        selectedId = state.animalId,
                                        syncMessage = when {
                                            push.conflicts > 0 -> "Conflict needs review"
                                            push.rejected > 0 -> "Server rejected a pending record"
                                            push.retrying > 0 -> "Saved locally · server retry pending"
                                            (push.acknowledged > 0) || ((pull?.appliedEvents ?: 0) > 0) ->
                                                "Synced · ${pull?.appliedEvents ?: 0} server changes applied"
                                            else -> "Synced · no new server changes"
                                        },
                                    )
                                }.onFailure { error ->
                                    if (error is AuthenticationRequiredException) {
                                        requireReauthentication(error.message)
                                    } else {
                                        state = state.copy(
                                            busy = false,
                                            herdState = LoadableSurfaceState.ERROR,
                                            syncMessage = "Saved locally · sync failed. Entries stay on this device.",
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
                        onSignOut = {
                            app.applyAuthorizationLoss(AuthorizationLoss.SESSION_EXPIRED)
                            sessionPresent = false
                            memberships = emptyList()
                            selectedMembership = null
                            authError = null
                        },
                        onBack = { module = FarmModule.HOME },
                    )
                        else -> OperatingModuleHost(
                            module = module,
                            farmId = farmId,
                            database = app.database,
                            ops = remember(farmId) { app.opsRepository(farmId) },
                            newContext = { newContext(app, farmId) },
                            enqueueSync = { enqueueSync() },
                            onBack = { module = FarmModule.HOME },
                        )
                    }
                }
            }
        }
    }

    private fun summarizeGoat(goat: GoatSnapshot): String = buildString {
        append(goat.tag)
        append(" · ")
        append(goat.sex.name.lowercase())
        append(" · ")
        append(goat.status.wireValue())
        goat.latestWeightGrams?.let { grams ->
            append(" · ")
            append("%.2f".format(grams / 1_000.0))
            append(" kg")
        }
        if (goat.syncPending) append(" · waiting to sync")
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
            .addTag(FarmOsApplication.SYNC_WORK_TAG)
            .build()
        WorkManager.getInstance(this).enqueue(request)
    }
}
