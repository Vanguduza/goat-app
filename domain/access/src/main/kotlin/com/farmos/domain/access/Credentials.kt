package com.farmos.domain.access

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class CredentialKind { PIN, PASSWORD }

/** A secret as typed. It is hashed immediately and never stored or logged. */
class Credential(val kind: CredentialKind, val secret: String) {
    override fun toString(): String = "Credential($kind)"
}

object CredentialPolicy {
    const val PIN_MIN = 6
    const val PIN_MAX = 12
    const val PASSWORD_MIN = 10

    /** The reason [credential] is too weak, or null when it is acceptable. */
    fun problem(credential: Credential): String? = when (credential.kind) {
        CredentialKind.PIN -> when {
            credential.secret.length !in PIN_MIN..PIN_MAX || !credential.secret.all(Char::isDigit) ->
                "A PIN must be $PIN_MIN to $PIN_MAX digits"
            credential.secret.toSet().size == 1 -> "A PIN cannot repeat one digit"
            isRun(credential.secret) -> "A PIN cannot be a simple sequence"
            else -> null
        }
        CredentialKind.PASSWORD ->
            if (credential.secret.length < PASSWORD_MIN) "A password must be at least $PASSWORD_MIN characters" else null
    }

    /** True for a run of consecutive digits in one direction, such as 123456 or 876543. */
    private fun isRun(digits: String): Boolean {
        val steps = digits.zipWithNext { a, b -> b - a }.toSet()
        return steps == setOf(1) || steps == setOf(-1)
    }
}

/**
 * Salted PBKDF2-HMAC-SHA256 hashing. Stored form: `pbkdf2-sha256$<iterations>$<salt>$<hash>` (Base64), so a
 * stored hash stays verifiable if the iteration count later changes. Comparison is constant-time.
 */
class CredentialHasher(
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val random: SecureRandom = SecureRandom(),
) {
    fun hash(secret: String): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return encode(iterations, salt, derive(secret, salt, iterations))
    }

    fun verify(secret: String, stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 4 || parts[0] != SCHEME) return false
        val rounds = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return false
        val salt = runCatching { Base64.getDecoder().decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(parts[3]) }.getOrNull() ?: return false
        return MessageDigest.isEqual(expected, derive(secret, salt, rounds))
    }

    private fun derive(secret: String, salt: ByteArray, rounds: Int): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, rounds, HASH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(rounds: Int, salt: ByteArray, hash: ByteArray): String {
        val b64 = Base64.getEncoder()
        return "$SCHEME$$rounds$${b64.encodeToString(salt)}$${b64.encodeToString(hash)}"
    }

    companion object {
        /** OWASP-recommended work factor for PBKDF2-HMAC-SHA256. */
        const val DEFAULT_ITERATIONS = 600_000
        private const val SCHEME = "pbkdf2-sha256"
        private const val SALT_BYTES = 16
        private const val HASH_BITS = 256
    }
}

/**
 * Owner recovery code: 20 Crockford base32 characters (100 bits) shown once as `XXXX-XXXX-XXXX-XXXX-XXXX`.
 * Only its hash is stored. Input is normalised: case, spaces and dashes are ignored, and the
 * look-alikes O, I and L read as 0, 1 and 1.
 */
object RecoveryCode {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val LENGTH = 20

    fun generate(random: SecureRandom): String =
        (1..LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("").chunked(4).joinToString("-")

    fun normalise(input: String): String =
        input.uppercase().filterNot { it == '-' || it.isWhitespace() }
            .map { when (it) { 'O' -> '0'; 'I', 'L' -> '1'; else -> it } }
            .joinToString("")
}
