package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.farmos.data.herd.RoomOpsRepository
import java.time.LocalDate

internal data class FarmHomeSummary(
    val goatCount: Int = 0,
    val openTasks: Int = 0,
    val activeWithdrawals: Int = 0,
    val pendingSync: Long = 0,
)

@Composable
fun FarmHomeHost(
    farmName: String?,
    goatCount: Int,
    pendingSync: Long,
    ops: RoomOpsRepository,
    onOpen: (FarmModule) -> Unit,
    onSignOut: () -> Unit,
) {
    var summary by remember(goatCount, pendingSync) {
        mutableStateOf(FarmHomeSummary(goatCount = goatCount, pendingSync = pendingSync))
    }
    LaunchedEffect(ops, goatCount, pendingSync) {
        val today = LocalDate.now().toEpochDay()
        val tasks = runCatching { ops.openTasks() }.getOrDefault(emptyList())
        val withdrawals = runCatching { ops.withdrawals() }.getOrDefault(emptyList())
        summary = FarmHomeSummary(
            goatCount = goatCount,
            openTasks = tasks.count { it.status == "open" },
            activeWithdrawals = withdrawals.count { it.endsEpochDay >= today },
            pendingSync = pendingSync,
        )
    }
    FarmHomeScreen(
        farmName = farmName,
        summary = summary,
        onOpen = onOpen,
        onSignOut = onSignOut,
    )
}
