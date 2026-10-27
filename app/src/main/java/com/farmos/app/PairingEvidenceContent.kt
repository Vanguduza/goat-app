package com.farmos.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.design.*
import com.farmos.data.herd.BreedingEvidenceQueries
import com.farmos.data.herd.PairingComparison
import com.farmos.domain.ops.*
import kotlinx.coroutines.launch

/** Farm-selected objective and evidence sufficiency, not an arbitrary composite genetic score. */
@Composable
internal fun PairingEvidenceContent(database:FarmOsDatabase,farmId:String,initialId:String?=null,speciesCode:String?=null,
    onLineage:(String)->Unit) {
    var damId by rememberSaveable(farmId,initialId) { mutableStateOf<String?>(null) }
    var damLabel by rememberSaveable(farmId,initialId) { mutableStateOf("") }
    var sourceSpecies by rememberSaveable(farmId,initialId) { mutableStateOf(speciesCode) }
    var initialNotice by remember { mutableStateOf<String?>(null) }
    var objective by rememberSaveable { mutableStateOf(PairingObjective.LOWER_RECORDED_INBREEDING) }
    var generations by rememberSaveable { mutableStateOf("2") }
    var ceiling by rememberSaveable { mutableStateOf("") }
    var fromAge by rememberSaveable { mutableStateOf("") }
    var toAge by rememberSaveable { mutableStateOf("") }
    var tolerance by rememberSaveable { mutableStateOf("3") }
    var minimum by rememberSaveable { mutableStateOf("3") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember(farmId) { mutableStateOf<PairingComparison?>(null) }
    var selected by remember { mutableStateOf<PairingAssessment?>(null) }
    var limit by remember(damId,objective) { mutableIntStateOf(12) }
    val scope=rememberCoroutineScope()
    val queries=remember(database,farmId){BreedingEvidenceQueries(database,farmId)}
    LaunchedEffect(farmId,initialId) {
        if(initialId!=null) runSuspendCatching {
            val animal=requireNotNull(database.animals().get(farmId,initialId)) { "The selected animal is not on this farm" }
            sourceSpecies=animal.speciesCode
            if(animal.sex.uppercase() in setOf("FEMALE","F")) {damId=animal.id;damLabel=listOfNotNull(animal.tag,animal.name).joinToString(" · ")}
            else initialNotice="Choose a dam to assess ${animal.tag} alongside the other active sires of this species."
        }.onFailure {error=it.message}
    }
    val search=remember(database,farmId,sourceSpecies){animalSelectorSearch(database,farmId,sourceSpecies,"FEMALE")}
    val criteria=runCatching {
        PairingCriteria(objective,generations.toInt(),ceiling.takeIf{it.isNotBlank()}?.toDouble()?.div(100.0),
            if(objective==PairingObjective.OFFSPRING_GROWTH) fromAge.toInt() else null,
            if(objective==PairingObjective.OFFSPRING_GROWTH) toAge.toInt() else null,
            tolerance.toInt(),minimum.toInt())
    }.getOrNull()
    FarmSearchChoiceField("Dam / female",damId?.let{FarmSelectorOption(it,damLabel)},search,
        {damId=it?.id;damLabel=it?.label.orEmpty();result=null},"pairing-dam",enabled=!busy)
    initialNotice?.let {Text(it,style=AnimalFarmHtmlType.caption)}
    FarmChoicePicker("Comparison objective",listOf(PairingObjective.LOWER_RECORDED_INBREEDING to "Lower recorded offspring inbreeding",
        PairingObjective.OFFSPRING_GROWTH to "Recorded offspring growth"),objective,{objective=it},"pairing-objective",enabled=!busy)
    FarmOutlinedTextField(generations,{generations=it},label={Text("Minimum complete pedigree generations")},enabled=!busy,singleLine=true,
        modifier=Modifier.fillMaxWidth().testTag("pairing-min-generations"))
    FarmOutlinedTextField(ceiling,{ceiling=it},label={Text(if(objective==PairingObjective.OFFSPRING_GROWTH) "Your maximum recorded inbreeding (%)" else "Maximum recorded inbreeding (%) · optional")},
        enabled=!busy,singleLine=true,modifier=Modifier.fillMaxWidth().testTag("pairing-coi-ceiling"))
    if(objective==PairingObjective.OFFSPRING_GROWTH) {
        Text("Set age windows appropriate to this species and farm. This compares measured offspring growth, not genetic breeding values.",style=AnimalFarmHtmlType.caption)
        FarmOutlinedTextField(fromAge,{fromAge=it},label={Text("Starting offspring age (days)")},enabled=!busy,singleLine=true,modifier=Modifier.fillMaxWidth().testTag("pairing-from-age"))
        FarmOutlinedTextField(toAge,{toAge=it},label={Text("Ending offspring age (days)")},enabled=!busy,singleLine=true,modifier=Modifier.fillMaxWidth().testTag("pairing-to-age"))
        FarmOutlinedTextField(tolerance,{tolerance=it},label={Text("Allowed age difference (days)")},enabled=!busy,singleLine=true,modifier=Modifier.fillMaxWidth())
        FarmOutlinedTextField(minimum,{minimum=it},label={Text("Minimum measured offspring per sire")},enabled=!busy,singleLine=true,modifier=Modifier.fillMaxWidth())
    }
    FarmPrimaryButton({
        val dam=requireNotNull(damId);val requested=requireNotNull(criteria)
        scope.launch {busy=true;error=null;result=null
            try {runSuspendCatching {queries.compare(dam,requested)}.onSuccess{result=it}.onFailure{error=it.message}}
            finally {busy=false}
        }
    },Modifier.fillMaxWidth().testTag("pairing-analyse"),enabled=!busy && damId!=null && criteria!=null) {
        Text(if(busy) "Reviewing recorded evidence…" else "Compare all eligible sires")
    }
    error?.let {Text(it,color=AnimalFarmTheme.colors.critical)}
    val current=result?.takeIf{it.dam.id==damId && it.criteria==criteria}
    if(result!=null && current==null) Text("Criteria changed. Compare again before using the previous results.",style=AnimalFarmHtmlType.caption)
    current?.let { comparison ->
        Text("${comparison.assessed} candidates assessed · ${comparison.rows.count{it.rank!=null}} meet your data criteria",style=AnimalFarmHtmlType.sectionTitle)
        if(comparison.rows.none{it.rank!=null}) AnimalFarmEmptyState("No supported ranking yet. Review the missing ancestry or offspring evidence below; missing records do not make a candidate better.")
        comparison.rows.take(limit).forEach { row ->
            FarmResourceAction((row.rank?.let{"#$it · "} ?: "Review · ")+row.sire.label,{selected=row},
                detail=pairingSummary(row),modifier=Modifier.testTag("breeding-candidate:${row.sire.id}"))
        }
        if(comparison.rows.size>limit) FarmActionButton({limit+=12},Modifier.fillMaxWidth()) {Text("Show more candidates · $limit of ${comparison.assessed}")}
        Text("Ranks apply only to your current criteria and available records. Confirm maturity, current health, reproductive state and farm policy before any mating.",style=AnimalFarmHtmlType.caption,color=AnimalFarmTheme.colors.mutedInk)
    }
    selected?.let { row -> PairingEvidenceSheet(row,{selected=null},{id->selected=null;onLineage(id)}) }
}
