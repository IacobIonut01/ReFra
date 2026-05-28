package com.dot.gallery.feature_node.presentation.vault.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dot.gallery.core.activeDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** The type of custom authentication set for a vault. */
enum class VaultAuthType { PIN, PATTERN, PASSWORD }

/** How the vault selector screen is protected (gate). */
enum class GateMode { NONE, DEVICE, CUSTOM }

/** Result of a [VaultPasswordManager.verifyPassword] call. */
sealed interface VerifyResult {
    /** Secret matched. */
    data object Success : VerifyResult
    /** Secret did not match. [attemptsLeft] before lockout (0 = just locked out). */
    data class Failed(val attemptsLeft: Int) : VerifyResult
    /** Too many consecutive failures. Try again after [cooldownMs] milliseconds. */
    data class LockedOut(val cooldownMs: Long) : VerifyResult
}

/**
 * Manages per-vault custom authentication (PIN / Pattern / Password) using DataStore.
 * Credentials are stored as PBKDF2-hashed secrets: `"type:pbkdf2:salt:hash"`.
 * Legacy SHA-256 format (`"type:salt:hash"`) is still accepted for verification
 * and transparently upgraded on the next successful [verifyPassword] call.
 * When no custom auth is set, device biometric/credential auth is used.
 */
object VaultPasswordManager {

    /** Fixed UUID used to store the global gate authentication credentials. */
    val GATE_UUID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

    /** Fixed UUID used to store private folder authentication credentials. */
    val PRIVATE_FOLDER_UUID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private val GATE_MODE_KEY = stringPreferencesKey("vault_gate_mode")
    private val PRIVATE_FOLDER_MODE_KEY = stringPreferencesKey("private_folder_gate_mode")

    suspend fun getGateMode(context: Context): GateMode {
        val raw = context.activeDataStore.data.first()[GATE_MODE_KEY]
        return raw?.let { runCatching { GateMode.valueOf(it) }.getOrNull() } ?: GateMode.NONE
    }

    suspend fun setGateMode(context: Context, mode: GateMode) {
        context.activeDataStore.edit { it[GATE_MODE_KEY] = mode.name }
    }

    suspend fun getPrivateFolderMode(context: Context): GateMode {
        val raw = context.activeDataStore.data.first()[PRIVATE_FOLDER_MODE_KEY]
        return raw?.let { runCatching { GateMode.valueOf(it) }.getOrNull() } ?: GateMode.NONE
    }

    suspend fun setPrivateFolderMode(context: Context, mode: GateMode) {
        context.activeDataStore.edit { it[PRIVATE_FOLDER_MODE_KEY] = mode.name }
    }

    private const val SALT_LENGTH = 16
    private const val PBKDF2_ITERATIONS = 120_000
    private const val PBKDF2_KEY_LENGTH = 256
    private const val HASH_ALGORITHM = "pbkdf2"

    /** Max consecutive wrong attempts before lockout kicks in. */
    private const val MAX_ATTEMPTS = 5
    /** Base cooldown in ms after MAX_ATTEMPTS failures (30 seconds). */
    private const val BASE_COOLDOWN_MS = 30_000L

    private fun attemptsKeyFor(vaultUuid: UUID) =
        intPreferencesKey("vault_attempts_${vaultUuid}")
    private fun lockoutKeyFor(vaultUuid: UUID) =
        longPreferencesKey("vault_lockout_until_${vaultUuid}")

    private fun keyFor(vaultUuid: UUID) =
        stringPreferencesKey("vault_password_${vaultUuid}")

    /** Returns true if any custom auth has been set for the given vault. */
    suspend fun hasCustomPassword(context: Context, vaultUuid: UUID): Boolean {
        val key = keyFor(vaultUuid)
        return context.activeDataStore.data.map { prefs -> prefs[key] != null }.first()
    }

    /** Returns the auth type for the given vault, or null if none is set. */
    suspend fun getAuthType(context: Context, vaultUuid: UUID): VaultAuthType? {
        val key = keyFor(vaultUuid)
        val stored = context.activeDataStore.data.map { prefs -> prefs[key] }.first() ?: return null
        val typePart = stored.substringBefore(":")
        return runCatching { VaultAuthType.valueOf(typePart) }.getOrNull()
    }

    /** Sets custom authentication for the vault with the given [type] and [secret]. */
    suspend fun setPassword(
        context: Context,
        vaultUuid: UUID,
        secret: String,
        type: VaultAuthType = VaultAuthType.PASSWORD
    ) {
        val key = keyFor(vaultUuid)
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2Hash(secret, salt)
        val stored = "${type.name}:$HASH_ALGORITHM:${salt.toHex()}:${hash.toHex()}"
        context.activeDataStore.edit { prefs -> prefs[key] = stored }
    }

    /** Removes custom auth, reverting to device security. */
    suspend fun removePassword(context: Context, vaultUuid: UUID) {
        val key = keyFor(vaultUuid)
        context.activeDataStore.edit { prefs -> prefs.remove(key) }
    }

    /** Returns remaining lockout time in ms, or 0 if not locked out. */
    suspend fun getRemainingLockout(context: Context, vaultUuid: UUID): Long {
        val lockoutUntil = context.activeDataStore.data.map { prefs ->
            prefs[lockoutKeyFor(vaultUuid)] ?: 0L
        }.first()
        return (lockoutUntil - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    /** Verifies the entered [secret] against the stored hash.
     *  Returns [VerifyResult.LockedOut] if too many failures, [VerifyResult.Failed] on mismatch,
     *  or [VerifyResult.Success] on match.
     *  Legacy SHA-256 hashes are transparently upgraded to PBKDF2 on success. */
    suspend fun verifyPassword(context: Context, vaultUuid: UUID, secret: String): VerifyResult {
        // Check lockout
        val remainingLockout = getRemainingLockout(context, vaultUuid)
        if (remainingLockout > 0) {
            return VerifyResult.LockedOut(remainingLockout)
        }

        val key = keyFor(vaultUuid)
        val stored = context.activeDataStore.data.map { prefs -> prefs[key] }.first()
            ?: return VerifyResult.Failed(MAX_ATTEMPTS)
        val parts = stored.split(":")
        // New format: type:pbkdf2:salt:hash (4 parts)
        // Legacy format: type:salt:hash (3 parts) or salt:hash (2 parts)
        val salt: ByteArray
        val expectedHash: ByteArray
        val isPbkdf2: Boolean
        val authType: VaultAuthType?
        when {
            parts.size == 4 && parts[1] == HASH_ALGORITHM -> {
                // New PBKDF2 format
                authType = runCatching { VaultAuthType.valueOf(parts[0]) }.getOrNull()
                salt = parts[2].hexToBytes()
                expectedHash = parts[3].hexToBytes()
                isPbkdf2 = true
            }
            parts.size == 3 -> {
                // Legacy: type:salt:hash (SHA-256)
                authType = runCatching { VaultAuthType.valueOf(parts[0]) }.getOrNull()
                salt = parts[1].hexToBytes()
                expectedHash = parts[2].hexToBytes()
                isPbkdf2 = false
            }
            parts.size == 2 -> {
                // Legacy: salt:hash (SHA-256, no type)
                authType = null
                salt = parts[0].hexToBytes()
                expectedHash = parts[1].hexToBytes()
                isPbkdf2 = false
            }
            else -> return VerifyResult.Failed(MAX_ATTEMPTS)
        }

        val actualHash = if (isPbkdf2) pbkdf2Hash(secret, salt) else legacySha256Hash(secret, salt)
        val matches = MessageDigest.isEqual(expectedHash, actualHash)

        if (matches) {
            // Transparently upgrade legacy SHA-256 hashes to PBKDF2
            if (!isPbkdf2) {
                setPassword(context, vaultUuid, secret, authType ?: VaultAuthType.PASSWORD)
            }
            resetAttempts(context, vaultUuid)
            return VerifyResult.Success
        }

        // Record failed attempt and possibly trigger lockout
        return recordFailedAttempt(context, vaultUuid)
    }

    private suspend fun recordFailedAttempt(context: Context, vaultUuid: UUID): VerifyResult {
        val attemptsKey = attemptsKeyFor(vaultUuid)
        val lockoutKey = lockoutKeyFor(vaultUuid)
        var currentAttempts = 0
        context.activeDataStore.edit { prefs ->
            currentAttempts = (prefs[attemptsKey] ?: 0) + 1
            prefs[attemptsKey] = currentAttempts
            if (currentAttempts >= MAX_ATTEMPTS) {
                // Exponential backoff: 30s, 60s, 120s, …
                val multiplier = 1L shl ((currentAttempts / MAX_ATTEMPTS) - 1).coerceAtMost(6)
                val cooldown = BASE_COOLDOWN_MS * multiplier
                prefs[lockoutKey] = System.currentTimeMillis() + cooldown
            }
        }
        return if (currentAttempts >= MAX_ATTEMPTS) {
            val multiplier = 1L shl ((currentAttempts / MAX_ATTEMPTS) - 1).coerceAtMost(6)
            VerifyResult.LockedOut(BASE_COOLDOWN_MS * multiplier)
        } else {
            VerifyResult.Failed(attemptsLeft = MAX_ATTEMPTS - currentAttempts)
        }
    }

    private suspend fun resetAttempts(context: Context, vaultUuid: UUID) {
        context.activeDataStore.edit { prefs ->
            prefs.remove(attemptsKeyFor(vaultUuid))
            prefs.remove(lockoutKeyFor(vaultUuid))
        }
    }

    private fun pbkdf2Hash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun legacySha256Hash(password: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return digest.digest(password.toByteArray(Charsets.UTF_8))
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
