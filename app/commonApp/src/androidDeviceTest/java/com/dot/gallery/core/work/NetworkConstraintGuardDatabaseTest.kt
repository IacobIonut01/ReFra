/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.core.work

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Offline-variant regression: the guard used to sanitize databases/androidx.work.workdb while
 * androidx.work keeps its live database in the no-backup directory, so network-constrained
 * WorkSpecs persisted by an older build survived and crashed every launch with
 * SecurityException (ACCESS_NETWORK_STATE).
 */
@RunWith(AndroidJUnit4::class)
class NetworkConstraintGuardDatabaseTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        dbFile = File(context.cacheDir, "guard-test.workdb").also { SQLiteDatabase.deleteDatabase(it) }
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            db.execSQL("CREATE TABLE WorkSpec (id TEXT PRIMARY KEY NOT NULL, required_network_type INTEGER NOT NULL)")
            db.execSQL("INSERT INTO WorkSpec VALUES ('constrained', 1), ('unmetered', 2), ('free', 0)")
        }
    }

    @After
    fun tearDown() {
        SQLiteDatabase.deleteDatabase(dbFile)
    }

    @Test
    fun liveWorkManagerDatabaseIsSanitizedFirst() {
        val helper = Class.forName("androidx.work.impl.WorkDatabasePathHelper")
        val livePath = helper.getMethod("getDatabasePath", Context::class.java)
            .invoke(helper.getField("INSTANCE").get(null), context) as File
        val legacyPath = helper.getMethod("getDefaultDatabasePath", Context::class.java)
            .invoke(helper.getField("INSTANCE").get(null), context) as File

        assertEquals(
            listOf(livePath, legacyPath),
            NetworkConstraintGuard.workDatabaseFiles(context)
        )
    }

    @Test
    fun sanitizePurgesConstrainedSpecsAndStripsLaterWrites() {
        NetworkConstraintGuard.sanitizeWorkDatabase(dbFile)

        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            assertEquals(listOf("free"), db.ids())
            db.execSQL("INSERT INTO WorkSpec VALUES ('later', 1)")
            db.execSQL("UPDATE WorkSpec SET required_network_type = 2 WHERE id = 'free'")
            db.rawQuery("SELECT COUNT(*) FROM WorkSpec WHERE required_network_type <> 0", null).use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
            assertEquals(listOf("free", "later"), db.ids())
        }
    }

    @Test
    fun missingDatabaseIsNotCreated() {
        val missing = File(context.cacheDir, "missing.workdb")
        SQLiteDatabase.deleteDatabase(missing)

        NetworkConstraintGuard.sanitizeWorkDatabase(missing)

        assertFalse(missing.exists())
    }

    private fun SQLiteDatabase.ids(): List<String> =
        rawQuery("SELECT id FROM WorkSpec ORDER BY id", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
}
