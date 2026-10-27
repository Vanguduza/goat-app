package com.farmos.app

import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.TaskEntity

/** Presentation identity only, resolved by the authenticated farm owner; no new task or photo store. */
data class HomeTaskIdentity(val label: String, val speciesCode: String?)

internal suspend fun loadHomeTaskIdentities(database: FarmOsDatabase, farmId: String, tasks: List<TaskEntity>): Map<String, HomeTaskIdentity> {
    require(tasks.all { it.farmId == farmId }) { "Task identity must remain within the owning farm" }
    val animals = tasks.mapNotNull { it.animalId }.distinct().mapNotNull { id ->
        database.animals().get(farmId, id)?.let { animal ->
            id to HomeTaskIdentity(listOfNotNull(animal.name?.takeIf { it.isNotBlank() }, animal.tag).distinct().joinToString(" · "), animal.speciesCode)
        }
    }.toMap()
    val cages = if (tasks.any { it.cageId != null }) database.rabbitProgramme().cages(farmId).associateBy { it.id } else emptyMap()
    return tasks.mapNotNull { task ->
        val identity = task.animalId?.let(animals::get) ?: task.cageId?.let(cages::get)?.let { cage ->
            HomeTaskIdentity(if (cage.code.startsWith("Cage", ignoreCase = true)) cage.code else "Cage ${cage.code}", "rabbit")
        }
        identity?.let { task.id to it }
    }.toMap()
}
