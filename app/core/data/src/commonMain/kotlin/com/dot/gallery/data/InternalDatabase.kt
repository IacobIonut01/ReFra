/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data

import androidx.room3.AutoMigration
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.DeleteColumn
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.ColumnTypeConverters
import androidx.room3.migration.AutoMigrationSpec
import com.dot.gallery.data.CloudConverters
import com.dot.gallery.data.dao.CloudAlbumSyncDao
import com.dot.gallery.data.dao.CloudDeleteLocalPrefDao
import com.dot.gallery.data.dao.CloudMediaDao
import com.dot.gallery.data.dao.CloudOfflinePinDao
import com.dot.gallery.data.dao.CloudServerConfigDao
import com.dot.gallery.data.dao.DetectedFaceDao
import com.dot.gallery.data.dao.CloudTagDao
import com.dot.gallery.data.dao.CloudUploadPrefDao
import com.dot.gallery.data.dao.PersonDao
import com.dot.gallery.data.dao.SyncStateDao
import com.dot.gallery.data.entity.CloudAlbumSyncEntity
import com.dot.gallery.data.entity.CloudBackupRevisionEntity
import com.dot.gallery.data.entity.CloudMediaEntity
import com.dot.gallery.data.entity.CloudMediaTagEntity
import com.dot.gallery.data.entity.CloudOfflinePinEntity
import com.dot.gallery.data.entity.CloudServerConfigEntity
import com.dot.gallery.data.entity.CloudTagEntity
import com.dot.gallery.data.entity.DetectedFaceEntity
import com.dot.gallery.data.entity.FaceClusterEntity
import com.dot.gallery.data.entity.FaceExclusionEntity
import com.dot.gallery.data.entity.FaceLinkEntity
import com.dot.gallery.data.entity.FaceSuppressionEntity
import com.dot.gallery.data.entity.OcrResultEntity
import com.dot.gallery.data.entity.PersonEntity
import com.dot.gallery.data.entity.CloudUploadPrefEntity
import com.dot.gallery.data.entity.CloudDeleteLocalPrefEntity
import com.dot.gallery.data.entity.SyncStateEntity
import com.dot.gallery.domain.model.AlbumGroup
import com.dot.gallery.domain.model.AlbumGroupMember
import com.dot.gallery.domain.model.AlbumSection
import com.dot.gallery.domain.model.AlbumSectionMember
import com.dot.gallery.domain.model.AlbumThumbnail
import com.dot.gallery.domain.model.Category
import com.dot.gallery.domain.model.Collection
import com.dot.gallery.domain.model.CollectionAlbum
import com.dot.gallery.domain.model.CollectionExcludedMedia
import com.dot.gallery.domain.model.CollectionMedia
import com.dot.gallery.domain.model.EditedMedia
import com.dot.gallery.domain.model.IgnoredAlbum
import com.dot.gallery.domain.model.LockedAlbum
import com.dot.gallery.domain.model.ImageEmbedding
import com.dot.gallery.domain.model.MediaCategory
import com.dot.gallery.data.entity.MediaCaptureTimeEntity
import com.dot.gallery.data.entity.MediaMetadataCore
import com.dot.gallery.data.entity.MediaMetadataFlags
import com.dot.gallery.data.entity.MediaMetadataVideo
import com.dot.gallery.domain.model.MergedSubfolderAlbum
import com.dot.gallery.domain.model.MediaVersion
import com.dot.gallery.domain.model.PinnedAlbum
import com.dot.gallery.domain.model.ScannedMedia
import com.dot.gallery.domain.model.TimelineSettings
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.model.ClassifiedMedia
import com.dot.gallery.domain.model.EncryptedMedia2
import com.dot.gallery.data.dao.AlbumGroupDao
import com.dot.gallery.data.dao.AlbumSectionDao
import com.dot.gallery.data.dao.AlbumThumbnailDao
import com.dot.gallery.data.dao.BlacklistDao
import com.dot.gallery.data.dao.CategoryDao
import com.dot.gallery.data.dao.ClassifierDao
import com.dot.gallery.data.dao.CollectionDao
import com.dot.gallery.data.dao.EditHistoryDao
import com.dot.gallery.data.dao.ImageEmbeddingDao
import com.dot.gallery.data.dao.LockedAlbumDao
import com.dot.gallery.data.dao.MediaCaptureTimeDao
import com.dot.gallery.data.dao.MediaDao
import com.dot.gallery.data.entity.MediaFeatureStateEntity
import com.dot.gallery.data.dao.MergedSubfolderDao
import com.dot.gallery.data.dao.MetadataDao
import com.dot.gallery.data.dao.PinnedDao
import com.dot.gallery.data.dao.ScannedMediaDao
import com.dot.gallery.data.dao.SmartScanDao
import com.dot.gallery.data.entity.SmartScanPhaseEntity
import com.dot.gallery.data.entity.SmartScanRunEntity
import com.dot.gallery.data.dao.VaultDao
import com.dot.gallery.data.Converters


@ConstructedBy(InternalDatabaseConstructor::class)
@Database(
    entities = [
        PinnedAlbum::class,
        IgnoredAlbum::class,
        UriMedia::class,
        MediaVersion::class,
        TimelineSettings::class,
        ClassifiedMedia::class,
        EncryptedMedia2::class,
        Vault::class,
        MediaMetadataCore::class,
        MediaMetadataVideo::class,
        MediaMetadataFlags::class,
        AlbumThumbnail::class,
        ImageEmbedding::class,
        Category::class,
        MediaCategory::class,
        EditedMedia::class,
        LockedAlbum::class,
        AlbumGroup::class,
        AlbumGroupMember::class,
        MergedSubfolderAlbum::class,
        Collection::class,
        CollectionMedia::class,
        CollectionAlbum::class,
        CollectionExcludedMedia::class,
        ScannedMedia::class,
        CloudMediaEntity::class,
        CloudBackupRevisionEntity::class,
        CloudServerConfigEntity::class,
        PersonEntity::class,
        DetectedFaceEntity::class,
        FaceClusterEntity::class,
        FaceExclusionEntity::class,
        FaceLinkEntity::class,
        FaceSuppressionEntity::class,
        OcrResultEntity::class,
        SyncStateEntity::class,
        CloudAlbumSyncEntity::class,
        CloudUploadPrefEntity::class,
        CloudDeleteLocalPrefEntity::class,
        CloudOfflinePinEntity::class,
        AlbumSection::class,
        AlbumSectionMember::class,
        SmartScanRunEntity::class,
        SmartScanPhaseEntity::class,
        MediaFeatureStateEntity::class,
        MediaCaptureTimeEntity::class,
        CloudTagEntity::class,
        CloudMediaTagEntity::class
    ],
    version = 56,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 11, to = 12),
        // Migration 12 to 13 is handled manually in IgnoredAlbumMigration.kt
        AutoMigration(from = 13, to = 14),
        AutoMigration(from = 14, to = 15, spec = InternalDatabase.RemoveIconEmojiMigration::class),
        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 19, to = 20),
        AutoMigration(from = 20, to = 21),
        AutoMigration(from = 21, to = 22),
        AutoMigration(from = 22, to = 23),
        AutoMigration(from = 23, to = 24),
        AutoMigration(from = 24, to = 25),
        AutoMigration(from = 25, to = 26),
        AutoMigration(from = 26, to = 27),
        AutoMigration(from = 27, to = 28),
        AutoMigration(from = 28, to = 29),
        AutoMigration(from = 29, to = 30),
        AutoMigration(from = 30, to = 31),
        AutoMigration(from = 31, to = 32),
        AutoMigration(from = 32, to = 33),
        // Migration 33 to 34 is handled manually in CloudUploadPrefMigration.kt
        // (cloud_upload_pref primary key changed to (serverConfigId, albumId))
        AutoMigration(from = 34, to = 35), // cloud_media.fileId (server numeric file id)
        // Migration 35 to 36 is handled manually in CloudMediaPkMigration.kt
        // (cloud_media primary key extended to (remoteId, providerType, serverConfigId))
        // Migration 36 to 37 is handled manually in CloudMediaPkMigration.kt
        // (purges the cloud_media cache to recover from the faulty 35->36 SELECT * copy)
        // Migration 37 to 38 is handled manually in CloudDeleteLocalPrefMigration.kt
        // (global per-album delete-local table)
        AutoMigration(from = 38, to = 39), // cloud_offline_pin (accounts marked available offline)
        AutoMigration(from = 39, to = 40), // people.hidden (on-device Person grouping)
        // Migration 40 to 41 is handled manually in SmartScanMigration.kt
        // Migration 41 to 42 is handled manually in MergedSubfolderMigration.kt
        // Migration 42 to 43 is handled manually in SmartScanMigration.kt
        // Migrations 43 to 45 are handled manually in CloudAlbumSyncPkMigration.kt
        // Migration 45 to 46 is handled manually in CaptureTimeMigration.kt
        // Migration 46 to 47 is handled manually in CloudTagMigration.kt
        AutoMigration(from = 47, to = 48), // cloud_server_config.downloadRemoteEnabled/downloadVideos
        AutoMigration(from = 48, to = 49), // uploadBasePath/uploadVideosPath + cloud_upload_pref.customPath
        AutoMigration(from = 49, to = 50), // face_exclusions (user "not this person" assertions)
        AutoMigration(from = 50, to = 51), // face_links (face-scoped include/exclude assertions)
        AutoMigration(from = 51, to = 52), // face_suppressions (deleted-person face bans)
        AutoMigration(from = 52, to = 53), // people.birthDate (on-device person birthday)
        // Migration 53 to 54 is handled manually in EmbeddingGemma2Migration.kt
        // (selectable search engines: purge foreign-dimension embeddings, normalize thresholds)
        AutoMigration(from = 54, to = 55), // collection_excluded_media
        AutoMigration(from = 55, to = 56), // categories.coverMediaId (user-picked category cover)
    ]
)
@ColumnTypeConverters(Converters::class, CloudConverters::class, SmartScanConverters::class)
abstract class InternalDatabase : RoomDatabase() {

    @DeleteColumn(tableName = "categories", columnName = "iconEmoji")
    class RemoveIconEmojiMigration : AutoMigrationSpec

    abstract fun getPinnedDao(): PinnedDao

    abstract fun getBlacklistDao(): BlacklistDao

    abstract fun getMediaDao(): MediaDao

    abstract fun getMediaCaptureTimeDao(): MediaCaptureTimeDao

    abstract fun getClassifierDao(): ClassifierDao

    abstract fun getVaultDao(): VaultDao

    abstract fun getMetadataDao(): MetadataDao

    abstract fun getAlbumThumbnailDao(): AlbumThumbnailDao

    abstract fun getImageEmbeddingDao(): ImageEmbeddingDao

    abstract fun getCategoryDao(): CategoryDao

    abstract fun getEditHistoryDao(): EditHistoryDao

    abstract fun getLockedAlbumDao(): LockedAlbumDao

    abstract fun getAlbumGroupDao(): AlbumGroupDao

    abstract fun getMergedSubfolderDao(): MergedSubfolderDao

    abstract fun getCollectionDao(): CollectionDao

    abstract fun getScannedMediaDao(): ScannedMediaDao

    abstract fun getSmartScanDao(): SmartScanDao

    abstract fun getCloudMediaDao(): CloudMediaDao

    abstract fun getCloudServerConfigDao(): CloudServerConfigDao

    abstract fun getPersonDao(): PersonDao

    abstract fun getDetectedFaceDao(): DetectedFaceDao

    abstract fun getSyncStateDao(): SyncStateDao

    abstract fun getCloudAlbumSyncDao(): CloudAlbumSyncDao

    abstract fun getCloudUploadPrefDao(): CloudUploadPrefDao

    abstract fun getCloudDeleteLocalPrefDao(): CloudDeleteLocalPrefDao

    abstract fun getCloudOfflinePinDao(): CloudOfflinePinDao

    abstract fun getAlbumSectionDao(): AlbumSectionDao

    abstract fun getCloudTagDao(): CloudTagDao

    companion object {
        const val NAME = "internal_db"
    }
}


@Suppress("KotlinNoActualForExpect", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object InternalDatabaseConstructor : RoomDatabaseConstructor<InternalDatabase> {
    override fun initialize(): InternalDatabase
}
