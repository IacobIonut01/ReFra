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
import com.dot.gallery.data.InternalDatabase
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


/**
 * Covers the v51 -> v52 AutoMigration that creates the `face_suppressions` table —
 * the durable "never cluster this face into any person" record written when a
 * person is deleted.
 */
@RunWith(AndroidJUnit4::class)
class FaceSuppressionMigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation,
        File.createTempFile(
            "face-suppression-migration-test",
            ".db",
            instrumentation.targetContext.cacheDir
        ),
        BundledSQLiteDriver(),
        InternalDatabase::class
    )

    @Test
    fun migrate51To52CreatesFaceSuppressionsAndKeepsExistingFaces() = runBlocking {
        helper.createDatabase(51).use { db ->
            db.execSQL(
                """
                INSERT INTO people (
                    id, name, providerType, thumbnailMediaId, thumbnailUrl,
                    faceCount, lastUpdated, hidden
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>("local_person", "Person", "LOCAL_PEOPLE", 7L, "thumb", 1, 13L, 0)
            )
            db.execSQL(
                """
                INSERT INTO detected_faces (
                    mediaId, personId, embedding, left, top, right, bottom,
                    confidence, timestamp, resultRevision
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(7L, "local_person", byteArrayOf(1, 2), 0.1, 0.1, 0.8, 0.8, 0.9, 100L, "face-v2:model")
            )
        }

        // The v51 -> v52 AutoMigration is discovered from the database class itself.
        helper.runMigrationsAndValidate(52).use { db ->
            db.prepare(
                """
                SELECT COUNT(*) FROM sqlite_master
                WHERE type = 'table' AND name = 'face_suppressions'
                """.trimIndent()
            ).use { statement ->
                statement.step()
                assertEquals(1, statement.getInt(0))
            }
            // Existing person/face rows survive untouched.
            db.prepare("SELECT personId FROM detected_faces WHERE mediaId = 7").use { statement ->
                statement.step()
                assertEquals("local_person", statement.getText(0))
            }
            // The new table accepts rows keyed purely by media + box.
            db.execSQL(
                """
                INSERT INTO face_suppressions (
                    mediaId, left, top, right, bottom, createdAt
                ) VALUES (7, 0.1, 0.1, 0.8, 0.8, 1)
                """.trimIndent()
            )
            db.prepare("SELECT mediaId FROM face_suppressions").use { statement ->
                statement.step()
                assertEquals(7L, statement.getLong(0))
            }
            db.prepare("PRAGMA foreign_key_check").use { statement ->
                assertFalse(statement.step())
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
