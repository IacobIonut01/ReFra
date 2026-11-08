/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.async.executeSQL
import androidx.sqlite.async.prepare
import androidx.sqlite.async.step


/**
 * Migration from version 12 to 13 for the ignored albums feature.
 * 
 * Changes:
 * - label column is now nullable
 * - Added albumIds column with default '[]'
 * - Re-generate labels based on type (Single/Multiple/Regex)
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.executeSQL("""
            CREATE TABLE IF NOT EXISTS blacklist_new (
                id INTEGER NOT NULL PRIMARY KEY,
                label TEXT,
                wildcard TEXT,
                albumIds TEXT NOT NULL DEFAULT '[]',
                location INTEGER NOT NULL DEFAULT 0,
                matchedAlbums TEXT NOT NULL DEFAULT '[]'
            )
        """)
        
        connection.executeSQL("""
            INSERT INTO blacklist_new (id, label, wildcard, location, matchedAlbums)
            SELECT id, label, wildcard, location, matchedAlbums FROM blacklist
        """)
        
        connection.executeSQL("DROP TABLE blacklist")
        
        connection.executeSQL("ALTER TABLE blacklist_new RENAME TO blacklist")
        
        regenerateLabels(connection)
    }
    
    private suspend fun regenerateLabels(connection: SQLiteConnection) {
        val cursor = connection.prepare("SELECT id, label, wildcard, albumIds FROM blacklist")
        
        val singleLabels = mutableListOf<String>()
        val multipleLabels = mutableListOf<String>()
        val regexLabels = mutableListOf<String>()
        
        val albumsToUpdate = mutableListOf<AlbumLabelUpdate>()
        
        cursor.use {
            while (it.step()) {
                val id = it.getLong(0)
                val oldLabel = if (it.isNull(1)) null else it.getText(1)
                val wildcard = if (it.isNull(2)) null else it.getText(2)
                val albumIds = it.getText(3)
                
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
                
                val isValidLabel = oldLabel?.let { label ->
                    label.startsWith("$type #") && label !in existingLabels
                } ?: false
                
                val newLabel = if (isValidLabel) {
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
        
        connection.prepare("UPDATE blacklist SET label = ? WHERE id = ?").use { stmt ->
            albumsToUpdate.forEach { update ->
                stmt.clearBindings()
                stmt.bindText(1, update.newLabel)
                stmt.bindLong(2, update.id)
                stmt.step()
            }
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
