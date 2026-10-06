package com.farmos.domain.replication

/**
 * Merge semantics of every local command that enters the replication journal. Classification is
 * explicit for postings, field updates and irreversible status changes; every other command records a
 * new fact (a weight, a treatment, a creation) and is append-only. The table is exhaustive over the
 * commands the app issues and is proven by `CommandMergeClassificationTest`.
 */
object CommandMergeClassification {
    /** Money and stock movements: every posting is kept and reconciled explicitly, never overwritten. */
    val POSTINGS: Set<String> = setOf(
        "feed.issue.v1",
        "inventory.lot_issue.v1",
        "inventory.lot_receive.v1",
        "inventory.move.v1",
        "labour.record.v1",
        "labour.record.v2",
        "money.record.v1",
        "purchase.record.v1",
        "sale.record.v1",
        "sale.record.v2",
        "sale.record_exit.v1",
    )

    /** Mutable configuration of an existing record. */
    val FIELD_UPDATES: Set<String> = setOf(
        "access.account_set.v1",
        "access.recovery_set.v1",
        "farm.set_currency.v1",
        "farm.set_gestation.v1",
        "health.pack_accept.v1",
        "inventory.set_reorder.v1",
        "rabbit.nest_box_set_status.v1",
    )

    fun forCommand(commandName: String): MergeClass = when {
        commandName in POSTINGS -> MergeClass.POSTING
        commandName in FIELD_UPDATES -> MergeClass.FIELD_UPDATE
        // Animal status (exit, death, cull, sale) cannot be undone from the field.
        commandName.endsWith(".set_status.v1") -> MergeClass.IRREVERSIBLE_STATUS
        else -> MergeClass.APPEND_ONLY_EVENT
    }
}
