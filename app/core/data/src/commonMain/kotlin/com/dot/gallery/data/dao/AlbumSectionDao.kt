/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.dot.gallery.domain.model.AlbumSection
import com.dot.gallery.domain.model.AlbumSectionMember
import kotlinx.coroutines.flow.Flow


@Dao
interface AlbumSectionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSection(section: AlbumSection): Long

    @Update
    suspend fun updateSection(section: AlbumSection)

    @Query("DELETE FROM album_sections WHERE id = :sectionId")
    suspend fun deleteSection(sectionId: Long)

    @Query("SELECT * FROM album_sections ORDER BY displayOrder ASC")
    fun getAllSections(): Flow<List<AlbumSection>>

    @Query("SELECT * FROM album_sections ORDER BY displayOrder ASC")
    suspend fun getAllSectionsAsync(): List<AlbumSection>

    @Query("SELECT * FROM album_sections WHERE id = :sectionId")
    suspend fun getSectionAsync(sectionId: Long): AlbumSection?

    @Query("SELECT * FROM album_sections WHERE type = :type LIMIT 1")
    suspend fun getSectionByType(type: Int): AlbumSection?

    @Query("UPDATE album_sections SET displayOrder = :order WHERE id = :sectionId")
    suspend fun updateDisplayOrder(sectionId: Long, order: Int)

    @Query("UPDATE album_sections SET isVisible = :visible WHERE id = :sectionId")
    suspend fun updateVisibility(sectionId: Long, visible: Boolean)

    @Query("UPDATE album_sections SET isExpanded = :expanded WHERE id = :sectionId")
    suspend fun updateExpanded(sectionId: Long, expanded: Boolean)

    @Query("SELECT COUNT(*) FROM album_sections")
    suspend fun getSectionCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addAlbumToSection(member: AlbumSectionMember)

    @Delete
    suspend fun removeAlbumFromSection(member: AlbumSectionMember)

    @Query("DELETE FROM album_section_members WHERE sectionId = :sectionId AND albumId = :albumId")
    suspend fun removeAlbumFromSectionById(sectionId: Long, albumId: Long)

    @Query("DELETE FROM album_section_members WHERE albumId = :albumId")
    suspend fun removeAlbumFromAllSections(albumId: Long)

    @Query("SELECT * FROM album_section_members")
    fun getAllSectionMembers(): Flow<List<AlbumSectionMember>>

    @Query("SELECT * FROM album_section_members")
    suspend fun getAllSectionMembersAsync(): List<AlbumSectionMember>

    @Query("SELECT sectionId FROM album_section_members WHERE albumId = :albumId LIMIT 1")
    suspend fun getSectionIdForAlbum(albumId: Long): Long?

    @Query("SELECT albumId FROM album_section_members WHERE sectionId = :sectionId")
    fun getAlbumIdsInSection(sectionId: Long): Flow<List<Long>>
}
