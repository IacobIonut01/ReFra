/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.dot.gallery.core.injection.Singleton
import com.dot.gallery.core.logging.printError
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject

/**
 * Persists the active backup/restore job under `noBackupFilesDir`, which Android Auto Backup
 * never copies. The password is stored separately, wrapped by the app's Keystore MasterKey, and
 * is deleted as soon as the worker has finished using it.
 */
@Singleton
class BackupJobStore @Inject constructor(
    private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val dir = File(context.noBackupFilesDir, "backup_jobs/current")
    private val jobFile = File(dir, "job.json")
    private val secretFile = File(dir, "secret.bin")
    private fun lastFile(kind: BackupJobKind) =
        File(dir.parentFile, "last_${kind.name.lowercase()}.json")
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    fun load(): BackupJobSnapshot? = runCatching {
        if (!jobFile.isFile) return null
        json.decodeFromString(BackupJobSnapshot.serializer(), jobFile.readText())
    }.getOrNull()

    fun save(snapshot: BackupJobSnapshot) {
        runCatching {
            dir.mkdirs()
            jobFile.writeText(json.encodeToString(BackupJobSnapshot.serializer(), snapshot))
        }.onFailure { printError("app.backup", "could not persist backup job", it) }
    }

    fun clear() {
        secretFile.delete()
        jobFile.delete()
        dir.delete()
    }

    fun loadLast(kind: BackupJobKind): BackupJobSnapshot? = runCatching {
        val file = lastFile(kind)
        if (!file.isFile) return null
        json.decodeFromString(BackupJobSnapshot.serializer(), file.readText())
    }.getOrNull()

    fun saveLast(snapshot: BackupJobSnapshot) {
        runCatching {
            val file = lastFile(snapshot.kind)
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(BackupJobSnapshot.serializer(), snapshot))
        }.onFailure { printError("app.backup", "could not persist last backup job", it) }
    }

    fun saveSecret(password: String?) {
        secretFile.delete()
        if (password.isNullOrEmpty()) return
        runCatching {
            dir.mkdirs()
            EncryptedFile.Builder(
                context,
                secretFile,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build().openFileOutput().use { output ->
                output.write(password.toByteArray(Charsets.UTF_8))
            }
        }.onFailure { printError("app.backup", "could not store backup secret", it) }
    }

    fun readSecret(): String? = runCatching {
        if (!secretFile.isFile) return null
        EncryptedFile.Builder(
            context,
            secretFile,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build().openFileInput().use { input ->
            input.readBytes().decodeToString()
        }
    }.getOrNull()

    fun clearSecret() {
        secretFile.delete()
    }
}
