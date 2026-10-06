package com.farmos.domain.replication

import kotlin.test.Test
import kotlin.test.assertEquals

class CommandMergeClassificationTest {
    @Test
    fun postingsFieldUpdatesAndStatusChangesAreClassifiedExplicitly() {
        CommandMergeClassification.POSTINGS.forEach { assertEquals(MergeClass.POSTING, CommandMergeClassification.forCommand(it)) }
        CommandMergeClassification.FIELD_UPDATES.forEach { assertEquals(MergeClass.FIELD_UPDATE, CommandMergeClassification.forCommand(it)) }
        listOf("goat.set_status.v1", "sheep.set_status.v1", "cattle.set_status.v1", "rabbit.set_status.v1", "poultry.set_status.v1").forEach {
            assertEquals(MergeClass.IRREVERSIBLE_STATUS, CommandMergeClassification.forCommand(it))
        }
    }

    @Test
    fun recordsAndCreationsAreAppendOnlyFacts() {
        listOf(
            "goat.register.v1", "goat.record_weight.v1", "health.record_treatment.v1", "health.record_lab.v1",
            "poultry.flock_day.v1", "rabbit.record_palpation.v1", "inventory.item_create.v1", "task.create.v1",
            "water.record.v1", "sheep.record_lambing.v1", "cattle.record_calving.v1",
        ).forEach { assertEquals(MergeClass.APPEND_ONLY_EVENT, CommandMergeClassification.forCommand(it)) }
    }
}
