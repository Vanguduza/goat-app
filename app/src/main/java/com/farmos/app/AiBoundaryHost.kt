package com.farmos.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalRows
import com.farmos.core.design.FarmOperationalSection
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.farmos.core.design.runSuspendCatching

/** AI module pages; each maps to one canonical FOS-AI-* screen. */
private enum class AiPage(val screenId: String, val title: String) {
    HOME("FOS-AI-001", "Copilot home"),
    ASK("FOS-AI-002", "Ask Farm OS"),
    CONVERSATION("FOS-AI-003", "Conversation"),
    INSIGHT("FOS-AI-004", "Insight detail"),
    EVIDENCE("FOS-AI-005", "Evidence sources"),
    SUGGESTED("FOS-AI-006", "Suggested actions"),
    REVIEW("FOS-AI-007", "Action review"),
    ANOMALY("FOS-AI-008", "Anomaly review"),
    PROVIDER("FOS-AI-009", "Model provider settings"),
    API_KEY("FOS-AI-010", "Provider connection"),
    PRIVACY("FOS-AI-011", "AI privacy and controls"),
    UNAVAILABLE("FOS-AI-012", "AI unavailable"),
}

/** A deterministic on-device suggested action. Advisory only: nothing executes without the owner. */
private data class SuggestedAction(
    val id: String,
    val title: String,
    val detail: String,
    val evidence: String,
    val ownerModule: FarmModule,
    val ownerScreenId: String,
)

private data class AiProviderConfig(
    val providerId: String,
    val endpoint: String,
    val keyStored: Boolean,
)

/**
 * Farm OS-owned AI boundary. Vendor SDKs live behind this boundary; until a provider adapter is
 * connected, every analytical answer is computed on-device from local records and labelled as such.
 * No prescribing, dosing, treating, culling, selling, posting or authorization bypass happens here.
 */
private class AiProviderStore(filesDir: File, farmId: String) {
    private val dir = File(filesDir, "ai_boundary/$farmId").also { it.mkdirs() }
    private val configFile = File(dir, "provider.json")
    private val keyFile = File(dir, "provider_key.sealed")
    private val sealer = KeystoreSealer("goat_farm_ai_v1")

    fun load(): AiProviderConfig {
        val json = configFile.takeIf { it.exists() }?.let { JSONObject(it.readText()) }
        return AiProviderConfig(
            providerId = json?.optString("providerId").orEmpty(),
            endpoint = json?.optString("endpoint").orEmpty(),
            keyStored = keyFile.exists(),
        )
    }

    fun saveProvider(providerId: String, endpoint: String) {
        configFile.writeText(JSONObject().put("providerId", providerId).put("endpoint", endpoint).toString())
    }

    fun storeKey(plaintext: String) {
        keyFile.writeBytes(sealer.seal(plaintext.toByteArray(Charsets.UTF_8)))
    }

    fun clearKey() {
        keyFile.delete()
    }
}

/** Dedicated AI-boundary orchestration. */
@Composable
fun AiBoundaryHost(
    database: FarmOsDatabase,
    farmId: String,
    filesDir: File,
    onOpenModule: (FarmModule) -> Unit,
    onBack: () -> Unit,
) {
    var page by remember(farmId) { mutableStateOf(AiPage.HOME) }
    var config by remember(farmId) { mutableStateOf<AiProviderConfig?>(null) }
    var suggestions by remember(farmId) { mutableStateOf(listOf<SuggestedAction>()) }
    var dismissed by remember(farmId) { mutableStateOf(setOf<String>()) }
    var selectedAction by remember { mutableStateOf<SuggestedAction?>(null) }
    var log by remember(farmId) { mutableStateOf(listOf<String>()) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val store = remember(farmId) { AiProviderStore(filesDir, farmId) }

    suspend fun refreshSuggestions() {
        val out = mutableListOf<SuggestedAction>()
        val inventory = database.reports().inventoryTotals(farmId)
        if ((inventory?.atOrBelowReorder ?: 0) > 0) out += SuggestedAction(
            id = "reorder",
            title = "Review procurement",
            detail = "${inventory?.atOrBelowReorder} inventory item(s) at or below reorder level.",
            evidence = "FarmReportDao.inventoryTotals",
            ownerModule = FarmModule.PROCUREMENT,
            ownerScreenId = "FOS-PROC-001",
        )
        val health = database.reports().healthTotals(farmId, java.time.LocalDate.now().toEpochDay())
        if (health.activeWithdrawals > 0) out += SuggestedAction(
            id = "withdrawals",
            title = "Check withdrawal windows",
            detail = "${health.activeWithdrawals} active medication withdrawal window(s).",
            evidence = "FarmReportDao.healthTotals",
            ownerModule = FarmModule.HEALTH,
            ownerScreenId = "FOS-HEALTH-009",
        )
        val deaths = database.animalExits().exitKindCounts(farmId).firstOrNull { it.kind == "DEATH" }?.exits ?: 0
        if (deaths > 0) out += SuggestedAction(
            id = "mortality",
            title = "Review mortality",
            detail = "$deaths death exit(s) recorded. Review causes with the health module.",
            evidence = "AnimalExitDao.exitKindCounts",
            ownerModule = FarmModule.HEALTH,
            ownerScreenId = "FOS-HEALTH-001",
        )
        val conflicts = database.lifecycle().animalsWithConflictingParentage(farmId)
        if (conflicts.isNotEmpty()) out += SuggestedAction(
            id = "parentage",
            title = "Resolve parentage conflicts",
            detail = "${conflicts.size} animal(s) with conflicting parentage records.",
            evidence = "LifecycleDao.animalsWithConflictingParentage",
            ownerModule = FarmModule.GENETICS,
            ownerScreenId = "FOS-GEN-004",
        )
        suggestions = out
    }

    LaunchedEffect(farmId) {
        runSuspendCatching {
            withContext(Dispatchers.IO) { config = store.load() }
            refreshSuggestions()
        }.onFailure { error = it.message }
    }

    fun appendLog(entry: String) {
        log = (listOf(entry) + log).take(50)
    }

    val current = page
    FarmOperationalPage(
        screenId = current.screenId,
        title = current.title,
        subtitle = "Advisory and evidence-bound. Nothing here acts on the farm without you.",
        onBack = if (current == AiPage.HOME) onBack else ({ page = AiPage.HOME }),
        backLabel = if (current == AiPage.HOME) "Farm home" else "Copilot home",
    ) {
        if (error != null) {
            FarmOperationalSection("Attention", error) {}
        }
        fun nav(target: AiPage, label: String) {
            TextButton(onClick = { page = target }, modifier = Modifier.fillMaxWidth()) { Text(label) }
        }
        when (current) {
            AiPage.HOME -> {
                FarmOperationalSection("Copilot home") {
                    Text(
                        "On-device analysis answers from your farm's own records. A connected model provider " +
                            "can assist with questions; it never prescribes, doses, treats, culls, sells, posts " +
                            "accounts or bypasses permissions.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                FarmOperationalSection("Work areas") {
                    nav(AiPage.ASK, "Ask Farm OS")
                    nav(AiPage.CONVERSATION, "Conversation log")
                    nav(AiPage.SUGGESTED, "Suggested actions")
                    nav(AiPage.REVIEW, "Action review")
                    nav(AiPage.ANOMALY, "Anomaly review")
                    nav(AiPage.PROVIDER, "Model provider settings")
                    nav(AiPage.PRIVACY, "AI privacy and controls")
                }
                val pending = suggestions.filter { it.id !in dismissed }
                if (pending.isNotEmpty()) {
                    FarmOperationalSection("Suggested actions (${pending.size})", "Advisory only.") {
                        pending.take(3).forEach { action ->
                            Text("• ${action.title}", style = MaterialTheme.typography.bodyMedium)
                        }
                        nav(AiPage.SUGGESTED, "Review all")
                    }
                }
            }
            AiPage.ASK -> {
                var question by remember { mutableStateOf("") }
                var answer by remember { mutableStateOf<String?>(null) }
                FarmOperationalSection("Ask Farm OS") {
                    Text(
                        "No model provider is connected, so answers come from on-device analysis of your records.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(value = question, onValueChange = { question = it }, label = { Text("Your question") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        scope.launch {
                            val q = question.trim().lowercase()
                            val result = runCatching {
                                val hits = suggestions.filter { s ->
                                    q.isNotBlank() && (s.title.lowercase() in q || s.detail.lowercase().split(" ").any { it.length > 4 && it in q })
                                }
                                if (q.isBlank()) "Ask about reorder levels, withdrawals, mortality or parentage."
                                else if (hits.isEmpty()) "On-device analysis found no matching farm signals for that question. Try asking about inventory, withdrawals, mortality or genetics."
                                else "On-device analysis found ${hits.size} relevant signal(s):\n" + hits.joinToString("\n") { "• ${it.title}: ${it.detail}" }
                            }.getOrElse { "Could not analyse: ${it.message}" }
                            answer = result
                            appendLog("Q: ${question.trim().take(80)}\nA: ${result.take(200)}")
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Analyse on-device") }
                    answer?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
                nav(AiPage.PROVIDER, "Connect a model provider")
            }
            AiPage.CONVERSATION -> {
                FarmOperationalSection("Conversation log", "On-device session notes. Nothing leaves the device.") {
                    if (log.isEmpty()) Text("No questions asked yet this session.", style = MaterialTheme.typography.bodyMedium)
                    log.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            AiPage.INSIGHT -> {
                val action = selectedAction
                FarmOperationalSection("Insight detail") {
                    if (action == null) Text("Open an item from suggested actions or anomaly review.", style = MaterialTheme.typography.bodyMedium)
                    else {
                        Text(action.title, style = MaterialTheme.typography.titleMedium)
                        Text(action.detail, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { page = AiPage.EVIDENCE }, modifier = Modifier.fillMaxWidth()) { Text("View evidence sources") }
                    }
                }
            }
            AiPage.EVIDENCE -> {
                FarmOperationalSection("Evidence sources", "The exact local queries behind the insight.") {
                    Text(selectedAction?.evidence ?: "No insight selected.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            AiPage.SUGGESTED -> {
                FarmOperationalSection("Suggested actions", "Advisory only. Each action needs your review; nothing executes here.") {
                    val pending = suggestions.filter { it.id !in dismissed }
                    if (pending.isEmpty()) Text("No pending suggestions.", style = MaterialTheme.typography.bodyMedium)
                    pending.forEach { action ->
                        Text(action.title, style = MaterialTheme.typography.titleMedium)
                        Text(action.detail, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { selectedAction = action; page = AiPage.REVIEW }, modifier = Modifier.fillMaxWidth()) {
                            Text("Review")
                        }
                    }
                }
            }
            AiPage.REVIEW -> {
                val action = selectedAction
                FarmOperationalSection("Action review", "Accepting opens the owning screen. The action itself is yours to take.") {
                    if (action == null) {
                        Text("Select a suggested action to review it.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(action.title, style = MaterialTheme.typography.titleMedium)
                        Text(action.detail, style = MaterialTheme.typography.bodyMedium)
                        Text("Owner screen: ${action.ownerScreenId}", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { onOpenModule(action.ownerModule) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Open owning screen")
                        }
                        TextButton(onClick = {
                            dismissed = dismissed + action.id
                            appendLog("Dismissed: ${action.title}")
                            page = AiPage.SUGGESTED
                        }, modifier = Modifier.fillMaxWidth()) { Text("Dismiss") }
                    }
                }
            }
            AiPage.ANOMALY -> {
                FarmOperationalSection("Anomaly review", "Deterministic on-device checks over local records.") {
                    if (suggestions.isEmpty()) Text("No anomalies flagged.", style = MaterialTheme.typography.bodyMedium)
                    suggestions.forEach { action ->
                        TextButton(onClick = { selectedAction = action; page = AiPage.INSIGHT }, modifier = Modifier.fillMaxWidth()) {
                            Text(action.title)
                        }
                    }
                }
            }
            AiPage.PROVIDER -> {
                var providerId by remember(config) { mutableStateOf(config?.providerId ?: "") }
                var endpoint by remember(config) { mutableStateOf(config?.endpoint ?: "") }
                FarmOperationalSection("Model provider settings", "Vendor adapters connect here. None is bundled.") {
                    Text(
                        "Status: " + if ((config?.providerId.orEmpty()).isBlank()) "no provider configured"
                        else "provider '${config?.providerId}' recorded, key ${if (config?.keyStored == true) "stored" else "missing"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(value = providerId, onValueChange = { providerId = it }, label = { Text("Provider id") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, label = { Text("Endpoint (optional)") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        scope.launch {
                            runSuspendCatching { withContext(Dispatchers.IO) { store.saveProvider(providerId.trim(), endpoint.trim()); config = store.load() } }
                                .onFailure { error = it.message }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Save provider") }
                    nav(AiPage.API_KEY, "Manage provider key")
                    nav(AiPage.UNAVAILABLE, "Why is AI unavailable?")
                }
            }
            AiPage.API_KEY -> {
                var key by remember { mutableStateOf("") }
                FarmOperationalSection(
                    "Provider connection",
                    "The key is sealed with this device's Keystore and stored in app-private storage. It is never logged, backed up, or sent anywhere by Farm OS itself.",
                ) {
                    Text("Key status: ${if (config?.keyStored == true) "stored" else "not stored"}", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("API key") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        scope.launch {
                            runSuspendCatching {
                                require(key.isNotBlank()) { "Enter a key to store it" }
                                withContext(Dispatchers.IO) { store.storeKey(key); key = ""; config = store.load() }
                            }.onFailure { error = it.message }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Store key") }
                    TextButton(onClick = {
                        scope.launch {
                            runSuspendCatching { withContext(Dispatchers.IO) { store.clearKey(); config = store.load() } }
                                .onFailure { error = it.message }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Remove stored key") }
                }
            }
            AiPage.PRIVACY -> {
                FarmOperationalSection("AI privacy and controls") {
                    Text("• Analysis runs on-device against your farm's local database.", style = MaterialTheme.typography.bodyMedium)
                    Text("• No farm records leave the device for AI processing unless you connect a provider.", style = MaterialTheme.typography.bodyMedium)
                    Text("• Provider keys are sealed in this device's Keystore and excluded from backups.", style = MaterialTheme.typography.bodyMedium)
                    Text("• AI never prescribes, doses, treats, culls, sells, posts accounts, or bypasses your role permissions.", style = MaterialTheme.typography.bodyMedium)
                    Text("• Suggested actions always need your explicit review in the owning screen.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            AiPage.UNAVAILABLE -> {
                FarmOperationalSection(
                    "AI unavailable",
                    "This is an explicit state, not a silent failure.",
                ) {
                    Text(
                        "Conversational AI needs a connected model provider. Until then, on-device analysis " +
                            "covers inventory, withdrawals, mortality and genetics signals.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    nav(AiPage.PROVIDER, "Configure a provider")
                    nav(AiPage.ASK, "Use on-device analysis")
                }
            }
        }
    }
}
