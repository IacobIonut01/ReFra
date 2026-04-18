@file:Suppress("DEPRECATION")

package com.dot.gallery.feature_node.data.data_source

import android.content.Context
import android.net.Uri
import android.security.keystore.UserNotAuthenticatedException
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
import androidx.security.crypto.MasterKey
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.model.VaultInfo
import com.dot.gallery.feature_node.domain.util.fromKotlinByteArray
import com.dot.gallery.feature_node.domain.util.toKotlinByteArray
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

class KeychainHolder @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    val filesDir: File = context.filesDir

    fun vaultFolder(vault: Vault) = File(filesDir, vault.uuid.toString())
    private fun vaultInfoFile(vault: Vault) = File(vaultFolder(vault), VAULT_INFO_FILE_NAME)
    fun Vault.mediaFile(mediaId: Long) = File(vaultFolder(this), "$mediaId.enc")

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    /**
     * Legacy write: always device-locked (non-transferable). Kept for backward compatibility.
     */
    fun writeVaultInfo(vault: Vault, onSuccess: () -> Unit = {}, onFailed: (reason: String) -> Unit = {}) {
        try {
            val vaultFolder = File(filesDir, vault.uuid.toString())
            if (!vaultFolder.exists()) {
                vaultFolder.mkdirs()
            }

            vaultInfoFile(vault).apply {
                if (exists()) delete()
                encryptKotlin(vault)
                onSuccess()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onFailed(e.message.toString())
        }
    }

    /**
     * New portable vault initialization.
     * When transferable=true we generate a random 32-byte Data Key (DK), wrap it with EncryptedFile
     * and store plaintext metadata (VaultInfo) containing dkHash for integrity.
     */
    fun writeVaultInfo(
        vault: Vault,
        transferable: Boolean,
        force: Boolean = false,
        onSuccess: () -> Unit = {},
        onFailed: (reason: String) -> Unit = {}
    ) {
        try {
            val folder = vaultFolder(vault)
            if (!folder.exists()) folder.mkdirs()

            if (transferable) {
                val dkFile = dataKeyFile(vault)
                if (!dkFile.exists() || force) {
                    val dk = generateDataKey()
                    // Store encrypted with MasterKey for local-at-rest protection
                    EncryptedFile.Builder(
                        context,
                        dkFile,
                        masterKey,
                        AES256_GCM_HKDF_4KB
                    ).build().openFileOutput().use { it.write(dk) }
                    val meta = VaultInfo(
                        version = 1,
                        transferable = true,
                        dkHash = dk.sha256Base64(),
                        uuid = vault.uuid.toString()
                    )
                    vaultInfoFile(vault).writeText(meta.toKotlinByteArray().decodeToString())
                }
            } else {
                // Non-transferable path: keep previous encrypted info object
                writeVaultInfo(vault)
            }
            onSuccess()
        } catch (e: Exception) {
            e.printStackTrace()
            onFailed(e.message.toString())
        }
    }

    fun deleteVault(vault: Vault, onSuccess: () -> Unit, onFailed: (reason: String) -> Unit) {
        try {
            val vaultFolder = vaultFolder(vault)
            if (vaultFolder.exists()) {
                vaultFolder.deleteRecursively()
            }
            onSuccess()
        } catch (e: Exception) {
            e.printStackTrace()
            onFailed(e.message.toString())
        }
    }

    fun checkVaultFolder(vault: Vault) {
        val mainFolder = File(filesDir, vault.uuid.toString())
        if (!mainFolder.exists()) {
            mainFolder.mkdirs()
            writeVaultInfo(vault)
        }
    }

    /** Determine if vault is portable by inspecting metadata file (plaintext JSON). */
    fun isTransferable(vault: Vault): Boolean = try {
        val info = readVaultInfo(vault) ?: return false
        info.transferable
    } catch (_: Exception) { false }

    fun readVaultInfo(vault: Vault): VaultInfo? {
        val infoFile = vaultInfoFile(vault)
        if (!infoFile.exists()) return null
        return try {
            // Try plaintext JSON first (portable). If that fails fall back to legacy encrypted
            val text = infoFile.readText()
            fromKotlinByteArray<VaultInfo>(text.toByteArray())
        } catch (_: Exception) {
            // Legacy path: encrypted Vault object (ignore for portable metadata)
            null
        }
    }

    private fun dataKeyFile(vault: Vault) = File(vaultFolder(vault), DATA_KEY_FILE_NAME)

    private fun generateDataKey(): ByteArray = ByteArray(32).apply { SecureRandom().nextBytes(this) }

    private fun ByteArray.sha256Base64(): String =
        Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(this))

    /** Load raw DK (only for transferable vault). */
    fun loadDataKey(vault: Vault): ByteArray? {
        if (!isTransferable(vault)) return null
        val file = dataKeyFile(vault)
        if (!file.exists()) return null
        return EncryptedFile.Builder(
            context,
            file,
            masterKey,
            AES256_GCM_HKDF_4KB
        ).build().openFileInput().use { it.readBytes() }
    }

    /** Export base64 DK for user backup (only if transferable). */
    fun exportDataKeyBase64(vault: Vault): String? = loadDataKey(vault)?.let {
        Base64.getEncoder().encodeToString(it)
    }

    // ---- Portable content encryption helpers ----
    // File layout: MAGIC(6) | NONCE(12) | CIPHERTEXT+TAG (Cipher.doFinal output with 128-bit tag)
    private val MAGIC = "VLTv1".toByteArray() // 5 bytes + sentinel
    private val MAGIC_PAD = 6 // ensure fixed length (add 1 padding byte)

    fun encryptPortableContent(vault: Vault, plaintext: ByteArray): ByteArray {
        val dk = loadDataKey(vault) ?: error("Data key missing for portable vault")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val nonce = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        cipher.init(Cipher.ENCRYPT_MODE, dk.toAesKey(), GCMParameterSpec(128, nonce))
        val ct = cipher.doFinal(plaintext)
        return ByteArray(MAGIC_PAD + nonce.size + ct.size).apply {
            System.arraycopy(MAGIC, 0, this, 0, MAGIC.size)
            this[MAGIC.size] = 0 // padding byte
            System.arraycopy(nonce, 0, this, MAGIC_PAD, nonce.size)
            System.arraycopy(ct, 0, this, MAGIC_PAD + nonce.size, ct.size)
        }
    }

    fun encryptPortableStream(vault: Vault, input: InputStream, outputFile: File) {
        val dk = loadDataKey(vault) ?: error("Data key missing for portable vault")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val nonce = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        cipher.init(Cipher.ENCRYPT_MODE, dk.toAesKey(), GCMParameterSpec(128, nonce))

        outputFile.outputStream().buffered().use { out ->
            out.write(MAGIC)
            out.write(ByteArray(MAGIC_PAD - MAGIC.size)) // padding
            out.write(nonce)

            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var len: Int
            while (input.read(buffer).also { len = it } != -1) {
                val encrypted = cipher.update(buffer, 0, len)
                if (encrypted != null) out.write(encrypted)
            }
            val finalBlock = cipher.doFinal()
            if (finalBlock != null && finalBlock.isNotEmpty()) out.write(finalBlock)
        }
    }

    fun decryptPortableContent(vault: Vault, bytes: ByteArray): ByteArray {
        if (!startsWithMagic(bytes)) error("Not portable content")
        val dk = loadDataKey(vault) ?: error("Data key missing")
        val nonce = bytes.copyOfRange(MAGIC_PAD, MAGIC_PAD + 12)
        val ct = bytes.copyOfRange(MAGIC_PAD + 12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, dk.toAesKey(), GCMParameterSpec(128, nonce))
        return cipher.doFinal(ct)
    }

    fun decryptPortableStream(vault: Vault, inputFile: File, output: OutputStream) {
        val dk = loadDataKey(vault) ?: error("Data key missing")
        inputFile.inputStream().buffered().use { input ->
            val dis = DataInputStream(input)
            val header = ByteArray(MAGIC_PAD)
            dis.readFully(header)
            if (!startsWithMagic(header)) error("Not portable content")

            val nonce = ByteArray(12)
            dis.readFully(nonce)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, dk.toAesKey(), GCMParameterSpec(128, nonce))

            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var len: Int
            while (input.read(buffer).also { len = it } != -1) {
                val decrypted = cipher.update(buffer, 0, len)
                if (decrypted != null) output.write(decrypted)
            }
            val finalBlock = cipher.doFinal()
            if (finalBlock != null && finalBlock.isNotEmpty()) output.write(finalBlock)
        }
    }

    fun isPortableFile(file: File): Boolean {
        if (!file.exists() || file.length() < MAGIC_PAD) return false
        val header = ByteArray(MAGIC_PAD)
        file.inputStream().use { it.read(header) }
        return startsWithMagic(header)
    }

    /**
     * Attempt portable decryption; if magic header absent falls back to returning original bytes.
     */
    fun decryptPortableIfNeeded(vault: Vault, bytes: ByteArray): ByteArray =
        if (startsWithMagic(bytes)) decryptPortableContent(vault, bytes) else bytes

    /**
     * Import a portable vault by injecting a provided Base64 data key.
     * If vault folder exists and already has key, it's left unchanged unless force=true.
     */
    fun importPortableVault(vault: Vault, base64Key: String, force: Boolean = false): Boolean {
        return try {
            val dk = Base64.getDecoder().decode(base64Key)
            require(dk.size == 32) { "Invalid key length" }
            val folder = vaultFolder(vault)
            if (!folder.exists()) folder.mkdirs()
            val dkFile = dataKeyFile(vault)
            if (dkFile.exists() && !force) return true
            // Write encrypted DK file
            EncryptedFile.Builder(
                context,
                dkFile,
                masterKey,
                AES256_GCM_HKDF_4KB
            ).build().openFileOutput().use { it.write(dk) }
            val meta = VaultInfo(
                version = 1,
                transferable = true,
                dkHash = dk.sha256Base64(),
                uuid = vault.uuid.toString()
            )
            vaultInfoFile(vault).writeText(meta.toKotlinByteArray().decodeToString())
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Migrate an existing non-transferable (legacy) vault to portable format.
     * Creates data key and re-encrypts each legacy EncryptedMedia file into portable binary format.
     */
    fun migrateVaultToPortable(vault: Vault, onProgress: (Int, Int) -> Unit = { _, _ -> }): Boolean {
        return try {
            if (isTransferable(vault)) return true // already portable
            // Initialize transferable vault (generates DK & metadata)
            writeVaultInfo(vault, transferable = true)
            val files = vaultFolder(vault).listFiles { f -> f.name.endsWith(".enc") } ?: emptyArray()
            var index = 0
            files.forEach { f ->
                try {
                    val encrypted = f.decryptKotlin<Media.EncryptedMedia>()
                    val portableBytes = encryptPortableContent(vault, encrypted.bytes)
                    f.writeBytes(portableBytes)
                } catch (e: Throwable) {
                    // leave file as-is if failure; could log
                    e.printStackTrace()
                }
                index++
                onProgress(index, files.size)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    internal fun startsWithMagic(bytes: ByteArray): Boolean {
        if (bytes.size < MAGIC_PAD) return false
        for (i in MAGIC.indices) if (bytes[i] != MAGIC[i]) return false
        return true
    }

    private fun ByteArray.toAesKey(): SecretKey = SecretKeySpec(this, "AES")

    @Throws(GeneralSecurityException::class, IOException::class, FileNotFoundException::class, UserNotAuthenticatedException::class)
    internal inline fun <reified T> File.decryptKotlin(): T = EncryptedFile.Builder(
        context,
        this,
        masterKey,
        AES256_GCM_HKDF_4KB
    ).build().openFileInput().use {
        fromKotlinByteArray(it.readBytes())
    }
/*
    @Throws(GeneralSecurityException::class, IOException::class, FileNotFoundException::class, UserNotAuthenticatedException::class)
    fun <T : Serializable> File.decrypt(): T = EncryptedFile.Builder(
        context,
        this,
        masterKey,
        AES256_GCM_HKDF_4KB
    ).build().openFileInput().use {
        fromByteArray(it.readBytes())
    }*/

    @Throws(GeneralSecurityException::class, IOException::class, FileNotFoundException::class, UserNotAuthenticatedException::class)
    internal inline fun <reified T> File.encryptKotlin(data: T) {
        EncryptedFile.Builder(
            context,
            this,
            masterKey,
            AES256_GCM_HKDF_4KB
        ).build().openFileOutput().use {
            it.write(data.toKotlinByteArray())
        }
    }

    @Throws(IOException::class)
    fun getBytes(uri: Uri): ByteArray? =
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val byteBuffer = ByteArrayOutputStream()
            val bufferSize = 1024
            val buffer = ByteArray(bufferSize)

            var len: Int
            while (inputStream.read(buffer).also { len = it } != -1) {
                byteBuffer.write(buffer, 0, len)
            }
            byteBuffer.toByteArray()
        }

    /**
     * Unified vault file decryption that handles both portable (VLTv1) and legacy (EncryptedFile) formats.
     * Returns raw decrypted bytes and the mime type.
     */
    fun decryptVaultMedia(file: File): DecryptedVaultMedia {
        if (isPortableFile(file)) {
            val vaultUuidStr = file.parentFile?.name ?: error("Cannot determine vault UUID from path")
            val vault = Vault(uuid = UUID.fromString(vaultUuidStr), name = "")
            val encBytes = file.readBytes()
            val plainBytes = decryptPortableContent(vault, encBytes)
            val mime = sniffMimeType(plainBytes)
            return DecryptedVaultMedia(plainBytes, mime)
        }
        val enc = file.decryptKotlin<Media.EncryptedMedia>()
        return DecryptedVaultMedia(enc.bytes, enc.mimeType)
    }

    companion object {
        const val VAULT_INFO_FILE_NAME = "info.vault"
        const val DATA_KEY_FILE_NAME = "vault.key.enc"

        fun sniffMimeType(bytes: ByteArray): String {
            if (bytes.size < 12) return "application/octet-stream"
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "image/jpeg"
            if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) return "image/png"
            if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()) return "image/gif"
            if (bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
                bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() && bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()
            ) return "image/webp"
            if (bytes[4] == 0x66.toByte() && bytes[5] == 0x74.toByte() && bytes[6] == 0x79.toByte() && bytes[7] == 0x70.toByte()) {
                val brand = String(bytes, 8, 4)
                if (brand.startsWith("heic") || brand.startsWith("heix") || brand.startsWith("mif1")) return "image/heif"
                return "video/mp4"
            }
            if (bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte()) return "image/bmp"
            return "application/octet-stream"
        }
    }
}

data class DecryptedVaultMedia(val bytes: ByteArray, val mimeType: String)