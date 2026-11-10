/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.dot.gallery.core.logging.printWarn

/**
 * Android builder for [InternalDatabase]. Pass [SqlCipherDriver] for the
 * production encrypted database, or [BundledSQLiteDriver] for tests /
 * unencrypted fallbacks.
 */
fun getAndroidDatabaseBuilder(
    context: Context
): RoomDatabase.Builder<InternalDatabase> =
    Room.databaseBuilder<InternalDatabase>(context, InternalDatabase.NAME)

/** Plaintext fallback for devices without a usable hardware keystore. */
fun getBundledDatabase(context: Context): InternalDatabase =
    getInternalDatabase(
        getAndroidDatabaseBuilder(context)
            .setDriver(BundledSQLiteDriver())
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .fallbackToDestructiveMigration(true)
    )

/**
 * The database to use when the encrypted one could not be opened. A plaintext file
 * (or none) opens with [getBundledDatabase]. An encrypted file is never handed to the
 * plaintext driver, which fails every query with "file is not a database"; the session
 * runs on an in-memory database instead and the file is left for the next launch.
 */
fun getFallbackDatabase(context: Context): InternalDatabase {
    val file = context.getDatabasePath(InternalDatabase.NAME)
    if (DatabaseFileState.of(file) != DatabaseFileState.NOT_PLAINTEXT) {
        return getBundledDatabase(context)
    }
    printWarn(
        tag = "db.encryption",
        message = "Database file is encrypted; using an in-memory database for this session",
        ctx = mapOf("bytes" to file.length().toString()),
    )
    return getInternalDatabase(
        Room.inMemoryDatabaseBuilder<InternalDatabase>(context)
            .setDriver(BundledSQLiteDriver())
    )
}
