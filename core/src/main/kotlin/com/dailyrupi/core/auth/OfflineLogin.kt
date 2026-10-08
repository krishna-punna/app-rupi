package com.dailyrupi.core.auth

import com.dailyrupi.core.net.CookieStorage
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Lets the last user log in while the server cannot be reached, checking the password against a
 * salted PBKDF2 hash made on the phone at their last online login. The password itself and the
 * server's own hash are never stored. The hash is forgotten on Log out, when the server refuses a
 * login, and after [MAX_ATTEMPTS] wrong passwords offline, so the next login has to be online.
 */
class OfflineLogin(
    /** Encrypted on the phone by the app, like the session cookies. */
    private val storage: CookieStorage,
    private val iterations: Int = ITERATIONS,
    private val random: SecureRandom = SecureRandom(),
) {
    sealed interface Result {
        data class Success(val username: String) : Result
        data class WrongPassword(val attemptsLeft: Int) : Result

        /** No online login on this phone to check against, for this username. */
        data object Unavailable : Result
    }

    /** After a successful online login. */
    @Synchronized
    fun remember(username: String, password: String) {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        save(Credential(username, encode(salt), encode(hash(password, salt, iterations)), iterations))
    }

    @Synchronized
    fun forget() = storage.write(null)

    @Synchronized
    fun isAvailable(username: String): Boolean = load()?.username.equals(username.trim(), ignoreCase = true)

    @Synchronized
    fun check(username: String, password: String): Result {
        val saved = load() ?: return Result.Unavailable
        if (!saved.username.equals(username.trim(), ignoreCase = true)) return Result.Unavailable
        val actual = hash(password, decode(saved.salt), saved.iterations)
        if (MessageDigest.isEqual(actual, decode(saved.hash))) {
            if (saved.failedAttempts != 0) save(saved.copy(failedAttempts = 0))
            return Result.Success(saved.username)
        }
        val failed = saved.failedAttempts + 1
        if (failed >= MAX_ATTEMPTS) {
            forget()
            return Result.WrongPassword(0)
        }
        save(saved.copy(failedAttempts = failed))
        return Result.WrongPassword(MAX_ATTEMPTS - failed)
    }

    private fun load(): Credential? =
        storage.read()?.let { runCatching { json.decodeFromString(Credential.serializer(), it) }.getOrNull() }

    private fun save(credential: Credential) = storage.write(json.encodeToString(Credential.serializer(), credential))

    @Serializable
    private data class Credential(
        val username: String,
        val salt: String,
        val hash: String,
        val iterations: Int,
        val failedAttempts: Int = 0,
    )

    companion object {
        const val MAX_ATTEMPTS = 5
        const val ITERATIONS = 120_000
        private const val SALT_BYTES = 16
        private const val HASH_BITS = 256
        private val json = Json { ignoreUnknownKeys = true }

        private fun hash(password: String, salt: ByteArray, iterations: Int): ByteArray {
            val spec = PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS)
            return try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
        }

        private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
        private fun decode(text: String) = Base64.getDecoder().decode(text)
    }
}
