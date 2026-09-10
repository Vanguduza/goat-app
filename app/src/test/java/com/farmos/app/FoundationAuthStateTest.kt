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
    fun ordinaryEntranceStatesRemainStable() {
        assertEquals(FoundationAuthScreenState.FARM_SELECTION, resolveFoundationAuthScreenState(true, true, 1, null))
        assertEquals(FoundationAuthScreenState.FARM_SETUP, resolveFoundationAuthScreenState(true, true, 0, null))
        assertEquals(FoundationAuthScreenState.SIGN_IN, resolveFoundationAuthScreenState(true, false, 0, null))
    }
}
