package com.farmos.app

import com.farmos.core.database.AnimalEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.design.FarmSearchPage
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.design.FarmSelectorSearch

/**
 * Selector search over every animal of [farmId] in the local database, optionally limited to one species
 * and sex. It reads the database directly, never a module's capped presentation list, and pages through
 * all matches in deterministic tag order. The option id is the animal's exact record id.
 */
internal fun animalSelectorSearch(
    database: FarmOsDatabase,
    farmId: String,
    speciesCode: String?,
    sex: String? = null,
): FarmSelectorSearch = FarmSelectorSearch { query, offset, limit ->
    val pattern = query.trim().takeIf { it.isNotEmpty() }?.let { "%${escapeLike(it)}%" }
    val rows = database.animals().search(farmId, speciesCode, sex, pattern, limit + 1, offset)
    FarmSearchPage(rows.take(limit).map { it.toSelectorOption(showSpecies = speciesCode == null) }, hasMore = rows.size > limit)
}

private fun AnimalEntity.toSelectorOption(showSpecies: Boolean): FarmSelectorOption {
    val status = status.replace('_', ' ').replaceFirstChar { it.uppercase() }
    return FarmSelectorOption(
        id = id,
        label = listOfNotNull(tag, name?.takeIf { it.isNotBlank() }).joinToString(" · "),
        detail = if (showSpecies) "${speciesCode.replaceFirstChar { it.uppercase() }} · $status" else status,
    )
}

/** Escapes LIKE wildcards so a typed `%` or `_` matches literally. */
internal fun escapeLike(text: String): String =
    text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
