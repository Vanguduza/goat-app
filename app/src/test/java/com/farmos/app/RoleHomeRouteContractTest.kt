package com.farmos.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoleHomeRouteContractTest {
    @Test
    fun canonicalRoleHomeOwnersCoverTheEightRegisteredVariants() {
        val expected = mapOf(
            FarmHomePersona.OWNER to "FOS-HOME-012-A",
            FarmHomePersona.MANAGER to "FOS-HOME-012-B",
            FarmHomePersona.SUPERVISOR to "FOS-HOME-012-C",
            FarmHomePersona.WORKER to "FOS-HOME-012-D",
            FarmHomePersona.BREEDING to "FOS-HOME-012-E",
            FarmHomePersona.VET to "FOS-HOME-012-F",
            FarmHomePersona.FINANCE to "FOS-HOME-012-G",
            FarmHomePersona.BUYER to "FOS-HOME-012-H",
        )

        assertEquals(8, expected.size)
        expected.forEach { (persona, screenId) ->
            assertEquals(screenId, persona.canonicalHomeScreenId())
        }
        assertNull(FarmHomePersona.GENERAL.canonicalHomeScreenId())
    }

    @Test
    fun roleHomeFamilyIdentityRemainsCanonical() {
        assertEquals("FOS-HOME-012", ROLE_HOME_FAMILY_SCREEN_ID)
    }
}
