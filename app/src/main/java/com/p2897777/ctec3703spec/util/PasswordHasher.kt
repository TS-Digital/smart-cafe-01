package com.p2897777.ctec3703spec.util

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Passwords are never stored. We store a random per-user salt and a PBKDF2 hash.
 * PBKDF2 is deliberately slow so a stolen database is expensive to brute force.
 */
object PasswordHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    data class Hashed(val hash: String, val salt: String)

    fun hash(password: String, salt: ByteArray = newSalt()): Hashed {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
            return Hashed(encode(bytes), encode(salt))
        } finally {
            spec.clearPassword()
        }
    }

    fun verify(password: String, storedHash: String, storedSalt: String): Boolean {
        val candidate = hash(password, decode(storedSalt)).hash
        // Constant-time comparison so response time does not leak how much matched
        return MessageDigest.isEqual(decode(candidate), decode(storedHash))
    }

    private fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    private fun encode(b: ByteArray): String = Base64.getEncoder().encodeToString(b)
    private fun decode(s: String): ByteArray = Base64.getDecoder().decode(s)
}
