package com.farmos.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmCanvas
import com.farmos.core.design.AnimalFarmModuleHeader
import com.farmos.core.design.FarmIllustratedSectionSurface
import com.farmos.core.network.AuthenticationRequiredException
import kotlinx.coroutines.launch

internal data class GlobalSearchResultUi(
    val animalId: String,
    val speciesCode: String,
    val tag: String,
    val displayName: String?,
    val status: String,
    val source: String,
)

@Composable
internal fun GlobalSearchHost(
    app: FarmOsApplication,
    farmId: String,
    onOpen: (FarmDestination) -> Unit,
    onBack: () -> Unit,
    onRequireReauth: (String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Search animal tags, names or species. Local records work offline.") }
    var results by remember { mutableStateOf<List<GlobalSearchResultUi>>(emptyList()) }

    GlobalSearchScreen(
        busy = busy,
        searched = searched,
        message = message,
        results = results,
        onSearch = { rawQuery ->
            val query = rawQuery.trim()
            if (query.isNotEmpty()) {
                scope.launch {
                    busy = true
                    searched = true
                val local = runCatching {
                    app.database.animals().searchAll(farmId, query, 50).map {
                        GlobalSearchResultUi(
                            animalId = it.id,
                            speciesCode = it.speciesCode,
                            tag = it.tag,
                            displayName = it.name,
                            status = it.status,
                            source = "local",
                        )
                    }
                }.getOrElse {
                    message = "Local search failed: ${it.message ?: "unknown error"}"
                    emptyList()
                }
                results = local
                message = "${local.size} local result(s)"

                val client = app.farmSearchClient
                if (client != null) {
                    runCatching { client.searchAnimals(farmId, query, 50) }
                        .onSuccess { remoteHits ->
                            val remote = remoteHits.map { hit ->
                                GlobalSearchResultUi(
                                    animalId = hit.id,
                                    speciesCode = hit.speciesCode ?: "animal",
                                    tag = hit.tag ?: hit.id,
                                    displayName = hit.displayName,
                                    status = hit.status ?: "active",
                                    source = "online",
                                )
                            }
                            results = (local + remote).distinctBy { it.animalId }
                            message = "${local.size} local · ${remote.size} online result(s)"
                        }
                        .onFailure { failure ->
                            if (failure is AuthenticationRequiredException) {
                                onRequireReauth(failure.message)
                            } else {
                                message = "${local.size} local result(s) · online search unavailable"
                            }
                        }
                }
                    busy = false
                }
            }
        },
        onOpenResult = { result ->
            speciesDestination(result.speciesCode)?.let(onOpen)
        },
        onBack = onBack,
    )
}

@Composable
internal fun GlobalSearchScreen(
    busy: Boolean,
    searched: Boolean,
    message: String,
    results: List<GlobalSearchResultUi>,
    onSearch: (String) -> Unit,
    onOpenResult: (GlobalSearchResultUi) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }

    AnimalFarmCanvas(Modifier.testTag("farm-screen:FOS-HOME-006")) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimalFarmModuleHeader(
                title = "Search farm",
                subtitle = "Animals on this farm · offline first",
            )
            FarmIllustratedSectionSurface {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Tag, name or species") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Button(
                    onClick = { onSearch(query) },
                    enabled = !busy && query.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (busy) "Searching…" else "Search farm")
                }
                Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (searched) {
                FarmIllustratedSectionSurface(
                    Modifier.testTag("farm-screen:FOS-HOME-007"),
                ) {
                    Text(
                        "Search results",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (results.isEmpty()) {
                        Text("No matching animal records.")
                    } else {
                        results.forEach { result ->
                            val label = buildString {
                                append(result.speciesCode.replaceFirstChar { it.uppercase() })
                                append(" · ").append(result.tag)
                                result.displayName?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                                append(" · ").append(result.status)
                                append(" · ").append(result.source)
                            }
                            if (speciesDestination(result.speciesCode) != null) {
                                TextButton(
                                    onClick = { onOpenResult(result) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(label)
                                }
                            } else {
                                Text(label)
                            }
                        }
                    }
                }
            }

            TextButton(onClick = onBack) { Text("Farm home") }
        }
    }
}

private fun speciesDestination(speciesCode: String): FarmDestination? =
    when (speciesCode.lowercase()) {
        "goat" -> FarmDestination.Goat()
        "rabbit" -> FarmDestination.Module(FarmModule.RABBIT)
        "sheep" -> FarmDestination.Module(FarmModule.SHEEP)
        "cattle" -> FarmDestination.Module(FarmModule.CATTLE)
        "poultry" -> FarmDestination.Module(FarmModule.POULTRY)
        else -> null
    }
