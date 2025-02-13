package com.dot.gallery.feature_node.data.data_source

import android.content.Context
import android.net.Uri
import android.security.keystore.UserNotAuthenticatedException
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
import androidx.security.crypto.MasterKey
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.util.fromByteArray
import com.dot.gallery.feature_node.domain.util.fromKotlinByteArray
import com.dot.gallery.feature_node.domain.util.toKotlinByteArray
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.Serializable
import java.security.GeneralSecurityException
import javax.inject.Inject

class KeychainHolder @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val filesDir: File = context.filesDir

    fun vaultFolder(vault: Vault) = File(filesDir, vault.uuid.toString())
    private fun vaultInfoFile(vault: Vault) = File(vaultFolder(vault), VAULT_INFO_FILE_NAME)
    fun Vault.mediaFile(mediaId: Long) = File(vaultFolder(this), "$mediaId.enc")

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

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

    @Throws(GeneralSecurityException::class, IOException::class, FileNotFoundException::class, UserNotAuthenticatedException::class)
    internal inline fun <reified T> File.decryptKotlin(): T = EncryptedFile.Builder(
        context,
        this,
        masterKey,
        AES256_GCM_HKDF_4KB
    ).build().openFileInput().use {
        fromKotlinByteArray(it.readBytes())
    }

    @Throws(GeneralSecurityException::class, IOException::class, FileNotFoundException::class, UserNotAuthenticatedException::class)
    fun <T : Serializable> File.decrypt(): T = EncryptedFile.Builder(
        context,
        this,
        masterKey,
        AES256_GCM_HKDF_4KB
    ).build().openFileInput().use {
        fromByteArray(it.readBytes())
    }

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

    companion object {
        const val VAULT_INFO_FILE_NAME = "info.vault"
        const val VAULT_INFO_FILE = "/$VAULT_INFO_FILE_NAME"
    }
}