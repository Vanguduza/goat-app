package com.farmos.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.farmos.core.network.AuthUser
import com.farmos.core.network.SupabaseSession
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

internal data class RestoredSession(
    val session: SupabaseSession,
    val expiresAtEpochMillis: Long,
)

internal class SecureSessionPersistence(
    context: Context,
    supabaseUrl: String,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val backendFingerprint = fingerprint(supabaseUrl)

    fun save(session: SupabaseSession, expiresAtEpochMillis: Long) {
        val payload = JSONObject()
            .put("backend", backendFingerprint)
            .put("access_token", session.accessToken)
            .put("refresh_token", session.refreshToken)
            .put("expires_at_epoch_ms", expiresAtEpochMillis)
            .put("user_id", session.user.id)
            .toString()
            .toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(payload)
        val iv = cipher.iv
        val packed = ByteBuffer.allocate(1 + Int.SIZE_BYTES + iv.size + encrypted.size)
            .put(FORMAT_VERSION)
            .putInt(iv.size)
            .put(iv)
            .put(encrypted)
            .array()

        preferences.edit()
            .putString(SESSION_BLOB, Base64.encodeToString(packed, Base64.NO_WRAP))
            .commit()
    }

    fun load(): RestoredSession? {
        val encoded = preferences.getString(SESSION_BLOB, null) ?: return null
        return runCatching {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(packed)
            require(buffer.get() == FORMAT_VERSION) { "Unsupported secure-session format" }
            val ivSize = buffer.int
            require(ivSize in 12..32 && buffer.remaining() > ivSize) { "Invalid secure-session payload" }
            val iv = ByteArray(ivSize)
            buffer.get(iv)
            val encrypted = ByteArray(buffer.remaining())
            buffer.get(encrypted)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            val payload = JSONObject(cipher.doFinal(encrypted).toString(Charsets.UTF_8))
            require(payload.getString("backend") == backendFingerprint) { "Session belongs to another backend" }

            val expiry = payload.getLong("expires_at_epoch_ms")
            RestoredSession(
                session = SupabaseSession(
                    accessToken = payload.getString("access_token"),
                    refreshToken = payload.getString("refresh_token"),
                    expiresInSeconds = ((expiry - System.currentTimeMillis()) / 1_000L).coerceAtLeast(0L),
                    user = AuthUser(payload.getString("user_id")),
                ),
                expiresAtEpochMillis = expiry,
            )
        }.getOrElse {
            clear()
            null
        }
    }

    fun clear() {
        preferences.edit().remove(SESSION_BLOB).commit()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFERENCES_NAME = "farm_os_secure_session"
        private const val SESSION_BLOB = "session_blob_v1"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "farm_os_supabase_session_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val FORMAT_VERSION: Byte = 1

        private fun fingerprint(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.trimEnd('/').toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
