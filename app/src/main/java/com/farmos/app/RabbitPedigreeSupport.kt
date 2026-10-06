package com.farmos.app

import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.LinkPedigree
import com.farmos.feature.rabbit.RabbitCoiView
import com.farmos.feature.rabbit.RabbitParentView
import com.farmos.feature.rabbit.RabbitPedigreePorts
import java.util.UUID

/**
 * Rabbit pedigree over the whole local rabbitry (D-004, D-023). A sire must be a buck and a dam a doe; links
 * go through the governed pedigree command and replicate with it.
 */
internal fun rabbitPedigreePorts(database: FarmOsDatabase, farmId: String, ops: RoomOpsRepository, newContext: () -> LocalCommandContext, onLinked: () -> Unit): RabbitPedigreePorts {
    val coi = speciesMateCoi(database, farmId, "rabbit")
    suspend fun label(id: String) = database.animals().get(farmId, id)?.let { listOfNotNull(it.tag, it.name).joinToString(" · ") } ?: "Unregistered animal"
    return RabbitPedigreePorts(
        searchRabbits = animalSelectorSearch(database, farmId, "rabbit"),
        searchDoes = animalSelectorSearch(database, farmId, "rabbit", "FEMALE"),
        searchBucks = animalSelectorSearch(database, farmId, "rabbit", "MALE"),
        parents = { rabbitId -> database.lifecycle().pedigreeParents(farmId, rabbitId).map { RabbitParentView(it.relationType.replace('_', ' '), label(it.parentId)) } },
        link = { rabbitId, parentId, relation ->
            val parent = requireNotNull(database.animals().get(farmId, parentId)) { "Parent not found on this farm" }
            require(parent.speciesCode == "rabbit") { "Choose a rabbit as the parent" }
            require(if (relation == "sire") parent.sex == "MALE" else parent.sex == "FEMALE") { if (relation == "sire") "A sire must be a buck" else "A dam must be a doe" }
            ops.linkPedigree(LinkPedigree(UUID.randomUUID().toString(), rabbitId, parentId, relation), newContext())
            onLinked()
        },
        coi = { buckId, doeId ->
            val view = coi.analyse(buckId, doeId)
            RabbitCoiView(view.coefficient, view.generationsKnown, view.commonAncestors, view.conflictingParentage, view.sireDateOfBirthEpochDay, view.sireLatestWeightGrams)
        },
    )
}
