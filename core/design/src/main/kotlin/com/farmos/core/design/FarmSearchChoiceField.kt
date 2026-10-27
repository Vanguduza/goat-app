package com.farmos.core.design

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier

/** A full local-database selector in a modal, with exact IDs preserved. No lookup over a capped screen list. */
@Composable
fun FarmSearchChoiceField(title:String, selected:FarmSelectorOption?, search:FarmSelectorSearch,
    onSelect:(FarmSelectorOption?)->Unit,tag:String,enabled:Boolean=true,noneLabel:String?=null,
    emptyText:String="No matching records",modifier:Modifier=Modifier) {
    var open by rememberSaveable(tag) { mutableStateOf(false) }
    FarmPickerField(title,selected?.label ?: noneLabel ?: "Choose",{open=true},"$tag:field",enabled,modifier)
    if(open) AnimalFarmSelectionSheet(title,{open=false}) {
        if(noneLabel!=null) FarmActionButton({onSelect(null);open=false}, enabled=enabled) {
            androidx.compose.material3.Text(noneLabel)
        }
        FarmSearchSelector(atomTag=tag,title=title,search=search,selected=selected,
            onSelect={option -> onSelect(option);open=false},
            emptyText=emptyText,enabled=enabled)
    }
}
