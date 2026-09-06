package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.projectWorkerTaskStage
import java.time.LocalDate

data class HomeTaskRow(
    val id: String,
    val title: String,
    val moduleCode: String,
    val taskCode: String,
    val dueEpochDay: Long,
    val status: String,
    val animalId: String?,
)

internal data class FarmHomeSummary(
    val goatCount: Int = 0,
    val openTasks: Int = 0,
    val overdueTasks: Int = 0,
    val upcomingTasks: Int = 0,
    val completedTasks: Int = 0,
    val activeWithdrawals: Int = 0,
    val pendingSync: Long = 0,
    val inventoryItemCount: Int = 0,
    val inventoryBelowReorder: Int = 0,
    val tasks: List<HomeTaskRow> = emptyList(),
    val loading: Boolean = false,
    val loadError: String? = null,
)

@Composable
fun FarmHomeHost(
    farmName: String?,
    membershipRole: String,
    farmId: String,
    ops: RoomOpsRepository,
    loadGoatCount: suspend () -> Int,
    loadPendingSync: suspend () -> Long,
    onOpen: (FarmDestination) -> Unit,
    onSignOut: () -> Unit,
) {
    var summary by remember(farmId) { mutableStateOf(FarmHomeSummary(loading = true)) }
    LaunchedEffect(ops, farmId) {
        val today = LocalDate.now().toEpochDay()
        runCatching {
            val open = ops.openTasks()
            val done = ops.completedTasks()
            val withdrawals = ops.withdrawals()
            val items = runCatching { ops.items() }.getOrDefault(emptyList())
            val rows = (open + done).map { row ->
                HomeTaskRow(
                    id = row.id,
                    title = row.title,
                    moduleCode = row.moduleCode,
                    taskCode = row.taskCode,
                    dueEpochDay = row.dueOnEpochDay,
                    status = row.status,
                    animalId = row.animalId,
                )
            }
            val stages = rows.mapNotNull { projectWorkerTaskStage(it.status, it.dueEpochDay, today) }
            FarmHomeSummary(
                goatCount = loadGoatCount(),
                openTasks = open.count { it.status == "open" },
                overdueTasks = stages.count { it == com.farmos.domain.ops.WorkerTaskStage.DUE_NOW },
                upcomingTasks = stages.count { it == com.farmos.domain.ops.WorkerTaskStage.UPCOMING },
                completedTasks = stages.count { it == com.farmos.domain.ops.WorkerTaskStage.COMPLETED },
                activeWithdrawals = withdrawals.count { it.endsEpochDay >= today },
                pendingSync = loadPendingSync(),
                inventoryItemCount = items.size,
                inventoryBelowReorder = items.count { it.reorderMilli > 0 && it.quantityMilli <= it.reorderMilli },
                tasks = rows,
            )
        }.onSuccess { summary = it }
            .onFailure { failure ->
                summary = FarmHomeSummary(loading = false, loadError = failure.message)
            }
    }
    RoleAwareFarmHomeScreen(
        role = membershipRole,
        farmName = farmName,
        summary = summary,
        onOpen = onOpen,
        onSignOut = onSignOut,
    )
}
