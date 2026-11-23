/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Content hashes of local media, used to find exact duplicates.
 *
 * Only files whose byte size another file shares are ever hashed, so most media has no row.
 * [partialHash] is a SHA-1 of the first and last 64 KiB plus the size; [sha1] is the full-file
 * SHA-1 and is only computed while the partial hash still collides with another file's.
 * A row is reused while its source key (size, modified time, path) still matches the media.
 */
@Entity(
    tableName = "media_content_hash",
    indices = [Index(value = ["sha1"]), Index(value = ["sourceSize"])]
)
data class MediaContentHashEntity(
    @PrimaryKey val mediaId: Long,
    val sourceSize: Long,
    val sourceModifiedSeconds: Long,
    val sourcePath: String,
    val partialHash: String?,
    val sha1: String?,
    val updatedAtMillis: Long,
) {
    fun isFreshFor(size: Long, modifiedSeconds: Long, path: String): Boolean =
        sourceSize == size && sourceModifiedSeconds == modifiedSeconds && sourcePath == path
}
