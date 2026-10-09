package com.farmos.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Explicit AES test vault for local setup; it does not stand in for Android Keystore acceptance. */
internal fun localAccessTestVault(directory: File = File(
    ApplicationProvider.getApplicationContext<Context>().cacheDir, "access-test-vault-" + UUID.randomUUID(),
), sealer: DeviceSealer = LocalAccessTestSealer()) = testFarmKeyVault(directory, sealer)

internal class LocalAccessTestSealer : DeviceSealer {
    private val key = ByteArray(32).also(SecureRandom()::nextBytes)

    override fun seal(plaintext: ByteArray): ByteArray {
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return iv + cipher.doFinal(plaintext)
    }

    override fun open(sealed: ByteArray): ByteArray {
        require(sealed.size >= 28) { "Invalid sealed test vault" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, sealed.copyOfRange(0, 12)))
        return cipher.doFinal(sealed.copyOfRange(12, sealed.size))
    }
}
