package com.farmos.feature.ops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.AnimalFarmWarningSurface
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmVisualClass
import java.time.LocalDate

/** A group as registered; [recordedHeadCount] is the stored group head count, not a physical census. */
data class GroupView(val id: String, val name: String, val speciesCode: String, val recordedHeadCount: Int)

data class GroupCensusView(val id: String, val headCount: Int, val epochDay: Long)

data class GroupGrazingView(val id: String, val paddockLabel: String, val headCount: Int, val enteredEpochDay: Long, val exitedEpochDay: Long?)

data class GroupFeedView(val id: String, val itemLabel: String, val quantity: String, val epochDay: Long)

/** Every record for one group, read exhaustively from local farm-scoped tables. Nothing here writes. */
data class GroupRecords(
    val census: List<GroupCensusView> = emptyList(),
    val grazing: List<GroupGrazingView> = emptyList(),
    val feed: List<GroupFeedView> = emptyList(),
)

enum class GroupRecordPage(val label: String) {
    DETAIL("Group detail"),
    CENSUS("Group census"),
    TIMELINE("Group timeline"),
}

private sealed interface GroupLoadState {
    data object Loading : GroupLoadState
    data class Failed(val message: String) : GroupLoadState
    data class Loaded(val records: GroupRecords) : GroupLoadState
}

private fun day(epochDay: Long) = LocalDate.ofEpochDay(epochDay).toString()

@Composable
private fun GroupRow(primary: String, secondary: String, tag: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(tag), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(primary, color = AnimalFarmTheme.colors.mutedInk)
        Text(secondary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Group record navigation. [home] renders the groups home (FOS-GROUP-001) and places the supplied
 * record actions; each opens a read-only page for a selected group, loaded by [loadGroup].
 */
@Composable
fun GroupRecordNavigator(
    groups: List<GroupView>,
    loadGroup: suspend (String) -> GroupRecords,
    home: @Composable (recordActions: @Composable () -> Unit) -> Unit,
) {
    var page by rememberSaveable { mutableStateOf<GroupRecordPage?>(null) }
    val current = page
    if (current == null) {
        home {
            FarmOperationalSection("Records") {
                GroupRecordPage.entries.forEach { target ->
                    TextButton(onClick = { page = target }, modifier = Modifier.fillMaxWidth()) { Text("Open ${target.label}") }
                }
            }
        }
    } else {
        GroupRecordPageScreen(current, groups, loadGroup) { page = null }
    }
}

@Composable
private fun GroupRecordPageScreen(page: GroupRecordPage, groups: List<GroupView>, loadGroup: suspend (String) -> GroupRecords, onBack: () -> Unit) {
    val (screenId, title, subtitle) = when (page) {
        GroupRecordPage.DETAIL -> Triple("FOS-GROUP-002", "Group detail", "One group as recorded with its latest census and grazing.")
        GroupRecordPage.CENSUS -> Triple("FOS-GROUP-006", "Group census", "Every census recorded for one group, newest first.")
        GroupRecordPage.TIMELINE -> Triple("FOS-GROUP-009", "Group timeline", "Every census, grazing and feed record for one group, newest first.")
    }
    var selectedId by rememberSaveable { mutableStateOf(groups.firstOrNull()?.id) }
    var state by remember(selectedId) { mutableStateOf<GroupLoadState>(GroupLoadState.Loading) }
    LaunchedEffect(selectedId) {
        val id = selectedId ?: return@LaunchedEffect
        state = runCatching { loadGroup(id) }.fold({ GroupLoadState.Loaded(it) }, { GroupLoadState.Failed(it.message ?: "Records could not be loaded") })
    }
    FarmOperationalPage(screenId, title, subtitle, FarmVisualClass.I3, onBack) {
        if (groups.isEmpty()) {
            AnimalFarmEmptyState("No groups on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection("Groups") {
            groups.forEach { group ->
                val selected = group.id == selectedId
                TextButton(
                    onClick = { selectedId = group.id },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                        .semantics { this.selected = selected }
                        .testTag("group-option:${group.id}"),
                ) { Text("${group.name} · ${group.speciesCode}", fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
            }
        }
        val group = groups.firstOrNull { it.id == selectedId } ?: return@FarmOperationalPage
        when (val current = state) {
            GroupLoadState.Loading -> Text("Loading records")
            is GroupLoadState.Failed -> AnimalFarmWarningSurface { Text(current.message) }
            is GroupLoadState.Loaded -> when (page) {
                GroupRecordPage.DETAIL -> GroupDetailContent(group, current.records)
                GroupRecordPage.CENSUS -> GroupCensusContent(current.records)
                GroupRecordPage.TIMELINE -> GroupTimelineContent(current.records)
            }
        }
    }
}

/** FOS-GROUP-002 */
@Composable
private fun GroupDetailContent(group: GroupView, records: GroupRecords) {
    FarmOperationalSection(group.name) {
        GroupRow("Species", group.speciesCode, "group-species")
        GroupRow("Recorded head count", group.recordedHeadCount.toString(), "group-head-count")
        GroupRow(
            "Latest census",
            records.census.firstOrNull()?.let { "${it.headCount} head · ${day(it.epochDay)}" } ?: "No census recorded",
            "group-latest-census",
        )
        val open = records.grazing.filter { it.exitedEpochDay == null }
        GroupRow(
            "Grazing now",
            if (open.isEmpty()) "No open grazing recorded" else open.joinToString(" · ") { "${it.paddockLabel} since ${day(it.enteredEpochDay)}" },
            "group-grazing-now",
        )
    }
}

/** FOS-GROUP-006 */
@Composable
private fun GroupCensusContent(records: GroupRecords) {
    if (records.census.isEmpty()) {
        AnimalFarmEmptyState("No census recorded for this group.")
        return
    }
    FarmOperationalSection("Census records · ${records.census.size}") {
        records.census.forEachIndexed { index, census ->
            if (index > 0) HorizontalDivider()
            GroupRow(day(census.epochDay), "${census.headCount} head", "group-census:${census.id}")
        }
    }
}

/** FOS-GROUP-009 */
@Composable
private fun GroupTimelineContent(records: GroupRecords) {
    val events = buildList {
        records.census.forEach { add(Triple(it.epochDay, "census:${it.id}", "${day(it.epochDay)} · Census" to "${it.headCount} head")) }
        records.grazing.forEach {
            add(Triple(it.enteredEpochDay, "enter:${it.id}", "${day(it.enteredEpochDay)} · Entered paddock" to "${it.paddockLabel} · ${it.headCount} head at entry"))
            it.exitedEpochDay?.let { exited -> add(Triple(exited, "exit:${it.id}", "${day(exited)} · Left paddock" to it.paddockLabel)) }
        }
        records.feed.forEach { add(Triple(it.epochDay, "feed:${it.id}", "${day(it.epochDay)} · Feed issued" to "${it.itemLabel} · ${it.quantity}")) }
    }.sortedWith(compareByDescending<Triple<Long, String, Pair<String, String>>> { it.first }.thenBy { it.second })
    if (events.isEmpty()) {
        AnimalFarmEmptyState("No records for this group on this device.")
        return
    }
    FarmOperationalSection("Events · ${events.size}") {
        events.forEachIndexed { index, (_, key, text) ->
            if (index > 0) HorizontalDivider()
            GroupRow(text.first, text.second, "group-event:$key")
        }
    }
}
