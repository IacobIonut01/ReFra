/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.async.executeSQL

/**
 * Search engines became selectable (CLIP 512-d alongside EmbeddingGemma 2 768-d), and
 * category thresholds became a normalized *sensitivity* (0-1) that each engine maps
 * onto its own cosine band.
 *
 * Data cleanup performed here (no schema change — this migration exists solely to
 * normalize stored data):
 *  - `image_embeddings`: drop rows whose dimension belongs to neither engine
 *    (CLIP 512-d = 2048 B, EG2 768-d = 3072 B). CLIP rows stay valid — CLIP is the
 *    default engine and revision checks already decide staleness.
 *  - `categories.embedding`: same dimension rule for cached text embeddings — the
 *    next classification pass recomputes whichever are missing.
 *  - `media_category`: untouched — automatic rows are re-validated per category by
 *    the classification phase (revision + similarity re-scoring), and the threshold
 *    normalization below preserves the stored sensitivity either way.
 *  - `categories.threshold`: convert the legacy CLIP cosine range [0.15, 0.80] into
 *    normalized sensitivity [0, 1]. Under CLIP the value maps back to the same cosine;
 *    under EG2 it lands at the equivalent strictness in EG2's compressed band.
 */
val MIGRATION_53_54 = object : Migration(53, 54) {

    override suspend fun migrate(connection: SQLiteConnection) {
        // FloatArray serialised by FloatVectorCodec: CLIP 512*4 = 2048 B, EG2 768*4 = 3072 B.
        connection.executeSQL(
            "DELETE FROM `image_embeddings` WHERE LENGTH(`embedding`) NOT IN (2048, 3072)"
        )
        connection.executeSQL(
            "UPDATE `categories` SET `embedding` = NULL " +
                "WHERE `embedding` IS NOT NULL AND LENGTH(`embedding`) NOT IN (2048, 3072)"
        )
        // Legacy rows stored CLIP-space cosines; the new contract stores sensitivity:
        // s = (cosine - CLIP_MIN) / (CLIP_MAX - CLIP_MIN), clamped to [0,1].
        connection.executeSQL(
            """
            UPDATE `categories` SET `threshold` =
                MIN(1.0, MAX(0.0, (`threshold` - 0.15) / 0.65))
            """.trimIndent()
        )
    }
}
