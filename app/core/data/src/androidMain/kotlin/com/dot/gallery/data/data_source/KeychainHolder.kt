@file:Suppress("DEPRECATION")

package com.dot.gallery.data.data_source

import android.content.Context
import android.net.Uri
import android.security.keystore.UserNotAuthenticatedException
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
import androidx.security.crypto.MasterKey
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.domain.model.VaultInfo
import com.dot.gallery.domain.util.toKotlinByteArray
import com.dot.gallery.core.logging.printError
import com.dot.gallery.core.logging.printInfo
import com.dot.gallery.core.logging.printWarn
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.uuid.Uuid
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import com.dot.gallery.domain.model.EncryptedMedia
import com.dot.gallery.domain.uri.path
import com.dot.gallery.domain.util.migrate
import com.dot.gallery.domain.util.fromKotlinByteArray
import java.util.UUID


class KeychainHolder @Inject constructor(
    @PublishedApi internal val context: Context
) {

    val filesDir: File = context.filesDir

    /**
     * Root of every vault folder. It sits in `noBackupFilesDir` because vault data is keyed
     * by Android Keystore keys, which are never backed up, so a restored copy is unreadable.
     */
    val vaultRoot: File = File(context.noBackupFilesDir, VAULT_ROOT_DIR)

    fun vaultFolder(vault: Vault) = File(vaultRoot, vault.uuid.toString())
    private fun vaultInfoFile(vault: Vault) = File(vaultFolder(vault), VAULT_INFO_FILE_NAME)
    fun Vault.mediaFile(mediaId: Long) = File(vaultFolder(this), "$mediaId.enc")

    @PublishedApi internal val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    init {
        if (!storagePrepared) synchronized(KeychainHolder) {
            if (!storagePrepared) {
                prepareStorage()
                storagePrepared = true
            }
        }
    }

    /**
     * Runs once per process before any vault file is touched: moves vault folders out of
     * `filesDir` into [vaultRoot], then recovers from a restored keyset (see
     * [recoverUndecryptableKeyset]). Safe to call again.
     */
    fun prepareStorage() {
        moveVaultFoldersOutOfBackup()
        recoverUndecryptableKeyset()
    }

    private fun moveVaultFoldersOutOfBackup() {
        val legacy = filesDir.listFiles { f ->
            f.isDirectory && runCatching { Uuid.parse(f.name) }.isSuccess &&
                f.list()?.any { it == VAULT_INFO_FILE_NAME || it.endsWith(".enc") } == true
        } ?: return
        if (legacy.isEmpty()) return
        vaultRoot.mkdirs()
        legacy.forEach { from ->
            val to = File(vaultRoot, from.name)
            if (to.exists() || !from.renameTo(to)) {
                to.mkdirs()
                from.listFiles()?.forEach { child ->
                    val target = File(to, child.name)
                    if (!target.exists() && !child.renameTo(target)) {
                        printWarn(TAG, "Could not move vault file", ctx = mapOf("vault" to from.name, "file" to child.name))
                    }
                }
                from.delete()
            }
        }
        printInfo(TAG, "Moved ${legacy.size} vault folder(s) to noBackupFilesDir")
    }

    /**
     * A backup restore brings back the EncryptedFile keyset but not the Keystore master key
     * that wraps it, and from then on every [EncryptedFile.Builder.build] throws
     * [AEADBadTagException], so no vault can be created or read. Checked once per install
     * (the marker is in `noBackupFilesDir`, which a restore never brings back). On that
     * failure the keyset is cleared so a new one is generated. Device-locked vaults can
     * never be read again and are deleted; portable vaults keep their media and lose only
     * their wrapped data key, so re-importing the exported key opens them again.
     */
    private fun recoverUndecryptableKeyset() {
        val marker = File(context.noBackupFilesDir, KEYSET_VERIFIED_MARKER)
        if (marker.exists()) return
        val keysetFile = File(filesDir.parentFile, "shared_prefs/$KEYSET_PREFS_NAME.xml")
        if (keysetFile.exists()) {
            try {
                EncryptedFile.Builder(context, File(context.cacheDir, KEYSET_PROBE_FILE), masterKey, AES256_GCM_HKDF_4KB).build()
            } catch (e: AEADBadTagException) {
                context.getSharedPreferences(KEYSET_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
                var deleted = 0
                var keysDropped = 0
                vaultRoot.listFiles { f -> f.isDirectory }?.forEach { folder ->
                    val vault = runCatching { Vault(uuid = Uuid.parse(folder.name), name = "") }.getOrNull() ?: return@forEach
                    if (isTransferable(vault)) {
                        if (dataKeyFile(vault).delete()) keysDropped++
                    } else if (folder.deleteRecursively()) {
                        deleted++
                    }
                }
                printError(
                    TAG, "Vault keyset cannot be decrypted (restored without its Keystore key); cleared it", e,
                    ctx = mapOf("deviceLockedVaultsDeleted" to "$deleted", "portableKeysDropped" to "$keysDropped"),
                )
            } catch (e: Exception) {
                printWarn(TAG, "Vault keyset check failed; retrying next launch", ctx = mapOf("exception" to "${e.javaClass.name}: ${e.message}"))
                return
            }
        }
        marker.createNewFile()
    }

    /**
     * Legacy write: always device-locked (non-transferable). Kept for backward compatibility.
     */
    fun writeVaultInfo(vault: Vault, onSuccess: () -> Unit = {}, onFailed: (reason: String) -> Unit = {}) {
        try {
            val vaultFolder = vaultFolder(vault)
            if (!vaultFolder.exists()) {
                vaultFolder.mkdirs()
            }

            vaultInfoFile(vault).apply {
                if (exists()) delete()
                encryptKotlin(vault)
                onSuccess()
            }
        } catch (e: Exception) {
            printError(TAG, "Failed to write vault info for ${vault.uuid}", e)
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
            printError(TAG, "Failed to write vault info for ${vault.uuid}", e)
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
            printError(TAG, "Failed to delete vault ${vault.uuid}", e)
            onFailed(e.message.toString())
        }
    }

    fun checkVaultFolder(vault: Vault) {
        val mainFolder = vaultFolder(vault)
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
    // V1 layout: MAGIC_V1(6) | NONCE(12) | CIPHERTEXT+TAG (single GCM, in-memory)
    // V2 layout: MAGIC_V2(6) | BASE_NONCE(12) | CHUNK_SIZE(4) | [LEN(4)+CHUNK_CIPHERTEXT]*
    private val MAGIC = "VLTv1".toByteArray() // 5 bytes + sentinel
    private val MAGIC_V2 = "VLTv2".toByteArray()
    private val MAGIC_PAD = 6 // ensure fixed length (add 1 padding byte)
    private val STREAM_CHUNK_SIZE = 1 * 1024 * 1024 // 1 MiB per GCM chunk

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

    /**
     * Chunked streaming encryption (VLTv2). Each chunk is independently GCM-encrypted
     * so the cipher never accumulates more than STREAM_CHUNK_SIZE in memory.
     */
    fun encryptPortableStream(vault: Vault, input: InputStream, outputFile: File) {
        val dk = loadDataKey(vault) ?: error("Data key missing for portable vault")
        val key = dk.toAesKey()
        val baseNonce = ByteArray(12).apply { SecureRandom().nextBytes(this) }

        DataOutputStream(outputFile.outputStream().buffered()).use { dos ->
            // Header
            dos.write(MAGIC_V2)
            dos.write(ByteArray(MAGIC_PAD - MAGIC_V2.size)) // padding to 6 bytes
            dos.write(baseNonce)
            dos.writeInt(STREAM_CHUNK_SIZE)

            val readBuf = ByteArray(STREAM_CHUNK_SIZE)
            var chunkIndex = 0L
            while (true) {
                val bytesRead = readFully(input, readBuf)
                if (bytesRead == 0) break

                val chunkNonce = deriveChunkNonce(baseNonce, chunkIndex++)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, chunkNonce))
                val ct = cipher.doFinal(readBuf, 0, bytesRead)
                dos.writeInt(ct.size)
                dos.write(ct)
            }
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

    /**
     * Streaming decryption that auto-detects VLTv1 (single GCM) vs VLTv2 (chunked GCM).
     */
    fun decryptPortableStream(vault: Vault, inputFile: File, output: OutputStream) {
        val dk = loadDataKey(vault) ?: error("Data key missing")
        val key = dk.toAesKey()
        inputFile.inputStream().buffered().use { input ->
            val dis = DataInputStream(input)
            val header = ByteArray(MAGIC_PAD)
            dis.readFully(header)

            if (startsWithMagicV2(header)) {
                // VLTv2 chunked decryption
                val baseNonce = ByteArray(12)
                dis.readFully(baseNonce)
                val chunkSize = dis.readInt() // stored but not strictly needed for read

                var chunkIndex = 0L
                while (dis.available() > 0 || input.available() > 0) {
                    val ctLen = try { dis.readInt() } catch (_: EOFException) { break }
                    val ct = ByteArray(ctLen)
                    dis.readFully(ct)
                    val chunkNonce = deriveChunkNonce(baseNonce, chunkIndex++)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, chunkNonce))
                    val plain = cipher.doFinal(ct)
                    output.write(plain)
                }
            } else if (startsWithMagic(header)) {
                // VLTv1 single-GCM (legacy streaming — works for small files)
                val nonce = ByteArray(12)
                dis.readFully(nonce)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var len: Int
                while (input.read(buffer).also { len = it } != -1) {
                    val decrypted = cipher.update(buffer, 0, len)
                    if (decrypted != null) output.write(decrypted)
                }
                val finalBlock = cipher.doFinal()
                if (finalBlock != null && finalBlock.isNotEmpty()) output.write(finalBlock)
            } else {
                error("Not portable content")
            }
        }
    }

    fun isPortableFile(file: File): Boolean {
        if (!file.exists() || file.length() < MAGIC_PAD) return false
        val header = ByteArray(MAGIC_PAD)
        file.inputStream().use { it.read(header) }
        return startsWithMagic(header) || startsWithMagicV2(header)
    }

    /**
     * Attempt portable decryption; if magic header absent falls back to returning original bytes.
     */
    fun decryptPortableIfNeeded(vault: Vault, bytes: ByteArray): ByteArray =
        if (startsWithMagic(bytes)) decryptPortableContent(vault, bytes) else bytes

    /**
     * Import a portable vault by injecting a provided Base64 data key.
     * If vault folder exists and already has a readable key, it's left unchanged unless force=true.
     */
    fun importPortableVault(vault: Vault, base64Key: String, force: Boolean = false): Boolean {
        return try {
            val dk = Base64.getDecoder().decode(base64Key)
            require(dk.size == 32) { "Invalid key length" }
            val folder = vaultFolder(vault)
            if (!folder.exists()) folder.mkdirs()
            val dkFile = dataKeyFile(vault)
            // Keep an existing key only if it still decrypts; otherwise the import replaces it.
            if (dkFile.exists() && !force && runCatching { loadDataKey(vault) }
                    .onFailure { printWarn(TAG, "Existing vault data key unreadable; replacing it", ctx = mapOf("exception" to "${it.javaClass.name}: ${it.message}")) }
                    .getOrNull() != null
            ) return true
            // EncryptedFile refuses to overwrite an existing file.
            dkFile.delete()
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
            printError(TAG, "Failed to import portable vault ${vault.uuid}", e)
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
                    val encrypted = f.decryptKotlin<EncryptedMedia>()
                    val portableBytes = encryptPortableContent(vault, encrypted.bytes)
                    f.writeBytes(portableBytes)
                } catch (e: Throwable) {
                    // leave file as-is if failure; could log
                    printError(TAG, "Failed to migrate file ${f.name} to portable format", e)
                }
                index++
                onProgress(index, files.size)
            }
            true
        } catch (e: Exception) {
            printError(TAG, "Failed to migrate vault ${vault.uuid} to portable format", e)
            false
        }
    }

    fun startsWithMagic(bytes: ByteArray): Boolean {
        if (bytes.size < MAGIC_PAD) return false
        for (i in MAGIC.indices) if (bytes[i] != MAGIC[i]) return false
        return true
    }

    fun startsWithMagicV2(bytes: ByteArray): Boolean {
        if (bytes.size < MAGIC_PAD) return false
        for (i in MAGIC_V2.indices) if (bytes[i] != MAGIC_V2[i]) return false
        return true
    }

    fun isPortableV2File(file: File): Boolean {
        if (!file.exists() || file.length() < MAGIC_PAD) return false
        val header = ByteArray(MAGIC_PAD)
        file.inputStream().use { it.read(header) }
        return startsWithMagicV2(header)
    }

    /** XOR chunk index into the last 8 bytes of the base nonce to derive a unique per-chunk nonce. */
    private fun deriveChunkNonce(baseNonce: ByteArray, chunkIndex: Long): ByteArray {
        val nonce = baseNonce.copyOf()
        for (i in 0 until 8) {
            nonce[nonce.size - 1 - i] = (nonce[nonce.size - 1 - i].toInt() xor ((chunkIndex shr (i * 8)) and 0xFF).toInt()).toByte()
        }
        return nonce
    }

    /** Read up to buf.size bytes from input, returning actual count (0 at EOF). */
    private fun readFully(input: InputStream, buf: ByteArray): Int {
        var offset = 0
        while (offset < buf.size) {
            val n = input.read(buf, offset, buf.size - offset)
            if (n == -1) break
            offset += n
        }
        return offset
    }

    private fun ByteArray.toAesKey(): SecretKey = SecretKeySpec(this, "AES")

    @Throws(GeneralSecurityException::class, IOException::class, FileNotFoundException::class, UserNotAuthenticatedException::class)
    inline fun <reified T> File.decryptKotlin(): T = EncryptedFile.Builder(
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
    inline fun <reified T> File.encryptKotlin(data: T) {
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
    /**
     * Decrypt a VLTv2 file to a temporary file (streaming, constant memory).
     * Returns the temp file and sniffed MIME type.
     */
    fun decryptToTempFile(file: File, cacheDir: File? = null): DecryptedVaultMedia {
        val vaultUuidStr = file.parentFile?.name ?: error("Cannot determine vault Uuid from path")
        val vault = Vault(uuid = Uuid.parse(vaultUuidStr), name = "")
        val tmpDir = cacheDir ?: context.cacheDir
        val tempFile = File.createTempFile("vault_dec_", ".tmp", tmpDir)
        FileOutputStream(tempFile).use { out ->
            decryptPortableStream(vault, file, out)
        }
        // Sniff MIME from first 12 bytes of decrypted content
        val header = ByteArray(12)
        tempFile.inputStream().use { it.read(header) }
        val mime = sniffMimeType(header)
        return DecryptedVaultMedia(bytes = null, mimeType = mime, tempFile = tempFile)
    }

    fun decryptVaultMedia(file: File): DecryptedVaultMedia {
        val vaultUuidStr = file.parentFile?.name ?: error("Cannot determine vault Uuid from path")
        val vault = Vault(uuid = Uuid.parse(vaultUuidStr), name = "")

        if (isPortableV2File(file)) {
            // VLTv2: stream-decrypt to temp file (constant memory)
            return decryptToTempFile(file)
        }
        if (isPortableFile(file)) {
            val encBytes = file.readBytes()
            val plainBytes = decryptPortableContent(vault, encBytes)
            val mime = sniffMimeType(plainBytes)
            return DecryptedVaultMedia(plainBytes, mime)
        }
        val enc = file.decryptKotlin<EncryptedMedia>()
        return DecryptedVaultMedia(enc.bytes, enc.mimeType)
    }

    companion object {
        private const val TAG = "vault.keychain"
        const val VAULT_INFO_FILE_NAME = "info.vault"
        const val DATA_KEY_FILE_NAME = "vault.key.enc"
        const val VAULT_ROOT_DIR = "vaults"
        const val KEYSET_VERIFIED_MARKER = "vault_keyset_verified"
        // EncryptedFile's default keyset preferences; excluded from backup in the backup rules.
        const val KEYSET_PREFS_NAME = "__androidx_security_crypto_encrypted_file_pref__"
        private const val KEYSET_PROBE_FILE = "vault_keyset_probe"

        @Volatile
        private var storagePrepared = false

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