package com.farmos.app

import com.farmos.core.database.FarmOsDatabase
import com.farmos.data.herd.RabbitryCommands
import com.farmos.domain.rabbit.*
import com.farmos.feature.rabbit.*
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** The UI's exact selected versions are checked by the same journalled handlers on every device. */
internal fun rabbitryControlPorts(database:FarmOsDatabase,state:RabbitModuleState,access:RabbitryAccess):RabbitryPorts {
    val farmId=state.farmId
    val commands=RabbitryCommands(database,farmId)
    suspend fun save(block:suspend ()->Unit):String? {
        check(!state.busy) { "A rabbitry change is already being saved" }
        state.busy=true;state.error=null
        return try {
            completeModuleWrite(write=block,onCommitted={},enqueueSync={state.enqueueSync()},refresh={state.refresh()})
                .also { state.error=it }
        } finally {state.busy=false}
    }
    return RabbitryPorts(access,
        animalSelectorSearch(database,farmId,"rabbit"),animalSelectorSearch(database,farmId,"rabbit","FEMALE"),animalSelectorSearch(database,farmId,"rabbit","MALE"),
        housing={id ->
            val animal=requireNotNull(database.animals().get(farmId,id)) { "Animal is not on this farm" }
            require(animal.speciesCode=="rabbit")
            val row=database.rabbitry().housingFor(farmId,id)
            RabbitHousingChoice(id,row?.cageId,row?.cageId?.let{database.rabbitry().cage(farmId,it)?.code},row?.role,row?.version ?: 0)
        },
        setHousing={animal,cage,role,version,reason -> save {
            val ctx=state.newContext();commands.setHousing(SetRabbitHousing(animal,cage,role,version,ctx.occurredAtEpochMillis,reason),ctx)
        } },
        startService={cage,buck,wave,notes -> save {
            val ctx=state.newContext();commands.startService(StartRabbitBuckService(UUID.randomUUID().toString(),cage,buck,wave,ctx.occurredAtEpochMillis,notes),ctx)
        } },
        endService={service,version -> save {
            val ctx=state.newContext();commands.endService(EndRabbitBuckService(service,version,ctx.occurredAtEpochMillis),ctx)
        } },
        recordMating={cage,doe,buck,wave,day,notes -> save {
            val ctx=state.newContext();val today=Instant.ofEpochMilli(ctx.occurredAtEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            val at=if(day==today)ctx.occurredAtEpochMillis else day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            commands.recordMating(RecordRabbitPairMating(UUID.randomUUID().toString(),cage,doe,buck,wave,at,notes),ctx)
        } },
        assignNest={box,doe,wave,version -> save {
            val ctx=state.newContext();commands.assignNest(SetRabbitNestAssignment(box,doe,wave,version,ctx.occurredAtEpochMillis),ctx)
        } },
        setNestStatus={box,status -> save {state.ops.setNestBoxStatus(SetRabbitNestBoxStatus(box,status),state.newContext())} },
    )
}
