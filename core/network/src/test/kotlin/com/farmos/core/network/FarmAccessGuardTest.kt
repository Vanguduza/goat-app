package com.farmos.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FarmAccessGuardTest {
    private val farmA = FarmMembership(farmId = "farm-a", role = "owner")
    private val farmB = FarmMembership(farmId = "farm-b", role = "worker")

    @Test
    fun `missing session is terminal and does not trust a remembered farm`() {
        val decision = FarmAccessGuard.decide(
            sessionPresent = false,
            rememberedFarmId = farmA.farmId,
            memberships = listOf(farmA),
        )

        assertEquals(FarmAccessDecision.SESSION_EXPIRED, decision)
        assertEquals(AuthorizationLoss.SESSION_EXPIRED, FarmAccessGuard.authorizationLoss(decision))
    }

    @Test
    fun `revoked remembered farm is not trusted even when pull would return zero rows`() {
        val decision = FarmAccessGuard.decide(
            sessionPresent = true,
            rememberedFarmId = farmA.farmId,
            memberships = emptyList(),
        )

        assertEquals(FarmAccessDecision.NO_FARM_MEMBERSHIP, decision)
        assertEquals(AuthorizationLoss.FARM_ACCESS_REVOKED, FarmAccessGuard.authorizationLoss(decision))
    }

    @Test
    fun `revoked remembered farm returns remaining memberships for safe reselection`() {
        val decision = FarmAccessGuard.decide(
            sessionPresent = true,
            rememberedFarmId = farmA.farmId,
            memberships = listOf(farmB),
        )

        assertEquals(FarmAccessDecision.REMEMBERED_FARM_REVOKED, decision)
        assertEquals(AuthorizationLoss.FARM_ACCESS_REVOKED, FarmAccessGuard.authorizationLoss(decision))
    }

    @Test
    fun `current membership still authorizes the remembered farm`() {
        val decision = FarmAccessGuard.decide(
            sessionPresent = true,
            rememberedFarmId = farmA.farmId,
            memberships = listOf(farmA, farmB),
        )

        assertEquals(FarmAccessDecision.GRANTED, decision)
        assertNull(FarmAccessGuard.authorizationLoss(decision))
    }

    @Test
    fun `signed-in user without a remembered farm must choose from live memberships`() {
        val decision = FarmAccessGuard.decide(
            sessionPresent = true,
            rememberedFarmId = null,
            memberships = listOf(farmA),
        )

        assertEquals(FarmAccessDecision.NEEDS_FARM_SELECTION, decision)
        assertNull(FarmAccessGuard.authorizationLoss(decision))
    }
}
