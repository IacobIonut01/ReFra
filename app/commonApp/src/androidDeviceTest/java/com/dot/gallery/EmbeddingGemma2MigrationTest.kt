/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.data.FloatVectorCodec
import com.dot.gallery.data.InternalDatabase
import com.dot.gallery.data.migration.MIGRATION_53_54
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmbeddingGemma2MigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation,
        File.createTempFile(
            "eg2-migration-test",
            ".db",
            instrumentation.targetContext.cacheDir
        ),
        BundledSQLiteDriver(),
        InternalDatabase::class
    )

    @Test
    fun migrate53To54KeepsEngineEmbeddingsAndNormalizesSensitivity() = runBlocking {
        val clipVector = FloatVectorCodec.encode(FloatArray(512) { 1f / kotlin.math.sqrt(512f) })
        val eg2Vector = FloatVectorCodec.encode(FloatArray(768) { 1f / kotlin.math.sqrt(768f) })
        val bogusVector = FloatVectorCodec.encode(FloatArray(256) { 1f / 16f })
        helper.createDatabase(53).use { db ->
            // CLIP 512-d + EG2 768-d rows are both engine-valid and stay; a
            // 256-d row belongs to no engine and must go.
            db.execSQL(
                "INSERT INTO image_embeddings (id, date, embedding, resultRevision) VALUES (?, ?, ?, ?)",
                arrayOf<Any>(6L, 100L, bogusVector, "clip-v2:model")
            )
            db.execSQL(
                "INSERT INTO image_embeddings (id, date, embedding, resultRevision) VALUES (?, ?, ?, ?)",
                arrayOf<Any>(7L, 100L, clipVector, "clip-v2:model")
            )
            db.execSQL(
                "INSERT INTO image_embeddings (id, date, embedding, resultRevision) VALUES (?, ?, ?, ?)",
                arrayOf<Any>(8L, 100L, eg2Vector, "eg2-v1:model")
            )
            // Category 9: cached CLIP embedding (kept) + low CLIP threshold.
            // Category 10: no embedding + max CLIP threshold (remap must clamp to 1.0).
            // Category 11: below-range threshold (remap must clamp to 0.0).
            // Category 12: bogus-dimension cached embedding (nulled) + mid threshold.
            db.execSQL(
                """
                INSERT INTO categories (
                    id, name, searchTerms, embedding, referenceImageIds, threshold,
                    isUserCreated, isPinned, createdAt, updatedAt
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(9L, "Nature", "nature", clipVector, "[]", 0.2, 0, 0, 10L, 11L)
            )
            db.execSQL(
                """
                INSERT INTO categories (
                    id, name, searchTerms, embedding, referenceImageIds, threshold,
                    isUserCreated, isPinned, createdAt, updatedAt
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(10L, "Strict", "strict", null, "[]", 0.8, 1, 0, 10L, 11L)
            )
            db.execSQL(
                """
                INSERT INTO categories (
                    id, name, searchTerms, embedding, referenceImageIds, threshold,
                    isUserCreated, isPinned, createdAt, updatedAt
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(11L, "Loose", "loose", null, "[]", 0.05, 1, 0, 10L, 11L)
            )
            db.execSQL(
                """
                INSERT INTO categories (
                    id, name, searchTerms, embedding, referenceImageIds, threshold,
                    isUserCreated, isPinned, createdAt, updatedAt
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(12L, "Mid", "mid", bogusVector, "[]", 0.475, 0, 0, 10L, 11L)
            )
            // Automatic + manual memberships are both kept — the classification
            // phase re-validates automatic rows under the active engine.
            db.execSQL(
                """
                INSERT INTO media_category (
                    mediaId, categoryId, similarityScore, addedAt, isManuallyAdded, resultRevision
                ) VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(7L, 9L, 0.8, 12L, 0, "categories-v2:model")
            )
            db.execSQL(
                """
                INSERT INTO media_category (
                    mediaId, categoryId, similarityScore, addedAt, isManuallyAdded, resultRevision
                ) VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(8L, 10L, 0.9, 12L, 1, "categories-v2:model")
            )
        }

        helper.runMigrationsAndValidate(54, listOf(MIGRATION_53_54)).use { db ->
            db.prepare("SELECT COUNT(*) FROM image_embeddings").use { statement ->
                statement.step()
                assertEquals(2, statement.getInt(0))
            }
            db.prepare("SELECT embedding FROM image_embeddings WHERE id = 7").use { statement ->
                statement.step()
                assertTrue(FloatVectorCodec.decode(statement.getBlob(0)).size == 512)
            }
            db.prepare("SELECT embedding FROM image_embeddings WHERE id = 8").use { statement ->
                statement.step()
                assertTrue(FloatVectorCodec.decode(statement.getBlob(0)).size == 768)
            }
            // CLIP-space category embedding survives — usable when CLIP is active.
            db.prepare("SELECT embedding, threshold FROM categories WHERE id = 9").use { statement ->
                statement.step()
                assertFalse(statement.isNull(0))
                assertTrue(FloatVectorCodec.decode(statement.getBlob(0)).size == 512)
                // s = (0.2 - 0.15) / 0.65
                assertEquals(0.077, statement.getDouble(1), 0.001)
            }
            db.prepare("SELECT threshold FROM categories WHERE id = 10").use { statement ->
                statement.step()
                assertEquals(1.0, statement.getDouble(0), 0.001)
            }
            db.prepare("SELECT threshold FROM categories WHERE id = 11").use { statement ->
                statement.step()
                assertEquals(0.0, statement.getDouble(0), 0.001)
            }
            db.prepare("SELECT embedding, threshold FROM categories WHERE id = 12").use { statement ->
                statement.step()
                assertTrue(statement.isNull(0))
                // s = (0.475 - 0.15) / 0.65 = 0.5
                assertEquals(0.5, statement.getDouble(1), 0.001)
            }
            db.prepare("SELECT COUNT(*) FROM media_category").use { statement ->
                statement.step()
                assertEquals(2, statement.getInt(0))
            }
        }
    }
}

/** Args-style exec for fixture inserts, mirroring `SupportSQLiteDatabase.execSQL(sql, args)`. */
private fun SQLiteConnection.execSQL(sql: String, args: Array<out Any?>) {
    prepare(sql).use { statement ->
        args.forEachIndexed { i, arg ->
            when (arg) {
                null -> statement.bindNull(i + 1)
                is String -> statement.bindText(i + 1, arg)
                is Long -> statement.bindLong(i + 1, arg)
                is Int -> statement.bindInt(i + 1, arg)
                is Double -> statement.bindDouble(i + 1, arg)
                is Float -> statement.bindFloat(i + 1, arg)
                is Boolean -> statement.bindBoolean(i + 1, arg)
                is ByteArray -> statement.bindBlob(i + 1, arg)
                else -> error("Unsupported bind argument: $arg")
            }
        }
        statement.step()
    }
}
