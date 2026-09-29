package com.farmos.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Rabbit commerce record reads are exhaustive, farm-scoped and deterministically ordered. */
@RunWith(AndroidJUnit4::class)
class RabbitCommerceQueryTest {
    private lateinit var database: FarmOsDatabase
    private val farmA = "11111111-1111-4111-8111-111111111111"
    private val farmB = "22222222-2222-4222-8222-222222222222"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FarmOsDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun retentionAndContractsListNewestFirstThenById() = runBlocking {
        val lifecycle = database.lifecycle()
        lifecycle.insertRetention(RabbitRetentionEntity("r-b", farmA, "k1", "sale_pet", 9))
        lifecycle.insertRetention(RabbitRetentionEntity("r-a", farmA, "k2", "cull", 9))
        lifecycle.insertRetention(RabbitRetentionEntity("r-old", farmA, "k3", "keep_breeder", 2))
        lifecycle.insertRetention(RabbitRetentionEntity("r-farm-b", farmB, "k1", "cull", 20))
        lifecycle.insertContract(RabbitContractEntity("c-b", farmA, null, "Buyer", null, 100, "USD", "agreed", 5))
        lifecycle.insertContract(RabbitContractEntity("c-a", farmA, "w1", "Buyer", "a1", 200, "ZAR", "agreed", 5))
        lifecycle.insertContract(RabbitContractEntity("c-new", farmA, null, "Buyer", null, 300, "USD", "agreed", 8))
        lifecycle.insertContract(RabbitContractEntity("c-farm-b", farmB, null, "Buyer", null, 999, "USD", "agreed", 1))

        assertEquals(listOf("r-a", "r-b", "r-old"), lifecycle.retentionDecisions(farmA).map { it.id })
        assertEquals(listOf("c-new", "c-a", "c-b"), lifecycle.rabbitContracts(farmA).map { it.id })
        assertEquals(listOf("r-farm-b"), lifecycle.retentionDecisions(farmB).map { it.id })
        assertEquals(listOf("c-farm-b"), lifecycle.rabbitContracts(farmB).map { it.id })
    }

    @Test
    fun marketPlansListByTargetDateAndWaitlistBreaksNameTiesById() = runBlocking {
        val lifecycle = database.lifecycle()
        lifecycle.insertPlan(RabbitMarketPlanEntity("p-late", farmA, "k1", null, 2_400, 40, "meat", "active"))
        lifecycle.insertPlan(RabbitMarketPlanEntity("p-b", farmA, null, "wave-1", 1_800, 30, "pet_sale", "active"))
        lifecycle.insertPlan(RabbitMarketPlanEntity("p-a", farmA, "k2", null, 2_000, 30, "show", "active"))
        lifecycle.insertPlan(RabbitMarketPlanEntity("p-farm-b", farmB, "k1", null, 2_000, 1, "meat", "active"))
        lifecycle.insertWaitlist(RabbitWaitlistEntity("w-b", farmA, "Thandi", null, 1, "open", null))
        lifecycle.insertWaitlist(RabbitWaitlistEntity("w-a", farmA, "Thandi", "female", 2, "open", null))
        lifecycle.insertWaitlist(RabbitWaitlistEntity("w-c", farmA, "Andile", null, 1, "fulfilled", "k1"))

        assertEquals(listOf("p-a", "p-b", "p-late"), lifecycle.rabbitMarketPlans(farmA).map { it.id })
        assertEquals(listOf("p-farm-b"), lifecycle.rabbitMarketPlans(farmB).map { it.id })
        assertEquals(listOf("w-c", "w-a", "w-b"), lifecycle.waitlist(farmA).map { it.id })
    }
}
