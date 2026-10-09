package com.farmos.app

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS", application = Application::class)
class FarmKeyRotationOrderingTest {
    @Test
    fun aClockBehindTheCurrentKeyRefusesBeforeChangingAnyState(): Unit = runBlocking {
        KeyRotationTestFixture().use { f ->
            f.rotate(5_000)
            assertRefusedWithoutChanges(f, 1_000)
            val latest = requireNotNull(f.vault.secrets(f.farm))
            assertEquals(5_000L, latest.currentSinceEpochMillis)
            val receipt = f.committedRotation(latest.keys.currentKeyId)
            assertEquals(5_000L, receipt.businessTimeEpochMillis)
            assertEquals(5_000L, receipt.createdAtEpochMillis)
        }
    }

    @Test
    fun aSecondLocalRotationAtTheSameBusinessTimeRefusesWithoutFalseSuccess(): Unit = runBlocking {
        KeyRotationTestFixture().use { f ->
            f.rotate(5_000)
            assertRefusedWithoutChanges(f, 5_000)
            assertEquals(2, requireNotNull(f.vault.secrets(f.farm)).keys.keyIds.size)
            // A later user attempt retains its exact supplied business time and can complete.
            f.rotate(5_001)
            val latest = requireNotNull(f.vault.secrets(f.farm))
            assertEquals(5_001L, f.committedRotation(latest.keys.currentKeyId).businessTimeEpochMillis)
            assertEquals(3, latest.keys.keyIds.size)
        }
    }

    @Test
    fun theLargestOrderingTimeCannotWrapOrStageAnotherKey(): Unit = runBlocking {
        KeyRotationTestFixture().use { f ->
            val before = requireNotNull(f.vault.secrets(f.farm))
            f.vault.save(f.farm, FarmSecrets(before.keys, before.device, Long.MAX_VALUE))
            assertRefusedWithoutChanges(f, Long.MAX_VALUE)
        }
    }

    private suspend fun assertRefusedWithoutChanges(f: KeyRotationTestFixture, at: Long) {
        val path = java.io.File(f.vaultDirectory, "${f.farm}.vault")
        val sealed = path.readBytes()
        val writes = f.sealer.sealCalls.get()
        val operations = f.database.replication().count(f.farm)
        val before = requireNotNull(f.vault.secrets(f.farm))
        try {
            f.rotate(at)
            fail("A non-advancing business time must not report a completed rotation")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("rotation was not started"))
        }
        assertEquals(writes, f.sealer.sealCalls.get())
        assertEquals(operations, f.database.replication().count(f.farm))
        assertArrayEquals(sealed, path.readBytes())
        val retained = requireNotNull(f.vault.secrets(f.farm))
        assertEquals(before.keys.keyIds, retained.keys.keyIds)
        assertEquals(before.keys.currentKeyId, retained.keys.currentKeyId)
        assertTrue(retained.pendingRotations.isEmpty())
    }
}
