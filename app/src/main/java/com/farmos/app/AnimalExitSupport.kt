package com.farmos.app

import com.farmos.core.database.AnimalExitEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.domain.ops.AnimalExitEvent
import com.farmos.domain.ops.AnimalExitKind
import com.farmos.domain.ops.AnimalExitRules
import com.farmos.domain.ops.DeathCause
import com.farmos.domain.ops.FarmCurrency
import com.farmos.domain.ops.RecordAnimalExit
import com.farmos.feature.goat.GoatExitDraft
import com.farmos.feature.goat.GoatExitView
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** An animal's standing exit (D-022): its latest recorded exit that has not been reversed. */
internal suspend fun standingAnimalExit(database: FarmOsDatabase, farmId: String, animalId: String): AnimalExitEntity? {
    val rows = database.animalExits().forAnimal(farmId, animalId)
    val events = rows.map { AnimalExitEvent(it.id, AnimalExitKind.entries.firstOrNull { kind -> kind.name == it.kind }, it.occurredEpochDay, it.recordedAtEpochMillis, it.reversesExitId) }
    val standing = AnimalExitRules.standing(events) ?: return null
    return rows.first { it.id == standing.exitId }
}

/** One line describing an exit: what happened, when, and its facts. */
internal fun exitSummary(exit: AnimalExitEntity): String {
    val day = LocalDate.ofEpochDay(exit.occurredEpochDay)
    return when (exit.kind) {
        AnimalExitKind.DEATH.name -> listOfNotNull("Died on $day", DeathCause.entries.firstOrNull { it.name == exit.deathCause }?.label, exit.reason)
        AnimalExitKind.CULL.name -> listOfNotNull("Culled on $day", exit.reason)
        AnimalExitKind.SALE.name -> listOfNotNull(
            "Sold on $day to ${exit.buyer}",
            exit.priceMinor?.let { price -> "${exit.currency} ${BigDecimal.valueOf(price, FarmCurrency.minorDigits(exit.currency.orEmpty())).toPlainString()}" },
        )
        else -> listOf("Left the farm on $day")
    }.joinToString(" · ")
}

internal suspend fun loadStandingGoatExit(database: FarmOsDatabase, farmId: String, animalId: String): GoatExitView? =
    standingAnimalExit(database, farmId, animalId)?.let { GoatExitView(it.id, exitSummary(it)) }

/** The exit command for a goat exit draft; a price is converted exactly in the farm currency's minor unit. */
internal fun goatExitCommand(animalId: String, draft: GoatExitDraft, currency: String?): RecordAnimalExit {
    val priceMinor = draft.price?.let { price ->
        requireNotNull(currency) { "The farm currency is still loading" }
        price.toScaledLongExact(FarmCurrency.minorDigits(currency), "Price")
    }
    return RecordAnimalExit(
        exitId = UUID.randomUUID().toString(),
        animalId = animalId,
        kind = draft.kind.code,
        occurredEpochDay = draft.day.toEpochDay(),
        deathCause = draft.deathCause,
        reason = draft.reason,
        buyer = draft.buyer,
        priceMinor = priceMinor,
        currency = currency.takeIf { priceMinor != null },
    )
}
