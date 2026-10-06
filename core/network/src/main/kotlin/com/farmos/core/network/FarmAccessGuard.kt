package com.farmos.core.network

enum class AuthorizationLoss {
    SESSION_EXPIRED,
    FARM_ACCESS_REVOKED,
}

enum class FarmAccessDecision {
    GRANTED,
    SESSION_EXPIRED,
    NEEDS_FARM_SELECTION,
    REMEMBERED_FARM_REVOKED,
    NO_FARM_MEMBERSHIP,
}

object FarmAccessGuard {
    fun decide(
        sessionPresent: Boolean,
        rememberedFarmId: String?,
        memberships: List<FarmMembership>,
    ): FarmAccessDecision {
        if (!sessionPresent) return FarmAccessDecision.SESSION_EXPIRED
        if (rememberedFarmId == null) {
            return if (memberships.isEmpty()) {
                FarmAccessDecision.NO_FARM_MEMBERSHIP
            } else {
                FarmAccessDecision.NEEDS_FARM_SELECTION
            }
        }
        if (memberships.any { it.farmId == rememberedFarmId }) {
            return FarmAccessDecision.GRANTED
        }
        return if (memberships.isEmpty()) {
            FarmAccessDecision.NO_FARM_MEMBERSHIP
        } else {
            FarmAccessDecision.REMEMBERED_FARM_REVOKED
        }
    }

    fun authorizationLoss(decision: FarmAccessDecision): AuthorizationLoss? = when (decision) {
        FarmAccessDecision.SESSION_EXPIRED -> AuthorizationLoss.SESSION_EXPIRED
        FarmAccessDecision.NO_FARM_MEMBERSHIP,
        FarmAccessDecision.REMEMBERED_FARM_REVOKED,
        -> AuthorizationLoss.FARM_ACCESS_REVOKED
        FarmAccessDecision.GRANTED,
        FarmAccessDecision.NEEDS_FARM_SELECTION,
        -> null
    }
}
