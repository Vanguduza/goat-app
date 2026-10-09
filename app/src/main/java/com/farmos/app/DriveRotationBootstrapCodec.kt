package com.farmos.app

import com.farmos.domain.replication.BundleVerdict
import com.farmos.core.database.COMMAND_PAYLOAD_KEY
import org.json.JSONObject
import com.farmos.domain.replication.OperationBundle
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyPair
import java.security.PublicKey
import java.security.Signature

/** This path carries a signed singleton rotation or device-enrolment prerequisite; no business data. */
internal val DRIVE_ROTATION_BOOTSTRAP_PATH = Regex(
    "^GOAT/farms/([A-Za-z0-9-]{1,64})/key-bootstrap/([A-Za-z0-9-]{1,64})/([A-Za-z0-9-]{1,64})/([0-9a-f]{64})\\.rotation$",
)

internal val DRIVE_KEY_BOOTSTRAP_COMMANDS = setOf(KEY_ROTATED_COMMAND, DEVICE_ENROLLED_COMMAND)

internal data class DriveRotationAttestation(
    val path: String,
    val signerId: String,
    val bundle: OperationBundle,
    val encodedBundle: ByteArray,
    val signature: ByteArray,
)

/**
 * Gateway attestation, matching the authenticated LAN relay boundary; not an origin-signature retrofit.
 * The trusted gateway signs the full exact admitted envelope, farm, signer and immutable object path.
 * Possession of an old shared farm key or a recipient's public key cannot forge this device signature.
 */
internal object DriveRotationBootstrapCodec {
    private val magic = "GOATKEY1".toByteArray(Charsets.US_ASCII)
    private val domain = "goat-drive-key-bootstrap-v1\u0000".toByteArray(Charsets.US_ASCII)

    fun path(farmId: String, signerId: String, wrappingKeyId: String, bundle: OperationBundle): String {
        requireRotation(bundle, farmId)
        return "GOAT/farms/$farmId/key-bootstrap/$signerId/$wrappingKeyId/" +
            sha256Hex(DriveBundleCodec.encode(bundle)) + ".rotation"
    }

    fun encode(path: String, signerId: String, identity: KeyPair, bundle: OperationBundle): ByteArray {
        val bytes = DriveBundleCodec.encode(bundle)
        requirePath(path, signerId, bundle, bytes)
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(identity.private)
            update(signedBytes(path, signerId, bundle.farmId, bytes))
            sign()
        }
        return ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { stream ->
                stream.write(magic)
                stream.writeUTF(signerId)
                stream.writeInt(bytes.size)
                stream.write(bytes)
                stream.writeInt(signature.size)
                stream.write(signature)
            }
        }.toByteArray()
    }

    fun decode(path: String, bytes: ByteArray, farmId: String): DriveRotationAttestation {
        require(bytes.size <= MAX_DRIVE_OBJECT_BYTES) { "Drive rotation bootstrap exceeds the permitted size" }
        return DataInputStream(bytes.inputStream()).use { stream ->
            require(ByteArray(magic.size).also(stream::readFully).contentEquals(magic)) { "Not a signed Drive key bootstrap" }
            val signer = stream.readUTF()
            val size = stream.readInt()
            require(size in 1..MAX_DRIVE_OBJECT_BYTES && size <= stream.available() - 4) { "Invalid key bootstrap bundle size" }
            val encoded = ByteArray(size).also(stream::readFully)
            val bundle = DriveBundleCodec.decode(encoded)
            requireRotation(bundle, farmId)
            requirePath(path, signer, bundle, encoded)
            val signatureSize = stream.readInt()
            require(signatureSize in 8..256 && signatureSize == stream.available()) { "Invalid key bootstrap signature size" }
            DriveRotationAttestation(path, signer, bundle, encoded, ByteArray(signatureSize).also(stream::readFully))
        }
    }

    fun authentic(attestation: DriveRotationAttestation, publicKey: PublicKey): Boolean =
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(signedBytes(attestation.path, attestation.signerId, attestation.bundle.farmId, attestation.encodedBundle))
            verify(attestation.signature)
        }

    private fun requireRotation(bundle: OperationBundle, farmId: String) {
        require(bundle.verify(farmId) == BundleVerdict.Valid && bundle.operations.size == 1) { "Key bootstrap needs one exact verified prerequisite" }
        val operation = bundle.operations.single()
        require((operation.operationType == KEY_ROTATED_COMMAND && operation.entityType == "farm_key") ||
            (operation.operationType == DEVICE_ENROLLED_COMMAND && operation.entityType == "farm_device")) {
            "Key bootstrap cannot carry business records"
        }
        val payload = JSONObject(operation.payload.getValue(COMMAND_PAYLOAD_KEY))
        val target = payload.getString(if (operation.operationType == KEY_ROTATED_COMMAND) "keyId" else "deviceId")
        require(operation.entityId == target) { "Key prerequisite does not match its applied entity" }
    }

    private fun requirePath(path: String, signerId: String, bundle: OperationBundle, bytes: ByteArray) {
        requireRotation(bundle, bundle.farmId)
        val match = requireNotNull(DRIVE_ROTATION_BOOTSTRAP_PATH.matchEntire(path)) { "Invalid Drive key bootstrap path" }
        require(match.groupValues[1] == bundle.farmId && match.groupValues[2] == signerId &&
            match.groupValues[4] == sha256Hex(bytes)) { "Key bootstrap does not match its farm, signer or exact bundle path" }
    }

    private fun signedBytes(path: String, signerId: String, farmId: String, bundle: ByteArray): ByteArray =
        ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { stream ->
                stream.write(domain)
                stream.writeUTF(farmId)
                stream.writeUTF(signerId)
                stream.writeUTF(path)
                stream.writeInt(bundle.size)
                stream.write(bundle)
            }
        }.toByteArray()
}
