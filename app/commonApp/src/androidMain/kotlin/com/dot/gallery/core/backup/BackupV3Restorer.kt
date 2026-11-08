/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import android.content.Context
import android.net.Uri
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.stableIdHash
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.dot.gallery.cloud.di.CloudProviderInitializer
import com.dot.gallery.cloud.sync.CloudSyncScheduler
import com.dot.gallery.core.Resource
import com.dot.gallery.core.backup.ConfigBackupManager.ImportIssue
import com.dot.gallery.core.backup.ConfigBackupManager.ImportResult
import com.dot.gallery.core.activeDataStore
import com.dot.gallery.core.logging.printError
import com.dot.gallery.data.InternalDatabase
import com.dot.gallery.data.dao.CloudMediaDao
import com.dot.gallery.data.dao.CloudServerConfigDao
import com.dot.gallery.data.data_source.KeychainHolder
import com.dot.gallery.data.entity.CloudAlbumSyncEntity
import com.dot.gallery.data.entity.CloudDeleteLocalPrefEntity
import com.dot.gallery.data.entity.CloudOfflinePinEntity
import com.dot.gallery.data.entity.CloudUploadPrefEntity
import com.dot.gallery.data.entity.FaceExclusionEntity
import com.dot.gallery.data.entity.FaceLinkEntity
import com.dot.gallery.data.entity.FaceLinkKind
import com.dot.gallery.data.entity.FaceSuppressionEntity
import com.dot.gallery.data.entity.MediaCaptureTimeEntity
import com.dot.gallery.data.entity.MediaMetadataCore
import com.dot.gallery.data.entity.MediaMetadataFlags
import com.dot.gallery.data.entity.MediaMetadataVideo
import com.dot.gallery.data.entity.PersonEntity
import com.dot.gallery.data.repository.MediaRepository
import com.dot.gallery.domain.model.AlbumGroupMember
import com.dot.gallery.domain.model.AlbumSectionMember
import com.dot.gallery.domain.model.AlbumThumbnail
import com.dot.gallery.domain.model.CaptureTimeOrigin
import com.dot.gallery.domain.model.Category
import com.dot.gallery.domain.model.Collection
import com.dot.gallery.domain.model.CollectionAlbum
import com.dot.gallery.domain.model.CollectionExcludedMedia
import com.dot.gallery.domain.model.CollectionMedia
import com.dot.gallery.domain.model.EditedMedia
import com.dot.gallery.domain.model.EncryptedMedia2
import com.dot.gallery.domain.model.IgnoredAlbum
import com.dot.gallery.domain.model.LockedAlbum
import com.dot.gallery.domain.model.MediaCategory
import com.dot.gallery.domain.model.MergedSubfolderAlbum
import com.dot.gallery.domain.model.PinnedAlbum
import com.dot.gallery.domain.model.SearchHistory
import com.dot.gallery.domain.model.TimelineSettings
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.domain.util.MediaOrder
import com.dot.gallery.feature.vault.utils.VaultPasswordManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.uuid.Uuid

/**
 * Restores a schema-v3 archive with merge-only semantics: existing rows are never deleted or
 * overwritten; conflicts are counted and reported. Every write is idempotent so a resumed job
 * can replay a section.
 */
internal class BackupV3Restorer(
    private val context: Context,
    private val database: InternalDatabase,
    private val keychainHolder: KeychainHolder,
    private val cloudMediaDao: CloudMediaDao,
    private val cloudServerConfigDao: CloudServerConfigDao,
    private val mediaRepository: MediaRepository,
    private val pendingCloudFavoriteStore: PendingCloudFavoriteStore,
    private val providerInitializer: CloudProviderInitializer,
    private val syncScheduler: CloudSyncScheduler,
    private val legacy: BackupLegacyBridge,
    private val restoreOrder: List<BackupSection>
) {
    private val issues = mutableListOf<ImportIssue>()

    suspend fun restore(
        source: Uri,
        probe: BackupProbe,
        selection: BackupSelection,
        password: String?,
        onProgress: BackupProgressListener
    ): ImportResult = withContext(Dispatchers.IO) {
        val stamp = System.currentTimeMillis()
        val tmpDir = File(context.cacheDir, "backup_import_$stamp").apply { mkdirs() }
        val dataDir = File(tmpDir, "data").apply { mkdirs() }
        try {
            val encrypted = probe.encrypted
            if (encrypted && password.isNullOrEmpty()) throw BackupPasswordException()

            val payload = File(tmpDir, "payload.zip")
            context.contentResolver.openInputStream(source)?.use { raw ->
                val buffered = BufferedInputStream(raw)
                val preview = skipPreviewHeader(buffered)
                if (preview != null && preview.encrypted != encrypted) {
                    throw IOException("Backup header and payload disagree about encryption")
                }
                val pushback = java.io.PushbackInputStream(buffered, PAYLOAD_MAGIC_SIZE)
                val prefix = ByteArray(PAYLOAD_MAGIC_SIZE)
                val prefixRead = readUpTo(pushback, prefix)
                if (prefixRead > 0) pushback.unread(prefix, 0, prefixRead)
                val payloadIsEncrypted = prefixRead >= PAYLOAD_MAGIC_SIZE &&
                    prefix.copyOf(PAYLOAD_MAGIC_SIZE)
                        .contentEquals(BackupCrypto.MAGIC_V2_BYTES)
                if (payloadIsEncrypted != encrypted) {
                    throw IOException("Backup payload encryption does not match its header")
                }
                payload.outputStream().use { output ->
                    if (encrypted) {
                        BackupCrypto.decryptV2(pushback, output, password!!)
                    } else {
                        pushback.copyTo(output)
                    }
                }
            } ?: throw IOException("Unable to open input stream")

            payload.inputStream().use { extractZip(it, dataDir) }
            payload.delete()

            val manifestFile = File(dataDir, BackupManifest.MANIFEST_NAME)
            if (!manifestFile.isFile) {
                throw IOException("Invalid backup: manifest.json missing")
            }
            val manifest = backupJson.decodeFromString(
                BackupManifest.serializer(),
                manifestFile.readText()
            )
            BackupManifest.requireSupportedSchema(manifest.schemaVersion)
            if (!BackupManifest.supportsSectionFiles(manifest.schemaVersion)) {
                throw IOException("Unexpected legacy archive")
            }

            val sectionResults = LinkedHashMap<BackupSection, SectionRestoreResult>()
            val existingConfigs = cloudServerConfigDao.getAll().first()

            // Cloud accounts must be restored first so every other section can resolve accounts.
            var accountMappings = BackupAccountMappings()
            if (manifest.sections.containsKey(BackupSection.CLOUD_CONFIGS.name)) {
                val entries = mutableListOf<CloudConfigEntry>()
                readSection(
                    BackupSection.CLOUD_CONFIGS,
                    manifest,
                    dataDir,
                    CloudConfigEntry.serializer()
                ) { entries += it }
                accountMappings = legacy.bridgeMatchExistingAccounts(entries, existingConfigs)
                if (selection.cloudConfigs) {
                    val result = legacy.bridgeRestoreCloudConfigs(entries, existingConfigs, issues)
                    accountMappings = result.accountMappings
                    sectionResults[BackupSection.CLOUD_CONFIGS] = SectionRestoreResult(
                        section = BackupSection.CLOUD_CONFIGS,
                        restored = result.restored,
                        skipped = result.skipped
                    )
                }
            }

            val localMedia = database.getMediaDao().getMedia()
            val albums = mediaRepository.getAlbums(MediaOrder.Default)
                .first { it is Resource.Success || it is Resource.Error }
                .data.orEmpty()
            val resolver = BackupIdentityResolver(
                localMedia = localMedia,
                albums = albums,
                accountMappings = accountMappings,
                issues = issues
            )

            var pendingLocalFavoriteUris: List<Uri> = emptyList()
            var vaultsRestored = 0
            var vaultsSkipped = 0
            var vaultMediaRestored = 0
            var vaultMediaSkipped = 0

            restoreOrder.forEach { section ->
                if (section == BackupSection.CLOUD_CONFIGS) return@forEach
                if (!selection.isOn(section)) return@forEach
                if (!manifest.sections.containsKey(section.name)) return@forEach
                try {
                    when (section) {
                        BackupSection.VAULTS -> {
                            val credentialEntries = mutableListOf<VaultCredentialEntry>()
                            readSection(
                                BackupSection.VAULTS,
                                manifest,
                                dataDir,
                                VaultCredentialEntry.serializer()
                            ) { credentialEntries += it }
                            val vaultResult = restoreVaults(
                                manifest = manifest,
                                selection = selection,
                                encrypted = encrypted,
                                credentials = credentialEntries,
                                dataDir = dataDir,
                                onProgress = onProgress
                            )
                            vaultsRestored = vaultResult.vaultsRestored
                            vaultsSkipped = vaultResult.vaultsSkipped
                            vaultMediaRestored = vaultResult.mediaRestored
                            vaultMediaSkipped = vaultResult.mediaSkipped
                            sectionResults[section] = SectionRestoreResult(
                                section = section,
                                restored = vaultsRestored,
                                skipped = vaultsSkipped
                            )
                        }
                        BackupSection.LOCAL_FAVORITES -> {
                            val favorites = mutableListOf<LocalFavoriteEntry>()
                            readSection(section, manifest, dataDir, LocalFavoriteEntry.serializer()) {
                                favorites += it
                            }
                            onProgress(section, 0, favorites.size)
                            pendingLocalFavoriteUris = legacy.bridgeMatchLocalFavorites(favorites)
                            sectionResults[section] = SectionRestoreResult(
                                section = section,
                                restored = pendingLocalFavoriteUris.size,
                                skipped = favorites.size - pendingLocalFavoriteUris.size
                            )
                        }
                        BackupSection.CLOUD_FAVORITES -> {
                            val favorites = mutableListOf<CloudFavoriteEntry>()
                            readSection(section, manifest, dataDir, CloudFavoriteEntry.serializer()) {
                                favorites += it
                            }
                            val result = legacy.bridgeRestoreCloudFavorites(
                                favorites,
                                accountMappings,
                                issues
                            )
                            sectionResults[section] = SectionRestoreResult(
                                section = section,
                                restored = result.restored,
                                skipped = result.skipped
                            )
                            onProgress(section, result.restored, favorites.size)
                        }
                        BackupSection.SETTINGS -> {
                            val settings = mutableMapOf<String, SettingValue>()
                            readSection(section, manifest, dataDir, SettingsRecord.serializer()) {
                                settings[it.name] = it.value
                            }
                            val result = legacy.bridgeRestoreSettings(settings, issues)
                            sectionResults[section] = SectionRestoreResult(
                                section = section,
                                restored = result.restored,
                                skipped = result.skipped
                            )
                            onProgress(section, result.restored, settings.size)
                        }
                        BackupSection.ALBUMS -> sectionResults[section] =
                            restoreAlbums(manifest, dataDir, resolver, onProgress)
                        BackupSection.COLLECTIONS -> sectionResults[section] =
                            restoreCollections(manifest, dataDir, resolver, onProgress)
                        BackupSection.CATEGORIES -> sectionResults[section] =
                            restoreCategories(manifest, dataDir, resolver, onProgress)
                        BackupSection.METADATA -> sectionResults[section] =
                            restoreMetadata(manifest, dataDir, resolver, onProgress)
                        BackupSection.SEARCH_HISTORY -> sectionResults[section] =
                            restoreSearchHistory(manifest, dataDir, resolver, onProgress)
                        BackupSection.PEOPLE -> sectionResults[section] =
                            restorePeople(manifest, dataDir, resolver, onProgress)
                        BackupSection.CLOUD_PREFS -> sectionResults[section] =
                            restoreCloudPrefs(manifest, dataDir, resolver, onProgress)
                        BackupSection.EDIT_BACKUPS -> sectionResults[section] =
                            restoreEditBackups(manifest, dataDir, resolver, onProgress)
                        BackupSection.DEVICE_UI -> sectionResults[section] =
                            restoreDeviceUi(manifest, dataDir, onProgress)
                        BackupSection.CLOUD_CONFIGS -> Unit
                    }
                } catch (e: Exception) {
                    printError("app.backup", "section ${section.name} failed", e)
                    issues += ImportIssue(section, section.name, e.message ?: "Section failed")
                    if (sectionResults[section] == null) {
                        sectionResults[section] = SectionRestoreResult(
                            section = section,
                            skipped = manifest.sections[section.name]?.count ?: 0
                        )
                    }
                }
            }

            ImportResult(
                settingsRestored = sectionResults[BackupSection.SETTINGS]?.restored ?: 0,
                settingsSkipped = sectionResults[BackupSection.SETTINGS]?.skipped ?: 0,
                cloudFavoritesRestored = sectionResults[BackupSection.CLOUD_FAVORITES]?.restored ?: 0,
                cloudFavoritesSkipped = sectionResults[BackupSection.CLOUD_FAVORITES]?.skipped ?: 0,
                cloudConfigsRestored = sectionResults[BackupSection.CLOUD_CONFIGS]?.restored ?: 0,
                cloudConfigsSkipped = sectionResults[BackupSection.CLOUD_CONFIGS]?.skipped ?: 0,
                cloudConfigsRequiringAuthentication = 0,
                vaultsRestored = vaultsRestored,
                vaultsSkipped = vaultsSkipped,
                vaultMediaRestored = vaultMediaRestored,
                vaultMediaSkipped = vaultMediaSkipped,
                pendingLocalFavoriteUris = pendingLocalFavoriteUris,
                issues = issues,
                sectionResults = sectionResults
            )
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    // =============================================================================================
    // Section dispatchers
    // =============================================================================================

    private suspend fun restoreAlbums(
        manifest: BackupManifest,
        dataDir: File,
        resolver: BackupIdentityResolver,
        progress: BackupProgressListener
    ): SectionRestoreResult {
        val section = BackupSection.ALBUMS
        var restored = 0
        var skipped = 0
        var merged = 0
        val total = manifest.sections[section.name]?.count ?: 0
        progress(section, 0, total)
        var seen = 0

        val existingPins = database.getPinnedDao().getPinnedAlbumIds().toSet()
        val existingLocks = database.getLockedAlbumDao().getLockedAlbumIds().toSet()
        val existingIgnored = database.getBlacklistDao().getBlacklistedAlbumsAsync()
        val existingCovers = database.getAlbumThumbnailDao().getAlbumThumbnails()
            .map { it.albumId }.toSet()
        val groups = database.getAlbumGroupDao().getAllGroupsAsync().toMutableList()
        val sections = database.getAlbumSectionDao().getAllSectionsAsync().toMutableList()
        val mergedFolders = database.getMergedSubfolderDao().getAllAsync()

        readSection(section, manifest, dataDir, AlbumRecord.serializer()) { record ->
            seen++
            when (record) {
                is AlbumRecord.Pin -> {
                    val id = resolver.resolveAlbum(record.value.album, section)
                    if (id == null) skipped++ else if (id in existingPins) merged++ else {
                        database.getPinnedDao().insertPinnedAlbum(PinnedAlbum(id)); restored++
                    }
                }
                is AlbumRecord.Lock -> {
                    val id = resolver.resolveAlbum(record.value.album, section)
                    if (id == null) skipped++ else if (id in existingLocks) merged++ else {
                        database.getLockedAlbumDao().insertLockedAlbum(LockedAlbum(id)); restored++
                    }
                }
                is AlbumRecord.Ignored -> {
                    val recordValue = record.value
                    val albumIds = recordValue.albums.mapNotNull {
                        resolver.resolveAlbum(it, section)
                    }
                    val existing = existingIgnored.firstOrNull {
                        it.label == recordValue.label &&
                            it.wildcard == recordValue.wildcard &&
                            it.location == recordValue.location
                    }
                    if (existing != null) {
                        database.getBlacklistDao().addBlacklistedAlbum(
                            existing.copy(
                                albumIds = (existing.albumIds + albumIds).distinct(),
                                matchedAlbums = (existing.matchedAlbums + recordValue.matchedAlbums).distinct()
                            )
                        )
                        merged++
                    } else {
                        val id = recordValue.sourceId.takeIf { it > 0 }
                            ?: (-1L - stableIdHash(
                                "${recordValue.label}/${recordValue.wildcard}/${recordValue.location}"
                            ))
                        database.getBlacklistDao().addBlacklistedAlbum(
                            IgnoredAlbum(
                                id = id,
                                label = recordValue.label,
                                wildcard = recordValue.wildcard,
                                albumIds = albumIds,
                                location = recordValue.location,
                                matchedAlbums = recordValue.matchedAlbums
                            )
                        )
                        restored++
                    }
                }
                is AlbumRecord.Cover -> {
                    val albumId = resolver.resolveAlbum(record.value.album, section)
                    val mediaId = record.value.cover?.let { resolver.resolveMedia(it, section) }
                    if (albumId == null || mediaId == null) {
                        skipped++
                    } else if (albumId in existingCovers) {
                        merged++
                    } else {
                        val media = database.getMediaDao().getMediaById(mediaId)
                        database.getAlbumThumbnailDao().updateAlbumThumbnail(
                            AlbumThumbnail(albumId, media.uri)
                        )
                        restored++
                    }
                }
                is AlbumRecord.Group -> {
                    val value = record.value
                    val existing = groups.firstOrNull { it.label == value.label }
                    val groupId = existing?.id ?: database.getAlbumGroupDao()
                        .insertGroup(com.dot.gallery.domain.model.AlbumGroup(label = value.label, timestamp = value.timestamp))
                        .also { groups += com.dot.gallery.domain.model.AlbumGroup(it, value.label, value.timestamp) }
                    value.albums.mapNotNull { resolver.resolveAlbum(it, section) }.forEach { albumId ->
                        database.getAlbumGroupDao().addAlbumToGroup(
                            AlbumGroupMember(groupId, albumId)
                        )
                    }
                    if (existing != null) merged++ else restored++
                }
                is AlbumRecord.Section -> {
                    val value = record.value
                    val existing = sections.firstOrNull {
                        it.type == value.type && (value.type == 2 || it.label == value.label)
                    }
                    val sectionId = existing?.id ?: database.getAlbumSectionDao().insertSection(
                        com.dot.gallery.domain.model.AlbumSection(
                            label = value.label,
                            type = value.type,
                            displayOrder = value.displayOrder,
                            isVisible = value.isVisible,
                            isExpanded = value.isExpanded,
                            sortKind = value.sortKind,
                            sortOrder = value.sortOrder
                        )
                    )
                    value.albums.mapNotNull { resolver.resolveAlbum(it, section) }.forEach { albumId ->
                        database.getAlbumSectionDao().addAlbumToSection(
                            AlbumSectionMember(sectionId, albumId)
                        )
                    }
                    if (existing != null) merged++ else restored++
                }
                is AlbumRecord.Merged -> {
                    val value = record.value
                    val exists = mergedFolders.any {
                        (value.folderKey != null && it.folderKey == value.folderKey) ||
                            (it.volume == value.volume && it.relativePath == value.relativePath)
                    }
                    if (exists) {
                        merged++
                    } else {
                        database.getMergedSubfolderDao().insertMergedSubfolderAlbum(
                            MergedSubfolderAlbum(
                                id = value.sourceId,
                                folderKey = value.folderKey,
                                volume = value.volume,
                                relativePath = value.relativePath,
                                displayMode = value.displayMode
                            )
                        )
                        restored++
                    }
                }
                is AlbumRecord.Timeline -> {
                    val value = record.value
                    val current = database.getMediaDao().getTimelineSettingsOnce()
                    if (current == null) {
                        database.getMediaDao().setTimelineSettings(
                            TimelineSettings(
                                groupTimelineByMonth = value.groupTimelineByMonth,
                                groupTimelineByYear = value.groupTimelineByYear,
                                groupTimelineInAlbums = value.groupTimelineInAlbums,
                                timelineMediaOrder = backupJson.decodeFromString(
                                    MediaOrder.serializer(), value.timelineMediaOrder
                                ),
                                albumMediaOrder = backupJson.decodeFromString(
                                    MediaOrder.serializer(), value.albumMediaOrder
                                )
                            )
                        )
                        restored++
                    } else {
                        merged++
                    }
                }
            }
            if (seen % 25 == 0 || seen == total) progress(section, seen, total)
        }
        progress(section, total, total)
        return SectionRestoreResult(section, restored, skipped, merged)
    }

    private suspend fun restoreCollections(
        manifest: BackupManifest,
        dataDir: File,
        resolver: BackupIdentityResolver,
        progress: BackupProgressListener
    ): SectionRestoreResult {
        val section = BackupSection.COLLECTIONS
        var restored = 0
        var skipped = 0
        var merged = 0
        val total = manifest.sections[section.name]?.count ?: 0
        val existing = database.getCollectionDao().getAllCollectionsAsync().toMutableList()
        var seen = 0
        readSection(section, manifest, dataDir, CollectionRecord.serializer()) { record ->
            seen++
            val match = existing.firstOrNull { it.label.equals(record.label, ignoreCase = true) }
            val collectionId = if (match != null) {
                merged++
                match.id
            } else {
                val id = database.getCollectionDao().insertCollection(
                    Collection(
                        label = record.label,
                        isPinned = record.isPinned,
                        sortOrder = record.sortOrder,
                        createdAt = record.createdAt,
                        updatedAt = record.updatedAt
                    )
                )
                existing += Collection(
                    id = id,
                    label = record.label,
                    isPinned = record.isPinned,
                    sortOrder = record.sortOrder,
                    createdAt = record.createdAt,
                    updatedAt = record.updatedAt
                )
                restored++
                id
            }
            val media = record.media.mapNotNull { it ->
                resolver.resolveMedia(it.media, section)?.let { id ->
                    CollectionMedia(collectionId, id, it.addedAt)
                }
            }
            if (media.isNotEmpty()) database.getCollectionDao().addMediaListToCollection(media)
            val albums = record.albums.mapNotNull { it ->
                resolver.resolveAlbum(it.album, section)?.let { id ->
                    CollectionAlbum(collectionId, id, it.addedAt)
                }
            }
            if (albums.isNotEmpty()) database.getCollectionDao().addAlbumsToCollection(albums)
            record.excluded.forEach { ref ->
                resolver.resolveMedia(ref, section)?.let { id ->
                    database.getCollectionDao().excludeMedia(CollectionExcludedMedia(collectionId, id))
                }
            }
            if (match?.coverMediaId == null) {
                record.cover?.let { resolver.resolveMedia(it, section) }?.let { id ->
                    database.getCollectionDao().updateCollectionCover(collectionId, id)
                }
            }
            skipped += (record.media.size - media.size) + (record.albums.size - albums.size)
            if (seen % 10 == 0 || seen == total) progress(section, seen, total)
        }
        progress(section, total, total)
        return SectionRestoreResult(section, restored, skipped, merged)
    }

    private suspend fun restoreCategories(
        manifest: BackupManifest,
        dataDir: File,
        resolver: BackupIdentityResolver,
        progress: BackupProgressListener
    ): SectionRestoreResult {
        val section = BackupSection.CATEGORIES
        var restored = 0
        var skipped = 0
        var merged = 0
        val total = manifest.sections[section.name]?.count ?: 0
        val categories = database.getCategoryDao().getAllCategoriesAsync().toMutableList()
        var seen = 0
        readSection(section, manifest, dataDir, CategoryRecord.serializer()) { record ->
            seen++
            val existing = categories.firstOrNull {
                it.name == record.name && it.isUserCreated == record.isUserCreated
            } ?: categories.firstOrNull { it.name.equals(record.name, ignoreCase = true) }
            val categoryId = if (existing != null) {
                merged++
                existing.id
            } else {
                val id = database.getCategoryDao().insertCategory(
                    Category(
                        name = record.name,
                        searchTerms = record.searchTerms,
                        embedding = record.embedding?.let(::floatArrayFromBase64),
                        threshold = record.threshold,
                        isUserCreated = record.isUserCreated,
                        isPinned = record.isPinned,
                        createdAt = record.createdAt,
                        updatedAt = record.updatedAt
                    )
                )
                categories += Category(
                    id = id,
                    name = record.name,
                    searchTerms = record.searchTerms,
                    threshold = record.threshold,
                    isUserCreated = record.isUserCreated,
                    isPinned = record.isPinned,
                    createdAt = record.createdAt,
                    updatedAt = record.updatedAt
                )
                restored++
                id
            }
            record.memberships.forEach { membership ->
                val mediaId = resolver.resolveMedia(membership.media, section)
                if (mediaId == null) {
                    skipped++
                    return@forEach
                }
                val current = database.getCategoryDao().getMediaCategory(mediaId, categoryId)
                if (current != null && current.isManuallyAdded && !membership.isManuallyAdded) {
                    merged++
                } else if (current?.isManuallyAdded == true) {
                    merged++
                } else {
                    database.getCategoryDao().insertMediaCategory(
                        MediaCategory(
                            mediaId = mediaId,
                            categoryId = categoryId,
                            similarityScore = membership.similarityScore,
                            addedAt = membership.addedAt,
                            isManuallyAdded = membership.isManuallyAdded,
                            resultRevision = membership.resultRevision
                        )
                    )
                }
            }
            if (seen % 10 == 0 || seen == total) progress(section, seen, total)
        }
        progress(section, total, total)
        return SectionRestoreResult(section, restored, skipped, merged)
    }

    private suspend fun restoreMetadata(
        manifest: BackupManifest,
        dataDir: File,
        resolver: BackupIdentityResolver,
        progress: BackupProgressListener
    ): SectionRestoreResult {
        val section = BackupSection.METADATA
        var restored = 0
        var skipped = 0
        var merged = 0
        val total = manifest.sections[section.name]?.count ?: 0
        val existingCore = database.getMetadataDao().getAllCore().map { it.mediaId }.toSet()
        val existingVideo = database.getMetadataDao().getAllVideo().map { it.mediaId }.toSet()
        val existingFlags = database.getMetadataDao().getAllFlags().map { it.mediaId }.toSet()
        val existingCapture = database.getMediaCaptureTimeDao().getAll().map { it.mediaId }.toSet()
        var seen = 0
        readSection(section, manifest, dataDir, MetadataRecord.serializer()) { record ->
            seen++
            val mediaId = resolver.resolveMedia(record.media, section)
            val media = mediaId?.let { runCatching { database.getMediaDao().getMediaById(it) }.getOrNull() }
            if (mediaId == null || media == null) {
                skipped++
                return@readSection
            }
            val core = record.core
            val video = record.video
            val flags = record.flags
            val captureTime = record.captureTime
            var inserted = false
            var existed = false
            if (core != null) {
                if (mediaId !in existingCore) {
                    database.getMetadataDao().upsertCore(core.toEntity(mediaId))
                    inserted = true
                } else {
                    existed = true
                }
            }
            if (video != null) {
                if (mediaId !in existingVideo) {
                    database.getMetadataDao().upsertVideo(video.toEntity(mediaId))
                    inserted = true
                } else {
                    existed = true
                }
            }
            if (flags != null) {
                if (mediaId !in existingFlags) {
                    database.getMetadataDao().upsertFlags(flags.toEntity(mediaId))
                    inserted = true
                } else {
                    existed = true
                }
            }
            if (captureTime != null) {
                if (mediaId !in existingCapture) {
                    database.getMediaCaptureTimeDao().upsertAll(
                        listOf(captureTime.toEntity(mediaId, media))
                    )
                    inserted = true
                } else {
                    existed = true
                }
            }
            if (inserted) restored++ else if (existed) merged++
            if (seen % 100 == 0 || seen == total) progress(section, seen, total)
        }
        progress(section, total, total)
        return SectionRestoreResult(section, restored, skipped, merged)
    }

    private suspend fun restoreSearchHistory(
        manifest: BackupManifest,
        dataDir: File,
        resolver: BackupIdentityResolver,
        progress: BackupProgressListener
    ): SectionRestoreResult {
        val section = BackupSection.SEARCH_HISTORY
        val incoming = mutableListOf<SearchHistoryRecord>()
        readSection(section, manifest, dataDir, SearchHistoryRecord.serializer()) { incoming += it }
        progress(section, 0, incoming.size)
        if (incoming.isEmpty()) return SectionRestoreResult(section)
        val key = stringPreferencesKey("search_history_v2")
        val prefs = context.activeDataStore.data.first()
        val existing = prefs[key]?.let {
            runCatching {
                backupJson.decodeFromString(ListSerializer(SearchHistory.serializer()), it)
            }.getOrNull()
        }.orEmpty().toMutableList()
        var restored = 0
        var skipped = 0
        incoming.forEach { record ->
            val mediaId = record.media?.let { resolver.resolveMedia(it, section) }
            val entry = SearchHistory(
                timestamp = record.timestamp,
                query = record.query,
                mediaId = mediaId,
                mediaLabel = record.mediaLabel,
                mediaUri = if (mediaId != null) {
                    runCatching { database.getMediaDao().getMediaById(mediaId).uri.toString() }
                        .getOrNull()
                } else null
            )
            val index = existing.indexOfFirst { it.query.equals(record.query, ignoreCase = true) }
            if (index >= 0) {
                if (existing[index].timestamp < entry.timestamp) existing[index] = entry
                restored++
            } else {
                existing += entry
                restored++
            }
            if (record.media != null && mediaId == null) skipped++
        }
        val capped = existing.sortedByDescending { it.timestamp }.take(MAX_HISTORY)
        context.activeDataStore.edit { prefs ->
            prefs[key] = backupJson.encodeToString(
                ListSerializer(SearchHistory.serializer()),
                capped
            )
        }
        progress(section, incoming.size, incoming.size)
        return SectionRestoreResult(section, restored, skipped)
    }

    private suspend fun restorePeople(
        manifest: BackupManifest,
        dataDir: File,
        resolver: BackupIdentityResolver,
        progress: BackupProgressListener
    ): SectionRestoreResult {
        val section = BackupSection.PEOPLE
        var restored = 0
        var skipped = 0
        var merged = 0
        val total = manifest.sections[section.name]?.count ?: 0
        val people = database.getPersonDao().getByProviderOnce(ProviderType.LOCAL_PEOPLE)
            .associateBy { it.id }
            .toMutableMap()
        val pendingLinks = mutableListOf<FaceLinkEntity>()
        val pendingExclusions = mutableListOf<FaceExclusionEntity>()
        val pendingSuppressions = mutableListOf<FaceSuppressionEntity>()
        var seen = 0
        readSection(section, manifest, dataDir, PeopleRecord.serializer()) { record ->
            seen++
            when (record) {
                is PeopleRecord.Person -> {
                    val value = record.value
                    val existing = people[value.id]
                    val thumbnailId = value.thumbnailMedia?.let {
                        resolver.resolveMedia(it, section)
                    }
                    if (existing == null) {
                        database.getPersonDao().insert(
                            PersonEntity(
                                id = value.id,
                                name = value.name,
                                providerType = ProviderType.LOCAL_PEOPLE,
                                thumbnailMediaId = thumbnailId,
                                thumbnailUrl = null,
                                faceCount = 0,
                                lastUpdated = System.currentTimeMillis(),
                                hidden = value.hidden,
                                birthDate = value.birthDate
                            )
                        )
                        people[value.id] = PersonEntity(
                            id = value.id,
                            name = value.name,
                            providerType = ProviderType.LOCAL_PEOPLE,
                            thumbnailMediaId = thumbnailId,
                            faceCount = 0,
                            hidden = value.hidden,
                            birthDate = value.birthDate
                        )
                        restored++
                    } else {
                        if (existing.name.isBlank() && value.name.isNotBlank()) {
                            database.getPersonDao().updateName(existing.id, value.name)
                        }
                        if (existing.birthDate.isBlank() && value.birthDate.isNotBlank()) {
                            database.getPersonDao().insert(
                                existing.copy(birthDate = value.birthDate)
                            )
                        }
                        if (value.hidden && !existing.hidden) {
                            database.getPersonDao().setHidden(existing.id, true)
                        }
                        if (existing.thumbnailMediaId == null && thumbnailId != null) {
                            database.getPersonDao().updateThumbnail(existing.id, thumbnailId, null)
                        }
                        merged++
                    }
                }
                is PeopleRecord.Exclusion -> {
                    val mediaId = resolver.resolveMedia(record.value.media, section)
                    if (mediaId == null || record.value.personId !in people) {
                        skipped++
                    } else {
                        pendingExclusions += FaceExclusionEntity(
                            mediaId = mediaId,
                            personId = record.value.personId,
                            createdAt = record.value.createdAt
                        )
                    }
                }
                is PeopleRecord.Link -> {
                    val value = record.value
                    val mediaId = resolver.resolveMedia(value.media, section)
                    if (mediaId == null || value.personId !in people) {
                        skipped++
                    } else {
                        pendingLinks += FaceLinkEntity(
                            mediaId = mediaId,
                            left = value.left,
                            top = value.top,
                            right = value.right,
                            bottom = value.bottom,
                            personId = value.personId,
                            kind = runCatching { FaceLinkKind.valueOf(value.kind) }
                                .getOrDefault(FaceLinkKind.INCLUDE),
                            createdAt = value.createdAt
                        )
                    }
                }
                is PeopleRecord.Suppression -> {
                    val value = record.value
                    val mediaId = resolver.resolveMedia(value.media, section)
                    if (mediaId == null) {
                        skipped++
                    } else {
                        pendingSuppressions += FaceSuppressionEntity(
                            mediaId = mediaId,
                            left = value.left,
                            top = value.top,
                            right = value.right,
                            bottom = value.bottom,
                            createdAt = value.createdAt
                        )
                    }
                }
            }
            if (seen % 50 == 0 || seen == total) progress(section, seen, total)
        }
        if (pendingExclusions.isNotEmpty()) {
            database.getDetectedFaceDao().insertExclusions(pendingExclusions)
        }
        if (pendingLinks.isNotEmpty()) {
            database.getDetectedFaceDao().upsertLinks(pendingLinks)
        }
        if (pendingSuppressions.isNotEmpty()) {
            database.getDetectedFaceDao().insertSuppressions(pendingSuppressions)
        }
        progress(section, total, total)
        return SectionRestoreResult(section, restored, skipped, merged)
    }

    private suspend fun restoreCloudPrefs(
        manifest: BackupManifest,
        dataDir: File,
        resolver: BackupIdentityResolver,
        progress: BackupProgressListener
    ): SectionRestoreResult {
        val section = BackupSection.CLOUD_PREFS
        var restored = 0
        var skipped = 0
        val total = manifest.sections[section.name]?.count ?: 0
        val freeSpace = mutableListOf<FreeSpacePrefRecord>()
        val existingAlbumSync = database.getCloudAlbumSyncDao().getAllAsync()
            .map { Triple(it.albumRemoteId, it.providerType, it.serverConfigId) }
            .toSet()
        val existingOfflinePins = database.getCloudOfflinePinDao().getAllAsync()
            .map { it.serverConfigId }
            .toSet()
        var seen = 0
        readSection(section, manifest, dataDir, CloudPrefRecord.serializer()) { record ->
            seen++
            when (record) {
                is CloudPrefRecord.AlbumSync -> {
                    val value = record.value
                    val provider = runCatching { ProviderType.valueOf(value.providerType) }.getOrNull()
                    val configId = provider?.let {
                        resolver.resolveCloudAccount(value.sourceAccountId, value.providerType)
                    }
                    if (provider == null || configId == null) {
                        skipped++
                    } else {
                        val key = Triple(value.remoteAlbumId, provider, configId)
                        if (key in existingAlbumSync) {
                            skipped++
                        } else {
                            database.getCloudAlbumSyncDao().upsert(
                                CloudAlbumSyncEntity(
                                    albumRemoteId = value.remoteAlbumId,
                                    providerType = provider,
                                    serverConfigId = configId,
                                    albumName = value.albumName,
                                    syncEnabled = value.syncEnabled
                                )
                            )
                            restored++
                        }
                    }
                }
                is CloudPrefRecord.Upload -> {
                    val value = record.value
                    val provider = runCatching { ProviderType.valueOf(value.providerType) }.getOrNull()
                    val configId = provider?.let {
                        resolver.resolveCloudAccount(value.sourceAccountId, value.providerType)
                    }
                    val albumId = resolver.resolveAlbum(value.album, section)
                    if (provider == null || configId == null || albumId == null) {
                        skipped++
                    } else if (database.getCloudUploadPrefDao().get(configId, albumId) != null) {
                        skipped++
                    } else {
                        database.getCloudUploadPrefDao().upsert(
                            CloudUploadPrefEntity(
                                serverConfigId = configId,
                                albumId = albumId,
                                providerType = provider,
                                albumLabel = value.albumLabel,
                                uploadEnabled = value.uploadEnabled,
                                deleteLocalAfterUpload = value.deleteLocalAfterUpload,
                                customPath = value.customPath
                            )
                        )
                        restored++
                    }
                }
                is CloudPrefRecord.DeleteLocal -> {
                    val albumId = resolver.resolveAlbum(record.value.album, section)
                    if (albumId == null || database.getCloudDeleteLocalPrefDao().get(albumId) != null) {
                        skipped++
                    } else {
                        database.getCloudDeleteLocalPrefDao().upsert(
                            CloudDeleteLocalPrefEntity(
                                albumId = albumId,
                                enabled = record.value.enabled,
                                albumLabel = record.value.albumLabel
                            )
                        )
                        restored++
                    }
                }
                is CloudPrefRecord.OfflinePin -> {
                    val value = record.value
                    val provider = runCatching { ProviderType.valueOf(value.providerType) }.getOrNull()
                    val configId = provider?.let {
                        resolver.resolveCloudAccount(value.sourceAccountId, value.providerType)
                    }
                    if (provider == null || configId == null || configId in existingOfflinePins) {
                        skipped++
                    } else {
                        database.getCloudOfflinePinDao().upsert(
                            CloudOfflinePinEntity(
                                serverConfigId = configId,
                                providerType = provider,
                                label = value.label,
                                createdAt = value.createdAt
                            )
                        )
                        restored++
                    }
                }
                is CloudPrefRecord.FreeSpace -> freeSpace += record.value
            }
            if (seen % 25 == 0 || seen == total) progress(section, seen, total)
        }
        restoreFreeSpacePrefs(freeSpace, resolver)
        progress(section, total, total)
        return SectionRestoreResult(section, restored, skipped)
    }

    private suspend fun restoreFreeSpacePrefs(
        records: List<FreeSpacePrefRecord>,
        resolver: BackupIdentityResolver
    ) {
        if (records.isEmpty()) return
        val scheduled = records.filter { it.scheduled }
            .mapNotNull { resolver.resolveCloudAccount(it.sourceAccountId, providerOf(it)) }
            .map { it.toString() }
            .toSet()
        context.activeDataStore.edit { prefs ->
            records.forEach { record ->
                val configId = resolver.resolveCloudAccount(
                    record.sourceAccountId,
                    providerOf(record)
                ) ?: return@forEach
                prefs[booleanPreferencesKey("cloud_free_space_keep_favorites_$configId")] =
                    record.keepFavorites
                prefs[intPreferencesKey("cloud_free_space_cutoff_days_$configId")] =
                    record.cutoffDays
                prefs[booleanPreferencesKey("cloud_free_space_auto_enabled_$configId")] =
                    record.autoEnabled
                prefs[intPreferencesKey("cloud_free_space_auto_interval_days_$configId")] =
                    record.autoIntervalDays
            }
            if (scheduled.isNotEmpty()) {
                val current = prefs[stringSetPreferencesKey("cloud_free_space_scheduled_config_ids")]
                    .orEmpty()
                prefs[stringSetPreferencesKey("cloud_free_space_scheduled_config_ids")] =
                    current + scheduled
            }
        }
    }

    private fun providerOf(record: FreeSpacePrefRecord): String =
        record.sourceAccountId.substringBefore(':')

    private suspend fun restoreEditBackups(
        manifest: BackupManifest,
        dataDir: File,
        resolver: BackupIdentityResolver,
        progress: BackupProgressListener
    ): SectionRestoreResult {
        val section = BackupSection.EDIT_BACKUPS
        var restored = 0
        var skipped = 0
        val total = manifest.sections[section.name]?.count ?: 0
        val backupDir = File(context.filesDir, "edit_backups").apply { mkdirs() }
        var seen = 0
        readSection(section, manifest, dataDir, EditBackupRecord.serializer()) { record ->
            seen++
            val mediaId = resolver.resolveMedia(record.media, section)
            val source = File(dataDir, record.fileName)
            if (mediaId == null || !source.isFile) {
                skipped++
            } else {
                val target = File(backupDir, "$mediaId.bak")
                if (target.exists()) {
                    skipped++
                } else if (record.archiveSize >= 0 && source.length() != record.archiveSize) {
                    skipped++
                } else if (record.sha256.isNotBlank() && sha256(source) != record.sha256) {
                    skipped++
                } else {
                    source.copyTo(target, overwrite = false)
                    val media = database.getMediaDao().getMediaById(mediaId)
                    database.getEditHistoryDao().upsertEditedMedia(
                        EditedMedia(
                            mediaId = mediaId,
                            originalUri = media.uri,
                            backupPath = target.absolutePath,
                            originalMimeType = record.originalMimeType,
                            editTimestamp = record.editTimestamp
                        )
                    )
                    restored++
                }
            }
            if (seen % 10 == 0 || seen == total) progress(section, seen, total)
        }
        progress(section, total, total)
        return SectionRestoreResult(section, restored, skipped)
    }

    private suspend fun restoreDeviceUi(
        manifest: BackupManifest,
        dataDir: File,
        progress: BackupProgressListener
    ): SectionRestoreResult {
        val section = BackupSection.DEVICE_UI
        var restored = 0
        var skipped = 0
        val total = manifest.sections[section.name]?.count ?: 0
        val uiPrefs = context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)
        val widgetPrefs = context.getSharedPreferences("gallery_widget_prefs", Context.MODE_PRIVATE)
        var seen = 0
        readSection(section, manifest, dataDir, DeviceUiRecord.serializer()) { record ->
            seen++
            when (record) {
                is DeviceUiRecord.Grid -> {
                    if (record.value.name in GRID_KEYS && record.value.value > 0) {
                        uiPrefs.edit().putInt(
                            record.value.name,
                            record.value.value.coerceIn(MIN_GRID, MAX_GRID)
                        ).apply()
                        restored++
                    } else {
                        skipped++
                    }
                }
                is DeviceUiRecord.Widget -> {
                    val value = record.value
                    if (value.name.isNotBlank() && widgetPrefs.contains(value.name)) {
                        widgetPrefs.edit().putString(value.name, value.payload).apply()
                        restored++
                    } else {
                        skipped++
                    }
                }
                is DeviceUiRecord.PrivateFolder -> {
                    val value = record.value
                    val granted = runCatching {
                        context.contentResolver.persistedUriPermissions.any {
                            it.uri.toString() == value.uri && it.isReadPermission
                        }
                    }.getOrDefault(false)
                    if (granted) {
                        context.activeDataStore.edit { prefs ->
                            prefs[stringPreferencesKey("private_folder_uri")] = value.uri
                            prefs[booleanPreferencesKey("private_folder_enabled")] = value.enabled
                        }
                        restored++
                    } else {
                        skipped++
                        issues += ImportIssue(
                            section,
                            value.uri,
                            "Private folder permission is no longer available on this device"
                        )
                    }
                }
            }
            if (seen % 10 == 0 || seen == total) progress(section, seen, total)
        }
        progress(section, total, total)
        return SectionRestoreResult(section, restored, skipped)
    }

    // =============================================================================================
    // Vaults
    // =============================================================================================

    private suspend fun restoreVaults(
        manifest: BackupManifest,
        selection: BackupSelection,
        encrypted: Boolean,
        credentials: List<VaultCredentialEntry>,
        dataDir: File,
        onProgress: BackupProgressListener
    ): VaultRestoreCounts {
        val selected = manifest.vaults.filter { selection.isVaultSelected(it.uuid) }
        var vaultsRestored = 0
        var vaultsSkipped = 0
        var mediaRestored = 0
        var mediaSkipped = 0
        val total = selected.sumOf { it.media.size }
        var done = 0
        onProgress(BackupSection.VAULTS, 0, total)

        selected.forEach { entry ->
            val uuid = runCatching { Uuid.parse(entry.uuid) }.getOrNull()
            if (uuid == null) {
                vaultsSkipped++
                return@forEach
            }
            if (database.getVaultDao().getVault(uuid) != null) {
                vaultsSkipped++
                issues += ImportIssue(
                    BackupSection.VAULTS,
                    entry.name,
                    "A vault with the same id already exists on this device"
                )
                return@forEach
            }
            val vault = Vault(uuid = uuid, name = entry.name)
            // A restored vault must never inherit a stale local credential for the same UUID.
            runCatching { VaultPasswordManager.clearVaultSecrets(context, uuid) }
            try {
                var keychainFailure: String? = null
                keychainHolder.writeVaultInfo(
                    vault = vault,
                    transferable = true,
                    onFailed = { keychainFailure = it }
                )
                keychainFailure?.let { throw IOException(it) }
                database.getVaultDao().insertVault(vault)
                vaultsRestored++
                // Credentials are only honoured for password-encrypted archives, and are applied
                // as soon as the vault exists so an interrupted media import cannot lose them.
                if (encrypted) {
                    credentials.firstOrNull { it.vaultUuid == entry.uuid }
                        ?.takeIf { isValidCredential(it.credential) }
                        ?.let { credential ->
                            VaultPasswordManager.setStoredCredential(
                                context,
                                uuid,
                                credential.credential
                            )
                        }
                }
            } catch (e: Exception) {
                vaultsSkipped++
                mediaSkipped += entry.media.size
                issues += ImportIssue(BackupSection.VAULTS, entry.name, e.message ?: "Vault creation failed")
                return@forEach
            }

            entry.media.forEach { mediaEntry ->
                val srcFile = File(dataDir, mediaEntry.fileName)
                if (!srcFile.isFile ||
                    (mediaEntry.archiveSize >= 0 && srcFile.length() != mediaEntry.archiveSize) ||
                    (mediaEntry.sha256.isNotBlank() && sha256(srcFile) != mediaEntry.sha256)
                ) {
                    mediaSkipped++
                    done++
                    onProgress(BackupSection.VAULTS, done, total)
                    return@forEach
                }
                val outFile = with(keychainHolder) { vault.mediaFile(mediaEntry.id) }
                try {
                    srcFile.inputStream().use { input ->
                        keychainHolder.encryptPortableStream(vault, input, outFile)
                    }
                    outFile.setLastModified(System.currentTimeMillis())
                    database.getVaultDao().addMediaToVault(mediaEntry.toEncryptedMedia2(uuid))
                    mediaRestored++
                } catch (e: Exception) {
                    mediaSkipped++
                    outFile.delete()
                    issues += ImportIssue(
                        BackupSection.VAULTS,
                        mediaEntry.label,
                        e.message ?: "Vault media import failed"
                    )
                }
                done++
                onProgress(BackupSection.VAULTS, done, total)
            }

        }
        return VaultRestoreCounts(vaultsRestored, vaultsSkipped, mediaRestored, mediaSkipped)
    }

    private fun isValidCredential(value: String): Boolean =
        Regex("^[A-Z_]+:(pbkdf2:)?[0-9a-fA-F]+:[0-9a-fA-F]+$").matches(value)

    private fun VaultMediaEntry.toEncryptedMedia2(uuid: Uuid) = EncryptedMedia2(
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

    // =============================================================================================
    // Helpers
    // =============================================================================================

    private suspend fun <T> readSection(
        section: BackupSection,
        manifest: BackupManifest,
        dataDir: File,
        serializer: KSerializer<T>,
        block: suspend (T) -> Unit
    ): Int {
        val descriptor = manifest.sections[section.name] ?: return 0
        val file = File(dataDir, descriptor.file)
        return SectionReader(file, descriptor).read(serializer, block)
    }

    private fun skipPreviewHeader(input: InputStream): BackupPreviewHeader? {
        val magic = ByteArray(BackupPreviewHeader.MAGIC_SIZE)
        if (readUpTo(input, magic) < magic.size) return null
        if (!magic.contentEquals(BackupPreviewHeader.MAGIC.toByteArray())) return null
        val lengthBytes = ByteArray(4)
        if (readUpTo(input, lengthBytes) < 4) return null
        val length = java.nio.ByteBuffer.wrap(lengthBytes).int
        if (length <= 0 || length > 4 * 1024 * 1024) return null
        val bytes = ByteArray(length)
        if (readUpTo(input, bytes) < length) return null
        return runCatching {
            backupJson.decodeFromString(BackupPreviewHeader.serializer(), bytes.decodeToString())
        }.getOrNull()
    }

    private fun readUpTo(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = input.read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    private fun extractZip(input: InputStream, destDir: File) {
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            val destCanonical = destDir.canonicalPath
            val seen = mutableSetOf<String>()
            var totalBytes = 0L
            var entry: java.util.zip.ZipEntry? = zip.nextEntry
            while (entry != null) {
                if (seen.size >= MAX_ARCHIVE_ENTRIES) throw IOException("Backup has too many entries")
                val name = entry.name
                if (!seen.add(name)) throw IOException("Duplicate zip entry: $name")
                val outFile = File(destDir, name)
                if (!outFile.canonicalPath.startsWith(destCanonical + File.separator)) {
                    throw IOException("Illegal zip entry path: $name")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    var entryBytes = 0L
                    outFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            entryBytes += read
                            totalBytes += read
                            if (entryBytes > MAX_ENTRY_BYTES || totalBytes > MAX_ARCHIVE_BYTES) {
                                throw IOException("Backup exceeds extraction limits")
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private fun MediaMetadataCoreRecord.toEntity(mediaId: Long) = MediaMetadataCore(
        mediaId = mediaId,
        imageDescription = imageDescription,
        dateTimeOriginal = dateTimeOriginal,
        manufacturerName = manufacturerName,
        modelName = modelName,
        lensModel = lensModel,
        aperture = aperture,
        exposureTime = exposureTime,
        iso = iso,
        focalLength = focalLength,
        gpsLatitude = gpsLatitude,
        gpsLongitude = gpsLongitude,
        gpsLocationName = gpsLocationName,
        gpsLocationNameCountry = gpsLocationNameCountry,
        gpsLocationNameCity = gpsLocationNameCity,
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        imageResolutionX = imageResolutionX,
        imageResolutionY = imageResolutionY,
        resolutionUnit = resolutionUnit
    )

    private fun MediaMetadataVideoRecord.toEntity(mediaId: Long) = MediaMetadataVideo(
        mediaId = mediaId,
        durationMs = durationMs,
        videoWidth = videoWidth,
        videoHeight = videoHeight,
        frameRate = frameRate,
        bitRate = bitRate
    )

    private fun MediaMetadataFlagsRecord.toEntity(mediaId: Long) = MediaMetadataFlags(
        mediaId = mediaId,
        isNightMode = isNightMode,
        isPanorama = isPanorama,
        isPhotosphere = isPhotosphere,
        isLongExposure = isLongExposure,
        isMotionPhoto = isMotionPhoto
    )

    private fun MediaCaptureTimeRecord.toEntity(mediaId: Long, media: UriMedia) =
        MediaCaptureTimeEntity(
            mediaId = mediaId,
            captureTimestampMillis = captureTimestampMillis,
            origin = CaptureTimeOrigin.fromStoredValue(origin),
            sourceModifiedSeconds = media.timestamp,
            sourceTakenTimestampMillis = media.takenTimestamp,
            sourceSize = media.size,
            sourcePath = media.path,
            updatedAtMillis = updatedAtMillis
        )

    private data class VaultRestoreCounts(
        val vaultsRestored: Int,
        val vaultsSkipped: Int,
        val mediaRestored: Int,
        val mediaSkipped: Int
    )

    private companion object {
        const val PAYLOAD_MAGIC_SIZE = 7
        const val MAX_ARCHIVE_ENTRIES = 50_000
        const val MAX_ENTRY_BYTES = 2L * 1024 * 1024 * 1024
        const val MAX_ARCHIVE_BYTES = 20L * 1024 * 1024 * 1024
        const val MAX_HISTORY = 100
        const val MIN_GRID = 1
        const val MAX_GRID = 30
        val GRID_KEYS = setOf(
            "album_grid_size",
            "album_grid_size_landscape",
            "media_grid_size",
            "media_grid_size_landscape",
            "mosaic_grid_size",
            "mosaic_grid_size_landscape"
        )
    }
}
