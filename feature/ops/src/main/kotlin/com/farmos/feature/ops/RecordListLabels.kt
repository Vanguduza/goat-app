package com.farmos.feature.ops

/**
 * Section title for a bounded record list. When [total] (an exhaustive farm-scoped count) exceeds
 * the rows shown, the title says the list is only the latest rows so it is never read as complete.
 */
internal fun recordListTitle(title: String, shown: Int, total: Int?): String =
    if (total != null && total > shown) "$title · latest $shown of $total" else "$title · ${total ?: shown}"
