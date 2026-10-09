package com.farmos.app

import com.farmos.domain.replication.DriveJournalLayout
import com.farmos.domain.replication.FarmDataKey
import com.farmos.domain.replication.FarmKeyRing
import com.farmos.domain.replication.MergeClass
import com.farmos.domain.replication.OperationBundle
import com.farmos.domain.replication.OperationEnvelope
import com.farmos.domain.replication.SequenceRange
import com.farmos.domain.replication.SyncVector
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DriveTransportSecurityTest {
    private val farm = "farm-a"
    private val key = FarmDataKey("key-1", ByteArray(32) { 17 })
    private val keys = FarmKeyRing(listOf(key), key.keyId)

    private class MemoryStore : DriveObjectStore {
        val objects = linkedMapOf<String, ByteArray>()
        override suspend fun list(prefix: String) = objects.filterKeys { it.startsWith(prefix) }.map { (path, bytes) ->
            DriveObjectStore.DriveObject(path, bytes.size.toLong(), sha256Hex(bytes))
        }
        override suspend fun read(path: String) = objects[path]?.copyOf()
        override suspend fun putIfAbsent(path: String, bytes: ByteArray, sha256: String): Boolean {
            val old = objects[path]
            if (old != null) return old.contentEquals(bytes)
            objects[path] = bytes.copyOf()
            return true
        }
    }

    private fun bundle(device: String = "phone", sequence: Long = 1, value: String = "sensitive farm record"): OperationBundle =
        OperationBundle.seal(farm, device, listOf(OperationEnvelope.seal(
            operationId = "op-$device-$sequence", farmId = farm, entityType = "animal", entityId = "animal-1",
            actorId = "owner", deviceId = device, deviceSequence = sequence,
            businessTimeEpochMillis = sequence, createdAtEpochMillis = sequence, baseVersion = null,
            operationType = "goat.record_weight.v1", mergeClass = MergeClass.APPEND_ONLY_EVENT,
            payload = mapOf("value" to value), schemaVersion = 1, provenance = "local",
        )))

    @Test
    fun encryptedUploadsDoNotExposeFarmRecordsAndRetriesKeepTheOriginalObject(): Unit = runBlocking {
        val raw = MemoryStore()
        val store = EncryptedDriveStore(raw, farm) { keys }
        val transport = DriveReplicationTransport(store, farm)
        val bundle = bundle()
        transport.publish(farm, listOf(bundle))
        val path = DriveJournalLayout.bundlePath(farm, bundle)
        val first = raw.objects.getValue(path).copyOf()
        assertFalse(first.toString(Charsets.UTF_8).contains("sensitive farm record"))
        transport.publish(farm, listOf(bundle))
        assertTrue(first.contentEquals(raw.objects.getValue(path)))
        assertEquals(bundle, transport.fetch(farm, listOf(SequenceRange("phone", 1, 1))).single())
        assertEquals(1L, transport.remoteVector(farm).watermark("phone"))
    }

    @Test
    fun attachmentEncryptionUsesPlaintextContentIdentityAndAuthenticatesThePath(): Unit = runBlocking {
        val raw = MemoryStore()
        val store = EncryptedDriveStore(raw, farm) { keys }
        val bytes = "animal photo bytes".toByteArray()
        val hash = sha256Hex(bytes)
        val path = "GOAT/farms/$farm/attachments/$hash"
        assertTrue(store.putIfAbsent(path, bytes, hash))
        assertFalse(raw.objects.getValue(path).contentEquals(bytes))
        assertTrue(store.read(path)!!.contentEquals(bytes))
        val moved = "GOAT/farms/$farm/attachments/" + "a".repeat(64)
        raw.objects[moved] = raw.objects.getValue(path)
        assertFails { store.read(moved) }
        assertFails { store.read("GOAT/farms/farm-b/attachments/$hash") }
    }

    @Test
    fun tamperingAndPlaintextBlobsCannotAdvanceBackupWatermarks(): Unit = runBlocking {
        val raw = MemoryStore()
        val store = EncryptedDriveStore(raw, farm) { keys }
        val transport = DriveReplicationTransport(store, farm)
        val bundle = bundle()
        transport.publish(farm, listOf(bundle))
        val path = DriveJournalLayout.bundlePath(farm, bundle)
        val original = raw.objects.getValue(path).copyOf()
        val altered = original.copyOf()
        altered[altered.lastIndex] = (altered.last().toInt() xor 1).toByte()
        raw.objects[path] = altered
        assertFails { transport.remoteVector(farm) }
        raw.objects[path] = DriveBundleCodec.encode(bundle)
        assertFails { transport.remoteVector(farm) }
    }

    @Test
    fun rotatedKeysProtectLaterObjectsAndKeepOldHistoryReadable() {
        val fresh = keys.rotate("key-2")
        val path = "GOAT/farms/$farm/attachments/" + "b".repeat(64)
        val old = DrivePayloadCodec.seal(keys, path, byteArrayOf(1, 2, 3))
        val new = DrivePayloadCodec.seal(fresh, path, byteArrayOf(4, 5, 6))
        assertTrue(DrivePayloadCodec.open(fresh, path, old).contentEquals(byteArrayOf(1, 2, 3)))
        assertFails { DrivePayloadCodec.open(keys, path, new) }
    }

    @Test
    fun aValidBundleStoredUnderTheWrongRangeIsRejected(): Unit = runBlocking {
        val raw = MemoryStore()
        val store = EncryptedDriveStore(raw, farm) { keys }
        val bundle = bundle()
        val wrong = "GOAT/farms/$farm/sync/phone/000000000001-000000000002.bundle"
        val bytes = DriveBundleCodec.encode(bundle)
        store.putIfAbsent(wrong, bytes, sha256Hex(bytes))
        assertFails { DriveReplicationTransport(store, farm).remoteVector(farm) }
    }

    @Test
    fun aGapNeverClaimsThatMissingOperationsAreBackedUp(): Unit = runBlocking {
        val store = EncryptedDriveStore(MemoryStore(), farm) { keys }
        val transport = DriveReplicationTransport(store, farm)
        transport.publish(farm, listOf(bundle(sequence = 2)))
        assertEquals(0L, transport.remoteVector(farm).watermark("phone"))
        transport.publish(farm, listOf(bundle(sequence = 1)))
        assertEquals(2L, transport.remoteVector(farm).watermark("phone"))
    }

    @Test
    fun changedOrMissingDestinationsCannotReusePreviousBackupClaims() {
        val a = DriveGatewayConfig("owner@example.com", "folder-a", "Farm", 1)
        val b = a.copy(folderId = "folder-b")
        val c = a.copy(accountEmail = "another@example.com")
        assertTrue(driveCursorScope(farm, a) != driveCursorScope(farm, b))
        assertTrue(driveCursorScope(farm, a) != driveCursorScope(farm, c))
        assertTrue(driveCursorScope(farm, a) != driveCursorScope("farm-b", a))
        assertEquals(DriveOpState.BACKED_UP, driveOpStateFor("phone", 1, confirmedDriveCursor(SyncVector(mapOf("phone" to 1)))))
        assertEquals(DriveOpState.LOCAL_ONLY, driveOpStateFor("phone", 1, confirmedDriveCursor(SyncVector())))
    }

    @Test
    fun folderQueriesUseTheActualIdAndDownloadsAreBounded() {
        val folder = { "folder-actual_1" }
        assertEquals("'folder-actual_1' in parents and trashed = false", driveParentQuery(folder()))
        assertFails { driveParentQuery("bad' or trashed = true") }
        assertFails { readDriveBytes(ByteArray(9).inputStream(), 8) }
        assertEquals(8, readDriveBytes(ByteArray(8).inputStream(), 8).size)
    }

    @Test
    fun metadataOnlyAndTrailingBytesCannotBeTreatedAsAValidBackup(): Unit = runBlocking {
        val raw = MemoryStore()
        val path = DriveJournalLayout.bundlePath(farm, bundle())
        raw.objects[path] = byteArrayOf()
        assertFails { DriveReplicationTransport(EncryptedDriveStore(raw, farm) { keys }, farm).remoteVector(farm) }
        assertFails { DriveBundleCodec.decode(DriveBundleCodec.encode(bundle()) + byteArrayOf(0)) }
    }
}
