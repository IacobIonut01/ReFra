/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.data.data_source

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import com.dot.gallery.feature_node.domain.model.AlbumThumbnail
import com.dot.gallery.feature_node.domain.model.Category
import com.dot.gallery.feature_node.domain.model.IgnoredAlbum
import com.dot.gallery.feature_node.domain.model.ImageEmbedding
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaCategory
import com.dot.gallery.feature_node.domain.model.MediaMetadataCore
import com.dot.gallery.feature_node.domain.model.MediaMetadataFlags
import com.dot.gallery.feature_node.domain.model.MediaMetadataVideo
import com.dot.gallery.feature_node.domain.model.MediaVersion
import com.dot.gallery.feature_node.domain.model.PinnedAlbum
import com.dot.gallery.feature_node.domain.model.TimelineSettings
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.util.Converters

@Database(
    entities = [
        PinnedAlbum::class,
        IgnoredAlbum::class,
        Media.UriMedia::class,
        MediaVersion::class,
        TimelineSettings::class,
        Media.ClassifiedMedia::class,
        Media.EncryptedMedia2::class,
        Vault::class,
        MediaMetadataCore::class,
        MediaMetadataVideo::class,
        MediaMetadataFlags::class,
        AlbumThumbnail::class,
        ImageEmbedding::class,
        Category::class,
        MediaCategory::class
    ],
    version = 15,
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
    ]
)
@TypeConverters(Converters::class)
abstract class InternalDatabase : RoomDatabase() {

    @DeleteColumn(tableName = "categories", columnName = "iconEmoji")
    class RemoveIconEmojiMigration : AutoMigrationSpec

    abstract fun getPinnedDao(): PinnedDao

    abstract fun getBlacklistDao(): BlacklistDao

    abstract fun getMediaDao(): MediaDao

    abstract fun getClassifierDao(): ClassifierDao

    abstract fun getVaultDao(): VaultDao

    abstract fun getMetadataDao(): MetadataDao

    abstract fun getAlbumThumbnailDao(): AlbumThumbnailDao

    abstract fun getImageEmbeddingDao(): ImageEmbeddingDao

    abstract fun getCategoryDao(): CategoryDao

    companion object {
        const val NAME = "internal_db"
    }
}