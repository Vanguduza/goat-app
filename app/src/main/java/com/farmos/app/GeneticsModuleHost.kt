package com.farmos.app

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.testTag
import com.farmos.core.design.FarmMetricStrip
import com.farmos.core.design.FarmMetricValue
import com.farmos.core.design.FarmModuleTools
import com.farmos.core.design.FarmToolGroup
import com.farmos.core.design.FarmToolAction
import com.farmos.core.design.FarmSectionTabs
import com.farmos.core.design.FarmMetricBars
import com.farmos.core.design.FarmBarValue
import com.farmos.core.design.FarmPedigreeFamily
import com.farmos.core.design.FarmPedigreeView

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import com.farmos.core.design.FarmPrimaryButton as Button
import androidx.compose.material3.MaterialTheme
import com.farmos.core.design.FarmOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Text
import com.farmos.core.design.FarmActionButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.AnimalEntity
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalRows
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.PedigreeQueries
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.LinkPedigree
import com.farmos.domain.ops.PedigreeParents
import java.util.UUID
import kotlinx.coroutines.launch
import com.farmos.core.design.runSuspendCatching

/** Genetics module pages; each maps to one canonical FOS-GEN-* screen. */
private enum class GeneticsPage(val screenId: String, val title: String) {
    DASHBOARD("FOS-GEN-001", "Genetics"),
    PEDIGREE("FOS-GEN-002", "Pedigree explorer"),
    PARENTAGE("FOS-GEN-003", "Record parentage"),
    VERIFY("FOS-GEN-004", "Parentage verification"),
    RELATIONSHIP("FOS-GEN-005", "Relationship explorer"),
    RANKING("FOS-GEN-008", "Candidate ranking"),
    WARNINGS("FOS-GEN-009", "Genetic warnings"),
    REPORT("FOS-GEN-010", "Genetics report"),
}

private data class GeneticsSummary(
    val animalCount: Int,
    val pedigreeLinks: Int,
    val animalsWithParentage: Int,
    val conflictingIds: List<String>,
)

/** Dedicated genetics orchestration: pedigree reads and parentage commands only. */
@Composable
fun GeneticsModuleHost(
    database: FarmOsDatabase,
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
) {
    var page by rememberSaveable(farmId) { mutableStateOf(GeneticsPage.DASHBOARD) }
    val scope = rememberCoroutineScope()
    var summary by remember(farmId) { mutableStateOf<GeneticsSummary?>(null) }
    var error by remember(farmId) { mutableStateOf<String?>(null) }
    val pedigree = remember(farmId) { PedigreeQueries(database, farmId) }

    suspend fun countAnimals(): Int {
        var total = 0
        var offset = 0
        while (true) {
            val batch = database.animals().search(farmId, null, null, null, 500, offset)
            if (batch.isEmpty()) break
            total += batch.size
            offset += batch.size
            if (batch.size < 500) break
        }
        return total
    }

    suspend fun refreshSummary() {
        summary = GeneticsSummary(
            animalCount = countAnimals(),
            pedigreeLinks = database.lifecycle().pedigreeRelationCount(farmId),
            animalsWithParentage = database.lifecycle().animalsWithParentageCount(farmId),
            conflictingIds = database.lifecycle().animalsWithConflictingParentage(farmId),
        )
    }

    LaunchedEffect(farmId) {
        runSuspendCatching { refreshSummary() }.onFailure { error = it.message }
    }

    suspend fun resolveAnimalId(tag: String) = resolveGeneticsAnimal(database, farmId, tag).id

    fun recordParentage(animalTag: String, parentTag: String, relationType: String, done: (String) -> Unit) {
        scope.launch {
            error = null
            runSuspendCatching {
                val animalId = requireNotNull(resolveAnimalId(animalTag)) { "Animal '$animalTag' not found on this farm" }
                val parentId = requireNotNull(resolveAnimalId(parentTag)) { "Parent '$parentTag' not found on this farm" }
                require(relationType in setOf("sire", "dam", "genetic_dam")) { "Relation must be sire, dam or genetic_dam" }
                ops.linkPedigree(
                    LinkPedigree(UUID.randomUUID().toString(), animalId, parentId, relationType),
                    newContext(),
                )
                refreshSummary()
                "Parentage recorded: $animalTag <- $relationType $parentTag."
            }.onSuccess { done(it); enqueueSync() }.onFailure { error = it.message }
        }
    }

    val retainedPages = rememberSaveableStateHolder()
    val current = page
    FarmOperationalPage(
        screenId = current.screenId,
        title = current.title,
        subtitle = "",
        onBack = if (current == GeneticsPage.DASHBOARD) onBack else ({ page = GeneticsPage.DASHBOARD }),
        backLabel = if (current == GeneticsPage.DASHBOARD) "Farm home" else "Genetics",
    ) {
        if (error != null) {
            FarmOperationalSection("Attention", error) {}
        }
        retainedPages.SaveableStateProvider("$farmId:${current.name}") {
        when (current) {
            GeneticsPage.DASHBOARD -> GeneticsDashboard(summary = summary, onOpen = { page = it })
            GeneticsPage.PEDIGREE -> PedigreeExplorer(pedigree = pedigree, database = database, farmId = farmId)
            GeneticsPage.PARENTAGE -> ParentageCapture(onRecord = { a, p, r, done -> recordParentage(a, p, r, done) })
            GeneticsPage.VERIFY -> if (summary == null) Text("Reading parentage records") else VerificationList(conflictingIds = summary!!.conflictingIds)
            GeneticsPage.RELATIONSHIP -> RelationshipExplorer(pedigree = pedigree, database = database, farmId = farmId)
            GeneticsPage.RANKING -> CandidateRanking(pedigree = pedigree, database = database, farmId = farmId)
            GeneticsPage.WARNINGS -> GeneticWarnings(summary = summary, database = database, farmId = farmId)
            GeneticsPage.REPORT -> GeneticsReport(summary = summary)
        }
        }
    }
}

@Composable
private fun GeneticsDashboard(summary: GeneticsSummary?, onOpen: (GeneticsPage) -> Unit) {
    FarmMetricStrip(listOf(
        FarmMetricValue("Animals", summary?.animalCount?.toString() ?: "—"),
        FarmMetricValue("With parentage", summary?.animalsWithParentage?.toString() ?: "—", onClick = { onOpen(GeneticsPage.PEDIGREE) }),
        FarmMetricValue("Pedigree links", summary?.pedigreeLinks?.toString() ?: "—"),
        FarmMetricValue("Conflicts", summary?.conflictingIds?.size?.toString() ?: "—", onClick = { onOpen(GeneticsPage.VERIFY) }),
    ))
    FarmModuleTools(listOf(
        FarmToolGroup("Family records", listOf(GeneticsPage.PEDIGREE, GeneticsPage.PARENTAGE, GeneticsPage.VERIFY).map { page -> FarmToolAction(page.title, { onOpen(page) }) }),
        FarmToolGroup("Compare and review", listOf(GeneticsPage.RELATIONSHIP, GeneticsPage.RANKING, GeneticsPage.WARNINGS, GeneticsPage.REPORT).map { page -> FarmToolAction(page.title, { onOpen(page) }) }),
    ), title = "Genetics tools")
}

@Composable
private fun PedigreeExplorer(pedigree: PedigreeQueries, database: FarmOsDatabase, farmId: String) {
    val scope = rememberCoroutineScope()
    var tag by rememberSaveable(farmId) { mutableStateOf("") }
    var families by remember(farmId) { mutableStateOf(emptyList<FarmPedigreeView>()) }
    var view by rememberSaveable(farmId) { mutableStateOf("Family view") }
    var lines by remember { mutableStateOf(listOf("Enter an animal tag to explore its recorded ancestors.")) }
    var busy by remember { mutableStateOf(false) }
    FarmOperationalSection("Pedigree explorer") {
        OutlinedTextField(value = tag, onValueChange = { tag = it }, label = { Text("Animal tag") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            scope.launch {
                busy = true
                families = emptyList()
                lines = runSuspendCatching {
                    val animal = resolveGeneticsAnimal(database, farmId, tag)
                    val graph = pedigree.graph(listOf(animal.id))
                    suspend fun label(id: String): String =
                        database.animals().get(farmId, id)?.let { "${it.tag}${it.name?.let { n -> " ($n)" } ?: ""}" } ?: id
                    val ids = listOf(animal.id) + graph.parents.keys.filter { it != animal.id }.sorted()
                    families = ids.map { id ->
                        val parents = graph.parents[id]
                        val conflict = id in graph.conflicting
                        FarmPedigreeView(id, label(id), if (conflict) null else parents?.sireId?.let { label(it) }, if (conflict) null else parents?.damId?.let { label(it) }, conflict)
                    }
                    val out = mutableListOf("Ancestors of ${animal.tag}:")
                    if (graph.parents.isEmpty()) out += "No recorded parentage for ${animal.tag}."
                    graph.parents.forEach { (child, parents) ->
                        val bits = listOfNotNull(
                            parents.sireId?.let { "sire ${label(it)}" },
                            parents.damId?.let { "dam ${label(it)}" },
                        )
                        out += "${label(child)} <- ${bits.joinToString(", ")}"
                    }
                    if (graph.conflicting.isNotEmpty()) out += "Conflicting parentage left unknown for: ${graph.conflicting.joinToString(", ")}"
                    out
                }.getOrElse { listOf("Could not load pedigree: ${it.message}") }
                busy = false
            }
        }, enabled = !busy) { Text(if (busy) "Loading…" else "Show pedigree") }
    }
    if (families.isNotEmpty()) {
        FarmSectionTabs(listOf("Family view", "Records"), view, { view = it })
        if (view == "Family view") families.forEach { family -> FarmPedigreeFamily(family, Modifier.testTag("genetics-family:${family.childId}")) }
        else FarmOperationalRows(lines, "No pedigree loaded", null)
    } else FarmOperationalRows(lines, "No pedigree loaded", "Search by the exact animal tag.")
}

/** FOS-GEN-003 — record a parentage link through the governed pedigree command. */
@Composable
private fun ParentageCapture(onRecord: (String, String, String, (String) -> Unit) -> Unit) {
    var animalTag by rememberSaveable { mutableStateOf("") }
    var parentTag by rememberSaveable { mutableStateOf("") }
    var relation by rememberSaveable { mutableStateOf("sire") }
    var receipt by remember { mutableStateOf<String?>(null) }
    FarmOperationalSection("Record parentage") {
        OutlinedTextField(value = animalTag, onValueChange = { animalTag = it }, label = { Text("Animal tag") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = parentTag, onValueChange = { parentTag = it }, label = { Text("Parent tag") }, modifier = Modifier.fillMaxWidth())
        FarmSectionTabs(listOf("Sire", "Dam", "Genetic dam"), when (relation) { "dam" -> "Dam"; "genetic_dam" -> "Genetic dam"; else -> "Sire" }, {
            relation = when (it) { "Dam" -> "dam"; "Genetic dam" -> "genetic_dam"; else -> "sire" }
        })
        Button(onClick = { onRecord(animalTag, parentTag, relation) { receipt = it } }) { Text("Record link") }
        receipt?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun VerificationList(conflictingIds: List<String>) {
    FarmOperationalSection(
        "Parentage verification",
        "Animals with more than one recorded sire or dam. Their parentage is left unknown rather than guessed.",
    ) {
        if (conflictingIds.isEmpty()) {
            Text("No conflicting parentage on this device.", style = MaterialTheme.typography.bodyMedium)
        } else {
            conflictingIds.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

private fun ancestorsOf(parents: Map<String, PedigreeParents>): Set<String> {
    val seen = mutableSetOf<String>()
    val queue = ArrayDeque<String>()
    parents.values.forEach { it.sireId?.let(queue::add); it.damId?.let(queue::add) }
    while (queue.isNotEmpty()) {
        val id = queue.removeFirst()
        if (seen.add(id)) {
            parents[id]?.let { it.sireId?.let(queue::add); it.damId?.let(queue::add) }
        }
    }
    return seen
}

@Composable
private fun RelationshipExplorer(pedigree: PedigreeQueries, database: FarmOsDatabase, farmId: String) {
    val scope = rememberCoroutineScope()
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf(listOf("Enter two animal tags to find their common recorded ancestors.")) }
    var busy by remember { mutableStateOf(false) }
    suspend fun resolve(tag: String) = resolveGeneticsAnimal(database, farmId, tag)
    FarmOperationalSection("Relationship explorer") {
        OutlinedTextField(value = first, onValueChange = { first = it }, label = { Text("First animal tag") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = second, onValueChange = { second = it }, label = { Text("Second animal tag") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            scope.launch {
                busy = true
                lines = runSuspendCatching {
                    val a = requireNotNull(resolve(first)) { "Animal '${first.trim()}' not found" }
                    val b = requireNotNull(resolve(second)) { "Animal '${second.trim()}' not found" }
                    require(a.id != b.id) { "Select two different animals" }
                    val common = (ancestorsOf(pedigree.graph(listOf(a.id)).parents) + a.id) intersect
                        (ancestorsOf(pedigree.graph(listOf(b.id)).parents) + b.id)
                    if (common.isEmpty()) listOf("${a.tag} and ${b.tag} share no recorded common ancestors.")
                    else {
                        val out = mutableListOf("Common recorded ancestors of ${a.tag} and ${b.tag}:")
                        common.forEach { id ->
                            val row = database.animals().get(farmId, id)
                            out += row?.let { "${it.tag}${it.name?.let { n -> " ($n)" } ?: ""}" } ?: id
                        }
                        out
                    }
                }.getOrElse { listOf("Could not compare: ${it.message}") }
                busy = false
            }
        }, enabled = !busy) { Text(if (busy) "Working…" else "Find relationship") }
    }
    FarmOperationalRows(lines, "No comparison yet", null)
}

/** Exact tags are resolved across the complete farm result, never a presentation page. */
private suspend fun resolveGeneticsAnimal(database: FarmOsDatabase, farmId: String, tag: String): AnimalEntity {
    val exact = tag.trim()
    require(exact.isNotEmpty()) { "Enter an animal tag" }
    val matches = database.animals().search(farmId, null, null, escapeLike(exact), -1, 0)
        .filter { it.tag.equals(exact, ignoreCase = true) }
    require(matches.size == 1) {
        if (matches.isEmpty()) "Animal '" + exact + "' not found on this farm" else "Animal tag is ambiguous on this farm"
    }
    return matches.single()
}

@Composable
private fun CandidateRanking(pedigree: PedigreeQueries, database: FarmOsDatabase, farmId: String) {
    val scope = rememberCoroutineScope()
    var damTag by rememberSaveable(farmId) { mutableStateOf("") }
    var bars by remember(farmId) { mutableStateOf(emptyList<FarmBarValue>()) }
    var lines by remember(farmId) { mutableStateOf(listOf("Enter a dam tag to compare active sires by recorded offspring inbreeding coefficient.")) }
    var busy by remember(farmId) { mutableStateOf(false) }
    FarmOperationalSection("Candidate ranking", "Recorded COI is a lower bound. Incomplete parentage does not establish that animals are unrelated.") {
        OutlinedTextField(value = damTag, onValueChange = { damTag = it }, label = { Text("Dam tag") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            scope.launch {
                busy = true
                lines = runSuspendCatching {
                    bars = emptyList()
                    val dam = resolveGeneticsAnimal(database, farmId, damTag)
                    require(dam.sex.equals("FEMALE", ignoreCase = true) && dam.status == "active") { "Select an active female" }
                    val sires = database.animals().search(farmId, dam.speciesCode, null, null, -1, 0)
                        .filter { it.status == "active" && it.sex.equals("MALE", ignoreCase = true) }
                    if (sires.isEmpty()) return@runSuspendCatching listOf("No active sires of " + dam.speciesCode + " on this farm.")
                    val ranked = sires.map { sire -> sire to pedigree.mating(sire.id, dam.id) }
                        .sortedWith(compareBy({ it.second.inbreeding.coefficient }, { it.first.tag }, { it.first.id }))
                    bars = ranked.take(20).map { (sire, analysis) ->
                        val result = analysis.inbreeding
                        FarmBarValue(sire.tag + (sire.name?.let { " · $it" } ?: ""), result.coefficient.takeIf { result.generationsKnown > 0 },
                            "${"%.4f".format(java.util.Locale.ROOT, result.coefficient)} recorded lower bound", "${result.generationsKnown} complete generations · ${analysis.conflictingParentage.size} parentage conflicts", "genetics-candidate:${sire.id}")
                    }
                    val out = mutableListOf("Sire candidates for " + dam.tag + " (recorded offspring COI, lowest first):")
                    ranked.take(20).forEach { (sire, analysis) ->
                        val label = sire.tag + (sire.name?.let { " (" + it + ")" } ?: "")
                        val result = analysis.inbreeding
                        out += label + " — recorded COI " + "%.4f".format(result.coefficient) +
                            " over " + result.generationsKnown + " complete generations"
                        if (analysis.conflictingParentage.isNotEmpty()) {
                            out += "Parentage conflicts left unknown: " + analysis.conflictingParentage.size + "; review before selecting this candidate."
                        }
                    }
                    if (ranked.size > 20) out += "Showing the first 20 of " + ranked.size + " candidates assessed."
                    out
                }.getOrElse { listOf("Could not rank: " + it.message) }
                busy = false
            }
        }, enabled = !busy) { Text(if (busy) "Ranking…" else "Rank sires") }
    }
    if (bars.isNotEmpty()) FarmMetricBars("Recorded offspring inbreeding", bars, "coefficient", caption = "Incomplete pedigrees do not establish that animals are unrelated. Showing up to 20 candidates after assessing every eligible sire.")
    FarmOperationalRows(lines, "No ranking yet", null)
}

@Composable
private fun GeneticWarnings(summary: GeneticsSummary?, database: FarmOsDatabase, farmId: String) {
    var lines by remember(summary) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(summary) {
        if (summary == null) return@LaunchedEffect
        lines = runSuspendCatching {
            val out = mutableListOf<String>()
            if (summary.conflictingIds.isEmpty()) out += "No conflicting parentage records."
            else {
                out += "Conflicting parentage (${summary.conflictingIds.size}):"
                summary.conflictingIds.take(20).forEach { out += "• $it" }
            }
            var withoutParentage = 0
            var offset = 0
            while (true) {
                val batch = database.animals().search(farmId, null, null, null, 500, offset)
                if (batch.isEmpty()) break
                batch.forEach { if (database.lifecycle().pedigreeParents(farmId, it.id).isEmpty()) withoutParentage++ }
                offset += batch.size
                if (batch.size < 500) break
            }
            out += "Animals with no recorded parentage: $withoutParentage of ${summary.animalCount}."
            out
        }.getOrElse { listOf("Could not load warnings: ${it.message}") }
    }
    FarmOperationalSection("Genetic warnings", "Advisory only. No culling, mating or treatment is decided here.") {
        (lines ?: listOf("Loading…")).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun GeneticsReport(summary: GeneticsSummary?) {
    val rows = summary?.let {
        listOf(
            "Animals on farm: ${it.animalCount}",
            "Pedigree links recorded: ${it.pedigreeLinks}",
            "Animals with parentage: ${it.animalsWithParentage}",
            "Conflicting parentage: ${it.conflictingIds.size}",
            "Coverage: " + if (it.animalCount == 0) "no animals" else "${(100 * it.animalsWithParentage) / it.animalCount}% of animals have recorded parentage",
        )
    } ?: listOf("Loading…")
    FarmOperationalRows(rows, "No genetics data", "Record parentage to build this report.")
}
