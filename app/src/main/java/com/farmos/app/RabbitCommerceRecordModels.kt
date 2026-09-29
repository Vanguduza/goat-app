package com.farmos.app

import com.farmos.core.database.FarmOsDatabase
import com.farmos.feature.rabbit.RabbitCommerceRecords
import com.farmos.feature.rabbit.RabbitContractView
import com.farmos.feature.rabbit.RabbitMarketPlanView
import com.farmos.feature.rabbit.RabbitReservationView
import com.farmos.feature.rabbit.RabbitRetentionView
import java.time.LocalDate

/**
 * Farm-scoped read model for the read-only rabbit commerce record pages. Every row is read
 * exhaustively; labels come from farm-scoped lookups and missing rows are named as such.
 */
internal suspend fun loadRabbitCommerceRecords(database: FarmOsDatabase, farmId: String): RabbitCommerceRecords {
    val lifecycle = database.lifecycle()
    val kits = lifecycle.kits(farmId).associate { it.id to (it.earTag?.takeIf { tag -> tag.isNotBlank() } ?: it.tempLabel) }
    val waves = database.rabbitProgramme().waves(farmId).associate { it.id to "Wave mated ${LocalDate.ofEpochDay(it.matingEpochDay)}" }
    val waitlist = lifecycle.waitlist(farmId)
    val reservations = waitlist.associate { it.id to it.contactName }
    val contracts = lifecycle.rabbitContracts(farmId)
    val animalTags = contracts.mapNotNull { it.animalId }.distinct().chunked(LOOKUP_CHUNK)
        .flatMap { database.animals().getMany(farmId, it) }
        .associate { it.id to it.tag }
    fun kit(id: String) = kits[id] ?: "Kit not on this device"
    return RabbitCommerceRecords(
        reservations = waitlist.map { RabbitReservationView(it.id, it.contactName, it.desiredSex, it.qty, it.status, it.matchedKitId?.let(::kit)) },
        contracts = contracts.map {
            RabbitContractView(
                id = it.id,
                buyerName = it.buyerName,
                reservationLabel = it.waitlistId?.let { id -> reservations[id] ?: "Reservation not on this device" },
                animalLabel = it.animalId?.let { id -> animalTags[id] ?: "Animal not on this device" },
                amountMinor = it.amountMinor,
                currency = it.currency,
                status = it.status,
                epochDay = it.occurredEpochDay,
            )
        },
        retention = lifecycle.retentionDecisions(farmId).map { RabbitRetentionView(it.id, kit(it.kitId), it.decision, it.occurredEpochDay) },
        plans = lifecycle.rabbitMarketPlans(farmId).map { plan ->
            RabbitMarketPlanView(
                id = plan.id,
                subjectLabel = plan.kitId?.let(::kit) ?: plan.waveId?.let { waves[it] ?: "Wave not on this device" } ?: "No kit or wave recorded",
                targetWeightGrams = plan.targetWeightGrams,
                targetEpochDay = plan.targetEpochDay,
                purpose = plan.purpose,
                status = plan.status,
            )
        },
    )
}
