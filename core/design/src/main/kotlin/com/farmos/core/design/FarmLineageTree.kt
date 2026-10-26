package com.farmos.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Immutable graph inputs. Missing/conflicting parents are visible nodes, never fabricated identities. */
data class FarmLineageNode(val id:String,val label:String,val detail:String,val sireId:String?,val geneticDamId:String?,val conflict:Boolean=false)
private data class LineageSlot(val key:String,val id:String?,val level:Int,val position:Int,val relation:String,val cycle:Boolean)

@Composable
fun FarmLineageTree(rootId:String,nodes:Map<String,FarmLineageNode>,generations:Int,onSelect:(String)->Unit,
    modifier:Modifier=Modifier) {
    require(generations in 1..3)
    val slots=remember(rootId,nodes,generations) {
        val list=mutableListOf<LineageSlot>()
        fun visit(id:String?,level:Int,index:Int,path:String,relation:String,ancestors:Set<String>) {
            val cycle=id!=null && id in ancestors
            list+=LineageSlot(path,id,level,index,relation,cycle)
            if(level==generations)return
            val node=if(cycle) null else id?.let(nodes::get)
            val seen=if(id==null) ancestors else ancestors+id
            visit(if(node?.conflict==true)null else node?.sireId,level+1,index*2,path+"S","Sire",seen)
            visit(if(node?.conflict==true)null else node?.geneticDamId,level+1,index*2+1,path+"D","Genetic dam",seen)
        }
        visit(rootId,0,0,"R","Selected animal",emptySet());list
    }
    val scroll=rememberScrollState()
    LaunchedEffect(rootId,generations,scroll.maxValue) { if(scroll.maxValue>0) scroll.scrollTo(scroll.maxValue/2) }
    val colors=AnimalFarmTheme.colors
    val density=LocalDensity.current
    val cardWidth=if(density.fontScale>=1.5f) 300.dp else 210.dp
    Column(modifier.fillMaxWidth()) {
        Text("Drag sideways to follow the branches. Tap a recorded animal to make it the centre of the tree.",
            style=AnimalFarmHtmlType.caption,color=colors.mutedInk)
        Box(Modifier.fillMaxWidth().horizontalScroll(scroll).padding(vertical=12.dp)) {
            SubcomposeLayout(Modifier.testTag("pedigree-connected-tree")) { constraints ->
                val width=cardWidth.roundToPx();val xGap=16.dp.roundToPx();val yGap=48.dp.roundToPx()
                val measured=slots.associate { slot ->
                    val node=slot.id?.let(nodes::get)
                    val placeable=subcompose("card:${slot.key}") {
                        val enabled=node!=null && !slot.cycle
                        Surface(Modifier.width(cardWidth).testTag("pedigree-node:${slot.key}")
                            .clickable(enabled=enabled,role=Role.Button,onClick={node?.let { onSelect(it.id) }}),
                            color=if(slot.level==0) colors.lime else colors.surface,
                            contentColor=if(slot.level==0) colors.onLime else colors.ink,
                            shape=RoundedCornerShape(16.dp),
                            border=BorderStroke(if(slot.level==0)2.dp else 1.dp,if(node?.conflict==true || slot.cycle) colors.critical else colors.divider)) {
                            Column(Modifier.heightIn(min=AnimalFarmTheme.minimumTouchDp.dp).padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                Text(slot.relation,style=AnimalFarmHtmlType.caption)
                                Text(node?.label ?: if(slot.id==null) "Not recorded" else "Record unavailable",style=AnimalFarmHtmlType.action)
                                if(node!=null) Text(node.detail,style=AnimalFarmHtmlType.caption)
                                if(slot.cycle) Text("Repeated ancestry cycle · branch stopped",style=AnimalFarmHtmlType.caption,color=colors.critical)
                                else if(node?.conflict==true) Text("Parentage conflict · review before inference",style=AnimalFarmHtmlType.caption,color=colors.critical)
                            }
                        }
                    }.single().measure(Constraints.fixedWidth(width))
                    slot.key to placeable
                }
                val maxHeight=measured.values.maxOf{it.height};val row=maxHeight+yGap
                val leaves=1 shl generations;val totalWidth=leaves*(width+xGap)
                val totalHeight=(generations+1)*row-yGap
                fun x(slot:LineageSlot):Int=((slot.position+0.5)*leaves/(1 shl slot.level)*(width+xGap)-width/2.0).roundToInt()
                fun y(slot:LineageSlot)=slot.level*row
                val byKey=slots.associateBy{it.key}
                val edges=subcompose("edges") {
                    Canvas(Modifier.size(with(density){totalWidth.toDp()},with(density){totalHeight.toDp()})) {
                        slots.filter{it.level>0}.forEach { slot ->
                            val parent=byKey[slot.key.dropLast(1)] ?: return@forEach
                            val from=Offset(x(parent)+width/2f,y(parent)+measured.getValue(parent.key).height.toFloat())
                            val to=Offset(x(slot)+width/2f,y(slot).toFloat());val mid=(from.y+to.y)/2f
                            drawLine(colors.divider,from,Offset(from.x,mid),2.dp.toPx())
                            drawLine(colors.divider,Offset(from.x,mid),Offset(to.x,mid),2.dp.toPx())
                            drawLine(colors.divider,Offset(to.x,mid),to,2.dp.toPx())
                        }
                    }
                }.single().measure(Constraints.fixed(totalWidth,totalHeight))
                layout(totalWidth,totalHeight) {
                    edges.place(0,0)
                    slots.forEach { measured.getValue(it.key).place(x(it),y(it)) }
                }
            }
        }
    }
}
