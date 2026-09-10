package com.farmos.app

enum class AuthAttentionState {
    SESSION_EXPIRED,
    FARM_ACCESS_REVOKED,
}

enum class FoundationAuthScreenState(
    val screenId: String,
) {
    SIGN_IN("FOS-GLOBAL-002"),
    FARM_SELECTION("FOS-GLOBAL-005"),
    FARM_SETUP("FOS-GLOBAL-006"),
    SESSION_EXPIRED("FOS-GLOBAL-016"),
    FARM_ACCESS_REVOKED("FOS-GLOBAL-017"),
    BACKEND_UNAVAILABLE("FOS-GLOBAL-018"),
}

fun resolveFoundationAuthScreenState(
    backendConfigured: Boolean,
    sessionPresent: Boolean,
    membershipCount: Int,
    attention: AuthAttentionState?,
): FoundationAuthScreenState =
    when {
        !backendConfigured -> FoundationAuthScreenState.BACKEND_UNAVAILABLE
        attention == AuthAttentionState.SESSION_EXPIRED -> FoundationAuthScreenState.SESSION_EXPIRED
        attention == AuthAttentionState.FARM_ACCESS_REVOKED -> FoundationAuthScreenState.FARM_ACCESS_REVOKED
        membershipCount > 0 -> FoundationAuthScreenState.FARM_SELECTION
        sessionPresent -> FoundationAuthScreenState.FARM_SETUP
        else -> FoundationAuthScreenState.SIGN_IN
    }
