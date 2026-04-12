/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.data.data_source.migration

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 12 to 13 for the ignored albums feature.
 * 
 * Changes:
 * - label column is now nullable
 * - Added albumIds column with default '[]'
 * - Re-generate labels based on type (Single/Multiple/Regex)
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create temporary table with new schema
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS blacklist_new (
                id INTEGER NOT NULL PRIMARY KEY,
                label TEXT,
                wildcard TEXT,
                albumIds TEXT NOT NULL DEFAULT '[]',
                location INTEGER NOT NULL DEFAULT 0,
                matchedAlbums TEXT NOT NULL DEFAULT '[]'
            )
        """)
        
        // Copy data from old table to new table
        db.execSQL("""
            INSERT INTO blacklist_new (id, label, wildcard, location, matchedAlbums)
            SELECT id, label, wildcard, location, matchedAlbums FROM blacklist
        """)
        
        // Drop old table
        db.execSQL("DROP TABLE blacklist")
        
        // Rename new table to original name
        db.execSQL("ALTER TABLE blacklist_new RENAME TO blacklist")
        
        // Now regenerate labels based on type
        regenerateLabels(db)
    }
    
    private fun regenerateLabels(db: SupportSQLiteDatabase) {
        // Get all ignored albums
        val cursor = db.query("SELECT id, label, wildcard, albumIds FROM blacklist")
        
        val singleLabels = mutableListOf<String>()
        val multipleLabels = mutableListOf<String>()
        val regexLabels = mutableListOf<String>()
        
        val albumsToUpdate = mutableListOf<AlbumLabelUpdate>()
        
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow("id"))
                val oldLabel = it.getString(it.getColumnIndexOrThrow("label"))
                val wildcard = it.getString(it.getColumnIndexOrThrow("wildcard"))
                val albumIds = it.getString(it.getColumnIndexOrThrow("albumIds"))
                
                val type = when {
                    wildcard != null && wildcard.isNotEmpty() -> "Regex"
                    albumIds != "[]" && albumIds.isNotEmpty() -> "Multiple"
                    else -> "Single"
                }
                
                val existingLabels = when (type) {
                    "Single" -> singleLabels
                    "Multiple" -> multipleLabels
                    "Regex" -> regexLabels
                    else -> singleLabels
                }
                
                // Check if current label is valid (matches pattern and not duplicate)
                val isValidLabel = oldLabel?.let { label ->
                    label.startsWith("$type #") && label !in existingLabels
                } ?: false
                
                val newLabel = if (isValidLabel && oldLabel != null) {
                    oldLabel
                } else {
                    generateUniqueLabel(type, existingLabels)
                }
                
                existingLabels.add(newLabel)
                
                if (oldLabel != newLabel) {
                    albumsToUpdate.add(AlbumLabelUpdate(id, newLabel))
                }
            }
        }
        
        // Update labels
        albumsToUpdate.forEach { update ->
            val contentValues = ContentValues().apply {
                put("label", update.newLabel)
            }
            db.update("blacklist", SQLiteDatabase.CONFLICT_REPLACE, contentValues, "id = ?", arrayOf(update.id))
        }
    }
    
    private fun generateUniqueLabel(type: String, existingLabels: List<String>): String {
        var labelNumber = existingLabels.size + 1
        var generatedLabel = "$type #$labelNumber"
        while (generatedLabel in existingLabels) {
            labelNumber++
            generatedLabel = "$type #$labelNumber"
        }
        return generatedLabel
    }
}

private data class AlbumLabelUpdate(val id: Long, val newLabel: String)
