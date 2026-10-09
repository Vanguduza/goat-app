package com.farmos.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.domain.replication.DeviceKeys
import com.farmos.domain.replication.FarmCipher
import java.io.File
import java.nio.file.Files
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The farm key vault stores keys only sealed, restores them exactly, and never re-provisions a farm. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class FarmKeyVaultTest {
    private val directory: File = Files.createTempDirectory("vault").toFile()

    /** Stand-in for the Android Keystore sealer, which the JVM test runtime does not provide. */
    private class SoftwareSealer : DeviceSealer {
        private val key = ByteArray(32).also(SecureRandom()::nextBytes)

        override fun seal(plaintext: ByteArray): ByteArray {
            val iv = ByteArray(12).also(SecureRandom()::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv)) }
            return iv + cipher.doFinal(plaintext)
        }

        override fun open(sealed: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, sealed.copyOfRange(0, 12)))
            return cipher.doFinal(sealed.copyOfRange(12, sealed.size))
        }
    }

    private val vault = testFarmKeyVault(directory, SoftwareSealer())

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun aProvisionedFarmRestoresItsKeysAndIdentityExactlyAndIsStoredOnlySealed() {
        val farmId = "11111111-1111-4111-8111-111111111111"
        assertNull(vault.secrets(farmId))
        val provisioned = vault.provisionNewFarm(farmId)
        val restored = vault.secrets(farmId)!!

        assertEquals("k1", restored.keys.currentKeyId)
        assertArrayEquals(provisioned.keys.current.materialForVault(), restored.keys.current.materialForVault())
        assertArrayEquals(DeviceKeys.encode(provisioned.device.public), DeviceKeys.encode(restored.device.public))
        val sealed = FarmCipher.seal(provisioned.keys.current, "weights".toByteArray(), byteArrayOf(1))
        assertEquals("weights", String(FarmCipher.open(restored.keys, sealed, byteArrayOf(1))))

        val stored = File(directory, "$farmId.vault").readBytes()
        val material = provisioned.keys.current.materialForVault()
        assertFalse(stored.toList().windowed(material.size).any { it == material.toList() })
        assertThrows(IllegalStateException::class.java) { vault.provisionNewFarm(farmId) }
        assertArrayEquals(material, vault.requireSecrets(farmId).keys.current.materialForVault())
    }

    @Test
    fun rotatedKeyRingsAreSavedWithTheirHistory() {
        val farmId = "farm-2"
        val secrets = vault.provisionNewFarm(farmId)
        vault.save(farmId, FarmSecrets(secrets.keys.rotate("k2"), secrets.device))
        val restored = vault.secrets(farmId)!!
        assertEquals("k2", restored.keys.currentKeyId)
        assertEquals(setOf("k1", "k2"), restored.keys.keyIds)
    }

    @Test
    fun farmIdsCannotEscapeTheVaultDirectory() {
        assertThrows(IllegalArgumentException::class.java) { vault.secrets("../escape") }
    }
}
