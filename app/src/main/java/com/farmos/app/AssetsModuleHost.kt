package com.farmos.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.CreateFarmAsset
import com.farmos.domain.ops.RecordMaintenance
import com.farmos.feature.ops.SimpleCaptureScreen
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

/** Dedicated asset register and maintenance orchestration boundary. */
@Composable
fun AssetsModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf(emptyList<String>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        rows = ops.assets().map { "${it.id} ${it.code} · ${it.name}" }
    }

    LaunchedEffect(farmId) { runCatching { refresh() } }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            runCatching {
                block()
                refresh()
            }.onSuccess { enqueueSync() }
                .onFailure { error = it.message }
            busy = false
        }
    }

    val code = remember { mutableStateOf("") }
    val name = remember { mutableStateOf("") }
    val kind = remember { mutableStateOf("equipment") }
    val assetId = remember { mutableStateOf("") }
    val title = remember { mutableStateOf("") }
    val day = remember { mutableStateOf("") }

    SimpleCaptureScreen(
        screenId = "FOS-ASSET-001",
        title = "Assets",
        help = "Record equipment and maintenance. This is not a depreciation ledger.",
        empty = "No assets on this device.",
        rows = rows,
        busy = busy,
        error = error,
        fields = listOf("Code" to code, "Name" to name, "Kind" to kind),
        actionLabel = "Create asset",
        onSubmit = {
            run { ops.createAsset(CreateFarmAsset(UUID.randomUUID().toString(), code.value, name.value, kind.value), newContext()) }
        },
        onBack = onBack,
        extra = {
            androidx.compose.material3.OutlinedTextField(assetId.value, { assetId.value = it }, label = { androidx.compose.material3.Text("Asset id") }, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(title.value, { title.value = it }, label = { androidx.compose.material3.Text("Maintenance title") }, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(day.value, { day.value = it }, label = { androidx.compose.material3.Text("Date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.Button(
                onClick = {
                    run {
                        ops.recordMaintenance(
                            RecordMaintenance(UUID.randomUUID().toString(), assetId.value, title.value, LocalDate.parse(day.value).toEpochDay()),
                            newContext(),
                        )
                    }
                },
                enabled = !busy && assetId.value.isNotBlank() && title.value.isNotBlank() && day.value.isNotBlank(),
            ) { androidx.compose.material3.Text("Record maintenance") }
        },
    )
}
