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
import com.dot.gallery.domain.model.AlbumGroup
import com.dot.gallery.domain.model.AlbumGroupMember
import kotlinx.coroutines.flow.Flow


@Dao
interface AlbumGroupDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: AlbumGroup): Long

    @Update
    suspend fun updateGroup(group: AlbumGroup)

    @Query("DELETE FROM album_groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: Long)

    @Query("SELECT * FROM album_groups ORDER BY timestamp DESC")
    fun getAllGroups(): Flow<List<AlbumGroup>>

    @Query("SELECT * FROM album_groups WHERE id = :groupId")
    fun getGroup(groupId: Long): Flow<AlbumGroup?>

    @Query("SELECT * FROM album_groups WHERE id = :groupId")
    suspend fun getGroupAsync(groupId: Long): AlbumGroup?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addAlbumToGroup(member: AlbumGroupMember)

    @Delete
    suspend fun removeAlbumFromGroup(member: AlbumGroupMember)

    @Query("DELETE FROM album_group_members WHERE groupId = :groupId")
    suspend fun removeAllAlbumsFromGroup(groupId: Long)

    @Query("SELECT albumId FROM album_group_members WHERE groupId = :groupId")
    fun getAlbumIdsInGroup(groupId: Long): Flow<List<Long>>

    @Query("SELECT albumId FROM album_group_members WHERE groupId = :groupId")
    suspend fun getAlbumIdsInGroupAsync(groupId: Long): List<Long>

    @Query("SELECT * FROM album_group_members")
    fun getAllGroupMembers(): Flow<List<AlbumGroupMember>>

    @Query("SELECT * FROM album_groups ORDER BY timestamp DESC")
    suspend fun getAllGroupsAsync(): List<AlbumGroup>

    @Query("SELECT * FROM album_group_members")
    suspend fun getAllGroupMembersAsync(): List<AlbumGroupMember>

    @Query("SELECT groupId FROM album_group_members WHERE albumId = :albumId LIMIT 1")
    suspend fun getGroupIdForAlbum(albumId: Long): Long?

    @Query("SELECT EXISTS(SELECT 1 FROM album_group_members WHERE albumId = :albumId)")
    fun isAlbumInAnyGroup(albumId: Long): Flow<Boolean>
}
