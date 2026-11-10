/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.async.executeSQL

/**
 * 56 → 57: text in photos. `ocr_results` was a stub nothing ever wrote, so it is rebuilt with the
 * columns the index needs, and the `ocr_search` FTS4 table is created.
 */
val MIGRATION_56_57 = object : Migration(56, 57) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.executeSQL("DROP TABLE IF EXISTS `ocr_results`")
        connection.executeSQL(
            "CREATE TABLE IF NOT EXISTS `ocr_results` (`mediaId` INTEGER NOT NULL, " +
                "`sourceType` TEXT NOT NULL DEFAULT 'local', `providerType` TEXT DEFAULT NULL, " +
                "`serverConfigId` INTEGER DEFAULT NULL, `remoteId` TEXT DEFAULT NULL, " +
                "`sourceRevision` TEXT NOT NULL DEFAULT '', `resultRevision` TEXT NOT NULL DEFAULT '', " +
                "`packIds` TEXT NOT NULL DEFAULT '', `fullText` TEXT NOT NULL DEFAULT '', " +
                "`normalizedText` TEXT NOT NULL DEFAULT '', `documentJson` TEXT NOT NULL DEFAULT '{}', " +
                "`documentVersion` INTEGER NOT NULL DEFAULT 1, `averageConfidence` REAL NOT NULL DEFAULT 0, " +
                "`updatedAt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`mediaId`))"
        )
        connection.executeSQL("CREATE INDEX IF NOT EXISTS `index_ocr_results_sourceType` ON `ocr_results` (`sourceType`)")
        connection.executeSQL(
            "CREATE INDEX IF NOT EXISTS `index_ocr_results_providerType_serverConfigId` ON `ocr_results` (`providerType`, `serverConfigId`)"
        )
        connection.executeSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `ocr_search` USING FTS4(`normalizedText` TEXT NOT NULL, " +
                "`searchTokens` TEXT NOT NULL, tokenize=unicode61)"
        )
    }
}
