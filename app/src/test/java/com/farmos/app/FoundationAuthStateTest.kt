package com.farmos.app

import kotlin.test.Test
import kotlin.test.assertEquals

class FoundationAuthStateTest {
    @Test
    fun backendFailureOutranksAuthenticationPresentation() {
        assertEquals(
            FoundationAuthScreenState.BACKEND_UNAVAILABLE,
            resolveFoundationAuthScreenState(false, true, 2, AuthAttentionState.SESSION_EXPIRED),
        )
    }

    @Test
    fun sessionExpiryAndRevocationHaveExplicitRegisteredScreens() {
        assertEquals(
            "FOS-GLOBAL-016",
            resolveFoundationAuthScreenState(true, false, 0, AuthAttentionState.SESSION_EXPIRED).screenId,
        )
        assertEquals(
            "FOS-GLOBAL-017",
            resolveFoundationAuthScreenState(true, true, 1, AuthAttentionState.FARM_ACCESS_REVOKED).screenId,
        )
    }

    @Test
    fun ordinaryEntranceStatesRemainStableAndOwnRegisteredScreens() {
        assertEquals(
            "FOS-GLOBAL-005",
            resolveFoundationAuthScreenState(true, true, 1, null).screenId,
        )
        assertEquals(
            "FOS-GLOBAL-006",
            resolveFoundationAuthScreenState(true, true, 0, null).screenId,
        )
        assertEquals(
            "FOS-GLOBAL-002",
            resolveFoundationAuthScreenState(true, false, 0, null).screenId,
        )
        assertEquals(
            "FOS-GLOBAL-018",
            resolveFoundationAuthScreenState(false, false, 0, null).screenId,
        )
    }
}
