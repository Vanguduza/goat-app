package com.farmos.feature.rabbit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.farmos.core.design.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Recorded unit state, not a decorative cage icon or an inferred occupancy from programme capacity. */
@Composable
fun RabbitUnitBoard(records:RabbitRecords,today:LocalDate) {
    var query by rememberSaveable {mutableStateOf("")}
    var filter by rememberSaveable {mutableStateOf("All units")}
    var limit by rememberSaveable(query,filter) {mutableIntStateOf(12)}
    var selectedId by rememberSaveable {mutableStateOf<String?>(null)}
    val units=if(records.units.isNotEmpty()) records.units else records.cages.map {
        RabbitUnitView(it.id,it.code,it.doeCapacity,emptyList(),it.nestBoxes.map{box->RabbitUnitNestView(box.id,box.code,box.status,null,null,null,0,null)},emptyList(),emptyList(),it.waveIds)
    }
    val shown=units.filter { unit ->
        val text=listOf(unit.code)+unit.residents.map{it.label}+unit.nests.map{it.code}+unit.openServices.map{it.buckLabel}
        text.any{it.contains(query.trim(),ignoreCase=true)} && when(filter) {
            "Buck in service"->unit.openServices.isNotEmpty()
            "Nests in cage"->unit.nests.any{it.status=="in_cage"}
            "Nests to clean"->unit.nests.any{it.status=="dirty"}
            "Occupants unrecorded"->unit.residents.isEmpty()
            else->true
        }
    }.sortedWith(compareBy({it.code},{it.cageId}))
    Text("Rabbitry units",style=AnimalFarmHtmlType.sectionTitle)
    Text("Ordered by cage code, not a measured floor plan. Tap a unit for residents, nest boxes, services and records.",style=AnimalFarmHtmlType.caption,color=AnimalFarmTheme.colors.mutedInk)
    FarmOutlinedTextField(query,{query=it},label={Text("Find cage, rabbit or nest box")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("rabbit-unit-query"))
    FarmChoicePicker("Show units",listOf("All units","Buck in service","Nests in cage","Nests to clean","Occupants unrecorded").map{it to it},filter,{filter=it},"rabbit-unit-filter")
    if(shown.isEmpty()) AnimalFarmEmptyState(if(units.isEmpty()) "No cages recorded. Add a cage before assigning residents or nests." else "No matching units")
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns=if(maxWidth<330.dp || LocalDensity.current.fontScale>=1.5f)1 else 2
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            shown.take(limit).chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    row.forEach { unit -> RabbitUnitTile(unit,records,today,{selectedId=unit.cageId},Modifier.weight(1f).fillMaxHeight()) }
                    repeat(columns-row.size){Spacer(Modifier.weight(1f))}
                }
            }
        }
    }
    if(shown.size>limit) FarmActionButton({limit+=12},Modifier.fillMaxWidth()) {Text("Show more units · $limit of ${shown.size}")}
    val selected=units.firstOrNull{it.cageId==selectedId}
    if(selected!=null) RabbitUnitDetailSheet(selected,records,today,{selectedId=null})
}

@Composable
private fun RabbitUnitTile(unit:RabbitUnitView,records:RabbitRecords,today:LocalDate,onClick:()->Unit,modifier:Modifier) {
    val colors=AnimalFarmTheme.colors
    Surface(modifier.testTag("rabbit-cage-tile:${unit.cageId}").clickable(role=Role.Button,onClick=onClick),
        shape=RoundedCornerShape(20.dp),color=colors.surface,contentColor=colors.ink,
        border=BorderStroke(if(unit.openServices.isNotEmpty())2.dp else 1.dp,if(unit.openServices.isNotEmpty())colors.primary else colors.divider)) {
        Column(Modifier.heightIn(min=AnimalFarmTheme.minimumTouchDp.dp).padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                UnitGlyph(Modifier.size(32.dp))
                Text(unit.code,Modifier.weight(1f),style=AnimalFarmHtmlType.sectionTitle)
                FarmHtmlIcon("arrow-up-right",null)
            }
            Text(rabbitUnitStage(unit,records,today),style=AnimalFarmHtmlType.subject,color=colors.primary)
            Text("${unit.recordedDoes}/${unit.doeCapacity} does recorded",style=AnimalFarmHtmlType.caption)
            Text("${unit.residents.count{it.role=="buck"}} buck(s) · ${unit.residents.count{it.role=="growout"}} grow-out(s)",style=AnimalFarmHtmlType.caption)
            unit.openServices.firstOrNull()?.let {Text("In service: ${it.buckLabel}",style=AnimalFarmHtmlType.caption,modifier=Modifier.testTag("rabbit-service-summary:${unit.cageId}"))}
            if(unit.residents.isEmpty()) Text("Placement history is incomplete until residents are recorded.",style=AnimalFarmHtmlType.caption,color=colors.mutedInk)
            Text("Nest boxes",style=AnimalFarmHtmlType.caption,color=colors.mutedInk)
            if(unit.nests.isEmpty()) Text("None recorded",style=AnimalFarmHtmlType.caption)
            unit.nests.take(3).forEach { box ->
                Row(horizontalArrangement=Arrangement.spacedBy(7.dp),verticalAlignment=Alignment.CenterVertically) {
                    NestGlyph(box.status,Modifier.size(25.dp))
                    Column(Modifier.weight(1f)) {
                        Text(box.code,style=AnimalFarmHtmlType.caption)
                        Text(nestStateLabel(box.status),style=AnimalFarmHtmlType.navigation,color=colors.mutedInk)
                    }
                }
            }
            if(unit.nests.size>3) Text("+${unit.nests.size-3} more nest boxes",style=AnimalFarmHtmlType.caption)
        }
    }
}

@Composable
private fun UnitGlyph(modifier:Modifier) {
    val colors=AnimalFarmTheme.colors
    Canvas(modifier) {
        drawRoundRect(colors.primary,cornerRadius=androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),style=Stroke(1.5.dp.toPx()))
        for(i in 1..3) drawLine(colors.divider,Offset(size.width*i/4,0f),Offset(size.width*i/4,size.height),1.dp.toPx())
        drawLine(colors.primary,Offset(0f,size.height*0.65f),Offset(size.width,size.height*0.65f),1.5.dp.toPx())
    }
}

@Composable
internal fun NestGlyph(status:String,modifier:Modifier=Modifier) {
    val colors=AnimalFarmTheme.colors
    Canvas(modifier) {
        val fill=when(status){"in_cage"->colors.lime;"assigned"->colors.softSurface;else->colors.surface}
        drawRoundRect(fill,topLeft=Offset(2.dp.toPx(),5.dp.toPx()),size=Size(size.width-4.dp.toPx(),size.height-7.dp.toPx()),cornerRadius=androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
        drawRoundRect(colors.primary,topLeft=Offset(2.dp.toPx(),5.dp.toPx()),size=Size(size.width-4.dp.toPx(),size.height-7.dp.toPx()),cornerRadius=androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),style=Stroke(1.5.dp.toPx()))
        if(status=="dirty") {drawLine(colors.mutedInk,Offset(7.dp.toPx(),11.dp.toPx()),Offset(size.width-7.dp.toPx(),size.height-5.dp.toPx()),1.5.dp.toPx());drawLine(colors.mutedInk,Offset(size.width-7.dp.toPx(),11.dp.toPx()),Offset(7.dp.toPx(),size.height-5.dp.toPx()),1.5.dp.toPx())}
    }
}

internal fun nestStateLabel(status:String)=when(status){"available"->"Available";"sanitized"->"Clean / sanitized";"assigned"->"Reserved";"in_cage"->"In cage";"dirty"->"To clean";else->"Recorded: $status"}
internal fun rabbitRecordDate(at:Long)=Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate().toString()
