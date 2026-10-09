package com.farmos.app

import com.farmos.data.herd.HerdReplicationAppliers
import com.farmos.data.herd.OpsCommandPermissions
import com.farmos.domain.access.AccessDenied
import com.farmos.domain.access.LocalRole
import com.farmos.domain.access.Permission
import com.farmos.domain.access.RolePermissions
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpsCommandPermissionTest {
    @Test
    fun everyGovernedWriterHasAnExactPermissionAndNoUnclassifiedCommandIsAdmitted() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "data/herd/src/main/kotlin/com/farmos/data/herd").isDirectory }
        val source = File(root, "data/herd/src/main/kotlin/com/farmos/data/herd")
        val writers = listOf(
            "RoomOpsRepository", "FarmConfigurationCommands", "FarmResourceCommands", "AnimalGroupCommands",
            "FormularyCommands", "ReorderAlertCommands", "CustomerCommands", "StockCountCommands", "TaskSeriesCommands",
            "BreedingDueCommands", "WorkerRegisterCommands", "LabourCommands", "AttachmentCommands", "AnimalExitCommands",
        )
        val commandPattern = Regex("\"([a-z][a-z_.]+\\.v[0-9]+)\"")
        val emitted = writers.flatMap { writer ->
            commandPattern.findAll(File(source, "$writer.kt").readText()).map { it.groupValues[1] }.toList()
        }.toSet() + HerdReplicationAppliers.all.keys
        assertEquals("Every emitted/replayed ID needs a reviewed policy; stale policies must be removed", emitted, OpsCommandPermissions.commandNames)
        for (unclassified in listOf("unknown.record.v1", "money.record.v999", "stock.count_post.v99", "")) {
            assertThrows(AccessDenied::class.java) { OpsCommandPermissions.requiredFor(unclassified, "{}") }
        }
    }

    @Test
    fun administrativeCommercialAndClinicalCommandsNeverFallBackToOrdinaryWork() {
        val expected = mapOf(
            "money.record.v1" to Permission.RECORD_MONEY,
            "sale.record.v2" to Permission.RECORD_MONEY,
            "sale.record_exit.v1" to Permission.RECORD_MONEY,
            "purchase.record.v1" to Permission.RECORD_MONEY,
            "finance.record_budget.v1" to Permission.RECORD_MONEY,
            "finance.revise_budget.v1" to Permission.RECORD_MONEY,
            "rabbit.contract_agree.v1" to Permission.RECORD_MONEY,
            "inventory.set_reorder.v1" to Permission.POST_STOCK_ADJUSTMENT,
            "poultry.kind_enable.v1" to Permission.MANAGE_FARM_SETTINGS,
            "task.create.v1" to Permission.MANAGE_WORKERS,
            "rabbit.wave_create.v1" to Permission.MANAGE_BREEDING,
            "health.record_treatment.v1" to Permission.RECORD_TREATMENT,
            "health.record_vaccination.v1" to Permission.RECORD_TREATMENT,
            "formulary.item_create.v2" to Permission.MANAGE_HEALTH_PROTOCOLS,
            "health.pack_accept.v1" to Permission.MANAGE_HEALTH_PROTOCOLS,
        )
        expected.forEach { (command, permission) ->
            assertEquals(command, permission, OpsCommandPermissions.requiredFor(command, "{}"))
            assertFalse(command, RolePermissions.allows(LocalRole.WORKER, permission))
            assertFalse(command, RolePermissions.allows(LocalRole.VIEWER, permission))
        }
        for (permission in listOf(Permission.RECORD_MONEY, Permission.MANAGE_BREEDING, Permission.RECORD_TREATMENT)) {
            assertTrue(RolePermissions.allows(LocalRole.OWNER, permission))
            assertTrue(RolePermissions.allows(LocalRole.MANAGER, permission))
            assertFalse(RolePermissions.allows(LocalRole.SUPERVISOR, permission))
        }
        assertTrue(RolePermissions.allows(LocalRole.OWNER, Permission.MANAGE_HEALTH_PROTOCOLS))
        for (role in LocalRole.entries.filter { it != LocalRole.OWNER }) {
            assertFalse(RolePermissions.allows(role, Permission.MANAGE_HEALTH_PROTOCOLS))
        }
    }

    @Test
    fun routineWorkRemainsAvailableAndNestLifecycleChangesCannotHideAnAdministrativeAction() {
        for (command in listOf("inventory.lot_receive.v1", "inventory.lot_issue.v1", "inventory.move.v1",
            "task.complete.v1", "health.record_observation.v1", "rabbit.record_weight.v1", "grazing.start.v1")) {
            assertEquals(command, Permission.RECORD_FARM_WORK, OpsCommandPermissions.requiredFor(command, "{}"))
            assertTrue(RolePermissions.allows(LocalRole.WORKER, OpsCommandPermissions.requiredFor(command, "{}")))
        }
        for (status in listOf("assigned", "in_cage", "dirty", "sanitized", "available")) {
            assertEquals(Permission.RECORD_FARM_WORK,
                OpsCommandPermissions.requiredFor("rabbit.nest_box_set_status.v1", "{\"status\":\"$status\"}"))
        }
        assertEquals(Permission.MANAGE_BREEDING,
            OpsCommandPermissions.requiredFor("rabbit.nest_box_set_status.v1", "{\"status\":\"retired\"}"))
        assertThrows(AccessDenied::class.java) {
            OpsCommandPermissions.requiredFor("rabbit.nest_box_set_status.v1", "{\"status\":\"unknown\"}")
        }
    }

    @Test
    fun legacyAutomaticFormularyApprovalIsReplayOnlyAndNewDraftsHaveExplicitVersionTwo() {
        assertThrows(AccessDenied::class.java) { OpsCommandPermissions.requireLocalVersion("formulary.item_create.v1") }
        OpsCommandPermissions.requireLocalVersion("formulary.item_create.v2")
        for (species in HerdReplicationAppliers.SPECIES) {
            assertThrows(AccessDenied::class.java) { OpsCommandPermissions.requireLocalVersion("$species.set_status.v1") }
        }
        assertEquals(2, OpsCommandPermissions.schemaVersionFor("formulary.item_create.v2"))
        assertEquals(1, OpsCommandPermissions.schemaVersionFor("formulary.item_create.v1"))
        assertEquals(1, OpsCommandPermissions.schemaVersionFor("sale.record.v2"))
        assertEquals(2, OpsCommandPermissions.schemaVersionFor("inventory.record_reorder.v2"))
        assertEquals(1, OpsCommandPermissions.schemaVersionFor("inventory.record_reorder.v1"))
        assertThrows(AccessDenied::class.java) { OpsCommandPermissions.requireLocalVersion("inventory.record_reorder.v1") }
    }
}
