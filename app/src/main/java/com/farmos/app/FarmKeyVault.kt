package com.farmos.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.farmos.domain.replication.DeviceKeys
import com.farmos.domain.replication.FarmDataKey
import com.farmos.domain.replication.FarmKeyRing
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

/** Seals bytes under a key that never leaves this device. */
internal interface DeviceSealer {
    fun seal(plaintext: ByteArray): ByteArray

    fun open(sealed: ByteArray): ByteArray
}

/** AES-256-GCM under a non-exportable Android Keystore key. */
internal class KeystoreSealer(private val alias: String = "goat_farm_vault_v1") : DeviceSealer {
    override fun seal(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return ByteBuffer.allocate(1 + Int.SIZE_BYTES + iv.size + ciphertext.size).put(FORMAT).putInt(iv.size).put(iv).put(ciphertext).array()
    }

    override fun open(sealed: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(sealed)
        require(buffer.get() == FORMAT) { "Unsupported vault format" }
        val iv = ByteArray(buffer.int.also { require(it in 12..32) { "Invalid vault payload" } }).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT: Byte = 1
    }
}

/** What this device holds for one farm: the farm key ring and its own key-agreement identity. */
internal class FarmSecrets(
    val keys: FarmKeyRing,
    val device: KeyPair,
    /** Business time of the rotation that made the current key current; a later rotation always wins. */
    val currentSinceEpochMillis: Long = 0,
)

/**
 * This device's farm keys and device identity, sealed with [sealer] in the no-backup files directory, so
 * they are excluded from cloud backups and unreadable without this device's Keystore. Keys are never
 * written unsealed and never leave the device except wrapped to another paired device.
 */
internal class FarmKeyVault(private val directory: File, private val sealer: DeviceSealer) {
    @Synchronized
    fun secrets(farmId: String): FarmSecrets? {
        val file = file(farmId).takeIf { it.exists() } ?: return null
        val json = JSONObject(String(sealer.open(file.readBytes()), Charsets.UTF_8))
        val keys = json.getJSONArray("keys")
        val ring = FarmKeyRing(
            List(keys.length()) { index -> keys.getJSONObject(index).let { FarmDataKey(it.getString("id"), decode(it.getString("material"))) } },
            json.getString("current"),
        )
        return FarmSecrets(
            ring,
            DeviceKeys.keyPair(decode(json.getString("devicePublic")), decode(json.getString("devicePrivate"))),
            json.optLong("currentSince", 0),
        )
    }

    /** Reads, changes and saves one farm's secrets atomically with respect to other vault calls. */
    @Synchronized
    fun update(farmId: String, change: (FarmSecrets) -> FarmSecrets?) {
        val current = secrets(farmId) ?: return
        change(current)?.let { save(farmId, it) }
    }

    @Synchronized
    fun save(farmId: String, secrets: FarmSecrets) {
        val json = JSONObject()
            .put("current", secrets.keys.currentKeyId)
            .put("keys", JSONArray(secrets.keys.all().map { JSONObject().put("id", it.keyId).put("material", encode(it.materialForVault())) }))
            .put("devicePublic", encode(DeviceKeys.encode(secrets.device.public)))
            .put("devicePrivate", encode(DeviceKeys.encodePrivate(secrets.device.private)))
            .put("currentSince", secrets.currentSinceEpochMillis)
        directory.mkdirs()
        val target = file(farmId)
        val temporary = File(directory, "${target.name}.tmp")
        temporary.writeBytes(sealer.seal(json.toString().toByteArray(Charsets.UTF_8)))
        check(temporary.renameTo(target)) { "The farm key vault could not be saved on this device" }
    }

    /** First keys for a farm created on this device: a fresh farm key and this device's identity. */
    @Synchronized
    fun provisionNewFarm(farmId: String): FarmSecrets {
        check(secrets(farmId) == null) { "This farm already has keys on this device" }
        return FarmSecrets(FarmKeyRing(listOf(FarmDataKey.generate(FIRST_KEY_ID)), FIRST_KEY_ID), DeviceKeys.generate()).also { save(farmId, it) }
    }

    /**
     * Secrets for a farm on this device. A device that created the farm has no pairing grant, so it
     * provisions the farm's first key when first needed; a device that joined saved its grant at pairing.
     */
    @Synchronized
    fun secretsForLocalFarm(farmId: String): FarmSecrets = secrets(farmId) ?: provisionNewFarm(farmId)

    /** This device's identity for a farm it is about to join, created before pairing so its key can be wrapped to. */
    @Synchronized
    fun joiningIdentity(farmId: String): KeyPair = pendingIdentities.getOrPut(farmId) { DeviceKeys.generate() }

    private val pendingIdentities = mutableMapOf<String, KeyPair>()

    private fun file(farmId: String): File {
        require(farmId.matches(SAFE_ID)) { "Invalid farm id" }
        return File(directory, "$farmId.vault")
    }

    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String) = Base64.getDecoder().decode(text)

    private companion object {
        const val FIRST_KEY_ID = "k1"
        val SAFE_ID = Regex("[A-Za-z0-9-]{1,64}")
    }
}
