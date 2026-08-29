package com.farmos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.farmos.core.sync.SyncWorker
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.LocalCommandContext
import com.farmos.domain.goat.RecordGoatWeight
import com.farmos.domain.goat.RegisterGoat
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
                var state by remember { mutableStateOf(GoatSliceUiState()) }

                GoatVerticalSliceScreen(
                    state = state,
                    onRegister = { tag, name, sex ->
                        scope.launch {
                            state = state.copy(busy = true, error = null)
                            runCatching {
                                val animalId = UUID.randomUUID().toString()
                                app.goatRepository.registerGoat(
                                    RegisterGoat(
                                        animalId = animalId,
                                        tag = tag,
                                        name = name,
                                        sex = sex,
                                    ),
                                    newContext(),
                                )
                                app.goatRepository.getGoat(animalId)
                            }.onSuccess { goat ->
                                state = state.copy(
                                    busy = false,
                                    animalId = goat?.animalId,
                                    goatSummary = goat?.let { "${it.tag} · ${it.sex.name.lowercase()} · saved on this device" },
                                    syncMessage = "Saved on this device · waiting to sync",
                                )
                                enqueueSync()
                            }.onFailure { error ->
                                state = state.copy(busy = false, error = error.message ?: "Could not register goat")
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
                                app.goatRepository.recordWeight(
                                    RecordGoatWeight(
                                        animalId = animalId,
                                        measurementId = UUID.randomUUID().toString(),
                                        weightGrams = (kg * 1_000.0).toLong(),
                                        measuredAtEpochMillis = System.currentTimeMillis(),
                                    ),
                                    newContext(),
                                )
                                app.goatRepository.getGoat(animalId)
                            }.onSuccess { goat ->
                                val kgText = goat?.latestWeightGrams?.let { "%.2f".format(it / 1_000.0) }
                                state = state.copy(
                                    busy = false,
                                    goatSummary = goat?.let { "${it.tag} · ${it.sex.name.lowercase()} · $kgText kg" },
                                    syncMessage = "Weight saved on this device · waiting to sync",
                                )
                                enqueueSync()
                            }.onFailure { error ->
                                state = state.copy(busy = false, error = error.message ?: "Could not record weight")
                            }
                        }
                    },
                    onSyncNow = {
                        scope.launch {
                            state = state.copy(busy = true, error = null, syncMessage = "Syncing")
                            val run = app.syncEngine.drain()
                            state = state.copy(
                                busy = false,
                                syncMessage = when {
                                    run.acknowledged > 0 && run.retrying == 0 -> "Synced"
                                    run.conflicts > 0 -> "Conflict needs review"
                                    run.rejected > 0 -> "Server rejected a pending record"
                                    run.retrying > 0 -> "Waiting for Supabase connection/authentication"
                                    else -> "No records waiting"
                                },
                            )
                        }
                    },
                )
            }
        }
    }

    private fun newContext() = LocalCommandContext(
        farmId = "bootstrap-farm",
        actorId = "bootstrap-actor",
        deviceId = "bootstrap-device",
        mutationId = UUID.randomUUID().toString(),
        occurredAtEpochMillis = System.currentTimeMillis(),
    )

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
