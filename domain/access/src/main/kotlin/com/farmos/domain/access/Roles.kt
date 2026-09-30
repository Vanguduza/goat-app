package com.farmos.domain.access

/** Local GOAT roles. They are product identities on this farm, not cloud or Google accounts. */
enum class LocalRole { OWNER, MANAGER, SUPERVISOR, WORKER, VIEWER }

enum class Permission {
    VIEW_FARM,
    RECORD_FARM_WORK,
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

    /** Creating, disabling, re-roling or resetting Owner accounts. Only Owners hold it. */
    MANAGE_OWNERS,
}

/**
 * Default role permissions. Workers record and count; supervisors also review and manage workers;
 * management (Manager and Owner) administers accounts, devices, storage, conflicts, settings and posts
 * stock adjustments; only Owners manage Owners.
 */
object RolePermissions {
    private val viewer = setOf(Permission.VIEW_FARM)
    private val worker = viewer + setOf(Permission.RECORD_FARM_WORK, Permission.CAPTURE_STOCK_COUNT)
    private val supervisor = worker + setOf(Permission.REVIEW_WORK, Permission.MANAGE_WORKERS)
    private val manager = supervisor + setOf(
        Permission.POST_STOCK_ADJUSTMENT,
        Permission.MANAGE_ACCOUNTS,
        Permission.RESET_USER_CREDENTIALS,
        Permission.MANAGE_DEVICES,
        Permission.APPROVE_DEVICE_PAIRING,
        Permission.MANAGE_STORAGE_AND_BACKUP,
        Permission.RESOLVE_SYNC_CONFLICTS,
        Permission.MANAGE_FARM_SETTINGS,
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
