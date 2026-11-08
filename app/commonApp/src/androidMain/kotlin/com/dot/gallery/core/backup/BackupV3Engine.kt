/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.di.CloudProviderInitializer
import com.dot.gallery.cloud.sync.CloudSyncScheduler
import com.dot.gallery.core.Resource
import com.dot.gallery.core.backup.ConfigBackupManager.ExportResult
import com.dot.gallery.core.backup.ConfigBackupManager.ImportIssue
import com.dot.gallery.core.backup.ConfigBackupManager.ImportResult
import com.dot.gallery.core.activeDataStore
import com.dot.gallery.core.logging.printError
import com.dot.gallery.core.logging.printInfo
import com.dot.gallery.data.InternalDatabase
import com.dot.gallery.data.dao.CloudMediaDao
import com.dot.gallery.data.dao.CloudServerConfigDao
import com.dot.gallery.data.data_source.KeychainHolder
import com.dot.gallery.data.entity.CloudAlbumSyncEntity
import com.dot.gallery.data.entity.CloudDeleteLocalPrefEntity
import com.dot.gallery.data.entity.CloudMediaEntity
import com.dot.gallery.data.entity.CloudOfflinePinEntity
import com.dot.gallery.data.entity.CloudServerConfigEntity
import com.dot.gallery.data.entity.CloudUploadPrefEntity
import com.dot.gallery.data.entity.MediaCaptureTimeEntity
import com.dot.gallery.data.entity.MediaMetadataCore
import com.dot.gallery.data.entity.MediaMetadataFlags
import com.dot.gallery.data.entity.MediaMetadataVideo
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.AlbumGroupMember
import com.dot.gallery.domain.model.AlbumSectionMember
import com.dot.gallery.domain.model.Category
import com.dot.gallery.domain.model.Collection
import com.dot.gallery.domain.model.EditedMedia
import com.dot.gallery.domain.model.IgnoredAlbum
import com.dot.gallery.domain.model.MediaCategory
import com.dot.gallery.domain.model.SearchHistory
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.util.MediaOrder
import com.dot.gallery.domain.util.toEncryptedMedia2
import com.dot.gallery.feature.vault.utils.VaultPasswordManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.uuid.Uuid

/**
 * Schema-v3 archive engine.
 *
 * Export writes a plaintext preview header, then a ZIP (optionally encrypted as a whole) that
 * contains a small manifest plus one JSON Lines file per section. Restore reads the preview
 * header first, so it can show a preview and decide whether a password is required without
 * guessing from the file name or from which sections the archive contains.
 */
internal class BackupV3Engine(
    private val context: Context,
    private val database: InternalDatabase,
    private val keychainHolder: KeychainHolder,
    private val cloudMediaDao: CloudMediaDao,
    private val cloudServerConfigDao: CloudServerConfigDao,
    private val mediaRepository: com.dot.gallery.data.repository.MediaRepository,
    private val pendingCloudFavoriteStore: PendingCloudFavoriteStore,
    private val providerInitializer: CloudProviderInitializer,
    private val syncScheduler: CloudSyncScheduler,
    private val legacy: BackupLegacyBridge
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val exportOrder = listOf(
        BackupSection.SETTINGS,
        BackupSection.LOCAL_FAVORITES,
        BackupSection.CLOUD_FAVORITES,
        BackupSection.CLOUD_CONFIGS,
        BackupSection.ALBUMS,
        BackupSection.COLLECTIONS,
        BackupSection.CATEGORIES,
        BackupSection.METADATA,
        BackupSection.SEARCH_HISTORY,
        BackupSection.PEOPLE,
        BackupSection.CLOUD_PREFS,
        BackupSection.EDIT_BACKUPS,
        BackupSection.DEVICE_UI
    )

    private val restoreOrder = listOf(
        BackupSection.CLOUD_CONFIGS,
        BackupSection.ALBUMS,
        BackupSection.COLLECTIONS,
        BackupSection.CATEGORIES,
        BackupSection.PEOPLE,
        BackupSection.METADATA,
        BackupSection.LOCAL_FAVORITES,
        BackupSection.CLOUD_FAVORITES,
        BackupSection.CLOUD_PREFS,
        BackupSection.VAULTS,
        BackupSection.EDIT_BACKUPS,
        BackupSection.SEARCH_HISTORY,
        BackupSection.DEVICE_UI,
        BackupSection.SETTINGS
    )

    private class ExportContext(
        val selection: BackupSelection,
        val mediaRefs: MediaRefIndex,
        val albumRefs: AlbumRefIndex,
        val encrypted: Boolean,
        val workDir: File,
        val progress: BackupProgressListener
    )

    // =============================================================================================
    // Export
    // =============================================================================================

    suspend fun export(
        destination: Uri,
        selection: BackupSelection,
        password: String?,
        onProgress: BackupProgressListener
    ): ExportResult = withContext(Dispatchers.IO) {
        val stamp = System.currentTimeMillis()
        val workDir = File(context.cacheDir, "backup_export_$stamp").apply { mkdirs() }
        val payloadFile = File(context.cacheDir, "backup_payload_$stamp.zip")
        try {
            val encrypted = !password.isNullOrEmpty()
            val localMedia = database.getMediaDao().getMedia()
            val cloudMedia = cloudMediaDao.getAllAsync()
            val albums = mediaRepository.getAlbums(MediaOrder.Default)
                .first { it is Resource.Success || it is Resource.Error }
                .data.orEmpty()
            val ctx = ExportContext(
                selection = selection,
                mediaRefs = MediaRefIndex(localMedia, cloudMedia),
                albumRefs = AlbumRefIndex(albums),
                encrypted = encrypted,
                workDir = workDir,
                progress = onProgress
            )

            val descriptors = LinkedHashMap<BackupSection, SectionDescriptor>()
            val counts = LinkedHashMap<BackupSection, Int>()

            exportOrder.forEach { section ->
                if (!selection.isOn(section)) return@forEach
                val file = File(workDir, BackupManifest.sectionFileName(section))
                file.parentFile?.mkdirs()
                val descriptor = file.outputStream().use { output ->
                    val writer = SectionWriter(output)
                    exportSection(section, writer, ctx)
                    writer.finish().copy(file = file.relativeTo(workDir).invariantSeparatorsPath)
                }
                descriptors[section] = descriptor
                counts[section] = descriptor.count
                onProgress(section, descriptor.count, descriptor.count)
            }

            val plannedVaults = if (selection.vaults) planVaults(selection) else emptyList()
            val vaultEntries = writeVaultPayloads(plannedVaults, workDir, onProgress)
            val vaultMediaCount = vaultEntries.sumOf { it.media.size }
            counts[BackupSection.VAULTS] = vaultEntries.size
            val credentials = if (selection.vaults && encrypted) {
                readVaultCredentials(plannedVaults.map { it.vault })
            } else {
                emptyList()
            }
            if (selection.vaults) {
                val credentialFile = File(workDir, BackupManifest.sectionFileName(BackupSection.VAULTS))
                credentialFile.parentFile?.mkdirs()
                val descriptor = credentialFile.outputStream().use { output ->
                    val writer = SectionWriter(output)
                    credentials.forEach {
                        writer.write(VaultCredentialEntry.serializer(), it)
                    }
                    writer.finish().copy(
                        file = credentialFile.relativeTo(workDir).invariantSeparatorsPath
                    )
                }
                descriptors[BackupSection.VAULTS] = descriptor
                onProgress(BackupSection.VAULTS, descriptor.count, descriptor.count)
            }

            val manifest = BackupManifest(
                schemaVersion = BackupManifest.SCHEMA_VERSION,
                appVersionName = appVersionName(),
                appVersionCode = appVersionCode(),
                exportedAt = stamp,
                vaults = vaultEntries,
                sections = descriptors.mapKeys { it.key.name },
                vaultCredentials = credentials,
                credentialIncluded = credentials.isNotEmpty(),
                encryption = if (encrypted) BackupManifest.ENCRYPTION_PASSWORD else BackupManifest.ENCRYPTION_NONE
            )
            File(workDir, BackupManifest.MANIFEST_NAME)
                .writeText(json.encodeToString(BackupManifest.serializer(), manifest))

            zipDirectory(workDir, payloadFile)

            val payloadBytes = writeArchive(destination, manifest, counts, payloadFile, password)
            ExportResult(
                settingsCount = counts[BackupSection.SETTINGS] ?: 0,
                localFavoritesCount = counts[BackupSection.LOCAL_FAVORITES] ?: 0,
                cloudFavoritesCount = counts[BackupSection.CLOUD_FAVORITES] ?: 0,
                cloudConfigsCount = counts[BackupSection.CLOUD_CONFIGS] ?: 0,
                vaultCount = vaultEntries.size,
                vaultMediaCount = vaultMediaCount,
                sectionCounts = counts,
                payloadBytes = payloadBytes,
                encrypted = encrypted
            )
        } finally {
            workDir.deleteRecursively()
            payloadFile.delete()
        }
    }

    private suspend fun exportSection(
        section: BackupSection,
        writer: SectionWriter,
        ctx: ExportContext
    ) {
        when (section) {
            BackupSection.SETTINGS -> exportSettings().forEach { (name, value) ->
                writer.write(SettingsRecord.serializer(), SettingsRecord(name, value))
            }

            BackupSection.LOCAL_FAVORITES -> exportLocalFavorites().forEach {
                writer.write(LocalFavoriteEntry.serializer(), it)
            }

            BackupSection.CLOUD_FAVORITES -> exportCloudFavorites().forEach {
                writer.write(CloudFavoriteEntry.serializer(), it)
            }

            BackupSection.CLOUD_CONFIGS -> exportCloudConfigs().forEach {
                writer.write(CloudConfigEntry.serializer(), it)
            }

            BackupSection.ALBUMS -> exportAlbums(writer, ctx)
            BackupSection.COLLECTIONS -> exportCollections(writer, ctx)
            BackupSection.CATEGORIES -> exportCategories(writer, ctx)
            BackupSection.METADATA -> exportMetadata(writer, ctx)
            BackupSection.SEARCH_HISTORY -> exportSearchHistory(writer, ctx)
            BackupSection.PEOPLE -> exportPeople(writer, ctx)
            BackupSection.CLOUD_PREFS -> exportCloudPrefs(writer, ctx)
            BackupSection.EDIT_BACKUPS -> exportEditBackups(writer, ctx)
            BackupSection.DEVICE_UI -> exportDeviceUi(writer)
            BackupSection.VAULTS -> Unit // credentials are written by the vault planner
        }
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

    private suspend fun exportCloudFavorites(): List<CloudFavoriteEntry> =
        cloudMediaDao.getFavoritesAsync().map { entity ->
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

    private suspend fun exportCloudConfigs(): List<CloudConfigEntry> =
        cloudServerConfigDao.getAll().first().map { it.toEntry() }

    // ----- ALBUMS --------------------------------------------------------------------------------

    private suspend fun exportAlbums(writer: SectionWriter, ctx: ExportContext) {
        database.getPinnedDao().getPinnedAlbumIds().forEach { id ->
            ctx.albumRefs.ref(id)?.let { writer.write(AlbumRecord.serializer(), AlbumRecord.Pin(AlbumPinRecord(it))) }
        }
        database.getLockedAlbumDao().getLockedAlbumIds().forEach { id ->
            ctx.albumRefs.ref(id)?.let { writer.write(AlbumRecord.serializer(), AlbumRecord.Lock(AlbumLockRecord(it))) }
        }
        database.getBlacklistDao().getBlacklistedAlbumsAsync().forEach { ignored ->
            writer.write(
                AlbumRecord.serializer(),
                AlbumRecord.Ignored(ignored.toRecord(ctx))
            )
        }
        database.getAlbumThumbnailDao().getAlbumThumbnails().forEach { cover ->
            val album = ctx.albumRefs.ref(cover.albumId)
            if (album != null) {
                writer.write(
                    AlbumRecord.serializer(),
                    AlbumRecord.Cover(
                        AlbumCoverRecord(
                            album = album,
                            cover = ctx.mediaRefs.ref(cover.thumbnailUri.toString()),
                            coverUri = cover.thumbnailUri.toString()
                        )
                    )
                )
            }
        }
        val groupMembers = database.getAlbumGroupDao().getAllGroupMembersAsync()
            .groupBy { it.groupId }
        database.getAlbumGroupDao().getAllGroupsAsync().forEach { group ->
            val albums = groupMembers[group.id].orEmpty()
                .mapNotNull { ctx.albumRefs.ref(it.albumId) }
            writer.write(
                AlbumRecord.serializer(),
                AlbumRecord.Group(AlbumGroupRecord(group.id, group.label, group.timestamp, albums))
            )
        }
        val sectionMembers = database.getAlbumSectionDao().getAllSectionMembersAsync()
            .groupBy { it.sectionId }
        database.getAlbumSectionDao().getAllSectionsAsync().forEach { section ->
            val albums = sectionMembers[section.id].orEmpty()
                .mapNotNull { ctx.albumRefs.ref(it.albumId) }
            writer.write(
                AlbumRecord.serializer(),
                AlbumRecord.Section(
                    AlbumSectionRecord(
                        sourceId = section.id,
                        label = section.label,
                        type = section.type,
                        displayOrder = section.displayOrder,
                        isVisible = section.isVisible,
                        isExpanded = section.isExpanded,
                        sortKind = section.sortKind,
                        sortOrder = section.sortOrder,
                        albums = albums
                    )
                )
            )
        }
        database.getMergedSubfolderDao().getAllAsync().forEach { merged ->
            writer.write(
                AlbumRecord.serializer(),
                AlbumRecord.Merged(
                    MergedSubfolderRecord(
                        sourceId = merged.id,
                        folderKey = merged.folderKey,
                        volume = merged.volume,
                        relativePath = merged.relativePath,
                        displayMode = merged.displayMode
                    )
                )
            )
        }
        database.getMediaDao().getTimelineSettingsOnce()?.let { timeline ->
            writer.write(
                AlbumRecord.serializer(),
                AlbumRecord.Timeline(
                    TimelineSettingsRecord(
                    timelineMediaOrder = json.encodeToString(
                        MediaOrder.serializer(),
                        timeline.timelineMediaOrder
                    ),
                    albumMediaOrder = json.encodeToString(
                        MediaOrder.serializer(),
                        timeline.albumMediaOrder
                    ),
                    groupTimelineByMonth = timeline.groupTimelineByMonth,
                    groupTimelineByYear = timeline.groupTimelineByYear,
                        groupTimelineInAlbums = timeline.groupTimelineInAlbums
                    )
                )
            )
        }
    }

    private fun IgnoredAlbum.toRecord(ctx: ExportContext): IgnoredAlbumRecord = IgnoredAlbumRecord(
        sourceId = id,
        label = label,
        wildcard = wildcard,
        albums = albumIds.mapNotNull(ctx.albumRefs::ref),
        location = location,
        matchedAlbums = matchedAlbums
    )

    // ----- COLLECTIONS ---------------------------------------------------------------------------

    private suspend fun exportCollections(writer: SectionWriter, ctx: ExportContext) {
        val collectionDao = database.getCollectionDao()
        collectionDao.getAllCollectionsAsync().forEach { collection ->
            val media = collectionDao.getMediaIdsInCollectionAsync(collection.id)
                .mapNotNull(ctx.mediaRefs::ref)
                .map { CollectionMediaRecord(it) }
            val albums = collectionDao.getAlbumIdsInCollectionAsync(collection.id)
                .mapNotNull(ctx.albumRefs::ref)
                .map { CollectionAlbumRecord(it) }
            val excluded = collectionDao.getExcludedMediaIdsAsync(collection.id)
                .mapNotNull(ctx.mediaRefs::ref)
            writer.write(
                CollectionRecord.serializer(),
                CollectionRecord(
                    sourceId = collection.id,
                    label = collection.label,
                    cover = ctx.mediaRefs.ref(collection.coverMediaId),
                    isPinned = collection.isPinned,
                    sortOrder = collection.sortOrder,
                    createdAt = collection.createdAt,
                    updatedAt = collection.updatedAt,
                    media = media,
                    albums = albums,
                    excluded = excluded
                )
            )
        }
    }

    // ----- CATEGORIES ----------------------------------------------------------------------------

    private suspend fun exportCategories(writer: SectionWriter, ctx: ExportContext) {
        val categoryDao = database.getCategoryDao()
        val memberships = categoryDao.getAllMediaCategories().groupBy { it.categoryId }
        categoryDao.getAllCategoriesAsync().forEach { category ->
            writer.write(
                CategoryRecord.serializer(),
                category.toRecord(memberships[category.id].orEmpty(), ctx)
            )
        }
    }

    private fun Category.toRecord(
        memberships: List<MediaCategory>,
        ctx: ExportContext
    ): CategoryRecord = CategoryRecord(
        sourceId = id,
        name = name,
        searchTerms = searchTerms,
        embedding = embedding?.toBase64(),
        references = referenceImageIds.mapNotNull(ctx.mediaRefs::ref),
        threshold = threshold,
        isUserCreated = isUserCreated,
        isPinned = isPinned,
        createdAt = createdAt,
        updatedAt = updatedAt,
        memberships = memberships.mapNotNull { membership ->
            ctx.mediaRefs.ref(membership.mediaId)?.let { ref ->
                CategoryMembershipRecord(
                    media = ref,
                    similarityScore = membership.similarityScore,
                    addedAt = membership.addedAt,
                    isManuallyAdded = membership.isManuallyAdded,
                    resultRevision = membership.resultRevision
                )
            }
        }
    )

    // ----- METADATA ------------------------------------------------------------------------------

    private suspend fun exportMetadata(writer: SectionWriter, ctx: ExportContext) {
        val metadataDao = database.getMetadataDao()
        val core = metadataDao.getAllCore().associateBy { it.mediaId }
        val video = metadataDao.getAllVideo().associateBy { it.mediaId }
        val flags = metadataDao.getAllFlags().associateBy { it.mediaId }
        val capture = database.getMediaCaptureTimeDao().getAll().associateBy { it.mediaId }
        val ids = buildSet {
            addAll(core.keys)
            addAll(video.keys)
            addAll(flags.keys)
            addAll(capture.keys)
        }
        ids.forEach { id ->
            val ref = ctx.mediaRefs.ref(id) ?: return@forEach
            writer.write(
                MetadataRecord.serializer(),
                MetadataRecord(
                    media = ref,
                    core = core[id]?.toRecord(),
                    video = video[id]?.toRecord(),
                    flags = flags[id]?.toRecord(),
                    captureTime = capture[id]?.toRecord()
                )
            )
        }
    }

    private fun MediaMetadataCore.toRecord() = MediaMetadataCoreRecord(
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

    private fun MediaMetadataVideo.toRecord() = MediaMetadataVideoRecord(
        durationMs = durationMs,
        videoWidth = videoWidth,
        videoHeight = videoHeight,
        frameRate = frameRate,
        bitRate = bitRate
    )

    private fun MediaMetadataFlags.toRecord() = MediaMetadataFlagsRecord(
        isNightMode = isNightMode,
        isPanorama = isPanorama,
        isPhotosphere = isPhotosphere,
        isLongExposure = isLongExposure,
        isMotionPhoto = isMotionPhoto
    )

    private fun MediaCaptureTimeEntity.toRecord() = MediaCaptureTimeRecord(
        captureTimestampMillis = captureTimestampMillis,
        origin = origin.storedValue,
        sourceModifiedSeconds = sourceModifiedSeconds,
        sourceTakenTimestampMillis = sourceTakenTimestampMillis,
        sourceSize = sourceSize,
        sourcePath = sourcePath,
        updatedAtMillis = updatedAtMillis
    )


    private suspend fun exportSearchHistory(writer: SectionWriter, ctx: ExportContext) {
        val raw = context.activeDataStore.data.first()[SEARCH_HISTORY_KEY] ?: return
        val history = runCatching {
            json.decodeFromString(ListSerializer(SearchHistory.serializer()), raw)
        }.getOrElse { emptyList() }
        history.forEach { entry ->
            writer.write(
                SearchHistoryRecord.serializer(),
                SearchHistoryRecord(
                    timestamp = entry.timestamp,
                    query = entry.query,
                    media = ctx.mediaRefs.ref(entry.mediaId),
                    mediaLabel = entry.mediaLabel,
                    mediaUri = entry.mediaUri
                )
            )
        }
    }

    // ----- PEOPLE --------------------------------------------------------------------------------

    private suspend fun exportPeople(writer: SectionWriter, ctx: ExportContext) {
        val personDao = database.getPersonDao()
        personDao.getByProviderOnce(ProviderType.LOCAL_PEOPLE).forEach { person ->
            writer.write(
                PeopleRecord.serializer(),
                PeopleRecord.Person(PersonRecord(
                    id = person.id,
                    name = person.name,
                    birthDate = person.birthDate,
                    hidden = person.hidden,
                        thumbnailMedia = ctx.mediaRefs.ref(person.thumbnailMediaId)
                    )
                )
            )
        }
        val faceDao = database.getDetectedFaceDao()
        faceDao.getExclusions().forEach { exclusion ->
            ctx.mediaRefs.ref(exclusion.mediaId)?.let { ref ->
                writer.write(
                    PeopleRecord.serializer(),
                    PeopleRecord.Exclusion(
                        FaceExclusionRecord(ref, exclusion.personId, exclusion.createdAt)
                    )
                )
            }
        }
        faceDao.getLinks().forEach { link ->
            ctx.mediaRefs.ref(link.mediaId)?.let { ref ->
                writer.write(
                    PeopleRecord.serializer(),
                    PeopleRecord.Link(FaceLinkRecord(
                        media = ref,
                        left = link.left,
                        top = link.top,
                        right = link.right,
                        bottom = link.bottom,
                        personId = link.personId,
                            kind = link.kind.name,
                            createdAt = link.createdAt
                        )
                    )
                )
            }
        }
        faceDao.getSuppressions().forEach { suppression ->
            ctx.mediaRefs.ref(suppression.mediaId)?.let { ref ->
                writer.write(
                    PeopleRecord.serializer(),
                    PeopleRecord.Suppression(FaceSuppressionRecord(
                        media = ref,
                        left = suppression.left,
                        top = suppression.top,
                        right = suppression.right,
                            bottom = suppression.bottom,
                            createdAt = suppression.createdAt
                        )
                    )
                )
            }
        }
    }


    private suspend fun exportCloudPrefs(writer: SectionWriter, ctx: ExportContext) {
        database.getCloudAlbumSyncDao().getAllAsync().forEach { entity ->
            writer.write(
                CloudPrefRecord.serializer(),
                CloudPrefRecord.AlbumSync(CloudAlbumSyncRecord(
                    sourceAccountId = backupSourceAccountId(
                        entity.providerType.name,
                        entity.serverConfigId
                    ),
                    providerType = entity.providerType.name,
                    remoteAlbumId = entity.albumRemoteId,
                    albumName = entity.albumName,
                        syncEnabled = entity.syncEnabled
                    )
                )
            )
        }
        database.getCloudUploadPrefDao().getAll().first().forEach { entity ->
            writer.write(
                CloudPrefRecord.serializer(),
                CloudPrefRecord.Upload(CloudUploadPrefRecord(
                    sourceAccountId = backupSourceAccountId(
                        entity.providerType.name,
                        entity.serverConfigId
                    ),
                    providerType = entity.providerType.name,
                    album = ctx.albumRefs.ref(entity.albumId)
                        ?: AlbumRef.Local(label = entity.albumLabel, bucketId = entity.albumId),
                    albumLabel = entity.albumLabel,
                    uploadEnabled = entity.uploadEnabled,
                    deleteLocalAfterUpload = entity.deleteLocalAfterUpload,
                        customPath = entity.customPath
                    )
                )
            )
        }
        database.getCloudDeleteLocalPrefDao().getAllAsync().forEach { entity ->
            writer.write(
                CloudPrefRecord.serializer(),
                CloudPrefRecord.DeleteLocal(CloudDeleteLocalPrefRecord(
                    album = ctx.albumRefs.ref(entity.albumId)
                        ?: AlbumRef.Local(label = entity.albumLabel, bucketId = entity.albumId),
                    albumLabel = entity.albumLabel,
                        enabled = entity.enabled
                    )
                )
            )
        }
        database.getCloudOfflinePinDao().getAllAsync().forEach { entity ->
            writer.write(
                CloudPrefRecord.serializer(),
                CloudPrefRecord.OfflinePin(CloudOfflinePinRecord(
                    sourceAccountId = backupSourceAccountId(
                        entity.providerType.name,
                        entity.serverConfigId
                    ),
                    providerType = entity.providerType.name,
                    label = entity.label,
                        createdAt = entity.createdAt
                    )
                )
            )
        }
        exportFreeSpacePrefs().forEach { writer.write(CloudPrefRecord.serializer(), CloudPrefRecord.FreeSpace(it)) }
    }

    private suspend fun exportFreeSpacePrefs(): List<FreeSpacePrefRecord> {
        val prefs = context.activeDataStore.data.first()
        val scheduled = prefs[SCHEDULED_CONFIG_IDS_KEY].orEmpty()
        return cloudServerConfigDao.getAll().first().mapNotNull { config ->
            val keepFavorites = prefs[booleanPreferencesKey("cloud_free_space_keep_favorites_${config.id}")]
                ?: return@mapNotNull null
            FreeSpacePrefRecord(
                sourceAccountId = backupSourceAccountId(config.providerType.name, config.id),
                keepFavorites = keepFavorites,
                cutoffDays = prefs[intPreferencesKey("cloud_free_space_cutoff_days_${config.id}")] ?: -1,
                autoEnabled = prefs[booleanPreferencesKey("cloud_free_space_auto_enabled_${config.id}")] ?: false,
                autoIntervalDays = prefs[intPreferencesKey("cloud_free_space_auto_interval_days_${config.id}")] ?: 7,
                scheduled = scheduled.contains(config.id.toString())
            )
        }
    }


    private suspend fun exportEditBackups(writer: SectionWriter, ctx: ExportContext) {
        val backupDir = File(context.filesDir, "edit_backups")
        database.getEditHistoryDao().getAllEditedMedia().forEachIndexed { index, edited ->
            val ref = ctx.mediaRefs.ref(edited.mediaId) ?: return@forEachIndexed
            val source = File(edited.backupPath)
            if (!source.isFile) {
                printError("ConfigBackupManager: missing edit backup for ${edited.mediaId}")
                return@forEachIndexed
            }
            val entryName = "${BackupManifest.EDIT_BACKUPS_DIR}/$index.bak"
            val destination = File(ctx.workDir, entryName)
            destination.parentFile?.mkdirs()
            val digest = DigestingOutputStream(destination.outputStream().buffered())
            digest.use { source.inputStream().use { input -> input.copyTo(it) } }
            writer.write(
                EditBackupRecord.serializer(),
                EditBackupRecord(
                    media = ref,
                    originalUri = edited.originalUri.toString(),
                    originalMimeType = edited.originalMimeType,
                    editTimestamp = edited.editTimestamp,
                    fileName = entryName,
                    archiveSize = digest.byteCount,
                    sha256 = shaOf(source)
                )
            )
        }
    }


    private suspend fun exportDeviceUi(writer: SectionWriter) {
        val uiPrefs = context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)
        uiPrefs.all.forEach { (key, value) ->
            if (value is Int) writer.write(DeviceUiRecord.serializer(), DeviceUiRecord.Grid(GridSizeRecord(key, value)))
        }
        val widgetPrefs = context.getSharedPreferences("gallery_widget_prefs", Context.MODE_PRIVATE)
        widgetPrefs.all.forEach { (key, value) ->
            if (value is String) {
                writer.write(
                    DeviceUiRecord.serializer(),
                    DeviceUiRecord.Widget(WidgetRecord(name = key, payload = value))
                )
            }
        }
        val prefs = context.activeDataStore.data.first()
        val uri = prefs[PRIVATE_FOLDER_URI_KEY]
        if (!uri.isNullOrBlank()) {
            writer.write(
                DeviceUiRecord.serializer(),
                DeviceUiRecord.PrivateFolder(PrivateFolderRecord(
                    uri = uri,
                    enabled = prefs[PRIVATE_FOLDER_ENABLED_KEY] ?: false,
                        gateMode = prefs[PRIVATE_FOLDER_GATE_MODE_KEY]
                    )
                )
            )
        }
    }

    // =============================================================================================
    // Archive writing
    // =============================================================================================

    private fun writeArchive(
        destination: Uri,
        manifest: BackupManifest,
        counts: Map<BackupSection, Int>,
        payload: File,
        password: String?
    ): Long {
        val preview = BackupPreviewHeader(
            schemaVersion = BackupManifest.SCHEMA_VERSION,
            encrypted = !password.isNullOrEmpty(),
            appVersionName = manifest.appVersionName,
            appVersionCode = manifest.appVersionCode,
            exportedAt = manifest.exportedAt,
            credentialIncluded = manifest.credentialIncluded,
            sections = counts.mapKeys { it.key.name },
            vaults = manifest.vaults.map {
                PreviewVault(
                    uuid = it.uuid,
                    name = if (password.isNullOrEmpty()) it.name else "",
                    mediaCount = it.media.size
                )
            },
            payloadBytes = payload.length()
        )
        val header = json.encodeToString(BackupPreviewHeader.serializer(), preview)
            .toByteArray(Charsets.UTF_8)
        var written = 0L
        openTruncated(destination).use { raw ->
            val counting = CountingOutputStream(raw)
            val out = BufferedOutputStream(counting)
            out.write(BackupPreviewHeader.MAGIC.toByteArray())
            out.write(ByteBuffer.allocate(4).putInt(header.size).array())
            out.write(header)
            if (!password.isNullOrEmpty()) {
                payload.inputStream().use { input ->
                    BackupCrypto.encryptV2(input, out, password)
                }
            } else {
                payload.inputStream().use { input -> input.copyTo(out) }
            }
            out.flush()
            written = counting.bytes
        }
        // Some SAF providers ignore truncate modes. Trim any stale tail so the ZIP central
        // directory remains at the end, then verify the header we actually wrote.
        truncateTo(destination, written)
        verifyArchiveHeader(destination, preview.encrypted, written)
        return payload.length()
    }

    private class CountingOutputStream(
        private val delegate: OutputStream
    ) : OutputStream() {
        var bytes: Long = 0L
            private set

        override fun write(b: Int) {
            delegate.write(b)
            bytes++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            delegate.write(b, off, len)
            bytes += len
        }

        override fun flush() = delegate.flush()
    }

    private fun openTruncated(uri: Uri): OutputStream {
        var last: Exception? = null
        for (mode in listOf("rwt", "wt", "w")) {
            try {
                context.contentResolver.openOutputStream(uri, mode)?.let { return it }
            } catch (e: Exception) {
                last = e
            }
        }
        throw IOException("Unable to open output stream", last)
    }

    private fun truncateTo(uri: Uri, bytes: Long) {
        runCatching {
            val pfd: ParcelFileDescriptor =
                context.contentResolver.openFileDescriptor(uri, "rw") ?: return
            try {
                FileOutputStream(pfd.fileDescriptor).use { it.channel.truncate(bytes) }
            } finally {
                pfd.close()
            }
        }.onFailure {
            printError("app.backup", "could not truncate backup destination", it)
        }
    }

    private fun verifyArchiveHeader(uri: Uri, expectedEncrypted: Boolean, expectedBytes: Long) {
        val probe = context.contentResolver.openInputStream(uri)?.use { input ->
            val buffered = BufferedInputStream(input)
            val magic = BackupCrypto.readHeader(buffered, BackupPreviewHeader.MAGIC_SIZE)
            if (!magic.contentEquals(BackupPreviewHeader.MAGIC.toByteArray())) {
                throw IOException("Backup write did not produce a valid header")
            }
            val lengthBytes = magicLengthBytes(buffered)
            val headerBytes = ByteArray(lengthBytes)
            var read = 0
            while (read < lengthBytes) {
                val n = buffered.read(headerBytes, read, lengthBytes - read)
                if (n < 0) throw IOException("Backup header truncated")
                read += n
            }
            json.decodeFromString(
                BackupPreviewHeader.serializer(),
                headerBytes.decodeToString()
            )
        } ?: throw IOException("Unable to verify backup destination")
        if (probe.encrypted != expectedEncrypted) {
            throw IOException("Backup destination retained stale data")
        }
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                val size = descriptor.statSize
                if (size >= 0 && expectedBytes > 0 && size != expectedBytes) {
                    throw IOException("Backup destination size mismatch ($size != $expectedBytes)")
                }
            }
        }.getOrElse { throw it }
    }

    private fun magicLengthBytes(input: InputStream): Int {
        val bytes = ByteArray(4)
        var read = 0
        while (read < 4) {
            val n = input.read(bytes, read, 4 - read)
            if (n < 0) throw IOException("Backup header truncated")
            read += n
        }
        val length = ByteBuffer.wrap(bytes).int
        if (length <= 0 || length > 4 * 1024 * 1024) {
            throw IOException("Invalid backup header length")
        }
        return length
    }

    private fun zipDirectory(root: File, destination: File) {
        ZipOutputStream(BufferedOutputStream(destination.outputStream())).use { zip ->
            root.walkTopDown()
                .filter { it.isFile }
                .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
                .forEach { file ->
                    val name = file.relativeTo(root).invariantSeparatorsPath
                    val store = name.startsWith(BackupManifest.VAULTS_DIR) ||
                        name.startsWith(BackupManifest.EDIT_BACKUPS_DIR)
                    val entry = ZipEntry(name)
                    if (store) {
                        entry.method = ZipEntry.STORED
                        entry.size = file.length()
                        entry.compressedSize = file.length()
                        entry.crc = crc32(file)
                    }
                    zip.putNextEntry(entry)
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
    }

    private fun crc32(file: File): Long {
        val crc = CRC32()
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                crc.update(buffer, 0, read)
            }
        }
        return crc.value
    }

    private fun shaOf(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
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

    // =============================================================================================
    // Vaults
    // =============================================================================================

    private data class PlannedVault(
        val vault: com.dot.gallery.domain.model.Vault,
        val media: List<Pair<com.dot.gallery.domain.model.EncryptedMedia2, VaultMediaEntry>>
    )

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
                val digest = DigestingOutputStream()
                writeDecryptedVaultMedia(vault, media, digest)
                media to media.toEntry(entryName).copy(
                    archiveSize = digest.byteCount,
                    sha256 = digest.sha256()
                )
            }
            PlannedVault(vault, pairs)
        }
    }

    private suspend fun writeVaultPayloads(
        planned: List<PlannedVault>,
        workDir: File,
        onProgress: BackupProgressListener
    ): List<VaultEntry> {
        val total = planned.sumOf { it.media.size }
        var done = 0
        if (planned.isNotEmpty()) onProgress(BackupSection.VAULTS, 0, total)
        return planned.map { pv ->
            pv.media.forEach { (media, entry) ->
                val outFile = File(workDir, entry.fileName)
                outFile.parentFile?.mkdirs()
                outFile.outputStream().use { output ->
                    writeDecryptedVaultMedia(pv.vault, media, output)
                }
                done++
                onProgress(BackupSection.VAULTS, done, total)
            }
            VaultEntry(
                uuid = pv.vault.uuid.toString(),
                name = pv.vault.name,
                media = pv.media.map { it.second }
            )
        }
    }

    private suspend fun readVaultCredentials(
        vaults: List<com.dot.gallery.domain.model.Vault>
    ): List<VaultCredentialEntry> {
        val gateMode = VaultPasswordManager.getGateMode(context).name
        val lockAll = VaultPasswordManager.getVaultLockAll(context)
        return vaults.mapNotNull { vault ->
            val credential = VaultPasswordManager.getStoredCredential(context, vault.uuid)
                ?: return@mapNotNull null
            val type = VaultPasswordManager.getAuthType(context, vault.uuid)?.name
                ?: return@mapNotNull null
            VaultCredentialEntry(
                vaultUuid = vault.uuid.toString(),
                authType = type,
                credential = credential,
                gateMode = gateMode,
                lockAll = lockAll
            )
        }
    }

    private fun writeDecryptedVaultMedia(
        vault: com.dot.gallery.domain.model.Vault,
        media: com.dot.gallery.domain.model.EncryptedMedia2,
        output: OutputStream
    ) {
        val encFile = with(keychainHolder) { vault.mediaFile(media.id) }
        if (!encFile.exists()) throw IOException("Missing vault file for media ${media.id}")
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
                tempFile?.delete()
            }
        }
    }

    private fun com.dot.gallery.domain.model.EncryptedMedia2.toEntry(fileName: String) =
        VaultMediaEntry(
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

    // =============================================================================================
    // Restore
    // =============================================================================================

    suspend fun importSectioned(
        source: Uri,
        probe: BackupProbe,
        selection: BackupSelection,
        password: String?,
        onProgress: BackupProgressListener
    ): ImportResult = withContext(Dispatchers.IO) {
        BackupV3Restorer(
            context = context,
            database = database,
            keychainHolder = keychainHolder,
            cloudMediaDao = cloudMediaDao,
            cloudServerConfigDao = cloudServerConfigDao,
            mediaRepository = mediaRepository,
            pendingCloudFavoriteStore = pendingCloudFavoriteStore,
            providerInitializer = providerInitializer,
            syncScheduler = syncScheduler,
            legacy = legacy,
            restoreOrder = restoreOrder
        ).restore(source, probe, selection, password, onProgress)
    }

    private fun CloudServerConfigEntity.toEntry() = CloudConfigEntry(
        providerType = providerType.name,
        serverUrl = serverUrl,
        sourceConfigId = id,
        sourceAccountId = backupSourceAccountId(providerType.name, id),
        requiresReauthentication = !apiKey.isNullOrBlank() || !encryptedPassword.isNullOrBlank(),
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

    private companion object {
        val SEARCH_HISTORY_KEY = stringPreferencesKey("search_history_v2")
        val SCHEDULED_CONFIG_IDS_KEY =
            stringSetPreferencesKey("cloud_free_space_scheduled_config_ids")
        val PRIVATE_FOLDER_URI_KEY = stringPreferencesKey("private_folder_uri")
        val PRIVATE_FOLDER_ENABLED_KEY = booleanPreferencesKey("private_folder_enabled")
        val PRIVATE_FOLDER_GATE_MODE_KEY = stringPreferencesKey("private_folder_gate_mode")
    }
}
