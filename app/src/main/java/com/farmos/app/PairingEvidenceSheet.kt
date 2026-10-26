package com.farmos.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.farmos.core.design.*
import com.farmos.domain.ops.*
import java.util.Locale

internal fun pairingSummary(row:PairingAssessment):String=buildString {
    append(row.pedigree?.let { "Recorded F ${String.format(Locale.ROOT,"%.3f",it.coefficient*100)}% · ${it.generationsKnown} complete generations" } ?: "Coefficient unavailable")
    append(" · ${row.sireOffspring.linked} linked offspring")
    row.sireOffspring.meanGramsPerDay?.let { append(" · ${String.format(Locale.ROOT,"%.1f",it)} g/day in ${row.sireOffspring.growthSamples.size} measured offspring") }
    if(row.blockers.isNotEmpty()) append(" · ${row.blockers.first()}")
}

@Composable
internal fun PairingEvidenceSheet(row:PairingAssessment,onDismiss:()->Unit,onLineage:(String)->Unit) {
    AnimalFarmSelectionSheet("Pairing evidence",onDismiss) {
        Text("${row.dam.label} × ${row.sire.label}",style=AnimalFarmHtmlType.sectionTitle)
        Text(row.rank?.let{"Rank $it under the selected criteria"} ?: "Not ranked: evidence or criteria need review",style=AnimalFarmHtmlType.body)
        Text(pairingSummary(row),style=AnimalFarmHtmlType.body)
        row.blockers.forEach {Text(it,color=AnimalFarmTheme.colors.critical,style=AnimalFarmHtmlType.caption)}
        FarmSectionEvidence("Sire offspring",row.sireOffspring)
        FarmSectionEvidence("Dam offspring",row.damOffspring)
        Text("${row.sharedOffspring} genetically linked offspring already recorded from this pair",style=AnimalFarmHtmlType.caption)
        Text("Recorded growth samples",style=AnimalFarmHtmlType.sectionTitle)
        if(row.sireOffspring.growthSamples.isEmpty()) Text("No comparable offspring age-window samples in this analysis.",style=AnimalFarmHtmlType.body)
        row.sireOffspring.growthSamples.forEach { sample ->
            FarmResourceAction(sample.label,{onLineage(sample.offspringId)},
                detail="Ages ${sample.firstAgeDays}–${sample.lastAgeDays} days · ${String.format(Locale.ROOT,"%.1f",sample.gramsPerDay)} g/day",
                modifier=Modifier.testTag("offspring-evidence:${sample.offspringId}"))
        }
        row.warnings.forEach {Text(it,style=AnimalFarmHtmlType.caption,color=AnimalFarmTheme.colors.mutedInk)}
        FarmActionButton({onLineage(row.sire.id)},Modifier.fillMaxWidth()) {Text("View sire pedigree")}
        FarmActionButton({onLineage(row.dam.id)},Modifier.fillMaxWidth()) {Text("View dam pedigree")}
        Text("No mating, pregnancy, parent link or treatment has been created by this comparison.",style=AnimalFarmHtmlType.caption)
    }
}

@Composable
private fun FarmSectionEvidence(label:String,data:OffspringEvidence) {
    Text(label,style=AnimalFarmHtmlType.sectionTitle)
    Text("${data.linked} linked · ${data.active} currently active · ${data.exitedOrOther} other recorded statuses",style=AnimalFarmHtmlType.body)
    Text("${data.growthSamples.size} comparable growth samples · ${data.excludedFromGrowth} without usable samples · ${data.distinctOtherParents} other parents represented",style=AnimalFarmHtmlType.caption)
    Text("Current status counts are not a survival rate. Different environments and other parents can affect offspring results.",style=AnimalFarmHtmlType.caption,color=AnimalFarmTheme.colors.mutedInk)
}
