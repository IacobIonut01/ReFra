/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.cloud.core.ProviderType

@Entity(
    tableName = "people",
    indices = [Index(value = ["providerType"])]
)
data class PersonEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val providerType: ProviderType,
    val thumbnailMediaId: Long? = null,
    val thumbnailUrl: String? = null,
    val faceCount: Int = 0,
    val lastUpdated: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val hidden: Boolean = false,
    /** ISO-8601 calendar date (`yyyy-MM-dd`). Empty when unset. */
    @ColumnInfo(defaultValue = "")
    val birthDate: String = ""
)

fun PersonEntity.toPersonInfo(photoCount: Int): PersonInfo = PersonInfo(
    id = id,
    name = name,
    providerType = ProviderType.LOCAL_PEOPLE,
    serverConfigId = com.dot.gallery.cloud.core.LOCAL_PEOPLE_CONFIG_ID,
    thumbnailUrl = thumbnailUrl,
    assetCount = photoCount,
    birthDate = birthDate.takeIf { it.isNotBlank() },
    hidden = hidden
)
