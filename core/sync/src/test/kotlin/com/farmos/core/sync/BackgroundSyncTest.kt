package com.farmos.core.sync

import com.farmos.core.network.AuthorizationLoss
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Background sync never touches the outbox or retries when no server is configured. */
class BackgroundSyncTest {
    private fun <T> runSuspend(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
        return requireNotNull(outcome).getOrThrow()
    }

    private class ServerlessOwner : SyncEngineOwner {
        var pulled = false
        override val serverSyncConfigured = false
        override val syncEngine: SyncEngine get() = error("The outbox must not be drained without a server")
        override suspend fun pullAuthoritativeChanges(): AuthoritativePullOutcome {
            pulled = true
            return AuthoritativePullOutcome.RETRY
        }
        override fun onTerminalAuthorizationLoss(reason: AuthorizationLoss) = error("No authorization without a server")
    }

    @Test
    fun withoutAServerBackgroundSyncSucceedsWithoutDrainingOrPulling() {
        val owner = ServerlessOwner()
        assertEquals(SyncWorkOutcome.SUCCESS, runSuspend { runBackgroundSync(owner, "left-over-periodic-work") })
        assertTrue(!owner.pulled)
    }

    @Test
    fun ownersAreServerConfiguredUnlessTheySayOtherwise() {
        val owner = object : SyncEngineOwner {
            override val syncEngine: SyncEngine get() = error("unused")
            override suspend fun pullAuthoritativeChanges() = AuthoritativePullOutcome.SKIPPED
            override fun onTerminalAuthorizationLoss(reason: AuthorizationLoss) = Unit
        }
        assertTrue(owner.serverSyncConfigured)
    }
}
