package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.CustomerCommands
import com.farmos.domain.ops.CreateFarmCustomer
import com.farmos.domain.ops.RecordCustomerSale
import com.farmos.domain.ops.UpdateFarmCustomer
import com.farmos.domain.replication.LocalPeerTransport
import com.farmos.domain.replication.SyncSession
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Customers (FOS-SALES-002/003) and sales to them (D-004): searchable, never deleted, replicated. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class CustomerCommandsTest {
    private val farm = "34343434-3434-4343-8343-343434343434"
    private val databases = mutableListOf<FarmOsDatabase>()
    private var clock = 1_790_000_000_000L

    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
        .allowMainThreadQueries()
        .build()
        .also { databases += it }

    @After
    fun tearDown() = databases.forEach { it.close() }

    private fun context(device: String = "A", at: Long = clock++) = LocalCommandContext(farm, "worker-1", device, UUID.randomUUID().toString(), at)

    private fun sale(customerId: String, name: String, amount: Long = 12_000) =
        RecordCustomerSale(UUID.randomUUID().toString(), customerId, name, "live_goat", 1_000, amount, "USD", 20_300)

    @Test
    fun customersAreSearchedChangedAndNeverDeleted(): Unit = runBlocking {
        val db = database()
        val customers = CustomerCommands(db, farm)
        customers.create(CreateFarmCustomer("c1", "Moyo Butchery", "+263 77 123 4567"), context())
        customers.create(CreateFarmCustomer("c2", "Chari 100% Meats"), context())
        customers.create(CreateFarmCustomer("c3", "Rudo Market"), context())
        val search = customerSelectorSearch(db, farm)

        assertEquals(listOf("Chari 100% Meats", "Moyo Butchery", "Rudo Market"), search.page("", 0, 10).options.map { it.label })
        // A typed % matches literally, and the phone is searched too.
        assertEquals(listOf("c2"), search.page("100%", 0, 10).options.map { it.id })
        assertEquals(listOf("c1"), search.page("4567", 0, 10).options.map { it.id })
        assertEquals(true, search.page("", 0, 2).hasMore)

        customers.update(UpdateFarmCustomer("c3", active = false), context())
        assertEquals(listOf("c2", "c1"), search.page("", 0, 10).options.map { it.id })
        assertEquals(3, db.customers().all(farm).size)

        // An older change arriving later never overwrites a newer one; an empty phone clears it.
        customers.update(UpdateFarmCustomer("c1", name = "Moyo Butchery Ltd", phone = ""), context(at = clock + 1_000))
        customers.update(UpdateFarmCustomer("c1", name = "Stale name"), context(at = 1L))
        val moyo = db.customers().get(farm, "c1")!!
        assertEquals("Moyo Butchery Ltd", moyo.name)
        assertNull(moyo.phone)
    }

    @Test
    fun aSaleToACustomerPostsIncomeAndReplicates(): Unit = runBlocking {
        val aDb = database()
        val bDb = database()
        val customers = CustomerCommands(aDb, farm)
        customers.create(CreateFarmCustomer("c1", "Moyo Butchery"), context())
        customers.create(CreateFarmCustomer("c2", "Closed Buyer"), context())
        customers.update(UpdateFarmCustomer("c2", active = false), context())

        customers.recordSale(sale("c1", "Moyo Butchery"), context())
        assertThrows(IllegalArgumentException::class.java) { runBlocking { customers.recordSale(sale("c2", "Closed Buyer"), context()) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { customers.recordSale(sale("c1", "Moyo Butchery", amount = 0), context()) } }

        assertEquals(listOf("c1"), aDb.sales().recent(farm, 10).map { it.customerId })
        assertEquals(listOf("live_goat · Moyo Butchery" to 12_000L), aDb.money().recent(farm, 10).map { it.note to it.amountMinor })

        val a = RoomReplicaEndpoint(aDb, farm, "A", replicationAppliers).apply { registerPairedDevice("B", "B") }
        val b = RoomReplicaEndpoint(bDb, farm, "B", replicationAppliers).apply { registerPairedDevice("A", "A") }
        SyncSession.run(b, LocalPeerTransport(a), remoteDeviceId = "A")
        assertEquals(listOf("c1" to true, "c2" to false), bDb.customers().all(farm).map { it.id to it.active }.sortedBy { it.first })
        assertEquals(listOf("c1"), bDb.sales().recent(farm, 10).map { it.customerId })
        assertEquals(1, bDb.money().recent(farm, 10).size)
    }
}
