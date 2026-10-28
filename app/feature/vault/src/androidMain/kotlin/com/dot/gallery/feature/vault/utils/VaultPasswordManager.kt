package com.dot.gallery.feature.vault.utils

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.uuid.Uuid
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import com.dot.gallery.cloud.core.matches
import com.dot.gallery.core.util.or
import com.dot.gallery.core.presentation.util.remove
import java.util.UUID
import com.dot.gallery.feature.vault.domain.model.GateMode
import com.dot.gallery.feature.vault.domain.model.VaultAuthType
import com.dot.gallery.feature.vault.domain.model.VaultCredentialStatus
import com.dot.gallery.feature.vault.domain.model.VerifyResult
import com.dot.gallery.core.activeDataStore

/**
 * Manages per-vault custom authentication (PIN / Pattern / Password) using DataStore.
 * Credentials are stored as PBKDF2-hashed secrets: `"type:pbkdf2:salt:hash"`.
 * Legacy SHA-256 format (`"type:salt:hash"`) is still accepted for verification
 * and transparently upgraded on the next successful [verifyPassword] call.
 * When no custom auth is set, device biometric/credential auth is used.
 */
object VaultPasswordManager {

    /** Fixed Uuid used to store the global gate authentication credentials. */
    val GATE_UUID: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000000")

    /** Fixed Uuid used to store private folder authentication credentials. */
    val PRIVATE_FOLDER_UUID: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000001")

    private val GATE_MODE_KEY = stringPreferencesKey("vault_gate_mode")
    private val PRIVATE_FOLDER_MODE_KEY = stringPreferencesKey("private_folder_gate_mode")
    private val VAULT_LOCK_ALL_KEY = booleanPreferencesKey("vault_lock_all")

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

    /**
     * Whether the configured gate lock should also cover vault entry. Per-vault
     * credentials are kept in storage while enabled and apply again when disabled.
     */
    suspend fun getVaultLockAll(context: Context): Boolean =
        context.activeDataStore.data.first()[VAULT_LOCK_ALL_KEY] ?: false

    suspend fun setVaultLockAll(context: Context, enabled: Boolean) {
        context.activeDataStore.edit { it[VAULT_LOCK_ALL_KEY] = enabled }
    }

    private const val SALT_LENGTH = 16
    private const val PBKDF2_ITERATIONS = 120_000
    private const val PBKDF2_KEY_LENGTH = 256
    private const val HASH_ALGORITHM = "pbkdf2"

    /** Max consecutive wrong attempts before lockout kicks in. */
    private const val MAX_ATTEMPTS = 5
    /** Base cooldown in ms after MAX_ATTEMPTS failures (30 seconds). */
    private const val BASE_COOLDOWN_MS = 30_000L

    private fun attemptsKeyFor(vaultUuid: Uuid) =
        intPreferencesKey("vault_attempts_${vaultUuid}")
    private fun lockoutKeyFor(vaultUuid: Uuid) =
        longPreferencesKey("vault_lockout_until_${vaultUuid}")

    private fun keyFor(vaultUuid: Uuid) =
        stringPreferencesKey("vault_password_${vaultUuid}")

    /** Returns true if any custom auth has been set for the given vault. */
    suspend fun hasCustomPassword(context: Context, vaultUuid: Uuid): Boolean {
        val key = keyFor(vaultUuid)
        return context.activeDataStore.data.map { prefs -> prefs[key] != null }.first()
    }

    /** Returns whether the stored credential is present and structurally valid. */
    suspend fun getCredentialStatus(
        context: Context,
        vaultUuid: Uuid
    ): VaultCredentialStatus {
        val key = keyFor(vaultUuid)
        val stored = context.activeDataStore.data.map { prefs -> prefs[key] }.first()
        return credentialStatusForStoredValue(stored)
    }

    /** Returns the auth type for a valid credential, or null if it is missing or corrupt. */
    suspend fun getAuthType(context: Context, vaultUuid: Uuid): VaultAuthType? =
        (getCredentialStatus(context, vaultUuid) as? VaultCredentialStatus.Valid)?.authType

    /** Sets custom authentication for the vault with the given [type] and [secret]. */
    suspend fun setPassword(
        context: Context,
        vaultUuid: Uuid,
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
    suspend fun removePassword(context: Context, vaultUuid: Uuid) {
        val key = keyFor(vaultUuid)
        context.activeDataStore.edit { prefs -> prefs.remove(key) }
    }

    /** Raw stored credential (`TYPE:pbkdf2:<salt>:<hash>`), or null when none is set. */
    suspend fun getStoredCredential(context: Context, vaultUuid: Uuid): String? {
        val key = keyFor(vaultUuid)
        return context.activeDataStore.data.map { prefs -> prefs[key] }.first()
    }

    /**
     * Restores a credential verbatim. Used only by backup restore, where the archive already holds
     * the salted hash; never use this for a user-typed secret (use [setPassword]).
     */
    suspend fun setStoredCredential(context: Context, vaultUuid: Uuid, credential: String) {
        val key = keyFor(vaultUuid)
        context.activeDataStore.edit { prefs -> prefs[key] = credential }
    }

    /** Removes the credential and any lockout state for [vaultUuid]. */
    suspend fun clearVaultSecrets(context: Context, vaultUuid: Uuid) {
        context.activeDataStore.edit { prefs ->
            prefs.remove(keyFor(vaultUuid))
            prefs.remove(attemptsKeyFor(vaultUuid))
            prefs.remove(lockoutKeyFor(vaultUuid))
        }
    }

    /** Returns remaining lockout time in ms, or 0 if not locked out. */
    suspend fun getRemainingLockout(context: Context, vaultUuid: Uuid): Long {
        val lockoutUntil = context.activeDataStore.data.map { prefs ->
            prefs[lockoutKeyFor(vaultUuid)] ?: 0L
        }.first()
        return (lockoutUntil - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun credentialStatusForStoredValue(stored: String?): VaultCredentialStatus {
        if (stored == null) return VaultCredentialStatus.Missing
        val credential = parseStoredCredential(stored) ?: return VaultCredentialStatus.Corrupt
        return VaultCredentialStatus.Valid(credential.authType)
    }

    private data class StoredCredential(
        val authType: VaultAuthType,
        val salt: ByteArray,
        val expectedHash: ByteArray,
        val isPbkdf2: Boolean
    )

    private fun parseStoredCredential(stored: String): StoredCredential? {
        val parts = stored.split(":")
        val authType = parts.firstOrNull()
            ?.let { runCatching { VaultAuthType.valueOf(it) }.getOrNull() }
            ?: return null
        val isPbkdf2: Boolean
        val saltPart: String
        val hashPart: String
        when {
            parts.size == 4 && parts[1] == HASH_ALGORITHM -> {
                isPbkdf2 = true
                saltPart = parts[2]
                hashPart = parts[3]
            }
            parts.size == 3 -> {
                isPbkdf2 = false
                saltPart = parts[1]
                hashPart = parts[2]
            }
            else -> return null
        }
        val salt = saltPart.hexToBytesOrNull() ?: return null
        val expectedHash = hashPart.hexToBytesOrNull() ?: return null
        if (salt.size != SALT_LENGTH || expectedHash.size != PBKDF2_KEY_LENGTH / 8) return null
        return StoredCredential(authType, salt, expectedHash, isPbkdf2)
    }

    /** Verifies the entered [secret] against the stored hash.
     *  Returns [VerifyResult.LockedOut] if too many failures, [VerifyResult.Failed] on mismatch,
     *  or [VerifyResult.Success] on match.
     *  Legacy SHA-256 hashes are transparently upgraded to PBKDF2 on success. */
    suspend fun verifyPassword(context: Context, vaultUuid: Uuid, secret: String): VerifyResult {
        // Check lockout
        val remainingLockout = getRemainingLockout(context, vaultUuid)
        if (remainingLockout > 0) {
            return VerifyResult.LockedOut(remainingLockout)
        }

        val key = keyFor(vaultUuid)
        val stored = context.activeDataStore.data.map { prefs -> prefs[key] }.first()
            ?: return VerifyResult.Failed(MAX_ATTEMPTS)
        val credential = parseStoredCredential(stored)
            ?: return VerifyResult.Failed(MAX_ATTEMPTS)

        val actualHash = if (credential.isPbkdf2) {
            pbkdf2Hash(secret, credential.salt)
        } else {
            legacySha256Hash(secret, credential.salt)
        }
        val matches = MessageDigest.isEqual(credential.expectedHash, actualHash)

        if (matches) {
            // Transparently upgrade legacy SHA-256 hashes to PBKDF2
            if (!credential.isPbkdf2) {
                setPassword(context, vaultUuid, secret, credential.authType)
            }
            resetAttempts(context, vaultUuid)
            return VerifyResult.Success
        }

        // Record failed attempt and possibly trigger lockout
        return recordFailedAttempt(context, vaultUuid)
    }

    private suspend fun recordFailedAttempt(context: Context, vaultUuid: Uuid): VerifyResult {
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

    private suspend fun resetAttempts(context: Context, vaultUuid: Uuid) {
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

    private fun String.hexToBytesOrNull(): ByteArray? {
        if (isEmpty() || length % 2 != 0) return null
        return runCatching {
            chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }.getOrNull()
    }
}
