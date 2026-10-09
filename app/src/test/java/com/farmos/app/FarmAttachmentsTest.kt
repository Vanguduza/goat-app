package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.AnimalEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.model.LocalCommandContext
import com.farmos.domain.access.LocalRole
import com.farmos.data.herd.AttachmentCommands
import com.farmos.domain.ops.AttachFile
import com.farmos.domain.ops.AttachmentRules
import com.farmos.domain.replication.LocalPeerTransport
import com.farmos.domain.replication.SyncSession
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Owner decision D-015: attachments are content-addressed, kept locally, and their metadata replicates first. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class FarmAttachmentsTest {
    private val farm = "cdcdcdcd-cdcd-4dcd-8dcd-cdcdcdcdcdcd"
    private val databases = mutableListOf<FarmOsDatabase>()
    private val directories = mutableListOf<File>()
    private var clock = 1_790_000_000_000L
    private val photo = ByteArray(2_048) { (it % 251).toByte() }

    private fun database(device: String = "A") = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
        .allowMainThreadQueries()
        .build()
        .also { db ->
            databases += db
            seedCommandAuthority(db, farm, "worker-1", device, LocalRole.WORKER)
        }

    private fun store() = FileAttachmentStore(Files.createTempDirectory("attachments").toFile().also { directories += it })

    @After
    fun tearDown() {
        databases.forEach { it.close() }
        directories.forEach { it.deleteRecursively() }
    }

    private fun context(device: String = "A") = LocalCommandContext(farm, "worker-1", device, UUID.randomUUID().toString(), clock++)

    private suspend fun FarmOsDatabase.sheep(id: String) =
        animals().insert(AnimalEntity(id = id, farmId = farm, tag = "SH-$id", name = null, speciesCode = "sheep", sex = "FEMALE", status = "active", dateOfBirthEpochDay = null, updatedAtEpochMillis = 1L))

    @Test
    fun aPhotoIsKeptUnderItsHashAndItsMetadataIsJournalled(): Unit = runBlocking {
        val db = database()
        val store = store()
        db.sheep("s1")
        attachToAnimal(db, farm, store, "s1", photo, "image/jpeg", "Ewe ear tag.jpg", context())

        val row = db.attachments().forOwner(farm, "animal", "s1").single()
        assertEquals(sha256Hex(photo), row.contentSha256)
        assertEquals(2_048L, row.byteSize)
        assertEquals("Ewe ear tag.jpg", row.displayName)
        assertArrayEquals(photo, store.read(farm, row.contentSha256))
        val journalled = db.replication().operationsInRange(farm, "A", 1, 10)
        assertEquals(listOf(AttachmentCommands.ATTACH), journalled.map { it.operationType })

        // The same bytes attached again are stored once.
        attachToAnimal(db, farm, store, "s1", photo, "image/jpeg", "Copy.jpg", context())
        assertEquals(2, db.attachments().forOwner(farm, "animal", "s1").size)
        assertEquals(1, File(directories.single(), farm).listFiles()!!.size)
    }

    @Test
    fun aRefusedFileLeavesNothingBehind(): Unit = runBlocking {
        val db = database()
        val store = store()
        db.sheep("s1")
        assertThrows(IllegalStateException::class.java) { runBlocking { attachToAnimal(db, farm, store, "s1", photo, "text/plain", "notes.txt", context()) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { attachToAnimal(db, farm, store, "s1", ByteArray(0), "image/png", "empty.png", context()) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { attachToAnimal(db, farm, store, "missing", photo, "image/png", "p.png", context()) } }
        assertEquals(0, db.attachments().forOwner(farm, "animal", "s1").size)
        assertEquals(0L, db.replication().count(farm))
        assertFalse(store.has(farm, sha256Hex(ByteArray(0))))
        assertFalse(store.has(farm, sha256Hex(photo)))
        assertEquals("A file is at most 20 MB", AttachmentRules.attach(AttachFile("a", "animal", "s1", sha256Hex(photo), AttachmentRules.MAX_BYTES + 1, "image/png", "big.png")))
    }

    @Test
    fun anotherDeviceLearnsOfTheAttachmentBeforeItsBytes(): Unit = runBlocking {
        val aDb = database()
        val bDb = database("B")
        listOf(aDb, bDb).forEach { it.sheep("s1") }
        val a = RoomReplicaEndpoint(aDb, farm, "A", replicationAppliers).apply { registerPairedDevice("B", "B") }
        val b = RoomReplicaEndpoint(bDb, farm, "B", replicationAppliers).apply { registerPairedDevice("A", "A") }
        val aStore = store()
        val bStore = store()
        attachToAnimal(aDb, farm, aStore, "s1", photo, "image/png", "Lamb.png", context("A"))
        SyncSession.run(b, LocalPeerTransport(a), remoteDeviceId = "A")

        val onB = bDb.attachments().forOwner(farm, "animal", "s1").single()
        assertEquals(sha256Hex(photo), onB.contentSha256)
        // The metadata arrived; the bytes did not, so B shows it as available when connected.
        assertFalse(bStore.has(farm, onB.contentSha256))
        assertNull(bStore.read(farm, onB.contentSha256))
        assertTrue(aStore.has(farm, onB.contentSha256))

        // Replaying the same operation again changes nothing.
        SyncSession.run(b, LocalPeerTransport(a), remoteDeviceId = "A")
        assertEquals(1, bDb.attachments().forOwner(farm, "animal", "s1").size)
    }

    @Test
    fun damagedBytesReadAsMissingRatherThanAsTheWrongFile() {
        val store = store()
        val sha = store.put(farm, photo)
        File(File(directories.single(), farm), sha).writeBytes(byteArrayOf(1, 2, 3))
        assertNull(store.read(farm, sha))
        assertThrows(IllegalArgumentException::class.java) { store.read("../escape", sha) }
        assertThrows(IllegalArgumentException::class.java) { store.read(farm, "../../etc") }
    }

    @Test
    fun bytesFetchedFromAPairedDeviceAreKeptOnlyWhenTheyMatchTheRecord(): Unit = runBlocking {
        val aDb = database()
        val bDb = database("B")
        listOf(aDb, bDb).forEach { it.sheep("s1") }
        val a = RoomReplicaEndpoint(aDb, farm, "A", replicationAppliers).apply { registerPairedDevice("B", "B") }
        val b = RoomReplicaEndpoint(bDb, farm, "B", replicationAppliers).apply { registerPairedDevice("A", "A") }
        val aStore = store()
        val bStore = store()
        val scan = ByteArray(4_096) { (it % 7).toByte() }
        attachToAnimal(aDb, farm, aStore, "s1", photo, "image/png", "Lamb.png", context("A"))
        attachToAnimal(aDb, farm, aStore, "s1", scan, "application/pdf", "Scan.pdf", context("A"))
        SyncSession.run(b, LocalPeerTransport(a), remoteDeviceId = "A")

        // A peer that sends altered bytes, or none, leaves B without them.
        val served = aStore.source(farm)
        val tampered = pullMissingAttachments(bDb, farm, bStore) { sha, _ -> served.read(sha, 0, served.size(sha)!!.toInt())!!.also { it[0] = (it[0] + 1).toByte() } }
        assertEquals(0, tampered)
        assertEquals(0, pullMissingAttachments(bDb, farm, bStore) { _, _ -> null })
        assertFalse(bStore.has(farm, sha256Hex(photo)))

        // The genuine bytes are kept, once, and a second pull fetches nothing.
        var requests = 0
        val genuine: (String, Long) -> ByteArray? = { sha, max -> requests++; served.size(sha)?.takeIf { it <= max }?.let { served.read(sha, 0, it.toInt()) } }
        assertEquals(2, pullMissingAttachments(bDb, farm, bStore, fetch = genuine))
        assertArrayEquals(photo, bStore.read(farm, sha256Hex(photo)))
        assertArrayEquals(scan, bStore.read(farm, sha256Hex(scan)))
        assertEquals(0, pullMissingAttachments(bDb, farm, bStore, fetch = genuine))
        assertEquals(2, requests)
    }

    @Test
    fun theServedSourceReadsExactRangesAndNothingPastTheEnd() {
        val store = store()
        val sha = store.put(farm, photo)
        val source = store.source(farm)
        assertEquals(2_048L, source.size(sha))
        assertArrayEquals(photo.copyOfRange(1_000, 1_100), source.read(sha, 1_000, 100))
        assertNull(source.read(sha, 2_000, 100))
        assertNull(source.size("b".repeat(64)))
    }
}
