/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data

import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import com.dot.gallery.data.migration.MIGRATION_12_13
import com.dot.gallery.data.migration.MIGRATION_33_34
import com.dot.gallery.data.migration.MIGRATION_35_36
import com.dot.gallery.data.migration.MIGRATION_36_37
import com.dot.gallery.data.migration.MIGRATION_37_38
import com.dot.gallery.data.migration.MIGRATION_40_41
import com.dot.gallery.data.migration.MIGRATION_41_42
import com.dot.gallery.data.migration.MIGRATION_42_43
import com.dot.gallery.data.migration.MIGRATION_43_44
import com.dot.gallery.data.migration.MIGRATION_44_45
import com.dot.gallery.data.migration.MIGRATION_45_46
import com.dot.gallery.data.migration.MIGRATION_46_47
import com.dot.gallery.data.migration.MIGRATION_53_54
import kotlinx.coroutines.Dispatchers

val DATABASE_MIGRATIONS: List<Migration> = listOf(
    MIGRATION_12_13,
    MIGRATION_33_34,
    MIGRATION_35_36,
    MIGRATION_36_37,
    MIGRATION_37_38,
    MIGRATION_40_41,
    MIGRATION_41_42,
    MIGRATION_42_43,
    MIGRATION_43_44,
    MIGRATION_44_45,
    MIGRATION_45_46,
    MIGRATION_46_47,
    MIGRATION_53_54,
)

/**
 * Finishes a platform-created builder: manual migrations + background query
 * context. The driver (SQLCipher / bundled / web worker) is supplied by the
 * caller so this stays platform- and encryption-agnostic.
 */
fun getInternalDatabase(
    builder: RoomDatabase.Builder<InternalDatabase>
): InternalDatabase = builder
    .addMigrations(*DATABASE_MIGRATIONS.toTypedArray())
    .setQueryCoroutineContext(Dispatchers.Default)
    .build()
