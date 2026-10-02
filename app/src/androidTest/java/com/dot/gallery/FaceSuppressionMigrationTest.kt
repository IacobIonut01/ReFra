/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import com.dot.gallery.feature_node.data.data_source.InternalDatabase_AutoMigration_51_52_Impl
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the v51 -> v52 AutoMigration that creates the `face_suppressions` table —
 * the durable "never cluster this face into any person" record written when a
 * person is deleted (#1262).
 */
@RunWith(AndroidJUnit4::class)
class FaceSuppressionMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        InternalDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate51To52CreatesFaceSuppressionsAndKeepsExistingFaces() {
        helper.createDatabase(TEST_DB, 51).use { db ->
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

        helper.runMigrationsAndValidate(
            TEST_DB,
            52,
            true,
            InternalDatabase_AutoMigration_51_52_Impl()
        ).use { db ->
            db.query(
                """
                SELECT COUNT(*) FROM sqlite_master
                WHERE type = 'table' AND name = 'face_suppressions'
                """.trimIndent()
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            // Existing person/face rows survive untouched.
            db.query("SELECT personId FROM detected_faces WHERE mediaId = 7").use { cursor ->
                cursor.moveToFirst()
                assertEquals("local_person", cursor.getString(0))
            }
            // The new table accepts rows keyed purely by media + box.
            db.execSQL(
                """
                INSERT INTO face_suppressions (
                    mediaId, left, top, right, bottom, createdAt
                ) VALUES (7, 0.1, 0.1, 0.8, 0.8, 1)
                """.trimIndent()
            )
            db.query("SELECT mediaId FROM face_suppressions").use { cursor ->
                cursor.moveToFirst()
                assertEquals(7L, cursor.getLong(0))
            }
            db.query("PRAGMA foreign_key_check").use { cursor ->
                assertEquals(0, cursor.count)
            }
        }
    }

    private companion object {
        const val TEST_DB = "face-suppression-migration-test"
    }
}
