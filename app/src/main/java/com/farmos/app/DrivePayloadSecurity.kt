package com.farmos.app

import com.farmos.domain.replication.FarmCipher
import com.farmos.domain.replication.FarmKeyRing
import com.farmos.domain.replication.SealedPayload
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Encrypts every journal bundle and attachment before it leaves the device. Object identity is
 * authenticated as associated data. The raw Drive carrier never receives plaintext farm records.
 */
internal class EncryptedDriveStore(
    private val raw: DriveObjectStore,
    private val farmId: String,
    private val keys: () -> FarmKeyRing,
) : DriveObjectStore {
    override suspend fun validateDestination(accountEmail: String) = raw.validateDestination(accountEmail)

    override suspend fun list(prefix: String): List<DriveObjectStore.DriveObject> {
        require(prefix.startsWith("GOAT/farms/$farmId/")) { "Drive prefix belongs to another farm" }
        return raw.list(prefix)
    }

    override suspend fun read(path: String): ByteArray? {
        requireFarmPath(path)
        val sealed = raw.read(path) ?: return null
        val bytes = DrivePayloadCodec.open(keys(), path, sealed)
        requireDriveSafePayload(path, bytes)
        if (path.startsWith("GOAT/farms/$farmId/attachments/")) {
            require(sha256Hex(bytes) == path.substringAfterLast('/')) { "Drive attachment content hash does not match" }
        }
        return bytes
    }

    override suspend fun putIfAbsent(path: String, bytes: ByteArray, sha256: String): Boolean {
        requireFarmPath(path)
        requireDriveSafePayload(path, bytes)
        require(sha256Hex(bytes) == sha256) { "Drive plaintext checksum does not match" }
        if (path.startsWith("GOAT/farms/$farmId/attachments/")) {
            require(sha256 == path.substringAfterLast('/')) { "Drive attachment path does not match its content" }
        }
        // GCM uses random nonces. Compare the authenticated plaintext on retry, not ciphertext bytes.
        read(path)?.let { return it.contentEquals(bytes) }
        val sealed = DrivePayloadCodec.seal(keys(), path, bytes)
        if (raw.putIfAbsent(path, sealed, sha256Hex(sealed))) return true
        return read(path)?.contentEquals(bytes) == true
    }

    private fun requireFarmPath(path: String) {
        require(path.startsWith("GOAT/farms/$farmId/")) { "Drive object belongs to another farm" }
    }
}

internal object DrivePayloadCodec {
    private val MAGIC = "GOATENC1".toByteArray(Charsets.US_ASCII)

    fun seal(keys: FarmKeyRing, path: String, bytes: ByteArray): ByteArray {
        require(bytes.size <= MAX_DRIVE_OBJECT_BYTES - 1024) { "Drive payload exceeds the permitted size" }
        val sealed = FarmCipher.seal(keys.current, bytes, associatedData(path))
        return ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { stream ->
                stream.write(MAGIC)
                stream.writeUTF(sealed.keyId)
                stream.writeInt(sealed.nonce.size)
                stream.write(sealed.nonce)
                stream.writeInt(sealed.ciphertext.size)
                stream.write(sealed.ciphertext)
            }
        }.toByteArray()
    }

    fun open(keys: FarmKeyRing, path: String, bytes: ByteArray): ByteArray {
        require(bytes.size <= MAX_DRIVE_OBJECT_BYTES) { "Drive object exceeds the permitted size" }
        val sealed = DataInputStream(bytes.inputStream()).use { stream ->
            val magic = ByteArray(MAGIC.size).also(stream::readFully)
            require(magic.contentEquals(MAGIC)) { "Drive object is not an encrypted farm payload" }
            val keyId = stream.readUTF()
            val nonceSize = stream.readInt()
            require(nonceSize == 12) { "Invalid Drive payload nonce" }
            val nonce = ByteArray(nonceSize).also(stream::readFully)
            val length = stream.readInt()
            require(length in 16..MAX_DRIVE_OBJECT_BYTES && length == stream.available()) { "Invalid Drive payload size" }
            val ciphertext = ByteArray(length).also(stream::readFully)
            SealedPayload(keyId, nonce, ciphertext)
        }
        return FarmCipher.open(keys, sealed, associatedData(path))
    }

    private fun associatedData(path: String): ByteArray =
        ("goat-drive-payload-v1\u0000" + path).toByteArray(Charsets.UTF_8)
}
