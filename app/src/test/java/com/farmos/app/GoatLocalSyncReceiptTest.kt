package com.farmos.app

import com.farmos.domain.replication.SyncOutcome
import com.farmos.domain.replication.SyncSessionStatus
import com.farmos.domain.replication.TransportKind
import org.junit.Assert.assertEquals
import org.junit.Test

/** Without a server the goat Sync now receipt reports only what the farm-network sessions did. */
class GoatLocalSyncReceiptTest {
    private fun completed(pulled: Int, pushed: Int) =
        SyncOutcome(TransportKind.FARM_LAN_PEER, SyncSessionStatus.COMPLETED, pulledOperations = pulled, pushedOperations = pushed)

    private val unavailable = SyncOutcome(TransportKind.FARM_LAN_PEER, SyncSessionStatus.TRANSPORT_UNAVAILABLE)

    @Test
    fun noRuntimeOrNoDeviceNeverClaimsSynchronised() {
        assertEquals("Saved locally · farm network sync is not running on this device · 3 change(s) waiting to sync", goatLocalSyncReceipt(null, 3))
        assertEquals("Saved locally · no other farm device found on this network · 2 change(s) waiting to sync", goatLocalSyncReceipt(emptyList(), 2))
        assertEquals("Saved locally · no other farm device found on this network", goatLocalSyncReceipt(emptyList(), 0))
    }

    @Test
    fun onlyCompletedSessionsCountAsSynchronised() {
        assertEquals("Synchronised with 1 farm device(s), sent 4, received 1 change(s)", goatLocalSyncReceipt(listOf(completed(1, 4)), 0))
        assertEquals(
            "Synchronised with 1 farm device(s), sent 4, received 0 change(s) · 1 device(s) did not complete",
            goatLocalSyncReceipt(listOf(completed(0, 4), unavailable), 0),
        )
        assertEquals("Saved locally · sync with 2 farm device(s) did not complete · 5 change(s) waiting to sync", goatLocalSyncReceipt(listOf(unavailable, unavailable), 5))
    }
}
