package com.farmos.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GoatSyncReceiptTest {
    @Test
    fun emptySuccessfulPullDoesNotClaimSynced() {
        val message = goatManualSyncReceipt(0, 0, 0, 0, 0)
        assertEquals("No pending local changes. Server returned no new events.", message)
        assertFalse(message.contains("Synced"))
    }

    @Test
    fun acceptedLocalChangesNameTheServerOutcome() {
        assertEquals("Server accepted 2 local change(s)", goatManualSyncReceipt(2, 0, 0, 0, 0))
        assertEquals(
            "Server accepted 1 local change(s). 3 server change(s) applied",
            goatManualSyncReceipt(1, 3, 0, 0, 0),
        )
    }

    @Test
    fun conflictAndRetryStayLocalUntilResolved() {
        assertEquals("Conflict needs review", goatManualSyncReceipt(0, 0, 1, 0, 0))
        assertEquals("Saved locally · server retry pending", goatManualSyncReceipt(0, 0, 0, 0, 1))
        assertEquals("Server rejected a pending record", goatManualSyncReceipt(0, 0, 0, 1, 0))
    }
}
