package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomHerdRepository
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.feature.rabbit.RabbitProfileEntry

/** Shared module wiring for ordinary entries and an exact farm-local animal profile. */
@Composable
internal fun FarmSpeciesModuleHost(
    module: FarmModule,
    database: FarmOsDatabase,
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    profile: FarmDestination.AnimalProfile? = null,
) {
    require(module in setOf(FarmModule.SHEEP, FarmModule.CATTLE, FarmModule.RABBIT))
    require(profile == null || (profile.kind.module == module && profile.animalId.isNotBlank()))
    key(database, farmId, module, profile) {
        when (module) {
            FarmModule.RABBIT -> RabbitModuleHost(
                farmId = farmId,
                ops = ops,
                rabbitHerd = remember(database, farmId) { RoomHerdRepository(database, farmId, "rabbit") },
                newContext = newContext,
                enqueueSync = enqueueSync,
                onBack = onBack,
                loadRecords = { loadRabbitRecords(database, farmId) },
                searchRabbits = remember(database, farmId) { animalSelectorSearch(database, farmId, "rabbit") },
                pedigree = remember(database, farmId) { rabbitPedigreePorts(database, farmId, ops, newContext, enqueueSync) },
                exitFor = { rabbit, onRecorded ->
                    SpeciesExitHost(database, farmId, SpeciesAnimalRow(rabbit.animalId, rabbit.label, rabbit.active), null, newContext, onRecorded, onBack = {})
                },
                attachmentsFor = { rabbit -> AnimalAttachmentsHost(database, farmId, rabbit.animalId, canAttach = rabbit.active, newContext) },
                entryProfile = profile?.let { RabbitProfileEntry(it.animalId, it.kind == AnimalProfileKind.RABBIT_DOE) },
            )
            FarmModule.SHEEP, FarmModule.CATTLE -> OperatingModuleHost(
                module = module,
                farmId = farmId,
                database = database,
                ops = ops,
                newContext = newContext,
                enqueueSync = enqueueSync,
                onBack = onBack,
                entryAnimalId = profile?.animalId,
            )
            else -> error("Unsupported individual-animal module")
        }
    }
}
