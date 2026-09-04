package com.farmos.app

import androidx.compose.runtime.Composable
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
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.model.SearchSource
import com.farmos.core.network.AuthenticationRequiredException
import com.farmos.core.network.FarmMembership
import com.farmos.core.sync.SyncWorker
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
import com.farmos.feature.goat.GoatSliceUiState
import com.farmos.feature.goat.GoatVerticalSliceScreen
import com.farmos.feature.goat.LoadableSurfaceState
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun FarmSessionContent(
    app: FarmOsApplication,
    membership: FarmMembership,
    farmName: String?,
    onRequireReauth: (String?) -> Unit,
    onRequireFarmReselection: (String, List<FarmMembership>) -> Unit,
    onSignOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var module by remember { mutableStateOf(FarmModule.HOME) }
    val repository = remember(membership.farmId) { app.goatRepository(membership.farmId) }
    val ops = remember(membership.farmId) { app.opsRepository(membership.farmId) }
    var herd by remember { mutableStateOf<List<GoatSnapshot>>(emptyList()) }
    var selectedGoatId by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<GoatSnapshot?>(null) }
    var remoteResults by remember { mutableStateOf<List<GoatSearchResult>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var herdState by remember { mutableStateOf(LoadableSurfaceState.LOADING) }
    var pendingSyncCount by remember { mutableStateOf(0L) }

    fun context(): LocalCommandContext {
        val userId = requireNotNull(app.sessionStore.current()?.user?.id) { "Sign in is required" }
        return LocalCommandContext(
            mutationId = UUID.randomUUID().toString(),
            farmId = membership.farmId,
            actorId = userId,
            deviceId = app.deviceId,
            occurredAtEpochMillis = System.currentTimeMillis(),
        )
    }

    suspend fun refreshGoatState() {
        herdState = LoadableSurfaceState.LOADING
        runCatching {
            val loaded = repository.listGoats(500)
            val effectiveId = selectedGoatId ?: loaded.firstOrNull()?.animalId
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

    fun enqueueSync() {
        WorkManager.getInstance(app).enqueue(
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    fun runGoatWrite(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            runCatching {
                block()
                refreshGoatState()
            }.onSuccess {
                enqueueSync()
            }.onFailure(::handleFailure)
            busy = false
        }
    }

    LaunchedEffect(membership.farmId) { refreshGoatState() }

    if (module == FarmModule.HOME) {
        FarmHomeScreen(
            farmName = farmName,
            onOpen = { module = it },
            onSignOut = onSignOut,
        )
        return
    }

    if (module == FarmModule.TASKS) {
        TasksModuleHost(
            farmId = membership.farmId,
            ops = ops,
            newContext = ::context,
            enqueueSync = ::enqueueSync,
            onBack = { module = FarmModule.HOME },
        )
        return
    }

    if (module == FarmModule.MONEY) {
        MoneyModuleHost(
            farmId = membership.farmId,
            ops = ops,
            newContext = ::context,
            enqueueSync = ::enqueueSync,
            onBack = { module = FarmModule.HOME },
        )
        return
    }

    if (module == FarmModule.INVENTORY) {
        InventoryModuleHost(
            farmId = membership.farmId,
            ops = ops,
            newContext = ::context,
            enqueueSync = ::enqueueSync,
            onBack = { module = FarmModule.HOME },
        )
        return
    }

    if (module == FarmModule.HEALTH) {
        HealthModuleHost(
            farmId = membership.farmId,
            ops = ops,
            newContext = ::context,
            enqueueSync = ::enqueueSync,
            onBack = { module = FarmModule.HOME },
        )
        return
    }

    if (module != FarmModule.GOAT) {
        OperatingModuleHost(
            module = module,
            farmId = membership.farmId,
            database = app.database,
            ops = ops,
            newContext = ::context,
            enqueueSync = ::enqueueSync,
            onBack = { module = FarmModule.HOME },
        )
        return
    }

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
            remoteResults = remoteResults,
        ),
        onRegister = { tag, name, sex, dateText ->
            runGoatWrite {
                val day = dateText.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it).toEpochDay() }
                val animalId = UUID.randomUUID().toString()
                repository.registerGoat(
                    RegisterGoat(animalId, tag, name, sex, day),
                    context(),
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
                        context(),
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
                    context(),
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
                        damAnimalId = damId,
                        tag = tag,
                        name = null,
                        sex = sex,
                    ),
                    context(),
                )
                selectedGoatId = kidId
            }
        },
        onRecordFamacha = { score, day ->
            val animalId = selectedGoatId
            if (animalId == null) error = "Select a goat first" else runGoatWrite {
                repository.recordFamacha(
                    RecordGoatFamacha(UUID.randomUUID().toString(), animalId, score.toInt(), LocalDate.parse(day).toEpochDay()),
                    context(),
                )
            }
        },
        onRecordMilk = { litres, day ->
            val animalId = selectedGoatId
            if (animalId == null) error = "Select a doe first" else runGoatWrite {
                val milliLitres = (litres.toBigDecimal() * 1000.toBigDecimal()).longValueExact()
                repository.recordMilk(
                    RecordGoatMilk(UUID.randomUUID().toString(), animalId, milliLitres, LocalDate.parse(day).toEpochDay()),
                    context(),
                )
            }
        },
        onRecordBcs = { scoreTenths, day ->
            val animalId = selectedGoatId
            if (animalId == null) error = "Select a goat first" else runGoatWrite {
                repository.recordBcs(
                    RecordGoatBcs(UUID.randomUUID().toString(), animalId, scoreTenths.toInt(), LocalDate.parse(day).toEpochDay()),
                    context(),
                )
            }
        },
        onRecordScc = { cells, dim, day ->
            val animalId = selectedGoatId
            if (animalId == null) error = "Select a doe first" else runGoatWrite {
                repository.recordScc(
                    RecordGoatScc(UUID.randomUUID().toString(), animalId, cells.toLong(), dim.toInt(), LocalDate.parse(day).toEpochDay()),
                    context(),
                )
            }
        },
        onRecordHeat = { day ->
            val animalId = selectedGoatId
            if (animalId == null) error = "Select a doe first" else runGoatWrite {
                repository.recordHeat(
                    RecordGoatHeat(UUID.randomUUID().toString(), animalId, LocalDate.parse(day).toEpochDay()),
                    context(),
                )
            }
        },
        onRecordMating = { method, sireId, day ->
            val animalId = selectedGoatId
            if (animalId == null) error = "Select a doe first" else runGoatWrite {
                repository.recordMating(
                    RecordGoatMating(UUID.randomUUID().toString(), animalId, method, sireId.ifBlank { null }, LocalDate.parse(day).toEpochDay()),
                    context(),
                )
            }
        },
        onRecordPregnancy = { result, day ->
            val animalId = selectedGoatId
            if (animalId == null) error = "Select a doe first" else runGoatWrite {
                repository.recordPregnancy(
                    RecordGoatPregnancy(UUID.randomUUID().toString(), animalId, result, LocalDate.parse(day).toEpochDay()),
                    context(),
                )
            }
        },
        onPlanLactation = { day ->
            val animalId = selectedGoatId
            if (animalId == null) error = "Select a doe first" else runGoatWrite {
                repository.planLactation(
                    PlanGoatLactation(UUID.randomUUID().toString(), animalId, LocalDate.parse(day).toEpochDay()),
                    context(),
                )
            }
        },
        onSetStatus = { status ->
            val animalId = selectedGoatId
            if (animalId == null) error = "Select a goat first" else runGoatWrite {
                repository.setStatus(SetGoatStatus(animalId, status), context())
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
                runCatching { app.performAuthoritativePull() }
                    .onSuccess { refreshGoatState() }
                    .onFailure { failure ->
                        when (failure) {
                            is AuthenticationRequiredException -> onRequireReauth(failure.message)
                            else -> {
                                val message = failure.message.orEmpty()
                                if (message.contains("permission", true) || message.contains("access", true)) {
                                    refreshMembershipAfterAuthorizationLoss(
                                        "Farm access was removed. Choose another farm or sign out.",
                                    )
                                } else {
                                    error = failure.message
                                }
                            }
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
                    remoteResults = local
                    runCatching {
                        app.searchClient?.search(membership.farmId, "goat", query, 25).orEmpty()
                    }.onSuccess { remote ->
                        remoteResults = (local + remote).distinctBy { it.animalId }.map {
                            if (local.any { localRow -> localRow.animalId == it.animalId }) {
                                it.copy(source = SearchSource.LOCAL)
                            } else {
                                it
                            }
                        }
                    }.onFailure { remoteFailure ->
                        if (remoteFailure is AuthenticationRequiredException) onRequireReauth(remoteFailure.message)
                    }
                }.onFailure(::handleFailure)
            }
        },
        onSignOut = onSignOut,
        onBack = { module = FarmModule.HOME },
    )
}
