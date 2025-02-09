/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.util.ext

import android.database.Cursor
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import com.dot.gallery.core.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

fun <T> Cursor?.mapEachRow(
    projection: Array<String>,
    mapping: (Cursor, Array<Int>) -> T,
) = this?.use { cursor ->
    if (!cursor.moveToFirst()) {
        return@use emptyList<T>()
    }

    val indexCache = projection.map { column ->
        cursor.getColumnIndexOrThrow(column)
    }.toTypedArray()

    val data = mutableListOf<T>()
    do {
        data.add(mapping(cursor, indexCache))
    } while (cursor.moveToNext())

    data.toList()
} ?: emptyList()

fun Cursor?.tryGetString(columnIndex: Int, fallback: String? = null): String? {
    return this?.getStringOrNull(columnIndex) ?: fallback
}

fun Cursor?.tryGetLong(columnIndex: Int, fallback: Long? = null): Long? {
    return this?.getLongOrNull(columnIndex) ?: fallback
}

fun <T> Flow<List<T>>.mapAsResource(errorOnEmpty: Boolean = false, errorMessage: String = "No data found") = map {
    if (errorOnEmpty && it.isEmpty()) {
        Resource.Error(errorMessage)
    } else {
        Resource.Success(it)
    }
}.flowOn(Dispatchers.IO)