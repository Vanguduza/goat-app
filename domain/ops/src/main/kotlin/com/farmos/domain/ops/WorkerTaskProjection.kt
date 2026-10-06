package com.farmos.domain.ops

/**
 * Presentation projection for worker home stages.
 * This is not a new task workflow state machine.
 */
enum class WorkerTaskStage {
    DUE_NOW,
    UPCOMING,
    COMPLETED,
}

enum class HomeAttentionKind {
    WITHDRAWAL,
    OVERDUE_WORK,
    PENDING_SYNC,
    NONE,
}

fun projectWorkerTaskStage(
    status: String,
    dueOnEpochDay: Long,
    todayEpochDay: Long,
): WorkerTaskStage? =
    when (status) {
        "done" -> WorkerTaskStage.COMPLETED
        "open" ->
            if (dueOnEpochDay <= todayEpochDay) {
                WorkerTaskStage.DUE_NOW
            } else {
                WorkerTaskStage.UPCOMING
            }
        else -> null
    }

fun rankHomeAttention(
    activeWithdrawals: Int,
    overdueTasks: Int,
    pendingSync: Long,
): HomeAttentionKind =
    when {
        activeWithdrawals > 0 -> HomeAttentionKind.WITHDRAWAL
        overdueTasks > 0 -> HomeAttentionKind.OVERDUE_WORK
        pendingSync > 0L -> HomeAttentionKind.PENDING_SYNC
        else -> HomeAttentionKind.NONE
    }

/**
 * Maps a task module code to a family key only when the species is explicit.
 * Unknown or shared modules stay null so UI cannot invent a family portrait.
 */
fun inferTaskSpeciesFamily(moduleCode: String): String? =
    when (moduleCode.lowercase()) {
        "goat", "goats" -> "goat"
        "rabbit", "rabbits" -> "rabbit"
        "sheep" -> "sheep"
        "cattle", "cow", "cows" -> "cattle"
        "poultry", "chicken", "hen" -> "poultry"
        else -> null
    }
