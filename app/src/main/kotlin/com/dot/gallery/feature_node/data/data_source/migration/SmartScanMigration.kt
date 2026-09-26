/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.data.data_source.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.cloudMediaId
import com.dot.gallery.feature_node.domain.util.FloatVectorCodec
import com.dot.gallery.feature_node.presentation.util.printError
import kotlinx.serialization.json.Json

/**
 * Adds durable smart-scan orchestration state and a single account-safe media id namespace.
 *
 * Cloud ids are calculated in Kotlin with the same [cloudMediaId] function used at runtime. SQL
 * cannot reproduce that SHA-256 based derivation. The unique index is deliberately created only
 * after every old row has been backfilled and checked for the (extremely unlikely) hash collision.
 */
val MIGRATION_40_41 = object : Migration(40, 41) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `cloud_media` ADD COLUMN `globalMediaId` INTEGER NOT NULL DEFAULT 0")
        backfillGlobalMediaIds(db)
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_cloud_media_globalMediaId` " +
                "ON `cloud_media` (`globalMediaId`)"
        )

        db.execSQL("ALTER TABLE `image_embeddings` ADD COLUMN `resultRevision` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `detected_faces` ADD COLUMN `resultRevision` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `ocr_results` ADD COLUMN `resultRevision` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `media_category` ADD COLUMN `resultRevision` TEXT NOT NULL DEFAULT ''")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `smart_scan_runs` (
                `runId` TEXT NOT NULL,
                `trigger` TEXT NOT NULL,
                `requestedFeatures` INTEGER NOT NULL DEFAULT 0,
                `userVisible` INTEGER NOT NULL DEFAULT 0,
                `fullRefresh` INTEGER NOT NULL DEFAULT 0,
                `workId` TEXT,
                `currentPhase` TEXT,
                `status` TEXT NOT NULL DEFAULT 'queued',
                `requestedAt` INTEGER NOT NULL,
                `startedAt` INTEGER,
                `finishedAt` INTEGER,
                `updatedAt` INTEGER NOT NULL,
                `leaseOwner` TEXT,
                `leaseExpiresAt` INTEGER,
                `sourceSnapshot` TEXT NOT NULL DEFAULT '',
                `totalMedia` INTEGER NOT NULL DEFAULT 0,
                `processedMedia` INTEGER NOT NULL DEFAULT 0,
                `succeededMedia` INTEGER NOT NULL DEFAULT 0,
                `skippedMedia` INTEGER NOT NULL DEFAULT 0,
                `failedMedia` INTEGER NOT NULL DEFAULT 0,
                `lastErrorCode` TEXT,
                PRIMARY KEY(`runId`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_smart_scan_runs_status` ON `smart_scan_runs` (`status`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_smart_scan_runs_requestedAt` " +
                "ON `smart_scan_runs` (`requestedAt`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_smart_scan_runs_leaseExpiresAt` " +
                "ON `smart_scan_runs` (`leaseExpiresAt`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `smart_scan_phases` (
                `runId` TEXT NOT NULL,
                `phase` TEXT NOT NULL,
                `status` TEXT NOT NULL DEFAULT 'queued',
                `startedAt` INTEGER,
                `finishedAt` INTEGER,
                `updatedAt` INTEGER NOT NULL,
                `leaseOwner` TEXT,
                `leaseExpiresAt` INTEGER,
                `processorRevision` TEXT NOT NULL DEFAULT '',
                `attemptCount` INTEGER NOT NULL DEFAULT 0,
                `totalMedia` INTEGER NOT NULL DEFAULT 0,
                `processedMedia` INTEGER NOT NULL DEFAULT 0,
                `succeededMedia` INTEGER NOT NULL DEFAULT 0,
                `skippedMedia` INTEGER NOT NULL DEFAULT 0,
                `failedMedia` INTEGER NOT NULL DEFAULT 0,
                `lastErrorCode` TEXT,
                PRIMARY KEY(`runId`, `phase`),
                FOREIGN KEY(`runId`) REFERENCES `smart_scan_runs`(`runId`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_smart_scan_phases_runId` ON `smart_scan_phases` (`runId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_smart_scan_phases_status` ON `smart_scan_phases` (`status`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_smart_scan_phases_leaseExpiresAt` " +
                "ON `smart_scan_phases` (`leaseExpiresAt`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `media_feature_state` (
                `mediaId` INTEGER NOT NULL,
                `feature` TEXT NOT NULL,
                `status` TEXT NOT NULL DEFAULT 'pending',
                `sourceRevision` TEXT NOT NULL DEFAULT '',
                `resultRevision` TEXT NOT NULL DEFAULT '',
                `attemptCount` INTEGER NOT NULL DEFAULT 0,
                `updatedAt` INTEGER NOT NULL,
                `lastAttemptAt` INTEGER,
                `nextRetryAt` INTEGER,
                `leaseOwner` TEXT,
                `leaseExpiresAt` INTEGER,
                `runId` TEXT,
                `lastErrorCode` TEXT,
                PRIMARY KEY(`mediaId`, `feature`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_media_feature_state_feature_status` " +
                "ON `media_feature_state` (`feature`, `status`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_media_feature_state_status` " +
                "ON `media_feature_state` (`status`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_media_feature_state_leaseExpiresAt` " +
                "ON `media_feature_state` (`leaseExpiresAt`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_media_feature_state_runId` " +
                "ON `media_feature_state` (`runId`)"
        )

        // media_version is shared by the old media and metadata refresh paths. Neither old marker
        // proves that the newly revisioned feature outputs are complete, so force a safe refresh.
        db.execSQL("DELETE FROM `media_version`")
    }
}

private fun backfillGlobalMediaIds(db: SupportSQLiteDatabase) {
    data class CloudKey(val remoteId: String, val providerType: ProviderType, val serverConfigId: Long)

    val ids = HashMap<Long, CloudKey>()
    val rows = mutableListOf<Pair<CloudKey, Long>>()
    db.query(
        "SELECT `remoteId`, `providerType`, `serverConfigId` FROM `cloud_media`"
    ).use { cursor ->
        while (cursor.moveToNext()) {
            val key = CloudKey(
                remoteId = cursor.getString(0),
                providerType = ProviderType.valueOf(cursor.getString(1)),
                serverConfigId = cursor.getLong(2)
            )
            val globalMediaId = cloudMediaId(key.providerType, key.serverConfigId, key.remoteId)
            val previous = ids.putIfAbsent(globalMediaId, key)
            check(previous == null || previous == key) {
                "cloudMediaId collision for $previous and $key (id=$globalMediaId)"
            }
            rows += key to globalMediaId
        }
    }

    rows.forEach { (key, globalMediaId) ->
        db.execSQL(
            """
            UPDATE `cloud_media`
            SET `globalMediaId` = ?
            WHERE `remoteId` = ? AND `providerType` = ? AND `serverConfigId` = ?
            """.trimIndent(),
            arrayOf<Any>(globalMediaId, key.remoteId, key.providerType.name, key.serverConfigId)
        )
    }
}

val MIGRATION_42_43 = object : Migration(42, 43) {
    override fun migrate(db: SupportSQLiteDatabase) {
        migrateImageEmbeddings(db)
        migrateCategoryEmbeddings(db)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `face_clusters` (
                `personId` TEXT NOT NULL,
                `centroid` BLOB NOT NULL,
                `faceCount` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`personId`),
                FOREIGN KEY(`personId`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        backfillFaceClusters(db)
    }
}

private fun migrateImageEmbeddings(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE `image_embeddings_43` (
            `id` INTEGER NOT NULL,
            `date` INTEGER NOT NULL,
            `embedding` BLOB NOT NULL,
            `resultRevision` TEXT NOT NULL DEFAULT '',
            PRIMARY KEY(`id`)
        )
        """.trimIndent()
    )
    val invalidIds = mutableListOf<Long>()
    val insert = db.compileStatement(
        "INSERT INTO `image_embeddings_43` (`id`, `date`, `embedding`, `resultRevision`) VALUES (?, ?, ?, ?)"
    )
    try {
        db.query("SELECT `id`, `date`, `embedding`, `resultRevision` FROM `image_embeddings`").use { cursor ->
            while (cursor.moveToNext()) {
                val embedding = decodeLegacyVector(cursor.getString(2), expectedSize = 512)
                if (embedding == null) {
                    invalidIds += cursor.getLong(0)
                    continue
                }
                insert.clearBindings()
                insert.bindLong(1, cursor.getLong(0))
                insert.bindLong(2, cursor.getLong(1))
                insert.bindBlob(3, FloatVectorCodec.encode(embedding))
                insert.bindString(4, cursor.getString(3))
                insert.executeInsert()
            }
        }
    } finally {
        insert.close()
    }
    db.execSQL("DROP TABLE `image_embeddings`")
    db.execSQL("ALTER TABLE `image_embeddings_43` RENAME TO `image_embeddings`")
    invalidIds.forEach { mediaId ->
        db.execSQL(
            """
            UPDATE `media_feature_state`
            SET `status` = 'pending', `resultRevision` = '', `nextRetryAt` = NULL,
                `leaseOwner` = NULL, `leaseExpiresAt` = NULL, `runId` = NULL,
                `lastErrorCode` = 'invalid_legacy_embedding'
            WHERE `mediaId` = ? AND `feature` = 'search_embedding'
            """.trimIndent(),
            arrayOf<Any>(mediaId)
        )
    }
}

private fun migrateCategoryEmbeddings(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE `media_category_43_backup` AS
        SELECT `mediaId`, `categoryId`, `similarityScore`, `addedAt`, `isManuallyAdded`, `resultRevision`
        FROM `media_category`
        """.trimIndent()
    )
    db.execSQL("DROP TABLE `media_category`")
    db.execSQL(
        """
        CREATE TABLE `categories_43` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `name` TEXT NOT NULL,
            `searchTerms` TEXT NOT NULL,
            `embedding` BLOB,
            `referenceImageIds` TEXT NOT NULL DEFAULT '[]',
            `threshold` REAL NOT NULL,
            `isUserCreated` INTEGER NOT NULL,
            `isPinned` INTEGER NOT NULL,
            `createdAt` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL
        )
        """.trimIndent()
    )
    db.query(
        """
        SELECT `id`, `name`, `searchTerms`, `embedding`, `referenceImageIds`, `threshold`,
               `isUserCreated`, `isPinned`, `createdAt`, `updatedAt`
        FROM `categories`
        """.trimIndent()
    ).use { cursor ->
        while (cursor.moveToNext()) {
            val embedding = if (cursor.isNull(3)) null else decodeLegacyVector(cursor.getString(3), expectedSize = 512)
            db.execSQL(
                """
                INSERT INTO `categories_43` (
                    `id`, `name`, `searchTerms`, `embedding`, `referenceImageIds`, `threshold`,
                    `isUserCreated`, `isPinned`, `createdAt`, `updatedAt`
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(
                    cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                    embedding?.let(FloatVectorCodec::encode), cursor.getString(4), cursor.getDouble(5),
                    cursor.getInt(6), cursor.getInt(7), cursor.getLong(8), cursor.getLong(9)
                )
            )
        }
    }
    db.execSQL("DROP TABLE `categories`")
    db.execSQL("ALTER TABLE `categories_43` RENAME TO `categories`")
    db.execSQL(
        """
        CREATE TABLE `media_category` (
            `mediaId` INTEGER NOT NULL,
            `categoryId` INTEGER NOT NULL,
            `similarityScore` REAL NOT NULL,
            `addedAt` INTEGER NOT NULL,
            `isManuallyAdded` INTEGER NOT NULL,
            `resultRevision` TEXT NOT NULL DEFAULT '',
            PRIMARY KEY(`mediaId`, `categoryId`),
            FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent()
    )
    db.execSQL(
        """
        INSERT INTO `media_category` (`mediaId`, `categoryId`, `similarityScore`, `addedAt`, `isManuallyAdded`, `resultRevision`)
        SELECT `mediaId`, `categoryId`, `similarityScore`, `addedAt`, `isManuallyAdded`, `resultRevision`
        FROM `media_category_43_backup`
        """.trimIndent()
    )
    db.execSQL("DROP TABLE `media_category_43_backup`")
    db.execSQL("CREATE INDEX `index_media_category_categoryId` ON `media_category` (`categoryId`)")
    db.execSQL("CREATE INDEX `index_media_category_mediaId` ON `media_category` (`mediaId`)")
    db.execSQL("CREATE INDEX `index_media_category_similarityScore` ON `media_category` (`similarityScore`)")
}

private fun backfillFaceClusters(db: SupportSQLiteDatabase) {
    var personId: String? = null
    var sum = FloatArray(0)
    var count = 0
    var updatedAt = 0L

    fun persist() {
        val id = personId ?: return
        if (count <= 0) return
        val centroid = FloatArray(sum.size) { sum[it] / count }
        db.execSQL(
            "INSERT OR REPLACE INTO `face_clusters` (`personId`, `centroid`, `faceCount`, `updatedAt`) VALUES (?, ?, ?, ?)",
            arrayOf<Any>(id, FloatVectorCodec.encode(centroid), count, updatedAt)
        )
    }

    db.query(
        """
        SELECT faces.`personId`, faces.`embedding`, people.`lastUpdated`
        FROM `detected_faces` AS faces
        INNER JOIN `people` AS people ON people.`id` = faces.`personId`
        WHERE faces.`personId` IS NOT NULL AND faces.`embedding` IS NOT NULL
        ORDER BY faces.`personId`
        """.trimIndent()
    ).use { cursor ->
        while (cursor.moveToNext()) {
            val currentId = cursor.getString(0)
            val values = runCatching { FloatVectorCodec.decode(cursor.getBlob(1)) }
                .onFailure { e ->
                    printError("ml.migration", "face embedding decode failed; skipping row", e)
                }
                .getOrNull()
                ?.takeIf { it.size == 512 && it.all(Float::isFinite) } ?: continue
            if (personId != currentId) {
                persist()
                personId = currentId
                sum = FloatArray(values.size)
                count = 0
                updatedAt = cursor.getLong(2)
            }
            values.indices.forEach { sum[it] += values[it] }
            count++
        }
    }
    persist()
}

private fun decodeLegacyVector(value: String, expectedSize: Int): FloatArray? = runCatching {
    Json.decodeFromString<FloatArray>(value)
}.getOrNull()?.takeIf {
    if (it.size != expectedSize || it.any { value -> !value.isFinite() }) return@takeIf false
    var normSquared = 0.0
    it.forEach { value -> normSquared += value * value }
    normSquared in 0.9..1.1
}
