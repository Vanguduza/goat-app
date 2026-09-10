package com.farmos.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MutableSessionStoreTest {
    @Test
    fun `session expiry is derived from trusted receipt time and refresh leeway`() {
        var now = 1_000_000L
        var persistedExpiry = -1L
        val store = MutableSessionStore(
            now = { now },
            onChanged = { _, expiresAt -> persistedExpiry = expiresAt },
        )
        val session = SupabaseSession(
            accessToken = "access",
            refreshToken = "refresh",
            expiresInSeconds = 3_600,
            user = AuthUser("user-1"),
        )

        store.set(session)

        assertEquals(4_600_000L, store.expiryEpochMillis())
        assertEquals(4_600_000L, persistedExpiry)
        assertFalse(store.needsRefresh())

        now = 4_541_000L
        assertTrue(store.needsRefresh())
    }

    @Test
    fun `restored session keeps absolute expiry and can be cleared without password persistence`() {
        val store = MutableSessionStore(now = { 5_000L })
        val session = SupabaseSession(
            accessToken = "restored-access",
            refreshToken = "restored-refresh",
            expiresInSeconds = 60,
            user = AuthUser("user-2"),
        )

        store.restore(session, expiresAtEpochMillis = 10_000L)
        assertEquals("user-2", store.current()?.user?.id)
        assertEquals(10_000L, store.expiryEpochMillis())
        assertTrue(store.needsRefresh(leewayMillis = 5_000L))

        store.set(null)
        assertNull(store.current())
        assertEquals(0L, store.expiryEpochMillis())
    }
}
