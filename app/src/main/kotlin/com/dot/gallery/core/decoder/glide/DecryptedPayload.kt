package com.dot.gallery.core.decoder.glide

import com.dot.gallery.BuildConfig
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.domain.model.Media
import java.io.File

data class DecryptedPayload(
    val bytes: ByteArray,
    val mimeType: String,
    val width: Int? = null,
    val height: Int? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DecryptedPayload

        if (width != other.width) return false
        if (height != other.height) return false
        if (!bytes.contentEquals(other.bytes)) return false
        if (mimeType != other.mimeType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = width ?: 0
        result = 31 * result + (height ?: 0)
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        return result
    }
}

fun isEncryptedVaultPath(file: File): Boolean =
    file.path.contains(BuildConfig.APPLICATION_ID)

/**
 * Decrypt file (image or video) into bytes. Reuses existing extension:
 * file.decryptKotlin<EncryptedMedia>() from your project (not shown here).
 */
@Suppress("UNCHECKED_CAST")
fun decryptMediaFile(file: File, keychainHolder: KeychainHolder): DecryptedPayload {
    return try {
        val encrypted = with(keychainHolder) { file.decryptKotlin<Media.EncryptedMedia>() }
        DecryptedPayload(bytes = encrypted.bytes, mimeType = encrypted.mimeType)
    } catch (_: Throwable) {
        // Portable path: raw bytes decrypted via data key if magic present. We lack mime here so default guess; caller may override later.
        val raw = file.readBytes()
        val vaultUuid = file.parentFile?.name
        val mime = "image/*" // fallback; real mime should come from DB record in higher layer.
        val decrypted = try {
            // Attempt portable decrypt if we can identify vault folder as UUID.
            // We need a Vault object to call decryptPortableIfNeeded, but avoiding tight coupling here; expose a lightweight inline approach would require refactor.
            // For now, try each loaded vault? (Skipped). Simpler: attempt magic detection directly using reflection-free approach.
            // Use internal helper via public startsWithMagic + decryptPortableContent.
            val holderCls = keychainHolder::class.java
            val startsWithMagic = holderCls.getDeclaredMethod("startsWithMagic", ByteArray::class.java).apply { isAccessible = true }.invoke(keychainHolder, raw) as Boolean
            if (startsWithMagic) {
                // We need a Vault instance to decrypt; if vaultUuid missing, just return raw.
                val vault = try { java.util.UUID.fromString(vaultUuid); com.dot.gallery.feature_node.domain.model.Vault(java.util.UUID.fromString(vaultUuid), "") } catch (_: Throwable) { null }
                if (vault != null) {
                    val decryptMeth = holderCls.getDeclaredMethod("decryptPortableContent", com.dot.gallery.feature_node.domain.model.Vault::class.java, ByteArray::class.java).apply { isAccessible = true }
                    decryptMeth.invoke(keychainHolder, vault, raw) as ByteArray
                } else raw
            } else raw
        } catch (_: Throwable) { raw }
        DecryptedPayload(bytes = decrypted, mimeType = mime)
    }
}