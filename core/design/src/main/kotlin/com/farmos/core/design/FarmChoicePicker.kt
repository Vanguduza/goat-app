package com.farmos.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Compact form value. The full option list exists only while its native selection sheet is open. */
@Composable
fun FarmPickerField(title: String, value: String, onClick: () -> Unit, tag: String, enabled: Boolean = true,
    modifier: Modifier = Modifier) {
    val colors = AnimalFarmTheme.colors
    Surface(modifier.fillMaxWidth().testTag(tag).clickable(enabled=enabled, role=Role.Button, onClick=onClick),
        shape=RoundedCornerShape(AnimalFarmHomeMetrics.fieldRadius), border=BorderStroke(1.dp, colors.divider),
        color=colors.background, contentColor=if(enabled) colors.ink else colors.mutedInk) {
        Row(Modifier.heightIn(min=maxOf(56.dp,AnimalFarmTheme.minimumTouchDp.dp)).padding(horizontal=16.dp,vertical=10.dp),
            horizontalArrangement=Arrangement.spacedBy(12.dp), verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(title,style=AnimalFarmHtmlType.caption,color=colors.mutedInk)
                Text(value,style=AnimalFarmHtmlType.field)
            }
            FarmHtmlIcon("arrow-right",null,Modifier.size(16.dp).rotate(90f))
        }
    }
}

@Composable
fun <T> FarmChoicePicker(title: String, options: List<Pair<T,String>>, selected: T, onSelect: (T)->Unit,
    tag: String, enabled: Boolean=true, modifier: Modifier=Modifier) {
    require(options.map { it.first }.distinct().size==options.size) { "Each picker option needs one identity" }
    var open by rememberSaveable(tag) { mutableStateOf(false) }
    var query by rememberSaveable(tag) { mutableStateOf("") }
    FarmPickerField(title,options.firstOrNull { it.first==selected }?.second ?: "Choose",{open=true},"$tag:field",enabled,modifier)
    if(open) AnimalFarmSelectionSheet(title,{open=false}) {
        if(options.size>8) FarmOutlinedTextField(query,{query=it},label={Text("Search choices")},singleLine=true,
            modifier=Modifier.fillMaxWidth().testTag("$tag:query"),enabled=enabled)
        val found=options.filter { it.second.contains(query.trim(),ignoreCase=true) }
        found.forEach { (value,label) ->
            FarmSelectorOptionRow(tag,FarmSelectorOption(value?.toString() ?: "none",label),value==selected,enabled, optionTag="$tag:${value ?: "none"}") {
                onSelect(value);open=false;query=""
            }
        }
        if(found.isEmpty()) Text("No matching choices",style=AnimalFarmHtmlType.body,color=AnimalFarmTheme.colors.mutedInk)
    }
}
