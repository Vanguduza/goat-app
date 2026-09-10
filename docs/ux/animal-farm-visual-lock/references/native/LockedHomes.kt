package com.farmos.design.patterns

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import com.farmos.design.R
import com.farmos.design.components.*
import com.farmos.design.theme.*
import com.farmos.model.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
private fun HomeChrome(date: LocalDate?, onBack: () -> Unit, modifier: Modifier,
    footer: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(FosTheme.colors.canvas), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = HomeTokens.homeMax).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = FosSpace.small), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack, Modifier.size(FosTheme.minTouch)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), tint = FosTheme.colors.primary)
                }
                Column(Modifier.weight(1f).padding(vertical = FosSpace.small)) {
                    Text(stringResource(R.string.farm_os), style = FosText.bodyStrong, color = FosTheme.colors.text)
                    date?.let { Text(it.format(DateTimeFormatter.ofPattern("EEE, dd MMM", Locale.getDefault())),
                        style = FosText.label, color = FosTheme.colors.secondary) }
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(FosSpace.page),
                verticalArrangement = Arrangement.spacedBy(FosSpace.section), content = content)
            footer()
        }
    }
}

@Composable
private fun HomeHeading(eyebrow: String, title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FosSpace.xs)) {
            FosLabel(eyebrow)
            Text(title, Modifier.semantics { heading() }, style = FosText.display, color = FosTheme.colors.text)
        }
        trailing?.invoke()
    }
}

@Composable
internal fun HomeAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = HomeTokens.palette(FosTheme.mode)
    Button(onClick, modifier.heightIn(min = FosTheme.minTouch), enabled = enabled,
        shape = RoundedCornerShape(HomeTokens.actionRadius),
        colors = ButtonDefaults.buttonColors(containerColor = colors.hero, contentColor = colors.onHero)) {
        Text(label, style = FosText.bodyStrong, textAlign = TextAlign.Center)
    }
}

@Composable
private fun HomeNavigation(management: Boolean, onHome: () -> Unit, onWork: () -> Unit, onRecord: () -> Unit,
    onLast: () -> Unit, onSettings: () -> Unit) {
    val labels = listOf(R.string.home, R.string.work, R.string.record, if (management) R.string.modules else R.string.guides)
    val icons = listOf(Icons.Rounded.Home, Icons.AutoMirrored.Rounded.Assignment, Icons.Rounded.AddCircleOutline,
        if (management) Icons.Rounded.GridView else Icons.AutoMirrored.Rounded.MenuBook)
    val actions = listOf(onHome, onWork, onRecord, onLast)
    Surface(color = FosTheme.colors.card) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = FosSpace.small)) {
            val expanded = maxWidth < FosSpace.formMax && LocalDensity.current.fontScale >= 1.3f
            Column {
                FosRule()
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        labels.indices.toList().chunked(if (expanded) 2 else 4).forEach { entries ->
                            Row(Modifier.fillMaxWidth()) {
                                entries.forEach { i ->
                                    Column(Modifier.weight(1f).heightIn(min = FosTheme.minTouch)
                                        .clickable(role = Role.Tab, onClick = actions[i])
                                        .semantics { selected = i == 0 }.padding(FosSpace.small),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(FosSpace.xs, Alignment.CenterVertically)) {
                                        Icon(icons[i], null, Modifier.size(FosSpace.icon), tint = FosTheme.colors.primary)
                                        Text(stringResource(labels[i]), Modifier.testTag("home-nav-label-$i"),
                                            style = FosText.label, textAlign = TextAlign.Center, color = FosTheme.colors.primary)
                                    }
                                }
                            }
                        }
                    }
                    FosSettingsButton(onSettings)
                }
            }
        }
    }
}

@Composable
private fun HomeSurface(modifier: Modifier = Modifier, color: Color = FosTheme.colors.card,
    contentColor: Color = FosTheme.colors.text, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(HomeTokens.cardRadius), color = color, contentColor = contentColor) {
        Column(Modifier.padding(FosSpace.section), verticalArrangement = Arrangement.spacedBy(FosSpace.medium), content = content)
    }
}

@Composable
private fun HomeModule(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick, modifier.heightIn(min = HomeTokens.compactPortrait), shape = RoundedCornerShape(HomeTokens.actionRadius),
        color = FosTheme.colors.card, contentColor = FosTheme.colors.text) {
        Column(Modifier.padding(FosSpace.medium), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(FosSpace.small, Alignment.CenterVertically)) {
            Icon(icon, null, Modifier.size(FosSpace.icon), tint = FosTheme.colors.primary)
            Text(label, style = FosText.label, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ModuleGrid(onAnimals: () -> Unit, onResources: () -> Unit, onUnavailable: (Int) -> Unit) {
    val labels = listOf(R.string.animals, R.string.team, R.string.breeding, R.string.finance, R.string.resources, R.string.reports)
    val icons = listOf(Icons.Rounded.Pets, Icons.Rounded.Groups, Icons.Rounded.FavoriteBorder,
        Icons.Rounded.AccountBalanceWallet, Icons.Rounded.Inventory2, Icons.Rounded.Insights)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < FosSpace.formMax && LocalDensity.current.fontScale >= 1.3f) 2 else 3
        Column(verticalArrangement = Arrangement.spacedBy(FosSpace.small)) {
            labels.indices.toList().chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(FosSpace.small)) {
                    row.forEach { i -> HomeModule(stringResource(labels[i]), icons[i], {
                        when (i) { 0 -> onAnimals(); 4 -> onResources(); else -> onUnavailable(labels[i]) }
                    }, Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** A / Control room, scoped Cockpit adaptation. No aggregate is a fabricated farm balance. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LockedCommandCenter(boards: List<TodayBoardModel>, state: LoadState, onRetry: () -> Unit,
    onQueue: (Boolean) -> Unit, onSpecies: (Species) -> Unit, onResources: () -> Unit, onSettings: () -> Unit,
    onBack: () -> Unit, modifier: Modifier, onProfile: (String) -> Unit, personalPhoto: ImageBitmap?, onRecord: () -> Unit) {
    var sheet by rememberSaveable { mutableIntStateOf(0) }
    val colors = HomeTokens.palette(FosTheme.mode)
    val review = boards.workItems(reviewOnly = true)
    val effective = if (state == LoadState.Idle && boards.isEmpty()) LoadState.Empty else state
    HomeChrome(boards.firstOrNull()?.date, onBack, modifier, {
        HomeNavigation(true, {}, { onQueue(false) }, onRecord, { sheet = R.string.modules }, onSettings)
    }) {
        HomeHeading(stringResource(R.string.command_center), stringResource(R.string.farm_pulse))
        if (effective != LoadState.Idle) FosLoadableState(effective, "", {}, onRetry, genericContent = true)
        else {
            HomeSurface(color = colors.hero, contentColor = colors.onHero) {
                Text(stringResource(R.string.needs_review), style = FosText.label)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val stacked = LocalDensity.current.fontScale >= 1.3f && maxWidth < FosSpace.formMax
                    val title: @Composable () -> Unit = {
                        Text(review.firstOrNull()?.task?.homeTitle ?: stringResource(R.string.no_reviews), style = FosText.display)
                        Row(horizontalArrangement = Arrangement.spacedBy(FosSpace.small)) {
                            Text(review.size.toString(), Modifier.testTag("review-count"), style = FosText.numeric)
                            Text(stringResource(R.string.to_review), style = FosText.body)
                        }
                    }
                    if (stacked) Column(verticalArrangement = Arrangement.spacedBy(FosSpace.medium)) {
                        title()
                        review.firstOrNull()?.let { AnimalPortrait(it.species, Modifier.size(HomeTokens.compactPortrait),
                            if (it.task.individualKey != null) personalPhoto else null) }
                    } else Row(horizontalArrangement = Arrangement.spacedBy(FosSpace.medium), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FosSpace.small)) { title() }
                        review.firstOrNull()?.let { AnimalPortrait(it.species, Modifier.size(HomeTokens.compactPortrait),
                            if (it.task.individualKey != null) personalPhoto else null) }
                    }
                }
                TextButton({ onQueue(true) }, Modifier.heightIn(min = FosTheme.minTouch),
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.onHero)) {
                    Text(stringResource(R.string.open_review_queue), style = FosText.bodyStrong)
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.padding(start = FosSpace.small))
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stacked = maxWidth < FosSpace.formMax && LocalDensity.current.fontScale >= 1.3f
                val work: @Composable () -> Unit = {
                    HomeSurface(color = colors.accent, contentColor = colors.onAccent) {
                        Text(stringResource(R.string.work), style = FosText.body)
                        Text(boards.sumOf { b -> b.tasks.count { it.dueOn <= b.date } }.toString(),
                            Modifier.testTag("due-count"), style = FosText.display)
                        Text(stringResource(R.string.due_today_label), style = FosText.label)
                        Text(stringResource(R.string.scheduled_tasks, boards.workItems().size), style = FosText.label)
                        HomeAction(stringResource(R.string.open_work_queue), { onQueue(false) })
                    }
                }
                val resources: @Composable () -> Unit = {
                    HomeSurface {
                        Icon(Icons.Rounded.Inventory2, null, tint = FosTheme.colors.primary)
                        Text(stringResource(R.string.resources), style = FosText.bodyStrong)
                        Text(stringResource(R.string.nest_requirement), style = FosText.titleLg)
                        FosAction(stringResource(R.string.open_resources), onResources)
                    }
                }
                if (stacked) Column(verticalArrangement = Arrangement.spacedBy(FosSpace.medium)) { work(); resources() }
                else Row(horizontalArrangement = Arrangement.spacedBy(FosSpace.medium)) {
                    Column(Modifier.weight(1f)) { work() }; Column(Modifier.weight(1f)) { resources() }
                }
            }
            Text(stringResource(R.string.farm_modules), Modifier.semantics { heading() }, style = FosText.titleLg, color = FosTheme.colors.text)
            ModuleGrid({ sheet = R.string.animal_families }, onResources, { sheet = it })
        }
    }
    if (sheet != 0) ModalBottomSheet(onDismissRequest = { sheet = 0 }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(FosSpace.page),
            verticalArrangement = Arrangement.spacedBy(FosSpace.page)) {
            Text(stringResource(sheet), Modifier.semantics { heading() }, style = FosText.titleLg)
            when (sheet) {
                R.string.animal_families -> FamilyChoices(boards.map { it.species }.toSet(), { selected ->
                    sheet = 0; onSpecies(selected)
                }, { onProfile("nala"); sheet = 0 })
                R.string.modules -> ModuleGrid({ sheet = R.string.animal_families }, { sheet = 0; onResources() }, { sheet = it })
                else -> FosBody(stringResource(R.string.module_not_connected))
            }
            FosAction(stringResource(R.string.close_details), { sheet = 0 })
        }
    }
}

@Composable
private fun FamilyChoices(available: Set<Species>, onSpecies: (Species) -> Unit, onProfile: () -> Unit) {
    listOf(Species.Goat, Species.Rabbit, Species.Sheep, Species.Cattle, Species.Poultry).forEach { species ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FosSpace.page)) {
            AnimalPortrait(species, Modifier.size(HomeTokens.familyPortrait))
            Column(Modifier.weight(1f)) {
                FosAction(familyName(species), { onSpecies(species) }, enabled = species in available)
                if (species !in available) FosLabel(stringResource(R.string.module_not_connected))
                if (species == Species.Goat) FosAction(stringResource(R.string.open_nala_profile), onProfile)
            }
        }
    }
}

/** D work-stage shell, with C's task-first review carousel. */
@Composable
internal fun LockedWorkerBoard(boards: List<TodayBoardModel>, state: LoadState, query: String, onQuery: (String) -> Unit,
    onArea: (WorkArea) -> Unit, onTask: (String) -> Unit, onWeight: () -> Unit, onRetry: () -> Unit,
    onSettings: () -> Unit, onBack: () -> Unit, modifier: Modifier, onProfile: (String) -> Unit,
    personalPhoto: ImageBitmap?, onAllWork: () -> Unit) {
    var stage by rememberSaveable { mutableStateOf(WorkStage.ToDo) }
    var index by rememberSaveable(stage, query) { mutableIntStateOf(0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    val items = boards.stageItems(stage, query)
    val page = boundedPage(index, items.size)
    val date = boards.firstOrNull()?.date
    val colors = HomeTokens.palette(FosTheme.mode)
    val effective = if (state == LoadState.Idle && boards.isEmpty()) LoadState.Empty else state
    HomeChrome(date, onBack, modifier, {
        HomeNavigation(false, {}, onAllWork, { onArea(WorkArea.Record) }, { onArea(WorkArea.Guides) }, onSettings)
    }) {
        HomeHeading(stringResource(R.string.work_board), stringResource(R.string.my_work)) {
            IconButton({ searching = !searching }, Modifier.size(FosTheme.minTouch)) {
                Icon(Icons.Rounded.Search, stringResource(R.string.search_tasks), tint = FosTheme.colors.primary)
            }
        }
        if (effective != LoadState.Idle) FosLoadableState(effective, query, onQuery, onRetry, genericContent = true)
        else {
            date?.let {
                HomeSurface(color = colors.hero, contentColor = colors.onHero) {
                    Text(it.format(DateTimeFormatter.ofPattern("dd MMMM", Locale.getDefault())), style = FosText.display)
                    Text(stringResource(R.string.day_work, it.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())),
                        boards.sumOf { b -> b.tasks.count { task -> task.dueOn <= b.date } }), style = FosText.body)
                }
            }
            if (searching || query.isNotEmpty()) FosSearch(query, onQuery)
            StageTabs(stage, boards, query, { stage = it; index = 0 })
            Text(stringResource(R.string.review_decide), Modifier.semantics { heading() }, style = FosText.titleLg, color = FosTheme.colors.text)
            if (items.isEmpty()) {
                Column(Modifier.fillMaxWidth().heightIn(min = HomeTokens.emptyStage), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(FosSpace.medium, Alignment.CenterVertically)) {
                    Icon(Icons.Rounded.TaskAlt, null, tint = FosTheme.colors.secondary)
                    FosBody(stringResource(if (query.isEmpty()) R.string.no_stage_tasks else R.string.no_matches))
                    if (query.isNotEmpty()) FosAction(stringResource(R.string.clear_search), { onQuery("") })
                }
            } else {
                val item = items[page]
                HomeSurface(color = colors.accent, contentColor = colors.onAccent) {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FosSpace.page)) {
                        Text(item.task.category, style = FosText.label)
                        Text(item.task.dueOn.format(DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())), style = FosText.numeric)
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        AnimalPortrait(item.species, Modifier.size(HomeTokens.portrait),
                            if (item.task.individualKey != null) personalPhoto else null)
                    }
                    val subject = item.task.subject + " · " + familyName(item.species)
                    if (item.task.individualKey != null) {
                        val description = stringResource(R.string.open_nala_profile)
                        TextButton({ item.task.individualKey?.let(onProfile) }, Modifier.heightIn(min = FosTheme.minTouch)
                            .semantics { contentDescription = description },
                            colors = ButtonDefaults.textButtonColors(contentColor = colors.onAccent)) {
                            Text(subject, style = FosText.body); Icon(Icons.Rounded.ChevronRight, null)
                        }
                    } else Text(subject, style = FosText.body)
                    Text(item.task.homeTitle, style = FosText.display)
                    Text(item.task.homeSummary, style = FosText.body)
                    HomeAction(stringResource(R.string.open_task), { onTask(item.task.key) }, Modifier.fillMaxWidth())
                }
                Row(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ index = page - 1 }, Modifier.size(FosTheme.minTouch), enabled = page > 0) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.previous_task))
                    }
                    Text(stringResource(R.string.carousel_position, page + 1, items.size), style = FosText.numeric, color = FosTheme.colors.text)
                    IconButton({ index = page + 1 }, Modifier.size(FosTheme.minTouch), enabled = page < items.lastIndex) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, stringResource(R.string.next_task))
                    }
                }
            }
            FosLabel(stringResource(R.string.work_scope, boards.map { it.species }.distinct().size, boards.workItems().size))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = if (maxWidth < FosSpace.formMax && LocalDensity.current.fontScale >= 1.3f) 1 else 3
                val labels = listOf(R.string.weight, R.string.resources, R.string.guides)
                val icons = listOf(Icons.Rounded.MonitorWeight, Icons.Rounded.Inventory2, Icons.AutoMirrored.Rounded.MenuBook)
                val actions = listOf(onWeight, { onArea(WorkArea.Resources) }, { onArea(WorkArea.Guides) })
                Column(verticalArrangement = Arrangement.spacedBy(FosSpace.small)) {
                    labels.indices.toList().chunked(columns).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(FosSpace.small)) {
                        row.forEach { i -> HomeModule(stringResource(labels[i]), icons[i], actions[i], Modifier.weight(1f)) }
                    } }
                }
            }
        }
    }
}

@Composable
private fun StageTabs(selectedStage: WorkStage, boards: List<TodayBoardModel>, query: String, onSelect: (WorkStage) -> Unit) {
    val labels = listOf(R.string.to_do, R.string.active, R.string.review, R.string.later)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < FosSpace.formMax && LocalDensity.current.fontScale >= 1.3f) 2 else 4
        Column {
            WorkStage.entries.chunked(columns).forEach { entries -> Row(Modifier.fillMaxWidth()) {
                entries.forEach { stage ->
                    TextButton({ onSelect(stage) }, Modifier.weight(1f).heightIn(min = FosTheme.minTouch)
                        .testTag("stage-${stage.name}").semantics { selected = stage == selectedStage },
                        shape = RoundedCornerShape(FosSpace.small), contentPadding = PaddingValues(FosSpace.xs),
                        colors = ButtonDefaults.textButtonColors(containerColor = if (stage == selectedStage) FosTheme.colors.primaryContainer else Color.Transparent)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FosSpace.xs)) {
                            Text(stringResource(labels[stage.ordinal]), style = FosText.label, textAlign = TextAlign.Center, color = FosTheme.colors.text)
                            Text(boards.stageItems(stage, query).size.toString(), style = FosText.numeric, color = FosTheme.colors.text)
                        }
                    }
                }
            } }
        }
    }
}
