package com.farmos.domain.access

/** Local GOAT roles. They are product identities on this farm, not cloud or Google accounts. */
enum class LocalRole { OWNER, MANAGER, SUPERVISOR, WORKER, VIEWER }

enum class Permission {
    VIEW_FARM,
    RECORD_FARM_WORK,

    /** Operations Economics §12: recording money and committing farm commercial decisions. */
    RECORD_MONEY,

    /** Rabbit Programme §10: breeding decisions, distinct from completing assigned farm work. */
    MANAGE_BREEDING,

    /** Vet Intelligence §9: selecting an approved product or protocol for a treatment. */
    RECORD_TREATMENT,

    /** Health/Vet §9: Owner approval; the separate named-vet/attestation gates still apply. */
    MANAGE_HEALTH_PROTOCOLS,
    CAPTURE_STOCK_COUNT,
    REVIEW_WORK,
    MANAGE_WORKERS,
    POST_STOCK_ADJUSTMENT,
    MANAGE_ACCOUNTS,
    RESET_USER_CREDENTIALS,
    MANAGE_DEVICES,
    APPROVE_DEVICE_PAIRING,
    MANAGE_STORAGE_AND_BACKUP,
    RESOLVE_SYNC_CONFLICTS,
    MANAGE_FARM_SETTINGS,

    /** Exporting farm records from the device (D-026); farm data leaves the farm's control. */
    EXPORT_FARM_DATA,

    /** Creating, disabling, re-roling or resetting Owner accounts. Only Owners hold it. */
    MANAGE_OWNERS,
}

/**
 * Default role permissions. Workers record and count; supervisors also review and manage workers;
 * management (Manager and Owner) administers accounts, devices, storage, conflicts, settings, posts
 * stock adjustments, records money, manages breeding/product selection and exports farm records.
 * Only Owners manage Owners and approve health protocols; clinical evidence is a separate gate.
 */
object RolePermissions {
    private val viewer = setOf(Permission.VIEW_FARM)
    private val worker = viewer + setOf(Permission.RECORD_FARM_WORK, Permission.CAPTURE_STOCK_COUNT)
    private val supervisor = worker + setOf(Permission.REVIEW_WORK, Permission.MANAGE_WORKERS)
    private val manager = supervisor + setOf(
        Permission.RECORD_MONEY,
        Permission.MANAGE_BREEDING,
        Permission.RECORD_TREATMENT,
        Permission.POST_STOCK_ADJUSTMENT,
        Permission.MANAGE_ACCOUNTS,
        Permission.RESET_USER_CREDENTIALS,
        Permission.MANAGE_DEVICES,
        Permission.APPROVE_DEVICE_PAIRING,
        Permission.MANAGE_STORAGE_AND_BACKUP,
        Permission.RESOLVE_SYNC_CONFLICTS,
        Permission.MANAGE_FARM_SETTINGS,
        Permission.EXPORT_FARM_DATA,
    )
    private val owner = Permission.entries.toSet()

    fun of(role: LocalRole): Set<Permission> = when (role) {
        LocalRole.OWNER -> owner
        LocalRole.MANAGER -> manager
        LocalRole.SUPERVISOR -> supervisor
        LocalRole.WORKER -> worker
        LocalRole.VIEWER -> viewer
    }

    fun allows(role: LocalRole, permission: Permission): Boolean = permission in of(role)
}
