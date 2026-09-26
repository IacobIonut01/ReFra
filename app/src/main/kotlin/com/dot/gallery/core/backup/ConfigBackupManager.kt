/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dot.gallery.R
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.data.dao.CloudMediaDao
import com.dot.gallery.cloud.data.dao.CloudServerConfigDao
import com.dot.gallery.cloud.data.entity.CloudServerConfigEntity
import com.dot.gallery.cloud.di.CloudProviderInitializer
import com.dot.gallery.cloud.sync.CloudSyncScheduler
import com.dot.gallery.core.Resource
import com.dot.gallery.core.activeDataStore
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.domain.util.MediaOrder
import com.dot.gallery.feature_node.presentation.util.printError
import com.dot.gallery.feature_node.presentation.util.printInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports and imports a complete ReFra configuration backup as a ZIP archive.
 *
 * The archive contains a [BackupManifest] (`manifest.json`) plus the fully decrypted
 * binary content of every vault media item under `vaults/<uuid>/<mediaId>`.
 *
 * Local (MediaStore) favorites cannot be re-applied without a system consent dialog,
 * so [importBackup] returns the list of matched device URIs that the caller must pass
 * to a `MediaStore.createFavoriteRequest` consent flow.
 */
@Singleton
class ConfigBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: InternalDatabase,
    private val keychainHolder: KeychainHolder,
    private val cloudMediaDao: CloudMediaDao,
    private val cloudServerConfigDao: CloudServerConfigDao,
    private val mediaRepository: MediaRepository,
    private val pendingCloudFavoriteStore: PendingCloudFavoriteStore,
    private val providerInitializer: CloudProviderInitializer,
    private val syncScheduler: CloudSyncScheduler
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val maxManifestBytes = 4L * 1024 * 1024
    private val maxArchiveBytes = 20L * 1024 * 1024 * 1024
    private val maxEntryBytes = 2L * 1024 * 1024 * 1024
    private val maxArchiveEntries = 10_000

    data class ExportResult(
        val settingsCount: Int,
        val localFavoritesCount: Int,
        val cloudFavoritesCount: Int,
        val cloudConfigsCount: Int,
        val vaultCount: Int,
        val vaultMediaCount: Int
    )

    data class ImportIssue(
        val section: BackupSection,
        val item: String,
        val message: String
    )

    data class ImportResult(
        val settingsRestored: Int,
        val settingsSkipped: Int,
        val cloudFavoritesRestored: Int,
        val cloudFavoritesSkipped: Int,
        val cloudConfigsRestored: Int,
        val cloudConfigsSkipped: Int,
        val cloudConfigsRequiringAuthentication: Int,
        val vaultsRestored: Int,
        val vaultsSkipped: Int,
        val vaultMediaRestored: Int,
        val vaultMediaSkipped: Int,
        /** Device media URIs that match backed-up local favorites and need a system consent to favorite. */
        val pendingLocalFavoriteUris: List<Uri>,
        /** Non-fatal item-level failures. A successful result may still be partial when this is non-empty. */
        val issues: List<ImportIssue>
    )

    // ---------------------------------------------------------------------------------------------
    // Export
    // ---------------------------------------------------------------------------------------------

    /**
     * Exports a backup to [destination]. When [password] is non-null, the resulting
     * ZIP is encrypted with [BackupCrypto] (password-based AES-256-GCM).
     */
    suspend fun exportBackup(
        destination: Uri,
        selection: BackupSelection = BackupSelection(),
        password: String? = null,
        onProgress: BackupProgressListener = { _, _, _ -> }
    ): Result<ExportResult> = withContext(Dispatchers.IO) {
        var tempZip: File? = null
        try {
            val result: ExportResult
            if (password.isNullOrEmpty()) {
                val output = context.contentResolver.openOutputStream(destination)
                    ?: return@withContext Result.failure(IOException("Unable to open output stream"))
                output.use { result = buildArchive(it, selection, onProgress) }
            } else {
                // Build the plaintext ZIP into a temp file, then stream-encrypt into the destination.
                tempZip = File(context.cacheDir, "backup_export_${System.currentTimeMillis()}.zip")
                result = tempZip.outputStream().use { buildArchive(it, selection, onProgress) }
                val output = context.contentResolver.openOutputStream(destination)
                    ?: return@withContext Result.failure(IOException("Unable to open output stream"))
                output.use { out ->
                    tempZip.inputStream().use { input ->
                        BackupCrypto.encrypt(input, out, password)
                    }
                }
            }
            Result.success(result)
        } catch (e: Exception) {
            printError("ConfigBackupManager: export failed: ${e.message}")
            Result.failure(e)
        } finally {
            tempZip?.delete()
        }
    }

    private data class PlannedVault(
        val vault: Vault,
        val media: List<Pair<Media.EncryptedMedia2, VaultMediaEntry>>
    )

    private suspend fun buildArchive(
        output: OutputStream,
        selection: BackupSelection,
        onProgress: BackupProgressListener
    ): ExportResult {
        val settings = if (selection.settings) {
            exportSettings().also { onProgress(BackupSection.SETTINGS, it.size, it.size) }
        } else emptyMap()
        val localFavorites = if (selection.localFavorites) {
            exportLocalFavorites().also { onProgress(BackupSection.LOCAL_FAVORITES, it.size, it.size) }
        } else emptyList()
        val cloudFavorites = if (selection.cloudFavorites) {
            exportCloudFavorites().also { onProgress(BackupSection.CLOUD_FAVORITES, it.size, it.size) }
        } else emptyList()
        val cloudConfigs = if (selection.cloudConfigs) {
            exportCloudConfigs().also { onProgress(BackupSection.CLOUD_CONFIGS, it.size, it.size) }
        } else emptyList()

        val planned = if (selection.vaults) planVaults(selection) else emptyList()
        val vaultEntries = planned.map { pv ->
            VaultEntry(pv.vault.uuid.toString(), pv.vault.name, pv.media.map { it.second })
        }
        val totalVaultMedia = vaultEntries.sumOf { it.media.size }
        if (selection.vaults) onProgress(BackupSection.VAULTS, 0, totalVaultMedia)

        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            // Write the manifest FIRST so a backup can be inspected by reading only the leading bytes.
            val manifest = BackupManifest(
                appVersionName = appVersionName(),
                appVersionCode = appVersionCode(),
                exportedAt = System.currentTimeMillis(),
                settings = settings,
                localFavorites = localFavorites,
                cloudFavorites = cloudFavorites,
                cloudConfigs = cloudConfigs,
                vaults = vaultEntries
            )
            zip.putNextEntry(ZipEntry(BackupManifest.MANIFEST_NAME))
            zip.write(json.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
            zip.closeEntry()

            // Then stream the decrypted vault binaries, reporting per-item progress.
            var done = 0
            planned.forEach { pv ->
                pv.media.forEach { (media, entry) ->
                    zip.putNextEntry(ZipEntry(entry.fileName))
                    try {
                        val writtenDigest = VaultPayloadDigest(zip)
                        writeDecryptedVaultMedia(pv.vault, media, writtenDigest)
                        if (writtenDigest.byteCount != entry.archiveSize ||
                            writtenDigest.sha256() != entry.sha256
                        ) {
                            throw IOException("Vault media changed during export: ${media.id}")
                        }
                    } finally {
                        zip.closeEntry()
                    }
                    done++
                    onProgress(BackupSection.VAULTS, done, totalVaultMedia)
                }
            }
        }

        return ExportResult(
            settingsCount = settings.size,
            localFavoritesCount = localFavorites.size,
            cloudFavoritesCount = cloudFavorites.size,
            cloudConfigsCount = cloudConfigs.size,
            vaultCount = vaultEntries.size,
            vaultMediaCount = totalVaultMedia
        )
    }

    private suspend fun exportSettings(): Map<String, SettingValue> {
        val prefs = context.activeDataStore.data.first()
        return prefs.asMap().mapNotNull { (key, value) ->
            PortableBackupSettings.encode(key.name, value)?.let { key.name to it }
        }.toMap()
    }

    private suspend fun exportLocalFavorites(): List<LocalFavoriteEntry> {
        val favorites = mediaRepository.getFavorites(MediaOrder.Default)
            .first { it is Resource.Success || it is Resource.Error }
            .data.orEmpty()
        return favorites.map { media ->
            LocalFavoriteEntry(
                displayName = media.label,
                relativePath = media.relativePath,
                path = media.path,
                size = media.size,
                timestamp = media.timestamp
            )
        }
    }

    private suspend fun exportCloudFavorites(): List<CloudFavoriteEntry> {
        return cloudMediaDao.getFavoritesAsync().map { entity ->
            CloudFavoriteEntry(
                providerType = entity.providerType.name,
                remoteId = entity.remoteId,
                sourceAccountId = backupSourceAccountId(
                    entity.providerType.name,
                    entity.serverConfigId
                ),
                serverConfigId = entity.serverConfigId
            )
        }
    }

    private suspend fun exportCloudConfigs(): List<CloudConfigEntry> {
        return cloudServerConfigDao.getAll().first().map { it.toEntry() }
    }

    /** Gathers vault media metadata (no binary writes) so the manifest can be produced up front. */
    private suspend fun planVaults(selection: BackupSelection): List<PlannedVault> {
        val vaults = database.getVaultDao().getVaults().first()
            .filter { selection.isVaultSelected(it.uuid.toString()) }
        return vaults.map { vault ->
            val mediaList = database.getVaultDao().getMediaFromVault(vault.uuid).first()
            val pairs = mediaList.mapNotNull { media ->
                val encFile = with(keychainHolder) { vault.mediaFile(media.id) }
                if (!encFile.exists()) {
                    printError("ConfigBackupManager: missing vault file for media ${media.id}")
                    return@mapNotNull null
                }
                val entryName = "${BackupManifest.VAULTS_DIR}/${vault.uuid}/${media.id}"
                val digest = VaultPayloadDigest()
                writeDecryptedVaultMedia(vault, media, digest)
                media to media.toEntry(entryName).copy(
                    archiveSize = digest.byteCount,
                    sha256 = digest.sha256()
                )
            }
            PlannedVault(vault, pairs)
        }
    }

    private class VaultPayloadDigest(
        private val delegate: OutputStream? = null
    ) : OutputStream() {
        private val digest = MessageDigest.getInstance("SHA-256")
        var byteCount: Long = 0L
            private set

        override fun write(value: Int) {
            delegate?.write(value)
            digest.update(value.toByte())
            byteCount++
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            delegate?.write(bytes, offset, length)
            digest.update(bytes, offset, length)
            byteCount += length
        }

        fun sha256(): String = digest.digest().joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    /** Decrypts a single vault media item into [output]. */
    private fun writeDecryptedVaultMedia(
        vault: Vault,
        media: Media.EncryptedMedia2,
        output: OutputStream
    ) {
        val encFile = with(keychainHolder) { vault.mediaFile(media.id) }
        if (!encFile.exists()) {
            throw IOException("Missing vault file for media ${media.id}")
        }
        if (keychainHolder.isPortableFile(encFile)) {
            keychainHolder.decryptPortableStream(vault, encFile, output)
        } else {
            val decrypted = keychainHolder.decryptVaultMedia(encFile)
            val bytes = decrypted.bytes
            val tempFile = decrypted.tempFile
            try {
                when {
                    bytes != null -> output.write(bytes)
                    tempFile != null -> tempFile.inputStream().use { it.copyTo(output) }
                    else -> throw IOException("Unable to decrypt vault media ${media.id}")
                }
            } finally {
                // Always remove the transient decrypted file, even if the write fails.
                tempFile?.delete()
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Import
    // ---------------------------------------------------------------------------------------------

    /** Returns true if [source] points to a password-encrypted backup. */
    suspend fun isEncryptedBackup(source: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                BackupCrypto.isEncrypted(BackupCrypto.readHeader(input))
            } ?: false
        } catch (e: Exception) {
            printError("app.backup", "backup encryption probe failed", e)
            false
        }
    }

    /** Sentinel used to stop decryption early once the manifest has been recovered. */
    private class ManifestFound(val manifest: BackupManifest) : RuntimeException()

    /**
     * Reads only the manifest of [source] (without restoring anything) and summarizes its
     * contents. For encrypted backups [password] is required; a wrong password yields a
     * [BackupPasswordException] failure. The manifest is the first archive entry, so for
     * encrypted backups only the leading chunk(s) are decrypted.
     */
    suspend fun inspectBackup(
        source: Uri,
        password: String? = null
    ): Result<BackupContents> = withContext(Dispatchers.IO) {
        try {
            val encrypted = isEncryptedBackup(source)
            val manifest: BackupManifest = if (!encrypted) {
                context.contentResolver.openInputStream(source)?.use { input ->
                    readManifestFromZipStream(input)
                } ?: return@withContext Result.failure(IOException("Unable to open input stream"))
            } else {
                if (password.isNullOrEmpty()) {
                    return@withContext Result.failure(BackupPasswordException())
                }
                decryptManifestOnly(source, password)
            } ?: return@withContext Result.failure(IOException("Invalid backup: manifest.json missing"))
            BackupManifest.requireSupportedSchema(manifest.schemaVersion)

            Result.success(
                BackupContents(
                    schemaVersion = manifest.schemaVersion,
                    appVersionName = manifest.appVersionName,
                    exportedAt = manifest.exportedAt,
                    encrypted = encrypted,
                    settingsCount = manifest.settings.size,
                    localFavoritesCount = manifest.localFavorites.size,
                    cloudFavoritesCount = manifest.cloudFavorites.size,
                    cloudConfigsCount = manifest.cloudConfigs.size,
                    vaultCount = manifest.vaults.size,
                    vaultMediaCount = manifest.vaults.sumOf { it.media.size },
                    vaults = manifest.vaults.map {
                        VaultSummary(it.uuid, it.name, it.media.size)
                    }
                )
            )
        } catch (e: BackupPasswordException) {
            Result.failure(e)
        } catch (e: Exception) {
            printError("ConfigBackupManager: inspect failed: ${e.message}")
            Result.failure(e)
        }
    }

    /** Reads `manifest.json` from a (plaintext) ZIP stream without extracting other entries. */
    private fun readManifestFromZipStream(input: InputStream): BackupManifest? {
        ZipInputStream(BufferedInputStream(input)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (entry.name == BackupManifest.MANIFEST_NAME) {
                    val bytes = readBounded(zis, maxManifestBytes)
                    return json.decodeFromString(BackupManifest.serializer(), bytes.decodeToString())
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return null
    }

    /** Decrypts just enough of an encrypted backup to recover its manifest (the first entry). */
    private fun decryptManifestOnly(source: Uri, password: String): BackupManifest? {
        val accumulated = ByteArrayOutputStream()
        var found: BackupManifest? = null
        val sink = object : OutputStream() {
            override fun write(b: Int) {
                if (accumulated.size().toLong() >= maxManifestBytes) throw IOException("Backup manifest is too large")
                accumulated.write(b)
            }
            override fun write(b: ByteArray, off: Int, len: Int) {
                if (accumulated.size().toLong() + len > maxManifestBytes) throw IOException("Backup manifest is too large")
                accumulated.write(b, off, len)
                val manifest = runCatching {
                    readManifestFromZipStream(ByteArrayInputStream(accumulated.toByteArray()))
                }.getOrNull()
                if (manifest != null) throw ManifestFound(manifest)
            }
        }
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                BackupCrypto.decrypt(input, sink, password)
            }
        } catch (e: ManifestFound) {
            found = e.manifest
        }
        return found ?: runCatching {
            readManifestFromZipStream(ByteArrayInputStream(accumulated.toByteArray()))
        }.getOrNull()
    }

    /**
     * Imports a backup from [source]. When the backup is password-encrypted, [password]
     * must be supplied; an incorrect password yields a [BackupPasswordException] failure.
     */
    suspend fun importBackup(
        source: Uri,
        selection: BackupSelection = BackupSelection(),
        password: String? = null,
        onProgress: BackupProgressListener = { _, _, _ -> }
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        val tmpDir = File(context.cacheDir, "backup_import_${System.currentTimeMillis()}")
        val dataDir = File(tmpDir, "data")
        dataDir.mkdirs()
        try {
            val encrypted = isEncryptedBackup(source)
            if (encrypted) {
                if (password.isNullOrEmpty()) {
                    return@withContext Result.failure(BackupPasswordException())
                }
                val decryptedZip = File(tmpDir, "backup.zip")
                context.contentResolver.openInputStream(source)?.use { input ->
                    decryptedZip.outputStream().use { out ->
                        BackupCrypto.decrypt(input, LimitedOutputStream(out, maxArchiveBytes), password)
                    }
                } ?: return@withContext Result.failure(IOException("Unable to open input stream"))
                decryptedZip.inputStream().use { extractZipStream(it, dataDir) }
                decryptedZip.delete()
            } else {
                context.contentResolver.openInputStream(source)?.use { input ->
                    extractZipStream(input, dataDir)
                } ?: return@withContext Result.failure(IOException("Unable to open input stream"))
            }

            val manifestFile = File(dataDir, BackupManifest.MANIFEST_NAME)
            if (!manifestFile.exists()) {
                return@withContext Result.failure(IOException("Invalid backup: manifest.json missing"))
            }
            val manifest = json.decodeFromString(BackupManifest.serializer(), manifestFile.readText())
            BackupManifest.requireSupportedSchema(manifest.schemaVersion)
            preflightAccountIdentities(manifest)

            val selectedVaults = if (selection.vaults) {
                manifest.vaults.filter { selection.isVaultSelected(it.uuid) }
            } else {
                emptyList()
            }
            // Validate every selected binary and destination before DataStore, Room, or keychain mutation.
            preflightVaults(manifest.schemaVersion, selectedVaults, dataDir)
            preflightVaultDestinations(selectedVaults)

            val issues = mutableListOf<ImportIssue>()
            var settingsResult = RestoreCount()
            var cloudFavoriteResult = RestoreCount()
            var cloudConfigResult = CloudConfigRestoreResult()
            var vaultResult = VaultRestoreResult()
            var pendingLocalFavoriteUris: List<Uri> = emptyList()

            val existingConfigs = cloudServerConfigDao.getAll().first()
            var accountMappings = matchExistingAccounts(manifest.cloudConfigs, existingConfigs)

            if (selection.settings) {
                settingsResult = restoreSettings(manifest, issues)
                onProgress(BackupSection.SETTINGS, settingsResult.restored, manifest.settings.size)
            }
            if (selection.cloudConfigs) {
                cloudConfigResult = restoreCloudConfigs(manifest, existingConfigs, issues)
                accountMappings = accountMappings.mergedWith(cloudConfigResult.accountMappings)
                onProgress(
                    BackupSection.CLOUD_CONFIGS,
                    cloudConfigResult.restored,
                    manifest.cloudConfigs.size
                )
            }
            if (selection.cloudFavorites) {
                cloudFavoriteResult = restoreCloudFavorites(manifest, accountMappings, issues)
                onProgress(
                    BackupSection.CLOUD_FAVORITES,
                    cloudFavoriteResult.restored,
                    manifest.cloudFavorites.size
                )
            }
            if (selection.vaults) {
                val total = selectedVaults.sumOf { it.media.size }
                onProgress(BackupSection.VAULTS, 0, total)
                vaultResult = restoreVaults(selectedVaults, dataDir, issues) { done ->
                    onProgress(BackupSection.VAULTS, done, total)
                }
            }
            if (selection.localFavorites) {
                pendingLocalFavoriteUris = matchLocalFavorites(manifest)
                onProgress(
                    BackupSection.LOCAL_FAVORITES,
                    pendingLocalFavoriteUris.size,
                    manifest.localFavorites.size
                )
            }

            Result.success(
                ImportResult(
                    settingsRestored = settingsResult.restored,
                    settingsSkipped = settingsResult.skipped,
                    cloudFavoritesRestored = cloudFavoriteResult.restored,
                    cloudFavoritesSkipped = cloudFavoriteResult.skipped,
                    cloudConfigsRestored = cloudConfigResult.restored,
                    cloudConfigsSkipped = cloudConfigResult.skipped,
                    cloudConfigsRequiringAuthentication = cloudConfigResult.reauthenticationRequired,
                    vaultsRestored = vaultResult.vaultsRestored,
                    vaultsSkipped = vaultResult.vaultsSkipped,
                    vaultMediaRestored = vaultResult.mediaRestored,
                    vaultMediaSkipped = vaultResult.mediaSkipped,
                    pendingLocalFavoriteUris = pendingLocalFavoriteUris,
                    issues = issues
                )
            )
        } catch (e: Exception) {
            printError("ConfigBackupManager: import failed: ${e.message}")
            Result.failure(e)
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    private fun extractZipStream(input: InputStream, destDir: File) {
        ZipInputStream(BufferedInputStream(input)).use { zis ->
            val destCanonical = destDir.canonicalPath
            val extractedEntries = mutableSetOf<String>()
            var totalBytes = 0L
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (extractedEntries.size >= maxArchiveEntries) throw IOException("Backup contains too many entries")
                if (!extractedEntries.add(entry.name)) {
                    throw IOException("Duplicate zip entry: ${entry.name}")
                }
                if (entry.size > maxEntryBytes) throw IOException("Backup entry is too large: ${entry.name}")
                val outFile = File(destDir, entry.name)
                if (!outFile.canonicalPath.startsWith(destCanonical + File.separator) &&
                    outFile.canonicalPath != destCanonical
                ) {
                    throw IOException("Illegal zip entry path: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    var entryBytes = 0L
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    outFile.outputStream().use { output ->
                        while (true) {
                            val read = zis.read(buffer)
                            if (read < 0) break
                            entryBytes += read
                            totalBytes += read
                            if (entryBytes > maxEntryBytes || totalBytes > maxArchiveBytes) {
                                throw IOException("Backup exceeds extraction limits")
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun readBounded(input: InputStream, maxBytes: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (output.size().toLong() + read > maxBytes) throw IOException("Backup manifest is too large")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private class LimitedOutputStream(
        private val delegate: OutputStream,
        private val maxBytes: Long
    ) : OutputStream() {
        private var written = 0L

        override fun write(value: Int) {
            ensureCapacity(1)
            delegate.write(value)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            ensureCapacity(length)
            delegate.write(buffer, offset, length)
        }

        override fun flush() = delegate.flush()

        private fun ensureCapacity(length: Int) {
            written += length
            if (written > maxBytes) throw IOException("Decrypted backup exceeds size limit")
        }
    }

    private data class RestoreCount(
        val restored: Int = 0,
        val skipped: Int = 0
    )

    private data class CloudConfigRestoreResult(
        val restored: Int = 0,
        val skipped: Int = 0,
        val reauthenticationRequired: Int = 0,
        val accountMappings: BackupAccountMappings = BackupAccountMappings()
    )

    private data class VaultRestoreResult(
        val vaultsRestored: Int = 0,
        val vaultsSkipped: Int = 0,
        val mediaRestored: Int = 0,
        val mediaSkipped: Int = 0
    )

    private fun preflightAccountIdentities(manifest: BackupManifest) {
        val sourceAccounts = mutableSetOf<String>()
        val sourceConfigIds = mutableSetOf<Long>()
        manifest.cloudConfigs.forEach { config ->
            if (config.sourceAccountId.isNotBlank() && !sourceAccounts.add(config.sourceAccountId)) {
                throw IOException("Invalid backup: duplicate source account ${config.sourceAccountId}")
            }
            if (config.sourceConfigId > 0L && !sourceConfigIds.add(config.sourceConfigId)) {
                throw IOException("Invalid backup: duplicate source config id ${config.sourceConfigId}")
            }
        }
    }

    private fun preflightVaults(schemaVersion: Int, vaults: List<VaultEntry>, dataDir: File) {
        val seenVaults = mutableSetOf<String>()
        val seenEntries = mutableSetOf<String>()
        vaults.forEach { vault ->
            if (!seenVaults.add(vault.uuid)) {
                throw IOException("Invalid backup: duplicate vault ${vault.uuid}")
            }
            runCatching { UUID.fromString(vault.uuid) }.getOrElse {
                throw IOException("Invalid backup: malformed vault id ${vault.uuid}", it)
            }
            val expectedPrefix = "${BackupManifest.VAULTS_DIR}/${vault.uuid}/"
            val seenMediaIds = mutableSetOf<Long>()
            vault.media.forEach { media ->
                if (!seenMediaIds.add(media.id)) {
                    throw IOException("Invalid backup: duplicate media ${media.id} in vault ${vault.uuid}")
                }
                val expectedEntry = "$expectedPrefix${media.id}"
                if (media.fileName != expectedEntry || !seenEntries.add(media.fileName)) {
                    throw IOException("Invalid backup: illegal or duplicate vault entry ${media.fileName}")
                }
                val payload = File(dataDir, media.fileName)
                if (!payload.isFile) {
                    throw IOException("Invalid backup: vault payload missing: ${media.fileName}")
                }
                if (schemaVersion >= 2) {
                    if (media.archiveSize < 0L || !media.sha256.matches(Regex("[0-9a-f]{64}"))) {
                        throw IOException("Invalid backup: vault integrity metadata missing: ${media.fileName}")
                    }
                    if (payload.length() != media.archiveSize) {
                        throw IOException("Invalid backup: vault payload size mismatch: ${media.fileName}")
                    }
                    if (calculateSha256(payload) != media.sha256) {
                        throw IOException("Invalid backup: vault payload checksum mismatch: ${media.fileName}")
                    }
                }
            }
        }
    }

    private suspend fun preflightVaultDestinations(vaults: List<VaultEntry>) {
        vaults.forEach { entry ->
            val uuid = UUID.fromString(entry.uuid)
            val existingVault = database.getVaultDao().getVault(uuid)
            if (existingVault != null || entry.media.any {
                    database.getVaultDao().mediaExistsInVault(uuid, it.id)
                }
            ) {
                throw IOException(
                    "Import would overwrite existing vault data: ${entry.name.ifBlank { entry.uuid }}"
                )
            }
        }
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    private suspend fun restoreSettings(
        manifest: BackupManifest,
        issues: MutableList<ImportIssue>
    ): RestoreCount {
        var restored = 0
        var skipped = 0
        if (manifest.settings.isEmpty()) return RestoreCount()
        context.activeDataStore.edit { prefs ->
            manifest.settings.forEach { (name, value) ->
                if (applySetting(prefs, name, value)) {
                    restored++
                } else {
                    skipped++
                    issues += ImportIssue(
                        BackupSection.SETTINGS,
                        name,
                        context.getString(R.string.backup_issue_invalid_setting),
                    )
                }
            }
        }
        return RestoreCount(restored, skipped)
    }

    private fun applySetting(prefs: MutablePreferences, name: String, sv: SettingValue): Boolean {
        val decoded = PortableBackupSettings.decode(name, sv) ?: return false
        return try {
            when (decoded.type) {
                PortableSettingType.BOOLEAN -> prefs[booleanPreferencesKey(name)] = decoded.value as Boolean
                PortableSettingType.INT -> prefs[intPreferencesKey(name)] = decoded.value as Int
                PortableSettingType.STRING -> prefs[stringPreferencesKey(name)] = decoded.value as String
                else -> return false
            }
            true
        } catch (e: Exception) {
            printError("ConfigBackupManager: skipping setting '$name': ${e.message}")
            false
        }
    }

    private fun matchExistingAccounts(
        entries: List<CloudConfigEntry>,
        existing: List<CloudServerConfigEntity>
    ): BackupAccountMappings {
        val bySource = mutableMapOf<String, Long>()
        entries.forEach { entry ->
            val match = findUnambiguousCloudAccount(
                target = entry.accountIdentity(),
                existing = existing,
                identity = { it.accountIdentity() }
            )
            val sourceAccount = entry.resolvedSourceAccountId()
            if (sourceAccount.isNotEmpty() && match != null) {
                bySource[sourceAccount] = match.id
            }
        }
        return BackupAccountMappings(
            bySourceAccount = bySource,
            destinationIdsByProvider = existing.groupBy { it.providerType.name }
                .mapValues { (_, configs) -> configs.map { it.id }.toSet() }
        )
    }

    private suspend fun restoreCloudConfigs(
        manifest: BackupManifest,
        existingConfigs: List<CloudServerConfigEntity>,
        issues: MutableList<ImportIssue>
    ): CloudConfigRestoreResult {
        var restored = 0
        var skipped = 0
        var reauthenticationRequired = 0
        val bySource = mutableMapOf<String, Long>()
        val byProvider = mutableMapOf<String, MutableSet<Long>>()
        val knownConfigs = existingConfigs.toMutableList()
        manifest.cloudConfigs.forEach { entry ->
            val providerType = runCatching { ProviderType.valueOf(entry.providerType) }.getOrNull()
            if (providerType == null) {
                skipped++
                issues += ImportIssue(
                    BackupSection.CLOUD_CONFIGS,
                    entry.displayName,
                    context.getString(
                        R.string.backup_issue_unsupported_provider,
                        entry.providerType,
                    )
                )
                return@forEach
            }
            val matches = knownConfigs.filter { it.accountIdentity() == entry.accountIdentity() }
            if (matches.size > 1) {
                skipped++
                issues += ImportIssue(
                    BackupSection.CLOUD_CONFIGS,
                    entry.displayName,
                    context.getString(R.string.backup_issue_config_import_failed)
                )
                return@forEach
            }
            val existing = matches.singleOrNull()
            val importedCredentialsRequireAuth = entry.requiresReauthentication ||
                !entry.apiKey.isNullOrBlank() || !entry.encryptedPassword.isNullOrBlank()
            val requiresAuth = importedCredentialsRequireAuth &&
                existing?.hasStoredCredentials() != true
            try {
                // Keystore ciphertext from another installation is not portable. Never import it.
                val restoredEntity = entry.toEntity(providerType, requiresAuth, existing)
                val destinationId = if (existing == null) {
                    cloudServerConfigDao.insert(restoredEntity).also { insertedId ->
                        if (insertedId <= 0L) {
                            error(context.getString(R.string.backup_issue_config_not_inserted))
                        }
                    }
                } else {
                    cloudServerConfigDao.update(restoredEntity)
                    existing.id
                }
                providerInitializer.applyRestoredAccount(destinationId)
                restored++
                if (requiresAuth) reauthenticationRequired++
                entry.resolvedSourceAccountId().takeIf { it.isNotEmpty() }?.let {
                    bySource[it] = destinationId
                }
                byProvider.getOrPut(providerType.name) { mutableSetOf() }.add(destinationId)
                knownConfigs.removeAll { it.id == destinationId }
                knownConfigs += restoredEntity.copy(id = destinationId)
            } catch (e: Exception) {
                skipped++
                issues += ImportIssue(
                    BackupSection.CLOUD_CONFIGS,
                    entry.displayName,
                    e.message ?: context.getString(R.string.backup_issue_config_import_failed)
                )
            }
        }
        syncScheduler.reconcile()
        return CloudConfigRestoreResult(
            restored = restored,
            skipped = skipped,
            reauthenticationRequired = reauthenticationRequired,
            accountMappings = BackupAccountMappings(bySource, byProvider)
        )
    }

    private suspend fun restoreCloudFavorites(
        manifest: BackupManifest,
        mappings: BackupAccountMappings,
        issues: MutableList<ImportIssue>
    ): RestoreCount {
        var restored = 0
        var skipped = 0
        val pending = mutableSetOf<PendingCloudFavorite>()
        manifest.cloudFavorites.forEach { favorite ->
            val providerType = runCatching { ProviderType.valueOf(favorite.providerType) }.getOrNull()
            val destinationConfigId = providerType?.let {
                mappings.resolveDestination(
                    favorite.resolvedSourceAccountId(),
                    favorite.providerType
                )
            }
            if (providerType == null || destinationConfigId == null) {
                skipped++
                issues += ImportIssue(
                    BackupSection.CLOUD_FAVORITES,
                    favorite.remoteId,
                    context.getString(R.string.backup_issue_no_destination_account)
                )
                return@forEach
            }
            try {
                val updated = cloudMediaDao.updateFavoriteAndCount(
                    favorite.remoteId,
                    providerType,
                    destinationConfigId,
                    true
                )
                if (updated > 0) {
                    restored += updated
                } else {
                    pending += PendingCloudFavorite(
                        providerType = providerType.name,
                        serverConfigId = destinationConfigId,
                        remoteId = favorite.remoteId
                    )
                }
            } catch (e: Exception) {
                skipped++
                issues += ImportIssue(
                    BackupSection.CLOUD_FAVORITES,
                    favorite.remoteId,
                    e.message ?: context.getString(R.string.backup_issue_favorite_update_failed)
                )
            }
        }
        if (pending.isNotEmpty()) {
            try {
                pendingCloudFavoriteStore.enqueue(pending)
                restored += pending.size
            } catch (e: Exception) {
                skipped += pending.size
                pending.forEach { favorite ->
                    issues += ImportIssue(
                        BackupSection.CLOUD_FAVORITES,
                        favorite.remoteId,
                        e.message ?: context.getString(R.string.backup_issue_favorite_update_failed)
                    )
                }
            }
        }
        return RestoreCount(restored, skipped)
    }

    private suspend fun restoreVaults(
        vaults: List<VaultEntry>,
        tmpDir: File,
        issues: MutableList<ImportIssue>,
        onMediaRestored: (done: Int) -> Unit = {}
    ): VaultRestoreResult {
        var vaultCount = 0
        var vaultsSkipped = 0
        var mediaCount = 0
        var mediaSkipped = 0
        vaults.forEach { vaultEntry ->
            val uuid = UUID.fromString(vaultEntry.uuid)
            val vault = Vault(uuid = uuid, name = vaultEntry.name)
            try {
                // Portable vault encryption allows the staged plaintext to be re-encrypted safely.
                var keychainFailure: String? = null
                keychainHolder.writeVaultInfo(
                    vault = vault,
                    transferable = true,
                    onFailed = { keychainFailure = it }
                )
                keychainFailure?.let { error(it) }
                database.getVaultDao().insertVault(vault)
                vaultCount++
            } catch (e: Exception) {
                vaultsSkipped++
                mediaSkipped += vaultEntry.media.size
                issues += ImportIssue(
                    BackupSection.VAULTS,
                    vaultEntry.name,
                    e.message ?: context.getString(R.string.backup_issue_vault_creation_failed)
                )
                return@forEach
            }

            vaultEntry.media.forEach { mediaEntry ->
                val srcFile = File(tmpDir, mediaEntry.fileName)
                val outFile = with(keychainHolder) { vault.mediaFile(mediaEntry.id) }
                if (outFile.exists()) outFile.delete()
                try {
                    srcFile.inputStream().use { input ->
                        keychainHolder.encryptPortableStream(vault, input, outFile)
                    }
                    outFile.setLastModified(System.currentTimeMillis())
                    database.getVaultDao().addMediaToVault(mediaEntry.toEncryptedMedia2(uuid))
                    mediaCount++
                    onMediaRestored(mediaCount)
                } catch (e: Exception) {
                    mediaSkipped++
                    issues += ImportIssue(
                        BackupSection.VAULTS,
                        mediaEntry.label,
                        e.message ?: context.getString(R.string.backup_issue_vault_media_import_failed)
                    )
                    printError("ConfigBackupManager: failed to import vault media ${mediaEntry.id}: ${e.message}")
                    outFile.delete()
                }
            }
        }
        return VaultRestoreResult(vaultCount, vaultsSkipped, mediaCount, mediaSkipped)
    }

    private fun CloudConfigEntry.accountIdentity(): BackupCloudAccountIdentity =
        backupCloudAccountIdentity(providerType, serverUrl, username)

    private fun CloudServerConfigEntity.accountIdentity(): BackupCloudAccountIdentity =
        backupCloudAccountIdentity(providerType.name, serverUrl, username)

    private fun CloudServerConfigEntity.hasStoredCredentials(): Boolean =
        !apiKey.isNullOrBlank() || !encryptedPassword.isNullOrBlank()

    private fun CloudConfigEntry.resolvedSourceAccountId(): String = sourceAccountId.ifBlank {
        if (sourceConfigId > 0L) backupSourceAccountId(providerType, sourceConfigId) else ""
    }

    private fun CloudFavoriteEntry.resolvedSourceAccountId(): String = sourceAccountId.ifBlank {
        if (serverConfigId > 0L) backupSourceAccountId(providerType, serverConfigId) else ""
    }

    private suspend fun matchLocalFavorites(manifest: BackupManifest): List<Uri> {
        if (manifest.localFavorites.isEmpty()) return emptyList()
        val deviceMedia = mediaRepository.getMedia()
            .first { it is Resource.Success || it is Resource.Error }
            .data.orEmpty()
        if (deviceMedia.isEmpty()) return emptyList()

        val byPathName = deviceMedia.associateBy { it.relativePath + "/" + it.label }
        val result = LinkedHashSet<Uri>()
        manifest.localFavorites.forEach { fav ->
            val match = byPathName[fav.relativePath + "/" + fav.displayName]
                ?: deviceMedia.firstOrNull { it.label == fav.displayName && it.size == fav.size }
            if (match != null) result.add(match.uri)
        }
        printInfo("ConfigBackupManager: matched ${result.size}/${manifest.localFavorites.size} local favorites")
        return result.toList()
    }

    // ---------------------------------------------------------------------------------------------
    // Mappers / helpers
    // ---------------------------------------------------------------------------------------------

    private fun CloudServerConfigEntity.toEntry() = CloudConfigEntry(
        providerType = providerType.name,
        serverUrl = serverUrl,
        sourceConfigId = id,
        sourceAccountId = backupSourceAccountId(providerType.name, id),
        requiresReauthentication = !apiKey.isNullOrBlank() || !encryptedPassword.isNullOrBlank(),
        // Android Keystore ciphertext is device-bound, so credentials are intentionally omitted.
        apiKey = null,
        username = username,
        encryptedPassword = null,
        displayName = displayName,
        isActive = isActive,
        lastConnected = lastConnected,
        syncEnabled = syncEnabled,
        wifiOnly = wifiOnly,
        syncIntervalMinutes = syncIntervalMinutes,
        syncFolders = syncFolders,
        cellularPhotos = cellularPhotos,
        cellularVideos = cellularVideos,
        requireCharging = requireCharging,
        syncAlbums = syncAlbums,
        showBackupTotalProgress = showBackupTotalProgress,
        showBackupDetailProgress = showBackupDetailProgress,
        notifyBackupFailures = notifyBackupFailures,
        autoUrlSwitch = autoUrlSwitch,
        localWifiSsid = localWifiSsid,
        localServerUrl = localServerUrl,
        externalUrls = externalUrls,
        loadPreviewImage = loadPreviewImage,
        loadOriginalImage = loadOriginalImage,
        autoPlayVideos = autoPlayVideos,
        loopVideos = loopVideos,
        forceOriginalVideo = forceOriginalVideo,
        verboseLogging = verboseLogging,
        syncRemoteDeletions = syncRemoteDeletions,
        preferRemoteImages = preferRemoteImages,
        readOnlyMode = readOnlyMode,
        downloadRemoteEnabled = downloadRemoteEnabled,
        downloadVideos = downloadVideos,
        uploadBasePath = uploadBasePath,
        uploadVideosPath = uploadVideosPath
    )

    private fun CloudConfigEntry.toEntity(
        providerType: ProviderType,
        requiresAuthentication: Boolean,
        existing: CloudServerConfigEntity?
    ) = CloudServerConfigEntity(
        id = existing?.id ?: 0L,
        providerType = providerType,
        serverUrl = serverUrl,
        apiKey = existing?.apiKey,
        username = username,
        encryptedPassword = existing?.encryptedPassword,
        displayName = displayName,
        isActive = isActive && !requiresAuthentication,
        lastConnected = existing?.lastConnected ?: 0L,
        syncEnabled = syncEnabled,
        wifiOnly = wifiOnly,
        syncIntervalMinutes = syncIntervalMinutes,
        syncFolders = syncFolders,
        cellularPhotos = cellularPhotos,
        cellularVideos = cellularVideos,
        requireCharging = requireCharging,
        syncAlbums = syncAlbums,
        showBackupTotalProgress = showBackupTotalProgress,
        showBackupDetailProgress = showBackupDetailProgress,
        notifyBackupFailures = notifyBackupFailures,
        autoUrlSwitch = autoUrlSwitch,
        localWifiSsid = localWifiSsid,
        localServerUrl = localServerUrl,
        externalUrls = externalUrls,
        loadPreviewImage = loadPreviewImage,
        loadOriginalImage = loadOriginalImage,
        autoPlayVideos = autoPlayVideos,
        loopVideos = loopVideos,
        forceOriginalVideo = forceOriginalVideo,
        verboseLogging = verboseLogging,
        syncRemoteDeletions = syncRemoteDeletions,
        preferRemoteImages = preferRemoteImages,
        readOnlyMode = readOnlyMode,
        downloadRemoteEnabled = downloadRemoteEnabled,
        downloadVideos = downloadVideos,
        uploadBasePath = uploadBasePath,
        uploadVideosPath = uploadVideosPath
    )

    private fun Media.EncryptedMedia2.toEntry(fileName: String) = VaultMediaEntry(
        id = id,
        label = label,
        path = path,
        relativePath = relativePath,
        albumID = albumID,
        albumLabel = albumLabel,
        timestamp = timestamp,
        expiryTimestamp = expiryTimestamp,
        takenTimestamp = takenTimestamp,
        fullDate = fullDate,
        mimeType = mimeType,
        favorite = favorite,
        trashed = trashed,
        size = size,
        duration = duration,
        fileName = fileName
    )

    private fun VaultMediaEntry.toEncryptedMedia2(uuid: UUID) = Media.EncryptedMedia2(
        id = id,
        label = label,
        uuid = uuid,
        path = path,
        relativePath = relativePath,
        albumID = albumID,
        albumLabel = albumLabel,
        timestamp = timestamp,
        expiryTimestamp = expiryTimestamp,
        takenTimestamp = takenTimestamp,
        fullDate = fullDate,
        mimeType = mimeType,
        favorite = favorite,
        trashed = trashed,
        size = size,
        duration = duration
    )

    private fun appVersionName(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (_: Exception) {
        ""
    }

    @Suppress("DEPRECATION")
    private fun appVersionCode(): Long = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
    } catch (_: Exception) {
        0L
    }
}

/** Persists cloud favorites until their account's media has been indexed locally. */
@Singleton
class PendingCloudFavoriteStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = SetSerializer(PendingCloudFavorite.serializer())

    internal suspend fun enqueue(favorites: Set<PendingCloudFavorite>) {
        if (favorites.isEmpty()) return
        context.activeDataStore.edit { prefs ->
            val pending = decode(prefs[PENDING_FAVORITES_KEY]) + favorites
            prefs[PENDING_FAVORITES_KEY] = json.encodeToString(serializer, pending)
        }
    }

    suspend fun removeForAccount(serverConfigId: Long) {
        context.activeDataStore.edit { prefs ->
            val remaining = decode(prefs[PENDING_FAVORITES_KEY])
                .filterNotTo(mutableSetOf()) { it.serverConfigId == serverConfigId }
            if (remaining.isEmpty()) prefs.remove(PENDING_FAVORITES_KEY)
            else prefs[PENDING_FAVORITES_KEY] = json.encodeToString(serializer, remaining)
        }
    }

    suspend fun applyForAccount(
        providerType: ProviderType,
        serverConfigId: Long,
        cloudMediaDao: CloudMediaDao
    ): Int {
        val pending = decode(
            context.activeDataStore.data.first()[PENDING_FAVORITES_KEY]
        )
        if (pending.isEmpty()) return 0
        val result = applyPendingFavorites(
            pending = pending,
            providerType = providerType.name,
            serverConfigId = serverConfigId
        ) { favorite ->
            cloudMediaDao.updateFavoriteAndCount(
                remoteId = favorite.remoteId,
                providerType = providerType,
                serverConfigId = serverConfigId,
                favorite = true
            ) > 0
        }
        if (result.applied.isNotEmpty()) {
            context.activeDataStore.edit { prefs ->
                val remaining = decode(prefs[PENDING_FAVORITES_KEY]) - result.applied
                if (remaining.isEmpty()) {
                    prefs.remove(PENDING_FAVORITES_KEY)
                } else {
                    prefs[PENDING_FAVORITES_KEY] = json.encodeToString(serializer, remaining)
                }
            }
        }
        return result.applied.size
    }

    private fun decode(value: String?): Set<PendingCloudFavorite> = value?.let {
        runCatching { json.decodeFromString(serializer, it) }.getOrDefault(emptySet())
    }.orEmpty()

    private companion object {
        val PENDING_FAVORITES_KEY = stringPreferencesKey("backup_pending_cloud_favorites_v1")
    }
}
