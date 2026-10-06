package com.farmos.domain.replication

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * A farm data key (AES-256) named by [keyId]. Drive bundles, backups and sensitive attachments are
 * sealed under the farm's current key; older keys stay in the ring so history remains readable.
 */
class FarmDataKey(val keyId: String, material: ByteArray) {
    private val bytes = material.copyOf()

    init {
        require(keyId.isNotBlank()) { "A key id is required" }
        require(bytes.size == KEY_BYTES) { "A farm data key is 256 bits" }
    }

    internal fun material(): ByteArray = bytes.copyOf()

    /** The raw key, only for sealing into this device's key vault. Never log, send or store it unsealed. */
    fun materialForVault(): ByteArray = bytes.copyOf()

    override fun toString(): String = "FarmDataKey($keyId)"

    companion object {
        const val KEY_BYTES = 32

        fun generate(keyId: String, random: SecureRandom = SecureRandom()): FarmDataKey =
            FarmDataKey(keyId, ByteArray(KEY_BYTES).also(random::nextBytes))
    }
}

/** The farm keys this device holds. New payloads are sealed under [current]; older keys only open history. */
class FarmKeyRing(keys: Collection<FarmDataKey>, val currentKeyId: String) {
    private val keys = keys.associateBy { it.keyId }

    init {
        require(currentKeyId in this.keys) { "The current key must be in the ring" }
    }

    val current: FarmDataKey get() = keys.getValue(currentKeyId)

    val keyIds: Set<String> get() = keys.keys

    fun key(keyId: String): FarmDataKey? = keys[keyId]

    fun all(): List<FarmDataKey> = keys.values.sortedBy { it.keyId }

    /** Adds a fresh key and makes it current, so a revoked device cannot read anything sealed afterwards. */
    fun rotate(newKeyId: String, random: SecureRandom = SecureRandom()): FarmKeyRing {
        require(newKeyId !in keys) { "Key ids are never reused" }
        return FarmKeyRing(keys.values + FarmDataKey.generate(newKeyId, random), newKeyId)
    }
}

/** A payload sealed with AES-256-GCM under the farm key [keyId]. */
class SealedPayload(val keyId: String, nonce: ByteArray, ciphertext: ByteArray) {
    val nonce: ByteArray = nonce.copyOf()
    val ciphertext: ByteArray = ciphertext.copyOf()
}

/** The farm key a sealed payload needs is not on this device, for example after this device was revoked. */
class FarmKeyUnavailable(keyId: String) : IllegalStateException("Farm key $keyId is not on this device")

object FarmCipher {
    /** Seals [plaintext]; [associatedData] (farm, bundle identity) is authenticated so a payload cannot be moved. */
    fun seal(key: FarmDataKey, plaintext: ByteArray, associatedData: ByteArray, random: SecureRandom = SecureRandom()): SealedPayload {
        val nonce = ByteArray(GCM_NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.material(), "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(associatedData)
        return SealedPayload(key.keyId, nonce, cipher.doFinal(plaintext))
    }

    /** Opens [sealed] with the matching key from [ring]; tampering or the wrong associated data fails. */
    fun open(ring: FarmKeyRing, sealed: SealedPayload, associatedData: ByteArray): ByteArray {
        val key = ring.key(sealed.keyId) ?: throw FarmKeyUnavailable(sealed.keyId)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.material(), "AES"), GCMParameterSpec(GCM_TAG_BITS, sealed.nonce))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(sealed.ciphertext)
    }
}

/** A device's key-agreement identity (EC P-256). The private half stays on the device, in the Keystore on Android. */
object DeviceKeys {
    fun generate(random: SecureRandom = SecureRandom()): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), random) }.generateKeyPair()

    /** X.509 SubjectPublicKeyInfo encoding, safe to show, advertise and hash. */
    fun encode(publicKey: PublicKey): ByteArray = publicKey.encoded

    fun decode(encoded: ByteArray): PublicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))

    /** Short, stable fingerprint of a public key, used to name a farm's pairing identity. */
    fun fingerprint(encoded: ByteArray): String = Sha256.hex(encoded).take(FINGERPRINT_HEX)

    /**
     * The farm's pairing fingerprint, the same on every farm device. It is public by design: pairing is
     * protected by binding the new device's own key into the code the approver types, not by this value.
     */
    fun farmPairingFingerprint(farmId: String): String = Sha256.hex("goat-farm-pairing-v1|$farmId").take(FINGERPRINT_HEX)

    fun encodePrivate(privateKey: PrivateKey): ByteArray = privateKey.encoded

    fun keyPair(encodedPublic: ByteArray, encodedPrivate: ByteArray): KeyPair {
        val factory = KeyFactory.getInstance("EC")
        return KeyPair(factory.generatePublic(X509EncodedKeySpec(encodedPublic)), factory.generatePrivate(PKCS8EncodedKeySpec(encodedPrivate)))
    }
}

/** A farm data key encrypted to one device: only that device's private key can recover it. */
class WrappedFarmKey(val keyId: String, ephemeralPublicKey: ByteArray, nonce: ByteArray, ciphertext: ByteArray) {
    val ephemeralPublicKey: ByteArray = ephemeralPublicKey.copyOf()
    val nonce: ByteArray = nonce.copyOf()
    val ciphertext: ByteArray = ciphertext.copyOf()
}

/**
 * Wraps farm data keys to a device with ephemeral ECDH (P-256), HKDF-SHA256 and AES-256-GCM. The farm,
 * device and key ids are bound into the derivation and authenticated, so a wrapped key cannot be
 * replayed to another device or farm.
 */
object FarmKeyWrap {
    fun wrap(key: FarmDataKey, recipient: PublicKey, farmId: String, deviceId: String, random: SecureRandom = SecureRandom()): WrappedFarmKey {
        val ephemeral = DeviceKeys.generate(random)
        val context = context(farmId, deviceId, key.keyId)
        val wrappingKey = derive(ephemeral.private, recipient, DeviceKeys.encode(ephemeral.public), DeviceKeys.encode(recipient), context)
        val nonce = ByteArray(GCM_NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(wrappingKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(context)
        return WrappedFarmKey(key.keyId, DeviceKeys.encode(ephemeral.public), nonce, cipher.doFinal(key.material()))
    }

    fun unwrap(wrapped: WrappedFarmKey, recipient: KeyPair, farmId: String, deviceId: String): FarmDataKey {
        val context = context(farmId, deviceId, wrapped.keyId)
        val ephemeral = DeviceKeys.decode(wrapped.ephemeralPublicKey)
        val wrappingKey = derive(recipient.private, ephemeral, wrapped.ephemeralPublicKey, DeviceKeys.encode(recipient.public), context)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(wrappingKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, wrapped.nonce))
        cipher.updateAAD(context)
        return FarmDataKey(wrapped.keyId, cipher.doFinal(wrapped.ciphertext))
    }

    private fun context(farmId: String, deviceId: String, keyId: String): ByteArray =
        canonical(listOf("goat-farm-key-wrap-v1", farmId, deviceId, keyId))

    private fun derive(own: PrivateKey, peer: PublicKey, ephemeralPublic: ByteArray, recipientPublic: ByteArray, info: ByteArray): ByteArray {
        val shared = KeyAgreement.getInstance("ECDH").apply {
            init(own)
            doPhase(peer, true)
        }.generateSecret()
        return Hkdf.sha256(shared, salt = ephemeralPublic + recipientPublic, info = info, length = FarmDataKey.KEY_BYTES)
    }
}

/** HKDF (RFC 5869) with HMAC-SHA256. */
internal object Hkdf {
    fun sha256(inputKeyMaterial: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..255 * HASH_BYTES) { "HKDF output length out of range" }
        val prk = hmac(if (salt.isEmpty()) ByteArray(HASH_BYTES) else salt, inputKeyMaterial)
        val output = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            previous = hmac(prk, previous + info + byteArrayOf(counter.toByte()))
            val take = minOf(HASH_BYTES, length - offset)
            previous.copyInto(output, offset, 0, take)
            offset += take
            counter++
        }
        return output
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)

    private const val HASH_BYTES = 32
}

/** Length-prefixed concatenation so that no two different field lists share an encoding. */
internal fun canonical(fields: List<String>): ByteArray =
    buildString { fields.forEach { append(it.length).append(':').append(it).append('|') } }.toByteArray(Charsets.UTF_8)

private const val AES_GCM = "AES/GCM/NoPadding"
private const val GCM_NONCE_BYTES = 12
private const val GCM_TAG_BITS = 128
private const val FINGERPRINT_HEX = 32
