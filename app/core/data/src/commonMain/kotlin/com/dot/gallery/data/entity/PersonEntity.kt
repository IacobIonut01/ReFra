/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
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
    @ColumnInfo(defaultValue = "")
    val birthDate: String = ""
)
