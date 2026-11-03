package dev.nilsschlegel.forum.accounts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private const val HASH_ITERATIONS = 600_000
private const val HASH_BITS = 256
private const val SALT_BYTES = 16
private const val TOKEN_BYTES = 32

/** Random text for secrets and for a hash to check against when a user does not exist. */
internal fun randomToken(): String {
    val bytes = ByteArray(TOKEN_BYTES)
    SecureRandom().nextBytes(bytes)
    return Base64.getEncoder().encodeToString(bytes)
}

internal fun newPasswordHash(password: String): String {
    val salt = ByteArray(SALT_BYTES)
    SecureRandom().nextBytes(salt)
    val encoder = Base64.getEncoder()
    return "$HASH_ITERATIONS:${encoder.encodeToString(salt)}:${encoder.encodeToString(derive(password, salt, HASH_ITERATIONS))}"
}

internal suspend fun hashPassword(password: String): String = withContext(Dispatchers.Default) { newPasswordHash(password) }

internal suspend fun verifyPassword(
    password: String,
    stored: String,
): Boolean =
    withContext(Dispatchers.Default) {
        val (iterations, salt, hash) = stored.split(':')
        val decoder = Base64.getDecoder()
        MessageDigest.isEqual(decoder.decode(hash), derive(password, decoder.decode(salt), iterations.toInt()))
    }

private fun derive(
    password: String,
    salt: ByteArray,
    iterations: Int,
): ByteArray =
    SecretKeyFactory
        .getInstance("PBKDF2WithHmacSHA256")
        .generateSecret(PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS))
        .encoded
