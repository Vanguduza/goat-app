package com.farmos.data.herd

import com.farmos.domain.access.AccessDenied
import com.farmos.domain.access.Permission
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Exact command admission policy for the operations facade and customer/sales writer.
 *
 * Authority: Operations Economics §12 (money and stock); Owner decisions D-020/D-021 (tasks and
 * stock); Rabbit Programme §10 (breeding versus task completion); Health §9 and Vet Intelligence
 * §9 (clinical product selection and protocol approval). A role grant never substitutes for the
 * separate vet-attestation, pack, task-assignment or training evidence required by those contracts.
 * New commands must be classified here before they may make a local durable change.
 */
object OpsCommandPermissions {
    private val permissions = buildMap<String, Permission> {
        fun commands(permission: Permission, vararg names: String) {
            names.forEach { name -> check(put(name, permission) == null) { "Duplicate command permission: $name" } }
        }
        commands(
            Permission.RECORD_FARM_WORK,
            "animal.identifier_assign.v1",
            "rabbit.register.v1", "rabbit.record_weight.v1", "rabbit.record_weight.v2", "rabbit.set_status.v1",
            "sheep.register.v1", "sheep.record_weight.v1", "sheep.set_status.v1",
            "cattle.register.v1", "cattle.record_weight.v1", "cattle.set_status.v1",
            "poultry.register.v1", "poultry.record_weight.v1", "poultry.set_status.v1",
            "cattle.lot_close.v1", "cattle.lot_place.v1", "cattle.record_bcs.v1",
            "cattle.record_calving.v1", "cattle.record_dof.v1", "cattle.record_dryoff.v1",
            "cattle.record_heat.v1", "cattle.record_locomotion.v1", "cattle.record_milk.v1",
            "cattle.record_pd.v1", "cattle.record_scc.v1", "cattle.record_weaning.v1",
            "feed.issue.v1", "grazing.start.v1", "grazing.end.v1",
            "group.create.v1", "group.amend.v1", "group.animal_move.v1", "group.census.v1",
            "health.record_lab.v1", "health.record_observation.v1", "health.record_vet_visit.v1",
            "inventory.lot_issue.v1", "inventory.lot_receive.v1", "inventory.move.v1",
            "inventory.record_reorder.v1", "inventory.record_reorder.v2", "labour.record.v1", "maintenance.record.v1",
            "asset.meter_record.v1", "official.record_movement.v1",
            "poultry.flock_day.v1", "poultry.flock_place.v1", "poultry.flock_move.v1",
            "poultry.flock_close.v1", "poultry.hatch_candle.v1", "poultry.hatch_record.v1",
            "poultry.record_biosecurity.v1",
            "rabbit.kit_register.v1", "rabbit.nest_box_set_status.v1",
            "rabbit.record_foster.v1", "rabbit.record_gi_stasis.v1", "rabbit.record_kindling.v1",
            "rabbit.record_palpation.v1", "rabbit.record_wean.v1",
            "sheep.record_bcs.v1", "sheep.record_dag.v1", "sheep.record_famacha.v1",
            "sheep.record_flystrike.v1", "sheep.record_footrot.v1", "sheep.record_lambing.v1",
            "sheep.record_marking.v1", "sheep.record_micron.v1", "sheep.record_scan.v1",
            "sheep.record_shearing.v1", "sheep.record_weaning.v1", "sheep.record_wool.v1",
            "task.complete.v1", "water.record.v1", "water.record_point_event.v1",
            "task.occurrence_complete.v1", "labour.record.v2", "attachment.attach.v1",
            "animal.exit_record.v1", "animal.exit_reverse.v1",
        )
        commands(
            Permission.MANAGE_FARM_SETTINGS,
            "farm.record_unit_preference.v1", "asset.create.v1", "feed.record_plan.v1",
            "inventory.item_create.v1", "paddock.create.v1", "poultry.house_create.v1",
            "poultry.kind_enable.v1", "rabbit.bedding_bind.v1", "rabbit.cage_create.v1",
            "rabbit.nest_box_create.v1", "water.record_point.v1",
        )
        commands(
            Permission.MANAGE_WORKERS, "task.create.v1", "task.series_create.v1", "task.series_edit.v1",
            "task.series_end.v1", "task.update.v1", "worker.create.v1", "worker.update.v1",
        )
        commands(
            Permission.POST_STOCK_ADJUSTMENT, "inventory.set_reorder.v1", "stock.count_post.v1", "stock.count_reject.v1",
        )
        commands(
            Permission.CAPTURE_STOCK_COUNT, "stock.count_start.v1", "stock.count_line.v1", "stock.count_submit.v1",
        )
        commands(
            Permission.RECORD_MONEY,
            "money.record.v1", "finance.record_budget.v1", "finance.revise_budget.v1",
            "purchase.record.v1", "sale.record.v1", "sale.record.v2", "sale.record_exit.v1",
            "rabbit.contract_agree.v1", "rabbit.waitlist_enqueue.v1", "rabbit.waitlist_fulfill.v1",
            "supplier.create.v1", "customer.create.v1", "customer.update.v1",
        )
        commands(
            Permission.MANAGE_BREEDING,
            "cattle.record_service.v1", "cattle.record_service.v2", "sheep.record_joining.v2",
            "pedigree.link.v1", "poultry.hatch_set.v1",
            "rabbit.kit_promote.v1", "rabbit.market_plan.v1", "rabbit.record_mating_outcome.v1",
            "rabbit.retention_decide.v1", "rabbit.wave_create.v1", "sheep.record_joining.v1",
        )
        commands(
            Permission.RECORD_TREATMENT,
            "health.pack_apply.v1", "health.record_treatment.v1", "health.record_vaccination.v1",
            "poultry.record_vaccination.v1",
        )
        commands(
            Permission.MANAGE_HEALTH_PROTOCOLS,
            "formulary.item_create.v1", "formulary.item_create.v2", "health.pack_accept.v1", "health.pack_slot_add.v1",
        )
    }

    val commandNames: Set<String> get() = permissions.keys

    /** Command semantic version is explicit; older sale.record.v2 receipts still use schema 1. */
    fun schemaVersionFor(commandName: String): Int {
        if (commandName !in permissions) throw AccessDenied("This command has no approved local permission")
        return when (commandName) {
            FormularyCommands.CREATE_DRAFT, ReorderAlertCommands.RECORD, "cattle.record_service.v2", "sheep.record_joining.v2", "rabbit.record_weight.v2" -> 2
            else -> 1
        }
    }

    fun requireLocalVersion(commandName: String) {
        if (commandName == ReorderAlertCommands.LEGACY) {
            throw AccessDenied("Legacy reorder alerts are historical receipts; new alerts must record their stock snapshot")
        }
        if (commandName in setOf("rabbit.set_status.v1", "sheep.set_status.v1", "cattle.set_status.v1", "poultry.set_status.v1")) {
            throw AccessDenied("Record an animal exit or its reversal so the cause, buyer and audit history are kept")
        }
        if (commandName == FormularyCommands.LEGACY_CREATE) {
            throw AccessDenied("Legacy formulary approval is historical replay only; create an unapproved draft")
        }
    }

    fun requiredFor(commandName: String, payloadJson: String): Permission {
        val permission = permissions[commandName]
            ?: throw AccessDenied("This command has no approved local permission")
        if (commandName != "rabbit.nest_box_set_status.v1") return permission
        val status = runCatching {
            Json.parseToJsonElement(payloadJson).jsonObject["status"]?.jsonPrimitive?.content
        }.getOrNull()
        return when (status) {
            "retired" -> Permission.MANAGE_BREEDING
            "assigned", "in_cage", "dirty", "sanitized", "available" -> Permission.RECORD_FARM_WORK
            else -> throw AccessDenied("This nest-box change has no approved local permission")
        }
    }
}
